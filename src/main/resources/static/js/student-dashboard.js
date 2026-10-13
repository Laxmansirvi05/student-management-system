import { api } from "./api.js";
import { daysUntil, fill, formatDate, formatToday, h, icon, percent, stagger, tag } from "./dom.js";
import { countUp, enableTilt, reveal, ring } from "./motion.js";
import { cardHead, categoryTag, dueText, empty, loadInto } from "./parts.js";
import { currentStudent, currentUser } from "./shell.js";

// The student's home page (FR-STU-001): six cards, each a summary that links to its full page.
// Everything comes from one request, GET /students/me/dashboard.

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
    if (student) {
      document.getElementById("programme").textContent =
        `${student.branch} · Semester ${student.currentSemester} · Section ${student.section}`;
      fill(document.getElementById("hero-chips"), h("li.chip", icon("id"), "Roll no. ", h("strong.mono", student.rollNo)));
    }
  })
  .catch(() => {});

function attendanceCard({ overallPercent, requiredPercent, subjectsBelowRequired }) {
  const body = [];
  if (overallPercent === null) {
    body.push(empty("calendar", "No classes recorded yet", "Your attendance appears once classes are held."));
  } else {
    const figure = h("p.figure");
    countUp(figure, overallPercent, { decimals: 2, suffix: "%" });
    body.push(
      h(
        "div.line",
        h("div.stack.stack--tight", h("p.eyebrow", "Overall"), figure, h("p.row__meta", `Required: ${requiredPercent}%`)),
        ring(overallPercent, { size: 104, stroke: 10, label: icon("cap"), kind: overallPercent < requiredPercent ? "danger" : "" }),
      ),
      subjectsBelowRequired
        ? h("p.inset.inset--warning.inset--note", icon("alert"), h("span", h("strong", subjectsBelowRequired), ` subject${subjectsBelowRequired > 1 ? "s" : ""} below the required ${requiredPercent}%`))
        : h("p.inset.inset--note", icon("check-circle"), `Every subject is at or above ${requiredPercent}%.`),
    );
  }
  return h("section.card.card--lift.reveal", cardHead("attendance", "Attendance", "/student/attendance.html"), body);
}

function resultsCard({ latestSgpa, cgpa }) {
  const tile = (label, value) => {
    const figure = h("p.figure.figure--small");
    countUp(figure, value, { decimals: 2 });
    return h("div.inset", h("p.eyebrow", label), figure);
  };
  return h(
    "section.card.card--lift.reveal",
    cardHead("results", "Results", "/student/results.html"),
    latestSgpa === null
      ? empty("results", "Results not published yet", "Your SGPA and CGPA appear here once results are published.")
      : [h("div.tiles", tile("Latest SGPA", latestSgpa), tile("CGPA", cgpa)), h("p.note", icon("info"), "CGPA is weighted by the credits registered in each semester.")],
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

function classesCard(todayClasses) {
  const now = new Date().toTimeString().slice(0, 5);
  const next = todayClasses.find((entry) => entry.startTime > now);
  const rows = todayClasses.map((entry) =>
    h(
      "li.row",
      h("div.row__top", h("span.row__title.clip", entry.subjectName), h("span.mono.num", `${entry.startTime} – ${entry.endTime}`)),
      h("div.row__top", h("span.row__meta.clip", [entry.room, entry.facultyName].filter(Boolean).join(" · ")), classState(entry, now, entry === next)),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("clock", "Today's classes", "/student/timetable.html"),
    rows.length ? h("ul.rows", rows) : empty("coffee", "No classes today", "Enjoy the break."),
  );
}

function deadlinesCard(upcomingDeadlines) {
  const rows = upcomingDeadlines.slice(0, 4).map((notice) =>
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
    rows.length ? h("ul.rows", rows) : empty("check-circle", "Nothing due", "No deadlines in the next 14 days."),
  );
}

function noticesCard(recentNotices) {
  const rows = recentNotices.slice(0, 4).map((notice) =>
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

function recommendationsCard(recommendations) {
  const rows = recommendations.slice(0, 3).map((item) =>
    h(
      "a.inset.inset--link",
      { href: "/student/progress.html" },
      h("div.line", h("span.row__title", `Revise Unit ${item.unitNo}: ${item.unitTitle}`), icon("arrow-right")),
      h("p.row__meta.mono", item.subjectCode),
      h("p.reason", icon("alert"), `Accuracy ${percent(item.accuracyPercent)} in your tests`),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("target", "Revise next", "/student/progress.html"),
    rows.length ? h("div.stack", rows) : empty("target", "No suggestions yet", "Take a quick test to see what to revise."),
  );
}

const content = document.getElementById("content");
loadInto(content, () => api.get("/students/me/dashboard"), (data) => {
  fill(
    content,
    stagger([
      attendanceCard(data.attendance),
      resultsCard(data.results),
      classesCard(data.todayClasses),
      deadlinesCard(data.upcomingDeadlines),
      noticesCard(data.recentNotices),
      recommendationsCard(data.recommendations),
    ]),
  );
  reveal();
});
enableTilt();
