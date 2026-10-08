# Free recruiter deployment

Use two independent GitHub repositories, two Render Free web services, and two Neon Free projects. Each application's tiny demo page is served by the Java process; there is no frontend service, custom domain, paid database, or keep-awake job.

## 1. Publish the repositories

Create empty GitHub repositories named `expenseflow` and `stockhold` (do not initialize another README). From the corresponding local project directory:

```powershell
git remote add origin https://github.com/YOUR_USERNAME/PROJECT_NAME.git
git push -u origin main
```

Commit the deployment changes before pushing. Do not upload the workspace root, `.local`, `.env`, database dumps, or `target`. These projects already ignore their local artifacts.

## 2. Create the databases

In Neon, choose the Free plan. Create a separate project for each app, PostgreSQL 17, with a region close to your Render service. Leave database scale-to-zero enabled. The default `neondb` database is fine: the projects have independent hosts and credentials.

Open **Connect**, select the direct (non-pooled) endpoint. Keep its hostname, database, role and password in the hosting dashboard, never Git. Direct connections preserve the session-level transaction timeout configuration and avoid adding a pooler.

Set these Render environment variables separately for each app:

| Variable | Value |
|---|---|
| DB_URL | `jdbc:postgresql://YOUR_DIRECT_NEON_HOST:5432/neondb?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&channelBinding=require&connectTimeout=30` |
| DB_USER | Neon role name |
| DB_PASSWORD | Neon role password |

The JDBC URL has no embedded username/password. Java's default truststore validates the server certificate; verify-full also checks its hostname. Do not copy a `postgresql://user:password@...` URL into DB_URL.

## 3. Deploy each app

In Render choose **New → Blueprint**, connect the corresponding GitHub repository, and use its root `render.yaml`. It explicitly sets **plan: free** and supplies the Docker runtime, health endpoint, small JVM memory settings and **public-demo** profile.

Enter the three DB variables and every prompted DEMO_*_PASSWORD value. Use disposable passwords of 12–72 ASCII characters; using the same disposable password for all roles simplifies a portfolio demo. These passwords are intentionally displayed to visitors by `/demo/config`. Never reuse a personal password. The app provisions demo identities on first boot; Flyway creates the schema automatically.

ExpenseFlow requires ALICE, BOB, M1, M2, F1 passwords. StockHold requires ALICE, BOB, STAFF passwords. Keep the two services' DB settings separate.

Do not enable the `dev` profile on Render: it disables Secure cookies for local HTTP. The public-demo profile keeps Secure/HttpOnly/SameSite cookies and handles the hosting HTTPS proxy.

## 4. Verify the public URLs

Wait for Render's build and deploy to succeed. Open each supplied HTTPS URL:

1. Open the home page and choose a demo role. The interactive workspace should load.
2. In ExpenseFlow, create and submit a claim as Employee, review it as Manager, accept and record reimbursement as Finance. Inspect the employee's submission/activity history.
3. In StockHold, add sample stock as Staff, reserve and confirm as Customer, then cancel a placed order or fulfill it as Staff.
4. Open `/demo.html` and run the automated API walkthrough. ExpenseFlow must end with two snapshots and INR 1,050 recorded once; StockHold's cancellation and fulfillment scenarios must both pass. Reload the workspace and verify session restoration.
5. After a deploy/restart, authenticate and retrieve a previous record from the API to confirm database persistence. App restart invalidates in-memory sessions.
6. Check `/actuator/health` returns UP. Anonymous `/api/me` must return 401; mutation without CSRF must return 403.
7. Put the two HTTPS demo URLs and corresponding source repository URLs in your resume/project README.

**Status:** configuration and demo implementation prepared locally. Live cloud builds, Neon TLS connectivity, Render HTTPS cookies and public smoke tests still require the signed-in accounts and must pass before calling deployment complete.

## Cost and maintenance

Render Free services sleep after 15 idle minutes and share 750 running hours/month per workspace. Two always-running apps would exceed that shared allowance; normal sparse recruiter traffic and sleep are the intended use. Cold starts can take around a minute. Do not add uptime pings.

Neon Free projects have storage/compute/network quotas; keep the Free plan and scale-to-zero. Review both providers' dashboard usage before upgrading anything. Render documents possible bandwidth overage charges; do not add a payment method or choose a paid resource for this free-only setup.

Demo accounts share synthetic state. Avoid real personal data. Partial runs can leave drafts/holds; holds expire after the worker resumes. History and completed examples remain stored. If synthetic records eventually approach the free storage quota, provision a fresh isolated demo database and redeploy; never reset any retained non-demo database. No public reset endpoint exists.

Provisioning preserves existing passwords; changing DEMO_* variables does not rotate existing account hashes. For a disposable demo rotation, use a fresh database and redeploy.

## Sources

- [Render Free limits](https://render.com/docs/free)
- [Render Blueprint reference](https://render.com/docs/blueprint-spec)
- [Neon pricing and current quotas](https://neon.com/pricing)
- [PostgreSQL JDBC SSL factory](https://jdbc.postgresql.org/documentation/publicapi/org/postgresql/ssl/DefaultJavaSSLFactory.html)
