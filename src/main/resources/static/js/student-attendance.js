import { api } from "./api.js";
import { fill, formatDate, h, icon, percent, stagger, tag } from "./dom.js";
import { bar, countUp, enableTilt, reveal, ring, swap } from "./motion.js";
import { empty, isWide, loadInto, openSheet } from "./parts.js";
import "./shell.js";

// Attendance (FR-ATT-001, FR-ATT-002): the overall figure, one card per subject, and the dated
// history of the chosen subject. Everything is read-only and belongs to the logged-in student.

const HISTORY_SIZE = 20;
const content = document.getElementById("content");
let summary = null;
let filter = "all";
let chosen = null;

function stateOf(subject) {
  if (subject.percent === null) {
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
  const { overallPercent, requiredPercent, subjects, report } = summary;
  let held = subjects.reduce((sum, subject) => sum + subject.classesHeld, 0);
  let attended = subjects.reduce((sum, subject) => sum + subject.classesAttended, 0);
  // Without subject-wise records, the college's report is the figure.
  const fromReport = held === 0 && report;
  if (fromReport) {
    held = report.classesHeld;
    attended = report.classesAttended;
  }
  if (overallPercent === null) {
    return h(
      "section.hero.reveal",
      { "aria-label": "Overall attendance" },
      h("div.hero__main", h("p.hero__eyebrow", "Overall attendance"), h("h2.hero__title", "No classes recorded yet"), h("p.hero__sub", `Your attendance appears here once classes are held. The college requires ${requiredPercent}% in every subject.`)),
    );
  }
  const figure = h("p.hero__figure");
  countUp(figure, overallPercent, { decimals: 2, suffix: "%" });
  const margin = Math.abs(overallPercent - requiredPercent).toFixed(2);
  const onTrack = overallPercent >= requiredPercent;
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
      fromReport ? h("p.hero__sub", `College report, ${formatDate(report.fromDate)} to ${formatDate(report.toDate)}`) : null,
      h(
        "ul.chips",
        h("li.chip", icon("target"), "Required ", h("strong", requiredPercent + "%")),
        h("li.chip", icon("calendar"), "Classes held ", h("strong", held)),
        h("li.chip", icon("check"), "Attended ", h("strong", attended)),
      ),
    ),
    h(
      "div.hero__aside",
      ring(overallPercent, { size: 176, stroke: 14, label: [h("span.eyebrow.eyebrow--inverse", "Required"), h("strong.ring__big", requiredPercent + "%")] }),
    ),
  );
}

/** The college's report, when the hero shows subject-wise records instead. */
function reportCard() {
  const { report } = summary;
  const subjectTotal = summary.subjects.reduce((sum, subject) => sum + subject.classesHeld, 0);
  if (!report || subjectTotal === 0) {
    return null;
  }
  return h(
    "section.card.reveal" + (report.belowRequired ? ".card--alert" : ""),
    { "aria-label": "College attendance report" },
    h("div.line", h("p.eyebrow", "College report"), report.belowRequired ? tag("Below required", "danger", "alert") : tag("On track", "success", "check-circle")),
    h(
      "div.line",
      h("span.row__meta.num", `${formatDate(report.fromDate)} to ${formatDate(report.toDate)} · Attended ${report.classesAttended} of ${report.classesHeld}`),
      h("span.figure.figure--small" + (report.belowRequired ? ".figure--danger" : ""), percent(report.percent)),
    ),
    bar(report.percent, report.belowRequired ? "danger" : "success"),
  );
}

function historyHead(subject, note) {
  return h(
    "div.stack.stack--tight",
    h("p.eyebrow", "History"),
    h("h2.card__title", subject.subjectName, h("span.tag.mono", subject.subjectCode)),
    h("p.row__meta", note),
  );
}

function historyRows(page) {
  return page.content.map((entry) => {
    const present = entry.status === "PRESENT";
    return h(
      "li.row.history__row" + (present ? "" : ".history__row--absent"),
      h("span.badge-icon.badge-icon--round" + (present ? ".badge-icon--success" : ".badge-icon--danger"), icon(present ? "check" : "x")),
      h("div.stack.stack--tight", h("span.row__title.num", formatDate(entry.classDate)), h("span.row__meta", "Period " + entry.periodNo)),
      present ? tag("Present", "success") : tag("Absent", "danger"),
    );
  });
}

/** Draws the history of one subject into a container: a loading line first, then the rows. */
async function showHistory(container, subject) {
  fill(container, historyHead(subject, "Loading…"), h("div.skeleton"), h("div.skeleton"), h("div.skeleton"));
  await loadInto(
    container,
    () => api.get(`/attendance/me/subjects/${subject.subjectId}?size=${HISTORY_SIZE}`),
    (page) => {
      const note = page.totalElements ? `Newest first · latest ${page.content.length} of ${page.totalElements} classes` : "Newest first";
      fill(
        container,
        historyHead(subject, note),
        page.content.length ? h("ul.rows.history", historyRows(page)) : empty("calendar", "No classes recorded yet", "The history appears once classes are held."),
        h("p.note", icon("lock"), "Attendance is recorded by the college and cannot be changed here."),
      );
    },
  );
}

function subjectCard(subject) {
  const state = stateOf(subject);
  const card = h(
    "button.card.card--choice.reveal" + (state === "below" ? ".card--alert" : ""),
    { type: "button", "aria-pressed": String(subject === chosen && isWide()) },
    h("div.line", h("span.tag.mono", subject.subjectCode), statusTag(subject)),
    h("h3.subject__name", subject.subjectName),
    state === "none"
      ? h("p.row__meta", "No classes recorded yet")
      : h(
          "div.line",
          h("span.row__meta.num", `Classes held ${subject.classesHeld} · Attended ${subject.classesAttended}`),
          h("span.figure.figure--small" + (state === "below" ? ".figure--danger" : ""), percent(subject.percent)),
        ),
    bar(subject.percent ?? 0, state === "below" ? "danger" : state === "ok" ? "success" : "neutral"),
    h("span.more", "View history", icon("chevron-right")),
  );
  card.addEventListener("click", () => choose(subject, card));
  return card;
}

function choose(subject, card) {
  chosen = subject;
  if (isWide()) {
    for (const other of document.querySelectorAll("#subjects .card--choice")) {
      other.setAttribute("aria-pressed", String(other === card));
    }
    showHistory(document.getElementById("history"), subject);
  } else {
    const holder = h("div.stack.stack--loose");
    openSheet(holder);
    showHistory(holder, subject);
  }
}

function renderSubjects() {
  const list = summary.subjects.filter((subject) => filter === "all" || stateOf(subject) === filter);
  fill(
    document.getElementById("subjects"),
    list.length ? stagger(list.map(subjectCard)) : h("div.card", empty("filter", "No subjects here", "Try another filter.")),
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
      const count = summary.subjects.filter((subject) => value === "all" || stateOf(subject) === value).length;
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

loadInto(content, () => api.get("/attendance/me"), (data) => {
  summary = data;
  if (!summary.subjects.length) {
    const note = summary.report
      ? empty("resources", "Subject-wise attendance comes later", "The figure above is the college's report. Attendance for each subject appears once the college records it here.")
      : empty("resources", "No subjects yet", "Your subjects for this semester have not been set up.");
    fill(content, hero(), h("div.card", note));
    reveal();
    return;
  }
  chosen = summary.subjects[0];
  fill(
    content,
    hero(),
    reportCard(),
    h("div.section-head.reveal", h("h2", "Subjects"), filters()),
    h("div.grid.grid--split", h("div.stack.stack--loose", { id: "subjects" }), h("aside.card.sticky.reveal.only-wide", { id: "history", "aria-live": "polite" })),
  );
  renderSubjects();
  if (isWide()) {
    showHistory(document.getElementById("history"), chosen);
  }
  reveal();
  enableTilt();
});
