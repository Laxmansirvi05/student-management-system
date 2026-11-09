-- Seven foreign key columns had no index, against the rule in docs/08 section 1 ("every foreign key
-- column is indexed"). Without one, deleting a parent row (a student, a subject, a folder) scans the
-- whole child table, and joins on the column cannot use an index. Only indexes are added: no data,
-- table or constraint changes. The tables are small, so each takes a moment.
create index if not exists pyq_papers_created_by_idx on app.pyq_papers (created_by);
create index if not exists notes_folder_id_idx on app.notes (folder_id);
create index if not exists note_links_student_id_idx on app.note_links (student_id);
create index if not exists note_shares_owner_id_idx on app.note_shares (owner_id);
create index if not exists note_share_recipients_owner_id_idx on app.note_share_recipients (owner_id);
create index if not exists study_tasks_subject_id_idx on app.study_tasks (subject_id);
create index if not exists student_exams_subject_id_idx on app.student_exams (subject_id);
