// A smoke test: every module of the Quick Assistant must at least load. The page imports the robot
// with its errors swallowed (the robot is an extra), so a syntax slip would silently remove it;
// this makes such a slip fail the build instead.

import assert from "node:assert/strict";
import { test } from "node:test";

globalThis.document ??= { createElement: () => ({}), createElementNS: () => ({}), addEventListener() {}, querySelector: () => null };
globalThis.window ??= { addEventListener() {}, matchMedia: () => ({ matches: false, addEventListener() {} }), location: { pathname: "/" } };

const root = "../../main/resources/static/js/robot/";

for (const name of ["area-select.js", "attachment-rules.js", "attachment-tray.js", "page-snapshot.js", "chat-groups.js", "code-highlight.js", "rich-parse.js", "diagram-model.js", "diagram-draw.js", "rich-render.js", "quick-assistant.js", "robot.js"]) {
  test(`${name} loads`, async () => {
    const loaded = await import(root + name);
    assert.ok(Object.keys(loaded).length >= 0);
  });
}
