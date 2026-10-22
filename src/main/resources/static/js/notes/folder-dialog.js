import { h } from "../dom.js";
import { FOLDER_COLORS, paletteFor } from "./folder-colors.js";
import { openModal } from "./modal.js";
import { symbol } from "./symbols.js";

// Create or edit a folder: name, colour, icon, and where it sits. The colour preview is a real
// folder shape rather than a swatch, because colour here is identity: what the writer is choosing
// is how this folder will be recognised in the browser at a glance.

/** A deliberately short list: an icon picker with 900 options is a menu, not a choice. */
const ICONS = ["folder", "school", "work", "code", "lightbulb", "science", "psychology", "rocket_launch", "menu_book", "groups", "star", "flag"];

/** Options: folder (or null for a new one), folders (all, for the parent list), defaultParentId, onSave(input). */
export function openFolderDialog({ folder, folders, defaultParentId = "", onSave }) {
  let name = folder?.name ?? "";
  let color = folder?.color ?? "blue";
  let icon = folder?.icon ?? "folder";
  let parentId = folder ? (folder.parentId ?? "") : defaultParentId;

  const nameInput = h("input.nt-input", { type: "text", maxlength: "60", placeholder: "Semester 5 revision", value: name, "aria-label": "Folder name", oninput: () => ((name = nameInput.value), refresh()) });

  const previewBack = h("div.nt-fpreview__back");
  const previewTab = h("div.nt-fpreview__tab");
  const previewFront = h("div.nt-fpreview__front", symbol(icon));
  const preview = h("div.nt-fpreview", { "aria-hidden": "true" }, previewBack, previewTab, previewFront);

  const colourButtons = FOLDER_COLORS.map((option) => {
    const palette = paletteFor(option);
    const button = h("button.nt-colour", { type: "button", "aria-label": palette.label, title: palette.label, onclick: () => ((color = option), refresh()) });
    button.style.background = `linear-gradient(160deg, ${palette.frontFrom}, ${palette.back})`;
    return [option, button];
  });

  const iconButtons = ICONS.map((option) => [
    option,
    h("button.nt-tool", { type: "button", "aria-label": option.replace(/_/g, " "), onclick: () => ((icon = option), refresh()) }, symbol(option)),
  ]);

  const parents = folders.filter((option) => option.id !== folder?.id);
  const parentSelect =
    parents.length > 0
      ? h(
          "select.nt-input",
          { "aria-label": "Inside", onchange: () => (parentId = parentSelect.value ? Number(parentSelect.value) : "") },
          h("option", { value: "" }, "Top level"),
          parents.map((option) => h("option", { value: String(option.id), selected: option.id === parentId ? true : null }, option.name)),
        )
      : null;

  const save = h("button.nt-btn.nt-btn--primary", { type: "submit" }, folder ? "Save" : "Create folder");

  function refresh() {
    const palette = paletteFor(color);
    previewBack.style.backgroundColor = palette.back;
    previewTab.style.backgroundColor = palette.tab;
    previewFront.style.background = `linear-gradient(160deg, ${palette.frontFrom} 0%, ${palette.frontTo} 100%)`;
    previewFront.style.color = palette.ink;
    previewFront.firstChild.textContent = icon;
    for (const [option, button] of colourButtons) {
      button.classList.toggle("is-on", option === color);
      button.setAttribute("aria-pressed", String(option === color));
    }
    for (const [option, button] of iconButtons) {
      button.classList.toggle("is-on", option === icon);
      button.setAttribute("aria-pressed", String(option === icon));
    }
    save.disabled = !name.trim();
  }

  const form = h(
    "form",
    {
      onsubmit: (event) => {
        event.preventDefault();
        if (!name.trim()) return;
        onSave({ name: name.trim(), color, icon, parentId: parentId || null });
      },
    },
    h("h2", folder ? "Edit folder" : "New folder"),
    h("div.nt-fpreview-wrap", preview),
    h("label.nt-field", h("span.nt-field__label", "Name"), nameInput),
    h("fieldset.nt-fieldset", h("legend.nt-field__label", "Colour"), h("div.nt-colours", colourButtons.map(([, button]) => button))),
    h("fieldset.nt-fieldset", h("legend.nt-field__label", "Icon"), h("div.nt-icons", iconButtons.map(([, button]) => button))),
    parentSelect ? h("label.nt-field", h("span.nt-field__label", "Inside"), parentSelect) : null,
    h("div.nt-modal__actions", h("button.nt-btn.nt-btn--quiet", { type: "button", onclick: () => dialog.close() }, "Cancel"), save),
  );
  const dialog = openModal({ label: folder ? "Edit folder" : "New folder", children: [form] });
  refresh();
  nameInput.focus();
  return dialog;
}
