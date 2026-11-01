import { h } from "../dom.js";
import { layoutFlowchart, layoutMindmap, layoutSequence, parseChart, parseDiagram, parseFlowchart, parseMindmap, parseSteps } from "./diagram-model.js";

// Draws the diagrams of an explanatory answer as SVG, from the numbers diagram-model.js made. Every
// piece of text is set as text, never as markup, and colours come from classes in robot.css. A form
// that cannot be read returns null, and the caller shows its text as code instead.

const SVG = "http://www.w3.org/2000/svg";

function svg(name, attributes = {}, ...children) {
  const element = document.createElementNS(SVG, name);
  for (const [key, value] of Object.entries(attributes)) element.setAttribute(key, String(value));
  for (const child of children) if (child) element.append(child);
  return element;
}

function label(text, attributes) {
  const element = svg("text", attributes);
  element.textContent = text;
  return element;
}

function canvas(width, height, name) {
  const root = svg("svg", { class: "rb-diagram__svg", viewBox: `0 0 ${width} ${height}`, role: "img", "aria-label": name });
  // Set through the style object: the page's security policy does not allow a style attribute. A
  // diagram shrinks to fit its container but never below a readable width, so a wide one scrolls
  // sideways instead of turning into small print.
  root.style.maxWidth = width + "px";
  root.style.minWidth = Math.min(width, 420) + "px";
  return root;
}

function figure(title, drawing) {
  return h("figure.rb-diagram", title ? h("figcaption.rb-diagram__title", title) : null, h("div.rb-diagram__body", drawing));
}

// ------------------------------------------------------------------------------- flowchart

function nodeShape(node) {
  const { x, y, w, h: height, shape } = node;
  switch (shape) {
    case "decision":
      return svg("polygon", { class: "rb-node rb-node--decision", points: `${x + w / 2},${y} ${x + w},${y + height / 2} ${x + w / 2},${y + height} ${x},${y + height / 2}` });
    case "terminal":
      return svg("rect", { class: "rb-node rb-node--terminal", x, y, width: w, height, rx: height / 2 });
    case "circle":
      return svg("ellipse", { class: "rb-node rb-node--terminal", cx: x + w / 2, cy: y + height / 2, rx: w / 2, ry: height / 2 });
    case "rounded":
      return svg("rect", { class: "rb-node", x, y, width: w, height, rx: 14 });
    default:
      return svg("rect", { class: "rb-node", x, y, width: w, height, rx: 6 });
  }
}

export function drawFlowchart(source) {
  const graph = parseFlowchart(source);
  return graph ? drawGraph(graph) : null;
}

function drawGraph(graph) {
  const layout = layoutFlowchart(graph);
  const root = canvas(layout.width, layout.height, "Flowchart");
  root.append(
    svg("defs", {}, svg("marker", { id: "rb-arrow", viewBox: "0 0 10 10", refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: "auto-start-reverse" }, svg("path", { class: "rb-arrowhead", d: "M0,0 L10,5 L0,10 z" }))),
  );
  for (const edge of layout.edges) root.append(svg("path", { class: "rb-edge", d: edge.d, "marker-end": "url(#rb-arrow)" }));
  for (const node of layout.nodes) {
    const group = svg("g", {}, nodeShape(node));
    node.lines.forEach((line, index) => {
      const dy = (index - (node.lines.length - 1) / 2) * 16;
      group.append(label(line, { class: "rb-node__text", x: node.x + node.w / 2, y: node.y + node.h / 2 + dy + 4, "text-anchor": "middle" }));
    });
    root.append(group);
  }
  for (const edge of layout.edges) {
    if (!edge.label) continue;
    const width = edge.label.length * 6.4 + 10;
    root.append(svg("rect", { class: "rb-edge__back", x: edge.lx - width / 2, y: edge.ly - 11, width, height: 16, rx: 4 }), label(edge.label, { class: "rb-edge__text", x: edge.lx, y: edge.ly + 1, "text-anchor": "middle" }));
  }
  return figure("", root);
}

// ------------------------------------------------------------------------------- mind map

export function drawMindmap(source) {
  const tree = parseMindmap(source);
  return tree ? drawTree(tree) : null;
}

function drawTree(tree) {
  const layout = layoutMindmap(tree);
  const root = canvas(layout.width, layout.height, "Mind map");
  for (const link of layout.links) {
    const mid = (link.x1 + link.x2) / 2;
    root.append(svg("path", { class: "rb-edge", d: `M${link.x1},${link.y1} C${mid},${link.y1} ${mid},${link.y2} ${link.x2},${link.y2}` }));
  }
  for (const node of layout.nodes) {
    root.append(svg("rect", { class: "rb-node rb-node--branch" + (node.depth === 0 ? " rb-node--root" : ""), x: node.x, y: node.y, width: node.w, height: node.h, rx: 13 }), label(node.label, { class: "rb-node__text" + (node.depth === 0 ? " rb-node__text--root" : ""), x: node.x + node.w / 2, y: node.y + 17, "text-anchor": "middle" }));
  }
  return figure("", root);
}

// ------------------------------------------------------------------------------- chart

const PALETTE = ["#0071e3", "#6b3fc4", "#0b7285", "#b4500a", "#1a7f4b", "#c0262d", "#8a6d00", "#4c5bd4", "#2b8a3e", "#a61e4d", "#495057", "#1098ad"];

function niceMax(value) {
  if (value <= 0) return 1;
  const power = 10 ** Math.floor(Math.log10(value));
  const scaled = value / power;
  return (scaled <= 1 ? 1 : scaled <= 2 ? 2 : scaled <= 5 ? 5 : 10) * power;
}

function format(value) {
  return Math.abs(value) >= 1000 ? String(Math.round(value)) : String(Math.round(value * 100) / 100);
}

export function drawChart(source) {
  const chart = parseChart(source);
  return chart ? drawChartData(chart) : null;
}

function drawChartData(chart) {
  if (chart.type === "pie") return figure(chart.title, drawPie(chart));
  const width = 320;
  const height = 190;
  const left = 38;
  const bottom = 28;
  const top = 14;
  const inner = { w: width - left - 10, h: height - top - bottom };
  const low = Math.min(0, ...chart.values);
  const high = niceMax(Math.max(...chart.values, 0));
  const span = high - low || 1;
  const y = (value) => top + inner.h - ((value - low) / span) * inner.h;
  const root = canvas(width, height, chart.title || "Chart");
  for (const tick of [0, 0.5, 1]) {
    const value = low + span * tick;
    root.append(svg("line", { class: "rb-grid", x1: left, x2: width - 10, y1: y(value), y2: y(value) }), label(format(value) + chart.unit, { class: "rb-axis", x: left - 5, y: y(value) + 4, "text-anchor": "end" }));
  }
  const step = inner.w / chart.values.length;
  chart.values.forEach((value, index) => {
    const cx = left + step * index + step / 2;
    if (chart.type === "bar") {
      const barWidth = Math.min(34, step * 0.6);
      root.append(svg("rect", { class: "rb-bar", x: cx - barWidth / 2, y: Math.min(y(value), y(0)), width: barWidth, height: Math.max(1, Math.abs(y(value) - y(0))), rx: 3 }));
    }
    root.append(label(chart.labels[index], { class: "rb-axis", x: cx, y: height - 10, "text-anchor": "middle" }));
  });
  if (chart.type === "line") {
    const points = chart.values.map((value, index) => `${left + step * index + step / 2},${y(value)}`);
    root.append(svg("polyline", { class: "rb-line", points: points.join(" ") }));
    points.forEach((point) => root.append(svg("circle", { class: "rb-dot", cx: point.split(",")[0], cy: point.split(",")[1], r: 3.5 })));
  }
  chart.values.forEach((value, index) => root.append(label(format(value), { class: "rb-value", x: left + step * index + step / 2, y: y(value) - 5, "text-anchor": "middle" })));
  return figure(chart.title, root);
}

function drawPie(chart) {
  const total = chart.values.reduce((sum, value) => sum + value, 0);
  const radius = 62;
  const root = canvas(300, 150, chart.title || "Pie chart");
  if (total <= 0) return root;
  let angle = -Math.PI / 2;
  chart.values.forEach((value, index) => {
    const sweep = (value / total) * Math.PI * 2;
    const color = PALETTE[index % PALETTE.length];
    if (chart.values.length === 1 || sweep >= Math.PI * 2 - 1e-6) {
      root.append(svg("circle", { cx: 80, cy: 75, r: radius, fill: color }));
    } else {
      const x1 = 80 + radius * Math.cos(angle);
      const y1 = 75 + radius * Math.sin(angle);
      const x2 = 80 + radius * Math.cos(angle + sweep);
      const y2 = 75 + radius * Math.sin(angle + sweep);
      root.append(svg("path", { d: `M80,75 L${x1.toFixed(1)},${y1.toFixed(1)} A${radius},${radius} 0 ${sweep > Math.PI ? 1 : 0} 1 ${x2.toFixed(1)},${y2.toFixed(1)} Z`, fill: color, class: "rb-slice" }));
    }
    angle += sweep;
    const row = 14 + index * 17;
    root.append(svg("rect", { x: 165, y: row - 9, width: 10, height: 10, rx: 2, fill: color }), label(`${chart.labels[index]} · ${Math.round((value / total) * 100)}%`, { class: "rb-axis rb-axis--legend", x: 181, y: row }));
  });
  return root;
}

// ------------------------------------------------------------------------------- steps

export function drawSteps(source) {
  const steps = parseSteps(source);
  if (!steps) return null;
  return h(
    "figure.rb-diagram",
    h(
      "ol.rb-steps",
      steps.map((step, number) => {
        const marks = new Map();
        for (const mark of step.marks) marks.set(mark.index, [...(marks.get(mark.index) ?? []), mark.name]);
        return h(
          "li.rb-step",
          h("div.rb-step__cells", step.cells.map((cell, index) => {
            const names = marks.get(index) ?? [];
            const kind = names.find((name) => ["mid", "found", "swap"].includes(name));
            return h("div.rb-cell" + (kind ? ".rb-cell--" + kind : names.length ? ".rb-cell--mark" : ""), h("span.rb-cell__value", cell), h("span.rb-cell__index", String(index)), h("span.rb-cell__marks", names.join(" ")));
          })),
          h("p.rb-step__caption", h("strong", `Step ${number + 1}. `), step.caption),
        );
      }),
    ),
  );
}

// ------------------------------------------------------------------------------- Mermaid text

function drawSequence(sequence) {
  const layout = layoutSequence(sequence);
  const root = canvas(layout.width, layout.height, "Sequence diagram");
  root.append(svg("defs", {}, svg("marker", { id: "rb-arrow-seq", viewBox: "0 0 10 10", refX: 9, refY: 5, markerWidth: 7, markerHeight: 7, orient: "auto-start-reverse" }, svg("path", { class: "rb-arrowhead", d: "M0,0 L10,5 L0,10 z" }))));
  for (const actor of layout.actors) {
    root.append(svg("line", { class: "rb-lifeline", x1: actor.cx, x2: actor.cx, y1: layout.lifelineTop, y2: layout.lifelineBottom }), svg("rect", { class: "rb-node rb-node--terminal", x: actor.x, y: 6, width: actor.w, height: 28, rx: 8 }), label(actor.name, { class: "rb-node__text", x: actor.cx, y: 24, "text-anchor": "middle" }));
  }
  for (const message of layout.messages) {
    if (message.self) {
      root.append(svg("path", { class: "rb-edge" + (message.dashed ? " rb-edge--dashed" : ""), d: `M${message.x1},${message.y} h34 v18 h-34`, "marker-end": "url(#rb-arrow-seq)" }), label(message.text, { class: "rb-edge__text", x: message.x1 + 40, y: message.y + 12 }));
    } else {
      const middle = (message.x1 + message.x2) / 2;
      root.append(svg("line", { class: "rb-edge" + (message.dashed ? " rb-edge--dashed" : ""), x1: message.x1, x2: message.x2, y1: message.y + 8, y2: message.y + 8, "marker-end": "url(#rb-arrow-seq)" }), label(message.text, { class: "rb-edge__text", x: middle, y: message.y, "text-anchor": "middle" }));
    }
  }
  return figure("", root);
}

/**
 * A flowchart, a Mermaid diagram, or a diagram block of the page's own kind. Returns null when the
 * text cannot be read as a diagram; { unsupported: name } for a Mermaid kind that is not drawn here.
 */
export function drawDiagram(source) {
  const diagram = parseDiagram(source);
  if (!diagram) return null;
  switch (diagram.type) {
    case "flowchart":
      return drawGraph(diagram.graph);
    case "mindmap":
      return drawTree(diagram.tree);
    case "chart":
      return figure(diagram.chart.title, drawPie(diagram.chart));
    case "sequence":
      return drawSequence(diagram.sequence);
    default:
      return { unsupported: diagram.name };
  }
}
