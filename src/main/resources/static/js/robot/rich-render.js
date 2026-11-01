import { h } from "../dom.js";
import { highlight, languageOf } from "./code-highlight.js";
import { drawChart, drawDiagram, drawMindmap, drawSteps } from "./diagram-draw.js";
import { isSummaryHeading, parseInline, parseRich } from "./rich-parse.js";

// Draws an assistant answer (see rich-parse.js) as DOM, for a live reply and for a saved one alike.
// Text is only ever set as text, so nothing in an answer can become markup or script. A diagram
// that cannot be drawn is shown as a short notice with its text folded away, so an answer is never
// lost to a drawing problem and raw diagram syntax is never the first thing a student sees.

const LANGUAGE_NAMES = { javascript: "JavaScript", typescript: "TypeScript", python: "Python", java: "Java", cpp: "C++", c: "C", csharp: "C#", go: "Go", rust: "Rust", sql: "SQL", bash: "Shell", html: "HTML", css: "CSS", json: "JSON" };

function inline(pieces) {
  return pieces.map((piece) => {
    switch (piece.type) {
      case "bold":
        return h("strong.rb-md__key", inline(piece.children));
      case "italic":
        return h("em", inline(piece.children));
      case "strike":
        return h("s", inline(piece.children));
      case "code":
        return h("code.rb-md__code", piece.value);
      case "link":
        return h("a.rb-md__link", { href: piece.href, target: "_blank", rel: "noopener noreferrer" }, inline(piece.children));
      default:
        return piece.value;
    }
  });
}

function text(value) {
  return inline(parseInline(value));
}

function list(block) {
  const tag = block.ordered ? "ol" : "ul";
  // A numbered list that does not start at 1 keeps its numbers: the circles count from its start.
  const attributes = block.ordered && block.start > 1 ? { start: String(block.start), style: { "counter-reset": `step ${block.start - 1}` } } : {};
  return h(
    tag + ".rb-md__list" + (block.ordered ? ".rb-md__list--steps" : ""),
    attributes,
    block.items.map((item) =>
      h(
        "li" + (item.checked !== undefined ? ".rb-md__task" : ""),
        item.checked !== undefined ? h("span.rb-md__box" + (item.checked ? ".is-done" : ""), { "aria-hidden": "true" }, item.checked ? "✓" : "") : null,
        h("span.rb-md__item", text(item.text)),
        item.sub ? list(item.sub) : null,
      ),
    ),
  );
}

function codeBlock(block) {
  const known = languageOf(block.lang);
  const name = block.lang ? (LANGUAGE_NAMES[known] ?? block.lang) : "";
  return h(
    "div.rb-codeblock",
    h("div.rb-codeblock__bar", h("span.rb-codeblock__lang", name || "Code")),
    h("pre.rb-code", h("code", highlight(block.code, block.lang).map((part) => (part.type === "plain" ? part.text : h("span.rb-tok.rb-tok--" + part.type, part.text))))),
  );
}

/** What a diagram block that could not be drawn shows: a notice, and its text folded away. */
function undrawn(block, why) {
  return h("details.rb-diagram-fallback", h("summary", why), h("pre.rb-code", h("code", block.source)));
}

function visual(block) {
  if (block.kind === "math") {
    return h("div.rb-math", block.source.split("\n").filter((line) => line.trim()).map((line) => h("p", line.trim())));
  }
  let drawn = null;
  try {
    drawn = { flowchart: drawDiagram, mermaid: drawDiagram, mindmap: (source) => (/^\s*mindmap\b/i.test(source) ? drawDiagram(source) : drawMindmap(source)), chart: drawChart, steps: drawSteps }[block.kind]?.(block.source) ?? null;
  } catch {
    drawn = null;
  }
  if (drawn instanceof Node) return drawn;
  if (drawn?.unsupported) return undrawn(block, `This kind of diagram (${drawn.unsupported}) cannot be drawn here. Show its text`);
  return undrawn(block, "This diagram could not be drawn. Show its text");
}

function render(block) {
  switch (block.type) {
    case "heading":
      return h("h" + block.level + ".rb-md__heading", text(block.text));
    case "list":
      return list(block);
    case "table":
      return h("div.rb-md__table", h("table", h("thead", h("tr", block.header.map((cell) => h("th", text(cell))))), h("tbody", block.rows.map((row) => h("tr", row.map((cell) => h("td", text(cell))))))));
    case "quote":
      return h("blockquote.rb-md__quote", text(block.text));
    case "rule":
      return h("hr.rb-md__rule");
    case "code":
      return codeBlock(block);
    case "visual":
      return visual(block);
    default:
      return h("p", text(block.text));
  }
}

/**
 * The answer as a list of elements for a bubble or a history card. The closing summary (a heading
 * such as "Summary" or "Key takeaways" and what follows it) is set apart in a panel of its own.
 */
export function renderRich(source) {
  const blocks = parseRich(source);
  const elements = [];
  let summary = null;
  let summaryLevel = 0;
  for (const block of blocks) {
    if (block.type === "heading") {
      if (summary && block.level <= summaryLevel) summary = null;
      if (!summary && isSummaryHeading(block.text)) {
        summary = h("section.rb-summary");
        summaryLevel = block.level;
        elements.push(summary);
      }
    }
    (summary ?? { append: (node) => elements.push(node) }).append(render(block));
  }
  return elements;
}
