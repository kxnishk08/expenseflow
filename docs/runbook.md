# Runbook

Compose: `docker compose up --build -d`; inspect `docker compose logs app`; query `/actuator/health`. Database waits on health before application starts. `docker compose restart app` retains business rows and invalidates in-memory sessions. Do not use `down -v` if you want to retain data. Development accounts are provisioned only with both dev profile and `DEMO_PROVISION=true`; existing users are preserved and credentials are not silently reset.

For a native demo set DB_URL/DB_USER/DB_PASSWORD, SPRING_PROFILES_ACTIVE=dev, DEMO_PROVISION=true and the five DEMO_*_PASSWORD environment values. Run `java -jar target/expenseflow-1.0.0.jar`; use `scripts/demo.ps1`. Production: omit dev/demo provisioning, terminate HTTPS, retain secure cookies, restrict demo access, provision users/database rights separately. Migration and runtime credentials may be separate; Flyway needs DDL permissions and runtime needs only required SELECT/INSERT/UPDATE/DELETE permissions. This local baseline uses one owner account for simplicity.

Backup with Compose:

```powershell
docker compose exec -T db pg_dump -U expenseflow -d expenseflow -Fc -f /tmp/expenseflow.dump
docker compose cp db:/tmp/expenseflow.dump ./expenseflow.dump
docker compose exec -T db createdb -U expenseflow expenseflow_restore
docker compose cp ./expenseflow.dump db:/tmp/expenseflow.dump
docker compose exec -T db pg_restore -U expenseflow -d expenseflow_restore --exit-on-error /tmp/expenseflow.dump
docker compose exec -T db psql -U expenseflow -d expenseflow_restore -c 'select count(*) from report_submissions;'
```

Native automated proof: `./scripts/verify-demo.ps1 -PostgresBin <path-to-pg17-bin> -JavaHome <jdk21>` starts the real JVM, runs the demo, restarts it, compares counts, dumps and restores to a uniquely named isolated database, then verifies a reimbursement/submission/two-item relationship and INR 1,050 arithmetic. It preserves the demo/restore databases for inspection and stops only its own JVM. Use PGPASSWORD for authenticated PostgreSQL; the workspace-only verification cluster uses loopback trust and is not a production configuration.

Flyway owns schema; Hibernate validates it. Never edit an applied migration. Add a new migration for changes and back up first. An old binary may fail against a new schema; reverting the Docker image does not undo migrations. Restore a verified backup or implement a tested forward correction. Check unique references, submission/event relationships and representative totals after restore. Health and diagnostic logs are not the authoritative business history.

Limitations: one application instance, in-memory sessions, fixed manager assignment/no management API, immutable seeded policy/no policy API, receipt text labels, manual tracking only, no receipt storage, public sign-up, account recovery, abuse controls or financial settlement. No performance or scale claims.
