import { h } from "../dom.js";
import { symbol } from "./symbols.js";

// The editor's controls: the handful people reach for constantly stay on the bar, everything else
// lives one click away under More. Deliberately not 25 buttons in a row: a toolbar that fills the
// screen makes a notes app feel like a form rather than a page to write on.

const TEXT_COLORS = [
  { label: "Default", value: null },
  { label: "Blue", value: "#2563eb" },
  { label: "Green", value: "#059669" },
  { label: "Amber", value: "#d97706" },
  { label: "Red", value: "#dc2626" },
  { label: "Purple", value: "#7c3aed" },
];
const HIGHLIGHTS = ["#fef08a", "#bbf7d0", "#bfdbfe", "#fecaca", "#e9d5ff"];
const FONT_SIZES = [
  { label: "Small", value: "14px" },
  { label: "Normal", value: null },
  { label: "Large", value: "20px" },
  { label: "Huge", value: "28px" },
];
const LINE_HEIGHTS = [
  { label: "Tight", value: "1.2" },
  { label: "Normal", value: "1.6" },
  { label: "Relaxed", value: "2" },
];

/**
 * Builds the toolbar. `actions` holds what the buttons do. `setState` shows which formats are on at
 * the caret; `setUploading` shows the "Uploading…" note.
 */
export function createToolbar(actions) {
  const buttons = new Map();
  let moreOpen = false;
  let tableRow = null;

  // The editor loses its selection the moment a button takes focus, so focus is prevented rather
  // than restored afterwards.
  function button({ key, label, icon, run, small = false }) {
    const element = h(
      "button.nt-tool" + (small ? ".nt-tool--small" : ""),
      { type: "button", "aria-label": label, title: label, "aria-pressed": "false", onmousedown: (event) => event.preventDefault(), onclick: run },
      symbol(icon),
    );
    if (key) {
      buttons.set(key, element);
    }
    return element;
  }

  const divider = () => h("span.nt-tool-divider", { "aria-hidden": "true" });

  const row = (label, ...children) => h("div.nt-menu__row", h("span.nt-menu__label", label), h("div.nt-menu__tools", children));

  const paragraphStyle = h(
    "select.nt-style",
    {
      "aria-label": "Paragraph style",
      onchange: (event) => {
        const level = Number(event.target.value);
        if (level === 0) actions.setParagraph();
        else actions.toggleHeading(level);
      },
    },
    h("option", { value: "0" }, "Body"),
    h("option", { value: "1" }, "Title"),
    h("option", { value: "2" }, "Heading"),
    h("option", { value: "3" }, "Subheading"),
  );

  const uploading = h("span.nt-uploading", { "aria-live": "polite", hidden: true }, "Uploading…");

  const swatch = (label, color, run) =>
    h("button.nt-swatch", { type: "button", "aria-label": label, title: label, onmousedown: (event) => event.preventDefault(), onclick: run, style: { "background-color": color || "transparent" } }, color ? null : symbol("format_color_reset"));

  const textButton = (label, run) =>
    h("button.nt-chip", { type: "button", onmousedown: (event) => event.preventDefault(), onclick: run }, label);

  tableRow = row(
    "Table",
    button({ label: "Add row", icon: "add_row_below", run: actions.addRow, small: true }),
    button({ label: "Delete row", icon: "delete", run: actions.deleteRow, small: true }),
    button({ label: "Add column", icon: "add_column_right", run: actions.addColumn, small: true }),
    button({ label: "Delete column", icon: "remove_selection", run: actions.deleteColumn, small: true }),
    button({ label: "Toggle header row", icon: "table_rows", run: actions.toggleHeaderRow, small: true }),
    button({ label: "Delete table", icon: "grid_off", run: actions.deleteTable, small: true }),
  );
  tableRow.hidden = true;

  const menu = h(
    "div.nt-menu",
    { role: "menu", hidden: true },
    row(
      "Text",
      button({ key: "strike", label: "Strikethrough", icon: "strikethrough_s", run: actions.toggleStrike, small: true }),
      button({ key: "code", label: "Inline code", icon: "code", run: actions.toggleCode, small: true }),
      button({ key: "codeBlock", label: "Code block", icon: "data_object", run: actions.toggleCodeBlock, small: true }),
      button({ key: "blockquote", label: "Quote", icon: "format_quote", run: actions.toggleBlockquote, small: true }),
      button({ label: "Divider", icon: "horizontal_rule", run: actions.setHorizontalRule, small: true }),
    ),
    row(
      "Align",
      button({ key: "align-left", label: "Align left", icon: "format_align_left", run: () => actions.setTextAlign("left"), small: true }),
      button({ key: "align-center", label: "Align centre", icon: "format_align_center", run: () => actions.setTextAlign("center"), small: true }),
      button({ key: "align-right", label: "Align right", icon: "format_align_right", run: () => actions.setTextAlign("right"), small: true }),
      button({ key: "align-justify", label: "Justify", icon: "format_align_justify", run: () => actions.setTextAlign("justify"), small: true }),
    ),
    row(
      "Indent",
      button({ label: "Decrease indent", icon: "format_indent_decrease", run: () => actions.indent(false), small: true }),
      button({ label: "Increase indent", icon: "format_indent_increase", run: () => actions.indent(true), small: true }),
    ),
    row("Colour", TEXT_COLORS.map((color) => swatch(`Text colour ${color.label}`, color.value, () => (color.value ? actions.setColor(color.value) : actions.unsetColor())))),
    row(
      "Highlight",
      HIGHLIGHTS.map((color) => swatch(`Highlight ${color}`, color, () => actions.highlight(color))),
      button({ key: "highlight", label: "Remove highlight", icon: "format_color_reset", run: actions.unsetHighlight, small: true }),
    ),
    row("Size", FONT_SIZES.map((size) => textButton(size.label, () => (size.value ? actions.setFontSize(size.value) : actions.unsetFontSize())))),
    row("Spacing", LINE_HEIGHTS.map((height) => textButton(height.label, () => actions.setLineHeight(height.value)))),
    tableRow,
    row(
      "Insert",
      button({ key: "attach", label: "Attach a file", icon: "attach_file", run: actions.insertFile, small: true }),
      button({ label: "Drawing", icon: "draw", run: actions.openDrawing, small: true }),
    ),
    row("Clear", textButton("Clear formatting", actions.clearFormatting)),
  );

  const more = h(
    "button.nt-more",
    {
      type: "button",
      "aria-label": "More formatting",
      "aria-haspopup": "menu",
      "aria-expanded": "false",
      title: "More",
      onmousedown: (event) => event.preventDefault(),
      onclick: () => setMore(!moreOpen),
    },
    symbol("more_horiz"),
    h("span.nt-more__text", "More"),
  );

  function setMore(open) {
    moreOpen = open;
    menu.hidden = !open;
    more.setAttribute("aria-expanded", String(open));
    more.classList.toggle("is-on", open);
  }

  const element = h(
    "div.nt-toolbar",
    button({ key: "undo", label: "Undo", icon: "undo", run: actions.undo }),
    button({ key: "redo", label: "Redo", icon: "redo", run: actions.redo }),
    divider(),
    paragraphStyle,
    divider(),
    button({ key: "bold", label: "Bold", icon: "format_bold", run: actions.toggleBold }),
    button({ key: "italic", label: "Italic", icon: "format_italic", run: actions.toggleItalic }),
    button({ key: "underline", label: "Underline", icon: "format_underlined", run: actions.toggleUnderline }),
    divider(),
    button({ key: "bullet", label: "Bullet list", icon: "format_list_bulleted", run: actions.toggleBulletList }),
    button({ key: "ordered", label: "Numbered list", icon: "format_list_numbered", run: actions.toggleOrderedList }),
    button({ key: "task", label: "Checklist", icon: "checklist", run: actions.toggleTaskList }),
    divider(),
    button({ key: "link", label: "Link", icon: "link", run: actions.setLink }),
    button({ key: "image", label: "Insert image", icon: "image", run: actions.insertImage }),
    button({ key: "table", label: "Insert table", icon: "table", run: actions.insertTable }),
    h("div.nt-more-wrap", more, menu),
    uploading,
  );

  // The More menu closes on a click elsewhere and on Escape.
  const away = (event) => {
    if (moreOpen && !element.querySelector(".nt-more-wrap").contains(event.target)) setMore(false);
  };
  const escape = (event) => {
    if (moreOpen && event.key === "Escape") setMore(false);
  };
  document.addEventListener("pointerdown", away);
  document.addEventListener("keydown", escape);

  function setState(state) {
    const on = {
      bold: state.bold,
      italic: state.italic,
      underline: state.underline,
      strike: state.strike,
      code: state.code,
      codeBlock: state.codeBlock,
      highlight: state.highlight,
      blockquote: state.blockquote,
      link: state.link,
      bullet: state.bullet,
      ordered: state.ordered,
      task: state.task,
      table: state.table,
      "align-left": state.align === "left",
      "align-center": state.align === "center",
      "align-right": state.align === "right",
      "align-justify": state.align === "justify",
    };
    for (const [key, active] of Object.entries(on)) {
      const element = buttons.get(key);
      if (element) {
        element.setAttribute("aria-pressed", String(Boolean(active)));
        element.classList.toggle("is-on", Boolean(active));
      }
    }
    buttons.get("undo").disabled = !state.canUndo;
    buttons.get("redo").disabled = !state.canRedo;
    paragraphStyle.value = String(state.heading);
    tableRow.hidden = !state.table;
  }

  function setUploading(isUploading) {
    uploading.hidden = !isUploading;
    buttons.get("image").disabled = isUploading;
    buttons.get("attach").disabled = isUploading;
  }

  function destroy() {
    document.removeEventListener("pointerdown", away);
    document.removeEventListener("keydown", escape);
  }

  return { element, setState, setUploading, destroy };
}
