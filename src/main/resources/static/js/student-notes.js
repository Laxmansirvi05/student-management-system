import { confirmAction } from "./admin-kit.js";
import { messageFor } from "./api.js";
import { fill, h, toast } from "./dom.js";
import { attachmentsView } from "./notes/attachments-view.js";
import { openFolderDialog } from "./notes/folder-dialog.js";
import { folderCard } from "./notes/folder-card.js";
import { paletteFor } from "./notes/folder-colors.js";
import { noteCard } from "./notes/note-card.js";
import { createEditorPane } from "./notes/note-editor-pane.js";
import { bulkNoteAction, createFolder, deleteFolder, deleteNote, duplicateNote, emptyTrash, getNote, listFolders, listNotes, reorderFolders, restoreNote, updateFolder, updateNote } from "./notes/notes-api.js";
import { symbol } from "./notes/symbols.js";
import { NOTE_TEMPLATES } from "./notes/templates.js";
import "./shell.js";

// The notes workspace: sidebar, folder browser, note list and editor. They are one controller
// because they share a single source of truth for the current view, selection and folder list:
// splitting them would mean either passing every change around or a second state that can disagree.

const VIEWS = [
  { value: "all", label: "All Notes", icon: "description" },
  { value: "recent", label: "Recent", icon: "schedule" },
  { value: "pinned", label: "Pinned", icon: "push_pin" },
  { value: "shared", label: "Shared", icon: "group" },
  { value: "attachments", label: "Attachments", icon: "attach_file" },
  { value: "archived", label: "Archive", icon: "archive" },
  { value: "trash", label: "Trash", icon: "delete" },
];
const SORTS = [
  { value: "updated", label: "Recently updated" },
  { value: "created", label: "Recently created" },
  { value: "alphabetical", label: "Alphabetical" },
];
const SEARCH_DEBOUNCE_MS = 300;

const state = {
  notes: [],
  folders: [],
  view: "all",
  folderId: null,
  query: "",
  sort: "updated",
  layout: "grid",
  selected: new Set(),
  loading: true,
  pane: null,
  sidebarOpen: false,
  dragId: null,
  dropId: null,
};

const mount = document.getElementById("content");
let fetchTimer = null;
let fetchToken = 0;
let contextMenu = null;

const activeFolder = () => state.folders.find((folder) => folder.id === state.folderId) || null;
const rootFolders = () => state.folders.filter((folder) => !folder.parentId);
const showFolderBrowser = () => state.view === "all" && !state.folderId && !state.query.trim();
const failed = (failure) => toast(messageFor(failure));

// --- data -----------------------------------------------------------------------------------

async function refreshFolders() {
  try {
    state.folders = await listFolders();
    renderSidebar();
    renderBody();
  } catch {
    // A stale folder list is survivable: the notes themselves still load.
  }
}

async function fetchNotes() {
  const mine = ++fetchToken;
  state.loading = true;
  renderBody();
  try {
    const notes = await listNotes({ q: state.query || undefined, view: state.view, sort: state.sort, folder: state.folderId ?? undefined });
    if (mine !== fetchToken) return;
    state.notes = notes;
  } catch (failure) {
    if (mine === fetchToken) failed(failure);
  } finally {
    if (mine === fetchToken) {
      state.loading = false;
      renderBody();
    }
  }
}

function scheduleFetch() {
  clearTimeout(fetchTimer);
  fetchTimer = setTimeout(fetchNotes, SEARCH_DEBOUNCE_MS);
}

function selectView(view) {
  state.view = view;
  state.folderId = null;
  state.selected = new Set();
  state.sidebarOpen = false;
  render();
  fetchNotes();
}

function openFolder(folder) {
  state.folderId = folder.id;
  state.view = "all";
  state.selected = new Set();
  state.sidebarOpen = false;
  render();
  fetchNotes();
}

function upsertNote(note) {
  const index = state.notes.findIndex((item) => item.id === note.id);
  if (index >= 0) state.notes[index] = note;
  else state.notes.unshift(note);
  refreshFolders();
}

function dropNote(id) {
  state.notes = state.notes.filter((note) => note.id !== id);
  refreshFolders();
}

function toggleSelect(note) {
  if (state.selected.has(note.id)) state.selected.delete(note.id);
  else state.selected.add(note.id);
  renderBulk();
  renderBody();
}

async function runBulk(action, folderId) {
  const ids = [...state.selected];
  if (!ids.length) return;
  try {
    await bulkNoteAction(ids, action, folderId);
    state.selected = new Set();
    await fetchNotes();
    await refreshFolders();
    renderBulk();
    toast(`${ids.length} ${ids.length === 1 ? "note" : "notes"} updated`);
  } catch (failure) {
    failed(failure);
  }
}

/** One action on one note. Returns the updated note where there is one, for the editor pane. */
async function noteAction(action, note, folderId) {
  if (action === "pin") {
    const updated = await updateNote(note.id, { pinned: !note.pinned });
    upsertNote(updated);
    return updated;
  }
  if (action === "archive") {
    const updated = await updateNote(note.id, { archived: !note.archived });
    dropNote(note.id);
    toast(note.archived ? "Note unarchived" : "Note archived");
    return updated;
  }
  if (action === "duplicate") {
    upsertNote(await duplicateNote(note.id));
    toast("Note duplicated");
  } else if (action === "trash") {
    await deleteNote(note.id);
    dropNote(note.id);
    if (state.pane) await closePane(false);
    toast("Moved to Trash");
  } else if (action === "restore") {
    await restoreNote(note.id);
    dropNote(note.id);
    toast("Note restored");
  } else if (action === "delete") {
    if (!(await confirmAction({ title: "Delete this note for good?", text: "It will be removed with its pictures and files. This cannot be undone.", confirmLabel: "Delete permanently", danger: true }))) return;
    await deleteNote(note.id, true);
    dropNote(note.id);
    toast("Note deleted permanently");
  } else if (action === "move") {
    const updated = await updateNote(note.id, folderId === null || folderId === undefined ? { unfile: true } : { folderId });
    upsertNote(updated);
    return updated;
  }
  return undefined;
}

const guarded = (action, note, folderId) => noteAction(action, note, folderId).catch(failed);

async function saveFolder(input, existing, dialog) {
  try {
    if (existing) {
      await updateFolder(existing.id, input.parentId === null && existing.parentId ? { ...input, parentId: undefined, toTopLevel: true } : input);
      toast("Folder updated");
    } else {
      await createFolder(input);
      toast("Folder created");
    }
    dialog.close();
    await refreshFolders();
  } catch (failure) {
    failed(failure);
  }
}

async function removeFolder(folder) {
  if (!(await confirmAction({ title: `Delete "${folder.name}"?`, text: "Its notes are kept and move to Unfiled. Folders inside it are deleted too.", confirmLabel: "Delete folder", danger: true }))) return;
  try {
    await deleteFolder(folder.id);
    if (state.folderId === folder.id) state.folderId = null;
    await refreshFolders();
    await fetchNotes();
    render();
    toast(`"${folder.name}" deleted. Its notes moved to Unfiled.`);
  } catch (failure) {
    failed(failure);
  }
}

/** A sidebar drag is committed as one request: moving one folder shifts every folder after it. */
async function commitFolderDrop(targetId) {
  const sourceId = state.dragId;
  state.dragId = state.dropId = null;
  if (!sourceId || sourceId === targetId) return renderSidebar();
  const order = [...state.folders];
  const from = order.findIndex((folder) => folder.id === sourceId);
  const to = order.findIndex((folder) => folder.id === targetId);
  if (from < 0 || to < 0) return;
  const [moved] = order.splice(from, 1);
  order.splice(to, 0, moved);
  state.folders = order.map((folder, index) => ({ ...folder, position: index }));
  renderSidebar();
  try {
    await reorderFolders(order.map((folder, index) => ({ id: folder.id, position: index })));
  } catch (failure) {
    failed(failure);
    refreshFolders();
  }
}

// --- the editor pane --------------------------------------------------------------------------

function openPane(note, draft) {
  closeContextMenu();
  const pane = createEditorPane({
    note,
    draft,
    folderId: state.folderId,
    folders: state.folders,
    onClose: () => {
      state.pane = null;
      render();
      fetchNotes();
      refreshFolders();
    },
    onSaved: upsertNote,
    onDeleted: dropNote,
    onAction: (action, target, folderId) => noteAction(action, target, folderId),
  });
  state.pane = pane;
  render();
}

async function closePane(refresh = true) {
  if (!state.pane) return;
  const pane = state.pane;
  state.pane = null;
  pane.destroy();
  render();
  if (refresh) {
    fetchNotes();
    refreshFolders();
  }
}

// --- layout -----------------------------------------------------------------------------------

const sidebar = h("aside.nt-sidebar", { "aria-label": "Notes navigation" });
const scrim = h("button.nt-scrim", { type: "button", "aria-label": "Close navigation", hidden: true, onclick: () => ((state.sidebarOpen = false), render()) });

const heading = h("h2.nt-heading");
const layoutButton = h("button.nt-tool.nt-tool--round", { type: "button", onclick: () => ((state.layout = state.layout === "grid" ? "list" : "grid"), render()) });
const backButton = h("button.nt-ghost", { type: "button", onclick: () => ((state.folderId = null), render(), fetchNotes()) }, symbol("arrow_back"), "All Notes");
const menuButton = h("button.nt-tool.nt-tool--round.nt-show-sm", { type: "button", "aria-label": "Open notes navigation", onclick: () => ((state.sidebarOpen = true), render()) }, symbol("menu"));

let templateMenu = null;
const templateToggle = h("button.nt-split__toggle", { type: "button", "aria-label": "Start from a template", "aria-haspopup": "menu", "aria-expanded": "false", onclick: toggleTemplates }, symbol("expand_more"));
const newNote = h(
  "div.nt-split",
  h("button.nt-split__main", { type: "button", onclick: () => openPane(null, null) }, symbol("add"), h("span.nt-hide-sm", "New Note")),
  h("span.nt-split__divider", { "aria-hidden": "true" }),
  h("div.nt-split__more", templateToggle),
);

function toggleTemplates() {
  if (templateMenu) return closeTemplates();
  templateMenu = h(
    "div.nt-menu-panel.nt-menu-panel--templates",
    { role: "menu" },
    h("p.nt-menu-title", "Start from a template"),
    NOTE_TEMPLATES.map((template) =>
      h(
        "button.nt-menuitem.nt-menuitem--template",
        { type: "button", role: "menuitem", "aria-label": `New note from the ${template.name} template`, onclick: () => { closeTemplates(); openPane(null, { title: template.title, content: template.content }); } },
        symbol(template.icon),
        h("span", h("span.nt-menuitem__label", template.name), h("span.nt-menuitem__hint", template.description)),
      ),
    ),
  );
  templateToggle.parentElement.append(templateMenu);
  templateToggle.setAttribute("aria-expanded", "true");
  setTimeout(() => document.addEventListener("pointerdown", awayTemplates), 0);
}
const awayTemplates = (event) => {
  if (templateMenu && !templateMenu.contains(event.target) && !templateToggle.contains(event.target)) closeTemplates();
};
function closeTemplates() {
  templateMenu?.remove();
  templateMenu = null;
  templateToggle.setAttribute("aria-expanded", "false");
  document.removeEventListener("pointerdown", awayTemplates);
}

const searchInput = h("input.nt-search__input", { type: "search", placeholder: "Search notes…", "aria-label": "Search notes", oninput: () => ((state.query = searchInput.value), renderHeading(), scheduleFetch()) });
const sortSelect = h("select.nt-select", { "aria-label": "Sort notes", onchange: () => ((state.sort = sortSelect.value), scheduleFetch()) }, SORTS.map((option) => h("option", { value: option.value }, option.label)));
const emptyTrashButton = h("button.nt-btn.nt-btn--danger.nt-btn--pill", { type: "button", onclick: onEmptyTrash }, "Empty Trash");

async function onEmptyTrash() {
  if (!(await confirmAction({ title: "Empty the Trash?", text: "Every note in it is deleted for good, with its pictures and files.", confirmLabel: "Empty Trash", danger: true }))) return;
  try {
    const count = await emptyTrash();
    await fetchNotes();
    toast(`${count} ${count === 1 ? "note" : "notes"} deleted permanently`);
  } catch (failure) {
    failed(failure);
  }
}

const bulkBar = h("div.nt-bulk", { hidden: true });
const body = h("div.nt-body");
const mainHeader = h(
  "header.nt-main__header",
  h("div.nt-main__row", menuButton, backButton, heading, layoutButton, newNote),
  h("div.nt-main__row.nt-main__row--tools", h("div.nt-search", symbol("search"), searchInput), sortSelect, emptyTrashButton),
);
const main = h("main.nt-main");
const workspace = h("div.nt-workspace", sidebar, scrim, main);

function render() {
  renderSidebar();
  renderMain();
}

function renderSidebar() {
  sidebar.classList.toggle("is-open", state.sidebarOpen);
  scrim.hidden = !state.sidebarOpen;
  fill(
    sidebar,
    h(
      "nav.nt-nav",
      VIEWS.map((item) => {
        const active = state.view === item.value && !state.folderId;
        return h("button.nt-nav__item" + (active ? ".is-active" : ""), { type: "button", "aria-current": active ? "page" : null, onclick: () => selectView(item.value) }, symbol(item.icon), item.label);
      }),
    ),
    h("div.nt-folders__head", h("span", "Folders"), h("button.nt-tool.nt-tool--small", { type: "button", "aria-label": "New folder", onclick: () => folderDialog(null) }, symbol("add"))),
    h(
      "nav.nt-nav.nt-nav--folders",
      state.folders.map((folder) => {
        const palette = paletteFor(folder.color);
        const active = state.folderId === folder.id;
        const icon = symbol(folder.icon || "folder");
        icon.style.color = palette.tab;
        return h(
          "button.nt-nav__item" + (active ? ".is-active" : "") + (folder.parentId ? ".is-nested" : "") + (state.dragId === folder.id ? ".is-dragging" : "") + (state.dropId === folder.id && state.dragId !== folder.id ? ".is-drop" : ""),
          {
            type: "button",
            draggable: "true",
            "aria-current": active ? "page" : null,
            ondragstart: () => (state.dragId = folder.id),
            ondragover: (event) => {
              event.preventDefault();
              if (state.dropId !== folder.id) {
                state.dropId = folder.id;
                renderSidebar();
              }
            },
            ondrop: (event) => {
              event.preventDefault();
              commitFolderDrop(folder.id);
            },
            ondragend: () => ((state.dragId = state.dropId = null), renderSidebar()),
            onclick: () => openFolder(folder),
            oncontextmenu: (event) => {
              event.preventDefault();
              showContextMenu({ kind: "folder", folder, x: event.clientX, y: event.clientY });
            },
          },
          icon,
          h("span.nt-nav__name", folder.name),
          h("span.nt-nav__count", String(folder.noteCount)),
        );
      }),
      state.folders.length === 0 ? h("p.nt-nav__none", "No folders yet.") : null,
    ),
  );
}

function renderHeading() {
  const folder = activeFolder();
  heading.textContent = folder?.name ?? VIEWS.find((item) => item.value === state.view)?.label ?? "Notes";
  searchInput.placeholder = folder ? `Search in ${folder.name}…` : "Search notes…";
}

function renderMain() {
  if (state.pane) {
    fill(main, state.pane.element);
    return;
  }
  renderHeading();
  layoutButton.setAttribute("aria-label", state.layout === "grid" ? "Switch to list view" : "Switch to grid view");
  fill(layoutButton, symbol(state.layout === "grid" ? "view_list" : "grid_view"));
  backButton.hidden = !state.folderId;
  emptyTrashButton.hidden = !(state.view === "trash" && state.notes.length > 0);
  sortSelect.value = state.sort;
  fill(main, mainHeader, bulkBar, body);
  renderBulk();
  renderBody();
}

function renderBulk() {
  if (state.pane) return;
  const count = state.selected.size;
  bulkBar.hidden = count === 0;
  if (count === 0) return;
  const button = (icon, label, run) => h("button.nt-bulk__btn", { type: "button", onclick: run }, symbol(icon), label);
  const moveSelect = h(
    "select.nt-select.nt-select--small",
    { "aria-label": "Move selected notes to folder", onchange: () => {
      const value = moveSelect.value;
      moveSelect.value = "";
      runBulk("move", value === "unfiled" ? null : Number(value));
    } },
    h("option", { value: "", disabled: true, selected: true }, "Move to…"),
    h("option", { value: "unfiled" }, "Unfiled"),
    state.folders.map((folder) => h("option", { value: String(folder.id) }, folder.name)),
  );
  fill(
    bulkBar,
    h("span.nt-bulk__count", `${count} selected`),
    h("span.nt-spacer"),
    state.view === "trash"
      ? [button("restore_from_trash", "Restore", () => runBulk("restore")), button("delete_forever", "Delete", async () => {
          if (await confirmAction({ title: "Delete these notes for good?", text: "They are removed with their pictures and files. This cannot be undone.", confirmLabel: "Delete permanently", danger: true })) runBulk("delete");
        })]
      : [button("push_pin", "Pin", () => runBulk("pin")), button("archive", "Archive", () => runBulk("archive")), moveSelect, button("delete", "Trash", () => runBulk("trash"))],
    button("close", "Clear", () => ((state.selected = new Set()), renderBulk(), renderBody())),
  );
}

function emptyState(icon, title, text, actionLabel, onAction) {
  return h("div.nt-empty", symbol(icon), h("h2", title), h("p", text), actionLabel ? h("button.nt-btn.nt-btn--primary", { type: "button", onclick: onAction }, symbol("add"), actionLabel) : null);
}

function noteList(heading, empty) {
  if (state.loading && state.notes.length === 0) return h("p.nt-loading", "Loading notes…");
  if (state.notes.length === 0) return empty;
  return h(
    "section",
    heading ? h("h2.nt-list__heading", heading) : null,
    h(
      "div.nt-notes" + (state.layout === "list" ? ".nt-notes--list" : ""),
      state.notes.map((note) =>
        noteCard({
          note,
          layout: state.layout,
          selected: state.selected.has(note.id),
          selectionMode: state.selected.size > 0,
          onOpen: (target) => openPane(target, null),
          onToggleSelect: toggleSelect,
          onContextMenu: (target, position) => showContextMenu({ kind: "note", note: target, ...position }),
        }),
      ),
    ),
  );
}

function renderBody() {
  if (state.pane) return;
  const folder = activeFolder();
  if (state.view === "attachments") {
    fill(body, attachmentsView({ onOpenNote: openNoteById }));
    return;
  }
  if (showFolderBrowser()) {
    const roots = rootFolders();
    fill(
      body,
      roots.length
        ? h("div.nt-folder-grid", roots.map((folder) => folderCard({ folder, onOpen: openFolder, onContextMenu: (target, position) => showContextMenu({ kind: "folder", folder: target, ...position }) })))
        : emptyState("folder", "Organise your notes", "Create your first folder to group lecture notes, revision plans, and everything else.", "New Folder", () => folderDialog(null)),
      noteList(roots.length ? "All notes" : null, emptyState("note_stack", "Create your first note", "Capture ideas, lecture notes, revision plans, and more.", "New Note", () => openPane(null, null))),
    );
    return;
  }
  const empty = state.query.trim()
    ? emptyState("search_off", "No notes found", "Try another search.")
    : state.view === "trash"
      ? emptyState("delete", "Trash is empty", "Deleted notes wait here before they're gone for good.")
      : state.view === "shared"
        ? emptyState("group", "Nothing shared with you", "Notes other students share will appear here.")
        : state.view === "archived"
          ? emptyState("archive", "Nothing archived", "Archived notes are hidden from your other views.")
          : emptyState("note_stack", folder ? `"${folder.name}" is empty` : "Create your first note", "Capture ideas, lecture notes, revision plans, and more.", "New Note", () => openPane(null, null));
  fill(body, noteList(null, empty));
}

async function openNoteById(id) {
  try {
    openPane(await getNote(id), null);
  } catch (failure) {
    failed(failure);
  }
}

// --- folder dialog and context menu -----------------------------------------------------------

function folderDialog(folder, parentId = "") {
  const dialog = openFolderDialog({ folder, folders: state.folders, defaultParentId: parentId, onSave: (input) => saveFolder(input, folder, dialog) });
}

function closeContextMenu() {
  contextMenu?.remove();
  contextMenu = null;
  window.removeEventListener("pointerdown", dismissContext);
  window.removeEventListener("scroll", closeContextMenu, true);
}
const dismissContext = (event) => {
  if (contextMenu && !contextMenu.contains(event.target)) closeContextMenu();
};

function showContextMenu(target) {
  closeContextMenu();
  const item = (icon, label, run, destructive = false) =>
    h("button.nt-menuitem" + (destructive ? ".is-destructive" : ""), { type: "button", role: "menuitem", onclick: () => { closeContextMenu(); run(); } }, symbol(icon), label);
  const sep = () => h("div.nt-menu-sep");
  let items;
  if (target.kind === "folder") {
    const folder = target.folder;
    items = [
      item("folder_open", "Open", () => openFolder(folder)),
      item("edit", "Rename & colour", () => folderDialog(folder)),
      item("note_add", "New note here", () => ((state.folderId = folder.id), (state.view = "all"), openPane(null, null))),
      item("create_new_folder", "New subfolder", () => folderDialog(null, folder.id)),
      sep(),
      item("delete", "Delete folder", () => removeFolder(folder), true),
    ];
  } else if (state.view === "trash") {
    items = [item("restore_from_trash", "Restore", () => guarded("restore", target.note)), sep(), item("delete_forever", "Delete permanently", () => guarded("delete", target.note), true)];
  } else {
    const note = target.note;
    items = [
      item("open_in_new", "Open", () => openPane(note, null)),
      item("push_pin", note.pinned ? "Unpin" : "Pin", () => guarded("pin", note)),
      item("content_copy", "Duplicate", () => guarded("duplicate", note)),
      item("archive", note.archived ? "Unarchive" : "Archive", () => guarded("archive", note)),
      sep(),
      item("delete", "Move to Trash", () => guarded("trash", note), true),
    ];
  }
  contextMenu = h("div.nt-context", { role: "menu" }, items);
  contextMenu.style.top = Math.max(8, Math.min(target.y, window.innerHeight - 280)) + "px";
  contextMenu.style.left = Math.max(8, Math.min(target.x, window.innerWidth - 220)) + "px";
  document.body.append(contextMenu);
  window.addEventListener("pointerdown", dismissContext);
  window.addEventListener("scroll", closeContextMenu, true);
}
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") closeContextMenu();
});

// --- start ------------------------------------------------------------------------------------

fill(mount, workspace);
render();
(async () => {
  const params = new URLSearchParams(location.search);
  await refreshFolders();
  const folderId = Number(params.get("folder"));
  const noteId = Number(params.get("note"));
  if (folderId && state.folders.some((folder) => folder.id === folderId)) {
    state.folderId = folderId;
    render();
  }
  await fetchNotes();
  if (noteId) await openNoteById(noteId);
})();
