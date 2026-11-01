// The text of an assistant answer, read into blocks (no DOM here, so it can be tested). The
// answer is a Markdown subset: headings, paragraphs, nested bullet, numbered and task lists, tables,
// quotes, rules, code blocks, and diagram blocks ("flowchart", "mermaid", "mindmap", "chart",
// "steps", "math") that rich-render.js draws. HTML is never read: the renderer only sets text.

export const VISUALS = new Set(["flowchart", "diagram", "mermaid", "mindmap", "chart", "steps", "math"]);

const MAX_BLOCKS = 300;
const ITEM = /^(\s*)([-*+]|\d{1,3}[.)])\s+(.*)$/;

/** Splits an answer into blocks. Never throws: odd input becomes paragraphs. */
export function parseRich(text) {
  const lines = String(text ?? "").replace(/\r\n?/g, "\n").replace(/\t/g, "  ").split("\n");
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
    const fence = /^\s*(`{3,}|~{3,})\s*([^\s`]*)[^`]*$/.exec(line);
    if (fence) {
      flush();
      const body = [];
      const closer = new RegExp("^\\s*" + fence[1][0] + "{" + fence[1].length + ",}\\s*$");
      i++;
      while (i < lines.length && !closer.test(lines[i])) body.push(lines[i++]);
      const lang = fence[2].toLowerCase();
      // A closing fence that is missing (an answer cut off) still ends the block.
      blocks.push(VISUALS.has(lang) ? { type: "visual", kind: lang === "diagram" ? "flowchart" : lang, source: body.join("\n") } : { type: "code", lang, code: dedent(body).join("\n") });
      continue;
    }
    if (!line.trim()) {
      flush();
      continue;
    }
    const heading = /^(#{1,6})\s+(.+?)\s*#*\s*$/.exec(line);
    if (heading) {
      flush();
      blocks.push({ type: "heading", level: Math.min(heading[1].length + 1, 5), text: heading[2] });
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
    if (ITEM.test(line)) {
      flush();
      const items = [];
      while (i < lines.length) {
        const item = ITEM.exec(lines[i]);
        if (item) {
          items.push({ indent: item[1].length, ordered: /\d/.test(item[2]), number: /\d/.test(item[2]) ? parseInt(item[2], 10) : null, text: item[3] });
        } else if (items.length && lines[i].trim() && /^\s{2,}\S/.test(lines[i]) && !isFence(lines[i]) && !isTableRow(lines[i])) {
          // A continuation line of the item above.
          items[items.length - 1].text += " " + lines[i].trim();
        } else break;
        i++;
      }
      i--;
      blocks.push(listFrom(items));
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

function isFence(line) {
  return /^\s*(`{3,}|~{3,})/.test(line);
}

/** Removes the indent that every line of a block shares, so a nested block keeps its shape. */
function dedent(lines) {
  const indents = lines.filter((line) => line.trim()).map((line) => /^ */.exec(line)[0].length);
  const cut = indents.length ? Math.min(...indents) : 0;
  return cut ? lines.map((line) => line.slice(Math.min(cut, /^ */.exec(line)[0].length))) : lines;
}

/** Builds a nested list from items with their indents: deeper items belong to the item above. */
function listFrom(items) {
  const root = { type: "list", ordered: items[0].ordered, start: items[0].number ?? 1, items: [] };
  const stack = [{ indent: items[0].indent, list: root }];
  for (const item of items) {
    while (stack.length > 1 && item.indent < stack[stack.length - 1].indent) stack.pop();
    let top = stack[stack.length - 1];
    if (item.indent > top.indent && top.list.items.length) {
      const parent = top.list.items[top.list.items.length - 1];
      parent.sub = { type: "list", ordered: item.ordered, start: item.number ?? 1, items: [] };
      top = { indent: item.indent, list: parent.sub };
      stack.push(top);
    }
    const task = /^\[([ xX])\]\s+(.*)$/.exec(item.text);
    top.list.items.push(task ? { text: task[2], checked: task[1] !== " ", sub: null } : { text: item.text, sub: null });
  }
  return root;
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

// ---------------------------------------------------------------------------------- inline

const INLINE = /(`[^`\n]+`)|(\[[^\]\n]+\]\([^)\s]+\))|(\*\*\*[^*\n]+\*\*\*)|(\*\*(?:[^*\n]|\*(?!\*))+\*\*)|((?<![\w])__[^_\n]+__(?![\w]))|(~~[^~\n]+~~)|(\*[^*\s][^*\n]*\*)|((?<![\w])_[^_\s][^_\n]*_(?![\w]))/g;

/**
 * Inline text into pieces: text, code, bold, italic, strike and link. Bold, italic and strike hold
 * their own pieces, so **bold with `code`** and *italic with **bold*** read correctly. A link is
 * kept only when its address is http or https; otherwise its words stay as plain text.
 */
export function parseInline(text, depth = 0) {
  const pieces = [];
  let last = 0;
  const source = String(text);
  for (const match of source.matchAll(INLINE)) {
    if (match.index > last) pieces.push({ type: "text", value: source.slice(last, match.index) });
    const token = match[0];
    if (match[1]) pieces.push({ type: "code", value: token.slice(1, -1) });
    else if (match[2]) {
      const link = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(token);
      if (link && /^https?:\/\//i.test(link[2])) pieces.push({ type: "link", href: link[2], children: nested(link[1], depth) });
      else pieces.push({ type: "text", value: link ? link[1] : token });
    } else if (match[3]) pieces.push({ type: "bold", children: [{ type: "italic", children: nested(token.slice(3, -3), depth) }] });
    else if (match[4] || match[5]) pieces.push({ type: "bold", children: nested(token.slice(2, -2), depth) });
    else if (match[6]) pieces.push({ type: "strike", children: nested(token.slice(2, -2), depth) });
    else pieces.push({ type: "italic", children: nested(token.slice(1, -1), depth) });
    last = match.index + token.length;
  }
  if (last < source.length) pieces.push({ type: "text", value: source.slice(last) });
  return pieces;
}

function nested(text, depth) {
  return depth >= 3 ? [{ type: "text", value: text }] : parseInline(text, depth + 1);
}

/** Whether a heading opens the closing section of an answer (the summary or the takeaways). */
export function isSummaryHeading(text) {
  return /^(?:[\p{Extended_Pictographic}\s]*)(summary|in summary|quick summary|key takeaways?|takeaways?|conclusion|recap|to sum up|important points?|remember)[\s:.!]*$/iu.test(String(text).replace(/\*\*/g, "").trim());
}
