import { fill, h, toast } from "../dom.js";
import { messageFor } from "../api.js";
import { createAutosave } from "./autosave.js";
import { openLinkPicker } from "./link-picker.js";
import { listLinks, removeLink, uploadNoteAttachment } from "./notes-api.js";
import { createRichEditor } from "./rich-editor.js";
import { openShareDialog } from "./share-dialog.js";
import { symbol } from "./symbols.js";

// The writing surface: a header, the document, and its references. Autosave is the only save path:
// there is no Save button, and pending changes are flushed before the pane closes, so going back
// can never lose the last few keystrokes.

const TARGET_ICONS = { note: "description", folder: "folder", resource: "menu_book" };
const TARGET_HREFS = {
  note: (id) => `/student/notes.html?note=${id}`,
  folder: (id) => `/student/notes.html?folder=${id}`,
  resource: (id) => `/student/resource.html?id=${id}`,
};
const STATUS_LABELS = { saving: "Saving…", saved: "Saved", error: "Could not save", idle: "" };

/**
 * Options: note (an existing note, or null), draft ({title, content} from a template), folderId
 * (for a new note), folders (for "Move to folder"), onClose(), onSaved(note), onDeleted(id),
 * onAction(action, note) → Promise<note|undefined> for pin, archive, duplicate, trash and move.
 * Returns { element, close, destroy }.
 */
export function createEditorPane({ note, draft = null, folderId = null, folders = [], onClose, onSaved, onDeleted, onAction }) {
  let current = note;
  let links = [];
  let linkedFor = null;
  const readOnly = note?.sharedPermission === "view";

  const autosave = createAutosave({
    note,
    source: "manual",
    folderId,
    draft,
    onSaved: (saved) => {
      current = saved;
      onSaved(saved);
      refreshHeader();
      loadLinks();
    },
    onDeleted: () => {
      if (note) onDeleted(note.id);
    },
    onStatus: (status) => {
      statusLabel.textContent = STATUS_LABELS[status];
      statusLabel.classList.toggle("is-error", status === "error");
    },
  });

  const statusLabel = h("span.nt-pane__status", { "aria-live": "polite" });

  const titleInput = h("input.nt-title", {
    type: "text",
    value: autosave.title,
    placeholder: "Title",
    maxlength: "200",
    "aria-label": "Note title",
    readonly: readOnly ? true : null,
    oninput: () => autosave.setTitle(titleInput.value),
  });

  const editor = createRichEditor({
    content: autosave.content,
    editable: !readOnly,
    autoFocus: !note,
    minHeight: 320,
    placeholder: "Start writing…",
    onChange: (html) => autosave.setContent(html),
    onUpload: async (file) => {
      const uploaded = await uploadNoteAttachment(file, autosave.noteId);
      return { url: uploaded.url, kind: uploaded.kind, name: uploaded.name, sizeBytes: uploaded.sizeBytes };
    },
    onUploadError: (failure) => toast(messageFor(failure)),
  });

  const header = h("header.nt-pane__header");
  const related = h("section.nt-related", { "aria-label": "Related", hidden: true });

  function menuItem(icon, label, run, destructive = false) {
    return h("button.nt-menuitem" + (destructive ? ".is-destructive" : ""), { type: "button", role: "menuitem", onclick: () => { closeMenu(); run(); } }, symbol(icon), label);
  }

  let menu = null;
  function closeMenu() {
    menu?.remove();
    menu = null;
    document.removeEventListener("pointerdown", away);
  }
  const away = (event) => {
    if (menu && !menu.contains(event.target) && !event.target.closest("[data-more]")) closeMenu();
  };

  async function act(action) {
    try {
      const result = await onAction(action, current);
      if (result) current = result;
      refreshHeader();
    } catch (failure) {
      toast(messageFor(failure));
    }
  }

  function openMenu(anchor) {
    if (menu) return closeMenu();
    menu = h(
      "div.nt-menu-panel",
      { role: "menu" },
      menuItem("push_pin", current.pinned ? "Unpin" : "Pin", () => act("pin")),
      menuItem("archive", current.archived ? "Unarchive" : "Archive", () => act("archive")),
      menuItem("content_copy", "Duplicate", () => act("duplicate")),
      menuItem("drive_file_move", "Move to folder", () => openMoveMenu(anchor)),
      h("div.nt-menu-sep"),
      menuItem("delete", "Move to Trash", () => act("trash"), true),
    );
    anchor.parentElement.append(menu);
    setTimeout(() => document.addEventListener("pointerdown", away), 0);
  }

  function openMoveMenu(anchor) {
    const place = (id) => async () => {
      try {
        await onAction("move", current, id);
        toast("Note moved");
      } catch (failure) {
        toast(messageFor(failure));
      }
    };
    menu = h(
      "div.nt-menu-panel",
      { role: "menu" },
      h("p.nt-menu-title", "Move to…"),
      menuItem("inbox", "Unfiled", place(null)),
      folders.map((folder) => menuItem(folder.icon || "folder", folder.name, place(folder.id))),
    );
    anchor.parentElement.append(menu);
    setTimeout(() => document.addEventListener("pointerdown", away), 0);
  }

  function refreshHeader() {
    const owner = current !== null && !readOnly;
    fill(
      header,
      h("button.nt-tool.nt-tool--round", { type: "button", "aria-label": "Back to notes", onclick: close }, symbol("arrow_back")),
      statusLabel,
      readOnly ? h("span.nt-badge", "View only") : null,
      current?.sharedBy ? h("span.nt-badge.nt-badge--quiet", `Shared by ${current.sharedBy}`) : null,
      h("span.nt-spacer"),
      current && !readOnly && !current.sharedPermission
        ? [
            h("button.nt-ghost", { type: "button", onclick: () => autosave.noteId && openLinkPicker({ noteId: autosave.noteId, onLinked: (next) => ((links = next), drawRelated()) }) }, symbol("add_link"), h("span.nt-hide-sm", "Link")),
            h("button.nt-ghost", { type: "button", onclick: () => autosave.noteId && openShareDialog({ noteId: autosave.noteId, noteTitle: autosave.title }) }, symbol("share"), h("span.nt-hide-sm", "Share")),
            h("div.nt-more-anchor", h("button.nt-tool.nt-tool--round", { type: "button", "data-more": "", "aria-label": "More actions", "aria-haspopup": "menu", onclick: (event) => openMenu(event.currentTarget) }, symbol("more_vert"))),
          ]
        : owner && current?.sharedPermission === "edit"
          ? null
          : null,
    );
  }

  async function loadLinks() {
    const id = autosave.noteId;
    if (!id || linkedFor === id) return;
    linkedFor = id;
    try {
      links = await listLinks(id);
    } catch {
      links = [];
    }
    drawRelated();
  }

  function drawRelated() {
    related.hidden = links.length === 0;
    if (links.length === 0) return;
    fill(
      related,
      h("h2.nt-related__title", "Related"),
      h(
        "ul.nt-related__list",
        links.map((link) =>
          h(
            "li.nt-related__item",
            link.target.label
              ? h("a.nt-related__link", { href: TARGET_HREFS[link.target.type](link.target.id) }, symbol(TARGET_ICONS[link.target.type]), h("span.nt-related__label", link.target.label), link.target.sublabel ? h("span.nt-related__sub", link.target.sublabel) : null)
              : h("span.nt-related__gone", `This ${link.target.type} is no longer available`),
            !readOnly && autosave.noteId
              ? h("button.nt-tool.nt-tool--small.nt-related__remove", { type: "button", "aria-label": "Remove link", onclick: async () => {
                  try {
                    links = await removeLink(autosave.noteId, link.id);
                    drawRelated();
                  } catch (failure) {
                    toast(messageFor(failure));
                  }
                } }, symbol("close"))
              : null,
          ),
        ),
      ),
    );
  }

  async function close() {
    editor.flush();
    await autosave.flush();
    destroy();
    onClose();
  }

  function onKeydown(event) {
    if (event.key === "Escape" && !document.querySelector("dialog[open]") && !menu) {
      close();
    }
  }
  document.addEventListener("keydown", onKeydown);

  function destroy() {
    closeMenu();
    document.removeEventListener("keydown", onKeydown);
    editor.destroy();
    autosave.dispose();
  }

  const element = h(
    "section.nt-pane",
    { "aria-label": "Note editor" },
    header,
    h("div.nt-pane__scroll", h("div.nt-pane__page", titleInput, editor.element, related)),
  );

  refreshHeader();
  if (note) loadLinks();
  return { element, close, destroy, noteId: () => autosave.noteId };
}
