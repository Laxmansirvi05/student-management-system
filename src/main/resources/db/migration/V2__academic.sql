-- V2: academic tables and the audit log.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Every table here gets, in this file: Row Level Security, the minimum grants for the
-- runtime role cc_app, and policies for cc_app only.
-- Design: docs/08-database-design.md sections 4.5 to 4.10, 4.17, 11 and 12.

-- ---------------------------------------------------------------------------
-- attendance_records: one row per student per class held.
-- ---------------------------------------------------------------------------
create table app.attendance_records (
    id         bigint generated always as identity primary key,
    student_id bigint      not null,
    subject_id bigint      not null,
    class_date date        not null,
    period_no  smallint    not null default 1,
    status     varchar(10) not null,
    marked_by  bigint      not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint attendance_records_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint attendance_records_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint attendance_records_marked_by_fkey foreign key (marked_by) references app.users (id),
    constraint attendance_records_class_key unique (student_id, subject_id, class_date, period_no),
    constraint attendance_records_period_no_check check (period_no between 1 and 12),
    constraint attendance_records_status_check check (status in ('PRESENT', 'ABSENT'))
);

create index attendance_records_subject_id_class_date_idx on app.attendance_records (subject_id, class_date);
create index attendance_records_marked_by_idx on app.attendance_records (marked_by);

-- ---------------------------------------------------------------------------
-- assessments: something that is marked, for example "Internal 1".
-- Students see its marks only when published is true.
-- ---------------------------------------------------------------------------
create table app.assessments (
    id              bigint generated always as identity primary key,
    subject_id      bigint        not null,
    name            varchar(100)  not null,
    assessment_type varchar(20)   not null,
    max_marks       numeric(5, 2) not null,
    held_on         date,
    academic_year   varchar(9)    not null,
    published       boolean       not null default false,
    created_at      timestamptz   not null default now(),
    constraint assessments_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint assessments_subject_id_name_academic_year_key unique (subject_id, name, academic_year),
    constraint assessments_type_check
        check (assessment_type in ('INTERNAL', 'ASSIGNMENT', 'LAB', 'SEMESTER_EXAM')),
    constraint assessments_max_marks_check check (max_marks > 0)
);

-- ---------------------------------------------------------------------------
-- marks: one student's result in one assessment. No marks when absent.
-- ---------------------------------------------------------------------------
create table app.marks (
    id             bigint generated always as identity primary key,
    student_id     bigint      not null,
    assessment_id  bigint      not null,
    marks_obtained numeric(5, 2),
    absent         boolean     not null default false,
    entered_by     bigint      not null,
    updated_at     timestamptz not null default now(),
    constraint marks_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint marks_assessment_id_fkey foreign key (assessment_id) references app.assessments (id),
    constraint marks_entered_by_fkey foreign key (entered_by) references app.users (id),
    constraint marks_student_id_assessment_id_key unique (student_id, assessment_id),
    constraint marks_marks_obtained_check check (marks_obtained >= 0),
    constraint marks_absent_or_marked_check check (absent = true or marks_obtained is not null)
);

create index marks_assessment_id_idx on app.marks (assessment_id);
create index marks_entered_by_idx on app.marks (entered_by);

-- ---------------------------------------------------------------------------
-- semester_results: the official SGPA of one semester. CGPA is not stored;
-- it is calculated in Java, weighted by credits_registered.
-- ---------------------------------------------------------------------------
create table app.semester_results (
    id                 bigint generated always as identity primary key,
    student_id         bigint        not null,
    semester           smallint      not null,
    sgpa               numeric(4, 2) not null,
    credits_registered smallint      not null,
    credits_earned     smallint      not null,
    result_status      varchar(10)   not null,
    published_at       timestamptz,
    updated_at         timestamptz   not null default now(),
    constraint semester_results_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint semester_results_student_id_semester_key unique (student_id, semester),
    constraint semester_results_semester_check check (semester between 1 and 8),
    constraint semester_results_sgpa_check check (sgpa between 0 and 10),
    constraint semester_results_credits_registered_check check (credits_registered > 0),
    constraint semester_results_credits_earned_check check (credits_earned >= 0),
    constraint semester_results_credits_earned_within_registered_check
        check (credits_earned <= credits_registered),
    constraint semester_results_status_check check (result_status in ('PASS', 'FAIL', 'WITHHELD'))
);

-- ---------------------------------------------------------------------------
-- timetable_entries: one class in the weekly timetable of a section.
-- day_of_week 1 is Monday. Overlaps within a section are rejected in Java.
-- ---------------------------------------------------------------------------
create table app.timetable_entries (
    id           bigint generated always as identity primary key,
    branch       varchar(20) not null,
    semester     smallint    not null,
    section      varchar(5)  not null,
    day_of_week  smallint    not null,
    start_time   time        not null,
    end_time     time        not null,
    subject_id   bigint      not null,
    room         varchar(30),
    faculty_name varchar(120),
    constraint timetable_entries_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint timetable_entries_semester_check check (semester between 1 and 8),
    constraint timetable_entries_day_of_week_check check (day_of_week between 1 and 7),
    constraint timetable_entries_end_after_start_check check (end_time > start_time)
);

create index timetable_entries_section_day_idx
    on app.timetable_entries (branch, semester, section, day_of_week);
create index timetable_entries_subject_id_idx on app.timetable_entries (subject_id);

-- ---------------------------------------------------------------------------
-- notices: a null target means "all branches" or "all semesters".
-- ---------------------------------------------------------------------------
create table app.notices (
    id              bigint generated always as identity primary key,
    title           varchar(200) not null,
    body            text         not null,
    category        varchar(20)  not null,
    target_branch   varchar(20),
    target_semester smallint,
    due_date        date,
    status          varchar(10)  not null,
    published_at    timestamptz,
    created_by      bigint       not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint notices_created_by_fkey foreign key (created_by) references app.users (id),
    constraint notices_category_check check (category in ('GENERAL', 'EXAM', 'ASSIGNMENT', 'EVENT')),
    constraint notices_target_semester_check check (target_semester between 1 and 8),
    constraint notices_status_check check (status in ('DRAFT', 'PUBLISHED'))
);

create index notices_status_published_at_idx on app.notices (status, published_at desc);
create index notices_created_by_idx on app.notices (created_by);

-- ---------------------------------------------------------------------------
-- audit_logs: append-only record of sensitive changes.
-- actor_user_id is null only for actions taken by the system itself.
-- ---------------------------------------------------------------------------
create table app.audit_logs (
    id            bigint generated always as identity primary key,
    actor_user_id bigint,
    action        varchar(50) not null,
    entity_type   varchar(50) not null,
    entity_id     varchar(50) not null,
    old_value     jsonb,
    new_value     jsonb,
    ip_address    varchar(45),
    created_at    timestamptz not null default now(),
    constraint audit_logs_actor_user_id_fkey foreign key (actor_user_id) references app.users (id)
);

create index audit_logs_entity_idx on app.audit_logs (entity_type, entity_id);
create index audit_logs_created_at_idx on app.audit_logs (created_at desc);
create index audit_logs_actor_user_id_idx on app.audit_logs (actor_user_id);

-- Rows of the audit log may never be changed or removed, by any role, including
-- the table owner during a later migration (docs/08-database-design.md 12.2).
create function app.audit_logs_reject_change() returns trigger
    language plpgsql
as $$
begin
    raise exception 'audit_logs is append-only: % is not allowed', tg_op;
end
$$;

-- A trigger function cannot be called directly, so nobody needs the right to execute it.
revoke all on function app.audit_logs_reject_change() from public;

create trigger audit_logs_no_update_or_delete
    before update or delete on app.audit_logs
    for each row execute function app.audit_logs_reject_change();

create trigger audit_logs_no_truncate
    before truncate on app.audit_logs
    for each statement execute function app.audit_logs_reject_change();

-- ---------------------------------------------------------------------------
-- Row Level Security: a backstop that admits the runtime role only.
-- ---------------------------------------------------------------------------
alter table app.attendance_records enable row level security;
alter table app.assessments enable row level security;
alter table app.marks enable row level security;
alter table app.semester_results enable row level security;
alter table app.timetable_entries enable row level security;
alter table app.notices enable row level security;
alter table app.audit_logs enable row level security;

-- ---------------------------------------------------------------------------
-- Minimum privileges for the runtime role (docs/08-database-design.md 11.3).
-- Official records are corrected, never deleted. The audit log is insert-only.
-- ---------------------------------------------------------------------------
grant select, insert, update on app.attendance_records to cc_app;
grant select, insert, update on app.assessments to cc_app;
grant select, insert, update on app.marks to cc_app;
grant select, insert, update on app.semester_results to cc_app;
grant select, insert, update, delete on app.timetable_entries to cc_app;
grant select, insert, update, delete on app.notices to cc_app;
grant select, insert on app.audit_logs to cc_app;

create policy attendance_records_runtime_select on app.attendance_records for select to cc_app using (true);
create policy attendance_records_runtime_insert on app.attendance_records for insert to cc_app with check (true);
create policy attendance_records_runtime_update on app.attendance_records for update to cc_app using (true) with check (true);

create policy assessments_runtime_select on app.assessments for select to cc_app using (true);
create policy assessments_runtime_insert on app.assessments for insert to cc_app with check (true);
create policy assessments_runtime_update on app.assessments for update to cc_app using (true) with check (true);

create policy marks_runtime_select on app.marks for select to cc_app using (true);
create policy marks_runtime_insert on app.marks for insert to cc_app with check (true);
create policy marks_runtime_update on app.marks for update to cc_app using (true) with check (true);

create policy semester_results_runtime_select on app.semester_results for select to cc_app using (true);
create policy semester_results_runtime_insert on app.semester_results for insert to cc_app with check (true);
create policy semester_results_runtime_update on app.semester_results for update to cc_app using (true) with check (true);

create policy timetable_entries_runtime_select on app.timetable_entries for select to cc_app using (true);
create policy timetable_entries_runtime_insert on app.timetable_entries for insert to cc_app with check (true);
create policy timetable_entries_runtime_update on app.timetable_entries for update to cc_app using (true) with check (true);
create policy timetable_entries_runtime_delete on app.timetable_entries for delete to cc_app using (true);

create policy notices_runtime_select on app.notices for select to cc_app using (true);
create policy notices_runtime_insert on app.notices for insert to cc_app with check (true);
create policy notices_runtime_update on app.notices for update to cc_app using (true) with check (true);
create policy notices_runtime_delete on app.notices for delete to cc_app using (true);

create policy audit_logs_runtime_select on app.audit_logs for select to cc_app using (true);
create policy audit_logs_runtime_insert on app.audit_logs for insert to cc_app with check (true);

-- ---------------------------------------------------------------------------
-- Supabase's API roles get nothing here. They exist only on Supabase, so the
-- revoke is skipped where a role is absent (for example in the test database).
-- ---------------------------------------------------------------------------
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
