-- The planner (T7-02, FR-PLN-001 and FR-PLN-002): a student's own study tasks and exam dates.
-- Both belong to one student and are read and changed by that student only (the services filter
-- every query by the student taken from the session).

create table app.study_tasks (
    id           bigint generated always as identity primary key,
    student_id   bigint        not null,
    title        varchar(200)  not null,
    notes        varchar(1000),
    subject_id   bigint,
    due_date     date,
    status       varchar(10)   not null default 'PENDING',
    completed_at timestamptz,
    created_at   timestamptz   not null default now(),
    constraint study_tasks_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint study_tasks_subject_fkey foreign key (subject_id) references app.subjects (id),
    constraint study_tasks_status_check check (status in ('PENDING', 'DONE')),
    -- A task has a completion time exactly when it is done.
    constraint study_tasks_completed_check check ((status = 'DONE') = (completed_at is not null)),
    constraint study_tasks_title_check check (length(btrim(title)) > 0)
);

create index study_tasks_student_idx on app.study_tasks (student_id, status, due_date);

-- An exam the student adds for their own countdown. Exam dates the college publishes stay in
-- notices (category EXAM with a due date) and are not copied here.
create table app.student_exams (
    id         bigint generated always as identity primary key,
    student_id bigint       not null,
    name       varchar(120) not null,
    subject_id bigint,
    exam_date  date         not null,
    created_at timestamptz  not null default now(),
    constraint student_exams_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint student_exams_subject_fkey foreign key (subject_id) references app.subjects (id),
    constraint student_exams_name_check check (length(btrim(name)) > 0)
);

create index student_exams_student_idx on app.student_exams (student_id, exam_date);

alter table app.study_tasks enable row level security;
alter table app.student_exams enable row level security;

grant select, insert, update, delete on app.study_tasks to cc_app;
grant select, insert, update, delete on app.student_exams to cc_app;

create policy study_tasks_runtime_select on app.study_tasks for select to cc_app using (true);
create policy study_tasks_runtime_insert on app.study_tasks for insert to cc_app with check (true);
create policy study_tasks_runtime_update on app.study_tasks for update to cc_app using (true) with check (true);
create policy study_tasks_runtime_delete on app.study_tasks for delete to cc_app using (true);
create policy student_exams_runtime_select on app.student_exams for select to cc_app using (true);
create policy student_exams_runtime_insert on app.student_exams for insert to cc_app with check (true);
create policy student_exams_runtime_update on app.student_exams for update to cc_app using (true) with check (true);
create policy student_exams_runtime_delete on app.student_exams for delete to cc_app using (true);
