# microservices-pro-platform

**Microservices Course — Spring Boot & Spring Cloud, Professional Edition**
ITSharks · IT Learning & Training Center

This is the **Enterprise E-Commerce Platform** — the production-grade system built session after session across the curriculum. Every lab adds concrete capabilities to this real platform.

---

## Architecture Overview

| Module | Port | Technology Stack | Role / Responsibility |
|---|---|---|---|
| `infrastructure/eureka-server` | 8761 | Spring Cloud Netflix Eureka | Service Discovery Registry |
| `infrastructure/config-server` | 8888 | Spring Cloud Config | Centralized Configuration Server |
| `infrastructure/api-gateway` | 8080 | Spring Cloud Gateway, Reactive Redis, OAuth2 | API Entry Point, Dynamic Routing, Keycloak JWT Auth, Token-Bucket Rate Limiting |
| `services/product-service` | 8081 | Spring Boot, Spring Data JPA, Redis, CQRS | Product Catalog REST API, JPA + Redis Two-Level Cache, Command/Query Separation |
| `services/order-service` | 8082 | Spring Boot, OpenFeign, Resilience4j, Kafka, Outbox | Order Orchestration, Feign Stock Pre-Check, Choreography Saga Initiator, Transactional Outbox, Order Analytics Read Model |
| `services/payment-service` | 8083 | Spring Boot, Spring Kafka, PostgreSQL | Payment Processing, Idempotent Event Handling, Saga Participant |
| `services/inventory-service` | 8084 | Spring Boot, Spring Kafka, PostgreSQL | Synchronous Stock Checks, Idempotent Saga Commit/Compensation (Reserve & Release) |
| `services/notification-service` | 8085 | Spring Boot, Spring Kafka, PostgreSQL | Async Order Notifications with Durable Idempotency |

---

## Prerequisites

- **Java JDK 21+** (`java -version`)
- **Apache Maven 3.9+** (`mvn -version`)
- **Docker & Docker Compose** (`docker compose version`)
- **Kubernetes cluster & kubectl** (for Kubernetes deployment; e.g. Minikube, Kind, k3s, EKS, GKE, AKS)
- **curl** or **Postman / Bruno / HTTPie**

---

## Building the Platform

A root aggregator `pom.xml` links all services. You can compile and test the entire platform from the repository root:

```bash
# Run unit and integration tests across all modules
mvn test

# Package all JAR artifacts
mvn clean package -DskipTests
```

---

## Running the Platform

You can run the entire platform in three modes:
- **Mode A: Full Stack in Docker** (recommended for full system testing on a local machine)
- **Mode B: Hybrid Local Development** (infrastructure in Docker, microservices run locally via Maven)
- **Mode C: Kubernetes Cluster Deployment** (orchestrated container deployment via Kustomize or Helm)

---

### Mode A: Full Platform via Docker Compose

In this mode, Docker Compose boots all backing infrastructure, Keycloak, Prometheus, Grafana, Zipkin, and all containerized Spring microservices using multi-stage builds.

1. **Build and start all services:**
   ```bash
   docker compose up --build
   ```

   To run in the background (detached mode):
   ```bash
   docker compose up --build -d
   ```

2. **Verify running containers:**
   ```bash
   docker compose ps
   ```

3. **Stop the stack:**
   ```bash
   docker compose down
   ```

---

## Order Analytics Dashboard (Bonus Feature B2)

The platform includes an enterprise analytics subsystem for real-time order and revenue analytics:

### 1. CQRS Event-Driven Analytics Read Model
- **Consumer Group:** `order-analytics-group` listening on `order-events` independently from core order processing.
- **Idempotency:** Persistent deduplication table `analytics_processed_events` keyed on `<orderId>:<eventType>` ensures duplicate Kafka deliveries never skew metrics.
- **Read Model Tables:**
  - `order_analytics`: Order snapshot, amounts, and statuses.
  - `hourly_order_metrics`: Aggregated order counts and revenue bucketed by hour.

### 2. Admin Analytics REST Endpoint
- **Path:** `GET /api/v1/analytics/summary`
- **Security:** Protected at API Gateway — requires Keycloak `ADMIN` role (`401 Unauthorized` if unauthenticated, `403 Forbidden` if customer).
- **Payload:**
```json
{
  "totalOrders": 120,
  "confirmedOrders": 95,
  "cancelledOrders": 15,
  "pendingOrders": 10,
  "cancelledRatio": 0.125,
  "totalRevenue": 18450.00,
  "ordersByStatus": {
    "CONFIRMED": 95,
    "CANCELLED": 15,
    "PENDING": 10
  },
  "ordersPerHour": [
    { "hour": "2026-09-29T10:00:00Z", "count": 25, "statusCounts": { "CONFIRMED": 20, "CANCELLED": 3, "PENDING": 2 } }
  ],
  "revenuePerHour": [
    { "hour": "2026-09-29T10:00:00Z", "revenue": 3500.00 }
  ]
}
```

### 3. Prometheus Metrics & Grafana Dashboard
- **Exposed Micrometer Metrics:**
  - `ecommerce_orders_total` (tagged by `status`)
  - `ecommerce_orders_confirmed_total`
  - `ecommerce_orders_cancelled_total`
  - `ecommerce_order_revenue_total`
  - `ecommerce_saga_failures_total` (tagged by `reason`)
- **Grafana Dashboard:** `observability/grafana/dashboards/order-analytics-dashboard.json` auto-provisioned into Grafana with panels for:
  - Orders per minute by status
  - Cumulative confirmed revenue and revenue per hour
  - Saga failure / cancellation rate
  - Total order volume breakdown

---

## Continuous Integration & Continuous Deployment (CI/CD)

The repository includes a complete GitHub Actions workflow located at `.github/workflows/ci-cd.yml` covering build, multi-module test suite, Kustomize validation, Docker image matrix build, and gated Kubernetes deployment.

---

*ITSharks · IT Learning & Training Center · microservices-pro-platform*
