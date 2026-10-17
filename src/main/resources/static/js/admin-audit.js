import { dataTable, field, hidePageError, pager, select } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, h, icon, tag } from "./dom.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: the audit log, read only (FR-ADM-009). Filter by what was done and to what kind of record.

const content = document.getElementById("content");
const filters = { action: "", entityType: "", page: 0 };

const WHEN = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

const ENTITY_TYPES = [
  ["", "Any record"],
  ["USER", "Account"],
  ["STUDENT_PROFILE", "Student profile"],
  ["STUDENT", "Own profile"],
  ["ATTENDANCE_RECORD", "Attendance"],
  ["MARK", "Marks"],
  ["SEMESTER_RESULT", "Result"],
];

/** "PASSWORD_RESET" becomes "Password reset". */
function actionLabel(action) {
  const text = action.toLowerCase().replaceAll("_", " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** The values of a change as short text: "status: PRESENT, absent: false". */
function valuesText(value) {
  if (!value) {
    return "";
  }
  return Object.entries(value)
    .map(([key, item]) => key + ": " + (item === null ? "none" : item))
    .join(", ");
}

function changeCell(entry) {
  if (entry.action === "PASSWORD_RESET" || entry.action === "PASSWORD_CHANGED") {
    return h("span.muted", "Password never recorded");
  }
  const before = valuesText(entry.oldValue);
  const after = valuesText(entry.newValue);
  return h(
    "div.stack.stack--tight.audit__change",
    before ? h("span.row__meta", h("strong", "Before: "), before) : null,
    after ? h("span.row__meta", h("strong", "After: "), after) : null,
  );
}

function table(page) {
  return dataTable(
    [
      { label: "When", cell: (entry) => h("span.num", WHEN.format(new Date(entry.createdAt))) },
      { label: "Action", cell: (entry) => tag(actionLabel(entry.action), entry.action.startsWith("PASSWORD") ? "warning" : "primary") },
      { label: "By", cell: (entry) => entry.actor },
      { label: "About", cell: (entry) => h("span", entry.subject || entry.entityType + " " + entry.entityId) },
      { label: "Change", cell: changeCell },
      { label: "From", cell: (entry) => h("span.mono", entry.ipAddress || "") },
    ],
    page.content,
    "No entries match.",
  );
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ page: filters.page, size: 20 });
  for (const key of ["action", "entityType"]) {
    if (filters[key]) {
      params.set(key, filters[key]);
    }
  }
  loadInto(list, () => api.get("/admin/audit-logs?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 entry" : page.totalElements + " entries"),
      table(page),
      pager(page, (target) => {
        filters.page = target;
        load();
      }),
    );
  });
}

async function start() {
  let actions = [];
  try {
    actions = await api.get("/admin/audit-logs/actions");
  } catch {
    // The list still works without the filter choices.
  }
  const action = select("action-filter", [["", "Any action"], ...actions.map((name) => [name, actionLabel(name)])]);
  const entity = select("entity-filter", ENTITY_TYPES);
  action.addEventListener("change", () => {
    filters.action = action.value;
    filters.page = 0;
    load();
  });
  entity.addEventListener("change", () => {
    filters.entityType = entity.value;
    filters.page = 0;
    load();
  });
  fill(
    content,
    h("section.card.toolbar-card", { "aria-label": "Filters" }, h("div.filters-row", field("Action", action), field("Record", entity))),
    h("section.card", { id: "list", "aria-label": "Audit entries" }, h("div.skeleton")),
  );
  load();
}

start();
