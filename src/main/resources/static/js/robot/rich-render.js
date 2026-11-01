import { h } from "../dom.js";
import { drawChart, drawFlowchart, drawMindmap, drawSteps } from "./diagram-draw.js";
import { parseInline, parseRich } from "./rich-parse.js";

// Draws an assistant answer (see rich-parse.js) as DOM. Text is only ever set as text, so nothing in
// an answer can become markup or script. A diagram that cannot be read is shown as code, so an
// answer is never lost to a drawing problem.

function inline(text) {
  return parseInline(text).map((piece) => {
    if (piece.type === "bold") return h("strong", piece.value);
    if (piece.type === "italic") return h("em", piece.value);
    if (piece.type === "code") return h("code.rb-md__code", piece.value);
    return piece.value;
  });
}

function visual(block) {
  let drawn = null;
  try {
    drawn = { flowchart: drawFlowchart, mindmap: drawMindmap, chart: drawChart, steps: drawSteps }[block.kind]?.(block.source) ?? null;
  } catch {
    drawn = null;
  }
  if (block.kind === "math") {
    return h("div.rb-math", block.source.split("\n").filter((line) => line.trim()).map((line) => h("p", line.trim())));
  }
  return drawn ?? h("pre.rb-code", h("code", block.source));
}

function render(block) {
  switch (block.type) {
    case "heading":
      return h("h" + block.level + ".rb-md__heading", inline(block.text));
    case "list":
      return h((block.ordered ? "ol" : "ul") + ".rb-md__list", block.items.map((item) => h("li", inline(item))));
    case "table":
      return h("div.rb-md__table", h("table", h("thead", h("tr", block.header.map((cell) => h("th", inline(cell))))), h("tbody", block.rows.map((row) => h("tr", row.map((cell) => h("td", inline(cell))))))));
    case "quote":
      return h("blockquote.rb-md__quote", inline(block.text));
    case "rule":
      return h("hr.rb-md__rule");
    case "code":
      return h("pre.rb-code", block.lang ? h("span.rb-code__lang", block.lang) : null, h("code", block.code));
    case "visual":
      return visual(block);
    default:
      return h("p", inline(block.text));
  }
}

/** The answer as a list of elements for a bubble. */
export function renderRich(text) {
  return parseRich(text).map(render);
}
