-- V3: the resource hub: learning resources, question paper details and bookmarks.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Every table here gets, in this file: Row Level Security, the minimum grants for the
-- runtime role cc_app, and policies for cc_app only.
-- Design: docs/08-database-design.md sections 4.11 to 4.13 and 11.

-- ---------------------------------------------------------------------------
-- learning_resources: what the database knows about an uploaded PDF. The file
-- itself is in Supabase Storage at storage_path; its bytes are never stored here.
-- ---------------------------------------------------------------------------
create table app.learning_resources (
    id                 bigint generated always as identity primary key,
    title              varchar(200)  not null,
    description        varchar(1000),
    subject_id         bigint        not null,
    unit_id            bigint,
    resource_type      varchar(30)   not null,
    storage_path       varchar(300)  not null,
    original_file_name varchar(255)  not null,
    mime_type          varchar(100)  not null,
    file_size_bytes    bigint        not null,
    checksum_sha256    char(64)      not null,
    version            smallint      not null default 1,
    status             varchar(10)   not null,
    uploaded_by        bigint        not null,
    created_at         timestamptz   not null default now(),
    updated_at         timestamptz   not null default now(),
    constraint learning_resources_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint learning_resources_unit_id_fkey foreign key (unit_id) references app.subject_units (id),
    constraint learning_resources_uploaded_by_fkey foreign key (uploaded_by) references app.users (id),
    constraint learning_resources_storage_path_key unique (storage_path),
    constraint learning_resources_type_check check (resource_type in
        ('NOTES', 'SYLLABUS', 'LAB_MANUAL', 'QUESTION_PAPER', 'MODEL_PAPER', 'IMPORTANT_QUESTIONS')),
    constraint learning_resources_file_size_check check (file_size_bytes > 0),
    constraint learning_resources_status_check check (status in ('PUBLISHED', 'HIDDEN'))
);

create index learning_resources_subject_unit_type_idx
    on app.learning_resources (subject_id, unit_id, resource_type);
create index learning_resources_status_created_at_idx
    on app.learning_resources (status, created_at desc);
create index learning_resources_unit_id_idx on app.learning_resources (unit_id);
create index learning_resources_uploaded_by_idx on app.learning_resources (uploaded_by);

-- ---------------------------------------------------------------------------
-- question_papers: extra details for resources of type QUESTION_PAPER or
-- MODEL_PAPER. Removed together with its resource.
-- ---------------------------------------------------------------------------
create table app.question_papers (
    resource_id bigint      primary key,
    exam_year   smallint    not null,
    exam_type   varchar(20) not null,
    max_marks   smallint,
    constraint question_papers_resource_id_fkey foreign key (resource_id)
        references app.learning_resources (id) on delete cascade,
    constraint question_papers_exam_year_check check (exam_year between 2000 and 2100),
    constraint question_papers_exam_type_check
        check (exam_type in ('INTERNAL', 'SEMESTER', 'SUPPLEMENTARY', 'MODEL')),
    constraint question_papers_max_marks_check check (max_marks > 0)
);

create index question_papers_exam_year_idx on app.question_papers (exam_year);

-- ---------------------------------------------------------------------------
-- bookmarks: the resources a student saved. Removed together with the resource.
-- ---------------------------------------------------------------------------
create table app.bookmarks (
    student_id  bigint      not null,
    resource_id bigint      not null,
    created_at  timestamptz not null default now(),
    constraint bookmarks_pkey primary key (student_id, resource_id),
    constraint bookmarks_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint bookmarks_resource_id_fkey foreign key (resource_id)
        references app.learning_resources (id) on delete cascade
);

create index bookmarks_resource_id_idx on app.bookmarks (resource_id);

-- ---------------------------------------------------------------------------
-- Row Level Security: a backstop that admits the runtime role only.
-- ---------------------------------------------------------------------------
alter table app.learning_resources enable row level security;
alter table app.question_papers enable row level security;
alter table app.bookmarks enable row level security;

-- ---------------------------------------------------------------------------
-- Minimum privileges for the runtime role (docs/08-database-design.md 11.3).
-- A bookmark is never edited, so it has no update privilege.
-- ---------------------------------------------------------------------------
grant select, insert, update, delete on app.learning_resources to cc_app;
grant select, insert, update, delete on app.question_papers to cc_app;
grant select, insert, delete on app.bookmarks to cc_app;

create policy learning_resources_runtime_select on app.learning_resources for select to cc_app using (true);
create policy learning_resources_runtime_insert on app.learning_resources for insert to cc_app with check (true);
create policy learning_resources_runtime_update on app.learning_resources for update to cc_app using (true) with check (true);
create policy learning_resources_runtime_delete on app.learning_resources for delete to cc_app using (true);

create policy question_papers_runtime_select on app.question_papers for select to cc_app using (true);
create policy question_papers_runtime_insert on app.question_papers for insert to cc_app with check (true);
create policy question_papers_runtime_update on app.question_papers for update to cc_app using (true) with check (true);
create policy question_papers_runtime_delete on app.question_papers for delete to cc_app using (true);

create policy bookmarks_runtime_select on app.bookmarks for select to cc_app using (true);
create policy bookmarks_runtime_insert on app.bookmarks for insert to cc_app with check (true);
create policy bookmarks_runtime_delete on app.bookmarks for delete to cc_app using (true);

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
