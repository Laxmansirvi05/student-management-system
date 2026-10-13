import { fill, formatDate, h, icon, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring, segmented, swap } from "./motion.js";
import { empty } from "./parts.js";
import { marks, results } from "./sample-data.js";
import "./shell.js";

// Results (FR-MRK-001, FR-MRK-002): marks for each assessment of a semester, and the official
// semester results with the CGPA the server calculates. Everything is read-only.

const content = document.getElementById("content");
const TYPES = { INTERNAL: "Internal exam", ASSIGNMENT: "Assignment", LAB: "Lab", SEMESTER_EXAM: "Semester exam" };
let semester = marks.currentSemester;

const two = (value) => Number(value).toFixed(2);

function subjectCard(subject) {
  if (!subject.published) {
    return h(
      "section.card.card--flat.reveal",
      h("div.line", h("span.tag.mono", subject.code), h("span.eyebrow", `${subject.credits} credits`)),
      h("h3.subject__name", subject.name),
      h("p.note", icon("clock"), "Marks have not been published yet."),
    );
  }
  const counted = subject.assessments.filter((assessment) => !assessment.absent);
  const obtained = counted.reduce((sum, assessment) => sum + assessment.marksObtained, 0);
  const maximum = subject.assessments.reduce((sum, assessment) => sum + assessment.maxMarks, 0);
  const total = (obtained / maximum) * 100;
  const rows = subject.assessments.map((assessment) =>
    h(
      "li.row.marks__row",
      h("div.stack.stack--tight", h("span.row__title", assessment.name), h("span.row__meta", TYPES[assessment.type])),
      assessment.absent
        ? tag("Absent", "danger")
        : h("span.mono.num", `${two(assessment.marksObtained)} / ${assessment.maxMarks}`),
      h("span.row__meta.num.marks__pct", assessment.absent ? "—" : two((assessment.marksObtained / assessment.maxMarks) * 100) + "%"),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    h(
      "div.line",
      h("div.stack", h("div.line.line--start", h("span.tag.tag--primary.mono", subject.code), h("span.eyebrow", `${subject.credits} credits`)), h("h3.subject__name", subject.name)),
      h("div.stack.stack--tight.align-end", h("span.figure.figure--small", two(total) + "%"), h("span.eyebrow", "So far")),
    ),
    bar(total, total >= 75 ? "success" : total < 50 ? "danger" : ""),
    h("div.line.marks__head", h("span.eyebrow", "Assessment"), h("span.eyebrow", "Marks")),
    h("ul.rows", rows),
    h("div.inset.inset--row", h("span.row__title", "Total"), h("span.mono.num", `${two(obtained)} / ${maximum}`)),
  );
}

function marksView() {
  const pills = h(
    "div.pills",
    { role: "group", "aria-label": "Semester" },
    marks.semesters.map((number) => {
      const pill = h(
        "button.pill",
        { type: "button", "aria-pressed": String(number === semester) },
        "Semester " + number,
        number === marks.currentSemester ? h("span.pill__count", "current") : null,
      );
      pill.addEventListener("click", () => {
        semester = number;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        swap(document.getElementById("marks"), renderMarks);
      });
      return pill;
    }),
  );
  return [h("div.card.card--bar.reveal", h("span.eyebrow", "Semester"), pills), h("div.grid.grid--2", { id: "marks" })];
}

function renderMarks() {
  const holder = document.getElementById("marks");
  if (semester !== marks.currentSemester) {
    holder.className = "card";
    fill(holder, empty("clock", "Marks have not been published yet", `Nothing has been published for semester ${semester}.`));
    return;
  }
  holder.className = "grid grid--2";
  fill(holder, stagger(marks.subjects.map(subjectCard)));
}

const STATUS = {
  PASS: ["Pass", "success", "check-circle"],
  FAIL: ["Fail", "danger", "x-circle"],
  WITHHELD: ["Withheld", "warning", "minus-circle"],
};

function semesterCard(entry) {
  const [label, kind, iconName] = STATUS[entry.resultStatus];
  const figure = h("p.figure");
  countUp(figure, entry.sgpa, { decimals: 2 });
  return h(
    "section.card.card--lift.reveal",
    h("div.line", h("h3", "Semester " + entry.semester), tag(label, kind, iconName)),
    h("div.stack.stack--tight", h("p.eyebrow", "SGPA"), figure),
    bar(entry.sgpa * 10, kind === "danger" ? "danger" : "success"),
    h(
      "dl.facts",
      h("div", h("dt", "Credits registered"), h("dd.num", entry.creditsRegistered)),
      h("div", h("dt", "Credits earned"), h("dd.num", entry.creditsEarned)),
      h("div", h("dt", "Published"), h("dd.num", formatDate(entry.publishedAt))),
    ),
  );
}

function semestersView() {
  const figure = h("p.hero__figure");
  countUp(figure, results.cgpa, { decimals: 2 });
  return [
    h(
      "section.hero.hero--split.reveal",
      { "data-tilt": true, "aria-label": "CGPA" },
      h(
        "div.hero__main",
        h("p.hero__eyebrow", "Cumulative grade point average"),
        figure,
        h("p.hero__sub", `Calculated from your ${results.semesters.length} published semesters.`),
      ),
      h("div.hero__aside", ring(results.cgpa * 10, { size: 152, stroke: 12, label: [h("strong.ring__big", "CGPA"), h("span.eyebrow.eyebrow--inverse", "out of 10")] })),
    ),
    h(
      "div.grid.grid--3",
      stagger([
        ...results.semesters.map(semesterCard),
        h("section.card.card--flat.reveal", h("h3", "Semester " + marks.currentSemester), h("p.note", icon("clock"), "Results not published yet.")),
      ]),
    ),
    h(
      "p.note.reveal",
      icon("info"),
      "CGPA is the average of your SGPAs, weighted by the credits registered in each semester. A withheld semester is left out until it is resolved.",
    ),
  ];
}

function render(view) {
  fill(content, view === "marks" ? marksView() : semestersView());
  if (view === "marks") {
    renderMarks();
  }
  reveal();
  enableTilt();
}

segmented(document.getElementById("view-tabs"), (view) => swap(content, () => render(view)));
render("marks");
