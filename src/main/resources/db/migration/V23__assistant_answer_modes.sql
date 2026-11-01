-- Quick Assistant V2 (ADR 0018): an answer remembers its mode and the question it answers, so a
-- reopened chat can show the Quick and the Explanatory answer of one question side by side and
-- switch between them without asking the model again. Only two nullable columns and an index are
-- added; no existing text is changed or removed.
alter table app.assistant_messages add column style varchar(7);
alter table app.assistant_messages add column reply_to bigint;

alter table app.assistant_messages
    add constraint assistant_messages_style_check check (style is null or style in ('quick', 'explain'));
alter table app.assistant_messages
    add constraint assistant_messages_reply_to_fkey foreign key (reply_to)
        references app.assistant_messages (id) on delete cascade;
-- Only an answer has a mode and a question it answers.
alter table app.assistant_messages
    add constraint assistant_messages_answer_only_check
        check (role = 'ai' or (style is null and reply_to is null));

-- Earlier chats: each answer belongs to the question just before it. An answer with a heading line
-- is an Explanatory one (the Quick templates forbid headings); every other answer is a Quick one.
update app.assistant_messages a
   set reply_to = (select max(q.id)
                     from app.assistant_messages q
                    where q.conversation_id = a.conversation_id and q.role = 'user' and q.id < a.id),
       style = case when a.content ~ '(^|\n)#{2,4} ' then 'explain' else 'quick' end
 where a.role = 'ai';

-- One saved answer per question and mode: it prevents a duplicate and a second model call.
create unique index assistant_messages_answer_mode_idx
    on app.assistant_messages (reply_to, style) where reply_to is not null and style is not null;
