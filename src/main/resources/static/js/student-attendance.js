import { api } from "./api.js";
import { fill, formatDate, h, icon, tag } from "./dom.js";
import { countUp, enableTilt, reveal, ring } from "./motion.js";
import { empty, loadInto } from "./parts.js";
import "./shell.js";
import { DETAINED_BELOW, attendancePolicy, plan, termPlan } from "./attendance-plan.js";

// Attendance (FR-ATT-001): the student's total attendance and a planner that says how many classes
// to attend to reach a target, or how many can still be missed. Everything is read-only and
// belongs to the logged-in student.

const TARGETS = [65, 70, 75, 80, 85];
// The two lines of the attendance policy: 65% (below it: detained) and the required 75% (below it: condonation fee).
/** The offered targets, with the college's required one always among them. */
const targetsWith = (required) => [...new Set([...TARGETS, Number(required)])].sort((x, y) => x - y);
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

const pct = (attended, held) => (held === 0 ? 0 : (attended * 100) / held);

function hero(summary, { held, attended, report }) {
  const required = summary.requiredPercent;
  // The server's overall figure is the one source, so the dashboard and this page always agree.
  const current = summary.overallPercent === null ? pct(attended, held) : Number(summary.overallPercent);
  const figure = h("p.hero__figure");
  countUp(figure, current, { decimals: 2, suffix: "%" });
  const policy = attendancePolicy(current, required);
  const gap = Math.abs(current - policy.needed).toFixed(2);
  const status = {
    safe: { cls: ".chip--good", icon: "check-circle", text: `No issue · ${gap}% above the required ${required}%` },
    condonation: { cls: ".chip--bad", icon: "alert", text: `Condonation fee · between ${DETAINED_BELOW}% and ${required}%; ${gap}% to reach ${required}%` },
    detained: { cls: ".chip--bad", icon: "alert", text: `Detained · ${gap}% under ${DETAINED_BELOW}%` },
  }[policy.state];
  return h(
    "section.hero.hero--split.reveal",
    { "data-tilt": true, "aria-label": "Total attendance" },
    h(
      "div.hero__main",
      h("p.hero__eyebrow", "Total attendance"),
      figure,
      h("p.chip" + status.cls, icon(status.icon), status.text),
      report ? h("p.hero__sub", `College report, ${formatDate(report.fromDate)} to ${formatDate(report.toDate)}`) : null,
      h("ul.chips", h("li.chip", icon("target"), "Required ", h("strong", required + "%")), h("li.chip", icon("calendar"), "Classes held ", h("strong", held)), h("li.chip", icon("check"), "Attended ", h("strong", attended))),
    ),
    h("div.hero__aside", ring(current, { size: 176, stroke: 14, label: [h("span.eyebrow.eyebrow--inverse", "Required"), h("strong.ring__big", required + "%")] })),
  );
}

function plannerCard({ held, attended }, required) {
  const rows = targetsWith(required).map((target) => {
    const result = plan(attended, held, target);
    const text = result.reached
      ? result.classes === 0
        ? "Reached. Attend the next class to stay above it."
        : `Reached. You can miss up to ${result.classes} ${result.classes === 1 ? "class" : "classes"} in a row.`
      : result.classes === null
        ? "Cannot be reached once a class has been missed."
        : `Attend the next ${result.classes} ${result.classes === 1 ? "class" : "classes"} in a row.`;
    return h(
      "li.row.planner__row",
      h("span.planner__target.num", target + "%"),
      h("span.planner__text", text),
      result.reached ? tag("Reached", "success", "check-circle") : tag(result.classes === null ? "Not possible" : result.classes + " to go", target === required ? "danger" : "warning", "target"),
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

/** The whole term: out of all its classes, how many to attend and how many can be missed. */
function termCard({ held, attended }, required) {
  const total = h("input.input.num", { type: "number", id: "term-total", min: Math.max(1, held), max: 1000, value: Math.max(held, 48), inputmode: "numeric" });
  const target = h("select.input", { id: "term-target" }, targetsWith(required).map((value) => h("option", { value: String(value), selected: value === Number(required) }, value + "%")));
  const result = h("div.stack.stack--tight", { "aria-live": "polite" });
  const update = () => {
    const typed = Number.parseInt(total.value, 10) || 0;
    const classes = Math.max(held, Math.min(1000, typed));
    const p = termPlan(classes, Number(target.value), attended, held);
    const lines = [
      typed < held ? h("p.row__meta", `${held} classes have already been held, so the term has at least ${held}.`) : null,
      h("p", "Attend at least ", h("strong.num", `${p.mustAttend} of ${p.total}`), ` classes; you can miss up to `, h("strong.num", String(p.canMiss)), "."),
    ];
    if (held > 0) {
      lines.push(
        p.remaining === 0
          ? h("p.row__meta", `So far ${attended} of ${held}. No classes are left in this term.`)
          : p.possible
          ? h("p.row__meta", `So far ${attended} of ${held}. Of the ${p.remaining} classes left, attend at least ${p.stillNeeded}; you can still miss ${p.canStillMiss}.`)
          : h("p.row__meta", `So far ${attended} of ${held}. Even attending all ${p.remaining} classes left gives ${p.bestPercent.toFixed(2)}%, so ${target.value}% can no longer be reached this term.`),
      );
    }
    fill(result, lines);
  };
  total.addEventListener("input", update);
  target.addEventListener("change", update);
  update();
  return h(
    "section.card.reveal",
    { "aria-label": "Whole term" },
    h("div.stack.stack--tight", h("h2", "Whole term"), h("p.row__meta", "Enter the total classes of the term to see the least you must attend.")),
    h("div.planner__inputs", h("label.field", { for: "term-total" }, h("span.field__label", "Total classes in the term"), total), h("label.field", { for: "term-target" }, h("span.field__label", "Target"), target)),
    result,
  );
}

loadInto(content, () => api.get("/attendance/me"), (summary) => {
  const count = totals(summary);
  if (count.held === 0) {
    fill(content, h("div.card", empty("attendance", "No attendance yet", `Your attendance appears here once the college records it. The college requires ${summary.requiredPercent}%.`)));
    return;
  }
  fill(content, hero(summary, count), h("div.grid.grid--2", plannerCard(count, summary.requiredPercent), whatIfCard(count), termCard(count, summary.requiredPercent)));
  reveal();
  enableTilt();
});
