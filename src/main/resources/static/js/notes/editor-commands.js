// The editing commands of the notes editor: formatting, blocks, lists, checklists, tables,
// pictures and file chips. Each one changes the editing DOM inside the root, then asks the editor
// to record an undo step and to tell its owner the document changed.
//
// The browser's own commands (document.execCommand) do the work that is the same everywhere:
// bold, italic, underline, strike-through, alignment and the first conversion to a list. What the
// browser does badly (checklists, tables, code blocks, colours that must be stored as plain
// styles, pictures as blocks) is done here, directly on the DOM.

import { TEXT_BLOCKS, closestIn, currentTextBlock, ensureTrailingParagraph, isElement, leafBlocksIn, normalizeBlocks, placeCaret, rangeIn, selectRange, shapeTaskItem } from "./editor-core.js";

const ZERO_WIDTH = "\u200b";

export function createCommands({ root, history, changed, restoreSelection = () => {} }) {
  /** Runs a change: make sure the editor has focus and a selection, run it, record it. */
  function run(change, { css = false } = {}) {
    if (document.activeElement !== root) {
      // Focus was elsewhere (the link bar, a menu): focusing the editor moves its caret to the
      // start, so the selection the writer had is put back.
      root.focus({ preventScroll: true });
      restoreSelection();
    }
    if (!rangeIn(root)) {
      placeCaret(root.lastElementChild || root, true);
    }
    document.execCommand("styleWithCSS", false, css);
    document.execCommand("defaultParagraphSeparator", false, "p");
    change();
    settle();
  }

  /** After a change: empty blocks get their <br>, a paragraph follows the end, a step is recorded. */
  function settle() {
    normalizeBlocks(root);
    for (const block of root.querySelectorAll("p, h1, h2, h3, h4, h5, h6, li, blockquote, td, th")) {
      if (!block.firstChild) {
        block.append(document.createElement("br"));
      }
    }
    ensureTrailingParagraph(root);
    history.commit();
    changed();
  }

  const range = () => rangeIn(root);
  const inside = (selector) => {
    const r = range();
    return r ? closestIn(startNode(r), selector, root) : null;
  };

  /** The text node or element where the selection starts, for "is this active" checks. */
  function startNode(r) {
    if (!r.collapsed && r.startContainer.nodeType === Node.ELEMENT_NODE) {
      return r.startContainer.childNodes[r.startOffset] || r.startContainer;
    }
    return r.startContainer;
  }

  // --- inline marks ---------------------------------------------------------------------------

  /**
   * Wraps the selection in a new element made by `make`. The browser is asked to mark the text
   * with <font size=7>, which spans several blocks correctly, and each mark is then replaced by
   * the wanted element. With no text selected, an element holding a zero-width space is placed so
   * that what is typed next takes the style.
   */
  function wrapSelection(make) {
    const r = range();
    if (!r) {
      return [];
    }
    if (r.collapsed) {
      const element = make();
      element.append(document.createTextNode(ZERO_WIDTH));
      r.insertNode(element);
      const caret = document.createRange();
      caret.setStart(element.firstChild, 1);
      caret.collapse(true);
      selectRange(caret);
      return [element];
    }
    document.execCommand("fontSize", false, "7");
    const wrappers = [];
    for (const font of root.querySelectorAll('font[size="7"]')) {
      const element = make();
      element.append(...font.childNodes);
      font.replaceWith(element);
      wrappers.push(element);
    }
    if (wrappers.length) {
      const after = document.createRange();
      after.setStartBefore(wrappers[0]);
      after.setEndAfter(wrappers[wrappers.length - 1]);
      selectRange(after);
    }
    return wrappers;
  }

  /** Removes `selector` elements that touch the selection, keeping what is inside them. */
  function unwrapSelected(selector) {
    const r = range();
    if (!r) {
      return;
    }
    const found = [...root.querySelectorAll(selector)].filter((element) => r.intersectsNode(element));
    if (!found.length) {
      const own = closestIn(r.startContainer, selector, root);
      if (own) {
        found.push(own);
      }
    }
    for (const element of found) {
      element.replaceWith(...element.childNodes);
    }
    root.normalize();
  }

  function toggleInline(tag) {
    run(() => {
      if (inside(tag)) {
        unwrapSelected(tag);
      } else {
        wrapSelection(() => document.createElement(tag));
      }
    });
  }

  const format = (command) => run(() => document.execCommand(command));

  // --- colour, size, highlight, spacing -------------------------------------------------------

  /** Sets one inline style on the selected text, in plain spans: color, font-size. */
  function setInlineStyle(property, value) {
    run(() => {
      const spans = wrapSelection(() => document.createElement("span"));
      for (const span of spans) {
        span.style.setProperty(property, value);
        // A nested span already carrying the same property would hide the new one.
        for (const inner of span.querySelectorAll("span")) {
          inner.style.removeProperty(property);
          if (!inner.getAttribute("style")) {
            inner.removeAttribute("style");
          }
        }
      }
    });
  }

  function unsetInlineStyle(property) {
    run(() => {
      const r = range();
      if (!r) {
        return;
      }
      const spans = [...root.querySelectorAll("span[style]")].filter((span) => r.intersectsNode(span) || span.contains(r.startContainer));
      for (const span of spans) {
        span.style.removeProperty(property);
        if (!span.getAttribute("style")) {
          span.removeAttribute("style");
        }
        if (!span.attributes.length) {
          span.replaceWith(...span.childNodes);
        }
      }
    });
  }

  function highlight(color) {
    run(() => {
      const existing = inside("mark");
      if (existing && existing.getAttribute("data-color") === color) {
        unwrapSelected("mark");
        return;
      }
      if (existing) {
        unwrapSelected("mark");
      }
      for (const mark of wrapSelection(() => document.createElement("mark"))) {
        mark.setAttribute("data-color", color);
        mark.style.backgroundColor = color;
      }
    });
  }

  function unsetHighlight() {
    run(() => unwrapSelected("mark"));
  }

  function setLineHeight(value) {
    run(() => {
      for (const block of leafBlocksIn(root, range())) {
        block.style.lineHeight = value;
      }
    });
  }

  /** Alignment is a style on each selected block, set directly so it is not blocked as inline CSS. */
  function setTextAlign(alignment) {
    run(() => {
      for (const block of leafBlocksIn(root, range())) {
        if (alignment === "left") {
          block.style.removeProperty("text-align");
          if (!block.getAttribute("style")) {
            block.removeAttribute("style");
          }
        } else {
          block.style.textAlign = alignment;
        }
      }
    });
  }

  // --- blocks ---------------------------------------------------------------------------------

  function setParagraph() {
    run(() => {
      leaveTaskItem();
      document.execCommand("formatBlock", false, "p");
      unwrapSelected("blockquote");
    });
  }

  function toggleHeading(level) {
    run(() => {
      const current = inside("h1, h2, h3");
      leaveTaskItem();
      document.execCommand("formatBlock", false, current && current.tagName === "H" + level ? "p" : "h" + level);
    });
  }

  function toggleBlockquote() {
    run(() => {
      const quote = inside("blockquote");
      if (quote) {
        quote.replaceWith(...quote.childNodes);
      } else {
        document.execCommand("formatBlock", false, "blockquote");
      }
    });
  }

  function toggleCodeBlock() {
    run(() => {
      const pre = inside("pre");
      if (pre) {
        const lines = pre.textContent.replace(/\n$/, "").split("\n");
        const paragraphs = lines.map((line) => {
          const p = document.createElement("p");
          p.append(line ? document.createTextNode(line) : document.createElement("br"));
          return p;
        });
        pre.replaceWith(...paragraphs);
        placeCaret(paragraphs[0], true);
        return;
      }
      leaveTaskItem();
      document.execCommand("formatBlock", false, "pre");
    });
  }

  function setHorizontalRule() {
    run(() => {
      document.execCommand("insertHorizontalRule");
    });
  }

  // --- lists ----------------------------------------------------------------------------------

  function toggleList(command) {
    run(() => {
      if (inside('ul[data-type="taskList"]')) {
        taskListToParagraphs();
      }
      document.execCommand(command);
    });
  }

  /** A checklist item that is turned into a paragraph leaves its list, splitting it if needed. */
  function leaveTaskItem() {
    const item = inside('li[data-type="taskItem"]');
    if (item) {
      taskItemToParagraph(item);
    }
  }

  function taskItemToParagraph(item) {
    const list = item.parentElement;
    const body = item.querySelector(":scope > div");
    const nested = [...body.children].filter((child) => child.matches('ul[data-type="taskList"]'));
    const paragraphs = [...body.children].filter((child) => !nested.includes(child));
    const before = [];
    const after = [];
    let seen = false;
    for (const sibling of [...list.children]) {
      if (sibling === item) {
        seen = true;
      } else {
        (seen ? after : before).push(sibling);
      }
    }
    const tail = list.cloneNode(false);
    tail.append(...after);
    const moved = paragraphs.length ? paragraphs : [document.createElement("p")];
    if (!moved[0].firstChild) {
      moved[0].append(document.createElement("br"));
    }
    const pieces = [...moved, ...nested];
    list.after(...pieces);
    if (after.length) {
      pieces[pieces.length - 1].after(tail);
    }
    item.remove();
    if (!before.length) {
      list.remove();
    }
    placeCaret(moved[0], true);
  }

  function taskListToParagraphs() {
    for (const item of [...root.querySelectorAll('li[data-type="taskItem"]')].filter((entry) => range()?.intersectsNode(entry))) {
      if (item.isConnected) {
        taskItemToParagraph(item);
      }
    }
  }

  function toggleTaskList() {
    run(() => {
      if (inside('ul[data-type="taskList"]')) {
        taskListToParagraphs();
        return;
      }
      const list = inside("ul, ol");
      if (list) {
        convertListToTasks(list);
        return;
      }
      const blocks = leafBlocksIn(root, range()).filter((block) => block.matches("p, h1, h2, h3, h4, h5, h6, blockquote, pre"));
      if (!blocks.length) {
        return;
      }
      const taskList = newTaskList();
      blocks[0].before(taskList);
      for (const block of blocks) {
        const item = newTaskItem(false);
        const paragraph = item.querySelector("p");
        paragraph.replaceChildren(...block.childNodes);
        if (!paragraph.firstChild) {
          paragraph.append(document.createElement("br"));
        }
        taskList.append(item);
        block.remove();
      }
      placeCaret(taskList.lastElementChild.querySelector("p"), true);
    });
  }

  function newTaskList() {
    const list = document.createElement("ul");
    list.setAttribute("data-type", "taskList");
    return list;
  }

  function newTaskItem(checked) {
    const item = document.createElement("li");
    item.setAttribute("data-type", "taskItem");
    item.setAttribute("data-checked", checked ? "true" : "false");
    shapeTaskItem(item);
    return item;
  }

  function convertListToTasks(list) {
    const taskList = newTaskList();
    for (const li of [...list.children]) {
      const item = newTaskItem(false);
      const body = item.querySelector(":scope > div");
      body.replaceChildren();
      const hasBlocks = [...li.children].some((child) => child.matches("p, ul, ol"));
      if (hasBlocks) {
        body.append(...li.childNodes);
      } else {
        const p = document.createElement("p");
        p.append(...li.childNodes);
        body.append(p);
      }
      for (const nested of body.querySelectorAll(":scope > ul, :scope > ol")) {
        const converted = newTaskList();
        converted.append(...[...nested.children].map((child) => {
          const nestedItem = newTaskItem(false);
          const nestedBody = nestedItem.querySelector(":scope > div");
          nestedBody.replaceChildren();
          const p = document.createElement("p");
          p.append(...child.childNodes);
          nestedBody.append(p);
          return nestedItem;
        }));
        nested.replaceWith(converted);
      }
      taskList.append(item);
    }
    list.replaceWith(taskList);
    placeCaret(taskList.lastElementChild.querySelector("p"), true);
  }

  /** The checkbox of a checklist item was clicked: keep data-checked and the box in step. */
  function setTaskChecked(item, checked) {
    item.setAttribute("data-checked", checked ? "true" : "false");
    const box = item.querySelector(":scope > label > input");
    if (box) {
      box.checked = checked;
      box.toggleAttribute("checked", checked);
    }
    history.commit();
    changed();
  }

  /** Enter in a checklist item starts the next item; Enter in an empty one ends the checklist. */
  function splitTaskItem() {
    const item = inside('li[data-type="taskItem"]');
    const r = range();
    if (!item || !r) {
      return false;
    }
    const paragraph = closestIn(r.startContainer, "p", root);
    if (!paragraph || paragraph.parentElement.parentElement !== item) {
      return false;
    }
    const empty = !paragraph.textContent.replace(ZERO_WIDTH, "").trim() && item.querySelectorAll("p").length === 1;
    if (empty) {
      taskItemToParagraph(item);
      settle();
      return true;
    }
    if (!r.collapsed) {
      r.deleteContents();
    }
    const tail = document.createRange();
    tail.setStart(r.endContainer, r.endOffset);
    tail.setEnd(paragraph, paragraph.childNodes.length);
    const rest = tail.extractContents();
    const next = newTaskItem(false);
    const nextParagraph = next.querySelector("p");
    nextParagraph.replaceChildren(rest);
    if (!nextParagraph.textContent && !nextParagraph.querySelector("img, br")) {
      nextParagraph.append(document.createElement("br"));
    }
    if (!paragraph.firstChild) {
      paragraph.append(document.createElement("br"));
    }
    item.after(next);
    placeCaret(nextParagraph, false);
    settle();
    return true;
  }

  /** Enter on an empty last paragraph of a quotation leaves the quotation. */
  function leaveBlockquote() {
    const quote = inside("blockquote");
    const r = range();
    if (!quote || !r || !r.collapsed) {
      return false;
    }
    const paragraph = closestIn(r.startContainer, "p", root);
    if (!paragraph || paragraph.parentElement !== quote || paragraph !== quote.lastElementChild) {
      return false;
    }
    if (paragraph.textContent.replaceAll("\u200b", "").trim() || quote.children.length < 1) {
      return false;
    }
    quote.after(paragraph);
    if (!quote.children.length) {
      quote.remove();
    }
    placeCaret(paragraph);
    settle();
    return true;
  }

  /** Backspace at the very start of a checklist item turns the item back into a paragraph. */
  function backspaceInTaskItem() {
    const r = range();
    const item = inside('li[data-type="taskItem"]');
    if (!r || !r.collapsed || !item) {
      return false;
    }
    const first = item.querySelector(":scope > div > p");
    if (!first || !first.contains(r.startContainer)) {
      return false;
    }
    const probe = document.createRange();
    probe.selectNodeContents(first);
    probe.setEnd(r.startContainer, r.startOffset);
    if (probe.toString().replace(ZERO_WIDTH, "") !== "") {
      return false;
    }
    taskItemToParagraph(item);
    settle();
    return true;
  }

  /** Tab and Shift+Tab in a list item: one level in, or one level out. */
  function indentList(deeper) {
    const taskItem = inside('li[data-type="taskItem"]');
    if (taskItem) {
      run(() => (deeper ? sinkTaskItem(taskItem) : liftTaskItem(taskItem)));
      return true;
    }
    if (inside("li")) {
      run(() => document.execCommand(deeper ? "indent" : "outdent"));
      return true;
    }
    return false;
  }

  function sinkTaskItem(item) {
    const previous = item.previousElementSibling;
    if (!previous) {
      return;
    }
    const body = previous.querySelector(":scope > div");
    let nested = [...body.children].find((child) => child.matches('ul[data-type="taskList"]'));
    if (!nested) {
      nested = newTaskList();
      body.append(nested);
    }
    nested.append(item);
    placeCaret(item.querySelector("p"), true);
  }

  function liftTaskItem(item) {
    const outerBody = item.parentElement.parentElement;
    const parentItem = outerBody && outerBody.parentElement;
    if (!parentItem || !parentItem.matches('li[data-type="taskItem"]')) {
      return;
    }
    const siblingsAfter = [];
    for (let sibling = item.nextElementSibling; sibling; sibling = sibling.nextElementSibling) {
      siblingsAfter.push(sibling);
    }
    if (siblingsAfter.length) {
      const nestedBody = item.querySelector(":scope > div");
      let nested = [...nestedBody.children].find((child) => child.matches('ul[data-type="taskList"]'));
      if (!nested) {
        nested = newTaskList();
        nestedBody.append(nested);
      }
      nested.append(...siblingsAfter);
    }
    const emptied = item.parentElement;
    parentItem.after(item);
    if (!emptied.children.length) {
      emptied.remove();
    }
    placeCaret(item.querySelector("p"), true);
  }

  // --- links ----------------------------------------------------------------------------------

  /** The link around the selection, not counting a file chip. */
  function currentLink() {
    return inside("a[href]:not([data-note-file])");
  }

  function applyLink(href) {
    run(() => {
      const existing = currentLink();
      if (!href) {
        if (existing) {
          unwrapSelected("a[href]:not([data-note-file])");
        }
        return;
      }
      const external = !href.startsWith("/");
      const anchors = existing && range().collapsed ? [existing] : [];
      if (!anchors.length) {
        const r = range();
        if (r.collapsed) {
          const link = document.createElement("a");
          link.textContent = href;
          r.insertNode(link);
          anchors.push(link);
        } else {
          document.execCommand("createLink", false, href);
          anchors.push(...[...root.querySelectorAll("a[href]")].filter((a) => a.getAttribute("href") === href && !a.hasAttribute("data-note-file")));
        }
      }
      for (const anchor of anchors) {
        anchor.setAttribute("href", href);
        if (external) {
          anchor.setAttribute("target", "_blank");
          anchor.setAttribute("rel", "noopener noreferrer nofollow");
        } else {
          anchor.removeAttribute("target");
          anchor.removeAttribute("rel");
        }
      }
    });
  }

  // --- blocks that sit between paragraphs: pictures, files, tables -----------------------------

  /** Inserts an element as a block after the one holding the caret, replacing an empty paragraph. */
  function insertBlock(element, caretInside = null) {
    const r = range();
    let top = r ? r.startContainer : null;
    while (top && top.parentNode !== root) {
      top = top.parentNode;
    }
    if (top && isElement(top)) {
      if (top.matches("p") && !top.textContent.trim() && !top.querySelector("img, a[data-note-file]")) {
        top.replaceWith(element);
      } else {
        top.after(element);
      }
    } else {
      root.append(element);
    }
    ensureTrailingParagraph(root);
    if (caretInside) {
      placeCaret(caretInside);
    } else {
      placeCaret(element.nextElementSibling || root.lastElementChild);
    }
  }

  function insertImage({ src, alt = "" }) {
    run(() => {
      const image = document.createElement("img");
      image.setAttribute("src", src);
      image.setAttribute("alt", alt);
      image.setAttribute("data-align", "left");
      image.setAttribute("contenteditable", "false");
      image.setAttribute("draggable", "false");
      insertBlock(image);
    });
  }

  function insertFile({ href, name, sizeText }) {
    run(() => {
      const chip = document.createElement("a");
      chip.setAttribute("href", href);
      chip.setAttribute("data-note-file", "true");
      chip.setAttribute("data-file-name", name);
      chip.setAttribute("data-file-size", sizeText);
      chip.setAttribute("contenteditable", "false");
      chip.setAttribute("draggable", "false");
      chip.setAttribute("target", "_blank");
      chip.setAttribute("rel", "noopener noreferrer");
      chip.className = "note-file-chip";
      chip.textContent = sizeText ? `${name} · ${sizeText}` : name;
      insertBlock(chip);
    });
  }

  function insertTable({ rows = 3, cols = 3, withHeaderRow = true } = {}) {
    run(() => {
      const table = document.createElement("table");
      const body = document.createElement("tbody");
      for (let row = 0; row < rows; row++) {
        const tr = document.createElement("tr");
        for (let col = 0; col < cols; col++) {
          tr.append(newCell(withHeaderRow && row === 0 ? "th" : "td"));
        }
        body.append(tr);
      }
      table.append(body);
      insertBlock(table, table.querySelector("p"));
    });
  }

  function newCell(tag) {
    const cell = document.createElement(tag);
    const paragraph = document.createElement("p");
    paragraph.append(document.createElement("br"));
    cell.append(paragraph);
    return cell;
  }

  const cellParts = () => {
    const cell = inside("td, th");
    return cell ? { cell, row: cell.parentElement, table: cell.closest("table") } : null;
  };

  function addRow() {
    run(() => {
      const parts = cellParts();
      if (!parts) return;
      const row = document.createElement("tr");
      for (let i = 0; i < parts.row.children.length; i++) {
        row.append(newCell("td"));
      }
      parts.row.after(row);
      placeCaret(row.firstElementChild.querySelector("p"));
    });
  }

  function deleteRow() {
    run(() => {
      const parts = cellParts();
      if (!parts) return;
      if (parts.table.rows.length <= 1) {
        removeTable(parts.table);
        return;
      }
      const next = parts.row.nextElementSibling || parts.row.previousElementSibling;
      parts.row.remove();
      placeCaret(next.firstElementChild.querySelector("p") || next.firstElementChild);
    });
  }

  function addColumn() {
    run(() => {
      const parts = cellParts();
      if (!parts) return;
      const index = parts.cell.cellIndex;
      for (const row of parts.table.rows) {
        const reference = row.cells[index] || row.lastElementChild;
        reference.after(newCell(reference.tagName.toLowerCase()));
      }
      const widths = parts.table.querySelector("colgroup");
      if (widths) {
        widths.remove();
      }
      placeCaret(parts.row.cells[index + 1].querySelector("p"));
    });
  }

  function deleteColumn() {
    run(() => {
      const parts = cellParts();
      if (!parts) return;
      const index = parts.cell.cellIndex;
      if (parts.row.cells.length <= 1) {
        removeTable(parts.table);
        return;
      }
      for (const row of parts.table.rows) {
        row.cells[index]?.remove();
      }
      parts.table.querySelector("colgroup")?.remove();
      const next = parts.row.cells[Math.min(index, parts.row.cells.length - 1)];
      placeCaret(next.querySelector("p") || next);
    });
  }

  function toggleHeaderRow() {
    run(() => {
      const parts = cellParts();
      if (!parts) return;
      const first = parts.table.rows[0];
      const toHeader = ![...first.cells].every((cell) => cell.tagName === "TH");
      for (const cell of [...first.cells]) {
        const replacement = document.createElement(toHeader ? "th" : "td");
        for (const attribute of cell.attributes) {
          replacement.setAttribute(attribute.name, attribute.value);
        }
        replacement.append(...cell.childNodes);
        cell.replaceWith(replacement);
      }
    });
  }

  function removeTable(table) {
    const after = table.nextElementSibling;
    table.remove();
    ensureTrailingParagraph(root);
    placeCaret(after || root.lastElementChild);
  }

  function deleteTable() {
    run(() => {
      const parts = cellParts();
      if (parts) removeTable(parts.table);
    });
  }

  /** Tab moves to the next cell (a new row after the last one); Shift+Tab to the previous. */
  function moveInTable(forward) {
    const parts = cellParts();
    if (!parts) {
      return false;
    }
    const cells = [...parts.table.querySelectorAll("td, th")];
    const index = cells.indexOf(parts.cell);
    let target = cells[index + (forward ? 1 : -1)];
    if (!target && forward) {
      addRow();
      return true;
    }
    if (target) {
      placeCaret(target.querySelector("p") || target, false);
    }
    return true;
  }

  function clearFormatting() {
    run(() => {
      document.execCommand("removeFormat");
      unwrapSelected("mark, code:not(pre code), a[href]:not([data-note-file]), u, s, strong, em, b, i, sub, sup");
      for (const block of leafBlocksIn(root, range())) {
        block.removeAttribute("style");
        for (const styled of block.querySelectorAll("[style]")) {
          styled.removeAttribute("style");
        }
      }
      leaveTaskItem();
      const heading = inside("h1, h2, h3, h4, h5, h6, blockquote, pre");
      if (heading) {
        document.execCommand("formatBlock", false, "p");
        unwrapSelected("blockquote");
      }
    });
  }

  return {
    run,
    settle,
    inside,
    format,
    toggleInline,
    setInlineStyle,
    unsetInlineStyle,
    highlight,
    unsetHighlight,
    setLineHeight,
    setTextAlign,
    setParagraph,
    toggleHeading,
    toggleBlockquote,
    toggleCodeBlock,
    setHorizontalRule,
    toggleList,
    toggleTaskList,
    setTaskChecked,
    splitTaskItem,
    leaveBlockquote,
    backspaceInTaskItem,
    indentList,
    currentLink,
    applyLink,
    insertImage,
    insertFile,
    insertTable,
    addRow,
    deleteRow,
    addColumn,
    deleteColumn,
    toggleHeaderRow,
    deleteTable,
    moveInTable,
    clearFormatting,
    TEXT_BLOCKS,
    currentTextBlock: () => currentTextBlock(root),
  };
}
