import { fill, formatDate, h, icon, stagger, tag } from "./dom.js";
import { countUp, enableTilt, reveal, swap } from "./motion.js";
import { empty, scoreKind } from "./parts.js";
import { quiz } from "./sample-data.js";
import "./shell.js";

// Attempt history (FR-QZ-004): every test the student has taken, newest first.

const attempts = quiz.attempts;
const content = document.getElementById("content");
const percentOf = (attempt) => (attempt.score / attempt.total) * 100;
let subject = "";

function stat(label, value, suffix = "") {
  const figure = h("p.stat__value");
  countUp(figure, value, { suffix });
  return h("div.stat.reveal", { "data-tilt": true }, figure, h("p.stat__label", label));
}

function row(attempt) {
  const percentage = percentOf(attempt);
  const kind = scoreKind(percentage);
  return h(
    "li.row.hrow.reveal",
    h("span.row__meta.num.hrow__date", formatDate(attempt.takenAt)),
    h("div.stack.stack--tight.hrow__what", h("span.row__title", attempt.subjectName), h("span.row__meta", `Unit ${attempt.unitNo}: ${attempt.unitTitle}`)),
    h("strong.mono.num.hrow__score", `${attempt.score} / ${attempt.total}`),
    h("span.hrow__pct", tag(Math.round(percentage) + "%", kind, kind === "success" ? "check" : kind === "danger" ? "alert" : "")),
    h("a.more.hrow__view", { href: "/student/quiz.html?attempt=" + attempt.id, "aria-label": `View the ${attempt.subjectName} test of ${formatDate(attempt.takenAt)}` }, "View", icon("chevron-right")),
  );
}

function renderRows() {
  const list = attempts.filter((attempt) => !subject || attempt.subjectName === subject);
  fill(document.getElementById("rows"), stagger(list.map(row)));
}

function filters() {
  const names = [...new Set(attempts.map((attempt) => attempt.subjectName))];
  return h(
    "div.pills",
    { role: "group", "aria-label": "Subject" },
    [["", "All"], ...names.map((name) => [name, name])].map(([value, label]) => {
      const pill = h("button.pill", { type: "button", "aria-pressed": String(value === subject) }, label);
      pill.addEventListener("click", () => {
        subject = value;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        swap(document.getElementById("rows"), renderRows);
      });
      return pill;
    }),
  );
}

if (attempts.length) {
  const average = attempts.reduce((sum, attempt) => sum + percentOf(attempt), 0) / attempts.length;
  const best = Math.max(...attempts.map(percentOf));
  fill(
    content,
    h("section.stats.stats--3", { "aria-label": "Summary" }, stagger([stat("Tests taken", attempts.length), stat("Average score", Math.round(average), "%"), stat("Best score", Math.round(best), "%")])),
    h("div.reveal", filters()),
    h(
      "section.card.reveal",
      { "aria-label": "Tests" },
      h("div.hrow.hrow--head", h("span.eyebrow", "Date"), h("span.eyebrow", "Subject and unit"), h("span.eyebrow", "Score"), h("span.eyebrow", "Percentage"), h("span")),
      h("ul.rows", { id: "rows" }),
    ),
  );
  renderRows();
} else {
  const start = h("a.button.button--primary.button--small", { href: "/student/quiz.html" }, "Start a quick test");
  fill(content, h("div.card", empty("history", "You have not taken a test yet", "Your tests appear here once you take one.", start)));
}
reveal();
enableTilt();
