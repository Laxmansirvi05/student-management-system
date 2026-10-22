import { fill, h } from "../dom.js";
import { createCommands } from "./editor-commands.js";
import { History, cleanPastedHtml, closestIn, isElement, placeCaret, prepareForEditing, rangeIn, selectRange, serialize } from "./editor-core.js";
import { createToolbar } from "./editor-toolbar.js";
import { openDrawingDialog } from "./drawing-dialog.js";
import { formatBytes } from "./note-text.js";
import { setHtml } from "./safe-html.js";
import { symbol } from "./symbols.js";

// The one rich-text editor, shared by the notes workspace and the robot's quick-note composer:
// one editing surface, not two that drift apart. Content in and out is a plain HTML string, so
// nothing upstream (autosave, the API, the read-only shared view) needs to know what it is built on.

const IMAGE_ACCEPT = "image/jpeg,image/png,image/gif";
const FILE_ACCEPT = ".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.md,.zip,application/pdf,text/plain,text/csv,text/markdown,application/zip";
const ALIGNMENTS = [
  { value: "left", icon: "format_align_left", label: "Align left" },
  { value: "center", icon: "format_align_center", label: "Align centre" },
  { value: "right", icon: "format_align_right", label: "Align right" },
];
const MIN_IMAGE_WIDTH = 80;
const URL_LIKE = /^(https?:\/\/[^\s]+|www\.[^\s]+)$/i;
const MOD = /Mac|iPhone|iPad/.test(navigator.platform) ? "metaKey" : "ctrlKey";

/**
 * Creates an editor. Options: content (HTML), onChange(html), onUpload(file) → {url, kind, name,
 * sizeBytes}, onUploadError(error), placeholder, autoFocus, compact, editable, minHeight.
 * Returns { element, getHTML, setContent, focus, flush, setEditable, destroy }.
 */
export function createRichEditor({ content = "", onChange = () => {}, onUpload, onUploadError = () => {}, placeholder = "Start writing…", autoFocus = false, compact = false, editable = true, minHeight = 120 }) {
  const root = h("div.note-rich-editor-content", {
    contenteditable: editable ? "true" : "false",
    role: "textbox",
    "aria-multiline": "true",
    "aria-label": "Note",
    spellcheck: "true",
    "data-placeholder": placeholder,
    style: { "min-height": minHeight + "px" },
  });

  let lastSelection = null;
  let emitTimer = null;
  let emitted = null;
  let uploadCount = 0;
  let selectedIsland = null;
  let stateFrame = 0;
  let destroyed = false;

  const history = new History(root, (restored) => {
    if (restored) {
      selectIsland(null);
      emit(true);
    }
    scheduleState();
  });
  const commands = createCommands({
    root,
    history,
    changed: () => {
      emit();
      updatePlaceholder();
      scheduleState();
    },
    restoreSelection: () => {
      if (lastSelection && lastSelection.startContainer.isConnected) {
        try {
          selectRange(lastSelection);
        } catch {
          /* the remembered selection no longer exists */
        }
      }
    },
  });

  // --- loading and saving ---------------------------------------------------------------------

  function setContent(html) {
    setHtml(root, html || "<p></p>");
    prepareForEditing(root);
    history.reset();
    selectIsland(null);
    emitted = serialize(root);
    updatePlaceholder();
    scheduleState();
  }

  function getHTML() {
    return serialize(root);
  }

  /** Sends the current HTML to the owner now, if it changed since the last time. */
  function emit(force = false) {
    clearTimeout(emitTimer);
    emitTimer = null;
    const html = serialize(root);
    if (force || html !== emitted) {
      emitted = html;
      onChange(html);
    }
  }

  const scheduleEmit = () => {
    clearTimeout(emitTimer);
    emitTimer = setTimeout(() => emit(), 120);
  };

  function updatePlaceholder() {
    const first = root.firstElementChild;
    const empty = root.children.length === 1 && first.matches("p") && !first.textContent.replaceAll("\u200b", "") && !first.querySelector("img");
    root.querySelectorAll("p.is-editor-empty").forEach((p) => p.classList.remove("is-editor-empty"));
    if (empty) {
      first.classList.add("is-editor-empty");
    }
  }

  // --- toolbar state --------------------------------------------------------------------------

  function queryState(command) {
    try {
      return document.queryCommandState(command);
    } catch {
      return false;
    }
  }

  function computeState() {
    const active = rangeIn(root) !== null;
    const inside = (selector) => active && Boolean(commands.inside(selector));
    const block = active ? commands.currentTextBlock() : null;
    const heading = inside("h1") ? 1 : inside("h2") ? 2 : inside("h3") ? 3 : 0;
    let align = "left";
    if (block) {
      const value = getComputedStyle(block).textAlign;
      align = value === "center" || value === "right" || value === "justify" ? value : "left";
    }
    return {
      bold: active && queryState("bold"),
      italic: active && queryState("italic"),
      underline: active && queryState("underline"),
      strike: active && queryState("strikeThrough"),
      code: inside("code:not(pre code)"),
      codeBlock: inside("pre"),
      highlight: inside("mark"),
      blockquote: inside("blockquote"),
      link: Boolean(active && commands.currentLink()),
      bullet: inside('ul:not([data-type="taskList"])'),
      ordered: inside("ol"),
      task: inside('ul[data-type="taskList"]'),
      table: inside("td, th"),
      heading,
      align,
      canUndo: history.canUndo(),
      canRedo: history.canRedo(),
    };
  }

  function scheduleState() {
    if (!toolbar || stateFrame) return;
    stateFrame = requestAnimationFrame(() => {
      stateFrame = 0;
      if (!destroyed) toolbar.setState(computeState());
    });
  }

  // --- uploads --------------------------------------------------------------------------------

  async function uploadAndInsert(file) {
    uploadCount++;
    toolbar?.setUploading(true);
    try {
      const uploaded = await onUpload(file);
      if (uploaded.kind === "image") {
        commands.insertImage({ src: uploaded.url, alt: uploaded.name });
      } else {
        commands.insertFile({ href: uploaded.url, name: uploaded.name, sizeText: formatBytes(uploaded.sizeBytes) });
      }
    } catch (failure) {
      onUploadError(failure);
    } finally {
      uploadCount--;
      if (uploadCount === 0) toolbar?.setUploading(false);
    }
  }

  async function uploadMany(files) {
    for (const file of files) {
      await uploadAndInsert(file);
    }
  }

  const imageInput = h("input.nt-hidden", { type: "file", accept: IMAGE_ACCEPT, tabindex: "-1", "aria-hidden": "true" });
  const fileInput = h("input.nt-hidden", { type: "file", accept: FILE_ACCEPT, tabindex: "-1", "aria-hidden": "true" });
  for (const input of [imageInput, fileInput]) {
    input.addEventListener("change", async () => {
      const file = input.files[0];
      input.value = "";
      if (file) await uploadAndInsert(file);
    });
  }

  // --- the link bar ---------------------------------------------------------------------------

  const linkInput = h("input.nt-link__input", { type: "url", inputmode: "url", "aria-label": "Link address", placeholder: "https://example.com" });
  const linkBar = h(
    "div.nt-link",
    { hidden: true },
    symbol("link"),
    linkInput,
    h("button.nt-btn.nt-btn--primary.nt-btn--small", { type: "button", onclick: () => applyLink() }, "Apply"),
    h("button.nt-tool.nt-tool--small", { type: "button", "aria-label": "Cancel link", onclick: () => closeLinkBar() }, symbol("close")),
  );
  linkInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      applyLink();
    } else if (event.key === "Escape") {
      event.preventDefault();
      closeLinkBar();
    }
  });

  function openLinkBar() {
    const link = commands.currentLink();
    linkInput.value = link ? link.getAttribute("href") : "";
    linkBar.hidden = false;
    linkInput.focus();
  }

  function closeLinkBar() {
    linkBar.hidden = true;
    root.focus({ preventScroll: true });
  }

  function applyLink() {
    const href = linkInput.value.trim();
    linkBar.hidden = true;
    // A bare "example.com" is what people actually type; without this it would be stored as a
    // relative path and silently lead nowhere inside the app.
    const address = !href || /^(https?:|mailto:|tel:|\/)/i.test(href) ? href : "https://" + href;
    commands.applyLink(address);
  }

  // --- pictures and file chips ----------------------------------------------------------------

  const tools = h("div.nt-island-tools", { "data-nt-ui": "", hidden: true });
  const resizeHandle = h("span.nt-resize-handle", { "data-nt-ui": "", hidden: true, role: "presentation", title: "Drag to resize" });

  function selectIsland(island) {
    if (selectedIsland && selectedIsland !== island) {
      selectedIsland.classList.remove("is-selected");
    }
    selectedIsland = island;
    if (!island) {
      tools.hidden = true;
      resizeHandle.hidden = true;
      return;
    }
    island.classList.add("is-selected");
    drawIslandTools();
    positionIslandTools();
  }

  function drawIslandTools() {
    const island = selectedIsland;
    const isImage = island.tagName === "IMG";
    const align = island.getAttribute("data-align") || "left";
    const buttons = [];
    if (isImage) {
      for (const option of ALIGNMENTS) {
        buttons.push(
          h(
            "button.nt-tool.nt-tool--small" + (align === option.value ? ".is-on" : ""),
            {
              type: "button",
              "aria-label": option.label,
              "aria-pressed": String(align === option.value),
              onmousedown: (event) => event.preventDefault(),
              onclick: () => {
                island.setAttribute("data-align", option.value);
                afterIslandChange();
              },
            },
            symbol(option.icon),
          ),
        );
      }
      buttons.push(
        h("button.nt-tool.nt-tool--small", { type: "button", "aria-label": "Edit alt text", onmousedown: (event) => event.preventDefault(), onclick: editAlt }, symbol("accessibility_new")),
      );
    } else {
      buttons.push(
        h("a.nt-tool.nt-tool--small", { href: island.getAttribute("href"), target: "_blank", rel: "noopener noreferrer", "aria-label": "Open file", title: "Open file" }, symbol("open_in_new")),
      );
    }
    buttons.push(
      h(
        "button.nt-tool.nt-tool--small.nt-tool--danger",
        { type: "button", "aria-label": isImage ? "Remove image" : "Remove file", onmousedown: (event) => event.preventDefault(), onclick: removeIsland },
        symbol("delete"),
      ),
    );
    fill(tools, buttons);
    tools.hidden = false;
    resizeHandle.hidden = !isImage;
  }

  function positionIslandTools() {
    if (!selectedIsland) return;
    const surface = root.parentElement.getBoundingClientRect();
    const box = selectedIsland.getBoundingClientRect();
    tools.style.top = Math.max(4, box.top - surface.top + 8) + "px";
    tools.style.left = Math.max(4, box.right - surface.left - tools.offsetWidth - 8) + "px";
    resizeHandle.style.top = box.bottom - surface.top - 18 + "px";
    resizeHandle.style.left = box.right - surface.left - 18 + "px";
  }

  function afterIslandChange() {
    history.commit();
    emit();
    drawIslandTools();
    positionIslandTools();
  }

  function editAlt() {
    const next = window.prompt("Describe this image (for screen readers)", selectedIsland.getAttribute("alt") || "");
    if (next !== null) {
      selectedIsland.setAttribute("alt", next);
      afterIslandChange();
    }
  }

  function removeIsland() {
    const island = selectedIsland;
    const next = island.nextElementSibling;
    selectIsland(null);
    island.remove();
    commands.settle();
    placeCaret(next || root.lastElementChild);
  }

  resizeHandle.addEventListener("pointerdown", (event) => {
    const image = selectedIsland;
    if (!image || image.tagName !== "IMG") return;
    event.preventDefault();
    const startX = event.clientX;
    const startWidth = image.getBoundingClientRect().width;
    const maxWidth = root.getBoundingClientRect().width;
    resizeHandle.setPointerCapture(event.pointerId);
    const move = (moveEvent) => {
      // Written straight to the element during the drag; the width attribute is set once on release,
      // so a resize is one undo step rather than one per pointer event.
      image.style.width = Math.round(Math.min(Math.max(startWidth + (moveEvent.clientX - startX), MIN_IMAGE_WIDTH), maxWidth)) + "px";
      positionIslandTools();
    };
    const up = () => {
      resizeHandle.removeEventListener("pointermove", move);
      resizeHandle.removeEventListener("pointerup", up);
      const width = Math.round(image.getBoundingClientRect().width);
      image.style.removeProperty("width");
      if (!image.getAttribute("style")) image.removeAttribute("style");
      image.setAttribute("width", String(width));
      afterIslandChange();
    };
    resizeHandle.addEventListener("pointermove", move);
    resizeHandle.addEventListener("pointerup", up);
  });

  // --- tables: column widths ------------------------------------------------------------------

  const EDGE = 5;
  let columnDrag = null;

  function edgeCell(event) {
    const cell = event.target.closest?.("td, th");
    if (!cell || !root.contains(cell)) return null;
    const box = cell.getBoundingClientRect();
    return Math.abs(box.right - event.clientX) <= EDGE ? cell : null;
  }

  root.addEventListener("mousemove", (event) => {
    if (columnDrag) return;
    root.classList.toggle("nt-col-resize", Boolean(edgeCell(event)));
  });

  root.addEventListener("mousedown", (event) => {
    const cell = edgeCell(event);
    if (!cell || !editable) return;
    event.preventDefault();
    const table = cell.closest("table");
    const index = cell.cellIndex;
    const first = table.rows[0];
    columnDrag = { table, index, startX: event.clientX, widths: [...first.cells].map((entry) => Math.round(entry.getBoundingClientRect().width)) };
    ensureColgroup(table, columnDrag.widths);
    const move = (moveEvent) => {
      const next = Math.max(40, columnDrag.widths[index] + moveEvent.clientX - columnDrag.startX);
      setColumnWidth(table, index, Math.round(next));
    };
    const up = () => {
      document.removeEventListener("mousemove", move);
      document.removeEventListener("mouseup", up);
      columnDrag = null;
      root.classList.remove("nt-col-resize");
      commands.settle();
    };
    document.addEventListener("mousemove", move);
    document.addEventListener("mouseup", up);
  });

  function ensureColgroup(table, widths) {
    let group = table.querySelector("colgroup");
    if (!group || group.children.length !== widths.length) {
      group?.remove();
      group = document.createElement("colgroup");
      for (const width of widths) {
        const column = document.createElement("col");
        column.style.width = width + "px";
        group.append(column);
      }
      table.prepend(group);
      for (const row of table.rows) {
        [...row.cells].forEach((cell, i) => cell.setAttribute("colwidth", String(widths[i] ?? widths[widths.length - 1])));
      }
    }
  }

  function setColumnWidth(table, index, width) {
    const group = table.querySelector("colgroup");
    group.children[index].style.width = width + "px";
    for (const row of table.rows) {
      row.cells[index]?.setAttribute("colwidth", String(width));
    }
    const total = [...group.children].reduce((sum, column) => sum + parseInt(column.style.width, 10), 0);
    table.style.width = total + "px";
  }

  // --- input rules (typed shortcuts) ----------------------------------------------------------

  /** Text as typed: the browser writes a trailing space as a non-breaking one, and marks hold zero-width ones. */
  const plain = (text) => text.replaceAll("\u200b", "").replaceAll("\u00a0", " ");

  /** The text of the caret's block before the caret, and the block, when it is a plain paragraph. */
  function textBeforeCaret() {
    const range = rangeIn(root);
    if (!range || !range.collapsed) return null;
    const block = closestIn(range.startContainer, "p", root);
    if (!block || block.closest("pre, code")) return null;
    const probe = document.createRange();
    probe.selectNodeContents(block);
    probe.setEnd(range.startContainer, range.startOffset);
    return { block, text: plain(probe.toString()), range };
  }

  function clearBlockText(block) {
    block.replaceChildren(document.createElement("br"));
    placeCaret(block);
  }

  const topLevel = (block) => block.parentElement === root || block.parentElement.matches("td, th, blockquote");

  /** `# `, `- `, `1. `, `> `, `[] ` and triple backticks at the start of a paragraph. */
  function blockRule() {
    const before = textBeforeCaret();
    if (!before || !before.text.endsWith(" ") || !topLevel(before.block)) return false;
    const marker = before.text.slice(0, -1);
    if (plain(before.block.textContent) !== before.text) return false;
    const run = (command) => {
      clearBlockText(before.block);
      command();
    };
    if (/^#{1,3}$/.test(marker)) return run(() => commands.toggleHeading(marker.length)) || true;
    if (/^[-*+]$/.test(marker)) return run(() => commands.toggleList("insertUnorderedList")) || true;
    if (/^\d+\.$/.test(marker)) return run(() => commands.toggleList("insertOrderedList")) || true;
    if (marker === ">") return run(() => commands.toggleBlockquote()) || true;
    if (/^\[( |x)?\]$/i.test(marker)) {
      const checked = /x/i.test(marker);
      clearBlockText(before.block);
      commands.toggleTaskList();
      if (checked) commands.setTaskChecked(commands.inside('li[data-type="taskItem"]'), true);
      return true;
    }
    if (marker === "```") return run(() => commands.toggleCodeBlock()) || true;
    return false;
  }

  /** `---` on its own line becomes a divider. */
  function ruleRule() {
    const before = textBeforeCaret();
    if (!before || before.text !== "---" || !topLevel(before.block) || plain(before.block.textContent) !== "---") return false;
    const rule = document.createElement("hr");
    before.block.before(rule);
    clearBlockText(before.block);
    commands.settle();
    return true;
  }

  const INLINE_RULES = [
    { pattern: /(^|[^*])\*\*([^*\s](?:[^*]*[^*\s])?)\*\*$/, tag: "strong", marker: 2 },
    { pattern: /(^|[^_])__([^_\s](?:[^_]*[^_\s])?)__$/, tag: "strong", marker: 2 },
    { pattern: /(^|[^*])\*([^*\s](?:[^*]*[^*\s])?)\*$/, tag: "em", marker: 1 },
    { pattern: /(^|[^_\w])_([^_\s](?:[^_]*[^_\s])?)_$/, tag: "em", marker: 1 },
    { pattern: /(^|[^~])~~([^~\s](?:[^~]*[^~\s])?)~~$/, tag: "s", marker: 2 },
    { pattern: /(^|[^`])`([^`]+)`$/, tag: "code", marker: 1 },
    { pattern: /(^|[^=])==([^=\s](?:[^=]*[^=\s])?)==$/, tag: "mark", marker: 2 },
  ];

  /** `**bold**`, `*italic*`, `~~strike~~`, `` `code` `` and `==highlight==` as they are typed. */
  function inlineRule() {
    const range = rangeIn(root);
    if (!range || !range.collapsed || range.startContainer.nodeType !== Node.TEXT_NODE) return false;
    if (closestIn(range.startContainer, "pre, code", root)) return false;
    const node = range.startContainer;
    const text = node.data.slice(0, range.startOffset);
    for (const rule of INLINE_RULES) {
      const match = rule.pattern.exec(text);
      if (!match) continue;
      const inner = match[2];
      const start = text.length - inner.length - rule.marker * 2;
      const element = document.createElement(rule.tag);
      if (rule.tag === "mark") {
        element.setAttribute("data-color", "#fef08a");
        element.style.backgroundColor = "#fef08a";
      }
      element.textContent = inner;
      const replace = document.createRange();
      replace.setStart(node, start);
      replace.setEnd(node, range.startOffset);
      replace.deleteContents();
      replace.insertNode(element);
      // The caret goes after the new element, in text of its own, so typing continues unformatted.
      const after = document.createTextNode("\u200b");
      element.after(after);
      const caret = document.createRange();
      caret.setStart(after, 1);
      caret.collapse(true);
      selectRange(caret);
      return true;
    }
    return false;
  }

  /** A web address followed by a space becomes a link. */
  function autolink() {
    const range = rangeIn(root);
    if (!range || !range.collapsed || range.startContainer.nodeType !== Node.TEXT_NODE) return false;
    if (closestIn(range.startContainer, "a, pre, code", root)) return false;
    const node = range.startContainer;
    const before = node.data.slice(0, range.startOffset - 1);
    const token = /(\S+)$/.exec(before);
    if (!token || !URL_LIKE.test(token[1])) return false;
    const link = document.createElement("a");
    const href = /^https?:/i.test(token[1]) ? token[1] : "https://" + token[1];
    link.setAttribute("href", href);
    link.setAttribute("target", "_blank");
    link.setAttribute("rel", "noopener noreferrer nofollow");
    const target = document.createRange();
    target.setStart(node, before.length - token[1].length);
    target.setEnd(node, before.length);
    target.surroundContents(link);
    const caret = document.createRange();
    caret.setStart(node.nextSibling || node, node.nextSibling ? Math.min(1, node.nextSibling.length) : 0);
    caret.collapse(true);
    selectRange(caret);
    return true;
  }

  // --- events ---------------------------------------------------------------------------------

  root.addEventListener("input", (event) => {
    if (event.inputType === "insertText" && event.data === " ") {
      if (blockRule() || autolink()) {
        commands.settle();
        return;
      }
    }
    if (event.inputType === "insertText" && event.data === "-" && ruleRule()) {
      return;
    }
    if (event.inputType === "insertText" && event.data && /[*_~`=]/.test(event.data) && inlineRule()) {
      commands.settle();
      return;
    }
    history.schedule();
    scheduleEmit();
    updatePlaceholder();
    scheduleState();
  });

  root.addEventListener("beforeinput", (event) => {
    if (event.inputType === "historyUndo") {
      event.preventDefault();
      history.undo();
    } else if (event.inputType === "historyRedo") {
      event.preventDefault();
      history.redo();
    }
  });

  root.addEventListener("change", (event) => {
    const box = event.target;
    if (box.matches?.('li[data-type="taskItem"] > label > input')) {
      commands.setTaskChecked(box.closest("li"), box.checked);
    }
  });

  root.addEventListener("click", (event) => {
    const island = event.target.closest?.("img, a[data-note-file]");
    if (island && root.contains(island)) {
      event.preventDefault();
      if (editable) {
        selectIsland(island);
        const range = document.createRange();
        range.selectNode(island);
        selectRange(range);
      } else if (island.tagName !== "IMG") {
        window.open(island.href, "_blank", "noopener");
      }
      return;
    }
    const link = event.target.closest?.("a[href]");
    if (link && root.contains(link)) {
      // In an editable page a link places the caret; opening it takes Ctrl/Cmd+click.
      if (!editable || event[MOD]) {
        window.open(link.href, link.getAttribute("target") || "_self", "noopener");
      }
      event.preventDefault();
      return;
    }
    selectIsland(null);
  });

  root.addEventListener("dblclick", (event) => {
    const chip = event.target.closest?.("a[data-note-file]");
    if (chip) window.open(chip.href, "_blank", "noopener");
  });

  root.addEventListener("keydown", (event) => {
    if (!editable) return;
    const mod = event[MOD];
    const key = event.key;

    if (selectedIsland && !mod) {
      if (key === "Backspace" || key === "Delete") {
        event.preventDefault();
        removeIsland();
        return;
      }
      if (key === "Enter") {
        event.preventDefault();
        const paragraph = document.createElement("p");
        paragraph.append(document.createElement("br"));
        selectedIsland.after(paragraph);
        selectIsland(null);
        placeCaret(paragraph);
        commands.settle();
        return;
      }
      if (key.length === 1 || key.startsWith("Arrow")) {
        selectIsland(null);
      }
    }

    if (mod && !event.altKey) {
      const lower = key.toLowerCase();
      if (lower === "z") {
        event.preventDefault();
        event.shiftKey ? history.redo() : history.undo();
        return;
      }
      if (lower === "y") {
        event.preventDefault();
        history.redo();
        return;
      }
      if (lower === "k") {
        event.preventDefault();
        openLinkBar();
        return;
      }
      if (lower === "e" && !event.shiftKey) {
        event.preventDefault();
        commands.toggleInline("code");
        return;
      }
      if (event.shiftKey) {
        const shortcuts = {
          s: () => commands.format("strikeThrough"),
          x: () => commands.format("strikeThrough"),
          b: () => commands.toggleBlockquote(),
          h: () => commands.highlight("#fef08a"),
          "7": () => commands.toggleList("insertOrderedList"),
          "8": () => commands.toggleList("insertUnorderedList"),
          "9": () => commands.toggleTaskList(),
          l: () => commands.setTextAlign("left"),
          e: () => commands.setTextAlign("center"),
          r: () => commands.setTextAlign("right"),
          j: () => commands.setTextAlign("justify"),
        };
        const action = shortcuts[lower] || shortcuts[key];
        if (action) {
          event.preventDefault();
          action();
          return;
        }
      }
      if (lower === "b" || lower === "i" || lower === "u") {
        event.preventDefault();
        commands.format({ b: "bold", i: "italic", u: "underline" }[lower]);
        return;
      }
    }
    if (mod && event.altKey) {
      const level = { Digit1: 1, Digit2: 2, Digit3: 3 }[event.code];
      if (level) {
        event.preventDefault();
        commands.toggleHeading(level);
        return;
      }
      if (event.code === "KeyC") {
        event.preventDefault();
        commands.toggleCodeBlock();
        return;
      }
    }

    if (key === "Enter" && !event.shiftKey && !mod) {
      if (commands.splitTaskItem() || commands.leaveBlockquote()) {
        event.preventDefault();
        return;
      }
      if (leaveCodeBlockOnTripleEnter(event)) return;
    }
    if (key === "Backspace" && commands.backspaceInTaskItem()) {
      event.preventDefault();
      return;
    }
    if (key === "Tab" && !mod) {
      if (commands.moveInTable(!event.shiftKey) || commands.indentList(!event.shiftKey)) {
        event.preventDefault();
      }
    }
  });

  /** Enter three times at the end of a code block leaves it, as in the original editor. */
  function leaveCodeBlockOnTripleEnter(event) {
    const pre = commands.inside("pre");
    const range = rangeIn(root);
    if (!pre || !range || !range.collapsed) return false;
    const text = pre.textContent;
    if (!/\n\n$/.test(text) || range.startContainer !== (pre.querySelector("code") || pre).lastChild) return false;
    event.preventDefault();
    const code = pre.querySelector("code") || pre;
    code.textContent = text.replace(/\n\n$/, "");
    const paragraph = document.createElement("p");
    paragraph.append(document.createElement("br"));
    pre.after(paragraph);
    placeCaret(paragraph);
    commands.settle();
    return true;
  }

  root.addEventListener("paste", (event) => {
    if (!editable) return;
    const clipboard = event.clipboardData;
    const files = [...(clipboard?.files ?? [])];
    if (files.length) {
      event.preventDefault();
      uploadMany(files);
      return;
    }
    const html = clipboard?.getData("text/html");
    const text = clipboard?.getData("text/plain") ?? "";
    event.preventDefault();
    if (closestIn(rangeIn(root)?.startContainer, "pre", root) || (!html && text)) {
      const lines = text.replace(/\r\n?/g, "\n").split("\n");
      lines.forEach((line, index) => {
        if (index > 0) document.execCommand(closestIn(rangeIn(root)?.startContainer, "pre", root) ? "insertLineBreak" : "insertParagraph");
        if (line) document.execCommand("insertText", false, line);
      });
    } else if (html) {
      document.execCommand("insertHTML", false, cleanPastedHtml(html));
    }
    commands.settle();
  });

  root.addEventListener("dragover", (event) => {
    if (editable && event.dataTransfer?.types?.includes("Files")) event.preventDefault();
  });
  root.addEventListener("drop", (event) => {
    const files = [...(event.dataTransfer?.files ?? [])];
    if (!editable || !files.length) return;
    event.preventDefault();
    uploadMany(files);
  });

  root.addEventListener("blur", () => emit());
  root.addEventListener("focus", () => {
    // A new line is a <p>, not a <div>: the stored document has no anonymous blocks.
    document.execCommand("defaultParagraphSeparator", false, "p");
    scheduleState();
  });

  const onSelectionChange = () => {
    const range = rangeIn(root);
    if (range) {
      lastSelection = range.cloneRange();
      scheduleState();
      if (selectedIsland && !range.intersectsNode(selectedIsland)) selectIsland(null);
    }
  };
  document.addEventListener("selectionchange", onSelectionChange);
  const onResize = () => positionIslandTools();
  window.addEventListener("resize", onResize);

  // --- toolbar --------------------------------------------------------------------------------

  const toolbar = editable
    ? createToolbar({
        undo: () => (root.focus({ preventScroll: true }), history.undo()),
        redo: () => (root.focus({ preventScroll: true }), history.redo()),
        setParagraph: commands.setParagraph,
        toggleHeading: commands.toggleHeading,
        toggleBold: () => commands.format("bold"),
        toggleItalic: () => commands.format("italic"),
        toggleUnderline: () => commands.format("underline"),
        toggleStrike: () => commands.format("strikeThrough"),
        toggleCode: () => commands.toggleInline("code"),
        toggleCodeBlock: commands.toggleCodeBlock,
        toggleBlockquote: commands.toggleBlockquote,
        setHorizontalRule: commands.setHorizontalRule,
        toggleBulletList: () => commands.toggleList("insertUnorderedList"),
        toggleOrderedList: () => commands.toggleList("insertOrderedList"),
        toggleTaskList: commands.toggleTaskList,
        setLink: () => openLinkBar(),
        insertImage: () => imageInput.click(),
        insertFile: () => fileInput.click(),
        openDrawing: () => openDrawingDialog({ onInsert: uploadAndInsert }),
        insertTable: () => commands.insertTable({ rows: 3, cols: 3, withHeaderRow: true }),
        addRow: commands.addRow,
        deleteRow: commands.deleteRow,
        addColumn: commands.addColumn,
        deleteColumn: commands.deleteColumn,
        toggleHeaderRow: commands.toggleHeaderRow,
        deleteTable: commands.deleteTable,
        setTextAlign: commands.setTextAlign,
        indent: (deeper) => {
          root.focus({ preventScroll: true });
          commands.indentList(deeper);
        },
        setColor: (color) => commands.setInlineStyle("color", color),
        unsetColor: () => commands.unsetInlineStyle("color"),
        highlight: commands.highlight,
        unsetHighlight: commands.unsetHighlight,
        setFontSize: (size) => commands.setInlineStyle("font-size", size),
        unsetFontSize: () => commands.unsetInlineStyle("font-size"),
        setLineHeight: commands.setLineHeight,
        clearFormatting: commands.clearFormatting,
      })
    : null;

  const surface = h("div.nt-surface", root, tools, resizeHandle);
  const element = h("div.nt-editor" + (compact ? ".note-editor-compact" : "") + (editable ? "" : ".nt-editor--readonly"), toolbar?.element, editable ? linkBar : null, surface, imageInput, fileInput);

  setContent(content);
  if (autoFocus && editable) {
    requestAnimationFrame(() => {
      root.focus({ preventScroll: true });
      placeCaret(root.lastElementChild || root, true);
    });
  }

  function setEditable(value) {
    editable = value;
    root.setAttribute("contenteditable", value ? "true" : "false");
  }

  return {
    element,
    getHTML,
    /** Replaces the document. Does not call onChange. */
    setContent,
    focus: () => root.focus({ preventScroll: true }),
    /** Sends any change that is still waiting. */
    flush: () => emit(),
    setEditable,
    isEmpty: () => !serialize(root).replace(/<[^>]*>/g, "").trim() && !/<img\b/i.test(serialize(root)),
    destroy() {
      destroyed = true;
      clearTimeout(emitTimer);
      toolbar?.destroy();
      document.removeEventListener("selectionchange", onSelectionChange);
      window.removeEventListener("resize", onResize);
    },
  };
}
