-- V1: core tables.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Every table here gets, in this file: Row Level Security, the minimum grants for the
-- runtime role cc_app, and policies for cc_app only.
-- Design: docs/08-database-design.md sections 4 and 11.

-- ---------------------------------------------------------------------------
-- users: login identity, credentials and account state. One row per account.
-- ---------------------------------------------------------------------------
create table app.users (
    id                   bigint generated always as identity primary key,
    login_email          varchar(254) not null,
    password_hash        varchar(100) not null,
    role                 varchar(20)  not null,
    display_name         varchar(120) not null,
    active               boolean      not null default true,
    must_change_password boolean      not null default true,
    failed_login_count   smallint     not null default 0,
    locked_until         timestamptz,
    last_login_at        timestamptz,
    created_at           timestamptz  not null default now(),
    updated_at           timestamptz  not null default now(),
    constraint users_login_email_key unique (login_email),
    constraint users_login_email_normalised check (login_email = lower(btrim(login_email))),
    constraint users_login_email_format check (login_email ~ '^[^@[:space:]]+@[^@[:space:]]+$'),
    constraint users_role_check check (role in ('STUDENT', 'ADMIN')),
    constraint users_failed_login_count_check check (failed_login_count >= 0)
);

-- ---------------------------------------------------------------------------
-- student_profiles: the academic identity. No email column: a student's
-- institutional email is users.login_email.
-- ---------------------------------------------------------------------------
create table app.student_profiles (
    id               bigint generated always as identity primary key,
    user_id          bigint       not null,
    roll_no          varchar(20)  not null,
    full_name        varchar(120) not null,
    branch           varchar(20)  not null,
    current_semester smallint     not null,
    section          varchar(5)   not null,
    admission_year   smallint     not null,
    phone            varchar(20),
    created_at       timestamptz  not null default now(),
    updated_at       timestamptz  not null default now(),
    constraint student_profiles_user_id_key unique (user_id),
    constraint student_profiles_user_id_fkey foreign key (user_id) references app.users (id),
    constraint student_profiles_roll_no_key unique (roll_no),
    constraint student_profiles_roll_no_upper check (roll_no = upper(roll_no)),
    constraint student_profiles_roll_no_format check (roll_no ~ '^[A-Z0-9]+$'),
    constraint student_profiles_semester_check check (current_semester between 1 and 8),
    constraint student_profiles_admission_year_check check (admission_year between 2000 and 2100)
);

create index student_profiles_branch_semester_section_idx
    on app.student_profiles (branch, current_semester, section);

-- ---------------------------------------------------------------------------
-- subjects and their numbered units
-- ---------------------------------------------------------------------------
create table app.subjects (
    id       bigint generated always as identity primary key,
    code     varchar(20)  not null,
    name     varchar(150) not null,
    branch   varchar(20)  not null,
    semester smallint     not null,
    credits  smallint     not null,
    active   boolean      not null default true,
    constraint subjects_code_key unique (code),
    constraint subjects_semester_check check (semester between 1 and 8),
    constraint subjects_credits_check check (credits between 0 and 10)
);

create index subjects_branch_semester_idx on app.subjects (branch, semester);

create table app.subject_units (
    id         bigint generated always as identity primary key,
    subject_id bigint       not null,
    unit_no    smallint     not null,
    title      varchar(200) not null,
    constraint subject_units_subject_id_fkey foreign key (subject_id) references app.subjects (id),
    constraint subject_units_subject_id_unit_no_key unique (subject_id, unit_no),
    constraint subject_units_unit_no_check check (unit_no between 1 and 20)
);

-- ---------------------------------------------------------------------------
-- Row Level Security: a backstop. It admits the runtime role only. It does not
-- restrict the table owner, and it is not the access control between students.
-- ---------------------------------------------------------------------------
alter table app.users enable row level security;
alter table app.student_profiles enable row level security;
alter table app.subjects enable row level security;
alter table app.subject_units enable row level security;

-- ---------------------------------------------------------------------------
-- Minimum privileges for the runtime role (docs/08-database-design.md 11.3).
-- Accounts, profiles and subjects are deactivated, never deleted.
-- ---------------------------------------------------------------------------
grant select, insert, update on app.users to cc_app;
grant select, insert, update on app.student_profiles to cc_app;
grant select, insert, update on app.subjects to cc_app;
grant select, insert, update, delete on app.subject_units to cc_app;

create policy users_runtime_select on app.users for select to cc_app using (true);
create policy users_runtime_insert on app.users for insert to cc_app with check (true);
create policy users_runtime_update on app.users for update to cc_app using (true) with check (true);

create policy student_profiles_runtime_select on app.student_profiles for select to cc_app using (true);
create policy student_profiles_runtime_insert on app.student_profiles for insert to cc_app with check (true);
create policy student_profiles_runtime_update on app.student_profiles for update to cc_app using (true) with check (true);

create policy subjects_runtime_select on app.subjects for select to cc_app using (true);
create policy subjects_runtime_insert on app.subjects for insert to cc_app with check (true);
create policy subjects_runtime_update on app.subjects for update to cc_app using (true) with check (true);

create policy subject_units_runtime_select on app.subject_units for select to cc_app using (true);
create policy subject_units_runtime_insert on app.subject_units for insert to cc_app with check (true);
create policy subject_units_runtime_update on app.subject_units for update to cc_app using (true) with check (true);
create policy subject_units_runtime_delete on app.subject_units for delete to cc_app using (true);

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
            execute format('revoke all on schema app from %I', api_role);
        end if;
    end loop;
end
$$;
