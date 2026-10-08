# ExpenseFlow

Java backend portfolio project for INR expense drafts, policy validation, assigned-manager decisions, finance review and manual reimbursement tracking. Reimbursement records are entered references; this application does not transfer money. Receipt references are text labels and do not establish receipt authenticity.

Employees own editable drafts. Submission freezes title, items, total, policy rules and assigned approver. Managers approve, return or reject their assigned submissions. Finance accepts or returns approved submissions, then records one unique reimbursement reference. Returns can be reopened and resubmitted without replacing history. Withdrawn, rejected and reimbursed reports are terminal.

Java 21, Spring Boot 4.1.1, Spring MVC, Security sessions, Spring Data JPA/Hibernate, PostgreSQL 17.6, Flyway, Maven 3.9.9, JUnit/AssertJ, Testcontainers 2.0.5 and Actuator. Boot manages framework versions. There is one process and one database, independent of StockHold. No frontend, receipt uploads, external payments, public registration or personnel management API.

## Run locally

Install JDK 21 and Docker with Compose. Set `JAVA_HOME` to JDK 21. Copy `.env.example` to `.env` and replace all placeholders; demo passwords need at least 12 characters and at most 72 UTF-8 bytes. Never commit `.env`.

```powershell
Copy-Item .env.example .env
.\mvnw.cmd verify
docker compose up --build -d
Invoke-RestMethod http://localhost:8080/actuator/health
```

Unix build: `./mvnw verify`. `verify` runs unit tests and Failsafe `*IT` tests against real PostgreSQL. With no Docker, explicitly set `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD` to a disposable `*_test` PostgreSQL database. Tests truncate business tables. They never silently skip when Docker is unavailable. See [testing](docs/testing.md).

For the PowerShell demo, export the five `DEMO_*_PASSWORD` values from your local configuration into your shell environment, then run `./scripts/demo.ps1`. It verifies the two-submission INR 1,050 workflow, unassigned-manager denial, stale decision, duplicate reimbursement and policy failure. Each run uses a fresh reference. Use synthetic data only.

Compose binds only localhost port 8080; PostgreSQL is private to the Compose network. Its named volume retains data across application restarts. The dev profile alone disables Secure cookies for local HTTP. Production uses Secure/HttpOnly/SameSite=Lax cookies and has no demo provisioning. In-memory sessions expire after 30 minutes idle; application restart logs users out.

## Design and evidence

- [Architecture and ER diagram](docs/architecture.md)
- [API and login/CSRF examples](docs/api.md)
- [Decisions, invariants and retries](docs/decisions.md)
- [Testing and actual verification](docs/testing.md)
- [Restart, backup, restore and migration runbook](docs/runbook.md)

The row lock plus explicit `expectedRevision` check protects every aggregate mutation, including item edits. This is not JPA optimistic locking. Owner reads and reviewer reads have distinct DTOs. Reviewer queries select authorized immutable submissions, avoiding disclosure of mutable drafts. SQL summaries aggregate submissions and reimbursement items separately to avoid multiplying totals.

Core evidence: `WorkflowIT` exercises PostgreSQL transactions, concurrency, authorization, immutable history and arithmetic; `PolicyTest` covers exact caps and date/precision boundaries; `scripts/verify-demo.ps1` exercises a real running JVM, restart and backup restore. See the testing document for executed results and runtime limitations.

Possible resume statement after reviewing the evidence: “Built a Spring Boot expense approval backend with employee/manager/finance authorization, policy validation and immutable submission history.” Do not describe it as business-ready, audited, payment processing or a hardened public service.
