// Tests for the attendance planner's arithmetic (FR-ATT-003).

import assert from "node:assert/strict";
import { test } from "node:test";
import { plan, termPlan } from "../../main/resources/static/js/attendance-plan.js";

const pct = (a, h) => (a * 100) / h;

test("a whole term of 48 classes at 65%: attend 32, miss 16", () => {
  const p = termPlan(48, 65);
  assert.equal(p.mustAttend, 32);
  assert.equal(p.canMiss, 16);
  assert.ok(pct(32, 48) >= 65); // 66.67%
  assert.ok(pct(31, 48) < 65); // 64.58% fails
});

test("the minimum is always the smallest count that reaches the target", () => {
  for (const target of [65, 70, 75, 80, 85, 75.5]) {
    for (let total = 1; total <= 400; total++) {
      const { mustAttend, canMiss } = termPlan(total, target);
      assert.ok(mustAttend * 10000 >= Math.round(target * 100) * total, `${total} at ${target}`);
      assert.ok((mustAttend - 1) * 10000 < Math.round(target * 100) * total, `${total} at ${target} not minimal`);
      assert.equal(mustAttend + canMiss, total);
    }
  }
});

test("exact multiples are not pushed up by floating-point error", () => {
  assert.equal(termPlan(100, 65).mustAttend, 65); // 0.65 * 100 is 65.00000000000001 in floating point
  assert.equal(termPlan(20, 65).mustAttend, 13);
  assert.equal(termPlan(40, 75).mustAttend, 30);
});

test("zero classes needs nothing, and a negative count is zero", () => {
  assert.deepEqual([termPlan(0, 65).mustAttend, termPlan(0, 65).canMiss], [0, 0]);
  assert.equal(termPlan(-5, 65).total, 0);
});

test("with classes already held, it says what is still needed of the rest", () => {
  const p = termPlan(48, 65, 20, 30); // 20 of 30 so far, 18 left
  assert.equal(p.remaining, 18);
  assert.equal(p.stillNeeded, 12);
  assert.equal(p.canStillMiss, 6);
  assert.equal(p.possible, true);
});

test("an impossible target is reported, with the best that can still be reached", () => {
  const p = termPlan(48, 65, 5, 30); // 5 of 30, 18 left: at best 23 of 48
  assert.equal(p.possible, false);
  assert.equal(p.bestPercent.toFixed(2), "47.92");
});

test("already above the target with no classes left: nothing more needed", () => {
  const p = termPlan(48, 65, 40, 48);
  assert.equal(p.stillNeeded, 0);
  assert.equal(p.possible, true);
});

test("next classes in a row: the counts are the exact boundary", () => {
  for (let held = 1; held <= 120; held++) {
    for (let attended = 0; attended <= held; attended++) {
      for (const target of [65, 75]) {
        const r = plan(attended, held, target);
        if (r.reached) {
          assert.ok(pct(attended, held + r.classes) >= target);
          assert.ok(pct(attended, held + r.classes + 1) < target);
        } else {
          assert.ok(pct(attended + r.classes, held + r.classes) >= target);
          if (r.classes > 0) assert.ok(pct(attended + r.classes - 1, held + r.classes - 1) < target);
        }
      }
    }
  }
});

test("exactly at the target counts as reached", () => {
  assert.deepEqual(plan(65, 100, 65), { reached: true, classes: 0 });
  assert.equal(plan(32, 48, 65).classes, 1); // 32 of 49 is 65.3%, 32 of 50 is 64%
});

test("a 100% target cannot be reached again after a miss", () => {
  assert.deepEqual(plan(9, 10, 100), { reached: false, classes: null });
});
