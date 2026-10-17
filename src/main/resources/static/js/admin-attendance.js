import { dialogShell, field, hidePageError, importProblems, input, numberSelect } from "./admin-kit.js";
import { api, ApiError, messageFor } from "./api.js";
import { fill, h, icon, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Admin: the attendance sheet of one class (FR-ADM-002). Choose the subject, section, date and
// period, mark each student, and save the whole sheet at once. Saving it again corrects it.
// Also: import the college's attendance report (totals per student for a period) from a CSV file.

const content = document.getElementById("content");
const today = new Date().toLocaleDateString("en-CA");
let unsaved = false;

window.addEventListener("beforeunload", (event) => {
  if (unsaved) {
    event.preventDefault();
  }
});

function choice(entry) {
  const option = (status, label, modifier) => {
    const button = h("button.choice__option" + modifier, { type: "button", "aria-pressed": String(entry.status === status) }, label);
    button.addEventListener("click", () => {
      entry.status = status;
      unsaved = true;
      for (const other of button.parentElement.children) {
        other.setAttribute("aria-pressed", String(other === button));
      }
      updateCount();
    });
    return button;
  };
  return h("div.choice", { role: "group", "aria-label": "Attendance of " + entry.fullName }, option("PRESENT", "Present", ""), option("ABSENT", "Absent", ".choice__option--absent"));
}

let sheet = null;

function updateCount() {
  const present = sheet.entries.filter((entry) => entry.status === "PRESENT").length;
  document.getElementById("count").textContent = `${present} present · ${sheet.entries.length - present} absent`;
}

function showSheet(loaded) {
  sheet = loaded;
  const holder = document.getElementById("sheet");
  if (!sheet.entries.length) {
    fill(holder, empty("users", "No students in this section", "Check the section, or add students first."));
    return;
  }
  const alreadySaved = sheet.entries.some((entry) => entry.status !== null);
  // A class that has not been marked yet starts with everyone present.
  for (const entry of sheet.entries) {
    entry.status = entry.status || "PRESENT";
  }
  const errors = h("p.inset.inset--danger.inset--note", { hidden: true, role: "alert" });
  const save = h("button.button.button--primary", { type: "button" }, icon("check"), alreadySaved ? "Save corrections" : "Save attendance");
  const all = (status, label) => {
    const button = h("button.button.button--quiet.button--small", { type: "button" }, label);
    button.addEventListener("click", () => {
      for (const entry of sheet.entries) {
        entry.status = status;
      }
      unsaved = true;
      showRows();
    });
    return button;
  };
  const rows = h("div", { id: "rows" });
  function showRows() {
    fill(
      rows,
      h(
        "div.table-wrap",
        h(
          "table.table",
          h("thead", h("tr", h("th", { scope: "col" }, "Roll no."), h("th", { scope: "col" }, "Student"), h("th.table__end", { scope: "col" }, "Attendance"))),
          h("tbody", sheet.entries.map((entry) => h("tr", h("td", { "data-label": "Roll no." }, h("strong.mono", entry.rollNo)), h("td", { "data-label": "Student" }, entry.fullName), h("td.table__end", { "data-label": "Attendance" }, choice(entry))))),
        ),
      ),
    );
    updateCount();
  }
  save.addEventListener("click", async () => {
    errors.hidden = true;
    save.disabled = true;
    try {
      const result = await api.put("/admin/attendance", {
        subjectId: sheet.subjectId,
        section: sheet.section,
        classDate: sheet.classDate,
        periodNo: sheet.periodNo,
        entries: sheet.entries.map(({ studentId, status }) => ({ studentId, status })),
      });
      unsaved = false;
      toast(`Saved ${result.saved} rows · ${result.changed} changed`);
    } catch (failure) {
      const messages = failure instanceof ApiError && failure.fieldErrors.length ? failure.fieldErrors.map((error) => error.message) : [messageFor(failure)];
      fill(errors, icon("alert"), [...new Set(messages)].join(" "));
      errors.hidden = false;
    } finally {
      save.disabled = false;
    }
  });
  fill(
    holder,
    h("div.line.line--wrap", h("div.stack.stack--tight", h("h2", alreadySaved ? "Saved sheet" : "New sheet"), h("p.row__meta", alreadySaved ? "This class was already marked. Change what is wrong and save again." : "Everyone starts as present. Change the absentees and save.")), h("div.line.line--start", all("PRESENT", "All present"), all("ABSENT", "All absent"))),
    errors,
    rows,
    h("div.sheet-foot", h("strong.num", { id: "count" }), save),
  );
  showRows();
  unsaved = false;
}

function start(subjects) {
  if (!subjects.length) {
    fill(content, h("div.card", empty("resources", "No subjects yet", "Add a subject before marking attendance.", h("a.button.button--primary.button--small", { href: "/admin/subjects.html" }, "Go to subjects"))));
    return;
  }
  const subject = numberSelect("subjectId", subjects.map((item) => [item.id, `${item.code} · ${item.name} (${item.branch} sem ${item.semester})`]));
  const section = input("section", { value: "A", maxlength: 5 });
  const date = input("classDate", { type: "date", value: today, max: today });
  const period = numberSelect("periodNo", Array.from({ length: 12 }, (_, index) => [index + 1, "Period " + (index + 1)]));
  const loadButton = h("button.button.button--primary", { type: "submit" }, "Open sheet");
  const form = h("form.card.toolbar-card", { "aria-label": "Class" }, h("div.filters-row", field("Subject", subject), field("Section", section), field("Date", date), field("Period", period), loadButton));
  form.addEventListener("submit", (event) => {
    event.preventDefault();
    hidePageError();
    const params = new URLSearchParams({ subjectId: subject.value, section: section.value.trim().toUpperCase(), classDate: date.value, periodNo: period.value });
    const holder = document.getElementById("sheet");
    fill(holder, h("div.skeleton"), h("div.skeleton"));
    loadInto(holder, () => api.get("/admin/attendance?" + params), showSheet);
  });
  fill(content, form, h("section.card", { id: "sheet", "aria-label": "Attendance sheet", "aria-live": "polite" }, empty("attendance", "Choose a class", "Pick the subject, section, date and period, then open the sheet.")));
}

const REPORT_EXAMPLE = "rollNo,classesHeld,classesAttended,percent\n23CSE101,211,163,77.25";

function importReport() {
  const dialog = dialogShell("report-title");
  dialog.classList.add("dialog--wide");
  const from = input("fromDate", { type: "date", max: today });
  const to = input("toDate", { type: "date", max: today });
  const file = input("file", { type: "file", accept: ".csv,text/csv" });
  const result = h("div", { "aria-live": "polite" });
  const submit = h("button.button.button--primary", { type: "submit" }, icon("upload"), "Import");
  const form = h(
    "form.stack.stack--loose",
    { novalidate: true },
    h(
      "div.stack.stack--tight",
      h("h2", { id: "report-title" }, "Import an attendance report"),
      h("p.muted", "The college's totals for one period: one line per student with classes held and classes attended. Students see it on their attendance page and dashboard."),
    ),
    h("pre.import__example", { "aria-label": "Example file" }, REPORT_EXAMPLE),
    h("p.muted", "The percent column is optional. When it is there, it must agree with the two numbers, which catches typing slips. The whole file is checked first; if any line is wrong, nothing is saved. Importing the same period again corrects it."),
    h("div.form-grid", field("From", from), field("To", to)),
    field("CSV file", file, "UTF-8, up to 1 MB."),
    result,
    h("div.dialog__actions", h("button.button.button--quiet", { type: "button", "data-close": true }, "Cancel"), submit),
  );
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const chosen = file.files[0];
    let problem = "";
    if (!from.value || !to.value) {
      problem = "Choose the first and the last day of the period.";
    } else if (!chosen) {
      problem = "Choose a CSV file.";
    } else if (!chosen.name.toLowerCase().endsWith(".csv")) {
      problem = "Choose a file whose name ends in .csv.";
    }
    if (problem) {
      fill(result, h("p.inset.inset--danger.inset--note", { role: "alert" }, icon("alert"), problem));
      return;
    }
    submit.disabled = true;
    fill(result, h("p.muted", "Checking the file…"));
    try {
      const params = new URLSearchParams({ fromDate: from.value, toDate: to.value });
      const done = await api.postFile("/admin/imports/attendance-reports?" + params, chosen);
      dialog.close();
      toast(`Imported: ${done.created} new, ${done.corrected} corrected, ${done.unchanged} unchanged`);
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

const reportButton = h("button.button.button--secondary", { type: "button" }, icon("upload"), "Import report");
reportButton.addEventListener("click", importReport);
fill(document.getElementById("actions"), reportButton);

loadInto(content, () => api.get("/admin/subjects"), (subjects) => start(subjects.filter((subject) => subject.active)));

