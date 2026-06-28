# ============================================
# Terraform Outputs — Payment Chaos Engineering
# ============================================

# -------- Regions --------

output "primary_region" {
  description = "AWS primary region"
  value       = var.primary_region
}

output "secondary_region" {
  description = "AWS secondary (failover) region"
  value       = var.secondary_region
}

# -------- DynamoDB --------

output "payments_table_name" {
  description = "DynamoDB PaymentTransactions table name"
  value       = aws_dynamodb_table.payments.name
}

output "payments_table_arn" {
  description = "DynamoDB PaymentTransactions table ARN"
  value       = aws_dynamodb_table.payments.arn
}

output "payments_table_stream_arn" {
  description = "DynamoDB PaymentTransactions stream ARN (used by Lambda triggers)"
  value       = aws_dynamodb_table.payments.stream_arn
}

output "event_store_table_name" {
  description = "DynamoDB PaymentEventStore table name"
  value       = aws_dynamodb_table.payments_event_store.name
}

output "event_store_table_arn" {
  description = "DynamoDB PaymentEventStore table ARN"
  value       = aws_dynamodb_table.payments_event_store.arn
}

output "event_store_stream_arn" {
  description = "DynamoDB PaymentEventStore stream ARN"
  value       = aws_dynamodb_table.payments_event_store.stream_arn
}

# -------- Lambda Functions --------

output "payment_processor_function_name" {
  description = "PaymentProcessor Lambda function name"
  value       = aws_lambda_function.payment_processor.function_name
}

output "payment_processor_function_arn" {
  description = "PaymentProcessor Lambda function ARN"
  value       = aws_lambda_function.payment_processor.arn
}

output "payment_processor_invoke_arn" {
  description = "PaymentProcessor Lambda invoke ARN (for API Gateway integration)"
  value       = aws_lambda_function.payment_processor.invoke_arn
}

output "payment_query_function_name" {
  description = "PaymentQuery Lambda function name"
  value       = aws_lambda_function.payment_query.function_name
}

output "payment_query_function_arn" {
  description = "PaymentQuery Lambda function ARN"
  value       = aws_lambda_function.payment_query.arn
}

output "chaos_injector_function_name" {
  description = "ChaosInjector Lambda function name"
  value       = aws_lambda_function.chaos_injector.function_name
}

output "chaos_injector_function_arn" {
  description = "ChaosInjector Lambda function ARN"
  value       = aws_lambda_function.chaos_injector.arn
}

# -------- API Gateway --------

output "api_gateway_id" {
  description = "REST API Gateway ID"
  value       = aws_api_gateway_rest_api.payment_api.id
}

output "api_gateway_root_resource_id" {
  description = "API Gateway root resource ID"
  value       = aws_api_gateway_rest_api.payment_api.root_resource_id
}

output "api_gateway_execution_arn" {
  description = "API Gateway execution ARN (used for Lambda permission)"
  value       = aws_api_gateway_rest_api.payment_api.execution_arn
}

# -------- Route53 / DNS --------

output "payment_api_dns_name" {
  description = "Payment API public DNS name (with failover)"
  value       = "payment-api.${var.domain}"
}

output "primary_health_check_id" {
  description = "Route53 health check ID for the primary region"
  value       = aws_route53_health_check.primary.id
}

output "secondary_health_check_id" {
  description = "Route53 health check ID for the secondary (failover) region"
  value       = aws_route53_health_check.secondary.id
}

# -------- SQS --------

output "dlq_url" {
  description = "SQS Dead Letter Queue URL"
  value       = aws_sqs_queue.dlq.url
}

output "dlq_arn" {
  description = "SQS Dead Letter Queue ARN"
  value       = aws_sqs_queue.dlq.arn
}

# -------- IAM --------

output "lambda_execution_role_arn" {
  description = "IAM role ARN assumed by all Lambda functions"
  value       = aws_iam_role.lambda_role.arn
}

output "lambda_execution_role_name" {
  description = "IAM role name assumed by all Lambda functions"
  value       = aws_iam_role.lambda_role.name
}

# -------- Summary (for CI/CD pipelines) --------

output "deployment_summary" {
  description = "Key deployment values as a single map — convenient for CI/CD env-var injection"
  value = {
    primary_region              = var.primary_region
    secondary_region            = var.secondary_region
    environment                 = var.environment
    payments_table              = aws_dynamodb_table.payments.name
    event_store_table           = aws_dynamodb_table.payments_event_store.name
    payment_processor_function  = aws_lambda_function.payment_processor.function_name
    payment_query_function      = aws_lambda_function.payment_query.function_name
    chaos_injector_function     = aws_lambda_function.chaos_injector.function_name
    api_gateway_id              = aws_api_gateway_rest_api.payment_api.id
    dlq_url                     = aws_sqs_queue.dlq.url
    api_dns                     = "payment-api.${var.domain}"
  }
}
