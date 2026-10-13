import { api } from "./api.js";
import { fill, h, icon, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring } from "./motion.js";
import { empty, loadInto, resourceTypes } from "./parts.js";
import "./shell.js";

// Progress (FR-PRG-001, FR-PRG-002): how the student is doing in each unit, and what to revise
// next. The levels and the recommendations are worked out on the server by fixed rules from the
// student's submitted tests. No AI is involved.

const content = document.getElementById("content");

const LEVELS = {
  STRONG: { label: "Strong", kind: "success", iconName: "check", bar: "success" },
  IMPROVING: { label: "Improving", kind: "", iconName: "", bar: "neutral" },
  NEEDS_REVISION: { label: "Needs revision", kind: "danger", iconName: "alert", bar: "danger" },
  NOT_ENOUGH_DATA: { label: "Not enough data", kind: "", iconName: "", bar: "neutral" },
};

const quizLink = (unit) => `/student/quiz.html?subjectId=${unit.subjectId}&unitId=${unit.unitId}`;
const rounded = (value) => Math.round(value) + "%";

function hero(progress) {
  const tests = h("p.hero__figure");
  const average = h("p.hero__figure");
  const averagePercent = Math.round(progress.averagePercent);
  countUp(tests, progress.testsTaken);
  countUp(average, averagePercent, { suffix: "%" });
  return h(
    "section.hero.hero--split.reveal",
    { "data-tilt": true, "aria-label": "Summary" },
    h(
      "div.hero__main",
      h("p.hero__eyebrow", "From your quick tests"),
      h("div.hero__pair", h("div.stack.stack--tight", tests, h("p.hero__sub", "Tests submitted")), h("div.stack.stack--tight", average, h("p.hero__sub", "Average score"))),
    ),
    h("div.hero__aside", ring(averagePercent, { size: 152, stroke: 12, label: [h("strong.ring__big", averagePercent + "%"), h("span.eyebrow.eyebrow--inverse", "Average")] })),
  );
}

/** The units the server sent, gathered under their subjects. */
function bySubject(units) {
  const subjects = new Map();
  for (const unit of units) {
    if (!subjects.has(unit.subjectId)) {
      subjects.set(unit.subjectId, { id: unit.subjectId, code: unit.subjectCode, name: unit.subjectName, units: [] });
    }
    subjects.get(unit.subjectId).units.push(unit);
  }
  return [...subjects.values()];
}

function subjectCard(subject) {
  const answered = subject.units.reduce((sum, unit) => sum + unit.answered, 0);
  const correct = subject.units.reduce((sum, unit) => sum + unit.correct, 0);
  return h(
    "section.card.card--lift.reveal",
    h(
      "div.line",
      h("div.stack", h("span.tag.tag--primary.mono", subject.code), h("h3.subject__name", subject.name)),
      h("div.stack.stack--tight.align-end", h("span.figure.figure--small", rounded((correct / answered) * 100)), h("span.eyebrow", "Recent accuracy")),
    ),
    h(
      "ul.rows",
      subject.units.map((unit) => {
        const state = LEVELS[unit.level] || LEVELS.NOT_ENOUGH_DATA;
        return h(
          "li.row.unit",
          h("div.line", h("span.row__title.clip", `Unit ${unit.unitNo}: ${unit.unitTitle}`), h("span.line.line--start", h("strong.num", rounded(unit.accuracyPercent)), tag(state.label, state.kind, state.iconName))),
          bar(unit.accuracyPercent, state.bar),
          h("p.row__meta.num", `${unit.correct} of ${unit.answered} recent answers correct`),
        );
      }),
    ),
    h("div.card__foot", h("span", subject.units.length === 1 ? "1 unit practised" : subject.units.length + " units practised"), h("a.more", { href: "/student/quiz.html?subjectId=" + subject.id }, "Practise", icon("chevron-right"))),
  );
}

function recommendation(item) {
  const links = item.resources.map((resource) =>
    h("a.button.button--secondary.button--small", { href: "/student/resource.html?id=" + resource.id }, icon("file"), h("span.clip", resource.title), h("span.visually-hidden", ` (${resourceTypes[resource.type]?.label || "resource"})`)),
  );
  return h(
    "article.card.reveal.recommend",
    h("span.badge-icon.badge-icon--round.badge-icon--danger", icon("alert")),
    h(
      "div.stack",
      h("div.line.line--start.line--wrap", h("span.tag.mono", item.subjectCode), tag(`Accuracy ${rounded(item.accuracyPercent)}`, "danger")),
      h("h3.subject__name", `Unit ${item.unitNo}: ${item.unitTitle}`),
      links.length ? null : h("p.row__meta", "No study material has been uploaded for this unit yet."),
    ),
    h("div.recommend__actions", links, h("a.button.button--primary.button--small", { href: quizLink(item) }, icon("refresh"), "Retake test")),
  );
}

function render(progress) {
  if (!progress.testsTaken) {
    const start = h("a.button.button--primary.button--small", { href: "/student/quiz.html" }, "Start a quick test");
    fill(content, h("div.card", empty("progress", "Nothing to show yet", "Take a quick test to see your progress here.", start)));
    return;
  }
  const subjects = bySubject(progress.units);
  fill(
    content,
    hero(progress),
    h(
      "div.section-head.reveal",
      h("div.section-head__text", h("h2", "Strengths and weak areas"), h("p.row__meta", "Judged on your latest 20 answers in each unit, once you have answered 5.")),
      h("div.line.line--start.line--wrap", tag("Strong · 80% or more", "success"), tag("Improving · 60 to 79%"), tag("Needs revision · below 60%", "danger")),
    ),
    // One subject fills the row; two or more sit side by side.
    h(subjects.length > 1 ? "div.grid.grid--2" : "div.stack.stack--loose", stagger(subjects.map(subjectCard))),
    h("div.section-head.reveal", h("div.section-head__text", h("h2", "What to revise next"), h("p.row__meta", "Units that need revision, the weakest first."))),
    progress.recommendations.length
      ? h("div.stack.stack--loose", stagger(progress.recommendations.map(recommendation)))
      : h("section.card.card--tinted.reveal.recommend", h("span.badge-icon.badge-icon--round.badge-icon--success", icon("check")), h("div.stack.stack--tight", h("h3", "Nothing needs revision right now"), h("p.row__meta", "No unit you have practised is below 60%."))),
    h(
      "section.card.card--tinted.reveal.recommend",
      h("span.badge-icon.badge-icon--round.badge-icon--neutral", icon("bolt")),
      h("div.stack.stack--tight", h("h3", "Try a unit you have not practised"), h("p.row__meta", "A five-minute test is enough to see where you stand.")),
      h("div.recommend__actions", h("a.button.button--primary.button--small", { href: "/student/quiz.html" }, icon("play"), "Start a quick test")),
    ),
  );
  reveal();
  enableTilt();
}

loadInto(content, () => api.get("/progress/me"), render);
