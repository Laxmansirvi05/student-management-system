-- Student complaints (ADR 0013). The complaint row carries no student: the student is kept in
-- complaint_owners, so a query on complaints alone can never reveal who raised a secure complaint.
create table app.complaints (
    id             bigint generated always as identity primary key,
    type           varchar(10)   not null,
    category       varchar(20)   not null,
    title          varchar(120)  not null,
    description    varchar(2000) not null,
    -- Only a public complaint can show its author's roll number to other students.
    show_identity  boolean       not null default false,
    status         varchar(10)   not null default 'OPEN',
    admin_response varchar(2000),
    responded_at   timestamptz,
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    constraint complaints_type_check check (type in ('PUBLIC', 'PRIVATE', 'SECURE')),
    constraint complaints_category_check
        check (category in ('ACADEMIC', 'EXAMS', 'FACILITIES', 'FACULTY', 'ADMINISTRATION', 'OTHER')),
    constraint complaints_status_check check (status in ('OPEN', 'IN_REVIEW', 'RESOLVED', 'REJECTED')),
    constraint complaints_identity_check check (type = 'PUBLIC' or show_identity = false),
    constraint complaints_response_check check ((admin_response is null) = (responded_at is null))
);

create index complaints_board_idx on app.complaints (type, status, created_at desc);

create table app.complaint_owners (
    complaint_id bigint primary key,
    student_id   bigint not null,
    constraint complaint_owners_complaint_fkey
        foreign key (complaint_id) references app.complaints (id) on delete cascade,
    constraint complaint_owners_student_fkey
        foreign key (student_id) references app.student_profiles (id)
);

create index complaint_owners_student_idx on app.complaint_owners (student_id);

create table app.complaint_supports (
    complaint_id bigint      not null,
    student_id   bigint      not null,
    created_at   timestamptz not null default now(),
    primary key (complaint_id, student_id),
    constraint complaint_supports_complaint_fkey
        foreign key (complaint_id) references app.complaints (id) on delete cascade,
    constraint complaint_supports_student_fkey
        foreign key (student_id) references app.student_profiles (id)
);

create index complaint_supports_student_idx on app.complaint_supports (student_id);

alter table app.complaints enable row level security;
alter table app.complaint_owners enable row level security;
alter table app.complaint_supports enable row level security;

grant select, insert, update, delete on app.complaints to cc_app;
grant select, insert, delete on app.complaint_owners to cc_app;
grant select, insert, delete on app.complaint_supports to cc_app;

create policy complaints_runtime_select on app.complaints for select to cc_app using (true);
create policy complaints_runtime_insert on app.complaints for insert to cc_app with check (true);
create policy complaints_runtime_update on app.complaints for update to cc_app using (true) with check (true);
create policy complaints_runtime_delete on app.complaints for delete to cc_app using (true);
create policy complaint_owners_runtime_select on app.complaint_owners for select to cc_app using (true);
create policy complaint_owners_runtime_insert on app.complaint_owners for insert to cc_app with check (true);
create policy complaint_owners_runtime_delete on app.complaint_owners for delete to cc_app using (true);
create policy complaint_supports_runtime_select on app.complaint_supports for select to cc_app using (true);
create policy complaint_supports_runtime_insert on app.complaint_supports for insert to cc_app with check (true);
create policy complaint_supports_runtime_delete on app.complaint_supports for delete to cc_app using (true);
