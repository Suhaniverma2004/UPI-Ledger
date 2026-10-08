# UPI-Ledger

UPI-Ledger is a production-oriented, UPI-inspired internal payment ledger simulation.

It is **not** connected to NPCI, banks, real UPI infrastructure, or external payment networks. It is also intentionally separate from the PayGuard fraud/risk project.

## V1 Foundation

This version establishes:

- Java 21
- Spring Boot 3.5.x
- Maven
- Spring Data JPA
- PostgreSQL 16
- Redis 7
- Apache Kafka
- Flyway
- Spring Boot Actuator
- JUnit 5
- Testcontainers
- ArchUnit
- Docker Compose

V1 deliberately does not contain payment business logic, ledger posting logic, Kafka consumers/producers, Redis business logic, or reconciliation logic.

## Planned architecture

The application is a modular monolith with module roots:

- `common`
- `api`
- `payments`
- `ledger`
- `accounts`
- `idempotency`
- `eventing`
- `reconciliation`
- `audit`
- `config`

PostgreSQL will remain the financial source of truth.

## Local development

Prerequisites:

- JDK 21
- Maven 3.9+
- Docker Desktop

Start infrastructure:

```bash
docker compose up -d
```

Run tests:

```bash
mvn test
```

Build:

```bash
mvn clean verify
```
### V1 test behavior

The V1 test suite is intentionally infrastructure-free. `mvn clean test` does not require PostgreSQL, Redis, Kafka, or Docker. Integration tests that exercise the real PostgreSQL schema and infrastructure will be introduced in later implementation stages using Testcontainers.


## V2 — Database & Financial Schema

V2 adds the PostgreSQL/Flyway financial data model: accounts, balance projections, authorization holds, payment transactions, immutable ledger entries, transaction events, idempotency records, transactional outbox records, and reconciliation records. It also adds a deferred PostgreSQL constraint trigger enforcing the double-entry invariant per `posting_id` and currency. Business workflows are intentionally deferred to later versions.

## V3 — Double-Entry Ledger Engine

V3 adds the core accounting engine. Ledger postings are represented as balanced debit/credit lines under a single `posting_id`. The posting service executes inside a database transaction, locks account-balance rows in deterministic order, updates the materialized balance projection, and persists ledger entries atomically.

Wallet balances use positive customer-funds semantics: a debit reduces the wallet balance and a credit increases it. The PostgreSQL deferred constraint trigger from V2 remains the database-level backstop for the double-entry invariant.

V3 does not implement the payment lifecycle, Kafka publishing, Redis locking, or REST APIs yet. Those arrive in later versions.
