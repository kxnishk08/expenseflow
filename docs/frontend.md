# Browser workspace

[Executed frontend verification](frontend-verification.md).

The Java application serves a responsive, dependency-free frontend at `/`. It uses the existing APIs, cookie sessions and CSRF tokens. No Node runtime or separate frontend hosting is needed.

- Employee: report list with filters and pagination, new reports, title edits, item CRUD, submit/withdraw/reopen, policy limits and immutable submission/activity history.
- Manager: assigned queue, snapshot review, approve/return/reject with review notes.
- Finance: eligible queue, accept/return, reimbursement reference entry.
- Hosted demo: choose a disposable role at sign-in or use the header role selector. Standard deployments retain email/password sign-in without publishing credentials.

Only the `public-demo` profile exposes intentionally disposable demo credentials. No password or session token is saved in browser storage. Role switching logs out the previous identity and obtains fresh CSRF tokens. Data displayed from the API is escaped before insertion.

Submission revision values come from the last loaded report. Rejected writes show the server message and field errors; refresh before retrying a stale edit. Forms remain open after validation failure. The backend remains the source of truth for authorization, policy and state transitions.

The original automated API walkthrough is retained at `/demo.html`. This and the interactive workspace share the existing Java deployment.
