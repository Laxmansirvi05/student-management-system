-- Copies of the coding sites' badge pictures (ADR 0017, addendum 2). The server downloads each
-- picture once from the site's fixed image host, checks it, and serves it from this application,
-- so a student's browser never contacts the sites. The key is the SHA-256 of the source address.
create table app.coding_badge_images (
    id           varchar(64)  primary key,
    source       varchar(300) not null,
    content_type varchar(20)  not null,
    data         bytea        not null,
    created_at   timestamptz  not null default now(),
    constraint coding_badge_images_id_check check (id ~ '^[0-9a-f]{64}$'),
    constraint coding_badge_images_type_check check (content_type in
        ('image/png', 'image/svg+xml', 'image/gif', 'image/webp', 'image/jpeg')),
    constraint coding_badge_images_size_check check (octet_length(data) between 1 and 1500000)
);

alter table app.coding_badge_images enable row level security;

grant select, insert on app.coding_badge_images to cc_app;

create policy coding_badge_images_runtime_select on app.coding_badge_images for select to cc_app using (true);
create policy coding_badge_images_runtime_insert on app.coding_badge_images for insert to cc_app with check (true);
