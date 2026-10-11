# Capstone Project Backlog

This backlog translates the capstone scope into 15 small, demonstrable stories across Layers L1–L6. Estimates use the brief's scale: S (up to 1 hour), M (up to 2 hours), L (up to 4 hours). Owners are first owners; the team can pair and review through PRs. Stories describe acceptance criteria and do not claim that the work is already complete.

## L1 — Product, Gateway, and Keycloak (G1)

### L1-01 — Browse the public product catalogue
**Layer:** L1 · **Gate:** G1 · **Owner:** Eslam · **Estimate:** M (≤2h) · **Priority:** Must

As a customer, I want to browse products without signing in, so I can discover what is available.

**Definition of Done:** `GET /api/v1/products` returns a paginated product response without a token; a request for a product by ID returns its details; automated tests cover successful and missing-product responses.
**Depends on:** L0 running; product-service skeleton.

### L1-02 — Protect administrative product changes
**Layer:** L1 · **Gate:** G1 · **Owner:** Ayman · **Estimate:** M (≤2h) · **Priority:** Must

As an administrator, I want product write operations restricted to the ADMIN role, so catalogue changes are authorized.

**Definition of Done:** POST/PUT/DELETE product requests without a valid token receive 401; an authenticated non-admin receives 403; an ADMIN can perform the operation; automated tests demonstrate these outcomes.
**Depends on:** L1-01; Keycloak realm and Gateway security configuration.

## L2 — Inventory, Payment, and Order synchronous path (G1)

### L2-01 — Check stock before accepting an order
**Layer:** L2 · **Gate:** G1 · **Owner:** Ali · **Estimate:** M (≤2h) · **Priority:** Must

As a customer, I want an order checked against available stock before acceptance, so unavailable items are rejected without charging payment.

**Definition of Done:** Order Service calls the Inventory stock-check endpoint; an unavailable quantity returns a rejection and does not invoke Payment; tests cover available and unavailable stock.
**Depends on:** L1-01; Inventory stock-check API.

### L2-02 — Accept an order and process payment idempotently
**Layer:** L2 · **Gate:** G1 · **Owner:** Eslam (Order) and Ayman (Payment) · **Estimate:** L (≤4h) · **Priority:** Must

As a customer, I want an accepted order to receive an ID and PENDING status while payment is processed safely, so retries cannot charge me twice.

**Definition of Done:** A valid order response includes `orderId` and `PENDING`; repeating payment with the same order idempotency key does not create a second charge; automated tests verify the response and duplicate-request behavior.
**Depends on:** L2-01; Payment persistence and idempotency key handling.

## L3 — Saga, Outbox, and Notification (G2)

### L3-01 — Complete the order Saga happy path
**Layer:** L3 · **Gate:** G2 · **Owner:** Eslam · **Estimate:** L (≤4h) · **Priority:** Must

As a customer, I want a successfully paid order to become CONFIRMED, so I can see that it completed.

**Definition of Done:** An order event is persisted to the outbox with the order update in one transaction; the event is published; successful inventory reservation and payment lead to CONFIRMED; an integration or demonstrable end-to-end check verifies the flow.
**Depends on:** L2-02; Kafka topics and event contracts.

### L3-02 — Compensate when payment fails and notify the customer
**Layer:** L3 · **Gate:** G2 · **Owner:** Ali · **Estimate:** L (≤4h) · **Priority:** Must

As a customer, I want a failed payment to cancel my order and release reserved stock, so inventory is not held for a failed purchase.

**Definition of Done:** Payment failure results in CANCELLED, Inventory releases the reservation, and Notification handles the cancellation notice; duplicate event delivery does not repeat the state change; a failure-path test or demo proves the sequence.
**Depends on:** L3-01; inventory release and notification consumers.

## L4 — Containers, CI, Kubernetes, Helm, and GitOps (G2)

### L4-01 — Build and publish service images through CI
**Layer:** L4 · **Gate:** G2 · **Owner:** Ayman · **Estimate:** M (≤2h) · **Priority:** Must

As a developer, I want CI to test and publish service images, so deployments use reproducible builds.

**Definition of Done:** GitHub Actions runs the test gate before image build/push; images use a commit SHA tag; a non-root container configuration is present; a successful CI run provides evidence.
**Depends on:** L2-02; Dockerfiles and repository CI configuration.

### L4-02 — Deploy the platform to Kubernetes with Helm and ArgoCD
**Layer:** L4 · **Gate:** G2 · **Owner:** Ali · **Estimate:** L (≤4h) · **Priority:** Must

As an operator, I want the platform deployable to Kubernetes with health checks and managed configuration, so I can run and monitor it in a cluster.

**Definition of Done:** Required service charts and deployment assets install successfully; readiness/liveness probes, Kubernetes Secrets, and least-privilege ServiceAccounts are configured; ArgoCD reports the application Synced and Healthy.
**Depends on:** L4-01; container registry images and Kubernetes cluster access.

## L5 — Observability, load testing, and optimization (G3)

### L5-01 — Trace requests across HTTP and Kafka
**Layer:** L5 · **Gate:** G3 · **Owner:** Eslam · **Estimate:** M (≤2h) · **Priority:** Must

As a developer, I want a trace ID to follow a request across HTTP and Kafka, so I can diagnose a distributed order flow.

**Definition of Done:** A demonstrated order trace is visible across the relevant HTTP and Kafka steps in Zipkin; service logs include the trace ID.
**Depends on:** L3-01; tracing and Zipkin configuration.

### L5-02 — Measure and improve platform performance
**Layer:** L5 · **Gate:** G3 · **Owner:** Ayman · **Estimate:** L (≤4h) · **Priority:** Must

As a team, we want smoke, load, and stress measurements with a documented bottleneck and fix, so performance claims are supported by evidence.

**Definition of Done:** Existing k6 scripts run against a stated environment; actual P95/P99 latency, throughput, and error rate are recorded; one bottleneck and its fix are described in `docs/PERFORMANCE-REPORT.md`; no results are estimated or fabricated.
**Depends on:** L4-02; running environment and k6 scripts.

## L6 — Bonus B2, final documentation, and demo (G4)

### L6-01 — Build an idempotent order analytics projection (Bonus B2)
**Layer:** L6 · **Gate:** G4 · **Owner:** Ali · **Estimate:** L (≤4h) · **Priority:** Must — Bonus B2 Core

As an operations user, I want order events projected into analytics data, so I can inspect order volume, revenue, and Saga failures without querying service databases.

**Definition of Done:** The projection records orders per status per hour, revenue per hour, and cancelled ratio from order events; replaying a duplicate event does not double count; automated tests verify projection updates and duplicate handling.
**Depends on:** L3-01; stable order event schema and analytics storage decision recorded in the ADD.

### L6-02 — Expose the Bonus B2 analytics summary
**Layer:** L6 · **Gate:** G4 · **Owner:** Eslam · **Estimate:** M (≤2h) · **Priority:** Must — Bonus B2 Core

As an administrator, I want an analytics summary endpoint, so I can review current order and Saga metrics.

**Definition of Done:** `GET /api/v1/analytics/summary` returns the defined analytics measures; access is restricted to ADMIN; tests verify the response and access rules.
**Depends on:** L6-01; Gateway route and authorization configuration.

### L6-03 — Visualize Bonus B2 metrics in Grafana
**Layer:** L6 · **Gate:** G4 · **Owner:** Ayman · **Estimate:** M (≤2h) · **Priority:** Must — Bonus B2 Core

As an operations user, I want an analytics dashboard, so I can monitor order and Saga health at a glance.

**Definition of Done:** A Grafana dashboard has at least three panels: orders per minute by status, revenue, and Saga failure rate; panels display data from the analytics implementation and are included in the demo.
**Depends on:** L6-01; metrics/data source available to Grafana.

### L6-04 — Publish synchronized project documentation
**Layer:** L6 · **Gate:** G4 · **Owner:** Ali · **Estimate:** M (≤2h) · **Priority:** Must

As a reviewer, I want the ADD, architecture diagrams, README, and final report to agree with the repository, so I can understand and run the delivered platform.

**Definition of Done:** Required ADD sections and architecture diagrams are present; README run instructions match the current services and deployment; final report explains architecture, Saga/Outbox, security, testing, and Bonus B2; reviewed documentation matches the implementation.
**Depends on:** L3-02; L4-02; L5-02; L6-01.

### L6-05 — Rehearse the final demo and team Q&A
**Layer:** L6 · **Gate:** G4 · **Owner:** Ayman · **Estimate:** M (≤2h) · **Priority:** Must

As a team, we want a rehearsed end-to-end demo and shared Q&A preparation, so every member can explain the system and its decisions.

**Definition of Done:** A 15-minute runbook demonstrates authentication, product browsing, order happy path, payment failure and compensation, Bonus B2, tracing, deployment, and actual performance evidence; fallback evidence is prepared; all team members can answer questions about the major services and decisions.
**Depends on:** L3-02; L4-02; L5-01; L5-02; L6-03; L6-04.

## Scope notes

- The brief makes the primary Bonus mandatory for the capstone; this backlog includes Bonus B2 Core as L6-01 through L6-03.
- Stories are acceptance criteria, not a status report. Update owners, estimates, dependencies, and completion evidence with the team as work is reviewed.
- Keep stories within the brief's four-hour maximum. If implementation reveals a larger task, split it before assigning it.
