import { fill, h, toast } from "../dom.js";
import { messageFor } from "../api.js";
import { deleteAttachment, listAttachments } from "./notes-api.js";
import { formatBytes } from "./note-text.js";
import { symbol } from "./symbols.js";

// Every image and file across all notes, newest first. Deleting here removes the stored file but
// deliberately leaves the note's own text alone: silently rewriting someone's document from a file
// manager would be a surprising edit.

const FILTERS = [
  { value: "all", label: "All" },
  { value: "image", label: "Images" },
  { value: "file", label: "Files" },
];

/** Options: onOpenNote(noteId). Returns the element. */
export function attachmentsView({ onOpenNote }) {
  let filter = "all";
  let token = 0;
  const root = h("div");

  async function load() {
    const mine = ++token;
    fill(root, h("p.nt-loading", "Loading attachments…"));
    let rows = [];
    try {
      rows = await listAttachments(filter === "all" ? undefined : filter);
    } catch (failure) {
      toast(messageFor(failure));
    }
    if (mine === token) draw(rows);
  }

  function draw(rows) {
    const tabs = h(
      "div.nt-filters",
      FILTERS.map((option) =>
        h("button.nt-filter" + (filter === option.value ? ".is-on" : ""), { type: "button", "aria-pressed": String(filter === option.value), onclick: () => ((filter = option.value), load()) }, option.label),
      ),
    );
    if (rows.length === 0) {
      fill(
        root,
        tabs,
        h("div.nt-empty", symbol("attach_file"), h("h2", "No attachments yet"), h("p", "Images and files you add to a note collect here.")),
      );
      return;
    }
    fill(
      root,
      tabs,
      h(
        "div.nt-attachments",
        rows.map((attachment) => {
          const thumb =
            attachment.kind === "image"
              ? h("a.nt-attachment__thumb", { href: attachment.url, target: "_blank", rel: "noopener noreferrer", "aria-label": `Open ${attachment.name}` })
              : h("a.nt-attachment__thumb.nt-attachment__thumb--file", { href: attachment.url, target: "_blank", rel: "noopener noreferrer", "aria-label": `Open ${attachment.name}` }, symbol("description"));
          if (attachment.kind === "image") {
            thumb.style.backgroundImage = `url("${attachment.url}")`;
          }
          return h(
            "figure.nt-attachment",
            thumb,
            h(
              "figcaption.nt-attachment__caption",
              h("p.nt-attachment__name", { title: attachment.name }, attachment.name),
              h(
                "p.nt-attachment__sub",
                formatBytes(attachment.sizeBytes),
                attachment.noteTitle ? " · " : null,
                attachment.noteTitle ? h("button.nt-linklike", { type: "button", onclick: () => attachment.noteId && onOpenNote(attachment.noteId) }, attachment.noteTitle) : null,
              ),
            ),
            h(
              "button.nt-attachment__delete",
              {
                type: "button",
                "aria-label": `Delete ${attachment.name}`,
                onclick: async () => {
                  try {
                    await deleteAttachment(attachment.id);
                    toast("Attachment deleted");
                    load();
                  } catch (failure) {
                    toast(messageFor(failure));
                  }
                },
              },
              symbol("delete"),
            ),
          );
        }),
      ),
    );
  }

  load();
  return root;
}
