import { h } from "../dom.js";

// The "drag a rectangle" layer of the screenshot tool, like Cmd+Shift+4 on a Mac: a dimmed layer over
// the whole page, a crosshair cursor, a live rectangle with its size, and a result when the mouse,
// finger or pen is released. It only chooses the area (in CSS pixels of the visible page);
// page-snapshot.js draws the picture of it.

/** Smaller than this on either side is a stray click, not a selection. */
export const MIN_SIDE = 8;

/** The rectangle between two points, kept inside the bounds, in whole pixels. */
export function normalizeRect(a, b, bounds) {
  const within = (value, max) => Math.min(Math.max(value, 0), max);
  const x1 = within(a.x, bounds.width);
  const y1 = within(a.y, bounds.height);
  const x2 = within(b.x, bounds.width);
  const y2 = within(b.y, bounds.height);
  const x = Math.round(Math.min(x1, x2));
  const y = Math.round(Math.min(y1, y2));
  return { x, y, width: Math.round(Math.max(x1, x2)) - x, height: Math.round(Math.max(y1, y2)) - y };
}

export function isUsable(rect) {
  return rect.width >= MIN_SIDE && rect.height >= MIN_SIDE;
}

// Keys that would scroll or move focus on the page behind the layer.
const BLOCKED_KEYS = new Set(["Tab", " ", "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight", "PageUp", "PageDown", "Home", "End"]);

/**
 * Shows the layer and resolves with the chosen { x, y, width, height } (CSS pixels from the top left
 * of the visible page), or null when the student cancels (Escape, a right click, or a resize or zoom,
 * which would make the drawn rectangle wrong). A selection that is too small is not accepted: the
 * hint says so and the student can drag again.
 */
export function selectArea() {
  return new Promise((resolve) => {
    const size = h("span.rb-capture__size");
    const box = h("div.rb-capture__box", { hidden: true }, size);
    const hint = h("div.rb-capture__hint", { role: "status" }, "Drag to select an area. Press Esc to cancel.");
    const layer = h("div.rb-capture", { role: "dialog", "aria-modal": "true", "aria-label": "Select an area to capture", tabindex: "-1" }, box, hint);
    let start = null;
    let current = null;
    let finished = false;

    const bounds = () => {
      const area = layer.getBoundingClientRect();
      return { width: area.width, height: area.height };
    };
    const rectNow = () => normalizeRect(start, current, bounds());

    function paint() {
      const rect = rectNow();
      box.hidden = false;
      box.style.left = `${rect.x}px`;
      box.style.top = `${rect.y}px`;
      box.style.width = `${rect.width}px`;
      box.style.height = `${rect.height}px`;
      size.textContent = `${rect.width} × ${rect.height}`;
      // Put the size inside the box when it would fall off the top of the page.
      box.classList.toggle("is-low", rect.y < 28);
    }

    function done(result) {
      if (finished) return;
      finished = true;
      window.removeEventListener("keydown", onKey, true);
      window.removeEventListener("resize", onResize);
      layer.remove();
      resolve(result);
    }

    function onKey(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopImmediatePropagation(); // the assistant's own Escape must not close the panel
        done(null);
      } else if (BLOCKED_KEYS.has(event.key)) {
        event.preventDefault();
      }
    }
    const onResize = () => done(null);

    layer.addEventListener("pointerdown", (event) => {
      event.preventDefault();
      if (event.button !== 0) {
        done(null);
        return;
      }
      start = current = { x: event.clientX, y: event.clientY };
      layer.setPointerCapture?.(event.pointerId);
      layer.classList.add("is-dragging");
      hint.textContent = "Release to capture. Press Esc to cancel.";
      paint();
    });
    layer.addEventListener("pointermove", (event) => {
      if (!start) return;
      current = { x: event.clientX, y: event.clientY };
      paint();
    });
    layer.addEventListener("pointerup", (event) => {
      if (!start) return;
      current = { x: event.clientX, y: event.clientY };
      const rect = rectNow();
      if (isUsable(rect)) {
        done(rect);
        return;
      }
      // A click or a sliver: keep the layer and let the student try again.
      start = current = null;
      box.hidden = true;
      layer.classList.remove("is-dragging");
      hint.textContent = "That area is too small. Drag a larger one, or press Esc to cancel.";
    });
    layer.addEventListener("pointercancel", () => {
      start = current = null;
      box.hidden = true;
      layer.classList.remove("is-dragging");
    });
    layer.addEventListener("contextmenu", (event) => event.preventDefault());
    layer.addEventListener("wheel", (event) => event.preventDefault(), { passive: false });

    window.addEventListener("keydown", onKey, true);
    window.addEventListener("resize", onResize);
    document.body.append(layer);
    layer.focus({ preventScroll: true });
  });
}
