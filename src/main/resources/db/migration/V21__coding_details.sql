-- The richer public data of a coding account (ADR 0017, portfolio views): activity by day, topics,
-- badges, languages and projects, as one JSON document written only by the server. Adding a
-- nullable column keeps every existing row and needs no new privilege.
alter table app.coding_accounts add column details text;
alter table app.coding_accounts
    add constraint coding_accounts_details_size_check check (details is null or length(details) <= 100000);
