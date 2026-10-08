# Testing

`./mvnw verify` / `./mvnw.cmd verify` runs policy unit tests and PostgreSQL integration tests via Maven Failsafe. Default integration runtime uses Testcontainers 2.0.5 and `postgres:17.6-alpine`; an explicit external disposable PostgreSQL override is supported. No H2 and no mocked persistence.

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:TEST_DB_URL='jdbc:postgresql://127.0.0.1:55417/expenseflow_test'
$env:TEST_DB_USER='portfolio_test'
$env:TEST_DB_PASSWORD=''
.\mvnw.cmd -B -ntp verify
```

The external URL must end in a dedicated `*_test` database. Tests truncate synthetic business rows. Never point it at production or a database you need to retain. Docker-unavailable default runs fail with a precise Testcontainers error rather than being silently skipped.

`PolicyTest`: precision, cap equality, exact 90-day cutoff, Kolkata midnight +/- one second, hand-calculated total and total cap, reference normalization, summary range and revision/state checks. `WorkflowIT`: full two-revision workflow and manually expected INR 1,050 summary; employee/manager/finance visibility; real login/CSRF; unknown fields/missing revision; policy rollback; edit eligibility; reference reuse; finance audit; injected snapshot/event/reimbursement failures; competing reviews, item-vs-submit, withdrawal-vs-approval, draft admissions and reimbursements. Races use barriers and separate committed transactions/connections; no outer rollback test transaction hides worker data. `OperationProbe` is an internal test seam, never an HTTP endpoint.

Actual results are recorded in `verification.md` after running the suite and real-JVM demo. Runtime output belongs in ignored `target/`, not source control. A README checklist is not evidence of successful execution.

Docker/Compose packaging must be exercised on a Docker-capable machine; this workstation has no Docker executable. The local PostgreSQL 17.6 tests and actual JVM demo establish database/application behavior without claiming container execution.
