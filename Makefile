	.PHONY: all build deploy test chaos clean help

	DOCKER_COMPOSE = docker-compose
	PROJECT_NAME = payment-chaos-engineering
	CHAOS_RATE ?= 0.1

	# ============================================
	# Build Targets
	# ============================================
	build:
		@echo "🏗️ Building all services..."
		./mvnw clean package -pl services/payment-gateway,services/payment-processor,services/payment-query,services/chaos-monkey,services/fraud-detection,services/notification-service -DskipTests

	build-native:
		@echo "🏗️ Building native images..."
		./mvnw clean package -Pnative -pl services/payment-gateway,services/payment-processor

	docker-build:
		@echo "🐋 Building Docker images..."
		$(DOCKER_COMPOSE) build --parallel

	# ============================================
	# Deployment Targets
	# ============================================
	deploy-local:
		@echo "🚀 Deploying locally..."
		$(DOCKER_COMPOSE) up -d
		@echo "✅ Local deployment complete"
		@echo "   - Payment Gateway: http://localhost:8080"
		@echo "   - Grafana: http://localhost:3000 (admin/chaos_admin_2024)"
		@echo "   - Kibana: http://localhost:5601"
		@echo "   - Prometheus: http://localhost:9090"
		@echo "   - Kafka UI: http://localhost:8085"
		@echo "   - RabbitMQ: http://localhost:15672"
		@echo "   - DynamoDB: http://localhost:8000"

	deploy-aws:
		@echo "☁️ Deploying to AWS (Free Tier)..."
		cd infrastructure/terraform && terraform init && terraform apply -auto-approve

	destroy-aws:
		@echo "💥 Destroying AWS resources..."
		cd infrastructure/terraform && terraform destroy -auto-approve

	# ============================================
	# Testing Targets
	# ============================================
	test:
		@echo "🧪 Running tests..."
		./mvnw test -pl services/payment-gateway

	test-chaos:
		@echo "🔥 Running chaos engineering tests..."
		CHAOS_ENABLED=true CHAOS_FAILURE_RATE=$(CHAOS_RATE) \
		./mvnw test -pl tests/chaos -Dtest=PaymentChaosTest

	test-resilience:
		@echo "🛡️ Running resilience tests..."
		./mvnw test -pl tests/resilience

	test-integration:
		@echo "🔗 Running integration tests..."
		./mvnw test -pl tests/integration

	test-all: test test-resilience test-integration
		@echo "✅ All tests completed"

	load-test:
		@echo "⚡ Running load tests with k6..."
		k6 run tests/load/payment-load-test.js \
			-e BASE_URL=http://localhost:8080 \
			-e CHAOS_ENABLED=true \
			--out json=results/load-test-$(shell date +%Y%m%d_%H%M%S).json

	# ============================================
	# Chaos Engineering Targets
	# ============================================
	chaos-start:
		@echo "🔥 Starting chaos engineering mode..."
		$(DOCKER_COMPOSE) up -d
		CHAOS_ENABLED=true $(DOCKER_COMPOSE) -f docker-compose.chaos.yml up -d chaos-monkey
		@echo "💀 Chaos monkey is active!"

	chaos-stop:
		@echo "🛑 Stopping chaos engineering..."
		$(DOCKER_COMPOSE) stop chaos-monkey
		@echo "✅ Chaos stopped"

	chaos-inject-region-failure:
		@echo "🌍 Injecting region failure..."
		curl -X POST http://localhost:8090/chaos/region-failure \
			-H "Content-Type: application/json" \
			-d '{"region": "us-east-1", "duration": "60s"}'

	chaos-inject-provider-failure:
		@echo "💳 Injecting provider failure for $(PROVIDER)..."
		curl -X POST http://localhost:8080/api/v1/chaos/inject \
			-H "Content-Type: application/json" \
			-d '{"scenario": "ERROR", "target": "$(PROVIDER)", "duration": "30s"}'

	chaos-experiment-provider:
		@echo "🧪 Running provider failure experiment..."
		curl -X POST http://localhost:8080/api/v1/chaos/experiments/provider-failure \
			-H "Content-Type: application/json" \
			-d '{}' \
			-G --data-urlencode "target=$(PROVIDER)"

	chaos-experiment-failover:
		@echo "🧪 Running multi-region failover experiment..."
		curl -X POST http://localhost:8080/api/v1/chaos/experiments/region-failover

	chaos-report:
		@echo "📊 Getting chaos report..."
		curl -s http://localhost:8080/api/v1/chaos/report | jq .

	# ============================================
	# Monitoring Targets
	# ============================================
	monitoring-up:
		@echo "📊 Starting monitoring stack..."
		$(DOCKER_COMPOSE) up -d prometheus grafana elasticsearch kibana logstash alertmanager

	monitoring-down:
		@echo "🛑 Stopping monitoring stack..."
		$(DOCKER_COMPOSE) stop prometheus grafana elasticsearch kibana logstash alertmanager

	open-grafana:
		open http://localhost:3000

	open-kibana:
		open http://localhost:5601

	open-prometheus:
		open http://localhost:9090

	# ============================================
	# Utility Targets
	# ============================================
	logs:
		$(DOCKER_COMPOSE) logs -f --tail=100

	logs-gateway:
		$(DOCKER_COMPOSE) logs -f --tail=100 payment-gateway

	logs-chaos:
		$(DOCKER_COMPOSE) logs -f --tail=100 chaos-monkey

	status:
		$(DOCKER_COMPOSE) ps

	health-check:
		@echo "🏥 Checking service health..."
		@curl -s http://localhost:8080/api/v1/health | jq .
		@curl -s http://localhost:8080/api/v1/health/providers | jq .

	clean:
		@echo "🧹 Cleaning up..."
		$(DOCKER_COMPOSE) down -v --remove-orphans
		./mvnw clean

	seed-data:
		@echo "🌱 Seeding test data..."
		./scripts/seed-payments.sh

	generate-traffic:
		@echo "🚦 Generating synthetic traffic..."
		./scripts/generate-traffic.sh $(CHAOS_RATE)

	help:
		@echo "╔══════════════════════════════════════════════════════════════╗"
		@echo "║       Payment Chaos Engineering - Available Commands         ║"
		@echo "╠══════════════════════════════════════════════════════════════╣"
		@echo "║  Build:                                                      ║"
		@echo "║    make build              - Build all services              ║"
		@echo "║    make docker-build       - Build Docker images             ║"
		@echo "║                                                              ║"
		@echo "║  Deploy:                                                     ║"
		@echo "║    make deploy-local       - Deploy locally with Docker      ║"
		@echo "║    make deploy-aws         - Deploy to AWS Free Tier         ║"
		@echo "║                                                              ║"
		@echo "║  Testing:                                                    ║"
		@echo "║    make test               - Run unit tests                  ║"
		@echo "║    make test-chaos         - Run chaos engineering tests     ║"
		@echo "║    make test-resilience    - Run resilience tests            ║"
		@echo "║    make load-test          - Run k6 load tests               ║"
		@echo "║                                                              ║"
		@echo "║  Chaos:                                                      ║"
		@echo "║    make chaos-start        - Start chaos mode                ║"
		@echo "║    make chaos-stop         - Stop chaos mode                 ║"
		@echo "║    make chaos-report       - View chaos report               ║"
		@echo "║    make chaos-experiment-failover - Test region failover     ║"
		@echo "║                                                              ║"
		@echo "║  Monitoring:                                                 ║"
		@echo "║    make monitoring-up      - Start monitoring stack          ║"
		@echo "║    make open-grafana       - Open Grafana dashboard          ║"
		@echo "╚══════════════════════════════════════════════════════════════╝"