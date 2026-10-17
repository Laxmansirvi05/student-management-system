-- V7: attendance reports, the college's own totals for a student over a period.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Design: docs/08-database-design.md section 4.18.
--
-- The college publishes attendance as one total per student for a period ("classes held" and
-- "classes attended" from one date to another), not class by class. This table keeps those
-- totals. The percentage is never stored: Java calculates it from the two numbers.

create table app.attendance_reports (
    id               bigint generated always as identity primary key,
    student_id       bigint      not null,
    from_date        date        not null,
    to_date          date        not null,
    classes_held     integer     not null,
    classes_attended integer     not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    constraint attendance_reports_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint attendance_reports_period_key unique (student_id, from_date, to_date),
    constraint attendance_reports_dates_check check (from_date <= to_date),
    constraint attendance_reports_held_check check (classes_held between 1 and 10000),
    constraint attendance_reports_attended_check check (classes_attended between 0 and classes_held)
);

alter table app.attendance_reports enable row level security;

-- Corrected by importing the same period again; never deleted by the application.
grant select, insert, update on app.attendance_reports to cc_app;

create policy attendance_reports_runtime_select on app.attendance_reports for select to cc_app using (true);
create policy attendance_reports_runtime_insert on app.attendance_reports for insert to cc_app with check (true);
create policy attendance_reports_runtime_update on app.attendance_reports for update to cc_app using (true) with check (true);
