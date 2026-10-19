-- A past paper can be loaded by a one-off import before any administrator account exists, so the
-- "added by" link is optional. Papers added in the admin page still record who added them.
alter table app.pyq_papers alter column created_by drop not null;
