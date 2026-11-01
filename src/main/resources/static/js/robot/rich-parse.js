// The text of an assistant answer, read into blocks (no DOM here, so it can be tested). The
// answer is a small Markdown subset: headings, paragraphs, lists, tables, quotes, code blocks, and
// diagram blocks ("flowchart", "mindmap", "chart", "steps", "math") that rich-render.js draws.
// Anything else stays plain text. HTML is never read: the renderer only sets text.

export const VISUALS = new Set(["flowchart", "diagram", "mindmap", "chart", "steps", "math"]);

const MAX_BLOCKS = 200;

/** Splits an answer into blocks. Never throws: odd input becomes paragraphs. */
export function parseRich(text) {
  const lines = String(text ?? "").replace(/\r\n?/g, "\n").split("\n");
  const blocks = [];
  let paragraph = [];
  const flush = () => {
    if (paragraph.length) {
      blocks.push({ type: "paragraph", text: paragraph.join(" ") });
      paragraph = [];
    }
  };
  for (let i = 0; i < lines.length && blocks.length < MAX_BLOCKS; i++) {
    const line = lines[i];
    const fence = /^\s*```\s*([A-Za-z0-9_+#.-]*)\s*$/.exec(line);
    if (fence) {
      flush();
      const body = [];
      i++;
      while (i < lines.length && !/^\s*```\s*$/.test(lines[i])) body.push(lines[i++]);
      const lang = fence[1].toLowerCase();
      blocks.push(VISUALS.has(lang) ? { type: "visual", kind: lang === "diagram" ? "flowchart" : lang, source: body.join("\n") } : { type: "code", lang, code: body.join("\n") });
      continue;
    }
    if (!line.trim()) {
      flush();
      continue;
    }
    const heading = /^(#{1,4})\s+(.+?)\s*#*\s*$/.exec(line);
    if (heading) {
      flush();
      blocks.push({ type: "heading", level: Math.min(heading[1].length + 1, 4), text: heading[2] });
      continue;
    }
    if (/^\s*([-*_])(\s*\1){2,}\s*$/.test(line)) {
      flush();
      blocks.push({ type: "rule" });
      continue;
    }
    if (isTableRow(line) && i + 1 < lines.length && isTableDivider(lines[i + 1])) {
      flush();
      const header = cells(line);
      const rows = [];
      i += 2;
      while (i < lines.length && isTableRow(lines[i])) rows.push(cells(lines[i++]));
      i--;
      blocks.push({ type: "table", header, rows: rows.map((row) => header.map((_, index) => row[index] ?? "")) });
      continue;
    }
    const item = /^\s*([-*+]|\d{1,3}[.)])\s+(.*)$/.exec(line);
    if (item) {
      flush();
      const ordered = /\d/.test(item[1]);
      const items = [item[2]];
      while (i + 1 < lines.length) {
        const next = /^\s*([-*+]|\d{1,3}[.)])\s+(.*)$/.exec(lines[i + 1]);
        if (!next || /\d/.test(next[1]) !== ordered) break;
        items.push(next[2]);
        i++;
      }
      blocks.push({ type: "list", ordered, items });
      continue;
    }
    const quote = /^\s*>\s?(.*)$/.exec(line);
    if (quote) {
      flush();
      const body = [quote[1]];
      while (i + 1 < lines.length && /^\s*>/.test(lines[i + 1])) body.push(lines[++i].replace(/^\s*>\s?/, ""));
      blocks.push({ type: "quote", text: body.join(" ") });
      continue;
    }
    paragraph.push(line.trim());
  }
  flush();
  return blocks;
}

function isTableRow(line) {
  return /^\s*\|.*\|\s*$/.test(line);
}

function isTableDivider(line) {
  return /^\s*\|(\s*:?-{2,}:?\s*\|)+\s*$/.test(line);
}

function cells(line) {
  return line
    .trim()
    .replace(/^\|/, "")
    .replace(/\|$/, "")
    .split("|")
    .map((cell) => cell.trim());
}

/** Inline text into pieces: plain text, **bold**, *italic* and `code`. */
export function parseInline(text) {
  const pieces = [];
  const pattern = /(`[^`\n]+`)|(\*\*[^*\n]+\*\*)|(\*[^*\n]+\*)/g;
  let last = 0;
  for (const match of String(text).matchAll(pattern)) {
    if (match.index > last) pieces.push({ type: "text", value: text.slice(last, match.index) });
    const token = match[0];
    if (match[1]) pieces.push({ type: "code", value: token.slice(1, -1) });
    else if (match[2]) pieces.push({ type: "bold", value: token.slice(2, -2) });
    else pieces.push({ type: "italic", value: token.slice(1, -1) });
    last = match.index + token.length;
  }
  if (last < text.length) pieces.push({ type: "text", value: text.slice(last) });
  return pieces;
}
