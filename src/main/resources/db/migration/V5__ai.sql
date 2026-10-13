-- V5: the usage log of the AI study assistant.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Design: docs/08-database-design.md sections 4.18 and 11.

-- ---------------------------------------------------------------------------
-- ai_request_logs: one row for every AI request, for the daily limits and for
-- cost. It holds numbers and labels only. The text a student sent and the text
-- the AI returned are never stored, here or anywhere else.
-- ---------------------------------------------------------------------------
create table app.ai_request_logs (
    id             bigint generated always as identity primary key,
    user_id        bigint      not null,
    feature        varchar(30) not null,
    prompt_version varchar(30) not null,
    input_chars    integer     not null,
    output_chars   integer,
    status         varchar(15) not null,
    duration_ms    integer,
    created_at     timestamptz not null default now(),
    constraint ai_request_logs_user_id_fkey foreign key (user_id) references app.users (id),
    constraint ai_request_logs_feature_check check (feature in
        ('EXPLAIN', 'SUMMARIZE', 'GENERATE_QUIZ', 'SUMMARIZE_RESOURCE', 'FLOWCHART')),
    constraint ai_request_logs_status_check
        check (status in ('SUCCESS', 'FAILED', 'TIMEOUT', 'DECLINED'))
);

create index ai_request_logs_user_created_at_idx on app.ai_request_logs (user_id, created_at);
create index ai_request_logs_created_at_idx on app.ai_request_logs (created_at);

-- Row Level Security: a backstop that admits the runtime role only.
alter table app.ai_request_logs enable row level security;

-- Append-only for the runtime role (docs/08-database-design.md 11.3): rows are
-- added and read, never changed or removed.
grant select, insert on app.ai_request_logs to cc_app;

create policy ai_request_logs_runtime_select on app.ai_request_logs for select to cc_app using (true);
create policy ai_request_logs_runtime_insert on app.ai_request_logs for insert to cc_app with check (true);

-- Supabase's API roles get nothing here. They exist only on Supabase, so the
-- revoke is skipped where a role is absent (for example in the test database).
do $$
declare
    api_role text;
begin
    foreach api_role in array array['anon', 'authenticated', 'service_role'] loop
        if exists (select 1 from pg_roles where rolname = api_role) then
            execute format('revoke all on all tables in schema app from %I', api_role);
        end if;
    end loop;
end
$$;
