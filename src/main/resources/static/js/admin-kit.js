// Building blocks for the admin screens: form fields, a form in a dialog, a confirmation, a data
// table and a pager. Each admin page describes its data and lets these pieces draw it.

import { ApiError, messageFor } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";

/** A text, number, date or time input. Its id is "f-" plus the field name the server uses. */
export function input(name, attributes = {}) {
  return h("input.textbox", { id: "f-" + name, name, type: "text", autocomplete: "off", ...attributes });
}

export function textarea(name, attributes = {}) {
  return h("textarea.textbox.textbox--area", { id: "f-" + name, name, ...attributes });
}

/** A dropdown. options is a list of [value, label]. */
export function select(name, options, chosen = "") {
  return h(
    "select.select",
    { id: "f-" + name, name },
    options.map(([value, label]) => h("option", { value, selected: String(value) === String(chosen) }, label)),
  );
}

/** A dropdown whose value is sent to the server as a number, for example a semester. */
export function numberSelect(name, options, chosen = "") {
  const element = select(name, options, chosen);
  element.dataset.number = "";
  return element;
}

export function checkbox(name, label, checked = false) {
  const box = h("input.checkbox", { id: "f-" + name, name, type: "checkbox", checked });
  return h("label.check", { for: "f-" + name }, box, h("span", label));
}

/** A labelled field with room for a hint and for the server's error message. */
export function field(label, control, hint = "") {
  const id = (control.matches?.("input, select, textarea") ? control : control.querySelector("input, select, textarea")).id;
  const isCheck = control.classList.contains("check");
  return h(
    "div.field",
    isCheck ? null : h("label.field__label", { for: id }, label),
    control,
    hint ? h("p.field__help", hint) : null,
    h("p.field__error", { id: id + "-error", hidden: true }),
  );
}

/** Reads every named control of a form into an object, with numbers and booleans as such. */
export function valuesOf(form) {
  const values = {};
  for (const control of form.querySelectorAll("[name]")) {
    if (control.type === "checkbox") {
      values[control.name] = control.checked;
    } else if (control.type === "number" || control.dataset.number !== undefined) {
      values[control.name] = control.value === "" ? null : Number(control.value);
    } else {
      values[control.name] = control.value.trim() === "" ? null : control.value.trim();
    }
  }
  return values;
}

function clearErrors(form) {
  for (const error of form.querySelectorAll(".field__error")) {
    error.hidden = true;
    error.textContent = "";
  }
  for (const control of form.querySelectorAll("[aria-invalid]")) {
    control.removeAttribute("aria-invalid");
  }
}

/**
 * Shows the server's validation messages under their fields. Returns the messages that belong to
 * no field on the form, so the caller can show them at the top.
 */
export function showFieldErrors(form, error) {
  const others = [];
  for (const { field: name, message } of error.fieldErrors || []) {
    const control = form.querySelector(`[name="${name}"]`);
    const slot = control && document.getElementById(control.id + "-error");
    if (slot) {
      control.setAttribute("aria-invalid", "true");
      slot.textContent = message;
      slot.hidden = false;
    } else {
      others.push(message);
    }
  }
  form.querySelector("[aria-invalid]")?.focus();
  return others;
}

/** An empty modal dialog, labelled by the element with labelId. It removes itself when closed. */
export function dialogShell(labelId) {
  const dialog = h("dialog.dialog", { "aria-labelledby": labelId });
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog || event.target.closest("[data-close]")) {
      dialog.close();
    }
  });
  dialog.addEventListener("close", () => dialog.remove());
  document.body.append(dialog);
  return dialog;
}

/**
 * Opens a form in a dialog. fields is a list of elements made with field(). onSubmit receives the
 * values and returns a promise; when the server refuses, its messages appear under the fields.
 */
export function openForm({ title, intro = "", fields, submitLabel = "Save", onSubmit, wide = false }) {
  const dialog = dialogShell("form-title");
  if (wide) {
    dialog.classList.add("dialog--wide");
  }
  const banner = h("p.inset.inset--danger.inset--note", { hidden: true, role: "alert" });
  const submit = h("button.button.button--primary", { type: "submit" }, submitLabel);
  const form = h(
    "form.stack.stack--loose",
    { novalidate: true },
    h("div.stack.stack--tight", h("h2", { id: "form-title" }, title), intro ? h("p.muted", intro) : null),
    banner,
    h("div.form-grid", fields),
    h("div.dialog__actions", h("button.button.button--quiet", { type: "button", "data-close": true }, "Cancel"), submit),
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearErrors(form);
    banner.hidden = true;
    submit.disabled = true;
    try {
      await onSubmit(valuesOf(form));
      dialog.close();
    } catch (failure) {
      const others = failure instanceof ApiError && failure.fieldErrors.length ? showFieldErrors(form, failure) : [messageFor(failure)];
      if (others.length) {
        fill(banner, icon("alert"), others.join(" "));
        banner.hidden = false;
      }
    } finally {
      submit.disabled = false;
    }
  });
  fill(dialog, form);
  dialog.showModal();
  return dialog;
}

/** Asks before something that matters. Resolves to true when the admin confirms. */
export function confirmAction({ title, text, confirmLabel = "Confirm", danger = false }) {
  return new Promise((resolve) => {
    const dialog = dialogShell("confirm-title");
    let confirmed = false;
    const yes = h("button.button" + (danger ? ".button--danger" : ".button--primary"), { type: "button" }, confirmLabel);
    yes.addEventListener("click", () => {
      confirmed = true;
      dialog.close();
    });
    dialog.addEventListener("close", () => resolve(confirmed));
    fill(
      dialog,
      h(
        "div.stack.stack--loose",
        h("div.stack.stack--tight", h("h2", { id: "confirm-title" }, title), h("p.muted", text)),
        h("div.dialog__actions", h("button.button.button--quiet", { type: "button", "data-close": true }, "Cancel"), yes),
      ),
    );
    dialog.showModal();
  });
}

/** Shows something once in a dialog, for example a temporary password. */
export function showOnce({ title, text, value }) {
  const dialog = dialogShell("once-title");
  const copy = h("button.button.button--secondary.button--small", { type: "button" }, icon("copy"), "Copy");
  copy.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(value);
      toast("Copied");
    } catch {
      toast("Could not copy");
    }
  });
  fill(
    dialog,
    h(
      "div.stack.stack--loose",
      h("div.stack.stack--tight", h("h2", { id: "once-title" }, title), h("p.muted", text)),
      h("div.inset.inset--row", h("strong.mono.once__value", value), copy),
      h("div.dialog__actions", h("button.button.button--primary", { type: "button", "data-close": true }, "Done")),
    ),
  );
  dialog.showModal();
}

/**
 * A table. columns is a list of { label, cell, align }, where cell turns a row into text or an
 * element. On phones each row becomes a small card with the labels beside the values.
 */
export function dataTable(columns, rows, emptyText = "Nothing to show.") {
  if (!rows.length) {
    return h("p.table__empty.muted", emptyText);
  }
  return h(
    "div.table-wrap",
    h(
      "table.table",
      h("thead", h("tr", columns.map((column) => h("th" + (column.align === "end" ? ".table__end" : ""), { scope: "col" }, column.label)))),
      h(
        "tbody",
        rows.map((row) =>
          h(
            "tr",
            columns.map((column) =>
              h("td" + (column.align === "end" ? ".table__end" : ""), { "data-label": column.label }, column.cell(row)),
            ),
          ),
        ),
      ),
    ),
  );
}

/** Previous and Next for a paged list. page is the server's page object. */
export function pager(page, onChange) {
  if (page.totalPages <= 1) {
    return null;
  }
  const button = (label, iconName, target, before) => {
    const element = h("button.button.button--secondary.button--small", { type: "button", disabled: target < 0 || target >= page.totalPages }, before ? icon(iconName) : null, label, before ? null : icon(iconName));
    element.addEventListener("click", () => onChange(target));
    return element;
  };
  return h(
    "nav.pager",
    { "aria-label": "Pages" },
    button("Previous", "arrow-left", page.page - 1, true),
    h("span.row__meta.num", "Page ", h("strong", page.page + 1), " of " + page.totalPages),
    button("Next", "arrow-right", page.page + 1, false),
  );
}

/** A small button for a row of a table. */
export function rowAction(label, iconName, onClick, kind = "") {
  const button = h("button.row-action" + (kind ? ".row-action--" + kind : ""), { type: "button", title: label, "aria-label": label }, icon(iconName));
  button.addEventListener("click", onClick);
  return button;
}

/** Runs a request and reports failure in a toast-like banner at the top of the page. */
export async function attempt(action, successMessage = "") {
  try {
    const result = await action();
    if (successMessage) {
      toast(successMessage);
    }
    return result;
  } catch (failure) {
    const banner = document.getElementById("page-error");
    if (banner) {
      fill(banner, icon("alert"), messageFor(failure));
      banner.hidden = false;
      banner.scrollIntoView({ behavior: "smooth", block: "nearest" });
    }
    throw failure;
  }
}

export function hidePageError() {
  const banner = document.getElementById("page-error");
  if (banner) {
    banner.hidden = true;
  }
}

/** [value, label] pairs for the eight semesters, with an optional first "all" choice. */
export function semesterOptions(allLabel = "") {
  const numbers = Array.from({ length: 8 }, (_, index) => [String(index + 1), "Semester " + (index + 1)]);
  return allLabel ? [["", allLabel], ...numbers] : numbers;
}

/** "rows[4].fullName" becomes "Line 4"; problems with the header, the period or the file say so. */
function whereOf(field) {
  const row = /^rows\[(\d+)\]/.exec(field || "");
  if (row) {
    return "Line " + row[1];
  }
  if (field === "fromDate" || field === "toDate") {
    return "Period";
  }
  return field === "header" ? "First line" : "File";
}

/** The problems of a refused CSV import, one per line of the file, with "nothing was imported". */
export function importProblems(failure) {
  const items = failure.fieldErrors.map(({ field: where, message }) => h("li.import__problem", h("span.import__where", whereOf(where)), h("span", message)));
  return h(
    "div.stack.stack--tight",
    h("p.inset.inset--danger.inset--note", { role: "alert" }, icon("alert"), "Nothing was imported. Fix these lines in the file and choose it again."),
    h("ul.import__problems", { "aria-label": "Problems in the file" }, items),
  );
}
