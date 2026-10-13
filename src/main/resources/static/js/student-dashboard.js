import { daysUntil, fill, formatDate, formatToday, h, icon, percent, stagger, tag } from "./dom.js";
import { countUp, enableTilt, reveal, ring } from "./motion.js";
import { cardHead, categoryTag, dueText, empty } from "./parts.js";
import { attendance, notices, progress, results, timetable } from "./sample-data.js";
import { currentStudent, currentUser } from "./shell.js";

// The student's home page: six cards, each a summary that links to its full page.
// The greeting and the student's details are real; the cards show sample data for now.

function greeting() {
  const hour = new Date().getHours();
  if (hour < 12) {
    return "Good morning";
  }
  return hour < 17 ? "Good afternoon" : "Good evening";
}

document.getElementById("today").textContent = formatToday();
currentUser
  .then((user) => {
    document.getElementById("greeting").textContent = `${greeting()}, ${user.displayName}`;
  })
  .catch(() => {});
currentStudent
  .then((student) => {
    if (!student) {
      return;
    }
    document.getElementById("programme").textContent =
      `${student.branch} · Semester ${student.currentSemester} · Section ${student.section}`;
    fill(
      document.getElementById("hero-chips"),
      h("li.chip", icon("id"), "Roll no. ", h("strong.mono", student.rollNo)),
      h("li.chip", icon("calendar"), `${todaysClasses().length} classes today`),
    );
  })
  .catch(() => {});

function todaysClasses() {
  const dayOfWeek = new Date().getDay() || 7;
  return timetable
    .filter((entry) => entry.dayOfWeek === dayOfWeek)
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
}

function attendanceCard() {
  const { overall, requiredPercent, subjects } = attendance;
  const below = subjects.filter((subject) => subject.belowRequired);
  const figure = h("p.figure");
  countUp(figure, overall.percentage, { decimals: 2, suffix: "%" });
  const warning = below.length
    ? h(
        "p.inset.inset--warning.inset--note",
        icon("alert"),
        h("span", `${below.length} subject${below.length > 1 ? "s" : ""} below ${requiredPercent}%: `, h("strong", below.map((subject) => `${subject.name} (${percent(subject.percentage)})`).join(", "))),
      )
    : h("p.inset.inset--note", icon("check-circle"), `Every subject is at or above ${requiredPercent}%.`);
  return h(
    "section.card.card--lift.reveal",
    cardHead("attendance", "Attendance", "/student/attendance.html"),
    h(
      "div.line",
      h("div.stack.stack--tight", h("p.eyebrow", "Overall"), figure, h("p.row__meta", `${overall.classesAttended} of ${overall.classesHeld} classes attended`)),
      ring(overall.percentage, { size: 104, stroke: 10, label: icon("cap") }),
    ),
    warning,
  );
}

function resultsCard() {
  const latest = results.semesters[0];
  const tile = (label, value, caption) => {
    const figure = h("p.figure.figure--small");
    countUp(figure, value, { decimals: 2 });
    return h("div.inset", h("p.eyebrow", label), figure, h("p.row__meta", caption));
  };
  const earned = results.semesters.reduce((sum, semester) => sum + semester.creditsEarned, 0);
  const registered = results.semesters.reduce((sum, semester) => sum + semester.creditsRegistered, 0);
  return h(
    "section.card.card--lift.reveal",
    cardHead("results", "Results", "/student/results.html"),
    h(
      "div.tiles",
      tile("Latest SGPA", latest.sgpa, `Semester ${latest.semester}`),
      tile("CGPA", results.cgpa, `${results.semesters.length} semesters`),
    ),
    h(
      "div.card__foot",
      latest.resultStatus === "PASS"
        ? tag("Passed semester " + latest.semester, "success", "check-circle")
        : tag("Semester " + latest.semester + ": not passed", "danger", "x-circle"),
      h("span.num", `Credits earned ${earned} of ${registered}`),
    ),
  );
}

/** Where a class stands against the clock: done, on now, the next one, or later today. */
function classState(entry, now, isNext) {
  if (entry.endTime <= now) {
    return tag("Completed");
  }
  if (entry.startTime <= now) {
    return tag("Now", "solid");
  }
  return isNext ? tag("Next", "primary") : tag("Later");
}

function classesCard() {
  const list = todaysClasses();
  const now = new Date().toTimeString().slice(0, 5);
  const next = list.find((entry) => entry.startTime > now);
  const rows = list.slice(0, 4).map((entry) =>
    h(
      "li.row",
      h("div.row__top", h("span.row__title.clip", entry.subjectName), h("span.mono.num", `${entry.startTime} – ${entry.endTime}`)),
      h("div.row__top", h("span.row__meta.clip", `${entry.room} · ${entry.facultyName}`), classState(entry, now, entry === next)),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("clock", "Today's classes", "/student/timetable.html"),
    rows.length ? h("ul.rows", rows) : empty("coffee", "No classes today", "Enjoy the break."),
  );
}

function deadlinesCard() {
  const upcoming = notices
    .filter((notice) => notice.dueDate && daysUntil(notice.dueDate) >= 0)
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
    .slice(0, 3);
  const rows = upcoming.map((notice) =>
    h(
      "li.row",
      h("div.row__top", categoryTag(notice.category), h("span.row__meta.num", formatDate(notice.dueDate))),
      h("a.row__title.row__link", { href: "/student/notices.html?id=" + notice.id }, notice.title),
      h("p.row__meta" + (daysUntil(notice.dueDate) <= 3 ? ".row__meta--warning" : ""), dueText(notice.dueDate)),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("check-circle", "Upcoming deadlines", "/student/notices.html"),
    rows.length ? h("ul.rows", rows) : empty("check-circle", "Nothing due", "You have no upcoming deadlines."),
  );
}

function noticesCard() {
  const rows = notices.slice(0, 3).map((notice) =>
    h(
      "li.row",
      h("div.row__top", categoryTag(notice.category), h("span.row__meta.num", formatDate(notice.publishedAt))),
      h("a.row__title.row__link", { href: "/student/notices.html?id=" + notice.id }, notice.title),
      h("p.row__meta.clip", notice.body.split("\n")[0]),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("notices", "Recent notices", "/student/notices.html"),
    rows.length ? h("ul.rows", rows) : empty("bell", "No notices yet", ""),
  );
}

function recommendationsCard() {
  const rows = progress.recommendations.slice(0, 2).map((item) =>
    h(
      "a.inset.inset--link",
      { href: "/student/progress.html" },
      h("div.line", h("span.row__title", `Revise Unit ${item.unitNo}: ${item.unitTitle}`), icon("arrow-right")),
      h("p.row__meta", `${item.subjectName} · ${item.subjectCode}`),
      h("p.reason", icon("alert"), `Average ${item.averagePercent}% over ${item.attempts} tests`),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("target", "Revise next", "/student/progress.html"),
    rows.length
      ? h("div.stack", rows)
      : empty("target", "No suggestions yet", "Take a quick test to see what to revise."),
  );
}

const content = document.getElementById("content");
fill(
  content,
  stagger([attendanceCard(), resultsCard(), classesCard(), deadlinesCard(), noticesCard(), recommendationsCard()]),
);
reveal();
enableTilt();
