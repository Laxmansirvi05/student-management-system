// Tests for the number animation: a missing number must never crash a page (the dashboard once
// failed completely for a student whose CGPA was not available).

import assert from "node:assert/strict";
import { test } from "node:test";

globalThis.window ??= { matchMedia: () => ({ matches: true, addEventListener() {} }) };
globalThis.matchMedia ??= () => ({ matches: true, addEventListener() {} });
globalThis.document ??= { createElement: () => ({}), addEventListener() {}, querySelector: () => null };

const { countUp } = await import("../../main/resources/static/js/motion.js");

test("a missing, null or non-numeric target shows a dash and does not throw", () => {
  for (const target of [null, undefined, NaN, Infinity, "x"]) {
    const element = { textContent: "" };
    assert.doesNotThrow(() => countUp(element, target, { decimals: 2 }));
    assert.equal(element.textContent, "–");
  }
});

test("a number is shown with its decimals and suffix when motion is reduced", () => {
  const element = { textContent: "" };
  countUp(element, 71.0912, { decimals: 2, suffix: "%" });
  assert.equal(element.textContent, "71.09%");
});
