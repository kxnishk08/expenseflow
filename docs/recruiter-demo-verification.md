# Recruiter demo verification

Verified locally on 2026-10-09 after adding the hosted-demo configuration.

- `mvnw verify`: 8 unit + 35 PostgreSQL integration tests passed, zero failures/errors/skips. The new public-demo profile tests verify anonymous static page access, protected API authentication, CSRF enforcement, deliberately public disposable credentials, no-store response, Secure cookie configuration and a three-connection pool.
- Rebuilt packaged JVM started with `public-demo`, synthetic provisioning, fresh PostgreSQL 17.6 database, and Render's proposed 256 MB heap settings.
- Actual in-app browser button ran the return/reopen/resubmit/approval/reimbursement workflow; two immutable snapshots and INR 1,050 were verified. Stale review and duplicate reimbursement returned 409 as expected.
- JavaScript parsed with `node --check`; updated Java compiled; Git whitespace checks passed.
- Local HTTP browser testing explicitly overrode Secure cookies to false on loopback only. The committed hosted profile retains Secure cookies; live HTTPS cookie behavior still needs verification on Render.

Raw logs are ignored `target/deployment-verification.log`. Local screenshots are outside the repositories under the workspace's ignored `.local` directory.

Still pending: GitHub publication, Render Docker build/startup, Neon TLS connectivity, public URL workflow checks, hosted restart/persistence checks. No public deployment is claimed.
