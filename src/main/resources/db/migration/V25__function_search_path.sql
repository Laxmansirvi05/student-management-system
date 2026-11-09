-- The trigger function that keeps audit_logs append-only had no fixed search_path, which Supabase's
-- security advisor flags: a function should not depend on the caller's search_path. It only raises
-- an error, so the built-in schema is all it needs. Behaviour is unchanged.
alter function app.audit_logs_reject_change() set search_path = pg_catalog;
