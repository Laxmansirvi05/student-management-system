# 0014. Notes, the Quick Assistant and the floating robot, ported from Opportunity Radar

- Status: Accepted by the owner's request of 2026-10-23 ("Option 1": port faithfully to plain JavaScript and Java, keep this application's architecture)
- Date: 2026-10-23
- Deciders: Laxman Sirvi
- Changes: adds the Notes workspace (rich-text editor, folders, links between notes, attachments, sharing between students), the Quick Note composer, the Quick Assistant chat, and the floating robot; adds the tables of migrations `V14` (notes) and `V15` (assistant chats); adds the `GeminiAiProvider`; adds two narrow Content Security Policy allowances and a larger request body for one endpoint

## Context

The owner built these features in Opportunity Radar (Next.js, React, Tiptap, Spline, Supabase through the browser) and asked for them in Campus Companion without a redesign: the same editor, folders, quick note, assistant chat and robot, with their styles, assets and behaviour. This application has rules the original did not: one Spring Boot backend, plain JavaScript with no framework or bundler, no browser use of Supabase, a strict Content Security Policy, a private storage bucket, students identified by roll number, and AI text that is never stored (docs/14).

The choice made with the owner: keep this application's architecture, read the original's source, and port only what is needed to plain JavaScript and Java.

## Decision

### What was ported, and how

| Feature | Original | Here |
| --- | --- | --- |
| Notes API and workspace | Next.js routes, Supabase tables with Row Level Security | The `notes` package (controllers, services, repositories) and tables `notes`, `note_folders`, `note_links`, `note_attachments`, `note_shares`, `note_share_recipients` (V14, Row Level Security, runtime grants). Every query carries the student from the session. |
| Rich-text editor | Tiptap (ProseMirror) in React | A `contenteditable` editor written for this application (`js/notes/rich-editor.js` and its modules). Tiptap cannot be used without a bundler and a framework, both outside the approved stack. It has the same toolbar, shortcuts, typed shortcuts (`# `, `- `, `1. `, `> `, `[] `, `---`, `` ``` ``, `**bold**` and so on), checklists, tables with resizable columns, code blocks, pictures with resize and alignment, file chips, drawing pad, links, colours, highlight, sizes and spacing, and its own undo history. |
| HTML sanitizer | `sanitize-html` | `NoteHtmlSanitizer`: plain Java, no dependency. It rebuilds every write from an allowlist (tags, attributes, style values, link and picture addresses) rather than editing it. Tested with injection cases. |
| Folders | 3D CSS cards, colours, icons, nesting, drag ordering | The same design in plain JavaScript and CSS; folder ordering, nesting (with a loop check) and defaults (`Quick Notes`, `Lectures`, `Assignments`, `General`). |
| Quick Note | Composer in React | The same composer on the same editor and autosave engine; the server files a note from the robot into the `Quick Notes` folder. |
| Quick Assistant | `/api/assistant` route calling Gemini, chats in Supabase | `AssistantController` and `AssistantService` over this application's `AiProvider`, usage limits and usage log; chats in `assistant_conversations` and `assistant_messages` (V15). |
| Floating robot | Spline scene, drag, double and triple tap | Same scene file, same overdraw, size, float and pulse motion, drag with a 6 px threshold, position remembered in `robot_position_v1`, double tap opens the assistant, triple tap the quick note, Enter and A for the keyboard. |

### Changes made because this application's rules differ

- **Identity.** A note belongs to a student (`student_id`), not to an account. Sharing names a classmate by roll number or college email.
- **Sharing.** "Anyone with the link can view" becomes "any signed-in student with the link can view": there is no signed-out reading of a note. A link never grants editing; editing is granted to a named classmate. The share page is `student/note-shared.html`.
- **Opportunities.** There are no opportunities or applications here. A note links to another note, a folder, or a learning resource. The Opportunity notes section is not ported.
- **Attachments and the storage bucket.** Files live in the existing private bucket under `notes/<studentId>/<random>`, and are read through `GET /api/v1/notes/attachments/{id}/file`, which checks the reader and redirects to a five-minute signed link. The bucket accepts only PDF and JPEG, so a picture is **converted to JPEG** on upload (`NoteImageProcessor`: new JPEG drawn on white, turned upright by its EXIF tag, metadata dropped; JPEG, PNG and GIF are accepted, a GIF keeps its first frame; SVG and WebP are refused). PDF is accepted. Other documents (doc, docx, xls, xlsx, ppt, pptx, txt, csv, md, zip) are implemented and checked by their first bytes but are **off by default**; they are switched on with `NOTES_EXTRA_FILE_TYPES` once the bucket's allowed types are widened. The limit is 10 MB, the bucket's own.
- **Content Security Policy.** Stored note HTML keeps text alignment, colours and sizes in `style` attributes, which a strict policy ignores when they arrive as markup. `js/notes/safe-html.js` therefore renames them before parsing and sets them through the CSS object model, which the policy does not restrict; `style-src` stays `'self'`. The robot's 3D scene needs a WebAssembly mesh decoder that runs in a worker made from a blob, so the policy gains `'wasm-unsafe-eval'` in `script-src` and `worker-src 'self' blob:`. `'wasm-unsafe-eval'` allows compiling WebAssembly only; `eval()` of text and inline scripts remain forbidden, and a test asserts both.
- **Request size.** An API body is limited to 64 KB. A note carries its whole document (up to 200,000 characters), so `RequestBodyLimitFilter` allows 1 MB for creating or changing a note (`POST` and `PATCH` on `/api/v1/notes` and `/api/v1/notes/{id}`) and nothing else.
- **A note that is too long is refused**, not cut short as the original did.
- **Vendored 3D runtime.** `@splinetool/runtime` 1.12.98 is copied as static files under `/vendor/spline/` (its `package.json` is kept as `PACKAGE.json`), with the robot scene in `/robot/` and the Draco decoder (Apache 2.0) in `/vendor/spline/wasm/`, so nothing is loaded from a CDN. The runtime's `package.json` states no licence; it is the owner's own dependency from Opportunity Radar. If the owner prefers not to vendor it, the robot shows its round badge and everything else works.
- **Icons.** The original's icon font (Material Symbols) is served from `/fonts/material-symbols.woff2`, a subset of only the glyphs used (Apache 2.0). It is refreshed whenever an icon is added.

### The assistant and the AI rules

The Quick Assistant is the same assistant as the study tools: the same `AiProvider`, the same daily limits (a chat message counts as one request), the same usage log (`feature = QUICK_CHAT`, numbers and labels only), and a prompt file in `prompts/` (`assistant-quick-v1`). It follows the AI rules (docs/13) with one difference: its answer is plain text of a few sentences instead of a JSON structure. Rule R14 still holds: the model remembers nothing; the application sends the earlier messages of the chat (up to twenty) with each question.

**A decision for the owner.** docs/14 says the text a student sends to the assistant and the text it returns are never stored. A chat history needs the text. It is stored in `assistant_messages` under these limits: a chat belongs to one student and is read by nobody else, including the administrator; the robot keeps only the six newest chats, older ones are deleted; a student can delete a chat; the usage log still holds no text; nothing is stored when an answer fails. docs/14 is updated to say so. If the owner prefers the old rule, the history can be removed and each chat becomes memory of the page only.

A real provider is added: `AI_PROVIDER=gemini` with `AI_API_KEY` (and optionally `AI_MODEL`, default `gemini-flash-latest`). The key is read from the environment, is sent only in a request header, and is never logged or written in a message. The application refuses to start with `gemini` and no key. `AI_MAX_OUTPUT_TOKENS` should be at least 1500 for a model that spends part of the limit on its own reasoning.

## Options considered

1. **Add React, Tiptap and a bundler.** Faithful, but outside the approved stack and a second build to deploy. Rejected.
2. **Rebuild the features simpler.** Against the owner's instruction. Rejected.
3. **Keep Supabase Row Level Security as the only access control and call it from the browser.** Against ADR 0002 and SECURITY_RULES. Rejected.
4. **Chosen:** a faithful port in plain JavaScript and Java, on this application's backend and database, with the differences above recorded.

## Consequences

- Two migrations, six tables for notes and two for chats, each with Row Level Security.
- Endpoints `/api/v1/notes/**` and `/api/v1/assistant/**`, both for students only.
- The repository gains about 7 MB of static files for the robot (runtime, scene, decoder).
- To do by the owner: widen the bucket's allowed types and set `NOTES_EXTRA_FILE_TYPES` if documents other than PDF are wanted; set `AI_PROVIDER` and `AI_API_KEY` on the host for a real assistant; confirm the chat-history decision above.
