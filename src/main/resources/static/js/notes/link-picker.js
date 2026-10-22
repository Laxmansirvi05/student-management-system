import { fill, h, toast } from "../dom.js";
import { messageFor } from "../api.js";
import { addLink, searchLinkTargets } from "./notes-api.js";
import { openModal } from "./modal.js";
import { symbol } from "./symbols.js";

// Picks something for a note to reference: another note, a folder, or a learning resource. The
// reference stores an id, never a copy: the linked note stays the single source of truth, so
// editing it updates every note pointing at it.

const ICONS = { note: "description", folder: "folder", resource: "menu_book" };
const GROUP_LABELS = { note: "Notes", folder: "Folders", resource: "Resources" };
const SEARCH_DEBOUNCE_MS = 250;

/** Options: noteId, onLinked(links). */
export function openLinkPicker({ noteId, onLinked }) {
  let timer = null;
  let token = 0;
  const results = h("div.nt-picker__results");
  const input = h("input.nt-picker__input", { type: "text", placeholder: "Search notes, folders, resources…", "aria-label": "Search for something to link", oninput: schedule });

  const hint = (text) => h("p.nt-picker__hint", text);
  fill(results, hint("Type at least two characters to search."));

  function schedule() {
    clearTimeout(timer);
    const query = input.value.trim();
    timer = setTimeout(() => search(query), SEARCH_DEBOUNCE_MS);
  }

  async function search(query) {
    const mine = ++token;
    if (query.length < 2) {
      fill(results, hint("Type at least two characters to search."));
      return;
    }
    fill(results, hint("Searching…"));
    let targets = [];
    try {
      targets = await searchLinkTargets(query, noteId);
    } catch {
      targets = [];
    }
    if (mine !== token) return;
    if (targets.length === 0) {
      fill(results, hint("Nothing matched that search."));
      return;
    }
    const groups = {};
    for (const target of targets) (groups[target.type] ||= []).push(target);
    fill(
      results,
      Object.entries(groups).map(([type, items]) =>
        h(
          "div",
          h("p.nt-picker__group", GROUP_LABELS[type]),
          items.map((target) =>
            h(
              "button.nt-picker__item",
              {
                type: "button",
                onclick: async () => {
                  try {
                    onLinked(await addLink(noteId, target.type, target.id));
                    toast("Link added");
                    dialog.close();
                  } catch (failure) {
                    toast(messageFor(failure));
                  }
                },
              },
              symbol(ICONS[target.type]),
              h("span.nt-picker__text", h("span.nt-picker__label", target.label), target.sublabel ? h("span.nt-picker__sub", target.sublabel) : null),
            ),
          ),
        ),
      ),
    );
  }

  const dialog = openModal({
    label: "Link something to this note",
    className: "nt-modal--picker",
    onClose: () => clearTimeout(timer),
    children: [
      h("div.nt-picker__bar", symbol("search"), input, h("button.nt-tool", { type: "button", "aria-label": "Close", onclick: () => dialog.close() }, symbol("close"))),
      results,
    ],
  });
  input.focus();
  return dialog;
}
