import { attempt, confirmAction, dataTable, dialogShell, field, importProblems, hidePageError, input, numberSelect, openForm, pager, rowAction, semesterOptions, showOnce } from "./admin-kit.js";
import { ApiError, api, messageFor } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { loadInto } from "./parts.js";
import "./shell.js";

// Admin: student accounts (FR-ADM-001). Create, search, edit, deactivate, reactivate and reset,
// and create many at once from a CSV file (FR-ADM-010).

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

const IMPORT_MAX_BYTES = 1024 * 1024;
const IMPORT_EXAMPLE = "rollNo,fullName,branch,currentSemester,section,admissionYear,phone\n23CSE101,Demo Student,CSE,3,A,2023,";

function importStudents() {
  const dialog = dialogShell("import-title");
  dialog.classList.add("dialog--wide");
  const file = input("file", { type: "file", accept: ".csv,text/csv" });
  const result = h("div", { "aria-live": "polite" });
  const submit = h("button.button.button--primary", { type: "submit" }, icon("upload"), "Import");
  const form = h(
    "form.stack.stack--loose",
    { novalidate: true },
    h(
      "div.stack.stack--tight",
      h("h2", { id: "import-title" }, "Import students from a CSV file"),
      h("p.muted", "One student per line, up to 100 in a file. The first line names the columns, in any order. The phone column may be left out."),
    ),
    h("pre.import__example", { "aria-label": "Example file" }, IMPORT_EXAMPLE),
    h("p.muted", "Every line follows the same rules as Add student: the login email is made from the roll number, the first password is the roll number, and the student must change it at first login. The whole file is checked first; if any line is wrong, nothing is imported."),
    field("CSV file", file, "Saved from a spreadsheet as CSV (UTF-8), up to 1 MB."),
    result,
    h("div.dialog__actions", h("button.button.button--quiet", { type: "button", "data-close": true }, "Cancel"), submit),
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const chosen = file.files[0];
    let problem = "";
    if (!chosen) {
      problem = "Choose a CSV file.";
    } else if (!chosen.name.toLowerCase().endsWith(".csv")) {
      problem = "Choose a file whose name ends in .csv.";
    } else if (chosen.size > IMPORT_MAX_BYTES) {
      problem = "The file is larger than 1 MB. Split it into smaller files.";
    }
    if (problem) {
      fill(result, h("p.inset.inset--danger.inset--note", { role: "alert" }, icon("alert"), problem));
      return;
    }
    submit.disabled = true;
    fill(result, h("p.muted", "Checking and creating the accounts…"));
    try {
      const { created } = await api.postFile("/admin/imports/students", chosen);
      dialog.close();
      toast(created === 1 ? "Imported 1 student" : "Imported " + created + " students");
      filters.page = 0;
      load();
    } catch (failure) {
      const listed = failure instanceof ApiError && failure.fieldErrors.length;
      fill(result, listed ? importProblems(failure) : h("p.inset.inset--danger.inset--note", { role: "alert" }, icon("alert"), messageFor(failure)));
    } finally {
      submit.disabled = false;
    }
  });
  file.addEventListener("change", () => fill(result));
  fill(dialog, form);
  dialog.showModal();
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
const importButton = h("button.button.button--secondary", { type: "button" }, icon("upload"), "Import CSV");
importButton.addEventListener("click", importStudents);
fill(document.getElementById("actions"), h("div.line", importButton, add));
fill(content, filterBar(), h("section.card", { id: "list", "aria-label": "Students" }, h("div.skeleton")));
load();
