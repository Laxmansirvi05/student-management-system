import { api, messageFor } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { reveal } from "./motion.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Complaints (FR-CMP-001 to FR-CMP-003, ADR 0013): the public board, the student's own complaints,
// a form to raise one, and support for public complaints. The server decides who sees what; this
// page only draws what comes back.

const PAGE_SIZE = 20;
const TYPES = {
  PUBLIC: { label: "Public", colour: "primary", text: "Every student and the administrator can read it, and students can support it. You choose whether other students see your roll number. The administrator can always see who raised it." },
  PRIVATE: { label: "Private", colour: "warning", text: "Only the administrator can read it. The administrator sees who you are." },
  SECURE: { label: "Secure", colour: "success", text: "Only the administrator can read it, and the administrator never sees who raised it. You still follow it and read the response here. Use it for serious matters." },
};
const CATEGORIES = { ACADEMIC: "Academic", EXAMS: "Exams", FACILITIES: "Facilities", FACULTY: "Faculty", ADMINISTRATION: "Administration", OTHER: "Other" };
const STATUSES = { OPEN: ["Open", "warning"], IN_REVIEW: ["In review", "primary"], RESOLVED: ["Resolved", "success"], REJECTED: ["Rejected", "danger"] };
const state = { view: "board", category: "", status: "", sort: "newest", page: 0 };

const content = document.getElementById("content");
const list = h("div.stack.stack--loose", { "aria-live": "polite" }, h("div.skeleton.skeleton--card"));
const viewBar = h("div.pills", { role: "group", "aria-label": "View" });
const filterBar = h("div.filters");
let latest = 0;

function statusTag(status) {
  const [label, colour] = STATUSES[status];
  return tag(label, colour);
}

function supportButton(complaint, onChange) {
  const button = h("button.button.button--secondary.button--small", { type: "button", "aria-pressed": String(complaint.supported) });
  const show = () => {
    fill(button, icon("check-circle"), complaint.supported ? "Supported" : "I face this too", h("span.pill__count", complaint.supporters));
    button.setAttribute("aria-pressed", String(complaint.supported));
  };
  show();
  button.disabled = complaint.mine || complaint.status === "RESOLVED";
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      const updated = complaint.supported ? await api.delete(`/complaints/${complaint.id}/support`) : await api.put(`/complaints/${complaint.id}/support`);
      Object.assign(complaint, updated);
      show();
      onChange?.(complaint);
    } catch (failure) {
      toast(messageFor(failure));
    } finally {
      button.disabled = complaint.mine || complaint.status === "RESOLVED";
    }
  });
  return button;
}

function card(complaint, reload) {
  const type = TYPES[complaint.type];
  const author = complaint.type !== "PUBLIC" ? null : complaint.mine ? "Raised by you" : complaint.authorRollNo ? "Raised by " + complaint.authorRollNo : "Raised anonymously";
  const actions = h("div.line.line--wrap");
  if (complaint.type === "PUBLIC" && !complaint.mine) {
    actions.append(supportButton(complaint));
  }
  if (complaint.mine && complaint.status === "OPEN") {
    const withdraw = h("button.button.button--quiet.button--small", { type: "button" }, icon("trash"), "Withdraw");
    withdraw.addEventListener("click", async () => {
      if (!window.confirm("Withdraw this complaint? It is deleted for everyone.")) {
        return;
      }
      try {
        await api.delete("/complaints/" + complaint.id);
        toast("Complaint withdrawn");
        reload();
      } catch (failure) {
        toast(messageFor(failure));
      }
    });
    actions.append(withdraw);
  }
  return h(
    "article.card.reveal",
    h("div.line.line--start.line--wrap", tag(type.label, type.colour), tag(CATEGORIES[complaint.category], ""), statusTag(complaint.status), complaint.group ? tag("Group complaint", "danger", "alert") : null, complaint.mine ? tag("Yours", "success") : null),
    h("h3.subject__name", complaint.title),
    h("p.complaint__text", complaint.description),
    h("p.row__meta.num", [author, formatDate(complaint.createdAt), complaint.type === "PUBLIC" && complaint.supporters ? complaint.supporters + (complaint.supporters === 1 ? " supporter" : " supporters") : null].filter(Boolean).join(" · ")),
    complaint.adminResponse ? h("div.inset", h("p.eyebrow", "Response from the administrator"), h("p.complaint__text", complaint.adminResponse)) : null,
    actions,
  );
}

function pager(page) {
  const move = (label, iconName, target, before) => {
    const element = h("button.button.button--secondary.button--small", { type: "button", disabled: target < 0 || target >= page.totalPages }, before ? icon(iconName) : null, label, before ? null : icon(iconName));
    element.addEventListener("click", () => {
      state.page = target;
      load();
    });
    return element;
  };
  return h("nav.pager", { "aria-label": "Pages" }, move("Previous", "arrow-left", page.page - 1, true), h("span.row__meta.num", `Page ${page.page + 1} of ${page.totalPages}`), move("Next", "arrow-right", page.page + 1, false));
}

function load() {
  const mine = ++latest;
  const url = state.view === "mine" ? `/complaints/mine?page=${state.page}&size=${PAGE_SIZE}` : "/complaints?" + new URLSearchParams({ page: state.page, size: PAGE_SIZE, sort: state.sort, ...(state.category && { category: state.category }), ...(state.status && { status: state.status }) });
  loadInto(list, () => api.get(url), (page) => {
    if (mine !== latest) {
      return;
    }
    fill(
      list,
      page.content.length
        ? [...page.content.map((complaint) => card(complaint, load)), page.totalPages > 1 ? pager(page) : null]
        : h("div.card", state.view === "mine" ? empty("alert", "You have not raised a complaint", "Use Raise a complaint to tell the college about a problem.") : empty("alert", "No public complaints", "Nothing has been raised publicly yet, or nothing matches the filters.")),
    );
    reveal();
  });
}

function select(id, label, options, current, onChange) {
  const element = h("select.select", { id }, Object.entries(options).map(([value, text]) => h("option", { value }, text)));
  element.value = current;
  element.addEventListener("change", () => onChange(element.value));
  return h("label.filter", { for: id }, h("span.visually-hidden", label), element);
}

function drawControls() {
  fill(
    viewBar,
    [["board", "Public board"], ["mine", "My complaints"]].map(([value, label]) => {
      const pill = h("button.pill", { type: "button", "aria-pressed": String(state.view === value) }, label);
      pill.addEventListener("click", () => {
        state.view = value;
        state.page = 0;
        drawControls();
        load();
      });
      return pill;
    }),
  );
  filterBar.hidden = state.view !== "board";
  const change = (name) => (value) => {
    state[name] = value;
    state.page = 0;
    load();
  };
  fill(filterBar, select("c-category", "Category", { "": "All categories", ...CATEGORIES }, state.category, change("category")), select("c-status", "Status", { "": "Any status", OPEN: "Open", IN_REVIEW: "In review", RESOLVED: "Resolved" }, state.status, change("status")), select("c-sort", "Sort", { newest: "Newest first", top: "Most supported" }, state.sort, change("sort")));
}

function raiseDialog() {
  const chosen = { type: "PUBLIC" };
  const error = h("p.field__error", { hidden: true });
  const explain = h("p.row__meta");
  const identity = h("input", { type: "checkbox", id: "c-identity" });
  const identityRow = h("label.line.line--start", { for: "c-identity" }, identity, "Show my roll number to other students");
  const identityNote = h("p.field__help", "If you leave this off, other students see “Anonymous”. The administrator can still see who you are. Choose Secure to hide yourself from the administrator too.");
  const category = h("select.select", { id: "c-new-category" }, Object.entries(CATEGORIES).map(([value, text]) => h("option", { value }, text)));
  const title = h("input.textbox", { id: "c-title", maxlength: 120, autocomplete: "off" });
  const description = h("textarea.textbox", { id: "c-description", rows: 6, maxlength: 2000 });
  const count = h("span.row__meta.num");
  const choices = Object.entries(TYPES).map(([value, type]) => {
    const radio = h("input", { type: "radio", name: "c-type", id: "c-type-" + value, value });
    radio.checked = value === chosen.type;
    radio.addEventListener("change", () => {
      chosen.type = value;
      refresh();
    });
    return h("label.card.card--choice", { for: "c-type-" + value }, h("div.line.line--start", radio, h("strong", type.label)), h("p.row__meta", type.text));
  });
  const refresh = () => {
    explain.textContent = TYPES[chosen.type].text;
    identityRow.hidden = identityNote.hidden = chosen.type !== "PUBLIC";
  };
  const submit = h("button.button.button--primary", { type: "submit" }, "Submit complaint");
  const cancel = h("button.button.button--quiet", { type: "button", "data-close": true }, "Cancel");
  const form = h(
    "form.stack.stack--loose",
    h("h2", { id: "c-form-title" }, "Raise a complaint"),
    h("fieldset.stack", h("legend.field__label", "Who should see it?"), h("div.grid.grid--3", choices)),
    h("div.field", h("label.field__label", { for: "c-new-category" }, "Category"), category),
    h("div.field", h("label.field__label", { for: "c-title" }, "Title"), title),
    h("div.field", h("div.line", h("label.field__label", { for: "c-description" }, "What is the problem?"), count), description, h("p.field__help", "Plain text, 20 to 2,000 characters. Do not put passwords or other secrets in it.")),
    identityRow,
    identityNote,
    error,
    h("div.dialog__actions", cancel, submit),
  );
  const dialog = h("dialog.dialog.dialog--wide", { "aria-labelledby": "c-form-title" }, form);
  description.addEventListener("input", () => (count.textContent = description.value.length + " / 2000"));
  count.textContent = "0 / 2000";
  refresh();
  dialog.addEventListener("click", (event) => {
    if (event.target === dialog || event.target.closest("[data-close]")) {
      dialog.close();
    }
  });
  dialog.addEventListener("close", () => dialog.remove());
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    error.hidden = true;
    submit.disabled = true;
    try {
      await api.post("/complaints", { type: chosen.type, category: category.value, title: title.value.trim(), description: description.value.trim(), showIdentity: chosen.type === "PUBLIC" && identity.checked });
      dialog.close();
      toast("Complaint submitted");
      state.view = chosen.type === "PUBLIC" ? "board" : "mine";
      state.page = 0;
      drawControls();
      load();
    } catch (failure) {
      error.textContent = failure.fieldErrors?.length ? failure.fieldErrors.map((item) => item.message).join(" ") : messageFor(failure);
      error.hidden = false;
    } finally {
      submit.disabled = false;
    }
  });
  document.body.append(dialog);
  dialog.showModal();
  title.focus();
}

const raise = h("button.button.button--primary", { type: "button" }, icon("plus"), "Raise a complaint");
raise.addEventListener("click", raiseDialog);
fill(document.getElementById("actions"), raise);
fill(content, h("section.card", { "aria-label": "Views and filters" }, viewBar, filterBar), list);
drawControls();
load();
