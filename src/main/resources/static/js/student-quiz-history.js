import { api } from "./api.js";
import { fill, formatDate, h, icon, stagger, tag } from "./dom.js";
import { countUp, enableTilt, reveal } from "./motion.js";
import { empty, loadInto, scoreKind } from "./parts.js";
import "./shell.js";

// Attempt history (FR-QZ-004): every test the student has taken, newest first. The server sends
// only this student's tests, and a result can be opened again from here.

const PAGE_SIZE = 20;
const content = document.getElementById("content");
let pageNumber = 0;

function stat(label, value, suffix = "") {
  const figure = h("p.stat__value");
  if (value === null) {
    figure.textContent = "—";
  } else {
    countUp(figure, value, { suffix });
  }
  return h("div.stat.reveal", { "data-tilt": true }, figure, h("p.stat__label", label));
}

function row(attempt) {
  const what = h("div.stack.stack--tight.hrow__what", h("span.row__title", attempt.subjectName), h("span.row__meta", `Unit ${attempt.unitNo}: ${attempt.unitTitle}`));
  const date = h("span.row__meta.num.hrow__date", formatDate(attempt.startedAt));
  if (attempt.status === "IN_PROGRESS") {
    return h("li.row.hrow.reveal", date, what, h("strong.mono.num.hrow__score", "—"), h("span.hrow__pct", tag("In progress", "primary", "timer")), h("span.hrow__view"));
  }
  const percentage = (attempt.score / attempt.questionCount) * 100;
  const kind = scoreKind(percentage);
  const result = attempt.status === "EXPIRED" ? tag("Ran out of time", "warning", "timer") : tag(Math.round(percentage) + "%", kind, kind === "success" ? "check" : kind === "danger" ? "alert" : "");
  return h(
    "li.row.hrow.reveal",
    date,
    what,
    h("strong.mono.num.hrow__score", `${attempt.score} / ${attempt.questionCount}`),
    h("span.hrow__pct", result),
    h("a.more.hrow__view", { href: "/student/quiz.html?attempt=" + attempt.attemptId, "aria-label": `View the ${attempt.subjectName} test of ${formatDate(attempt.startedAt)}` }, "View", icon("chevron-right")),
  );
}

function pager(page) {
  if (page.totalPages <= 1) {
    return null;
  }
  const button = (label, iconName, target, before) => {
    const element = h("button.button.button--secondary.button--small", { type: "button", disabled: target < 0 || target >= page.totalPages }, before ? icon(iconName) : null, label, before ? null : icon(iconName));
    element.addEventListener("click", () => {
      pageNumber = target;
      loadRows();
    });
    return element;
  };
  return h("nav.pager", { "aria-label": "Pages" }, button("Newer", "arrow-left", page.page - 1, true), h("span.row__meta.num", "Page ", h("strong", page.page + 1), " of " + page.totalPages), button("Older", "arrow-right", page.page + 1, false));
}

function loadRows() {
  const holder = document.getElementById("tests");
  loadInto(holder, () => api.get(`/quiz-attempts/me?size=${PAGE_SIZE}&page=${pageNumber}`), (page) => {
    fill(
      holder,
      h("div.hrow.hrow--head", h("span.eyebrow", "Date"), h("span.eyebrow", "Subject and unit"), h("span.eyebrow", "Score"), h("span.eyebrow", "Result"), h("span")),
      h("ul.rows", stagger(page.content.map(row))),
      pager(page),
    );
    reveal();
  });
}

loadInto(
  content,
  () => Promise.all([api.get("/progress/me"), api.get(`/quiz-attempts/me?size=1`)]),
  ([progress, history]) => {
    if (!history.totalElements) {
      const start = h("a.button.button--primary.button--small", { href: "/student/quiz.html" }, "Start a quick test");
      fill(content, h("div.card", empty("history", "You have not taken a test yet", "Your tests appear here once you take one.", start)));
      return;
    }
    fill(
      content,
      h(
        "section.stats.stats--3",
        { "aria-label": "Summary" },
        stagger([
          stat("Tests submitted", progress.testsTaken),
          stat("Average score", progress.averagePercent === null ? null : Math.round(progress.averagePercent), "%"),
          stat("Units practised", progress.units.length),
        ]),
      ),
      h("section.card.reveal", { id: "tests", "aria-label": "Tests" }, h("div.skeleton")),
    );
    loadRows();
    reveal();
    enableTilt();
  },
);
