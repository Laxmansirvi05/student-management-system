import { checkbox, dataTable, field, hidePageError, input, numberSelect, openForm, rowAction, select } from "./admin-kit.js";
import { api, ApiError, messageFor } from "./api.js";
import { fill, formatDate, h, icon, tag, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Admin: assessments and their marks sheets (FR-ADM-003). A sheet is saved whole: if one row is
// wrong, nothing is saved and the row says why. Students see marks only after publication.

const TYPES = [["INTERNAL", "Internal exam"], ["ASSIGNMENT", "Assignment"], ["LAB", "Lab"], ["SEMESTER_EXAM", "Semester exam"]];
const typeLabel = Object.fromEntries(TYPES);
const content = document.getElementById("content");
let subjects = [];
let subjectId = null;

function academicYear() {
  // The academic year starts in June: October 2026 is in "2026-27".
  const now = new Date();
  const start = now.getMonth() >= 5 ? now.getFullYear() : now.getFullYear() - 1;
  return `${start}-${String((start + 1) % 100).padStart(2, "0")}`;
}

function assessmentForm(assessment) {
  openForm({
    title: assessment ? "Edit " + assessment.name : "Add an assessment",
    fields: [
      field("Name", input("name", { value: assessment?.name || "", maxlength: 100 }), "For example Internal 1."),
      field("Type", select("type", TYPES, assessment?.type || "INTERNAL")),
      field("Maximum marks", input("maxMarks", { type: "number", value: assessment?.maxMarks ?? 50, min: 0.01, max: 999.99, step: 0.01 })),
      field("Held on (optional)", input("heldOn", { type: "date", value: assessment?.heldOn || "" })),
      field("Academic year", input("academicYear", { value: assessment?.academicYear || academicYear(), maxlength: 7 }), "Written like 2026-27."),
      field("Visibility", checkbox("published", "Published: students can see these marks", assessment ? assessment.published : false)),
    ],
    wide: true,
    onSubmit: async (values) => {
      const body = { ...values, subjectId };
      if (assessment) {
        await api.put("/admin/assessments/" + assessment.id, body);
      } else {
        await api.post("/admin/assessments", body);
      }
      toast("Saved");
      loadAssessments();
    },
  });
}

function marksSheet(assessment) {
  const holder = document.getElementById("sheet");
  fill(holder, h("div.skeleton"), h("div.skeleton"));
  holder.scrollIntoView({ behavior: "smooth", block: "start" });
  loadInto(holder, () => api.get(`/admin/assessments/${assessment.id}/marks`), (sheet) => {
    if (!sheet.entries.length) {
      fill(holder, empty("users", "No students take this subject yet", "Students are matched by the branch and semester of the subject."));
      return;
    }
    const banner = h("p.inset.inset--danger.inset--note", { hidden: true, role: "alert" });
    const rows = sheet.entries.map((entry, index) => {
      const marks = h("input.textbox.marks-input", { type: "number", min: 0, max: assessment.maxMarks, step: 0.01, value: entry.marksObtained ?? "", disabled: entry.absent, "aria-label": "Marks of " + entry.fullName, id: "marks-" + index });
      const absent = h("input.checkbox", { type: "checkbox", checked: entry.absent, "aria-label": entry.fullName + " was absent" });
      const error = h("span.cell-error", { hidden: true, id: "marks-error-" + index });
      absent.addEventListener("change", () => {
        marks.disabled = absent.checked;
        if (absent.checked) {
          marks.value = "";
        }
      });
      return { entry, marks, absent, error };
    });
    const save = h("button.button.button--primary", { type: "button" }, icon("check"), "Save marks");
    save.addEventListener("click", async () => {
      banner.hidden = true;
      for (const row of rows) {
        row.error.hidden = true;
        row.marks.removeAttribute("aria-invalid");
      }
      save.disabled = true;
      try {
        const result = await api.put(`/admin/assessments/${assessment.id}/marks`, {
          entries: rows.map((row) => ({ studentId: row.entry.studentId, marksObtained: row.absent.checked || row.marks.value === "" ? null : Number(row.marks.value), absent: row.absent.checked })),
        });
        toast(`Saved ${result.saved} rows · ${result.changed} changed`);
      } catch (failure) {
        const others = [];
        for (const { field: name, message } of failure instanceof ApiError ? failure.fieldErrors : []) {
          const match = /^entries\[(\d+)\]/.exec(name);
          const row = match && rows[Number(match[1])];
          if (row) {
            row.error.textContent = message;
            row.error.hidden = false;
            row.marks.setAttribute("aria-invalid", "true");
          } else {
            others.push(message);
          }
        }
        const invalid = rows.filter((row) => !row.error.hidden).length;
        fill(banner, icon("alert"), invalid ? `Nothing was saved: ${invalid} row${invalid > 1 ? "s need" : " needs"} correcting. ${others.join(" ")}` : others.join(" ") || messageFor(failure));
        banner.hidden = false;
      } finally {
        save.disabled = false;
      }
    });
    fill(
      holder,
      h("div.line.line--wrap", h("div.stack.stack--tight", h("h2", "Marks: " + assessment.name), h("p.row__meta", `Out of ${assessment.maxMarks}. Tick "Absent" for a student who did not take it.`)), assessment.published ? tag("Published", "success", "eye") : tag("Not published", "", "lock")),
      banner,
      h(
        "div.table-wrap",
        h(
          "table.table",
          h("thead", h("tr", h("th", { scope: "col" }, "Roll no."), h("th", { scope: "col" }, "Student"), h("th.table__end", { scope: "col" }, "Marks"), h("th.table__end", { scope: "col" }, "Absent"))),
          h("tbody", rows.map((row) => h("tr", h("td", { "data-label": "Roll no." }, h("strong.mono", row.entry.rollNo)), h("td", { "data-label": "Student" }, row.entry.fullName), h("td.table__end", { "data-label": "Marks" }, row.marks, row.error), h("td.table__end", { "data-label": "Absent" }, row.absent)))),
        ),
      ),
      h("div.sheet-foot", h("span.row__meta", `${sheet.entries.length} students`), save),
    );
  });
}

function loadAssessments() {
  hidePageError();
  const list = document.getElementById("list");
  fill(document.getElementById("sheet"), empty("table", "Choose an assessment", "Open the marks sheet of an assessment from the list above."));
  loadInto(list, () => api.get("/admin/assessments?subjectId=" + subjectId), (assessments) => {
    fill(
      list,
      dataTable(
        [
          { label: "Assessment", cell: (item) => h("strong", item.name) },
          { label: "Type", cell: (item) => typeLabel[item.type] },
          { label: "Maximum", cell: (item) => h("span.mono.num", item.maxMarks) },
          { label: "Held on", cell: (item) => (item.heldOn ? formatDate(item.heldOn) : "—") },
          { label: "Year", cell: (item) => item.academicYear },
          { label: "Students see it", cell: (item) => (item.published ? tag("Published", "success", "eye") : tag("Not published", "", "lock")) },
          { label: "Actions", align: "end", cell: (item) => h("span.row-actions", rowAction("Enter marks for " + item.name, "table", () => marksSheet(item)), rowAction("Edit " + item.name, "edit", () => assessmentForm(item))) },
        ],
        assessments,
        "No assessments for this subject yet. Add one with the button above.",
      ),
    );
  });
}

function start(loaded) {
  subjects = loaded.filter((subject) => subject.active);
  if (!subjects.length) {
    fill(content, h("div.card", empty("resources", "No subjects yet", "Add a subject before creating assessments.", h("a.button.button--primary.button--small", { href: "/admin/subjects.html" }, "Go to subjects"))));
    return;
  }
  subjectId = subjects[0].id;
  const picker = numberSelect("subject", subjects.map((item) => [item.id, `${item.code} · ${item.name} (${item.branch} sem ${item.semester})`]));
  picker.addEventListener("change", () => {
    subjectId = Number(picker.value);
    loadAssessments();
  });
  const add = h("button.button.button--primary", { type: "button" }, icon("plus"), "Add assessment");
  add.addEventListener("click", () => assessmentForm(null));
  fill(document.getElementById("actions"), add);
  fill(
    content,
    h("section.card.toolbar-card", { "aria-label": "Subject" }, h("div.filters-row", field("Subject", picker))),
    h("section.card", { id: "list", "aria-label": "Assessments" }),
    h("section.card", { id: "sheet", "aria-label": "Marks sheet", "aria-live": "polite" }),
  );
  loadAssessments();
}

loadInto(content, () => api.get("/admin/subjects"), start);
