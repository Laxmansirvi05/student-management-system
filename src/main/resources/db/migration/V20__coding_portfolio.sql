-- Coding portfolio and leaderboard (FR-COD-001 to FR-COD-005, ADR 0017). A student links their
-- accounts on coding sites; the server proves each account is theirs with a code in the profile,
-- reads the public numbers, and keeps one snapshot a day to draw the student's journey.

create table app.coding_accounts (
    id                 bigint generated always as identity primary key,
    student_id         bigint       not null,
    platform           varchar(20)  not null,
    handle             varchar(50)  not null,
    status             varchar(10)  not null default 'PENDING',
    verification_code  varchar(12)  not null,
    verified_at        timestamptz,
    problems_solved    integer,
    easy_solved        integer,
    medium_solved      integer,
    hard_solved        integer,
    contest_rating     integer,
    max_rating         integer,
    contests_attended  integer,
    rank_label         varchar(40),
    score              integer,
    public_repos       integer,
    stars              integer,
    followers          integer,
    last_synced_at     timestamptz,
    last_sync_error    varchar(20),
    last_attempt_at    timestamptz,
    created_at         timestamptz  not null default now(),
    constraint coding_accounts_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint coding_accounts_platform_check check (platform in
        ('GITHUB', 'LEETCODE', 'CODEFORCES', 'CODECHEF', 'GEEKSFORGEEKS', 'HACKERRANK')),
    constraint coding_accounts_status_check check (status in ('PENDING', 'VERIFIED')),
    -- An account is verified exactly when it has a verification time.
    constraint coding_accounts_verified_check check ((status = 'VERIFIED') = (verified_at is not null)),
    constraint coding_accounts_handle_check check (handle ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,49}$'),
    constraint coding_accounts_counts_check check (
        coalesce(problems_solved, 0) >= 0 and coalesce(easy_solved, 0) >= 0
        and coalesce(medium_solved, 0) >= 0 and coalesce(hard_solved, 0) >= 0
        and coalesce(contests_attended, 0) >= 0 and coalesce(public_repos, 0) >= 0
        and coalesce(stars, 0) >= 0 and coalesce(followers, 0) >= 0),
    -- One account per site per student.
    constraint coding_accounts_student_platform_key unique (student_id, platform)
);

-- One verified owner per handle on each site; handles are compared without letter case.
create unique index coding_accounts_verified_handle_idx
    on app.coding_accounts (platform, lower(handle)) where status = 'VERIFIED';
create index coding_accounts_status_sync_idx on app.coding_accounts (status, last_synced_at);

-- One row per account per day with the numbers read that day: the journey chart.
create table app.coding_snapshots (
    account_id      bigint  not null,
    captured_on     date    not null,
    problems_solved integer,
    contest_rating  integer,
    constraint coding_snapshots_account_fkey foreign key (account_id)
        references app.coding_accounts (id) on delete cascade,
    constraint coding_snapshots_pkey primary key (account_id, captured_on),
    constraint coding_snapshots_solved_check check (coalesce(problems_solved, 0) >= 0)
);

-- A row means the student shows on the students' leaderboard. No row: hidden (the default).
create table app.coding_leaderboard_optins (
    student_id bigint      primary key,
    created_at timestamptz not null default now(),
    constraint coding_leaderboard_optins_student_fkey foreign key (student_id)
        references app.student_profiles (id) on delete cascade
);

alter table app.coding_accounts enable row level security;
alter table app.coding_snapshots enable row level security;
alter table app.coding_leaderboard_optins enable row level security;

grant select, insert, update, delete on app.coding_accounts to cc_app;
grant select, insert, update on app.coding_snapshots to cc_app;
grant select, insert, delete on app.coding_leaderboard_optins to cc_app;

create policy coding_accounts_runtime_select on app.coding_accounts for select to cc_app using (true);
create policy coding_accounts_runtime_insert on app.coding_accounts for insert to cc_app with check (true);
create policy coding_accounts_runtime_update on app.coding_accounts for update to cc_app using (true) with check (true);
create policy coding_accounts_runtime_delete on app.coding_accounts for delete to cc_app using (true);

create policy coding_snapshots_runtime_select on app.coding_snapshots for select to cc_app using (true);
create policy coding_snapshots_runtime_insert on app.coding_snapshots for insert to cc_app with check (true);
create policy coding_snapshots_runtime_update on app.coding_snapshots for update to cc_app using (true) with check (true);

create policy coding_leaderboard_optins_runtime_select on app.coding_leaderboard_optins for select to cc_app using (true);
create policy coding_leaderboard_optins_runtime_insert on app.coding_leaderboard_optins for insert to cc_app with check (true);
create policy coding_leaderboard_optins_runtime_delete on app.coding_leaderboard_optins for delete to cc_app using (true);
