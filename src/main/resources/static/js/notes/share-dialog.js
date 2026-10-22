import { fill, h, toast } from "../dom.js";
import { messageFor } from "../api.js";
import { addShareRecipient, getShare, removeShareRecipient, setLinkAccess, stopSharing } from "./notes-api.js";
import { openModal } from "./modal.js";
import { symbol } from "./symbols.js";

// Sharing controls for one note. Link access offers Private and "any student with the link can
// view" only: editing is never offered by link, because there would be no known author behind the
// write. Editing is granted per classmate instead, named by roll number or college email.

/** Options: noteId, noteTitle, onChanged(share). */
export function openShareDialog({ noteId, noteTitle, onChanged = () => {} }) {
  let share = null;
  let busy = false;
  const body = h("div");

  async function load() {
    fill(body, h("p.nt-loading", "Loading…"));
    try {
      share = await getShare(noteId);
    } catch (failure) {
      toast(messageFor(failure));
    }
    draw();
  }

  async function apply(work, message) {
    busy = true;
    draw();
    try {
      share = await work();
      onChanged(share);
      toast(message);
    } catch (failure) {
      toast(messageFor(failure));
    } finally {
      busy = false;
      draw();
    }
  }

  function draw() {
    const linkAccess = share?.linkAccess ?? "private";
    const shareUrl = share?.slug ? `${window.location.origin}/student/note-shared.html?slug=${share.slug}` : null;

    const options = [
      { value: "private", label: "Private", hint: "Only you and the classmates you add below" },
      { value: "view", label: "Any student with the link", hint: "Can view, but not edit. They must be signed in." },
    ].map((option) =>
      h(
        "label.nt-radio" + (linkAccess === option.value ? ".is-on" : ""),
        h("input", {
          type: "radio",
          name: "link-access",
          value: option.value,
          checked: linkAccess === option.value ? true : null,
          disabled: busy ? true : null,
          onchange: () => apply(() => setLinkAccess(noteId, option.value), option.value === "private" ? "Link sharing turned off" : "Any student with the link can now view"),
        }),
        h("span", h("span.nt-radio__label", option.label), h("span.nt-radio__hint", option.hint)),
      ),
    );

    const identifier = h("input.nt-input", { type: "text", placeholder: "Roll number or college email", "aria-label": "Roll number or college email to share with", autocomplete: "off", maxlength: "120" });
    const permission = h("select.nt-input.nt-input--short", { "aria-label": "Permission" }, h("option", { value: "view" }, "View"), h("option", { value: "edit" }, "Edit"));
    const add = h("form.nt-share__add", { onsubmit: (event) => {
      event.preventDefault();
      if (!identifier.value.trim()) return;
      const value = identifier.value.trim();
      apply(() => addShareRecipient(noteId, value, permission.value), "Note shared");
    } }, identifier, permission, h("button.nt-btn.nt-btn--primary.nt-btn--small", { type: "submit", disabled: busy ? true : null }, "Add"));

    const recipients = share?.recipients ?? [];
    fill(
      body,
      h(
        "fieldset.nt-fieldset",
        h("legend.nt-field__label", "Link access"),
        h("div.nt-radios", options),
        linkAccess === "view" && shareUrl
          ? h(
              "div.nt-share__link",
              h("span.nt-share__url", shareUrl),
              h("button.nt-btn.nt-btn--primary.nt-btn--small", { type: "button", onclick: async () => {
                try {
                  await navigator.clipboard.writeText(shareUrl);
                  toast("Link copied");
                } catch {
                  // Clipboard access can be denied outright; showing the address is better than a silent no-op.
                  toast("Copy failed. The link is " + shareUrl);
                }
              } }, "Copy"),
            )
          : null,
      ),
      h(
        "div.nt-share__people",
        h("h3.nt-field__label", "Classmates"),
        add,
        h(
          "ul.nt-share__list",
          recipients.length
            ? recipients.map((person) =>
                h(
                  "li.nt-share__person",
                  h("span.nt-avatar", (person.name || person.rollNo || "?").charAt(0).toUpperCase()),
                  h("span.nt-share__who", h("span.nt-share__name", person.name || person.rollNo || "Unknown"), h("span.nt-share__perm", `${person.rollNo ? person.rollNo + " · " : ""}${person.permission === "edit" ? "Can edit" : "Can view"}`)),
                  h("button.nt-tool.nt-tool--small", { type: "button", "aria-label": `Remove ${person.name || person.rollNo}`, disabled: busy ? true : null, onclick: () => apply(() => removeShareRecipient(noteId, person.recipientId), "Access removed") }, symbol("close")),
                ),
              )
            : [h("li.nt-share__none", "Not shared with anyone yet.")],
        ),
      ),
      linkAccess !== "private" || recipients.length > 0
        ? h("button.nt-btn.nt-btn--danger.nt-btn--block", { type: "button", disabled: busy ? true : null, onclick: () => apply(() => stopSharing(noteId), "Sharing turned off") }, "Stop sharing")
        : null,
    );
  }

  const dialog = openModal({
    label: `Share ${noteTitle || "note"}`,
    className: "nt-modal--wide",
    children: [
      h("div.nt-share__head", h("div", h("h2", "Share note"), h("p.nt-share__title", noteTitle || "Untitled")), h("button.nt-tool", { type: "button", "aria-label": "Close", onclick: () => dialog.close() }, symbol("close"))),
      body,
    ],
  });
  load();
  return dialog;
}
