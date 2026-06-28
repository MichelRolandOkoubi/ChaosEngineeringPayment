    variable "primary_region" {
    description = "AWS Primary Region"
    default     = "us-east-1"
    }

    variable "secondary_region" {
    description = "AWS Secondary Region"
    default     = "eu-west-1"
    }

    variable "environment" {
    description = "Environment name"
    default     = "chaos-testing"
    }

    variable "domain" {
    description = "Domain name"
    default     = "payment-chaos.example.com"
    }

    variable "hosted_zone_id" {
    description = "Route53 Hosted Zone ID"
    }

    variable "kafka_brokers" {
    description = "Kafka brokers connection string"
    }

    variable "rabbitmq_host" {
    description = "RabbitMQ host"
    }

    variable "primary_api_domain" {
    description = "Primary API domain"
    }

    variable "secondary_api_domain" {
    description = "Secondary API domain"
    }

    variable "primary_api_gateway_domain" {
    description = "Primary API Gateway domain name"
    }

    variable "secondary_api_gateway_domain" {
    description = "Secondary API Gateway domain name"
    }

    variable "api_gateway_zone_id" {
    description = "API Gateway hosted zone ID"
    }