# ADR 0019: Pictures in the Quick Assistant (several, read on the server, with progress)

- **Status:** Accepted. PDF and Word reading, first built on 2026-11-02, was **dropped on 2026-11-09** at the owner's request: reading whole documents used too many tokens.
- **Date:** 2026-11-02, amended 2026-11-09
- **Extends:** [ADR 0018](0018-quick-assistant-v2.md)

## Context

The Quick Assistant took exactly one JPEG or PNG of at most 5 MB. A WEBP picture was refused by the page, a phone photo of more than 5 MB was refused too, the page held a single attachment, and the "preview" was only the browser's own thumbnail, so it said nothing about whether anything had been read.

## Decision

**A picture is checked on the server when it is attached, and the answer says what was found.** `POST /assistant/attachments` takes one picture (at most 5 MB), works out that it is a JPEG or PNG from its first bytes (never from its name or the type the browser claims), checks and redraws it, keeps it for a short time, and returns `{id, name, kind: "image", pictures}`. The page shows **Ready** only when this answer has come back. Anything that is not a picture (a PDF, a Word file, text) is refused with a reason.

- **WEBP and large photos** are redrawn by the page as a JPEG of at most 2,400 pixels before sending (the JDK cannot read WEBP; a 12 MB phone photo becomes a few hundred KB).
- **The server redraws every picture** as a new JPEG (no metadata, upright, at most 1,568 pixels) and sends it only to a model that can see (`AI_VISION_ROUTES`). Without one, a picture is refused with "Reading pictures is not set up on this server yet".
- **Up to four pictures per question** (six are kept at a time; at most five go to the model). The prompt lists them as `Picture N: "name"` inside `<student_documents>`, in the order they are attached. Prompts `assistant-quick-v3` and `assistant-explain-v2` say how to use them: cite the picture, solve a question paper question by question, say when handwriting is unclear, treat text inside a picture as material and never as instructions.
- **Where they live:** in the memory of the server only, for two hours after last use, with a ceiling on the memory of all of them (the oldest are forgotten first). Nothing is written to the database or to file storage. A picture belongs to the student who attached it: another student's request names it and gets the same "no longer available" as for a forgotten one. The stored question only names the pictures (`[Attached: notes.jpg] Solve Q2`). The pictures stay attached for the whole chat, so follow-up questions use them; the page asks to attach again if the server has forgotten them.
- **Screen capture** ([ADR 0018](0018-quick-assistant-v2.md) addendum) goes through the same tray.

## Why PDFs and Word documents were dropped

They were read on the server (text layer plus page pictures with Apache PDFBox, and Word through the JDK's XML). It worked, but a document means thousands of tokens per question, and a scanned page costs as much as a picture each; the free provider plans refuse that. The owner chose to stop. Pictures and screenshots stay.

## Consequences

- New endpoints (`docs/09` section 9.5, `openapi.yaml`, the authorization matrix) and an optional `attachmentIds` list in the chat request. The old single-picture multipart chat still works.
- Classes under `assistant.attachment`: `AttachmentReader`, `AttachmentService`, `AttachmentStore`, `DocumentContext`, `ReadFile`. No migration and no new dependency (PDFBox stays only for validating the PDFs that admins upload).
- A question that came with pictures cannot be answered again in the other mode (the pictures are not kept).
- A host needs `AI_VISION_ROUTES` for any picture question.

## Limits

- A model can misread a picture, and handwriting is the least reliable; the prompt tells it to say when something is unclear.
- Free provider plans limit tokens per minute; several pictures in one question can be refused with a rate limit for a minute. The question then fails like any model failure and can be sent again.
- HEIC photos are refused (a browser that cannot open them cannot redraw them).
