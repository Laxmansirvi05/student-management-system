import { fill, h, icon, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring } from "./motion.js";
import { empty } from "./parts.js";
import { progress } from "./sample-data.js";
import "./shell.js";

// Progress (FR-PRG-001, FR-PRG-002): how the student scores in each unit, and what to revise
// next. The averages and the recommendations are worked out on the server from past tests.

const content = document.getElementById("content");

/** Strong from 75%, needs work below 50%, okay in between. */
function level(averagePercent) {
  if (averagePercent === null) {
    return { label: "Not attempted yet", kind: "", iconName: "", bar: "neutral" };
  }
  if (averagePercent >= 75) {
    return { label: "Strong", kind: "success", iconName: "check", bar: "success" };
  }
  if (averagePercent < 50) {
    return { label: "Needs work", kind: "danger", iconName: "alert", bar: "danger" };
  }
  return { label: "Okay", kind: "", iconName: "", bar: "neutral" };
}

function hero() {
  const tests = h("p.hero__figure");
  const average = h("p.hero__figure");
  countUp(tests, progress.testsTaken);
  countUp(average, progress.averagePercent, { suffix: "%" });
  return h(
    "section.hero.hero--split.reveal",
    { "data-tilt": true, "aria-label": "Summary" },
    h(
      "div.hero__main",
      h("p.hero__eyebrow", "From your quick tests"),
      h("div.hero__pair", h("div.stack.stack--tight", tests, h("p.hero__sub", "Tests taken")), h("div.stack.stack--tight", average, h("p.hero__sub", "Average score"))),
    ),
    h("div.hero__aside", ring(progress.averagePercent, { size: 152, stroke: 12, label: [h("strong.ring__big", progress.averagePercent + "%"), h("span.eyebrow.eyebrow--inverse", "Average")] })),
  );
}

function subjectCard(subject) {
  const tried = subject.units.filter((unit) => unit.averagePercent !== null).length;
  return h(
    "section.card.card--lift.reveal",
    h(
      "div.line",
      h("div.stack", h("span.tag.tag--primary.mono", subject.code), h("h3.subject__name", subject.name)),
      h("div.stack.stack--tight.align-end", h("span.figure.figure--small", subject.averagePercent + "%"), h("span.eyebrow", "Overall")),
    ),
    h(
      "ul.rows",
      subject.units.map((unit) => {
        const state = level(unit.averagePercent);
        return h(
          "li.row.unit" + (unit.averagePercent === null ? ".unit--untried" : ""),
          h(
            "div.line",
            h("span.row__title.clip", `Unit ${unit.unitNo}: ${unit.title}`),
            h("span.line.line--start", unit.averagePercent === null ? null : h("strong.num", unit.averagePercent + "%"), tag(state.label, state.kind, state.iconName)),
          ),
          bar(unit.averagePercent ?? 0, state.bar),
        );
      }),
    ),
    h("div.card__foot", h("span", `${tried} of ${subject.units.length} units tried`), h("a.more", { href: `/student/quiz.html?subject=${subject.code}` }, "Practise", icon("chevron-right"))),
  );
}

function recommendation(item) {
  const weak = item.averagePercent < 50;
  return h(
    "article.card.reveal.recommend",
    h("span.badge-icon.badge-icon--round" + (weak ? ".badge-icon--danger" : ".badge-icon--warning"), icon(weak ? "alert" : "target")),
    h(
      "div.stack",
      h("div.line.line--start.line--wrap", h("span.tag.mono", item.subjectCode), tag(`Average ${item.averagePercent}% over ${item.attempts} tests`, weak ? "danger" : "warning")),
      h("h3.subject__name", `${item.subjectName} · Unit ${item.unitNo}: ${item.unitTitle}`),
    ),
    h(
      "div.recommend__actions",
      h("a.button.button--secondary.button--small", { href: "/student/resource.html?id=" + item.resourceId }, icon("file"), "Open notes"),
      h("a.button.button--primary.button--small", { href: `/student/quiz.html?subject=${item.subjectCode}&unit=${item.unitNo}` }, icon("play"), "Practise this unit"),
    ),
  );
}

if (progress.testsTaken) {
  fill(
    content,
    hero(),
    h(
      "div.section-head.reveal",
      h("div.section-head__text", h("h2", "Strengths and weak areas"), h("p.row__meta", "Your average score in each unit.")),
      h("div.line.line--start.line--wrap", tag("Strong · 75% or more", "success"), tag("Okay · 50 to 74%"), tag("Needs work · below 50%", "danger")),
    ),
    h("div.grid.grid--2", stagger(progress.subjects.map(subjectCard))),
    h("div.section-head.reveal", h("div.section-head__text", h("h2", "What to revise next"), h("p.row__meta", "Your lowest-scoring units first."))),
    h("div.stack.stack--loose", stagger(progress.recommendations.map(recommendation))),
    h(
      "section.card.card--tinted.reveal.recommend",
      h("span.badge-icon.badge-icon--round.badge-icon--neutral", icon("bolt")),
      h("div.stack.stack--tight", h("h3", "Some units have no tests yet"), h("p.row__meta", "A five-minute test is enough to see where you stand.")),
      h("div.recommend__actions", h("a.button.button--primary.button--small", { href: "/student/quiz.html" }, icon("play"), "Start a quick test")),
    ),
  );
} else {
  const start = h("a.button.button--primary.button--small", { href: "/student/quiz.html" }, "Start a quick test");
  fill(content, h("div.card", empty("progress", "Nothing to show yet", "Take a quick test to see your progress here.", start)));
}
reveal();
enableTilt();
