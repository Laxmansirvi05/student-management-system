// Tests for how a saved Quick Assistant chat is grouped by question and answer mode.

import assert from "node:assert/strict";
import { test } from "node:test";
import { groupMessages, lastStyle, shownStyle } from "../../main/resources/static/js/robot/chat-groups.js";

const q = (id, content = "q" + id) => ({ id, role: "user", content });
const a = (id, replyTo, style, content = "a" + id) => ({ id, role: "ai", content, replyTo, style });

test("each question gets its answers by mode, in any order of saving", () => {
  const groups = groupMessages([q(1), a(2, 1, "quick"), q(3), a(4, 3, "explain"), a(5, 1, "explain"), a(6, 3, "quick")]);
  assert.equal(groups.length, 2);
  assert.deepEqual(Object.keys(groups[0].answers).sort(), ["explain", "quick"]);
  assert.equal(groups[0].answers.quick.id, 2);
  assert.equal(groups[0].answers.explain.id, 5);
  assert.equal(groups[1].answers.explain.id, 4);
  assert.equal(groups[1].answers.quick.id, 6);
  assert.equal(groups[0].last, "explain", "written last");
  assert.equal(groups[1].last, "quick");
});

test("the saved text is kept exactly, not rewritten", () => {
  const text = "## Title\n\n```flowchart\nA --> B\n```\n| a | b |\n| --- | --- |";
  const [group] = groupMessages([q(1), a(2, 1, "explain", text)]);
  assert.equal(group.answers.explain.content, text);
});

test("a chat saved by an older version pairs an answer with the question before it", () => {
  const groups = groupMessages([q(1), { id: 2, role: "ai", content: "old", replyTo: null, style: null }, q(3), { id: 4, role: "ai", content: "old2" }]);
  assert.equal(groups.length, 2);
  assert.equal(groups[0].answers.quick.content, "old");
  assert.equal(groups[1].answers.quick.content, "old2");
});

test("only the first answer of a mode is kept, so a duplicate never replaces it", () => {
  const [group] = groupMessages([q(1), a(2, 1, "quick", "first"), a(3, 1, "quick", "second")]);
  assert.equal(group.answers.quick.content, "first");
});

test("a question still waiting for its answer has no answers", () => {
  const [group] = groupMessages([{ role: "user", content: "pending" }]);
  assert.deepEqual(group.answers, {});
  assert.equal(shownStyle(group, "explain"), "quick");
});

test("the mode shown is the chosen one when it has an answer, else the last written", () => {
  const [group] = groupMessages([q(1), a(2, 1, "quick"), a(3, 1, "explain")]);
  assert.equal(shownStyle(group, "quick"), "quick");
  assert.equal(shownStyle(group, undefined), "explain");
  const [onlyQuick] = groupMessages([q(1), a(2, 1, "quick")]);
  assert.equal(shownStyle(onlyQuick, "explain"), "quick", "a mode with no answer is not shown");
});

test("the mode of the last answer of a chat is found, or null for an empty chat", () => {
  assert.equal(lastStyle([q(1), a(2, 1, "explain"), q(3), a(4, 3, "quick")]), "quick");
  assert.equal(lastStyle([]), null);
  assert.equal(lastStyle([q(1)]), null);
});
