import { fill, h } from "../dom.js";

// A modal dialog in the style of the notes workspace: the page behind is dimmed, Escape and a click
// on the backdrop close it, and focus returns to what had it. It is the native <dialog> element, so
// focus stays inside it while it is open.

/**
 * Opens a dialog with the given content. `label` names it for screen readers. `onClose` runs once
 * when it closes for any reason. Returns the dialog; call dialog.close() to close it.
 */
export function openModal({ label, className = "", children = [], onClose = () => {}, labelledBy = null }) {
  const returnTo = document.activeElement;
  const dialog = h("dialog.dialog.nt-modal" + (className ? "." + className.split(" ").join(".") : ""), {
    "aria-label": labelledBy ? null : label,
    "aria-labelledby": labelledBy,
  });
  fill(dialog, ...children);
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog) {
      dialog.close();
    }
  });
  dialog.addEventListener("close", () => {
    dialog.remove();
    onClose();
    if (returnTo && returnTo.isConnected && typeof returnTo.focus === "function") {
      returnTo.focus({ preventScroll: true });
    }
  });
  document.body.append(dialog);
  dialog.showModal();
  return dialog;
}
