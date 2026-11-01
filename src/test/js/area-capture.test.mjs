// Tests for the maths of the screenshot tool of the Quick Assistant: which rectangle the student
// dragged, and how large the picture of it is. The drawing itself needs a browser and is checked there.

import assert from "node:assert/strict";
import { test } from "node:test";
import { MIN_SIDE, isUsable, normalizeRect } from "../../main/resources/static/js/robot/area-select.js";
import { MAX_SIDE, outputScale, outputSize } from "../../main/resources/static/js/robot/page-snapshot.js";

globalThis.document ??= { createElement: () => ({}), createElementNS: () => ({}), addEventListener() {}, querySelector: () => null };

const BOUNDS = { width: 1000, height: 600 };

test("a drag in any direction gives the same rectangle", () => {
  const expected = { x: 100, y: 50, width: 300, height: 200 };
  assert.deepEqual(normalizeRect({ x: 100, y: 50 }, { x: 400, y: 250 }, BOUNDS), expected);
  assert.deepEqual(normalizeRect({ x: 400, y: 250 }, { x: 100, y: 50 }, BOUNDS), expected);
  assert.deepEqual(normalizeRect({ x: 400, y: 50 }, { x: 100, y: 250 }, BOUNDS), expected);
  assert.deepEqual(normalizeRect({ x: 100, y: 250 }, { x: 400, y: 50 }, BOUNDS), expected);
});

test("a drag past the edge of the page stops at the edge", () => {
  assert.deepEqual(normalizeRect({ x: -50, y: -20 }, { x: 1400, y: 900 }, BOUNDS), { x: 0, y: 0, width: 1000, height: 600 });
});

test("fractional pointer positions give whole pixels that still cover the drag", () => {
  const rect = normalizeRect({ x: 10.4, y: 10.4 }, { x: 50.6, y: 30.6 }, BOUNDS);
  assert.ok(Number.isInteger(rect.x) && Number.isInteger(rect.y) && Number.isInteger(rect.width) && Number.isInteger(rect.height));
  assert.deepEqual(rect, { x: 10, y: 10, width: 41, height: 21 });
});

test("a click or a sliver is not a selection", () => {
  assert.equal(isUsable(normalizeRect({ x: 5, y: 5 }, { x: 5, y: 5 }, BOUNDS)), false);
  assert.equal(isUsable({ x: 0, y: 0, width: MIN_SIDE - 1, height: 200 }), false);
  assert.equal(isUsable({ x: 0, y: 0, width: 200, height: MIN_SIDE - 1 }), false);
  assert.equal(isUsable({ x: 0, y: 0, width: MIN_SIDE, height: MIN_SIDE }), true);
});

test("the picture uses the screen's pixel density for a small area", () => {
  assert.equal(outputScale({ width: 400, height: 300 }, 2), 2);
  assert.deepEqual(outputSize({ width: 400, height: 300 }, 2), { width: 800, height: 600 });
});

test("a browser zoomed out never makes the picture smaller than the page's own pixels", () => {
  assert.equal(outputScale({ width: 400, height: 300 }, 0.5), 1);
  assert.equal(outputScale({ width: 400, height: 300 }, undefined), 1);
});

test("a large area is scaled down so its long side stays within the limit", () => {
  const scale = outputScale({ width: 1024, height: 768 }, 2);
  const size = outputSize({ width: 1024, height: 768 }, scale);
  assert.equal(size.width, MAX_SIDE);
  assert.ok(size.height <= MAX_SIDE);
  const tall = outputSize({ width: 300, height: 4000 }, outputScale({ width: 300, height: 4000 }, 2));
  assert.equal(tall.height, MAX_SIDE);
});

test("a picture is never empty", () => {
  assert.deepEqual(outputSize({ width: 1, height: 1 }, 0.1), { width: 1, height: 1 });
});
