-- V9: more personal details a student may keep on their own profile.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Design: docs/08-database-design.md section 4.2; decision: docs/decisions/0009 (amended 2026-10-18).
--
-- All optional and empty at first, so nothing that exists changes. The table already has Row Level
-- Security, its grants and its policies from V1; new columns are covered by them. The formats are
-- checked in Java (ProfileLinks); the database keeps the lengths and refuses anything but https links.

alter table app.student_profiles
    add column personal_email varchar(254),
    add column linkedin_url   varchar(200),
    add column github_url     varchar(200),
    add column portfolio_url  varchar(200),
    -- A short list the student writes, for example "Java, SQL, Figma".
    add column skills         varchar(200),
    add constraint student_profiles_links_https_check check (
        (linkedin_url is null or linkedin_url like 'https://%')
        and (github_url is null or github_url like 'https://%')
        and (portfolio_url is null or portfolio_url like 'https://%'));
