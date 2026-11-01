// The diagrams of an explanatory answer, as data (no DOM here, so they can be tested). The model
// writes each one in a small text form; these functions read it, check it, and lay it out in
// numbers. diagram-draw.js turns the numbers into SVG. Nothing from the model is ever run or read
// as markup: a form that does not parse falls back to showing its text as code.

const MAX_LABEL = 60;

function clip(text, max = MAX_LABEL) {
  const clean = String(text).replace(/\s+/g, " ").trim();
  return clean.length > max ? clean.slice(0, max - 1) + "…" : clean;
}

// ------------------------------------------------------------------------------- flowchart

const SHAPES = [
  [/^\(\[(.*)\]\)/s, "terminal"],
  [/^\(\((.*?)\)\)/s, "circle"],
  [/^\[\[(.*?)\]\]/s, "box"],
  [/^\[(.*?)\]/s, "box"],
  [/^\{(.*?)\}/s, "decision"],
  [/^\((.*?)\)/s, "rounded"],
];

const ARROW = /^\s*(?:(?:-->|==>|-\.->|---)\s*(?:\|([^|]*)\|)?|--\s+([^->]+?)\s+-->)\s*/;
const ID = /^([A-Za-z0-9_]{1,24})/;

/** Reads "A([Start]) --> B{Is it sorted?} -->|Yes| C" lines. Returns null when nothing usable. */
export function parseFlowchart(source) {
  const lines = String(source).split("\n").map((line) => line.trim()).filter(Boolean);
  let direction = "TD";
  const nodes = new Map();
  const edges = [];
  const node = (text) => {
    const id = ID.exec(text);
    if (!id) return null;
    let rest = text.slice(id[0].length);
    let label = null;
    let shape = null;
    for (const [pattern, kind] of SHAPES) {
      const shaped = pattern.exec(rest);
      if (shaped) {
        label = clip(shaped[1].replace(/^"|"$/g, ""));
        shape = kind;
        rest = rest.slice(shaped[0].length);
        break;
      }
    }
    const existing = nodes.get(id[1]);
    if (!existing) {
      if (nodes.size >= 30) return null;
      nodes.set(id[1], { id: id[1], label: label ?? id[1], shape: shape ?? "box" });
    } else if (label !== null) {
      existing.label = label;
      existing.shape = shape;
    }
    return { id: id[1], rest };
  };
  for (const line of lines) {
    const head = /^(?:flowchart|graph)\s+(TD|TB|BT|LR|RL)\b/i.exec(line);
    if (head) {
      direction = /LR|RL/i.test(head[1]) ? "LR" : "TD";
      continue;
    }
    if (/^(%%|subgraph\b|end$|style\b|classDef\b|class\b|linkStyle\b)/.test(line)) continue;
    let rest = line.replace(/;$/, "");
    let from = node(rest);
    if (!from) continue;
    rest = from.rest;
    for (;;) {
      const arrow = ARROW.exec(rest);
      if (!arrow) break;
      const next = node(rest.slice(arrow[0].length));
      if (!next) break;
      if (edges.length < 60) edges.push({ from: from.id, to: next.id, label: clip(arrow[1] ?? arrow[2] ?? "", 24) });
      from = next;
      rest = next.rest;
    }
  }
  return nodes.size >= 2 && edges.length >= 1 ? { direction, nodes: [...nodes.values()], edges } : null;
}

/** Puts every node in a layer (its longest path from a start) and finds the arrows that go back. */
function layering(graph) {
  const ids = graph.nodes.map((node) => node.id);
  const out = new Map(ids.map((id) => [id, []]));
  const indegree = new Map(ids.map((id) => [id, 0]));
  graph.edges.forEach((edge, index) => {
    out.get(edge.from).push({ to: edge.to, index });
    indegree.set(edge.to, indegree.get(edge.to) + 1);
  });
  const state = new Map();
  const back = new Set();
  const visit = (start) => {
    const stack = [[start, 0]];
    state.set(start, 1);
    while (stack.length) {
      const top = stack[stack.length - 1];
      const [id, at] = top;
      const next = out.get(id)[at];
      if (!next) {
        state.set(id, 2);
        stack.pop();
        continue;
      }
      top[1]++;
      const seen = state.get(next.to);
      if (seen === 1 || next.to === id) back.add(next.index);
      else if (!seen) {
        state.set(next.to, 1);
        stack.push([next.to, 0]);
      }
    }
  };
  const roots = ids.filter((id) => indegree.get(id) === 0);
  for (const id of [...roots, ...ids]) if (!state.has(id)) visit(id);
  const layer = new Map(ids.map((id) => [id, 0]));
  // Longest path over the arrows that go forward; the number of nodes bounds the passes.
  for (let pass = 0; pass < ids.length; pass++) {
    let changed = false;
    graph.edges.forEach((edge, index) => {
      if (back.has(index)) return;
      if (layer.get(edge.to) < layer.get(edge.from) + 1) {
        layer.set(edge.to, layer.get(edge.from) + 1);
        changed = true;
      }
    });
    if (!changed) break;
  }
  return { layer, back };
}

function wrap(label, width) {
  const words = label.split(" ");
  const lines = [];
  let line = "";
  for (const word of words) {
    if (line && (line + " " + word).length > width) {
      lines.push(line);
      line = word;
    } else {
      line = line ? line + " " + word : word;
    }
  }
  if (line) lines.push(line);
  return lines.slice(0, 4);
}

const CHAR = 7.2;
const LINE = 16;

/**
 * Numbers for the drawing: the size of the picture, each node's box and each arrow's path. Layers
 * run down the page ("TD") or across it ("LR"); within a layer the nodes are ordered by where their
 * predecessors are, so arrows cross less.
 */
export function layoutFlowchart(graph) {
  const { layer, back } = layering(graph);
  const horizontal = graph.direction === "LR";
  const sized = new Map();
  for (const node of graph.nodes) {
    const lines = wrap(node.label, node.shape === "decision" ? 14 : 20);
    const textWidth = Math.max(...lines.map((line) => line.length)) * CHAR;
    let width = Math.max(72, textWidth + 28);
    let height = lines.length * LINE + 22;
    if (node.shape === "decision") {
      width = Math.max(96, textWidth * 1.5 + 30);
      height = Math.max(58, lines.length * LINE * 1.5 + 26);
    } else if (node.shape === "circle") {
      width = height = Math.max(width, height);
    }
    sized.set(node.id, { ...node, lines, w: Math.round(width), h: Math.round(height) });
  }
  const layers = [];
  for (const node of graph.nodes) {
    const index = layer.get(node.id);
    (layers[index] ??= []).push(sized.get(node.id));
  }
  const layerList = layers.filter(Boolean);
  const position = new Map();
  layerList.forEach((members, depth) => {
    if (depth > 0) {
      // Order by the average place of the predecessors in the layer before.
      const score = (node) => {
        const before = graph.edges.filter((edge, index) => edge.to === node.id && !back.has(index) && position.has(edge.from));
        return before.length ? before.reduce((sum, edge) => sum + position.get(edge.from), 0) / before.length : Number.MAX_SAFE_INTEGER;
      };
      members.sort((a, b) => score(a) - score(b));
    }
    members.forEach((node, index) => position.set(node.id, index));
  });
  // Axis 1 runs along the flow, axis 2 across it. For TD, along = y and across = x.
  const along = (node) => (horizontal ? node.w : node.h);
  const across = (node) => (horizontal ? node.h : node.w);
  const GAP_ALONG = horizontal ? 70 : 52;
  const GAP_ACROSS = 28;
  const crossSizes = layerList.map((members) => members.reduce((sum, node) => sum + across(node), 0) + GAP_ACROSS * (members.length - 1));
  const crossMax = Math.max(...crossSizes);
  let cursor = 16;
  layerList.forEach((members, depth) => {
    const thick = Math.max(...members.map(along));
    let at = 16 + (crossMax - crossSizes[depth]) / 2;
    for (const node of members) {
      const main = cursor + (thick - along(node)) / 2;
      const side = at;
      node.x = Math.round(horizontal ? main : side);
      node.y = Math.round(horizontal ? side : main);
      at += across(node) + GAP_ACROSS;
    }
    cursor += thick + GAP_ALONG;
  });
  const placed = new Map(layerList.flat().map((node) => [node.id, node]));
  const pad = back.size ? 56 : 16;
  const width = horizontal ? cursor - GAP_ALONG + 16 : crossMax + 32 + pad - 16;
  const height = horizontal ? crossMax + 32 + pad - 16 : cursor - GAP_ALONG + 16;
  const paths = graph.edges.map((edge, index) => {
    const a = placed.get(edge.from);
    const b = placed.get(edge.to);
    if (!a || !b) return null;
    const isBack = back.has(index);
    let d;
    let lx;
    let ly;
    if (isBack || (horizontal ? b.x <= a.x : b.y <= a.y)) {
      // An arrow that returns goes round the outside and comes in from the side.
      if (horizontal) {
        const y = Math.max(a.y + a.h, b.y + b.h) + 28;
        const sx = a.x + a.w / 2;
        const ex = b.x + b.w / 2;
        d = `M${sx},${a.y + a.h} C${sx},${y} ${ex},${y} ${ex},${b.y + b.h}`;
        lx = (sx + ex) / 2;
        ly = y - 4;
      } else {
        const x = Math.max(a.x + a.w, b.x + b.w) + 30;
        const sy = a.y + a.h / 2;
        const ey = b.y + b.h / 2;
        d = `M${a.x + a.w},${sy} C${x},${sy} ${x},${ey} ${b.x + b.w},${ey}`;
        lx = x + 2;
        ly = (sy + ey) / 2;
      }
    } else if (horizontal) {
      const sx = a.x + a.w;
      const sy = a.y + a.h / 2;
      const ex = b.x;
      const ey = b.y + b.h / 2;
      const mid = (sx + ex) / 2;
      d = `M${sx},${sy} C${mid},${sy} ${mid},${ey} ${ex},${ey}`;
      lx = mid;
      ly = (sy + ey) / 2 - 6;
    } else {
      const sx = a.x + a.w / 2;
      const sy = a.y + a.h;
      const ex = b.x + b.w / 2;
      const ey = b.y;
      const mid = (sy + ey) / 2;
      d = `M${sx},${sy} C${sx},${mid} ${ex},${mid} ${ex},${ey}`;
      lx = (sx + ex) / 2 + (sx === ex ? 14 : 0);
      ly = mid - 2;
    }
    return { d, label: edge.label, lx: Math.round(lx), ly: Math.round(ly), back: isBack };
  });
  return { width: Math.round(width), height: Math.round(height), nodes: [...placed.values()], edges: paths.filter(Boolean) };
}

// ------------------------------------------------------------------------------- mind map

/** Reads an indented list (two spaces a level) into a tree. Returns null for a single line. */
export function parseMindmap(source) {
  const rows = String(source)
    .split("\n")
    .filter((line) => line.trim())
    .slice(0, 24)
    .map((line) => {
      const indent = /^ */.exec(line.replace(/\t/g, "  "))[0].length;
      return { depth: Math.floor(indent / 2), label: clip(line.trim().replace(/^[-*]\s+/, ""), 40) };
    });
  if (rows.length < 2) return null;
  const root = { label: rows[0].label, children: [] };
  const stack = [{ depth: 0, node: root }];
  for (const row of rows.slice(1)) {
    const depth = Math.max(1, row.depth);
    while (stack.length > 1 && stack[stack.length - 1].depth >= depth) stack.pop();
    const child = { label: row.label, children: [] };
    stack[stack.length - 1].node.children.push(child);
    stack.push({ depth, node: child });
  }
  return root;
}

/** Root on the left, branches to the right; each parent sits at the middle of its children. */
export function layoutMindmap(root) {
  const ROW = 34;
  const nodes = [];
  const links = [];
  let leaf = 0;
  let deepest = 0;
  const place = (node, depth) => {
    deepest = Math.max(deepest, depth);
    const width = Math.max(64, node.label.length * 7 + 24);
    let y;
    if (node.children.length === 0) {
      y = leaf++ * ROW;
    } else {
      const kids = node.children.map((child) => place(child, depth + 1));
      y = (kids[0].y + kids[kids.length - 1].y) / 2;
      for (const kid of kids) links.push({ from: node, to: kid });
    }
    node.depth = depth;
    node.y = y;
    node.w = width;
    nodes.push(node);
    return node;
  };
  place(root, 0);
  const widest = Array.from({ length: deepest + 1 }, () => 0);
  for (const node of nodes) widest[node.depth] = Math.max(widest[node.depth], node.w);
  const starts = [];
  let x = 12;
  for (const w of widest) {
    starts.push(x);
    x += w + 44;
  }
  for (const node of nodes) node.x = starts[node.depth];
  return {
    width: Math.round(x - 44 + 12),
    height: Math.round(Math.max(leaf, 1) * ROW + 12),
    nodes: nodes.map((node) => ({ label: node.label, x: Math.round(node.x), y: Math.round(node.y + 6), w: node.w, h: 26, depth: node.depth })),
    links: links.map(({ from, to }) => ({ x1: Math.round(from.x + from.w), y1: Math.round(from.y + 19), x2: Math.round(to.x), y2: Math.round(to.y + 19) })),
  };
}

// ------------------------------------------------------------------------------- chart

/** Reads "type: bar / title: ... / labels: a, b / values: 1, 2". Returns null if it is not usable. */
export function parseChart(source) {
  const fields = {};
  for (const line of String(source).split("\n")) {
    const match = /^\s*(type|title|labels|values|unit)\s*:\s*(.*)$/i.exec(line);
    if (match) fields[match[1].toLowerCase()] = match[2].trim();
  }
  const type = ["bar", "line", "pie"].includes((fields.type ?? "bar").toLowerCase()) ? (fields.type ?? "bar").toLowerCase() : null;
  const labels = (fields.labels ?? "").split(",").map((label) => clip(label, 16)).filter(Boolean);
  const values = (fields.values ?? "").split(",").map((value) => Number(value.trim()));
  if (!type || labels.length < 2 || labels.length > 12 || labels.length !== values.length) return null;
  if (values.some((value) => !Number.isFinite(value)) || (type === "pie" && values.some((value) => value < 0))) return null;
  return { type, title: clip(fields.title ?? "", 60), unit: clip(fields.unit ?? "", 8), labels, values };
}

// ------------------------------------------------------------------------------- steps

/** Reads "2 5 8 12 | low=0 mid=3 high=6 | caption" lines into rows of cells with marks. */
export function parseSteps(source) {
  const steps = [];
  for (const line of String(source).split("\n").filter((row) => row.trim()).slice(0, 8)) {
    const [cellText = "", markText = "", ...caption] = line.split("|");
    const cells = cellText.trim().split(/\s+/).filter(Boolean).slice(0, 10).map((cell) => clip(cell, 6));
    if (!cells.length) continue;
    const marks = [];
    for (const mark of markText.trim().split(/[\s,]+/).filter(Boolean)) {
      const pair = /^([A-Za-z][A-Za-z0-9_]{0,10})=(\d{1,2})$/.exec(mark);
      if (pair && Number(pair[2]) < cells.length) marks.push({ name: pair[1].toLowerCase(), index: Number(pair[2]) });
    }
    steps.push({ cells, marks, caption: clip(caption.join("|"), 120) });
  }
  return steps.length ? steps : null;
}
