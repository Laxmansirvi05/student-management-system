-- Reports on learning resources (FR-LIB-005, T7-06): a student flags a problem with a resource
-- (wrong file, broken PDF, wrong subject) with a short reason; an administrator resolves it.
create table app.resource_reports (
    id          bigint generated always as identity primary key,
    resource_id bigint       not null,
    student_id  bigint       not null,
    reason      varchar(500) not null,
    status      varchar(10)  not null default 'OPEN',
    created_at  timestamptz  not null default now(),
    resolved_at timestamptz,
    constraint resource_reports_resource_fkey
        foreign key (resource_id) references app.learning_resources (id) on delete cascade,
    constraint resource_reports_student_fkey
        foreign key (student_id) references app.student_profiles (id),
    constraint resource_reports_status_check check (status in ('OPEN', 'RESOLVED')),
    constraint resource_reports_resolved_check check ((status = 'RESOLVED') = (resolved_at is not null)),
    constraint resource_reports_reason_check check (length(btrim(reason)) >= 5)
);

-- One open report per student and resource: reporting the same problem twice adds nothing.
create unique index resource_reports_open_once_idx
    on app.resource_reports (resource_id, student_id) where status = 'OPEN';
create index resource_reports_status_idx on app.resource_reports (status, created_at desc);
create index resource_reports_student_idx on app.resource_reports (student_id);

alter table app.resource_reports enable row level security;

grant select, insert, update on app.resource_reports to cc_app;

create policy resource_reports_runtime_select on app.resource_reports for select to cc_app using (true);
create policy resource_reports_runtime_insert on app.resource_reports for insert to cc_app with check (true);
create policy resource_reports_runtime_update on app.resource_reports for update to cc_app using (true) with check (true);
