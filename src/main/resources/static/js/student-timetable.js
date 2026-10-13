import { fill, formatToday, h, icon, stagger, tag } from "./dom.js";
import { reveal, swap } from "./motion.js";
import { empty } from "./parts.js";
import { subjects, timetable } from "./sample-data.js";
import "./shell.js";

// Timetable (FR-TT-001): the week as a grid on wide screens, one day at a time on phones.
// Each subject keeps one colour, so a subject is easy to follow across the week.

const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const COLOURS = ["primary", "purple", "teal", "orange", "success"];
const colourOf = Object.fromEntries(subjects.map((subject, index) => [subject.code, COLOURS[index % COLOURS.length]]));

const today = new Date().getDay() || 7;
const now = new Date().toTimeString().slice(0, 5);
const starts = [...new Set(timetable.map((entry) => entry.startTime))].sort();
// An afternoon lab covers two hours, so the grid also needs the hour it runs into.
const slots = [...new Set([...starts, "15:00"])].sort();
// The first afternoon slot. A "Lunch break" row is drawn above it.
const afternoon = slots.findIndex((slot) => slot >= "13:00");
const rowOf = (time) => slots.indexOf(time) + 2 + (afternoon > 0 && slots.indexOf(time) >= afternoon ? 1 : 0);
let chosenDay = today <= 6 ? today : 1;

const isNow = (entry) => entry.dayOfWeek === today && entry.startTime <= now && now < entry.endTime;
const dayClasses = (day) =>
  timetable.filter((entry) => entry.dayOfWeek === day).sort((a, b) => a.startTime.localeCompare(b.startTime));

function headerChips() {
  document.getElementById("today-chip").textContent = "Today: " + formatToday();
  const current = timetable.find(isNow);
  const next = dayClasses(today).find((entry) => entry.startTime > now);
  const chip = document.getElementById("now-chip");
  if (current) {
    fill(chip, icon("play"), `Now: ${current.subjectName} until ${current.endTime}`);
    chip.className = "tag tag--solid";
  } else if (next) {
    fill(chip, icon("clock"), `Next: ${next.subjectName} at ${next.startTime}`);
  }
  chip.hidden = !current && !next;
}

function block(entry) {
  const span = slots.filter((slot) => slot >= entry.startTime && slot < entry.endTime).length;
  return h(
    `article.tt-block.tt-block--${colourOf[entry.subjectCode]}` + (isNow(entry) ? ".tt-block--now" : ""),
    { style: { "--col": entry.dayOfWeek + 1, "--row": rowOf(entry.startTime), "--span": span } },
    h("div.line", h("span.mono.tt-block__code", entry.subjectCode), isNow(entry) ? tag("Now", "solid") : null),
    h("h3.tt-block__name", entry.subjectName),
    h("p.tt-block__meta.num", `${entry.startTime} – ${entry.endTime}`),
    h("p.tt-block__meta", `${entry.room} · ${entry.facultyName}`),
  );
}

function week() {
  const head = DAYS.map((name, index) =>
    h(
      "div.tt-day" + (index + 1 === today ? ".tt-day--today" : ""),
      { style: { "--col": index + 2 } },
      h("strong", name),
      index + 1 === today ? h("span.eyebrow", "Today") : null,
    ),
  );
  const times = slots.map((slot, index) =>
    h("div.tt-time.mono.num", { style: { "--row": rowOf(slot) } }, slot),
  );
  const lunch =
    afternoon > 0
      ? h("div.tt-break", { style: { "--row": afternoon + 2 } }, icon("coffee"), "Lunch break")
      : null;
  const todayColumn = today <= 6 ? h("div.tt-today", { style: { "--col": today + 1 }, "aria-hidden": "true" }) : null;
  return h(
    "section.card.tt-week.reveal",
    { "aria-label": "Weekly timetable" },
    h("div.tt-grid", todayColumn, head, times, lunch, timetable.map(block)),
  );
}

function dayCard(entry) {
  return h(
    `article.card.tt-class.tt-block--${colourOf[entry.subjectCode]}.reveal` + (isNow(entry) ? ".tt-block--now" : ""),
    h("div.line", h("span.mono.num.tt-class__time", `${entry.startTime} – ${entry.endTime}`), isNow(entry) ? tag("Now", "solid") : h("span.tag.mono", entry.subjectCode)),
    h("h3.subject__name", entry.subjectName),
    h("p.row__meta.line.line--start", icon("pin"), `${entry.room} · ${entry.facultyName}`),
  );
}

function renderDay() {
  const list = dayClasses(chosenDay);
  fill(
    document.getElementById("day-list"),
    list.length ? stagger(list.map(dayCard)) : h("div.card", empty("coffee", "No classes on this day", "")),
  );
}

function dayView() {
  const pills = h(
    "div.pills.tt-days",
    { role: "group", "aria-label": "Day" },
    DAYS.map((name, index) => {
      const pill = h(
        "button.pill",
        { type: "button", "aria-pressed": String(index + 1 === chosenDay) },
        name.slice(0, 3),
        index + 1 === today ? h("span.pill__count", "today") : null,
      );
      pill.addEventListener("click", () => {
        chosenDay = index + 1;
        for (const other of pill.parentElement.children) {
          other.setAttribute("aria-pressed", String(other === pill));
        }
        swap(document.getElementById("day-list"), renderDay);
      });
      return pill;
    }),
  );
  return h("section.tt-dayview.stack.stack--loose", { "aria-label": "Classes by day" }, pills, h("div.stack.stack--loose", { id: "day-list" }));
}

function legend() {
  return h(
    "section.card.reveal",
    h("div.card__head", h("h2.card__title", icon("resources"), "Subjects this semester")),
    h(
      "ul.rows.tt-legend",
      subjects.map((subject) =>
        h(
          "li.row.tt-legend__row",
          h(`span.tt-dot.tt-block--${colourOf[subject.code]}`, { "aria-hidden": "true" }),
          h("div.stack.stack--tight", h("span.row__title", subject.name), h("span.row__meta", subject.facultyName ?? subject.faculty)),
          h("span.tag.mono", subject.code),
        ),
      ),
    ),
  );
}

headerChips();
fill(
  document.getElementById("content"),
  today === 7 ? h("p.strip.reveal", icon("coffee"), "No classes today. Here is the week ahead.") : null,
  week(),
  dayView(),
  legend(),
);
renderDay();
reveal();
