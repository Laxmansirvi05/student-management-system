import { api } from "./api.js";
import { daysUntil, fill, formatDate, formatToday, h, icon, stagger, tag } from "./dom.js";
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

function attendanceCard({ overallPercent, requiredPercent }) {
  const body = [];
  if (overallPercent === null) {
    body.push(empty("calendar", "No classes recorded yet", "Your attendance appears once classes are held."));
  } else {
    const figure = h("p.figure");
    countUp(figure, overallPercent, { decimals: 2, suffix: "%" });
    const below = Number(overallPercent) < Number(requiredPercent);
    body.push(
      h(
        "div.line",
        h("div.stack.stack--tight", h("p.eyebrow", "Total"), figure, h("p.row__meta", `Required: ${requiredPercent}%`)),
        ring(overallPercent, { size: 104, stroke: 10, label: icon("cap"), kind: below ? "danger" : "" }),
      ),
      below
        ? h("p.inset.inset--warning.inset--note", icon("alert"), `Below the required ${requiredPercent}%. The attendance page shows how many classes you need.`)
        : h("p.inset.inset--note", icon("check-circle"), `At or above the required ${requiredPercent}%.`),
    );
  }
  return h("section.card.card--lift.reveal", cardHead("attendance", "Attendance", "/student/attendance.html"), body);
}

/** The CGPA on top, then the SGPA of every published semester, oldest first. */
function resultsCard({ cgpa, semesters }) {
  if (!semesters.length) {
    return h(
      "section.card.card--lift.reveal",
      cardHead("results", "Results", "/student/results.html"),
      empty("results", "Results not published yet", "Your SGPA and CGPA appear here once results are published."),
    );
  }
  const figure = h("p.figure");
  countUp(figure, cgpa, { decimals: 2 });
  const rows = semesters.map((entry) =>
    h(
      "li.row.results-row",
      h("span.row__title", `Semester ${entry.semester}`),
      entry.resultStatus === "FAIL" ? tag("Backlog", "danger") : null,
      h("span.results-row__sgpa.num", "SGPA ", h("strong", Number(entry.sgpa).toFixed(2))),
    ),
  );
  return h(
    "section.card.card--lift.reveal",
    cardHead("results", "Results", "/student/results.html"),
    h("div.inset.results-cgpa", h("p.eyebrow", "CGPA"), figure, h("p.row__meta", `Over ${semesters.length} semester${semesters.length === 1 ? "" : "s"}, weighted by credits`)),
    h("ul.rows", rows),
  );
}

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
  const rows = upcomingDeadlines.slice(0, 6).map((notice) =>
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
    rows.length ? h("ul.rows", rows) : empty("check-circle", "Nothing due", "No deadlines in the next 30 days."),
  );
}

/** "Today", "Tomorrow" or "In 5 days". */
function inDays(isoDate) {
  const days = daysUntil(isoDate);
  return days === 0 ? "Today" : days === 1 ? "Tomorrow" : `In ${days} days`;
}

/** The coming exams, the college's time table and the student's own (FR-PLN-002), soonest first. */
function examsCard(upcomingExams) {
  if (!upcomingExams.length) {
    return h(
      "section.card.card--lift.reveal",
      cardHead("timer", "Upcoming exams", "/student/planner.html", "Planner"),
      empty("timer", "No exams coming up", "Exam dates your college publishes appear here. You can add your own in the planner."),
    );
  }
  const [next, ...later] = upcomingExams;
  const soon = daysUntil(next.examDate) <= 3;
  return h(
    "section.card.card--lift.reveal",
    cardHead("timer", "Upcoming exams", "/student/planner.html", "Planner"),
    h(
      "div.planner-next",
      h("p.eyebrow", "Next exam"),
      h("p.planner-next__days.num" + (soon ? ".row__meta--warning" : ""), inDays(next.examDate)),
      h("p.row__title", next.name),
      h("p.row__meta", formatDate(next.examDate) + (next.source === "COLLEGE" ? " · from your college" : "")),
    ),
    later.length
      ? h(
          "ul.rows",
          later.map((exam) =>
            h("li.row", h("div.row__top", h("span.row__meta.num", formatDate(exam.examDate)), h("span.row__meta", inDays(exam.examDate))), h("span.row__title", exam.name)),
          ),
        )
      : null,
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

const content = document.getElementById("content");
loadInto(content, () => api.get("/students/me/dashboard"), (data) => {
  fill(
    content,
    stagger([
      attendanceCard(data.attendance),
      resultsCard(data.results),
      classesCard(data.todayClasses),
      deadlinesCard(data.upcomingDeadlines),
      examsCard(data.upcomingExams),
      noticesCard(data.recentNotices),
    ]),
  );
  reveal();
});
enableTilt();
