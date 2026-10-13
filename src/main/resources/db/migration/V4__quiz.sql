-- V4: Quick Exam: the question bank, the attempts and the answers of each attempt.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Every table here gets, in this file: Row Level Security, the minimum grants for the
-- runtime role cc_app, and policies for cc_app only.
-- Design: docs/08-database-design.md sections 4.14 to 4.16 and 11.

-- ---------------------------------------------------------------------------
-- quiz_questions: one multiple-choice question with four options. The correct
-- option never leaves the server before the attempt is submitted.
-- ---------------------------------------------------------------------------
create table app.quiz_questions (
    id             bigint generated always as identity primary key,
    subject_id     bigint        not null,
    unit_id        bigint        not null,
    question_text  varchar(1000) not null,
    option_a       varchar(300)  not null,
    option_b       varchar(300)  not null,
    option_c       varchar(300)  not null,
    option_d       varchar(300)  not null,
    correct_option char(1)       not null,
    explanation    varchar(2000) not null,
    difficulty     varchar(10)   not null,
    source         varchar(20)   not null,
    status         varchar(10)   not null,
    created_by     bigint        not null,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    constraint quiz_questions_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint quiz_questions_unit_id_fkey foreign key (unit_id) references app.subject_units (id),
    constraint quiz_questions_created_by_fkey foreign key (created_by) references app.users (id),
    constraint quiz_questions_correct_option_check check (correct_option in ('A', 'B', 'C', 'D')),
    constraint quiz_questions_difficulty_check check (difficulty in ('EASY', 'MEDIUM', 'HARD')),
    constraint quiz_questions_source_check check (source in ('ADMIN', 'AI_GENERATED')),
    constraint quiz_questions_status_check check (status in ('DRAFT', 'PUBLISHED', 'RETIRED'))
);

create index quiz_questions_subject_unit_status_idx
    on app.quiz_questions (subject_id, unit_id, status);
create index quiz_questions_unit_id_idx on app.quiz_questions (unit_id);
create index quiz_questions_created_by_idx on app.quiz_questions (created_by);

-- ---------------------------------------------------------------------------
-- quiz_attempts: one test taken by one student. expires_at is set by the
-- server when the test starts: started_at plus 60 seconds for each question.
-- ---------------------------------------------------------------------------
create table app.quiz_attempts (
    id             bigint generated always as identity primary key,
    student_id     bigint      not null,
    subject_id     bigint      not null,
    unit_id        bigint      not null,
    question_count smallint    not null,
    started_at     timestamptz not null,
    expires_at     timestamptz not null,
    submitted_at   timestamptz,
    status         varchar(15) not null,
    score          smallint,
    constraint quiz_attempts_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint quiz_attempts_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint quiz_attempts_unit_id_fkey foreign key (unit_id) references app.subject_units (id),
    constraint quiz_attempts_question_count_check check (question_count between 1 and 20),
    constraint quiz_attempts_status_check check (status in ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    constraint quiz_attempts_score_check check (score >= 0),
    constraint quiz_attempts_expires_after_start_check check (expires_at > started_at)
);

create index quiz_attempts_student_started_at_idx on app.quiz_attempts (student_id, started_at desc);
create index quiz_attempts_subject_id_idx on app.quiz_attempts (subject_id);
create index quiz_attempts_unit_id_idx on app.quiz_attempts (unit_id);

-- ---------------------------------------------------------------------------
-- quiz_attempt_answers: the questions served in an attempt, in order, and what
-- the student chose. is_correct is null until the attempt is closed.
-- ---------------------------------------------------------------------------
create table app.quiz_attempt_answers (
    id              bigint generated always as identity primary key,
    attempt_id      bigint   not null,
    question_id     bigint   not null,
    position        smallint not null,
    selected_option char(1),
    is_correct      boolean,
    constraint quiz_attempt_answers_attempt_id_fkey foreign key (attempt_id)
        references app.quiz_attempts (id) on delete cascade,
    constraint quiz_attempt_answers_question_id_fkey foreign key (question_id)
        references app.quiz_questions (id),
    constraint quiz_attempt_answers_attempt_question_key unique (attempt_id, question_id),
    constraint quiz_attempt_answers_selected_option_check
        check (selected_option in ('A', 'B', 'C', 'D')),
    constraint quiz_attempt_answers_position_check check (position between 1 and 20)
);

create index quiz_attempt_answers_question_id_idx on app.quiz_attempt_answers (question_id);

-- ---------------------------------------------------------------------------
-- Row Level Security: a backstop that admits the runtime role only.
-- ---------------------------------------------------------------------------
alter table app.quiz_questions enable row level security;
alter table app.quiz_attempts enable row level security;
alter table app.quiz_attempt_answers enable row level security;

-- ---------------------------------------------------------------------------
-- Minimum privileges for the runtime role (docs/08-database-design.md 11.3).
-- Questions are retired and attempts are kept, so nothing here is deleted.
-- ---------------------------------------------------------------------------
grant select, insert, update on app.quiz_questions to cc_app;
grant select, insert, update on app.quiz_attempts to cc_app;
grant select, insert, update on app.quiz_attempt_answers to cc_app;

create policy quiz_questions_runtime_select on app.quiz_questions for select to cc_app using (true);
create policy quiz_questions_runtime_insert on app.quiz_questions for insert to cc_app with check (true);
create policy quiz_questions_runtime_update on app.quiz_questions for update to cc_app using (true) with check (true);

create policy quiz_attempts_runtime_select on app.quiz_attempts for select to cc_app using (true);
create policy quiz_attempts_runtime_insert on app.quiz_attempts for insert to cc_app with check (true);
create policy quiz_attempts_runtime_update on app.quiz_attempts for update to cc_app using (true) with check (true);

create policy quiz_attempt_answers_runtime_select on app.quiz_attempt_answers for select to cc_app using (true);
create policy quiz_attempt_answers_runtime_insert on app.quiz_attempt_answers for insert to cc_app with check (true);
create policy quiz_attempt_answers_runtime_update on app.quiz_attempt_answers for update to cc_app using (true) with check (true);

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
