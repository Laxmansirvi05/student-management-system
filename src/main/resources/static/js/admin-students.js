import { attempt, confirmAction, dataTable, field, hidePageError, input, numberSelect, openForm, pager, rowAction, semesterOptions, showOnce } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: student accounts (FR-ADM-001). Create, search, edit, deactivate, reactivate and reset.

const content = document.getElementById("content");
const filters = { q: "", branch: "", semester: "", section: "", page: 0 };

/** The state of the login account, in words (awaiting first login, active, locked, deactivated). */
function status(student) {
  if (!student.active) {
    return tag("Deactivated", "danger", "x-circle");
  }
  if (student.locked) {
    return tag("Locked", "warning", "lock");
  }
  if (student.mustChangePassword) {
    return student.lastLoginAt ? tag("Temporary password", "warning", "key") : tag("Awaiting first login", "primary", "clock");
  }
  return tag("Active", "success", "check-circle");
}

function profileFields(student = {}, withRollNo = false) {
  return [
    withRollNo ? field("Roll number", input("rollNo", { maxlength: 20 }), "Letters and digits only. It cannot be changed later.") : null,
    field("Full name", input("fullName", { value: student.fullName || "", maxlength: 120 })),
    field("Branch", input("branch", { value: student.branch || "", maxlength: 20 })),
    field("Semester", numberSelect("currentSemester", semesterOptions(), student.currentSemester || 1)),
    field("Section", input("section", { value: student.section || "", maxlength: 5 })),
    field("Admission year", input("admissionYear", { type: "number", value: student.admissionYear || new Date().getFullYear(), min: 2000, max: 2100 })),
    field("Phone (optional)", input("phone", { value: student.phone || "", maxlength: 20, type: "tel" })),
  ];
}

function addStudent() {
  openForm({
    title: "Add a student",
    intro: "The login email is made from the roll number. The first password is the roll number, and the student must change it at first login.",
    submitLabel: "Create account",
    fields: profileFields({}, true),
    wide: true,
    onSubmit: async (values) => {
      const created = await api.post("/admin/students", values);
      toast("Created " + created.email);
      load();
    },
  });
}

function edit(student) {
  openForm({
    title: "Edit " + student.rollNo,
    intro: "The roll number and the login email cannot be changed.",
    fields: profileFields(student),
    wide: true,
    onSubmit: async (values) => {
      await api.put("/admin/students/" + student.id, values);
      toast("Saved");
      load();
    },
  });
}

async function resetPassword(student) {
  const yes = await confirmAction({
    title: "Reset the password of " + student.rollNo + "?",
    text: "The student is logged out everywhere and gets a new temporary password, which they must change at the next login.",
    confirmLabel: "Reset password",
  });
  if (yes) {
    const { temporaryPassword } = await attempt(() => api.post(`/admin/students/${student.id}/reset-password`));
    showOnce({
      title: "Temporary password",
      text: `Give this to ${student.fullName} in person. It is shown only once and is not stored in readable form.`,
      value: temporaryPassword,
    });
    load();
  }
}

async function setActive(student, active) {
  const yes = await confirmAction(
    active
      ? { title: "Reactivate " + student.rollNo + "?", text: "The student can log in again with the password they had.", confirmLabel: "Reactivate" }
      : { title: "Deactivate " + student.rollNo + "?", text: "The student is logged out at once and cannot log in until the account is reactivated. Nothing is deleted.", confirmLabel: "Deactivate", danger: true },
  );
  if (yes) {
    await attempt(() => api.post(`/admin/students/${student.id}/${active ? "activate" : "deactivate"}`), active ? "Reactivated" : "Deactivated");
    load();
  }
}

function table(page) {
  return dataTable(
    [
      { label: "Roll no.", cell: (student) => h("strong.mono", student.rollNo) },
      { label: "Student", cell: (student) => h("div.stack.stack--tight", h("strong", student.fullName), h("span.row__meta", student.email)) },
      { label: "Class", cell: (student) => `${student.branch} · Sem ${student.currentSemester} · ${student.section}` },
      { label: "Account", cell: status },
      { label: "Last login", cell: (student) => (student.lastLoginAt ? formatDate(student.lastLoginAt) : "—") },
      {
        label: "Actions",
        align: "end",
        cell: (student) =>
          h(
            "span.row-actions",
            rowAction("Edit " + student.rollNo, "edit", () => edit(student)),
            h("a.row-action", { href: "/admin/results.html?studentId=" + student.id, title: "Results of " + student.rollNo, "aria-label": "Results of " + student.rollNo }, icon("results")),
            rowAction("Reset the password of " + student.rollNo, "key", () => resetPassword(student)),
            student.active
              ? rowAction("Deactivate " + student.rollNo, "power", () => setActive(student, false), "danger")
              : rowAction("Reactivate " + student.rollNo, "undo", () => setActive(student, true)),
          ),
      },
    ],
    page.content,
    "No students match. Add one with the button above.",
  );
}

function load() {
  hidePageError();
  const list = document.getElementById("list");
  const params = new URLSearchParams({ page: filters.page, size: 20 });
  for (const key of ["q", "branch", "semester", "section"]) {
    if (filters[key] && (key !== "q" || filters.q.length >= 2)) {
      params.set(key, filters[key]);
    }
  }
  loadInto(list, () => api.get("/admin/students?" + params), (page) => {
    fill(
      list,
      h("p.row__meta.num", page.totalElements === 1 ? "1 student" : page.totalElements + " students"),
      table(page),
      pager(page, (target) => {
        filters.page = target;
        load();
      }),
    );
  });
}

function filterBar() {
  let timer = null;
  const changed = (key, value) => {
    filters[key] = value;
    filters.page = 0;
    clearTimeout(timer);
    timer = setTimeout(load, 250);
  };
  const search = h("input.search__input", { type: "search", id: "student-search", placeholder: "Search by roll number, name or email", autocomplete: "off" });
  search.addEventListener("input", () => changed("q", search.value.trim()));
  const branch = input("branch-filter", { placeholder: "Any" });
  branch.addEventListener("input", () => changed("branch", branch.value.trim().toUpperCase()));
  const semester = numberSelect("semester-filter", semesterOptions("Any semester"));
  semester.addEventListener("change", () => changed("semester", semester.value));
  const section = input("section-filter", { placeholder: "Any" });
  section.addEventListener("input", () => changed("section", section.value.trim().toUpperCase()));
  return h(
    "section.card.toolbar-card",
    { "aria-label": "Search and filters" },
    h("label.search", { for: "student-search" }, h("span.visually-hidden", "Search students"), icon("search"), search),
    h("div.filters-row", field("Branch", branch), field("Semester", semester), field("Section", section)),
  );
}

const add = h("button.button.button--primary", { type: "button" }, icon("plus"), "Add student");
add.addEventListener("click", addStudent);
fill(document.getElementById("actions"), add);
fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Students" }, h("div.skeleton")));
load();
