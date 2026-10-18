-- V8: the grade of each subject in each attempt, and semesters without a published SGPA.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Design: docs/08-database-design.md sections 4.8 and 4.8a.
--
-- The college publishes a grade per subject (O, A+, A, B+, B, C, D, F, Ab, and S or U for a
-- course without credits). A failed subject can be taken again in a supplementary or a later
-- examination; every attempt is kept, and the latest one is the subject's grade. While a subject
-- is failed, the college publishes no SGPA for that semester, so sgpa may now be empty, but only
-- for a semester that is not passed.

alter table app.semester_results alter column sgpa drop not null;
alter table app.semester_results
    add constraint semester_results_sgpa_required_when_passed
        check (sgpa is not null or result_status <> 'PASS');

create table app.subject_results (
    id           bigint generated always as identity primary key,
    student_id   bigint        not null,
    semester     smallint      not null,
    subject_code varchar(20)   not null,
    subject_name varchar(80)   not null,
    -- Null for a course that carries no credits (graded S or U).
    credits      numeric(3, 1),
    grade        varchar(2)    not null,
    -- 1 for the first attempt, 2 for the next, and so on. The highest is the current grade.
    attempt      smallint      not null,
    exam         varchar(100)  not null,
    created_at   timestamptz   not null default now(),
    constraint subject_results_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint subject_results_attempt_key unique (student_id, subject_code, attempt),
    constraint subject_results_semester_check check (semester between 1 and 8),
    constraint subject_results_attempt_check check (attempt between 1 and 20),
    constraint subject_results_credits_check check (credits is null or credits between 0 and 20),
    constraint subject_results_grade_check
        check (grade in ('O', 'A+', 'A', 'B+', 'B', 'C', 'D', 'F', 'Ab', 'S', 'U'))
);

create index subject_results_student_id_semester_idx on app.subject_results (student_id, semester);

alter table app.subject_results enable row level security;

-- Official records: added and corrected, never deleted by the application.
grant select, insert, update on app.subject_results to cc_app;

create policy subject_results_runtime_select on app.subject_results for select to cc_app using (true);
create policy subject_results_runtime_insert on app.subject_results for insert to cc_app with check (true);
create policy subject_results_runtime_update on app.subject_results for update to cc_app using (true) with check (true);
