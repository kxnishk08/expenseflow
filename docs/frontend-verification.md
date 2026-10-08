# Frontend verification

Verified locally on 2026-10-09 using packaged Java 21 applications, the public-demo profile, and isolated PostgreSQL 17.6 databases.

## Automated checks

- 8 unit + 35 integration tests passed with zero failures/errors/skips (`target/frontend-verification.log`, ignored).
- DemoProfileIT now checks anonymous access to the workspace JavaScript/CSS and retained API walkthrough, plus authentication, CSRF and hosted cookie configuration.
- `node --check` passed for the workspace JavaScript. Git whitespace checks passed.
- Final Java artifact packaged successfully after the browser-driven fixes. Subsequent edits affected static assets/documentation only.

## Actual browser checks

- Employee sign-in, report creation, travel/meal item entry and editing.
- An INR 9,000 meal was rejected at submission. The draft remained usable; correction to INR 450 succeeded.
- Manager return with required comment; employee reopen, clarified item description and resubmission.
- Manager approval, finance acceptance and INR 1,050 reimbursement reference entry.
- Employee history retained two immutable submission versions and the review comments.
- Policy page displays the current version and real category caps: travel 2,000, meals 750, office supplies 5,000 INR.
- Rebuilt-app restart invalidated the session; signing in again retrieved the retained claim.

Desktop views were visually inspected, and narrow-screen layouts were checked at 390 px with no page-level horizontal overflow. Tables scroll within their containers. Modal fields received focus on opening. Updated role switching and friendly error text were checked in the live workspace.

Local HTTP testing explicitly disables Secure cookies only on loopback. Committed hosted configuration retains Secure cookies. Live Render/Neon deployment, cloud TLS and public URL checks remain pending.

Screenshots are retained in the workspace's ignored `.local` directory. The application source has no third-party frontend runtime, external font/image requests, browser-storage credentials, or separate hosting requirement.
