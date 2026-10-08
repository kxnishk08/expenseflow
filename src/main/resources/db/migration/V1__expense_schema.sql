CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL CONSTRAINT users_email_unique UNIQUE,
 display_name VARCHAR(120) NOT NULL, password_hash VARCHAR(255) NOT NULL,
 role VARCHAR(20) NOT NULL CHECK(role IN ('EMPLOYEE','MANAGER','FINANCE')),
 active BOOLEAN NOT NULL, manager_id UUID REFERENCES users(id), created_at TIMESTAMPTZ NOT NULL,
 CHECK(email = lower(btrim(email))), CHECK(length(btrim(display_name)) BETWEEN 1 AND 120)
);
CREATE INDEX users_manager_idx ON users(manager_id);
CREATE TABLE expense_policies(id UUID PRIMARY KEY, version VARCHAR(40) NOT NULL UNIQUE, active BOOLEAN NOT NULL);
CREATE UNIQUE INDEX one_active_policy ON expense_policies(active) WHERE active;
CREATE TABLE policy_category_limits(
 policy_id UUID REFERENCES expense_policies(id), category VARCHAR(30) CHECK(category IN ('TRAVEL','MEALS','OFFICE_SUPPLIES')),
 per_item_limit NUMERIC(12,2) NOT NULL CHECK(per_item_limit > 0), receipt_required BOOLEAN NOT NULL,
 PRIMARY KEY(policy_id,category)
);
INSERT INTO expense_policies VALUES ('00000000-0000-0000-0000-000000000001','POLICY-2026-01',true);
INSERT INTO policy_category_limits VALUES
 ('00000000-0000-0000-0000-000000000001','TRAVEL',2000,true),
 ('00000000-0000-0000-0000-000000000001','MEALS',750,true),
 ('00000000-0000-0000-0000-000000000001','OFFICE_SUPPLIES',5000,true);
CREATE TABLE expense_reports(
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id), title VARCHAR(120) NOT NULL CHECK(length(btrim(title)) BETWEEN 1 AND 120),
 state VARCHAR(30) NOT NULL CHECK(state IN ('DRAFT','SUBMITTED','APPROVED','FINANCE_ACCEPTED','REIMBURSEMENT_RECORDED','RETURNED','REJECTED','WITHDRAWN')),
 revision BIGINT NOT NULL DEFAULT 0 CHECK(revision>=0), current_submission_number INTEGER NOT NULL DEFAULT 0 CHECK(current_submission_number>=0),
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX reports_owner_idx ON expense_reports(owner_id,created_at,id);
CREATE INDEX reports_state_idx ON expense_reports(state,updated_at,id);
CREATE TABLE expense_items(
 id UUID PRIMARY KEY, report_id UUID NOT NULL REFERENCES expense_reports(id),
 category VARCHAR(30) NOT NULL CHECK(category IN ('TRAVEL','MEALS','OFFICE_SUPPLIES')), expense_date DATE NOT NULL,
 merchant VARCHAR(120) NOT NULL CHECK(length(btrim(merchant))>=1), description VARCHAR(500) NOT NULL CHECK(length(btrim(description))>=1),
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), receipt_reference VARCHAR(100) NOT NULL CHECK(length(btrim(receipt_reference))>=1)
);
CREATE INDEX items_report_idx ON expense_items(report_id,id);
CREATE TABLE report_submissions(
 id UUID PRIMARY KEY, report_id UUID NOT NULL REFERENCES expense_reports(id), submission_number INTEGER NOT NULL CHECK(submission_number>0),
 approver_id UUID NOT NULL REFERENCES users(id), policy_id UUID NOT NULL REFERENCES expense_policies(id), title_snapshot VARCHAR(120) NOT NULL,
 total NUMERIC(12,2) NOT NULL CHECK(total>0 AND total<=25000), submitted_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT submission_number_unique UNIQUE(report_id,submission_number)
);
CREATE INDEX submissions_approver_idx ON report_submissions(approver_id,submitted_at,id);
CREATE TABLE submitted_expense_items(
 id UUID PRIMARY KEY, submission_id UUID NOT NULL REFERENCES report_submissions(id), source_item_id UUID NOT NULL,
 category VARCHAR(30) NOT NULL CHECK(category IN ('TRAVEL','MEALS','OFFICE_SUPPLIES')), expense_date DATE NOT NULL,
 merchant VARCHAR(120) NOT NULL, description VARCHAR(500) NOT NULL, amount NUMERIC(12,2) NOT NULL CHECK(amount>0),
 receipt_reference VARCHAR(100) NOT NULL, applied_cap NUMERIC(12,2) NOT NULL CHECK(applied_cap>0), receipt_required BOOLEAN NOT NULL,
 UNIQUE(submission_id,source_item_id)
);
CREATE INDEX snapshots_submission_idx ON submitted_expense_items(submission_id,id);
CREATE TABLE report_events(
 id UUID PRIMARY KEY, report_id UUID NOT NULL REFERENCES expense_reports(id), submission_id UUID REFERENCES report_submissions(id),
 actor_id UUID NOT NULL REFERENCES users(id), event_type VARCHAR(40) NOT NULL, from_state VARCHAR(30), to_state VARCHAR(30) NOT NULL,
 comment VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX events_report_idx ON report_events(report_id,created_at,id);
CREATE INDEX events_submission_idx ON report_events(submission_id,event_type);
CREATE TABLE reimbursements(
 id UUID PRIMARY KEY, report_id UUID NOT NULL REFERENCES expense_reports(id) UNIQUE, submission_id UUID NOT NULL REFERENCES report_submissions(id) UNIQUE,
 recorded_by UUID NOT NULL REFERENCES users(id), reference VARCHAR(64) NOT NULL CONSTRAINT reimbursement_reference_unique UNIQUE,
 amount NUMERIC(12,2) NOT NULL CHECK(amount>0), currency VARCHAR(3) NOT NULL CHECK(currency='INR'), recorded_at TIMESTAMPTZ NOT NULL,
 CHECK(reference ~ '^[A-Z0-9-]{8,64}$')
);
CREATE INDEX reimbursements_date_idx ON reimbursements(recorded_at,id);
