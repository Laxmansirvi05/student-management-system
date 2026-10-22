import { hideStyles, setHtml, showStyles } from "./safe-html.js";

// The editing document of the notes editor: how the page's DOM maps to the stored HTML, how a
// selection is remembered, and the undo history. Everything here works on one element, the
// editable root, and nothing in it knows about the toolbar or the network.
//
// The stored HTML is the HTML the server's sanitizer allows (NoteHtmlSanitizer): headings, lists,
// checklists, tables, pictures, file chips and a small set of inline marks. The editing DOM is the
// same markup plus a few editing-only touches (contenteditable="false" on pictures and chips, a
// <br> in empty blocks so the caret has somewhere to stand). `serialize` removes them again.

export const TEXT_BLOCKS = "p, h1, h2, h3, h4, h5, h6, li, blockquote, pre, td, th, div";
const BLOCK_CHILDREN = "p, h1, h2, h3, h4, h5, h6, ul, ol, li, blockquote, pre, table, tr, td, th, div, hr, img, figure";

export const isElement = (node) => node && node.nodeType === Node.ELEMENT_NODE;

// --- selection ------------------------------------------------------------------------------

/** The current selection range if it lies inside the root, otherwise null. */
export function rangeIn(root) {
  const selection = document.getSelection();
  if (!selection || selection.rangeCount === 0) {
    return null;
  }
  const range = selection.getRangeAt(0);
  return root.contains(range.startContainer) && root.contains(range.endContainer) ? range : null;
}

export function selectRange(range) {
  const selection = document.getSelection();
  selection.removeAllRanges();
  selection.addRange(range);
}

/** Puts the caret at the start (or end) of an element's content. */
export function placeCaret(element, atEnd = false) {
  const range = document.createRange();
  range.selectNodeContents(element);
  range.collapse(!atEnd);
  selectRange(range);
}

/** The closest ancestor (or the node itself) matching the selector, staying inside the root. */
export function closestIn(node, selector, root) {
  let current = isElement(node) ? node : node && node.parentElement;
  while (current && current !== root) {
    if (current.matches(selector)) {
      return current;
    }
    current = current.parentElement;
  }
  return null;
}

/** The innermost block that holds the caret's text (a paragraph, heading, list item, cell...). */
export function currentTextBlock(root, range = rangeIn(root)) {
  if (!range) {
    return null;
  }
  let block = closestIn(range.startContainer, TEXT_BLOCKS, root);
  // A block that contains other blocks is a container, not a text block: go in to the first child.
  while (block && block.querySelector(BLOCK_CHILDREN) && range.startContainer === block) {
    const child = block.childNodes[range.startOffset] || block.lastChild;
    const inner = isElement(child) ? child : null;
    if (!inner || !inner.matches(TEXT_BLOCKS)) {
      break;
    }
    block = inner;
  }
  return block;
}

/** Text blocks that have no block inside them and are touched by the range. */
export function leafBlocksIn(root, range) {
  if (!range) {
    return [];
  }
  const all = [...root.querySelectorAll(TEXT_BLOCKS)].filter((block) => !block.querySelector(BLOCK_CHILDREN));
  const touched = all.filter((block) => range.intersectsNode(block));
  if (touched.length) {
    return touched;
  }
  const own = currentTextBlock(root, range);
  return own ? [own] : [];
}

/** A selection as paths from the root, so it survives the root's content being replaced. */
export function saveSelection(root) {
  const range = rangeIn(root);
  if (!range) {
    return null;
  }
  return {
    start: pathOf(root, range.startContainer),
    startOffset: range.startOffset,
    end: pathOf(root, range.endContainer),
    endOffset: range.endOffset,
  };
}

export function restoreSelection(root, saved) {
  if (!saved) {
    return;
  }
  const start = nodeAt(root, saved.start);
  const end = nodeAt(root, saved.end);
  if (!start || !end) {
    return;
  }
  const range = document.createRange();
  range.setStart(start, Math.min(saved.startOffset, lengthOf(start)));
  range.setEnd(end, Math.min(saved.endOffset, lengthOf(end)));
  selectRange(range);
}

function pathOf(root, node) {
  const path = [];
  let current = node;
  while (current && current !== root) {
    path.unshift([...current.parentNode.childNodes].indexOf(current));
    current = current.parentNode;
  }
  return path;
}

function nodeAt(root, path) {
  let current = root;
  for (const index of path) {
    current = current && current.childNodes[index];
  }
  return current || null;
}

const lengthOf = (node) => (node.nodeType === Node.TEXT_NODE ? node.length : node.childNodes.length);

// --- the editing document -------------------------------------------------------------------

/**
 * Makes loaded HTML ready to edit: every empty block gets a <br> so the caret can stand in it,
 * pictures and file chips become non-editable islands, checklists get their checkboxes in step
 * with data-checked, and a paragraph follows a document that ends in something the caret cannot
 * enter, so there is always a place to continue writing.
 */
export function prepareForEditing(root) {
  normalizeBlocks(root);
  for (const block of root.querySelectorAll("p, h1, h2, h3, h4, h5, h6, li, blockquote, td, th, pre")) {
    if (!block.firstChild) {
      block.append(document.createElement("br"));
    }
  }
  for (const island of root.querySelectorAll("img, a[data-note-file]")) {
    island.setAttribute("contenteditable", "false");
    island.setAttribute("draggable", "false");
  }
  for (const item of root.querySelectorAll('li[data-type="taskItem"]')) {
    shapeTaskItem(item);
  }
  // While editing, a code block is a <pre> with its text directly inside; <code> is added on saving.
  for (const code of root.querySelectorAll("pre > code")) {
    code.replaceWith(...code.childNodes);
  }
  for (const label of root.querySelectorAll('li[data-type="taskItem"] > label')) {
    label.setAttribute("contenteditable", "false");
  }
  ensureTrailingParagraph(root);
}

const BLOCK_ONLY = "ul, ol, table, pre, blockquote, h1, h2, h3, h4, h5, h6, hr, p, div, li";

/**
 * Browsers sometimes put a list or a table inside a paragraph (for instance when a list is made of
 * an empty paragraph). Such a paragraph is dissolved: its blocks move up, and any loose text
 * around them is wrapped in paragraphs of its own. The selection stays on the same nodes.
 */
export function normalizeBlocks(root) {
  const range = rangeIn(root);
  const saved = range && { sc: range.startContainer, so: range.startOffset, ec: range.endContainer, eo: range.endOffset };
  for (const paragraph of [...root.querySelectorAll("p")]) {
    if (!paragraph.isConnected || ![...paragraph.children].some((child) => child.matches(BLOCK_ONLY))) {
      continue;
    }
    const pieces = [];
    let run = null;
    for (const node of [...paragraph.childNodes]) {
      if (isElement(node) && node.matches(BLOCK_ONLY)) {
        run = null;
        pieces.push(node);
      } else {
        if (!run) {
          run = document.createElement("p");
          pieces.push(run);
        }
        run.append(node);
      }
    }
    // Loose runs that hold nothing but whitespace or a placeholder <br> are not kept.
    const hasContent = (piece) => piece.tagName !== "P" || piece.textContent.trim() || piece.querySelector("img, a[data-note-file]");
    paragraph.replaceWith(...pieces.filter(hasContent));
  }
  // A quotation holds paragraphs, as it does in the stored document.
  for (const quote of root.querySelectorAll("blockquote")) {
    let run = null;
    for (const node of [...quote.childNodes]) {
      const isBlock = isElement(node) && node.matches(BLOCK_ONLY);
      if (isBlock) {
        run = null;
      } else {
        if (!run) {
          run = document.createElement("p");
          node.before(run);
        }
        run.append(node);
      }
    }
    if (!quote.firstChild) {
      const paragraph = document.createElement("p");
      paragraph.append(document.createElement("br"));
      quote.append(paragraph);
    }
  }
  // Text left directly in the root (outside any block) is wrapped too.
  for (const node of [...root.childNodes]) {
    if (node.nodeType === Node.TEXT_NODE && node.nodeValue.trim()) {
      const paragraph = document.createElement("p");
      node.replaceWith(paragraph);
      paragraph.append(node);
    }
  }
  // A caret left on the quotation itself belongs in its paragraph.
  if (saved && saved.sc === saved.ec && saved.so === saved.eo && isElement(saved.sc) && saved.sc.matches("blockquote")) {
    const target = saved.sc.children[Math.min(saved.so, saved.sc.children.length - 1)] || saved.sc.firstElementChild;
    if (target) {
      saved.sc = saved.ec = target;
      saved.so = saved.eo = 0;
    }
  }
  if (saved && saved.sc.isConnected && saved.ec.isConnected) {
    try {
      const after = document.createRange();
      after.setStart(saved.sc, Math.min(saved.so, lengthOf(saved.sc)));
      after.setEnd(saved.ec, Math.min(saved.eo, lengthOf(saved.ec)));
      selectRange(after);
    } catch {
      /* the selection could not be kept */
    }
  }
}

/** A task item is <li data-type=taskItem data-checked><label><input></label><div>content</div></li>. */
export function shapeTaskItem(item) {
  let label = item.querySelector(":scope > label");
  if (!label) {
    label = document.createElement("label");
    item.prepend(label);
  }
  let box = label.querySelector("input");
  if (!box) {
    box = document.createElement("input");
    box.type = "checkbox";
    label.prepend(box);
  }
  if (!label.querySelector("span")) {
    label.append(document.createElement("span"));
  }
  const checked = item.getAttribute("data-checked") === "true";
  item.setAttribute("data-checked", checked ? "true" : "false");
  box.checked = checked;
  if (checked) {
    box.setAttribute("checked", "");
  } else {
    box.removeAttribute("checked");
  }
  let body = item.querySelector(":scope > div");
  if (!body) {
    body = document.createElement("div");
    while (label.nextSibling) {
      body.append(label.nextSibling);
    }
    item.append(body);
  }
  if (!body.firstChild || ![...body.childNodes].some((node) => isElement(node) && node.matches("p"))) {
    const paragraph = document.createElement("p");
    paragraph.append(...body.childNodes);
    if (!paragraph.firstChild) {
      paragraph.append(document.createElement("br"));
    }
    body.append(paragraph);
  }
  label.setAttribute("contenteditable", "false");
}

export function ensureTrailingParagraph(root) {
  const last = root.lastElementChild;
  if (!last || !last.matches("p, h1, h2, h3, h4, h5, h6")) {
    const paragraph = document.createElement("p");
    paragraph.append(document.createElement("br"));
    root.append(paragraph);
  }
}

// --- serialising ----------------------------------------------------------------------------

const KEEP_ATTRIBUTES = {
  A: ["href", "target", "rel", "data-note-link", "data-link-type", "data-file-name", "data-file-size", "data-note-file"],
  IMG: ["src", "alt", "title", "width", "height", "data-align"],
  UL: ["data-type"],
  OL: ["start", "type"],
  LI: ["data-type", "data-checked"],
  INPUT: ["type", "checked", "disabled"],
  MARK: ["data-color", "style"],
  CODE: ["class"],
  PRE: ["class"],
  TH: ["colspan", "rowspan", "colwidth", "style"],
  TD: ["colspan", "rowspan", "colwidth", "style"],
  COL: ["style"],
  TABLE: ["style"],
};
const STYLE = "data-nt-style";
const STYLED = new Set(["P", "H1", "H2", "H3", "H4", "H5", "H6", "SPAN", "LI", "BLOCKQUOTE", "TABLE", "TH", "TD", "COL", "MARK", "DIV"]);

/**
 * The HTML to store for the editing root. The work is done on a parsed copy whose style attributes
 * are renamed to data-nt-style: copying an element that has a style attribute raises a Content
 * Security Policy violation, and the page's own copy of the document must not be touched.
 */
export function serialize(root) {
  const copy = new DOMParser().parseFromString("<!doctype html><body>" + hideStyles(root.innerHTML), "text/html").body;
  copy.normalize();
  for (const ui of copy.querySelectorAll("[data-nt-ui]")) {
    ui.remove();
  }
  tidy(copy);
  dropZeroWidthAndEmptyMarks(copy);
  while (copy.lastElementChild && isEmptyParagraph(copy.lastElementChild)) {
    copy.lastElementChild.remove();
  }
  return copy.innerHTML ? showStyles(copy.innerHTML) : "<p></p>";
}

/** Zero-width spaces hold the caret inside a new mark; marks left with no text are removed. */
function dropZeroWidthAndEmptyMarks(copy) {
  const walker = document.createTreeWalker(copy, NodeFilter.SHOW_TEXT);
  const texts = [];
  while (walker.nextNode()) {
    texts.push(walker.currentNode);
  }
  for (const text of texts) {
    if (text.nodeValue.includes("\u200b")) {
      text.nodeValue = text.nodeValue.replaceAll("\u200b", "");
    }
  }
  let removed = true;
  while (removed) {
    removed = false;
    for (const mark of copy.querySelectorAll("span, mark, strong, em, u, s, sub, sup, a:not([data-note-file]), code")) {
      if (!mark.textContent && !mark.querySelector("img, input, br, a[data-note-file]") && !mark.closest("pre")) {
        mark.remove();
        removed = true;
      }
    }
  }
}

function isEmptyParagraph(element) {
  return element.matches("p") && !element.textContent.trim() && !element.querySelector("img, a[data-note-file]");
}

function tidy(parent) {
  for (const child of [...parent.childNodes]) {
    if (!isElement(child)) {
      continue;
    }
    tidy(child);
    renameLegacyTags(child);
  }
  for (const child of [...parent.childNodes]) {
    if (isElement(child)) {
      cleanElement(child);
    }
  }
}

/** The browser's own tags for formatting become the tags the stored HTML uses. */
function renameLegacyTags(element) {
  const rename = { B: "strong", I: "em", STRIKE: "s", DEL: "s", DIV: null, FONT: null };
  if (!(element.tagName in rename)) {
    return;
  }
  const target = rename[element.tagName];
  if (element.tagName === "DIV") {
    const structural = element.hasAttribute("data-type") || element.parentElement?.matches('li[data-type="taskItem"]');
    if (structural) {
      return;
    }
    if (element.querySelector(BLOCK_CHILDREN)) {
      element.replaceWith(...element.childNodes);
    } else {
      swap(element, "p");
    }
    return;
  }
  if (element.tagName === "FONT") {
    const span = swap(element, "span");
    const color = element.getAttribute("color");
    if (color) {
      span.setAttribute(STYLE, `color: ${color};`);
    }
    span.removeAttribute("color");
    span.removeAttribute("size");
    span.removeAttribute("face");
    return;
  }
  swap(element, target);
}

function swap(element, tagName) {
  const replacement = document.createElement(tagName);
  for (const attribute of element.attributes) {
    replacement.setAttribute(attribute.name, attribute.value);
  }
  replacement.append(...element.childNodes);
  element.replaceWith(replacement);
  return replacement;
}

function cleanElement(element) {
  const keep = (KEEP_ATTRIBUTES[element.tagName] || []).map((name) => (name === "style" ? STYLE : name));
  const style = element.getAttribute(STYLE);
  for (const attribute of [...element.attributes]) {
    if (!keep.includes(attribute.name) && attribute.name !== STYLE) {
      element.removeAttribute(attribute.name);
    }
  }
  if (!STYLED.has(element.tagName) || !style) {
    if (!keep.includes(STYLE)) {
      element.removeAttribute(STYLE);
    }
  }
  if (element.tagName === "PRE") {
    for (const lineBreak of element.querySelectorAll("br")) {
      lineBreak.replaceWith(document.createTextNode("\n"));
    }
    if (!element.querySelector(":scope > code")) {
      const code = document.createElement("code");
      code.append(...element.childNodes);
      element.append(code);
    }
  }
  if (element.tagName === "MARK" && !element.hasAttribute("data-color")) {
    const colour = /background-color:\s*([^;]+)/.exec(element.getAttribute(STYLE) || "");
    if (colour) {
      element.setAttribute("data-color", colour[1].trim());
    }
  }
  if (element.tagName === "INPUT") {
    element.removeAttribute("checked");
    if (element.closest('li[data-checked="true"]')) {
      element.setAttribute("checked", "checked");
    }
  }
  if (element.tagName === "SPAN" && !element.attributes.length) {
    element.replaceWith(...element.childNodes);
    return;
  }
  if (element.tagName === "IMG") {
    element.removeAttribute("class");
  }
  // A browser leaves a <br> in an empty block so the caret can stand there. Stored, it is empty.
  if (/^(P|H[1-6]|LI|BLOCKQUOTE|TD|TH)$/.test(element.tagName) && element.childNodes.length === 1) {
    const only = element.firstChild;
    if (isElement(only) && only.tagName === "BR") {
      only.remove();
    }
  }
}

// --- pasted HTML ----------------------------------------------------------------------------

const PASTE_TAGS = new Set(["P", "H1", "H2", "H3", "H4", "H5", "H6", "UL", "OL", "LI", "BLOCKQUOTE", "PRE", "CODE", "STRONG", "B", "EM", "I", "U", "S", "STRIKE", "DEL", "A", "BR", "HR", "TABLE", "THEAD", "TBODY", "TR", "TD", "TH", "SUB", "SUP", "MARK"]);
const DROP_WITH_CONTENT = new Set(["SCRIPT", "STYLE", "IFRAME", "OBJECT", "EMBED", "TEMPLATE", "NOSCRIPT", "SVG", "MATH", "HEAD", "META", "LINK", "TITLE", "FORM", "INPUT", "BUTTON", "SELECT", "TEXTAREA", "VIDEO", "AUDIO", "IMG", "CANVAS"]);

/**
 * HTML from the clipboard, reduced to structure the editor understands. Styles, classes and ids
 * are dropped; links keep only a safe address. The server sanitizes again on every save.
 */
export function cleanPastedHtml(html) {
  const parsed = new DOMParser().parseFromString(html, "text/html");
  const out = document.createElement("div");
  appendClean(parsed.body, out);
  return out.innerHTML;
}

function appendClean(from, into) {
  for (const node of from.childNodes) {
    if (node.nodeType === Node.TEXT_NODE) {
      into.append(document.createTextNode(node.nodeValue));
    } else if (isElement(node)) {
      if (DROP_WITH_CONTENT.has(node.tagName.toUpperCase())) {
        continue;
      }
      if (!PASTE_TAGS.has(node.tagName)) {
        appendClean(node, into);
        continue;
      }
      const copy = document.createElement(node.tagName);
      if (node.tagName === "A") {
        const href = node.getAttribute("href") || "";
        if (/^(https?:|mailto:|tel:)/i.test(href.trim())) {
          copy.setAttribute("href", href.trim());
        }
      }
      if ((node.tagName === "TD" || node.tagName === "TH") && node.getAttribute("colspan")) {
        copy.setAttribute("colspan", node.getAttribute("colspan"));
      }
      appendClean(node, copy);
      into.append(copy);
    }
  }
}

// --- history --------------------------------------------------------------------------------

const MAX_STEPS = 150;
const MAX_CHARS = 8_000_000;

/**
 * Undo and redo by snapshots of the root. The browser's own undo cannot be trusted once the
 * document is changed by script (lists, tables, pictures), so every change goes through here.
 * Typing is grouped: a snapshot is taken when typing pauses, or before any command.
 */
export class History {
  constructor(root, onChange) {
    this.root = root;
    this.onChange = onChange;
    this.steps = [this.capture()];
    this.index = 0;
    this.timer = null;
  }

  capture() {
    return { html: this.root.innerHTML, selection: saveSelection(this.root) };
  }

  /** Records the document as it is now. Called after every change that should be one undo step. */
  commit() {
    clearTimeout(this.timer);
    this.timer = null;
    const now = this.capture();
    const top = this.steps[this.index];
    if (top.html === now.html) {
      top.selection = now.selection;
      return;
    }
    this.steps.length = this.index + 1;
    this.steps.push(now);
    this.index = this.steps.length - 1;
    while (this.steps.length > MAX_STEPS || this.size() > MAX_CHARS) {
      this.steps.shift();
      this.index--;
    }
    this.onChange();
  }

  /** Typing: take the snapshot when it pauses, so a burst of keys is one undo step. */
  schedule(delay = 450) {
    clearTimeout(this.timer);
    this.timer = setTimeout(() => this.commit(), delay);
  }

  size() {
    return this.steps.reduce((sum, step) => sum + step.html.length, 0);
  }

  canUndo() {
    return this.index > 0 || this.timer !== null;
  }

  canRedo() {
    return this.index < this.steps.length - 1;
  }

  undo() {
    this.commit();
    if (this.index > 0) {
      this.index--;
      this.apply(this.steps[this.index]);
    }
  }

  redo() {
    if (this.index < this.steps.length - 1) {
      this.index++;
      this.apply(this.steps[this.index]);
    }
  }

  apply(step) {
    setHtml(this.root, step.html);
    restoreSelection(this.root, step.selection);
    this.onChange(true);
  }

  /** Forgets all steps: a note was loaded, and that load is not something to undo. */
  reset() {
    clearTimeout(this.timer);
    this.timer = null;
    this.steps = [this.capture()];
    this.index = 0;
  }
}
