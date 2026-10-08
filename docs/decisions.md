# Decisions

Report mutations lock the report and compare the supplied revision, then increment exactly once. New-draft admission first locks the employee with `FOR NO KEY UPDATE` and counts DRAFT/RETURNED reports; other mutations do not lock the employee. Lock order is employee admission, report, children. Review returns may raise the draft count above ten; only new admission is blocked.

`DRAFT -> SUBMITTED -> APPROVED -> FINANCE_ACCEPTED -> REIMBURSEMENT_RECORDED`. Managers may RETURN/REJECT from SUBMITTED; employees may WITHDRAW from SUBMITTED. Finance may RETURN from APPROVED. RETURNED explicitly reopens to DRAFT. Every state transition appends one event. Draft item/title edits increment revision but do not invent state transitions.

Policy `POLICY-2026-01` is immutable through the API. INR decimal amounts cannot be silently rounded: request precision is at most two decimal places. Submission accepts 1–50 items, positive total at most 25,000, category caps TRAVEL 2,000 / MEALS 750 / OFFICE_SUPPLIES 5,000, receipt labels and dates from today minus 90 through today. Business date is Asia/Kolkata; summary ranges are half-open and at most 366 days. Drafts permit temporarily over-cap or out-of-date items to be corrected; submission revalidates them.

Each submission snapshots approver, policy, title, item content and applied rules. Snapshot source item IDs have no restrictive FK so draft deletion preserves history. Reviewer responses never contain current mutable draft items. Historical audit access filters snapshots and events by the reviewer's assignment/action; reimbursement data is withheld in historical filtered views to avoid leaking a future submission.

After an ambiguous mutation response, fetch the report/history and inspect the revision/state. Do not blindly increment expectedRevision and retry. Reimbursement reference normalization is ASCII uppercase, trim, 8–64 letters/digits/hyphens; report/submission/reference uniqueness is enforced in PostgreSQL. A duplicate reference failure is translated after rollback, with the second report unchanged.

Transactions set local lock timeout 3 seconds and statement timeout 10 seconds. No remote calls or automatic arbitrary retries. Contention returns 503; stale/business conflicts return 409. Logs contain IDs, operations, transitions, duration and diagnostic exceptions, never passwords, cookies, tokens or descriptions.

Versions verified against [Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) and [Boot dependency coordinates](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html). Boot 4's webmvc/security/flyway starters are used rather than copied Boot 3 imports. Maven wrapper 3.3.4 pins Maven 3.9.9; PostgreSQL image is 17.6-alpine. The installed PostgreSQL 18 service was not used for the acceptance suite.
