-- T7-09: the AI usage log may be cleaned up after its retention period (docs/14, privacy page).
-- The rows hold numbers only, never text. The clean-up job is off unless AI_LOG_RETENTION_ENABLED
-- is true; this migration only allows it to delete.
grant delete on app.ai_request_logs to cc_app;

create policy ai_request_logs_runtime_delete on app.ai_request_logs for delete to cc_app using (true);
