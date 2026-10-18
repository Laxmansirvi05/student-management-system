import { checkbox, dataTable, field, hidePageError, input, numberSelect, openForm, rowAction, select, semesterOptions } from "./admin-kit.js";
import { api } from "./api.js";
import { fill, h, icon, tag, toast } from "./dom.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Admin: semester results (FR-ADM-004). Find a student, then record the official SGPA, credits
// and status of a semester. The student sees a result only when it is published.

const STATUSES = [["PASS", "Pass"], ["FAIL", "Fail"], ["WITHHELD", "Withheld"]];
const statusTag = { PASS: ["Pass", "success", "check-circle"], FAIL: ["Fail", "danger", "x-circle"], WITHHELD: ["Withheld", "warning", "minus-circle"] };
const content = document.getElementById("content");
let student = null;

function resultForm(result) {
  openForm({
    title: result ? `Semester ${result.semester} of ${student.rollNo}` : "Add a result for " + student.rollNo,
    intro: "Credits earned cannot be more than credits registered. A withheld semester is left out of the CGPA.",
    fields: [
      field("Semester", numberSelect("semester", semesterOptions(), result?.semester || student.currentSemester)),
      field("SGPA", input("sgpa", { type: "number", value: result?.sgpa ?? "", min: 0, max: 10, step: 0.01 }), "From 0 to 10. Leave it empty when the college published none because a subject is failed."),
      field("Credits registered", input("creditsRegistered", { type: "number", value: result?.creditsRegistered ?? "", min: 1 })),
      field("Credits earned", input("creditsEarned", { type: "number", value: result?.creditsEarned ?? "", min: 0 })),
      field("Status", select("resultStatus", STATUSES, result?.resultStatus || "PASS")),
      field("Visibility", checkbox("published", "Published: the student can see it", result ? result.published : true)),
    ],
    wide: true,
    onSubmit: async (values) => {
      await api.put("/admin/semester-results", { ...values, studentId: student.id });
      toast("Saved");
      showStudent(student);
    },
  });
}

function showStudent(chosen) {
  student = chosen;
  hidePageError();
  const holder = document.getElementById("results");
  loadInto(holder, () => api.get("/admin/semester-results?studentId=" + student.id), (results) => {
    const add = h("button.button.button--primary.button--small", { type: "button" }, icon("plus"), "Add result");
    add.addEventListener("click", () => resultForm(null));
    fill(
      holder,
      h("div.line.line--wrap", h("div.stack.stack--tight", h("h2", student.fullName), h("p.row__meta", h("span.mono", student.rollNo), ` · ${student.branch} · Semester ${student.currentSemester} · Section ${student.section}`)), add),
      dataTable(
        [
          { label: "Semester", cell: (result) => h("strong", "Semester " + result.semester) },
          { label: "SGPA", cell: (result) => h("span.mono.num", result.sgpa === null ? "—" : Number(result.sgpa).toFixed(2)) },
          { label: "Registered", cell: (result) => result.creditsRegistered },
          { label: "Earned", cell: (result) => result.creditsEarned },
          { label: "Status", cell: (result) => tag(...statusTag[result.resultStatus]) },
          { label: "Student sees it", cell: (result) => (result.published ? tag("Published", "success", "eye") : tag("Not published", "", "lock")) },
          { label: "Actions", align: "end", cell: (result) => rowAction("Edit semester " + result.semester, "edit", () => resultForm(result)) },
        ],
        results,
        "No results recorded for this student yet.",
      ),
    );
  });
}

function search(query) {
  const matches = document.getElementById("matches");
  if (query.length < 2) {
    fill(matches);
    return;
  }
  loadInto(matches, () => api.get("/admin/students?size=8&q=" + encodeURIComponent(query)), (page) => {
    fill(
      matches,
      page.content.length
        ? h(
            "div.pills",
            page.content.map((item) => {
              const pill = h("button.pill", { type: "button", "aria-pressed": String(student?.id === item.id) }, h("span.mono", item.rollNo), item.fullName);
              pill.addEventListener("click", () => {
                for (const other of pill.parentElement.children) {
                  other.setAttribute("aria-pressed", String(other === pill));
                }
                showStudent(item);
              });
              return pill;
            }),
          )
        : h("p.muted", "No student matches."),
    );
  });
}

let timer = null;
const box = h("input.search__input", { type: "search", id: "student-search", placeholder: "Find a student by roll number, name or email", autocomplete: "off" });
box.addEventListener("input", () => {
  clearTimeout(timer);
  timer = setTimeout(() => search(box.value.trim()), 250);
});
fill(
  content,
  h("section.card.toolbar-card", { "aria-label": "Find a student" }, h("label.search", { for: "student-search" }, h("span.visually-hidden", "Find a student"), icon("search"), box), h("div", { id: "matches" })),
  h("section.card", { id: "results", "aria-label": "Results", "aria-live": "polite" }, empty("results", "Choose a student", "Search above, then pick a student to see and record results.")),
);

// Opened from the students page with a student already chosen.
const wanted = new URLSearchParams(location.search).get("studentId");
if (wanted) {
  api.get("/admin/students/" + wanted).then(showStudent).catch(() => {});
}
