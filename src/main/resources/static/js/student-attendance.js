import { fill, formatDate, h, icon, percent, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring, swap } from "./motion.js";
import { empty, isWide, openSheet } from "./parts.js";
import { attendance } from "./sample-data.js";
import "./shell.js";

// Attendance (FR-ATT-001, FR-ATT-002): the overall figure, one card per subject, and the dated
// history of the chosen subject. Everything is read-only.

const { overall, requiredPercent, subjects } = attendance;
const content = document.getElementById("content");
let filter = "all";
let chosen = subjects[0];

function stateOf(subject) {
  if (subject.percentage === null) {
    return "none";
  }
  return subject.belowRequired ? "below" : "ok";
}

function statusTag(subject) {
  const state = stateOf(subject);
  if (state === "none") {
    return tag("Not started", "", "clock");
  }
  return state === "below" ? tag("Below required", "danger", "alert") : tag("On track", "success", "check-circle");
}

function hero() {
  const figure = h("p.hero__figure");
  countUp(figure, overall.percentage, { decimals: 2, suffix: "%" });
  const margin = Math.abs(overall.percentage - requiredPercent).toFixed(2);
  const onTrack = overall.percentage >= requiredPercent;
  return h(
    "section.hero.hero--split.reveal",
    { "data-tilt": true, "aria-label": "Overall attendance" },
    h(
      "div.hero__main",
      h("p.hero__eyebrow", "Overall attendance"),
      figure,
      h(
        "p.chip" + (onTrack ? ".chip--good" : ".chip--bad"),
        icon(onTrack ? "check-circle" : "alert"),
        onTrack ? `On track · ${margin}% above the required ${requiredPercent}%` : `Below required · ${margin}% under ${requiredPercent}%`,
      ),
      h(
        "ul.chips",
        h("li.chip", icon("target"), "Required ", h("strong", requiredPercent + "%")),
        h("li.chip", icon("calendar"), "Classes held ", h("strong", overall.classesHeld)),
        h("li.chip", icon("check"), "Attended ", h("strong", overall.classesAttended)),
      ),
    ),
    h(
      "div.hero__aside",
      ring(overall.percentage, {
        size: 176,
        stroke: 14,
        label: [h("span.eyebrow.eyebrow--inverse", "Required"), h("strong.ring__big", requiredPercent + "%")],
      }),
    ),
  );
}

function historyPanel(subject) {
  const rows = subject.history.map((entry) => {
    const present = entry.status === "PRESENT";
    return h(
      "li.row.history__row" + (present ? "" : ".history__row--absent"),
      h("span.badge-icon.badge-icon--round" + (present ? ".badge-icon--success" : ".badge-icon--danger"), icon(present ? "check" : "x")),
      h("div.stack.stack--tight", h("span.row__title.num", formatDate(entry.date)), h("span.row__meta", "Period " + entry.periodNo)),
      present ? tag("Present", "success") : tag("Absent", "danger"),
    );
  });
  return [
    h(
      "div.stack.stack--tight",
      h("p.eyebrow", "History"),
      h("h2.card__title", subject.name, h("span.tag.mono", subject.code)),
      h("p.row__meta", rows.length ? `Newest first · latest ${rows.length} of ${subject.classesHeld} classes` : "Newest first"),
    ),
    rows.length ? h("ul.rows.history", rows) : empty("calendar", "No classes recorded yet", "The history appears once classes are held."),
    h("p.note", icon("lock"), "Attendance is recorded by the college and cannot be changed here."),
  ];
}

function subjectCard(subject) {
  const state = stateOf(subject);
  const card = h(
    "button.card.card--choice.reveal" + (state === "below" ? ".card--alert" : ""),
    { type: "button", "aria-pressed": String(subject === chosen && isWide()) },
    h("div.line", h("span.tag.mono", subject.code), statusTag(subject)),
    h("h3.subject__name", subject.name),
    state === "none"
      ? h("p.row__meta", "No classes recorded yet")
      : h(
          "div.line",
          h("span.row__meta.num", `Classes held ${subject.classesHeld} · Attended ${subject.classesAttended}`),
          h("span.figure.figure--small" + (state === "below" ? ".figure--danger" : ""), percent(subject.percentage)),
        ),
    bar(subject.percentage ?? 0, state === "below" ? "danger" : state === "ok" ? "success" : "neutral"),
    h("span.more", "View history", icon("chevron-right")),
  );
  card.addEventListener("click", () => choose(subject));
  return card;
}

function choose(subject) {
  chosen = subject;
  if (isWide()) {
    for (const card of document.querySelectorAll("#subjects .card--choice")) {
      card.setAttribute("aria-pressed", "false");
    }
    const index = visibleSubjects().indexOf(subject);
    document.querySelectorAll("#subjects .card--choice")[index]?.setAttribute("aria-pressed", "true");
    swap(document.getElementById("history"), () => fill(document.getElementById("history"), historyPanel(subject)));
  } else {
    openSheet(historyPanel(subject));
  }
}

function visibleSubjects() {
  return subjects.filter((subject) => filter === "all" || stateOf(subject) === filter);
}

function renderSubjects() {
  const list = visibleSubjects();
  fill(
    document.getElementById("subjects"),
    list.length ? stagger(list.map(subjectCard)) : empty("filter", "No subjects here", "Try another filter."),
  );
}

function filters() {
  const options = [
    ["all", "All"],
    ["ok", "On track"],
    ["below", "Below required"],
    ["none", "Not started"],
  ];
  return h(
    "div.pills",
    { role: "group", "aria-label": "Filter subjects" },
    options.map(([value, label]) => {
      const count = subjects.filter((subject) => value === "all" || stateOf(subject) === value).length;
      const pill = h("button.pill", { type: "button", "aria-pressed": String(value === filter) }, label, h("span.pill__count", count));
      pill.addEventListener("click", () => {
        filter = value;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        swap(document.getElementById("subjects"), renderSubjects);
      });
      return pill;
    }),
  );
}

fill(
  content,
  hero(),
  h("div.section-head.reveal", h("h2", "Subjects"), filters()),
  h(
    "div.grid.grid--split",
    h("div.stack.stack--loose", { id: "subjects" }),
    h("aside.card.sticky.reveal.only-wide", { id: "history", "aria-live": "polite" }, historyPanel(chosen)),
  ),
);
renderSubjects();
reveal();
enableTilt();
