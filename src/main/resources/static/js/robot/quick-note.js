import { h, toast } from "../dom.js";
import { messageFor } from "../api.js";
import { createAutosave } from "../notes/autosave.js";
import { uploadNoteAttachment } from "../notes/notes-api.js";
import { createRichEditor } from "../notes/rich-editor.js";
import { symbol } from "../notes/symbols.js";
import { createFloatingPanel } from "./floating-panel.js";

// The Quick Note popup that expands from the robot on a triple tap. It uses the same autosave engine
// and the same editor as the notes workspace: this is not a second notes system, only a different
// way into the one API. A note written here is filed into the Quick Notes folder by the server.

const DEFAULT_SIZE = { width: 340, height: 300 };
const STATUS_LABELS = { saving: "Saving…", saved: "✓ Saved", error: "Unable to save", idle: "" };

/** Options: anchor ({x, y, size}), onClose(). Returns { close }. */
export function openQuickNote({ anchor, onClose }) {
  const status = h("span.rb-note__status", { "aria-live": "polite" });
  const autosave = createAutosave({
    note: null,
    source: "robot",
    onStatus: (value) => {
      status.textContent = STATUS_LABELS[value] || "";
      status.classList.toggle("is-error", value === "error");
    },
  });

  const title = h("input.rb-note__title", {
    type: "text",
    placeholder: "Title (optional)",
    "aria-label": "Note title",
    maxlength: "200",
    oninput: () => autosave.setTitle(title.value),
  });

  // The note row may not exist yet when the first image is pasted: the draft is created on the first
  // non-empty keystroke, so an upload goes through unattached and is adopted once the note has an id.
  const editor = createRichEditor({
    content: "",
    placeholder: "What do you want to remember?",
    compact: true,
    minHeight: 110,
    onChange: (html) => autosave.setContent(html),
    onUpload: async (file) => {
      const uploaded = await uploadNoteAttachment(file, autosave.noteId);
      return { url: uploaded.url, kind: uploaded.kind, name: uploaded.name, sizeBytes: uploaded.sizeBytes };
    },
    onUploadError: (failure) => toast(messageFor(failure)),
  });

  const closeButton = h("button.rb-iconbtn", { type: "button", "aria-label": "Close quick note", onclick: close }, symbol("close"));
  const header = h("div.rb-panel__drag", h("h2.rb-panel__title", symbol("drag_indicator"), "Quick Note"), closeButton);
  const body = h(
    "div.rb-note",
    title,
    h("div.rb-note__editor", editor.element),
    h("div.rb-note__foot", status),
  );

  const panel = createFloatingPanel({
    label: "Quick note",
    anchor,
    defaultSize: DEFAULT_SIZE,
    storageKey: "robot_quick_note_size_v1",
    placement: "note",
    header,
    body,
    backdropLevel: 9998,
    resizeLabel: "Resize quick note",
    onBackdrop: () => close(),
  });
  panel.element.classList.add("rb-panel--note");

  let closed = false;
  async function close() {
    if (closed) return;
    closed = true;
    document.removeEventListener("keydown", onKeydown);
    editor.flush();
    await autosave.flush();
    editor.destroy();
    autosave.dispose();
    panel.destroy();
    onClose();
  }

  function onKeydown(event) {
    if (event.key === "Escape" && !document.querySelector("dialog[open]")) {
      event.preventDefault();
      close();
    }
  }
  document.addEventListener("keydown", onKeydown);

  document.body.append(panel.backdrop, panel.element);
  title.focus();
  return { close };
}
