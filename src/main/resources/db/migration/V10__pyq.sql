-- V10: previous-year question papers (PYQs).
-- Runs as the migration role cc_migrator, which owns schema app.
-- Design: docs/08-database-design.md sections 4.21 and 4.22; decision: docs/decisions/0012.
--
-- A paper belongs to a branch and a subject NAME, not to a row of "subjects": past papers span old
-- regulations and have no semester or credits. The PDF is in Supabase Storage at storage_path; its
-- bytes are never in the database.

create table app.pyq_papers (
    id              bigint generated always as identity primary key,
    branch          varchar(10)  not null,
    subject_name    varchar(120) not null,
    exam_year       smallint     not null,
    exam_month      smallint     not null,
    exam_kind       varchar(10)  not null,
    -- 1 or 2 for a mid-semester paper; null when the paper does not say, and for every other kind.
    mid_number      smallint,
    regulation      varchar(10),
    course_code     varchar(40),
    -- 1 for the usual case. 2, 3 ... when one sitting had more than one question set.
    variant         smallint     not null default 1,
    storage_path    varchar(300) not null,
    size_bytes      integer      not null,
    checksum_sha256 char(64)     not null,
    status          varchar(10)  not null default 'PUBLISHED',
    download_count  integer      not null default 0,
    created_by      bigint       not null,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint pyq_papers_created_by_fkey foreign key (created_by) references app.users (id),
    constraint pyq_papers_storage_path_key unique (storage_path),
    constraint pyq_papers_checksum_key unique (checksum_sha256),
    constraint pyq_papers_branch_check check (branch ~ '^[A-Z]{2,10}$'),
    constraint pyq_papers_year_check check (exam_year between 2000 and 2100),
    constraint pyq_papers_month_check check (exam_month between 1 and 12),
    constraint pyq_papers_kind_check check (exam_kind in ('REGULAR', 'SUPPLY', 'MID')),
    constraint pyq_papers_mid_check check (
        (exam_kind = 'MID' and (mid_number is null or mid_number between 1 and 4))
        or (exam_kind <> 'MID' and mid_number is null)),
    constraint pyq_papers_regulation_check check (regulation is null or regulation ~ '^AR[0-9]{2}$'),
    constraint pyq_papers_variant_check check (variant between 1 and 20),
    constraint pyq_papers_size_check check (size_bytes > 0),
    constraint pyq_papers_status_check check (status in ('PUBLISHED', 'HIDDEN')),
    constraint pyq_papers_downloads_check check (download_count >= 0)
);

-- The same sitting of the same subject can exist only once for a regulation and a question set.
create unique index pyq_papers_identity_key on app.pyq_papers (
    branch, lower(subject_name), exam_year, exam_month, exam_kind,
    coalesce(mid_number, 0), coalesce(regulation, ''), variant);

create index pyq_papers_branch_subject_idx on app.pyq_papers (branch, subject_name);
create index pyq_papers_year_idx on app.pyq_papers (exam_year desc, exam_month desc);

create table app.pyq_bookmarks (
    student_id bigint      not null,
    paper_id   bigint      not null,
    created_at timestamptz not null default now(),
    primary key (student_id, paper_id),
    constraint pyq_bookmarks_student_id_fkey foreign key (student_id) references app.student_profiles (id),
    constraint pyq_bookmarks_paper_id_fkey foreign key (paper_id) references app.pyq_papers (id)
);

create index pyq_bookmarks_paper_id_idx on app.pyq_bookmarks (paper_id);

alter table app.pyq_papers enable row level security;
alter table app.pyq_bookmarks enable row level security;

grant select, insert, update, delete on app.pyq_papers to cc_app;
grant select, insert, delete on app.pyq_bookmarks to cc_app;

create policy pyq_papers_runtime_select on app.pyq_papers for select to cc_app using (true);
create policy pyq_papers_runtime_insert on app.pyq_papers for insert to cc_app with check (true);
create policy pyq_papers_runtime_update on app.pyq_papers for update to cc_app using (true) with check (true);
create policy pyq_papers_runtime_delete on app.pyq_papers for delete to cc_app using (true);
create policy pyq_bookmarks_runtime_select on app.pyq_bookmarks for select to cc_app using (true);
create policy pyq_bookmarks_runtime_insert on app.pyq_bookmarks for insert to cc_app with check (true);
create policy pyq_bookmarks_runtime_delete on app.pyq_bookmarks for delete to cc_app using (true);
