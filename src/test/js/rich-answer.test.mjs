// Tests for how the Quick Assistant reads an explanatory answer and its diagrams (Quick Assistant
// V2). The parsers and the layout are plain JavaScript with no DOM, so Node's own runner tests them.

import assert from "node:assert/strict";
import { test } from "node:test";
import { layoutFlowchart, layoutMindmap, parseChart, parseFlowchart, parseMindmap, parseSteps } from "../../main/resources/static/js/robot/diagram-model.js";
import { parseInline, parseRich } from "../../main/resources/static/js/robot/rich-parse.js";

test("an answer becomes headings, lists, a table, code and diagram blocks", () => {
  const blocks = parseRich(
    [
      "## Introduction",
      "A stack is **LIFO**.",
      "",
      "- push",
      "- pop",
      "",
      "1. one",
      "2. two",
      "",
      "| Part | Meaning |",
      "| --- | --- |",
      "| Top | last in |",
      "",
      "```python",
      "print(1)",
      "```",
      "```flowchart",
      "A --> B",
      "```",
      "```math",
      "x² + y² = z²",
      "```",
    ].join("\n"),
  );
  assert.deepEqual(
    blocks.map((block) => block.type + (block.kind ? ":" + block.kind : "")),
    ["heading", "paragraph", "list", "list", "table", "code", "visual:flowchart", "visual:math"],
  );
  assert.equal(blocks[0].level, 3);
  assert.equal(blocks[2].ordered, false);
  assert.equal(blocks[3].ordered, true);
  assert.deepEqual(blocks[4].rows, [["Top", "last in"]]);
  assert.equal(blocks[5].lang, "python");
});

test("html in an answer stays text and is never read as markup", () => {
  const [block] = parseRich("<img src=x onerror=alert(1)> and <script>alert(2)</script>");
  assert.equal(block.type, "paragraph");
  assert.ok(block.text.includes("<script>"));
  assert.deepEqual(parseInline("<b>x</b>"), [{ type: "text", value: "<b>x</b>" }]);
});

test("inline bold, italic and code are told apart", () => {
  assert.deepEqual(parseInline("a **b** c *d* `e`"), [
    { type: "text", value: "a " },
    { type: "bold", value: "b" },
    { type: "text", value: " c " },
    { type: "italic", value: "d" },
    { type: "text", value: " " },
    { type: "code", value: "e" },
  ]);
});

test("a block left open by a cut answer still ends the answer", () => {
  const blocks = parseRich("intro\n\n```flowchart\nA --> B");
  assert.equal(blocks.at(-1).kind, "flowchart");
});

test("a table row without a divider is only text", () => {
  assert.equal(parseRich("| a | b |").at(0).type, "paragraph");
});

// The flowchart a real model wrote for binary search in a live check, with its odd hyphens.
const BINARY_SEARCH = `flowchart TD
    A([Start]) --> B[low = 0, high = n‑1]
    B --> C{low ≤ high?}
    C -->|No| D([Not found])
    C -->|Yes| E[mid = (low+high)//2]
    E --> F{list[mid] = target?}
    F -->|Yes| G([Found, return mid])
    F -->|No| H{list[mid] > target?}
    H -->|Yes| I[high = mid‑1]
    H -->|No| J[low = mid+1]
    I --> C
    J --> C`;

test("a flowchart is read into nodes, shapes and labelled arrows", () => {
  const graph = parseFlowchart(BINARY_SEARCH);
  assert.equal(graph.direction, "TD");
  assert.equal(graph.nodes.length, 10);
  const byId = Object.fromEntries(graph.nodes.map((node) => [node.id, node]));
  assert.equal(byId.A.shape, "terminal");
  assert.equal(byId.C.shape, "decision");
  assert.equal(byId.E.label, "mid = (low+high)//2");
  assert.equal(byId.F.label, "list[mid] = target?");
  assert.equal(graph.edges.length, 11);
  assert.deepEqual(graph.edges.filter((edge) => edge.from === "C").map((edge) => edge.label), ["No", "Yes"]);
});

test("a chain on one line makes one arrow for each link", () => {
  const graph = parseFlowchart("graph LR\nA[One] --> B[Two] -->|go| C(Three)");
  assert.equal(graph.direction, "LR");
  assert.deepEqual(graph.edges.map((edge) => [edge.from, edge.to, edge.label]), [["A", "B", ""], ["B", "C", "go"]]);
  assert.equal(graph.nodes.find((node) => node.id === "C").shape, "rounded");
});

test("a flowchart with no arrow is not a flowchart", () => {
  assert.equal(parseFlowchart("flowchart TD\nA[Only one]"), null);
  assert.equal(parseFlowchart("just some words"), null);
});

test("a flowchart is limited to 30 nodes and long labels are shortened", () => {
  const many = Array.from({ length: 50 }, (_, i) => `N${i} --> N${i + 1}`).join("\n");
  assert.equal(parseFlowchart(many).nodes.length, 30);
  const long = parseFlowchart(`A[${"x".repeat(200)}] --> B`);
  assert.ok(long.nodes[0].label.length <= 60);
});

test("the layout puts a start above what follows it and sends a return arrow round the side", () => {
  const layout = layoutFlowchart(parseFlowchart(BINARY_SEARCH));
  const at = Object.fromEntries(layout.nodes.map((node) => [node.id, node]));
  assert.ok(at.A.y < at.B.y && at.B.y < at.C.y && at.C.y < at.E.y);
  assert.equal(layout.edges.filter((edge) => edge.back).length, 2);
  for (const node of layout.nodes) {
    assert.ok(node.x >= 0 && node.y >= 0 && node.x + node.w <= layout.width && node.y + node.h <= layout.height, `${node.id} lies inside the picture`);
  }
  // No two nodes of one layer overlap.
  for (const a of layout.nodes) {
    for (const b of layout.nodes) {
      if (a.id < b.id && a.y === b.y) assert.ok(a.x + a.w <= b.x || b.x + b.w <= a.x, `${a.id} and ${b.id} overlap`);
    }
  }
});

test("a left-to-right flowchart runs across the picture", () => {
  const layout = layoutFlowchart(parseFlowchart("flowchart LR\nA --> B --> C"));
  const at = Object.fromEntries(layout.nodes.map((node) => [node.id, node]));
  assert.ok(at.A.x < at.B.x && at.B.x < at.C.x);
});

test("a loop in the arrows cannot hang the layout", () => {
  const layout = layoutFlowchart(parseFlowchart("flowchart TD\nA --> B\nB --> A\nB --> B"));
  assert.equal(layout.nodes.length, 2);
});

test("a mind map is an indented tree with the centre first", () => {
  const tree = parseMindmap("OOP\n  Class\n    Object\n  Inheritance\n  Polymorphism");
  assert.equal(tree.label, "OOP");
  assert.deepEqual(tree.children.map((child) => child.label), ["Class", "Inheritance", "Polymorphism"]);
  assert.equal(tree.children[0].children[0].label, "Object");
  const layout = layoutMindmap(tree);
  assert.equal(layout.nodes.length, 5);
  assert.equal(layout.links.length, 4);
  assert.equal(parseMindmap("only one line"), null);
});

test("a chart needs a type, matching labels and numbers", () => {
  const chart = parseChart("type: bar\ntitle: Marks\nlabels: Math, Physics, Chemistry\nvalues: 78, 64.5, 90");
  assert.deepEqual(chart, { type: "bar", title: "Marks", unit: "", labels: ["Math", "Physics", "Chemistry"], values: [78, 64.5, 90] });
  assert.equal(parseChart("type: bar\nlabels: a, b\nvalues: 1"), null, "different counts");
  assert.equal(parseChart("type: bar\nlabels: a, b\nvalues: 1, x"), null, "not a number");
  assert.equal(parseChart("type: radar\nlabels: a, b\nvalues: 1, 2"), null, "unknown type");
  assert.equal(parseChart("type: pie\nlabels: a, b\nvalues: 1, -2"), null, "negative share");
  assert.equal(parseChart(`type: line\nlabels: ${Array.from({ length: 13 }, (_, i) => "l" + i).join(", ")}\nvalues: ${Array(13).fill(1).join(", ")}`), null, "too many points");
});

test("a step shows its cells, its marks and its caption", () => {
  const steps = parseSteps("2 5 8 12 16 23 38 | low=0 mid=3 high=6 | Compare 12 with 23: go right\n2 5 8 | found=1 | Found");
  assert.equal(steps.length, 2);
  assert.deepEqual(steps[0].cells, ["2", "5", "8", "12", "16", "23", "38"]);
  assert.deepEqual(steps[0].marks, [{ name: "low", index: 0 }, { name: "mid", index: 3 }, { name: "high", index: 6 }]);
  assert.equal(steps[0].caption, "Compare 12 with 23: go right");
  assert.deepEqual(steps[1].marks, [{ name: "found", index: 1 }]);
  // A mark that points outside the cells is dropped.
  assert.deepEqual(parseSteps("1 2 | mid=9 | x")[0].marks, []);
  assert.equal(parseSteps("   \n"), null);
});
