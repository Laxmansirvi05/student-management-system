# ADR 0019: Files in the Quick Assistant (pictures, PDFs, Word documents)

- **Status:** Accepted (owner approved reading PDFs with Apache PDFBox, 2026-11-02)
- **Date:** 2026-11-02
- **Extends:** [ADR 0018](0018-quick-assistant-v2.md)

## Context

The Quick Assistant took exactly one JPEG or PNG of at most 5 MB. A PDF, a Word document or a WEBP picture was refused by the page ("Attach a JPG or PNG picture"), and the server had no code that could read anything but a picture. A phone photo of more than 5 MB was refused too. The prompt also told the model "you cannot open files". So a student could attach nothing but a picture, and a screen capture only seemed to work better because it is a plain PNG.

Where a picture did get through, it was sent to the model as pixels, and the model reads those. What was missing was everything else: nothing extracted text from a PDF or a Word document, nothing looked at the pages of a scanned PDF, the page held a single attachment, and the "preview" was only the browser's own picture of the chosen file, so it said nothing about whether anything had been read.

## Decision

**A file is read on the server when it is attached, and the answer says what was found.** `POST /assistant/attachments` takes one file (at most 10 MB), works out what it is from its first bytes (never from its name or the type the browser claims), reads it, keeps what was read for a short time, and returns `{id, name, kind, pages, pagesRead, characters, pictures, notice, preview}`. The page shows **Ready** only when this answer has come back, with a description built from it ("PDF · 3 pages", "Word · 429 characters, 1 figure"). A file that cannot be read is refused with a reason and shown as an error with a Retry; a blank or unreadable file is never shown as ready.

**How each kind is read** (no model is assumed to read a PDF or a DOCX directly):

| Kind | Method |
| --- | --- |
| JPG, PNG | Checked, redrawn as a JPEG (no metadata, upright, at most 1,568 px) and given to a model that can see. |
| WEBP, very large photos | Redrawn by the page as a JPEG of at most 2,400 px before sending (the JDK cannot read WEBP; a phone photo of 12 MB becomes a few hundred KB). |
| PDF | Apache PDFBox (already a dependency, used only to validate PDF uploads before). The text layer is read page by page in reading order. A page that has almost no text (a scan, a photograph of paper), whose text is symbols (a font the file does not explain), or that holds a large picture (a diagram) is also drawn as a picture of the page for the vision model. At most 60 pages are read and 8 drawn. |
| DOCX | The JDK's own zip and XML (no library): headings, paragraphs, lists with their numbers worked out from the numbering rules (so "1." and "(a)" survive), tables as Markdown tables, equations as plain text such as `$(a)/(b)+c^(2)$`, text boxes, and the pictures placed in the document (given to the vision model at their place). |

**What the model gets.** The text of the files in reading order, with page markers (`[Page 2]`) and markers where a picture belongs (`[Picture 1: Page 2 as a picture]`), inside `<student_documents>`; the pictures are attached in the same order. Prompts `assistant-quick-v3` and `assistant-explain-v2` tell the model how to use them: cite document, page and question number, solve a question paper question by question in its own numbering, say when handwriting or a symbol is unclear, say which part it has not seen when a document was cut, and treat text inside a file as material, never as instructions. A text-only PDF or DOCX goes to the ordinary route (any model); pictures go only to `AI_VISION_ROUTES`.

**Where the files live.** In the memory of the server only, for two hours after last use, a few per student (6; a question may use 4), with a ceiling on the memory of all of them (the oldest are forgotten first). Nothing is written to the database or to file storage. A file belongs to the student who attached it: another student's request names it and gets the same "no longer available" as for a file that was forgotten. The stored question only names the files (`[Attached: paper.pdf] Solve Q2`); the text of a document is never stored. The files stay attached for the whole chat, so follow-up questions use them; the page asks to attach again if the server has forgotten them.

**Limits that protect a small host.** PDFs are opened on two threads of their own under a 45-second limit; a page is drawn at most 1,568 px on its long side; a zip is read with limits on each part and on the total (a small zip can unpack to a huge file); XML is read with doctypes and external entities switched off; a PDF with a password is refused with that reason. One question carries at most 30,000 characters of document text and 5 pictures (some providers refuse more); beyond that the text says so and the model is told to say which part it has not seen.

## Alternatives considered

- **Send PDFs and DOCX straight to Gemini as files.** Works only where a Gemini key is set (none is set locally), and not at all for the Groq, Ollama and OpenRouter routes. Rejected as the only path.
- **Read everything with a vision model, even a text PDF.** More expensive, slower, and worse for exact text and tables than the text layer.
- **Keep the extracted text in the browser and send it with each question.** Larger requests, and a student could send anything as "document text". Rejected; the server reads the file itself.
- **Store the files or their text in the database.** Would make student documents persistent. Rejected: a document may hold anything, and ADR 0018 already chose not to keep pictures.
- **A hand-written PDF reader.** Rejected: unreliable for real files and hard to explain in a viva.

## Consequences

- New endpoints (`docs/09` section 9.5, `openapi.yaml`, the authorization matrix) and an optional `attachmentIds` list in the chat request. The old single-picture multipart chat still works.
- New classes under `assistant.attachment` (`AttachmentReader`, `DocxReader`, `PdfReader`, `AttachmentStore`, `DocumentContext`). No migration and no new dependency (PDFBox was already in `pom.xml`).
- Templates `assistant-quick-v2` and `assistant-explain-v1` are replaced by `assistant-quick-v3` and `assistant-explain-v2`.
- A question that came with files cannot be answered again in the other mode (the files are not kept): the page says so.
- A host needs `AI_VISION_ROUTES` for pictures, scanned PDFs and figures. Without it, text PDFs and DOCX still work, and a picture or scan is refused with "Reading pictures is not set up on this server yet".

## Limits

- Handwriting is read by the vision model and can be wrong; the prompt tells it to say when a word is unclear. The tests use a handwriting font, not real pen on paper.
- Mathematics in a PDF with special fonts comes out as symbols in the text layer; such a page is drawn and read as a picture instead. Equations in a DOCX are converted from Office Math to plain text, and complicated ones (matrices, stacked limits) are simplified.
- A PDF with two columns can have its columns interleaved in the text layer; the page picture is only added when the text looks wrong.
- Vector diagrams in a PDF (lines, not a picture) are not detected as figures. Pictures embedded in a PDF are.
- A long document is read only as far as the limits above; the model is told which part it has not seen.
- Free provider plans limit tokens per minute; several page pictures in one question can be refused with a rate limit for a minute (seen live with Groq). A question then fails like any other model failure and can be sent again.
- Old `.doc` files, HEIC photos, and password-protected PDFs are refused with a reason.
