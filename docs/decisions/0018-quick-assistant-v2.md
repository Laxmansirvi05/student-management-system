# 0018. Quick Assistant V2: two answer modes, picture questions and drawn explanations

- Status: Accepted by the owner's request of 2026-11-01
- Date: 2026-11-01
- Deciders: Laxman Sirvi
- Changes: the Quick Assistant ([ADR 0014](0014-notes-quick-assistant-and-robot.md)) gets a Quick Answer and an Explanatory Answer mode, accepts a JPG or PNG picture with a question (chosen, captured from the screen or pasted), and draws the diagrams of an explanation; adds a second set of AI models that can read pictures (`AI_VISION_ROUTES`), two prompt templates, one multipart form of `POST /assistant/chat`, and four small browser modules. No migration and no new dependency.

## Context

The Quick Assistant gave short plain-text answers only. The owner wants it to be a learning companion: short answers when asked for, a teacher-style explanation with examples and diagrams when asked for, and questions about a picture (a screenshot of an error, a question on the screen, a diagram).

Three constraints from earlier decisions shape the choices: the approved stack has no frontend libraries and no CDN scripts ([AGENTS.md](../../AGENTS.md) section 2), the flowchart tool was built without Mermaid (FR-AI-006), and AI text and pictures must not leave anything behind that the privacy page does not say ([docs/14](../14-AI-security-and-privacy.md)).

## Decision

- **Two modes, chosen per message.** The request carries `style`: `quick` (the default) or `explain`. Each has its own template (`assistant-quick-v2`, `assistant-explain-v1`), and the mode is logged only as the template version. Switching the mode changes nothing else: the chat, the typed text and the attached picture stay.
- **Pictures go to models that can read them.** A text-only model fails on a picture, so there is a separate route list, `AI_VISION_ROUTES` (`provider:model`, the same form as `AI_ROUTES`). Blank, it falls back to the Gemini models of `AI_ROUTES`, which all read pictures. With none, a question with a picture answers `503 AI_UNAVAILABLE` ("not set up on this server yet") and text questions are unaffected. The router, fallback, limits and usage log are the existing ones.
- **The picture is treated like every upload.** JPG or PNG only (declared type and first bytes must agree), at most 5 MB, and the file itself is thrown away: a new JPEG is drawn from its pixels, at most 1,568 pixels on the long side, upright, with no metadata. It lives for the one request. It is **not stored and not logged**: the stored question only begins with `[Image]`. It reuses the notes image processor (a size parameter was added to it; its behaviour for notes is unchanged).
- **Capture and paste are browser features, used as they are.** A screen, window or tab is captured with `getDisplayMedia`: the browser's own picker and permission, one still picture, and the sharing stops at once. The page cannot choose a region of the desktop, and the button is hidden where the browser does not offer capture or the page is not on HTTPS. A picture pasted from the clipboard is attached the same way.
- **Explanations are drawn here, without a library.** Mermaid and chart libraries would need a CDN or a bundler, which the stack forbids. The model writes a small Markdown subset (headings, lists, tables, code) and, where a visual helps, a fenced block of one of five kinds: `flowchart` (also for architecture and hierarchy; a subset of the familiar Mermaid flowchart text, read and laid out by `diagram-model.js`), `mindmap`, `chart` (bar, line, pie), `steps` (an array algorithm one step at a time) and `math` (plain Unicode, no LaTeX). The page reads these with its own parsers and draws SVG or HTML with `textContent` only. A block that cannot be read is shown as code, so an answer is never lost to a drawing problem. HTML in an answer is never read as markup.
- **Longer answers.** An explanation may use up to 2,600 output tokens and 7,500 stored characters (the column allows 8,000); one that is longer is cut at a blank line and any open block is closed.

### Addendum, 2026-11-01: saved answers keep their mode

An answer is stored with its mode and the question it answers (`V23`: `style`, `reply_to`, and a unique index on both), so a reopened chat shows each question with its Quick and its Explanatory answer and switches between them from the saved text, with no model call. The other mode of a question is written once, on request, through `questionId`, and saved next to the question; no second question is stored. The text itself was already saved whole (Markdown, code, tables and diagram blocks, character for character); what had been lost was the mode and the link between a question and its answers. The migration only adds two nullable columns and an index and fills them for earlier answers (paired with the question before; `explain` when the text has a heading line, which the Quick templates forbid); no text is changed or removed. A question with a picture cannot get a second mode, because the picture is not kept. When a chat is sent to the model, a question with two answers contributes only the newest one.

## Trade-offs

- A real Mermaid renderer would handle more diagram kinds; ours draws one flowchart style, and layout is simple layering, so a very tangled graph is drawn less neatly. Diagrams are capped (30 nodes) and the prompt asks for at most 14.
- Mathematics is Unicode text, not typeset. LaTeX would need a library.
- A picture can only be read by a model that accepts pictures. On the hosts, `AI_VISION_ROUTES` must name one with a key: `groq:qwen/qwen3.8-27b` was checked live to read a screenshot.
- The picture goes to a third-party AI service, like any text sent to the assistant. The privacy page says so.
- Earlier messages of a chat are sent as text; a picture sent earlier is not sent again, so a follow-up question about the same picture needs the picture attached again.
- A model can misread a picture or draw a wrong diagram. The existing notice ("AI-generated. Check it against your course material.") stays on every reply, and the prompts tell the model to say when it cannot read something.

## Consequences

- New optional setting `AI_VISION_ROUTES`; nothing changes on a host that does not set it, except that picture questions work only if a Gemini model is already in `AI_ROUTES`.
- `assistant-quick-v1` is replaced by `assistant-quick-v2` (a rewrite for pictures, code blocks and the mode name); the usage log shows the new version.
- API: [docs/09](../09-api-contract.md) section 9.5, [openapi.yaml](../openapi.yaml); requirement FR-AST-002 to FR-AST-004 in [docs/02](../02-PRD.md).
