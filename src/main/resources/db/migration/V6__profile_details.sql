-- V6: the personal details a student may keep on their own profile.
-- Runs as the migration role cc_migrator, which owns schema app.
-- Design: docs/08-database-design.md section 4.2; decision: docs/decisions/0009.
--
-- Both columns are optional and start empty, so nothing that exists is changed. The table already
-- has Row Level Security, its grants and its policies from V1; new columns are covered by them.

alter table app.student_profiles
    -- A few lines the student writes about themselves.
    add column bio        varchar(280),
    -- Where the student's photo is kept in file storage. The server generates it; the photo itself
    -- is never stored in the database.
    add column photo_path varchar(300),
    add constraint student_profiles_photo_path_key unique (photo_path);
