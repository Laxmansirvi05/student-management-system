-- The papers of an exam notice (date and subject), so the countdown can show each exam of a
-- published time table instead of only the notice's first date.
create table app.notice_exam_papers (
    notice_id  bigint       not null,
    exam_date  date         not null,
    subject    varchar(120) not null,
    constraint notice_exam_papers_notice_fkey
        foreign key (notice_id) references app.notices (id) on delete cascade,
    constraint notice_exam_papers_subject_check check (length(btrim(subject)) >= 1)
);

create index notice_exam_papers_notice_idx on app.notice_exam_papers (notice_id, exam_date);

alter table app.notice_exam_papers enable row level security;

-- Saving a notice replaces its papers: delete, then insert.
grant select, insert, delete on app.notice_exam_papers to cc_app;

create policy notice_exam_papers_runtime_select on app.notice_exam_papers for select to cc_app using (true);
create policy notice_exam_papers_runtime_insert on app.notice_exam_papers for insert to cc_app with check (true);
create policy notice_exam_papers_runtime_delete on app.notice_exam_papers for delete to cc_app using (true);

-- Quick Exam by subject: a question may belong to a unit but does not have to, and a test is
-- started for a whole subject. Existing rows keep their unit.
alter table app.quiz_questions alter column unit_id drop not null;
alter table app.quiz_attempts alter column unit_id drop not null;
