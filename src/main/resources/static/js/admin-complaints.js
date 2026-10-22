import { dataTable, dialogShell, field, hidePageError, pager, rowAction, select, textarea } from "./admin-kit.js";
import { api, messageFor } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: complaints (FR-CMP-004, ADR 0013). Every complaint, filtered by type and status, and the
// handling of one: a status and a written response. A secure complaint never shows its student:
// the server leaves the student out, and this page shows a sentence in its place.

const TYPE_LABELS = { PUBLIC: ["Public", "primary"], PRIVATE: ["Private", "warning"], SECURE: ["Secure", "success"] };
const STATUS_LABELS = { OPEN: ["Open", "warning"], IN_REVIEW: ["In review", "primary"], RESOLVED: ["Resolved", "success"], REJECTED: ["Rejected", "danger"] };
const CATEGORY_LABELS = { ACADEMIC: "Academic", EXAMS: "Exams", FACILITIES: "Facilities", FACULTY: "Faculty", ADMINISTRATION: "Administration", OTHER: "Other" };
const filters = { type: "", status: "", page: 0 };
const content = document.getElementById("content");

function who(complaint) {
  if (complaint.identityProtected) {
    return "Protected (secure)";
  }
  const student = complaint.student;
  return student ? `${student.rollNo} · ${student.fullName}` : "—";
}

async function open(id) {
  let complaint;
  try {
    complaint = await api.get("/admin/complaints/" + id);
  } catch (failure) {
    toast(messageFor(failure));
    return;
  }
  const dialog = dialogShell("complaint-title");
  dialog.classList.add("dialog--wide");
  const status = select("status", Object.entries(STATUS_LABELS).map(([value, [label]]) => [value, label]), complaint.status);
  const response = textarea("response", { maxlength: 2000 });
  response.value = complaint.adminResponse || "";
  const error = h("p.field__error", { hidden: true });
  const save = h("button.button.button--primary", { type: "button" }, "Save");
  const close = h("button.button.button--quiet", { type: "button", "data-close": true }, "Close");
  save.addEventListener("click", async () => {
    save.disabled = true;
    error.hidden = true;
    try {
      await api.put("/admin/complaints/" + id, { status: status.value, response: response.value.trim() || null });
      toast("Saved");
      dialog.close();
      load();
    } catch (failure) {
      error.textContent = failure.fieldErrors?.length ? failure.fieldErrors.map((item) => item.message).join(" ") : messageFor(failure);
      error.hidden = false;
    } finally {
      save.disabled = false;
    }
  });
  const [typeLabel, typeColour] = TYPE_LABELS[complaint.type];
  const identity = complaint.identityProtected
    ? h("p.inset.inset--note", icon("shield"), "This is a secure complaint. The student's identity is not available to you, by design.")
    : h(
        "div.inset",
        h("p.eyebrow", "Raised by"),
        h("p", h("strong", who(complaint)), complaint.student ? ` · ${complaint.student.branch}, semester ${complaint.student.semester}, section ${complaint.student.section}` : ""),
        complaint.hiddenFromStudents ? h("p.row__meta", "The student chose to hide their roll number from other students.") : null,
      );
  fill(
    dialog,
    h(
      "div.stack.stack--loose",
      h("div.line.line--start.line--wrap", tag(typeLabel, typeColour), tag(CATEGORY_LABELS[complaint.category], ""), complaint.group ? tag("Group complaint", "danger", "alert") : null),
      h("h2", { id: "complaint-title" }, complaint.title),
      h("p.row__meta.num", "Raised " + formatDate(complaint.createdAt)),
      h("p.complaint__text", complaint.description),
      identity,
      complaint.type === "PUBLIC"
        ? h("div.stack.stack--tight", h("p.eyebrow", complaint.supporters + (complaint.supporters === 1 ? " supporter" : " supporters")), complaint.supportedBy.length ? h("ul.rows", complaint.supportedBy.map((student) => h("li.row", h("span.row__title", student.rollNo), h("span.row__meta", `${student.fullName} · ${student.branch}`)))) : null)
        : null,
      field("Status", status),
      field("Response to the student", response, "The student, and for a public complaint every student, can read it."),
      error,
      h("div.dialog__actions", close, save),
    ),
  );
  dialog.showModal();
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ size: 20, page: filters.page });
  for (const name of ["type", "status"]) {
    if (filters[name]) {
      params.set(name, filters[name]);
    }
  }
  loadInto(list, () => api.get("/admin/complaints?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 complaint" : page.totalElements + " complaints"),
      dataTable(
        [
          { label: "Complaint", cell: (c) => h("div.stack.stack--tight", h("strong", c.title), h("span.row__meta", `${CATEGORY_LABELS[c.category]} · ${formatDate(c.createdAt)}`)) },
          { label: "Type", cell: (c) => tag(TYPE_LABELS[c.type][0], TYPE_LABELS[c.type][1]) },
          { label: "Raised by", cell: (c) => who(c) },
          { label: "Supporters", cell: (c) => (c.type === "PUBLIC" ? c.supporters + (c.group ? " · group" : "") : "—") },
          { label: "Status", cell: (c) => tag(STATUS_LABELS[c.status][0], STATUS_LABELS[c.status][1]) },
          { label: "Actions", align: "end", cell: (c) => h("span.row-actions", rowAction("Open: " + c.title, "eye", () => open(c.id))) },
        ],
        page.content,
        "No complaints yet.",
      ),
      pager(page, (target) => {
        filters.page = target;
        load();
      }),
    );
  });
}

function filterBar() {
  const type = select("type", [["", "All types"], ...Object.entries(TYPE_LABELS).map(([value, [label]]) => [value, label])], "");
  const status = select("status", [["", "Any status"], ...Object.entries(STATUS_LABELS).map(([value, [label]]) => [value, label])], "");
  type.addEventListener("change", () => {
    filters.type = type.value;
    filters.page = 0;
    load();
  });
  status.addEventListener("change", () => {
    filters.status = status.value;
    filters.page = 0;
    load();
  });
  return h("section.card", { "aria-label": "Filters" }, h("div.line.line--start.line--wrap", field("Type", type), field("Status", status)));
}

fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Complaints" }, h("div.skeleton")));
load();
