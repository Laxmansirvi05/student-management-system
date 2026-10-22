import { h } from "../dom.js";
import { paletteFor } from "./folder-colors.js";
import { checklistProgress, firstImageUrl, hasAttachmentMarkup, timeAgo, toSnippet } from "./note-text.js";
import { symbol } from "./symbols.js";

// A note as it appears in a list or grid. Everything shown is derived from a truncated snapshot of
// the document, not a render of it: drawing a grid of fifty notes must not mean building fifty
// rich-text documents.

/**
 * Options: note, layout ("grid" or "list"), selected, selectionMode, onOpen(note),
 * onToggleSelect(note, additive), onContextMenu(note, {x, y}).
 */
export function noteCard({ note, layout, selected, selectionMode, onOpen, onToggleSelect, onContextMenu }) {
  const snippet = toSnippet(note.content, layout === "grid" ? 180 : 120);
  const image = firstImageUrl(note.content);
  const checklist = checklistProgress(note.content);
  const palette = note.folder ? paletteFor(note.folder.color) : null;

  // Long press is the selection gesture on touch, where there is no right click and no modifier
  // key. Any movement cancels it, so a scroll that starts on a card does not select it.
  let pressTimer = null;
  let longPressed = false;
  const cancelPress = () => {
    clearTimeout(pressTimer);
    pressTimer = null;
  };

  const picture = image ? h("div.nt-card__image" + (layout === "grid" ? ".nt-card__image--wide" : ""), { "aria-hidden": "true" }) : null;
  if (picture) {
    picture.style.backgroundImage = `url("${image}")`;
  }

  const dot = palette ? h("span.nt-dot-small", { "aria-hidden": "true" }) : null;
  if (dot) {
    dot.style.backgroundColor = palette.tab;
  }

  const check = h(
    "button.nt-card__check" + (selected ? ".is-on" : ""),
    {
      type: "button",
      "aria-label": selected ? "Deselect note" : "Select note",
      onclick: (event) => {
        event.stopPropagation();
        onToggleSelect(note, true);
      },
    },
    symbol("check"),
  );

  const card = h(
    "article.nt-card" + (layout === "list" ? ".nt-card--list" : "") + (selected ? ".is-selected" : ""),
    {
      role: "button",
      tabindex: "0",
      "aria-label": note.title || "Untitled note",
      "aria-pressed": String(selected),
      onclick: (event) => {
        // The long press already acted; the click that follows it must not also open the note.
        if (longPressed) {
          longPressed = false;
          return;
        }
        if (selectionMode || event.metaKey || event.ctrlKey) {
          onToggleSelect(note, event.metaKey || event.ctrlKey);
          return;
        }
        onOpen(note);
      },
      onkeydown: (event) => {
        if (event.key === "Enter") {
          event.preventDefault();
          onOpen(note);
        }
        if (event.key === " ") {
          event.preventDefault();
          onToggleSelect(note, true);
        }
      },
      oncontextmenu: (event) => {
        event.preventDefault();
        onContextMenu(note, { x: event.clientX, y: event.clientY });
      },
      onpointerdown: (event) => {
        if (event.pointerType === "mouse") return;
        longPressed = false;
        pressTimer = setTimeout(() => {
          longPressed = true;
          onToggleSelect(note, true);
        }, 500);
      },
      onpointerup: cancelPress,
      onpointermove: cancelPress,
      onpointercancel: cancelPress,
    },
    picture,
    h(
      "div.nt-card__body",
      h(
        "div.nt-card__head",
        h("h3.nt-card__title", note.title || "Untitled"),
        note.pinned ? h("span.nt-card__flag.nt-card__flag--primary", { title: "Pinned" }, symbol("push_pin")) : null,
        note.shared ? h("span.nt-card__flag", { title: "Shared" }, symbol("group")) : null,
      ),
      snippet ? h("p.nt-card__snippet" + (layout === "grid" ? ".nt-clamp-3" : ".nt-clamp-2"), snippet) : null,
      h(
        "div.nt-card__meta",
        h("span", timeAgo(note.updatedAt)),
        note.folder ? h("span.nt-card__folder", dot, note.folder.name) : null,
        checklist ? h("span.nt-card__folder", { title: "Checklist progress" }, symbol("checklist"), `${checklist.done}/${checklist.total}`) : null,
        hasAttachmentMarkup(note.content) ? h("span.nt-card__flag", { title: "Has an attachment" }, symbol("attach_file")) : null,
        note.source === "robot" ? h("span.nt-card__flag", { title: "Captured with the robot" }, symbol("smart_toy")) : null,
        note.sharedBy ? h("span.nt-card__folder", { title: "Shared with you" }, symbol("person"), note.sharedBy) : null,
      ),
    ),
    check,
  );
  return card;
}
