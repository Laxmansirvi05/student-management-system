import { api } from "./api.js";
import { fill, formatDate, h, icon, tag } from "./dom.js";
import { countUp, enableTilt, reveal, ring } from "./motion.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";

// Attendance (FR-ATT-001): the student's total attendance and a planner that says how many classes
// to attend to reach a target, or how many can still be missed. Everything is read-only and
// belongs to the logged-in student.

const TARGETS = [70, 75, 80, 85];
const content = document.getElementById("content");

/** Classes held and attended: the college's report when there are no subject-wise records. */
function totals(summary) {
  const held = summary.subjects.reduce((sum, subject) => sum + subject.classesHeld, 0);
  const attended = summary.subjects.reduce((sum, subject) => sum + subject.classesAttended, 0);
  if (held === 0 && summary.report) {
    return { held: summary.report.classesHeld, attended: summary.report.classesAttended, report: summary.report };
  }
  return { held, attended, report: null };
}

/**
 * How many classes in a row must be attended to reach the target, or (when it is already reached)
 * how many can be missed in a row and still keep it.
 */
export function plan(attended, held, target) {
  if (held > 0 && (attended * 100) / held >= target) {
    return { reached: true, classes: Math.floor((attended * 100 - target * held) / target) };
  }
  return { reached: false, classes: Math.ceil((target * held - attended * 100) / (100 - target)) };
}

const pct = (attended, held) => (held === 0 ? 0 : (attended * 100) / held);

function hero(summary, { held, attended, report }) {
  const required = summary.requiredPercent;
  const current = pct(attended, held);
  const figure = h("p.hero__figure");
  countUp(figure, current, { decimals: 2, suffix: "%" });
  const onTrack = current >= required;
  const margin = Math.abs(current - required).toFixed(2);
  return h(
    "section.hero.hero--split.reveal",
    { "data-tilt": true, "aria-label": "Total attendance" },
    h(
      "div.hero__main",
      h("p.hero__eyebrow", "Total attendance"),
      figure,
      h("p.chip" + (onTrack ? ".chip--good" : ".chip--bad"), icon(onTrack ? "check-circle" : "alert"), onTrack ? `On track · ${margin}% above the required ${required}%` : `Below required · ${margin}% under ${required}%`),
      report ? h("p.hero__sub", `College report, ${formatDate(report.fromDate)} to ${formatDate(report.toDate)}`) : null,
      h("ul.chips", h("li.chip", icon("target"), "Required ", h("strong", required + "%")), h("li.chip", icon("calendar"), "Classes held ", h("strong", held)), h("li.chip", icon("check"), "Attended ", h("strong", attended))),
    ),
    h("div.hero__aside", ring(current, { size: 176, stroke: 14, label: [h("span.eyebrow.eyebrow--inverse", "Required"), h("strong.ring__big", required + "%")] })),
  );
}

function plannerCard({ held, attended }, required) {
  const rows = TARGETS.map((target) => {
    const result = plan(attended, held, target);
    const text = result.reached
      ? result.classes === 0
        ? "Reached. Attend the next class to stay above it."
        : `Reached. You can miss up to ${result.classes} ${result.classes === 1 ? "class" : "classes"} in a row.`
      : `Attend the next ${result.classes} ${result.classes === 1 ? "class" : "classes"} in a row.`;
    return h(
      "li.row.planner__row",
      h("span.planner__target.num", target + "%"),
      h("span.planner__text", text),
      result.reached ? tag("Reached", "success", "check-circle") : tag(result.classes + " to go", target === required ? "danger" : "warning", "target"),
    );
  });
  return h(
    "section.card.reveal",
    { "aria-label": "Attendance planner" },
    h("div.stack.stack--tight", h("h2", "Attendance planner"), h("p.row__meta", `Based on ${attended} of ${held} classes. Counts assume every class from now on is attended, or missed, in a row.`)),
    h("ul.rows", rows),
  );
}

function whatIfCard({ held, attended }) {
  const coming = h("input.input.num", { type: "number", id: "plan-coming", min: 1, max: 500, value: 20, inputmode: "numeric" });
  const going = h("input.input.num", { type: "number", id: "plan-going", min: 0, max: 500, value: 18, inputmode: "numeric" });
  const result = h("p.planner__result", { "aria-live": "polite" });
  const update = () => {
    const next = Math.max(0, Math.min(500, Number.parseInt(coming.value, 10) || 0));
    const attend = Math.max(0, Math.min(next, Number.parseInt(going.value, 10) || 0));
    const after = pct(attended + attend, held + next);
    fill(result, "Your attendance would be ", h("strong.num", after.toFixed(2) + "%"), ` (${attended + attend} of ${held + next} classes).`);
  };
  coming.addEventListener("input", update);
  going.addEventListener("input", update);
  update();
  return h(
    "section.card.reveal",
    { "aria-label": "What if" },
    h("div.stack.stack--tight", h("h2", "What if"), h("p.row__meta", "Try a plan for the coming classes.")),
    h("div.planner__inputs", h("label.field", { for: "plan-coming" }, h("span.field__label", "Classes coming up"), coming), h("label.field", { for: "plan-going" }, h("span.field__label", "Classes I will attend"), going)),
    result,
  );
}

loadInto(content, () => api.get("/attendance/me"), (summary) => {
  const count = totals(summary);
  if (count.held === 0) {
    fill(content, h("div.card", empty("attendance", "No attendance yet", `Your attendance appears here once the college records it. The college requires ${summary.requiredPercent}%.`)));
    return;
  }
  fill(content, hero(summary, count), h("div.grid.grid--2", plannerCard(count, summary.requiredPercent), whatIfCard(count)));
  reveal();
  enableTilt();
});
