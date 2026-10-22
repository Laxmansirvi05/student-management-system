import { api } from "./api.js";
import { fill, h, icon, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring } from "./motion.js";
import { empty, loadInto } from "./parts.js";
import { currentStudent } from "./shell.js";

// Results (FR-MRK-002): the official semester results with the grade sheets and the CGPA the
// server calculates. Everything is read-only.

const content = document.getElementById("content");
let currentSemester = 1;

const STATUS = {
  PASS: ["Pass", "success", "check-circle"],
  FAIL: ["Fail", "danger", "x-circle"],
  WITHHELD: ["Withheld", "warning", "minus-circle"],
};

const GRADE_TABLE = [
  ["90% and above", "O", "Outstanding", 10],
  ["80% to below 90%", "A+", "Excellent", 9],
  ["70% to below 80%", "A", "Very Good", 8],
  ["60% to below 70%", "B+", "Good", 7],
  ["50% to below 60%", "B", "Average", 6],
  ["40% to below 50%", "C", "Pass", 5],
  ["Below 40%", "F", "Fail", 0],
  ["Absent", "Ab", "Absent", 0],
];

/** The college's B.Tech grading rules (AR25), for reference. */
function gradingPolicy() {
  return h(
    "details.card.policy.reveal",
    h("summary", h("span.eyebrow", "For reference"), h("strong", "How grades, SGPA and CGPA work")),
    h(
      "div.memo__scroll",
      h(
        "table.memo__table",
        h("thead", h("tr", ["Marks in a course", "Grade", "Meaning", "Grade point"].map((label) => h("th", { scope: "col" }, label)))),
        h("tbody", GRADE_TABLE.map(([marks, grade, meaning, points]) => h("tr", h("td", marks), h("td.memo__grade", grade), h("td", meaning), h("td.num", points)))),
      ),
    ),
    h(
      "ul.policy__rules",
      h("li", "The minimum grade to pass each theory and practical course is ", h("strong", "C"), "."),
      h("li", "A semester is passed only when its SGPA is ", h("strong", "5.00 or more"), "."),
      h("li", h("strong", "SGPA"), " = Σ (grade point × credits) ÷ Σ credits, for the courses of that semester."),
      h("li", h("strong", "CGPA"), " = Σ (grade point × credits) ÷ Σ credits, for all courses passed up to that semester."),
      h("li", "A course without credits, such as the Induction Program, is graded S (satisfactory) and does not count in SGPA or CGPA."),
      h("li", "While a course is failed, the college publishes no SGPA for that semester and no CGPA."),
    ),
    h("p.note", icon("info"), "B.Tech, Academic Regulations AR25. If anything here differs from your memorandum of grades, the memorandum is the official record."),
  );
}

/** The semester's grade sheet, laid out like the college's memorandum of grades. */
function gradeSheet(entry) {
  const subjects = entry.subjects;
  const credited = subjects.filter((subject) => subject.credits !== null);
  const appeared = subjects.filter((subject) => subject.grade !== "Ab").length;
  const passed = subjects.filter((subject) => subject.passed).length;
  const points = credited.reduce((sum, subject) => sum + (subject.gradePoint ?? 0), 0);
  const credits = credited.reduce((sum, subject) => sum + Number(subject.credits), 0);
  const row = (subject, index) =>
    h(
      "tr" + (subject.passed ? "" : ".memo__failed"),
      h("td.num", index + 1),
      h("td.mono", subject.subjectCode),
      h(
        "td",
        subject.subjectName,
        subject.attempts > 1 ? h("span.memo__note", subject.passed ? `Cleared in ${subject.exam} (first grade ${subject.firstGrade})` : `Attempt ${subject.attempts}: ${subject.exam}`) : null,
      ),
      h("td.memo__grade", subject.grade),
      h("td.num", subject.gradePoint ?? "–"),
      h("td", subject.passed ? "P" : "F"),
      h("td.num", subject.credits === null ? "–" : Number(subject.credits)),
    );
  return h(
    "div.memo",
    h(
      "div.memo__scroll",
      h(
        "table.memo__table",
        h("thead", h("tr", ["S.No", "Code", "Subject", "Grade", "Grade point", "Result", "Credits"].map((label) => h("th", { scope: "col" }, label)))),
        h("tbody", subjects.map(row)),
        h("tfoot", h("tr", h("td", { colspan: 4 }, `Registered ${subjects.length} · Appeared ${appeared} · Passed ${passed}`), h("td.num", points), h("td"), h("td.num", credits))),
      ),
    ),
    h(
      "dl.facts.facts--2",
      h("div", h("dt", "SGPA"), h("dd.num", entry.sgpa === null ? "Not published" : Number(entry.sgpa).toFixed(2))),
      h("div", h("dt", "CGPA"), h("dd.num", entry.cgpaToDate === null || entry.cgpaToDate === undefined ? "Not published" : Number(entry.cgpaToDate).toFixed(2))),
    ),
  );
}

function semesterCard(entry) {
  const [label, kind, iconName] = STATUS[entry.resultStatus];
  const figure = h("p.figure");
  if (entry.sgpa !== null) {
    countUp(figure, entry.sgpa, { decimals: 2 });
  }
  const subjects = entry.subjects || [];
  return h(
    "section.card.card--lift.reveal",
    h("div.line", h("h3", "Semester " + entry.semester), tag(label, kind, iconName)),
    entry.sgpa === null
      ? h("div.stack.stack--tight", h("p.eyebrow", "SGPA"), h("p.note", icon("info"), "Published once every subject is cleared."))
      : h("div.stack.stack--tight", h("p.eyebrow", "SGPA"), figure),
    entry.sgpa === null ? null : bar(entry.sgpa * 10, kind === "danger" ? "danger" : kind === "warning" ? "warning" : "success"),
    h(
      "dl.facts.facts--2",
      h("div", h("dt", "Credits registered"), h("dd.num", entry.creditsRegistered)),
      h("div", h("dt", "Credits earned"), h("dd.num", entry.creditsEarned)),
    ),
    subjects.length ? h("details.grades", { open: entry.sgpa === null || undefined }, h("summary", `Grade sheet (${subjects.length} subjects)`), gradeSheet(entry)) : null,
  );
}

function semestersView() {
  fill(content, h("div.skeleton.skeleton--hero"), h("div.skeleton.skeleton--card"));
  loadInto(content, () => api.get("/results/me"), ({ cgpa, semesters }) => {
    if (!semesters.length) {
      fill(content, h("div.card", empty("results", "Results not published yet", "Your semester results appear here once the college publishes them.")));
      return;
    }
    const figure = h("p.hero__figure");
    countUp(figure, cgpa ?? 0, { decimals: 2 });
    const published = new Set(semesters.map((entry) => entry.semester));
    const cards = [...semesters].reverse().map(semesterCard);
    if (!published.has(currentSemester)) {
      cards.unshift(h("section.card.card--flat.reveal", h("h3", "Semester " + currentSemester), h("p.note", icon("clock"), "Results not published yet.")));
    }
    const failed = semesters.flatMap((entry) => (entry.subjects || []).filter((subject) => !subject.passed).map((subject) => ({ ...subject, semester: entry.semester })));
    if (failed.length) {
      fill(
        content,
        h(
          "section.card.card--alert.reveal",
          { "aria-label": "Subjects to clear" },
          h("div.line", h("h2", "Subjects to clear"), tag(failed.length + (failed.length === 1 ? " subject" : " subjects"), "danger", "alert")),
          h("p.row__meta", "CGPA is shown once every subject is cleared. Your grade sheet for each semester is below."),
          h(
            "ul.rows",
            failed.map((subject) =>
              h(
                "li.row.grade",
                h("div.stack.stack--tight", h("span.row__title", subject.subjectName), h("span.row__meta", h("span.mono", subject.subjectCode), ` · Semester ${subject.semester} · ${subject.exam}`)),
                tag(subject.grade, "danger"),
              ),
            ),
          ),
        ),
        h("div.stack.stack--loose", stagger(cards)),
        gradingPolicy(),
      );
      reveal();
      return;
    }
    fill(
      content,
      h(
        "section.hero.hero--split.reveal",
        { "data-tilt": true, "aria-label": "CGPA" },
        h(
          "div.hero__main",
          h("p.hero__eyebrow", "Cumulative grade point average"),
          cgpa === null ? h("h2.hero__title", "Not available yet") : figure,
          h("p.hero__sub", `From your ${semesters.length} published semester${semesters.length > 1 ? "s" : ""}.`),
        ),
        cgpa === null ? null : h("div.hero__aside", ring(cgpa * 10, { size: 152, stroke: 12, label: [h("strong.ring__big", "CGPA"), h("span.eyebrow.eyebrow--inverse", "out of 10")] })),
      ),
      h("div.stack.stack--loose", stagger(cards)),
      gradingPolicy(),
      h("p.note.reveal", icon("info"), "CGPA is the average of your SGPAs, weighted by the credits registered in each semester. It is shown once every published semester has an SGPA, as the college does. A withheld semester is left out until it is resolved."),
    );
    reveal();
    enableTilt();
  });
}

currentStudent
  .then((student) => {
    currentSemester = student ? student.currentSemester : 1;
  })
  .catch(() => {})
  .finally(semestersView);
