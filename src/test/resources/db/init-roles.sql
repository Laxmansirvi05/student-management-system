-- Test database bootstrap. Mirrors what the owner does once on Supabase in task T0-03
-- (docs/08-database-design.md 11.7), so tests run against the real role model.
-- These passwords are test-only values used nowhere else.
create role cc_migrator with login password 'test-migrator-password'
    nosuperuser nocreatedb nocreaterole nobypassrls;
create role cc_app with login password 'test-app-password'
    nosuperuser nocreatedb nocreaterole nobypassrls;

create schema app authorization cc_migrator;
grant usage on schema app to cc_app;
