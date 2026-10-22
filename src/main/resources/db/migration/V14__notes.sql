-- Notes (ADR 0014): rich-text notes, folders, links between notes, attachments and sharing between
-- students. Ported from Opportunity Radar's notes workspace; every row belongs to one student.
-- Ownership is enforced by the application (every query carries the student); the runtime role
-- may use the tables but, as everywhere in this schema, only through the grants below.

create table app.note_folders (
    id         bigint generated always as identity primary key,
    student_id bigint       not null,
    name       varchar(60)  not null,
    color      varchar(10)  not null default 'blue',
    icon       varchar(40),
    parent_id  bigint,
    position   integer      not null default 0,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint note_folders_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint note_folders_parent_fkey foreign key (parent_id) references app.note_folders (id) on delete cascade,
    constraint note_folders_name_check check (char_length(trim(name)) > 0),
    constraint note_folders_color_check check (color in (
        'blue', 'cyan', 'purple', 'indigo', 'green', 'yellow', 'orange', 'red', 'pink', 'teal', 'neutral')),
    constraint note_folders_student_name_unique unique (student_id, name)
);

create index note_folders_student_idx on app.note_folders (student_id, position);
create index note_folders_parent_idx on app.note_folders (parent_id);

create table app.notes (
    id         bigint generated always as identity primary key,
    student_id bigint       not null,
    folder_id  bigint,
    title      varchar(200) not null default '',
    content    text         not null default '',
    -- 'robot' is a note captured from the floating robot; it is filed into the Quick Notes folder.
    source     varchar(10)  not null default 'manual',
    tags       varchar(40)[] not null default '{}',
    pinned     boolean      not null default false,
    archived   boolean      not null default false,
    -- Set when the note is in the Trash. Every other view hides these rows.
    deleted_at timestamptz,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint notes_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint notes_folder_fkey foreign key (folder_id) references app.note_folders (id) on delete set null,
    constraint notes_source_check check (source in ('manual', 'robot')),
    constraint notes_not_empty_check check (char_length(trim(title)) > 0 or char_length(trim(content)) > 0)
);

create index notes_student_listing_idx on app.notes (student_id, pinned desc, updated_at desc);
create index notes_student_folder_idx on app.notes (student_id, folder_id);
create index notes_student_deleted_idx on app.notes (student_id, deleted_at);

-- A link from a note to something else, by id. Nothing is copied: the target stays the one source.
create table app.note_links (
    id             bigint generated always as identity primary key,
    student_id     bigint      not null,
    source_note_id bigint      not null,
    target_type    varchar(10) not null,
    target_id      bigint      not null,
    created_at     timestamptz not null default now(),
    constraint note_links_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint note_links_source_fkey foreign key (source_note_id) references app.notes (id) on delete cascade,
    constraint note_links_type_check check (target_type in ('note', 'folder', 'resource')),
    constraint note_links_no_self_check check (not (target_type = 'note' and target_id = source_note_id)),
    constraint note_links_unique unique (source_note_id, target_type, target_id)
);

create index note_links_source_idx on app.note_links (source_note_id);

-- An image or file inside a note. The bytes are in the private storage bucket; this row is what
-- the Attachments view lists and what lets deleting a note remove its files.
create table app.note_attachments (
    id           bigint generated always as identity primary key,
    student_id   bigint       not null,
    note_id      bigint,
    storage_path varchar(300) not null,
    name         varchar(120) not null,
    mime_type    varchar(100) not null,
    size_bytes   bigint       not null default 0,
    kind         varchar(5)   not null,
    created_at   timestamptz  not null default now(),
    constraint note_attachments_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint note_attachments_note_fkey foreign key (note_id) references app.notes (id) on delete cascade,
    constraint note_attachments_kind_check check (kind in ('image', 'file'))
);

-- A duplicated note gets its own attachment rows over the same stored file, so the path is not
-- unique. The file is removed from storage only when the last row that uses it is gone.
create index note_attachments_path_idx on app.note_attachments (storage_path);

create index note_attachments_student_idx on app.note_attachments (student_id, created_at desc);
create index note_attachments_note_idx on app.note_attachments (note_id);

-- Link sharing: one row per shared note. 'view' means any signed-in student who has the link can
-- read the note. There is no public, signed-out access, and no link that edits.
create table app.note_shares (
    id          bigint generated always as identity primary key,
    note_id     bigint      not null unique,
    owner_id    bigint      not null,
    slug        varchar(64) not null unique,
    link_access varchar(10) not null default 'private',
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    constraint note_shares_note_fkey foreign key (note_id) references app.notes (id) on delete cascade,
    constraint note_shares_owner_fkey foreign key (owner_id) references app.student_profiles (id),
    constraint note_shares_access_check check (link_access in ('private', 'view'))
);

-- Sharing with named classmates, with view or edit permission.
create table app.note_share_recipients (
    id           bigint generated always as identity primary key,
    note_id      bigint      not null,
    owner_id     bigint      not null,
    recipient_id bigint      not null,
    permission   varchar(10) not null default 'view',
    created_at   timestamptz not null default now(),
    constraint note_share_recipients_note_fkey foreign key (note_id) references app.notes (id) on delete cascade,
    constraint note_share_recipients_owner_fkey foreign key (owner_id) references app.student_profiles (id),
    constraint note_share_recipients_recipient_fkey foreign key (recipient_id) references app.student_profiles (id),
    constraint note_share_recipients_permission_check check (permission in ('view', 'edit')),
    constraint note_share_recipients_not_self_check check (recipient_id <> owner_id),
    constraint note_share_recipients_unique unique (note_id, recipient_id)
);

create index note_share_recipients_recipient_idx on app.note_share_recipients (recipient_id);

alter table app.note_folders enable row level security;
alter table app.notes enable row level security;
alter table app.note_links enable row level security;
alter table app.note_attachments enable row level security;
alter table app.note_shares enable row level security;
alter table app.note_share_recipients enable row level security;

grant select, insert, update, delete on app.note_folders to cc_app;
grant select, insert, update, delete on app.notes to cc_app;
grant select, insert, delete on app.note_links to cc_app;
grant select, insert, update, delete on app.note_attachments to cc_app;
grant select, insert, update, delete on app.note_shares to cc_app;
grant select, insert, update, delete on app.note_share_recipients to cc_app;

create policy note_folders_runtime_select on app.note_folders for select to cc_app using (true);
create policy note_folders_runtime_insert on app.note_folders for insert to cc_app with check (true);
create policy note_folders_runtime_update on app.note_folders for update to cc_app using (true) with check (true);
create policy note_folders_runtime_delete on app.note_folders for delete to cc_app using (true);
create policy notes_runtime_select on app.notes for select to cc_app using (true);
create policy notes_runtime_insert on app.notes for insert to cc_app with check (true);
create policy notes_runtime_update on app.notes for update to cc_app using (true) with check (true);
create policy notes_runtime_delete on app.notes for delete to cc_app using (true);
create policy note_links_runtime_select on app.note_links for select to cc_app using (true);
create policy note_links_runtime_insert on app.note_links for insert to cc_app with check (true);
create policy note_links_runtime_delete on app.note_links for delete to cc_app using (true);
create policy note_attachments_runtime_select on app.note_attachments for select to cc_app using (true);
create policy note_attachments_runtime_insert on app.note_attachments for insert to cc_app with check (true);
create policy note_attachments_runtime_update on app.note_attachments for update to cc_app using (true) with check (true);
create policy note_attachments_runtime_delete on app.note_attachments for delete to cc_app using (true);
create policy note_shares_runtime_select on app.note_shares for select to cc_app using (true);
create policy note_shares_runtime_insert on app.note_shares for insert to cc_app with check (true);
create policy note_shares_runtime_update on app.note_shares for update to cc_app using (true) with check (true);
create policy note_shares_runtime_delete on app.note_shares for delete to cc_app using (true);
create policy note_share_recipients_runtime_select on app.note_share_recipients for select to cc_app using (true);
create policy note_share_recipients_runtime_insert on app.note_share_recipients for insert to cc_app with check (true);
create policy note_share_recipients_runtime_update on app.note_share_recipients for update to cc_app using (true) with check (true);
create policy note_share_recipients_runtime_delete on app.note_share_recipients for delete to cc_app using (true);
