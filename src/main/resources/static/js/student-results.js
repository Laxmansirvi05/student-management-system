import { api } from "./api.js";
import { fill, h, icon, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring, segmented, swap } from "./motion.js";
import { empty, loadInto } from "./parts.js";
import { currentStudent } from "./shell.js";

// Results (FR-MRK-001, FR-MRK-002): the published marks of a semester, and the official semester
// results with the CGPA the server calculates. Everything is read-only.

const content = document.getElementById("content");
const TYPES = { INTERNAL: "Internal exam", ASSIGNMENT: "Assignment", LAB: "Lab", SEMESTER_EXAM: "Semester exam" };
let currentSemester = 1;
let semester = 1;

const two = (value) => Number(value).toFixed(2);

function subjectCard(subject) {
  if (!subject.assessments.length) {
    return h(
      "section.card.card--flat.reveal",
      h("div.line.line--start", h("span.tag.mono", subject.subjectCode)),
      h("h3.subject__name", subject.subjectName),
      h("p.note", icon("clock"), "Marks have not been published yet."),
    );
  }
  // An absent student has no marks for that assessment, so it adds nothing to "obtained".
  const obtained = subject.assessments.reduce((sum, item) => sum + (item.marksObtained ?? 0), 0);
  const maximum = subject.assessments.reduce((sum, item) => sum + item.maxMarks, 0);
  const total = (obtained / maximum) * 100;
  const rows = subject.assessments.map((item) =>
    h(
      "li.row.marks__row",
      h("div.stack.stack--tight", h("span.row__title", item.name), h("span.row__meta", TYPES[item.type])),
      item.absent
        ? tag("Absent", "danger")
        : item.marksObtained === null
          ? tag("Not entered")
          : h("span.mono.num", `${two(item.marksObtained)} / ${item.maxMarks}`),
      h("span.row__meta.num.marks__pct", item.percent === null ? "—" : two(item.percent) + "%"),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    h(
      "div.line",
      h("div.stack", h("span.tag.tag--primary.mono", subject.subjectCode), h("h3.subject__name", subject.subjectName)),
      h("div.stack.stack--tight.align-end", h("span.figure.figure--small", two(total) + "%"), h("span.eyebrow", "So far")),
    ),
    bar(total, total >= 75 ? "success" : total < 50 ? "danger" : ""),
    h("div.line.marks__head", h("span.eyebrow", "Assessment"), h("span.eyebrow", "Marks")),
    h("ul.rows", rows),
    h("div.inset.inset--row", h("span.row__title", "Total"), h("span.mono.num", `${two(obtained)} / ${maximum}`)),
  );
}

function loadMarks() {
  const holder = document.getElementById("marks");
  holder.className = "grid grid--2";
  fill(holder, h("div.skeleton.skeleton--card"), h("div.skeleton.skeleton--card"));
  loadInto(holder, () => api.get("/marks/me?semester=" + semester), (subjects) => {
    if (!subjects.length) {
      holder.className = "card";
      fill(holder, empty("clock", "Marks have not been published yet", `Nothing is published for semester ${semester}.`));
      return;
    }
    fill(holder, stagger(subjects.map(subjectCard)));
    reveal();
  });
}

function marksView() {
  const numbers = Array.from({ length: currentSemester }, (_, index) => currentSemester - index);
  const pills = h(
    "div.pills",
    { role: "group", "aria-label": "Semester" },
    numbers.map((number) => {
      const pill = h(
        "button.pill",
        { type: "button", "aria-pressed": String(number === semester) },
        "Semester " + number,
        number === currentSemester ? h("span.pill__count", "current") : null,
      );
      pill.addEventListener("click", () => {
        semester = number;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        loadMarks();
      });
      return pill;
    }),
  );
  fill(content, h("div.card.card--bar.reveal", h("span.eyebrow", "Semester"), pills), h("div.grid.grid--2", { id: "marks" }));
  loadMarks();
  reveal();
}

const STATUS = {
  PASS: ["Pass", "success", "check-circle"],
  FAIL: ["Fail", "danger", "x-circle"],
  WITHHELD: ["Withheld", "warning", "minus-circle"],
};

const GRADE_KIND = { O: "success", "A+": "success", A: "success", "B+": "primary", B: "primary", C: "warning", D: "warning", S: "success" };

/** One subject of a semester: its grade, and how it was cleared when it took more than one try. */
function subjectRow(subject) {
  const kind = subject.passed ? GRADE_KIND[subject.grade] || "" : "danger";
  const later = subject.attempts > 1 ? (subject.passed ? `Cleared in ${subject.exam}` : `Attempt ${subject.attempts}: ${subject.exam}`) : null;
  return h(
    "li.row.grade",
    h(
      "div.stack.stack--tight",
      h("span.row__title", subject.subjectName),
      h("span.row__meta", h("span.mono", subject.subjectCode), subject.credits === null ? " · No credits" : ` · ${Number(subject.credits)} credit${Number(subject.credits) === 1 ? "" : "s"}`, later ? " · " + later : null),
    ),
    h("span.grade__value", subject.firstGrade ? h("s.muted", subject.firstGrade) : null, tag(subject.grade, kind)),
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
    subjects.length ? h("details.grades", { open: entry.sgpa === null || undefined }, h("summary", `Subjects and grades (${subjects.length})`), h("ul.rows", subjects.map(subjectRow))) : null,
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
        h("div.grid.grid--2", stagger(cards)),
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
      h("div.grid.grid--2", stagger(cards)),
      h("p.note.reveal", icon("info"), "CGPA is the average of your SGPAs, weighted by the credits registered in each semester. It is shown once every published semester has an SGPA, as the college does. A withheld semester is left out until it is resolved."),
    );
    reveal();
    enableTilt();
  });
}

function render(view) {
  if (view === "marks") {
    marksView();
  } else {
    semestersView();
  }
}

segmented(document.getElementById("view-tabs"), (view) => swap(content, () => render(view)));
currentStudent
  .then((student) => {
    currentSemester = student ? student.currentSemester : 1;
    semester = currentSemester;
  })
  .catch(() => {})
  .finally(() => render("marks"));
