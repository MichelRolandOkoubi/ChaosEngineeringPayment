# 🔥 Payment Chaos Engineering - Multi-Region Serverless

<div align="center">

![Version](https://img.shields.io/badge/VERSION-2.0.0-blue?style=flat-square)
![Java](https://img.shields.io/badge/%F0%9F%94%A5_JAVA-17%2B-orange?style=flat-square)
![Quarkus](https://img.shields.io/badge/%F0%9F%94%B7_QUARKUS-3.X-4695EB?style=flat-square)
![AWS](https://img.shields.io/badge/AWS-MULTI--REGION-FF9900?style=flat-square&logo=amazonaws&logoColor=white)
![License](https://img.shields.io/badge/LICENSE-MIT-green?style=flat-square)
![Build](https://img.shields.io/badge/BUILD-PASSING-brightgreen?style=flat-square)
![Coverage](https://img.shields.io/badge/COVERAGE-87%25-yellowgreen?style=flat-square)
![Chaos](https://img.shields.io/badge/%F0%9F%92%A5_CHAOS-READY-red?style=flat-square)

</div>

> Chaos Engineering platform for a multi-region serverless payment aggregator  
> supporting Orange Money, Moov, MTN, Wave, Visa, Mastercard, Airtel, M-Pesa, Bitcoin, PI/SPI BCEAO

## Architecture Overview

### 🌍 Multi-Region AWS Architecture

```mermaid
graph TB
    subgraph CLIENT["🌐 Client Layer"]
        WEB["Web App / Mobile"]
        SDK["Payment SDK"]
    end

    subgraph DNS["🔀 DNS & Routing"]
        R53P["🟠 Route 53\nPrimary — Failover Policy"]
        R53S["🔵 Route 53\nSecondary — Failover Policy"]
    end

    subgraph US["🇺🇸 AWS Region: us-east-1 (Primary)"]
        direction TB
        APIGW_P["⚡ API Gateway\nREST + WebSocket"]
        LAMBDA_P["λ Lambda Functions\nQuarkus Native Runtime"]
        DDB_P["🗄️ DynamoDB Global Table\nEvent Store (us-east-1)"]
    end

    subgraph EU["🇪🇺 AWS Region: eu-west-1 (Replica)"]
        direction TB
        APIGW_R["⚡ API Gateway\n(Replica)"]
        LAMBDA_R["λ Lambda Functions\nQuarkus Native Runtime\n(Replica)"]
        DDB_R["🗄️ DynamoDB Global Table\nEvent Store (eu-west-1)"]
    end

    subgraph SHARED["⚙️ Shared Infrastructure"]
        direction LR
        KAFKA["📨 Apache Kafka\nEvent Streaming"]
        RABBIT["🐇 RabbitMQ\nMessage Queue"]
        PROM["📊 Prometheus\n+ Grafana"]
        ELK["🔍 ELK Stack\nLogging"]
        CHAOS["💥 Chaos Monkey\nChaos Engine"]
    end

    WEB & SDK --> R53P
    WEB & SDK --> R53S
    R53P -->|"Primary route"| APIGW_P
    R53S -->|"Failover route"| APIGW_R

    APIGW_P --> LAMBDA_P
    APIGW_R --> LAMBDA_R

    LAMBDA_P --> DDB_P
    LAMBDA_R --> DDB_R
    DDB_P <-->|"Global Replication"| DDB_R

    LAMBDA_P --> KAFKA
    LAMBDA_R --> KAFKA
    KAFKA --> RABBIT
    LAMBDA_P & LAMBDA_R --> PROM
    LAMBDA_P & LAMBDA_R --> ELK
    CHAOS -.->|"Inject faults"| LAMBDA_P
    CHAOS -.->|"Inject faults"| DDB_P

    style US fill:#1a3a5c,stroke:#4a90d9,color:#fff
    style EU fill:#1a3a5c,stroke:#4a90d9,color:#fff
    style SHARED fill:#2d1f3d,stroke:#9b59b6,color:#fff
    style CLIENT fill:#1a4a2e,stroke:#2ecc71,color:#fff
    style DNS fill:#4a2c00,stroke:#f39c12,color:#fff
```

---

### 💳 Payment Flow & Chaos Injection Points

```mermaid
sequenceDiagram
    autonumber
    actor Client as 🧑 Client
    participant GW as ⚡ API Gateway
    participant CMD as 📥 Command Handler<br/>(CQRS)
    participant SAGA as 🔄 Saga Orchestrator
    participant CB as 🛡️ Circuit Breaker<br/>(MicroProfile)
    participant PROV as 💳 Payment Provider<br/>(Orange / MTN / Visa…)
    participant ES as 🗄️ Event Store<br/>(DynamoDB)
    participant EVT as 📨 Event Bus<br/>(Kafka)
    participant CHAOS as 💥 Chaos Monkey

    Client->>GW: POST /api/v1/payments
    GW->>CMD: InitiatePaymentCommand

    Note over CHAOS: 🔴 Chaos Point 1<br/>Provider Failure Injection
    CHAOS-->>PROV: Inject latency / error

    CMD->>SAGA: Start Payment Saga
    SAGA->>CB: Execute with Circuit Breaker
    CB->>PROV: Charge Payment Request

    alt ✅ Provider Success
        PROV-->>CB: 200 OK + Transaction ID
        CB-->>SAGA: Success
        SAGA->>ES: PaymentSucceededEvent
        SAGA->>EVT: Publish PaymentSucceeded
        EVT-->>Client: Webhook notification
        CMD-->>GW: 201 Created
        GW-->>Client: PaymentConfirmed ✅

    else ⚠️ Provider Timeout / Failure
        PROV-->>CB: Timeout / 5xx
        CB-->>SAGA: Open Circuit (fallback)

        Note over CHAOS: 🔴 Chaos Point 2<br/>Failover to Backup Provider
        SAGA->>CB: Retry with backup provider
        CB->>PROV: Retry on alternate route

        alt 🔁 Retry Success
            PROV-->>CB: 200 OK (backup)
            CB-->>SAGA: Recovered
            SAGA->>ES: PaymentRetriedEvent
            CMD-->>GW: 201 Created (via fallback)
            GW-->>Client: PaymentConfirmed ✅ (fallback)
        else ❌ All Providers Failed
            SAGA->>ES: PaymentFailedEvent
            SAGA->>EVT: Publish PaymentFailed
            CMD-->>GW: 422 Unprocessable
            GW-->>Client: PaymentFailed ❌
        end
    end

    Note over CHAOS: 🔴 Chaos Point 3<br/>DB Partition Simulation
    CHAOS-->>ES: Block writes (partition)
    ES-->>SAGA: Write timeout → compensate
```

---

### 🔥 Chaos Experiments Map

```mermaid
mindmap
  root((💥 Chaos<br/>Engineering))
    Provider Failures
      Orange Money Down
      MTN MoMo Timeout
      Visa Gateway Error
      M-Pesa 5xx Flood
    Region Failover
      US-EAST-1 Outage
      Route53 DNS Switch
      EU-WEST-1 Takeover
      Cross-region Latency
    Database Chaos
      DynamoDB Partition
      Write Throttling
      Global Table Lag
      Event Store Corruption
    Infrastructure
      Lambda Cold Start
      Kafka Consumer Lag
      RabbitMQ Queue Full
      Network Partition
```

## Payment Providers Supported

| Provider      | Type          | Currencies     | Region        |
|---------------|---------------|----------------|---------------|
| Orange Money  | Mobile Money  | XOF, XAF, GNF  | West Africa   |
| Moov Money    | Mobile Money  | XOF, XAF       | West Africa   |
| MTN MoMo      | Mobile Money  | GHS, NGN, XOF  | Pan-Africa    |
| Wave          | Mobile Money  | XOF, GNF       | West Africa   |
| Airtel Money  | Mobile Money  | KES, TZS, UGX  | East Africa   |
| M-Pesa        | Mobile Money  | KES, TZS, GHS  | East Africa   |
| Visa          | Card          | USD, EUR, XOF  | International |
| Mastercard    | Card          | USD, EUR, XOF  | International |
| Bitcoin       | Crypto        | BTC, USD       | Global        |
| PI/SPI BCEAO  | Interbank     | XOF            | UEMOA Zone    |

## Architecture Patterns

- **DDD**: Domain-Driven Design with Aggregate Roots, Value Objects, Domain Events
- **CQRS**: Commands (write) separated from Queries (read)
- **Event Sourcing**: All state changes stored as events in DynamoDB
- **Event-Driven**: Kafka for event streaming, RabbitMQ for reliable messaging
- **Saga Pattern**: Distributed transactions across payment providers
- **Circuit Breaker**: MicroProfile Fault Tolerance
- **Chaos Engineering**: Netflix-style chaos monkey

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Maven 3.8+
- AWS CLI (for AWS deployment)
- Terraform 1.5+ (for AWS deployment)

### Local Development

```bash
# Clone repository
git clone https://github.com/org/payment-chaos-engineering.git
cd payment-chaos-engineering

# Build services
make build

# Start all services
make deploy-local

# Run tests
make test

# Start chaos mode (10% failure rate)
make chaos-start CHAOS_RATE=0.1

# Run chaos experiments
make chaos-experiment-failover
make chaos-experiment-provider PROVIDER=ORANGE

# View results
make chaos-report
open http://localhost:3000  # Grafana
```

### AWS Free Tier Deployment

```bash
# Configure AWS credentials
aws configure

# Initialize Terraform
cd infrastructure/terraform
terraform init

# Deploy (Free Tier optimized)
terraform plan -out=tfplan
terraform apply tfplan

# Outputs
terraform output payment_api_url
terraform output grafana_url
```

## Chaos Experiments

### 1. Provider Failure

Tests automatic failover when payment provider fails.

```bash
make chaos-experiment-provider PROVIDER=ORANGE
```

### 2. Region Failover

Tests Route53 health check failover to secondary region.

```bash
make chaos-experiment-failover
```

### 3. Database Partition

Tests system behavior during DynamoDB unavailability.

```bash
curl -X POST http://localhost:8080/api/v1/chaos/experiments/db-partition
```

### 4. Cascade Failure

Tests resilience when multiple providers fail simultaneously.

```bash
curl -X POST http://localhost:8080/api/v1/chaos/experiments/cascade-failure
```

## Monitoring

| Tool        | URL                                   | Credentials          |
|-------------|---------------------------------------|----------------------|
| Grafana     | http://localhost:3000                 | admin/chaos_admin    |
| Kibana      | http://localhost:5601                 | No auth required     |
| Prometheus  | http://localhost:9090                 | No auth required     |
| Kafka UI    | http://localhost:8085                 | No auth required     |
| RabbitMQ    | http://localhost:15672                | payment/payment_secret |
| Swagger UI  | http://localhost:8080/q/swagger-ui    | No auth              |
