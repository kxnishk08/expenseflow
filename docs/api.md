# API

`GET /api/auth/csrf` is public and returns `{token,headerName,parameterName}` while establishing a session. `POST /api/auth/login` uses form fields `username,password` and CSRF header; success 204, invalid credentials 401 JSON. Fetch a fresh token after login. `POST /api/auth/logout` requires CSRF, returns 204 and invalidates the session. `GET /api/me` returns ID/email/displayName/role. `GET /actuator/health` is public with minimal detail; metrics are finance-only.

PowerShell session walkthrough:

```powershell
$s=New-Object Microsoft.PowerShell.Commands.WebRequestSession
$c=Invoke-RestMethod http://localhost:8080/api/auth/csrf -WebSession $s
Invoke-WebRequest http://localhost:8080/api/auth/login -Method Post -WebSession $s -Headers @{$c.headerName=$c.token} -Body @{username='alice@example.test';password=$env:DEMO_ALICE_PASSWORD} -UseBasicParsing
$c=Invoke-RestMethod http://localhost:8080/api/auth/csrf -WebSession $s
Invoke-RestMethod http://localhost:8080/api/reports -Method Post -WebSession $s -Headers @{$c.headerName=$c.token} -ContentType application/json -Body '{"title":"Training trip"}'
```

All commands reject unknown fields. Mutation `expectedRevision` is mandatory, nonnegative and increments once. Lists use `page=0,size=20`, maximum size 100. Report UUIDs are not authorization tokens.

| Route | Contract |
|---|---|
| GET /api/policies/current | Any authenticated: seeded limits/date/total rules |
| POST /api/reports | Employee `{title}`, 201 + Location, revision 0 |
| GET /api/reports/mine | Own page, optional state |
| GET /api/reports/{id} | Owner detail including drafts, submissions, events, reimbursement |
| PATCH /api/reports/{id} | DRAFT `{expectedRevision,title}` |
| POST /api/reports/{id}/items | DRAFT `{expectedRevision,category,expenseDate,merchant,description,amount,receiptReference}`, 201 + Location |
| PUT /api/reports/{id}/items/{itemId} | Complete replacement, same item body |
| DELETE /api/reports/{id}/items/{itemId}?expectedRevision=N | DRAFT, `{reportId,revision}` |
| POST /api/reports/{id}/submit | `{expectedRevision}` -> frozen submission |
| POST /api/reports/{id}/withdraw | `{expectedRevision,comment?}` |
| POST /api/reports/{id}/reopen | RETURNED `{expectedRevision}` |
| GET /api/reports/{id}/history | Own paginated events |
| GET /api/manager/reports | Current assigned SUBMITTED queue |
| GET /api/manager/reports/{id} | Current assigned immutable submissions/events |
| GET /api/manager/reports/{id}/submissions | Only historically assigned snapshots/events |
| POST /api/manager/reports/{id}/decision | `{expectedRevision,decision,comment?}`, APPROVE/REJECT/RETURN |
| GET /api/finance/reports | APPROVED/FINANCE_ACCEPTED queue, optional eligible state |
| GET /api/finance/reports/{id} | Current eligible immutable snapshot/events |
| GET /api/finance/reports/{id}/returned-submissions | Actor's own returned snapshots/events |
| POST /api/finance/reports/{id}/decision | ACCEPT/RETURN, same decision shape |
| POST /api/finance/reports/{id}/reimbursement | `{expectedRevision,reference}`, 201 `{reimbursement,reportRevision}` |
| GET /api/manager/summary?from=YYYY-MM-DD&to=YYYY-MM-DD | Own submission revision counts/amounts by manager outcome |
| GET /api/finance/summary?from=YYYY-MM-DD&to=YYYY-MM-DD | Current workflow and recorded reimbursements by category |

Item result: `{item:{id,category,expenseDate,merchant,description,amount,receiptReference,appliedCap,receiptRequired},reportRevision}`. Applied policy fields are null for draft items and populated on submitted snapshots. Reviewer result: `{reportId,state,revision,submissions,events,reimbursement}`. Reimbursement historical visibility is deliberately bounded. Amounts are INR decimals; category reimbursement counts are distinct reimbursements in each category, and cannot be summed across categories for a unique overall count.

Errors: `{code,message,requestId,fieldErrors:[{field,message}]}` with `X-Request-Id` header. 400 invalid shape/precision/query; 401 authentication; 403 role/CSRF; 404 hidden/missing; 409 stale revision, invalid state, policy violation, no manager, draft cap, duplicate reference; 503 transient contention; 500 unexpected failure. Codes include `STALE_REPORT_REVISION`, `INVALID_REPORT_STATE`, `POLICY_VIOLATION`, `NO_ASSIGNED_MANAGER`, `DRAFT_LIMIT_REACHED`, `DUPLICATE_REIMBURSEMENT_REFERENCE`, `RETRYABLE_CONFLICT`.
