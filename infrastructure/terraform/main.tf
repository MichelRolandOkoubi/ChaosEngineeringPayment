    terraform {
    required_version = ">= 1.5.0"
    required_providers {
        aws = {
        source  = "hashicorp/aws"
        version = "~> 5.0"
        }
    }
    
    backend "s3" {
        bucket         = "payment-chaos-terraform-state"
        key            = "chaos/terraform.tfstate"
        region         = "us-east-1"
        encrypt        = true
        dynamodb_table = "payment-chaos-terraform-locks"
    }
    }

    provider "aws" {
    alias  = "primary"
    region = var.primary_region
    }

    provider "aws" {
    alias  = "secondary"
    region = var.secondary_region
    }

    # ============================================
    # DynamoDB Tables - Multi-Region
    # ============================================
    resource "aws_dynamodb_table" "payments" {
    provider         = aws.primary
    name             = "PaymentTransactions"
    billing_mode     = "PAY_PER_REQUEST"
    hash_key         = "transactionId"
    range_key        = "timestamp"
    stream_enabled   = true
    stream_view_type = "NEW_AND_OLD_IMAGES"

    attribute {
        name = "transactionId"
        type = "S"
    }
    
    attribute {
        name = "timestamp"
        type = "S"
    }
    
    attribute {
        name = "providerId"
        type = "S"
    }
    
    attribute {
        name = "userId"
        type = "S"
    }
    
    attribute {
        name = "status"
        type = "S"
    }

    global_secondary_index {
        name               = "ProviderId-index"
        hash_key           = "providerId"
        range_key          = "timestamp"
        projection_type    = "ALL"
    }
    
    global_secondary_index {
        name               = "UserId-index"
        hash_key           = "userId"
        range_key          = "timestamp"
        projection_type    = "ALL"
    }
    
    global_secondary_index {
        name               = "Status-index"
        hash_key           = "status"
        range_key          = "timestamp"
        projection_type    = "ALL"
    }

    point_in_time_recovery {
        enabled = true
    }

    server_side_encryption {
        enabled = true
    }

    ttl {
        attribute_name = "expirationTime"
        enabled        = true
    }

    tags = {
        Environment = var.environment
        Project     = "PaymentChaosEngineering"
    }
    }

    # DynamoDB Global Tables - Multi-Region Replication
    resource "aws_dynamodb_table" "payments_event_store" {
    provider         = aws.primary
    name             = "PaymentEventStore"
    billing_mode     = "PAY_PER_REQUEST"
    hash_key         = "aggregateId"
    range_key        = "sequenceNumber"
    stream_enabled   = true
    stream_view_type = "NEW_AND_OLD_IMAGES"

    attribute {
        name = "aggregateId"
        type = "S"
    }
    
    attribute {
        name = "sequenceNumber"
        type = "N"
    }

    replica {
        region_name = var.secondary_region
    }

    point_in_time_recovery {
        enabled = true
    }

    tags = {
        Environment = var.environment
        Project     = "PaymentChaosEngineering"
    }
    }

    # ============================================
    # Lambda Functions
    # ============================================
    resource "aws_lambda_function" "payment_processor" {
    provider         = aws.primary
    filename         = "${path.module}/../../services/payment-processor/target/payment-processor.jar"
    function_name    = "PaymentProcessor"
    role             = aws_iam_role.lambda_role.arn
    handler          = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
    runtime          = "java17"
    memory_size      = 512
    timeout          = 30

    environment {
        variables = {
        DYNAMODB_TABLE        = aws_dynamodb_table.payments.name
        KAFKA_BROKERS         = var.kafka_brokers
        RABBITMQ_HOST         = var.rabbitmq_host
        REGION                = var.primary_region
        CHAOS_MODE_ENABLED    = "false"
        PAYMENT_PROVIDERS     = "ORANGE,MOOV,MTN,WAVE,VISA,MASTERCARD,AIRTEL,MPESA,BTC,PI_SPI_BCEAO"
        }
    }

    dead_letter_config {
        target_arn = aws_sqs_queue.dlq.arn
    }

    reserved_concurrent_executions = 100

    tracing_config {
        mode = "Active"
    }

    tags = {
        Environment = var.environment
    }
    }

    resource "aws_lambda_function" "payment_query" {
    provider      = aws.primary
    filename      = "${path.module}/../../services/payment-query/target/payment-query.jar"
    function_name = "PaymentQuery"
    role          = aws_iam_role.lambda_role.arn
    handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
    runtime       = "java17"
    memory_size   = 256
    timeout       = 15

    environment {
        variables = {
        DYNAMODB_TABLE = aws_dynamodb_table.payments.name
        REGION         = var.primary_region
        }
    }

    tracing_config {
        mode = "Active"
    }
    }

    resource "aws_lambda_function" "chaos_injector" {
    provider      = aws.primary
    filename      = "${path.module}/../../services/chaos-monkey/target/chaos-monkey.jar"
    function_name = "ChaosInjector"
    role          = aws_iam_role.lambda_role.arn
    handler       = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
    runtime       = "java17"
    memory_size   = 256
    timeout       = 60

    environment {
        variables = {
        TARGET_FUNCTIONS = "PaymentProcessor,PaymentQuery"
        CHAOS_SCENARIOS  = "LATENCY,ERROR,THROTTLE,REGION_FAILURE"
        }
    }
    }

    # ============================================
    # API Gateway
    # ============================================
    resource "aws_api_gateway_rest_api" "payment_api" {
    provider    = aws.primary
    name        = "PaymentChaosAPI"
    description = "Multi-region Payment API with Chaos Engineering"

    endpoint_configuration {
        types = ["REGIONAL"]
    }

    tags = {
        Environment = var.environment
    }
    }

    resource "aws_api_gateway_resource" "payments" {
    provider    = aws.primary
    rest_api_id = aws_api_gateway_rest_api.payment_api.id
    parent_id   = aws_api_gateway_rest_api.payment_api.root_resource_id
    path_part   = "payments"
    }

    resource "aws_api_gateway_method" "payment_post" {
    provider         = aws.primary
    rest_api_id      = aws_api_gateway_rest_api.payment_api.id
    resource_id      = aws_api_gateway_resource.payments.id
    http_method      = "POST"
    authorization    = "NONE"
    api_key_required = true
    
    request_validator_id = aws_api_gateway_request_validator.payment_validator.id
    
    request_models = {
        "application/json" = aws_api_gateway_model.payment_request.name
    }
    }

    resource "aws_api_gateway_integration" "payment_lambda" {
    provider                = aws.primary
    rest_api_id             = aws_api_gateway_rest_api.payment_api.id
    resource_id             = aws_api_gateway_resource.payments.id
    http_method             = aws_api_gateway_method.payment_post.http_method
    integration_http_method = "POST"
    type                    = "AWS_PROXY"
    uri                     = aws_lambda_function.payment_processor.invoke_arn
    
    timeout_milliseconds = 29000
    }

    # ============================================
    # Route53 Health Checks & Failover
    # ============================================
    resource "aws_route53_health_check" "primary" {
    fqdn              = var.primary_api_domain
    port              = 443
    type              = "HTTPS"
    resource_path     = "/health"
    failure_threshold = "3"
    request_interval  = "30"
    
    tags = {
        Name = "PaymentAPI-Primary-HealthCheck"
    }
    }

    resource "aws_route53_health_check" "secondary" {
    fqdn              = var.secondary_api_domain
    port              = 443
    type              = "HTTPS"
    resource_path     = "/health"
    failure_threshold = "3"
    request_interval  = "30"

    tags = {
        Name = "PaymentAPI-Secondary-HealthCheck"
    }
    }

    resource "aws_route53_record" "payment_api_primary" {
    zone_id = var.hosted_zone_id
    name    = "payment-api.${var.domain}"
    type    = "A"

    failover_routing_policy {
        type = "PRIMARY"
    }

    set_identifier  = "primary"
    health_check_id = aws_route53_health_check.primary.id

    alias {
        name                   = var.primary_api_gateway_domain
        zone_id                = var.api_gateway_zone_id
        evaluate_target_health = true
    }
    }

    resource "aws_route53_record" "payment_api_secondary" {
    zone_id = var.hosted_zone_id
    name    = "payment-api.${var.domain}"
    type    = "A"

    failover_routing_policy {
        type = "SECONDARY"
    }

    set_identifier  = "secondary"
    health_check_id = aws_route53_health_check.secondary.id

    alias {
        name                   = var.secondary_api_gateway_domain
        zone_id                = var.api_gateway_zone_id
        evaluate_target_health = true
    }
    }

    # ============================================
    # SQS Dead Letter Queue
    # ============================================
    resource "aws_sqs_queue" "dlq" {
    provider                   = aws.primary
    name                       = "payment-dlq"
    message_retention_seconds  = 86400
    visibility_timeout_seconds = 30

    tags = {
        Environment = var.environment
    }
    }

    # ============================================
    # IAM Roles
    # ============================================
    resource "aws_iam_role" "lambda_role" {
    name = "payment-lambda-chaos-role"

    assume_role_policy = jsonencode({
        Version = "2012-10-17"
        Statement = [
        {
            Action = "sts:AssumeRole"
            Effect = "Allow"
            Principal = {
            Service = "lambda.amazonaws.com"
            }
        }
        ]
    })
    }

    resource "aws_iam_role_policy" "lambda_policy" {
    name = "payment-lambda-chaos-policy"
    role = aws_iam_role.lambda_role.id

    policy = jsonencode({
        Version = "2012-10-17"
        Statement = [
        {
            Effect = "Allow"
            Action = [
            "dynamodb:GetItem",
            "dynamodb:PutItem",
            "dynamodb:UpdateItem",
            "dynamodb:DeleteItem",
            "dynamodb:Query",
            "dynamodb:Scan",
            "dynamodb:BatchWriteItem",
            "dynamodb:DescribeTable"
            ]
            Resource = [
            aws_dynamodb_table.payments.arn,
            aws_dynamodb_table.payments_event_store.arn,
            "${aws_dynamodb_table.payments.arn}/index/*",
            "${aws_dynamodb_table.payments_event_store.arn}/index/*"
            ]
        },
        {
            Effect = "Allow"
            Action = [
            "sqs:SendMessage",
            "sqs:ReceiveMessage",
            "sqs:DeleteMessage"
            ]
            Resource = aws_sqs_queue.dlq.arn
        },
        {
            Effect = "Allow"
            Action = [
            "logs:CreateLogGroup",
            "logs:CreateLogStream",
            "logs:PutLogEvents"
            ]
            Resource = "arn:aws:logs:*:*:*"
        },
        {
            Effect = "Allow"
            Action = [
            "xray:PutTraceSegments",
            "xray:PutTelemetryRecords"
            ]
            Resource = "*"
        },
        {
            Effect = "Allow"
            Action = [
            "lambda:InvokeFunction",
            "lambda:GetFunction",
            "lambda:UpdateFunctionConfiguration"
            ]
            Resource = "*"
        }
        ]
    })
    }