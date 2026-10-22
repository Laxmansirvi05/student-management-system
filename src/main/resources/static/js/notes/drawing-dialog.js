import { h } from "../dom.js";
import { openModal } from "./modal.js";
import { symbol } from "./symbols.js";

// A lightweight sketch pad, kept as its own dialog rather than a live block in the editor. The
// output is a flattened PNG uploaded like any other picture, so the note gains one ordinary image:
// a drawing costs nothing to show and works unchanged in the read-only shared view.

const STROKE_WIDTHS = [
  { label: "Thin", value: 2 },
  { label: "Medium", value: 4 },
  { label: "Thick", value: 8 },
  { label: "Extra thick", value: 14 },
];
const STROKE_COLORS = ["#1f2933", "#2563eb", "#059669", "#d97706", "#dc2626", "#7c3aed"];
const CANVAS_WIDTH = 900;
const CANVAS_HEIGHT = 520;

/** Opens the pad. `onInsert(file)` receives the drawing as a PNG file. */
export function openDrawingDialog({ onInsert }) {
  let strokeWidth = 4;
  let strokeColor = STROKE_COLORS[0];
  let eraser = false;
  let empty = true;
  let saving = false;
  let drawing = false;
  let last = null;

  const canvas = h("canvas.nt-canvas", { width: CANVAS_WIDTH, height: CANVAS_HEIGHT, "aria-label": "Drawing area" });
  const context = canvas.getContext("2d");
  // Painted white rather than left transparent: dark ink on a transparent PNG would vanish on a dark theme.
  const whiten = () => {
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, canvas.width, canvas.height);
  };
  whiten();

  const widthButtons = STROKE_WIDTHS.map((option) =>
    h(
      "button.nt-tool",
      {
        type: "button",
        "aria-label": option.label,
        title: option.label,
        onclick: () => {
          strokeWidth = option.value;
          eraser = false;
          refresh();
        },
      },
      h("span.nt-dot", { style: { width: option.value + 2 + "px", height: option.value + 2 + "px" } }),
    ),
  );
  const colorButtons = STROKE_COLORS.map((color) =>
    h("button.nt-pen", {
      type: "button",
      "aria-label": `Colour ${color}`,
      style: { "background-color": color },
      onclick: () => {
        strokeColor = color;
        eraser = false;
        refresh();
      },
    }),
  );
  const eraserButton = h(
    "button.nt-chip.nt-chip--icon",
    {
      type: "button",
      onclick: () => {
        eraser = !eraser;
        refresh();
      },
    },
    symbol("ink_eraser"),
    "Eraser",
  );
  const clearButton = h("button.nt-chip.nt-chip--icon", { type: "button", onclick: clear }, symbol("delete_sweep"), "Clear");
  const cancel = h("button.nt-btn.nt-btn--quiet", { type: "button", onclick: () => dialog.close() }, "Cancel");
  const insert = h("button.nt-btn.nt-btn--primary", { type: "button", onclick: insertDrawing }, "Insert");

  function refresh() {
    widthButtons.forEach((button, index) => {
      const on = !eraser && STROKE_WIDTHS[index].value === strokeWidth;
      button.classList.toggle("is-on", on);
      button.setAttribute("aria-pressed", String(on));
    });
    colorButtons.forEach((button, index) => {
      const on = !eraser && STROKE_COLORS[index] === strokeColor;
      button.classList.toggle("is-on", on);
      button.setAttribute("aria-pressed", String(on));
    });
    eraserButton.classList.toggle("is-on", eraser);
    eraserButton.setAttribute("aria-pressed", String(eraser));
    insert.disabled = empty || saving;
    insert.textContent = saving ? "Inserting…" : "Insert";
  }

  // The canvas is displayed smaller than its backing resolution, so pointer coordinates are scaled.
  function pointFrom(event) {
    const box = canvas.getBoundingClientRect();
    return { x: ((event.clientX - box.left) / box.width) * canvas.width, y: ((event.clientY - box.top) / box.height) * canvas.height };
  }

  canvas.addEventListener("pointerdown", (event) => {
    canvas.setPointerCapture(event.pointerId);
    drawing = true;
    last = pointFrom(event);
  });
  canvas.addEventListener("pointermove", (event) => {
    if (!drawing || !last) return;
    const point = pointFrom(event);
    context.strokeStyle = eraser ? "#ffffff" : strokeColor;
    context.lineWidth = eraser ? strokeWidth * 3 : strokeWidth;
    context.lineCap = "round";
    context.lineJoin = "round";
    context.beginPath();
    context.moveTo(last.x, last.y);
    context.lineTo(point.x, point.y);
    context.stroke();
    last = point;
    if (empty) {
      empty = false;
      refresh();
    }
  });
  const end = () => {
    drawing = false;
    last = null;
  };
  canvas.addEventListener("pointerup", end);
  canvas.addEventListener("pointercancel", end);

  function clear() {
    whiten();
    empty = true;
    refresh();
  }

  async function insertDrawing() {
    if (empty || saving) return;
    saving = true;
    refresh();
    try {
      const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
      if (!blob) return;
      await onInsert(new File([blob], `drawing-${Date.now()}.png`, { type: "image/png" }));
      dialog.close();
    } finally {
      saving = false;
      if (dialog.isConnected) refresh();
    }
  }

  const dialog = openModal({
    label: "Drawing",
    className: "nt-modal--drawing",
    children: [
      h(
        "div.nt-drawing__bar",
        h("span.nt-drawing__title", "Drawing"),
        h("div.nt-group", { role: "group", "aria-label": "Stroke width" }, widthButtons),
        h("div.nt-group", { role: "group", "aria-label": "Stroke colour" }, colorButtons),
        eraserButton,
        clearButton,
        h("div.nt-drawing__actions", cancel, insert),
      ),
      canvas,
    ],
  });
  refresh();
  return dialog;
}
