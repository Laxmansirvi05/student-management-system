-- The robot's Quick Assistant (ADR 0014): a short chat with the AI assistant, kept as a history the
-- student can read again and delete. A chat belongs to one student and is never read by anyone else,
-- including the administrator. The text of a chat is stored here and nowhere else: the usage log
-- (ai_request_logs) still holds numbers and labels only.
create table app.assistant_conversations (
    id         bigint generated always as identity primary key,
    student_id bigint       not null,
    title      varchar(60)  not null default '',
    -- 'quick' is a chat started from the robot; 'assistant' is kept for the full assistant page.
    source     varchar(10)  not null default 'assistant',
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    constraint assistant_conversations_student_fkey foreign key (student_id) references app.student_profiles (id),
    constraint assistant_conversations_source_check check (source in ('assistant', 'quick'))
);

create index assistant_conversations_student_idx on app.assistant_conversations (student_id, source, updated_at desc);

create table app.assistant_messages (
    id              bigint generated always as identity primary key,
    conversation_id bigint        not null,
    role            varchar(4)    not null,
    content         text          not null,
    created_at      timestamptz   not null default now(),
    constraint assistant_messages_conversation_fkey foreign key (conversation_id) references app.assistant_conversations (id) on delete cascade,
    constraint assistant_messages_role_check check (role in ('user', 'ai')),
    constraint assistant_messages_content_check check (char_length(content) between 1 and 8000)
);

create index assistant_messages_conversation_idx on app.assistant_messages (conversation_id, id);

alter table app.assistant_conversations enable row level security;
alter table app.assistant_messages enable row level security;

grant select, insert, update, delete on app.assistant_conversations to cc_app;
grant select, insert, delete on app.assistant_messages to cc_app;

create policy assistant_conversations_runtime_select on app.assistant_conversations for select to cc_app using (true);
create policy assistant_conversations_runtime_insert on app.assistant_conversations for insert to cc_app with check (true);
create policy assistant_conversations_runtime_update on app.assistant_conversations for update to cc_app using (true) with check (true);
create policy assistant_conversations_runtime_delete on app.assistant_conversations for delete to cc_app using (true);
create policy assistant_messages_runtime_select on app.assistant_messages for select to cc_app using (true);
create policy assistant_messages_runtime_insert on app.assistant_messages for insert to cc_app with check (true);
create policy assistant_messages_runtime_delete on app.assistant_messages for delete to cc_app using (true);

-- The usage log gets a label for this kind of request. It still holds no text.
alter table app.ai_request_logs drop constraint ai_request_logs_feature_check;
alter table app.ai_request_logs add constraint ai_request_logs_feature_check check (feature in
    ('EXPLAIN', 'SUMMARIZE', 'GENERATE_QUIZ', 'SUMMARIZE_RESOURCE', 'FLOWCHART', 'QUICK_CHAT'));
