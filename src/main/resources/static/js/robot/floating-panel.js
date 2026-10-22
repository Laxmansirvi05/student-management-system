import { h } from "../dom.js";

// A floating window that opens beside the robot: it can be dragged by its header, resized from its
// bottom-right corner, and remembers its size. Both the Quick Note and the Quick Assistant are one.

const GAP = 12;
const VIEWPORT_PADDING = 12;
/** The share of the viewport a panel may grow to occupy, per axis, and never below its own default. */
const MAX_VIEWPORT_FRACTION = 0.4;
const MIN_WIDTH = 260;
const MIN_HEIGHT = 220;

function maxSize(defaultSize) {
  return {
    width: Math.max(defaultSize.width, Math.round(window.innerWidth * MAX_VIEWPORT_FRACTION)),
    height: Math.max(defaultSize.height, Math.round(window.innerHeight * MAX_VIEWPORT_FRACTION)),
  };
}

export function clampSize(size, defaultSize) {
  const max = maxSize(defaultSize);
  return {
    width: Math.min(Math.max(Math.round(size.width), MIN_WIDTH), max.width),
    height: Math.min(Math.max(Math.round(size.height), MIN_HEIGHT), max.height),
  };
}

function readSize(key) {
  try {
    const parsed = JSON.parse(localStorage.getItem(key) || "null");
    if (typeof parsed?.width === "number" && typeof parsed?.height === "number") {
      return { width: parsed.width, height: parsed.height };
    }
  } catch {
    // A corrupt or unreadable entry just means "use the default".
  }
  return null;
}

/** Keeps a panel fully on screen, whatever the drag or the resize asked for. */
function clampToViewport(top, left, size) {
  const maxTop = Math.max(VIEWPORT_PADDING, window.innerHeight - size.height - VIEWPORT_PADDING);
  const maxLeft = Math.max(VIEWPORT_PADDING, window.innerWidth - size.width - VIEWPORT_PADDING);
  return { top: Math.min(Math.max(top, VIEWPORT_PADDING), maxTop), left: Math.min(Math.max(left, VIEWPORT_PADDING), maxLeft) };
}

/**
 * Where a panel first opens. The note prefers above-left of the robot (the usual resting place, the
 * bottom-right corner) and the assistant above-right, so the two can sit open side by side. Either
 * flips to the side that has room, and is clamped inside the viewport.
 */
export function openingPosition(anchor, size, placement) {
  const preferAbove = anchor.y > size.height + GAP + VIEWPORT_PADDING;
  const top = preferAbove ? anchor.y - size.height - GAP : anchor.y + anchor.size + GAP;
  let left;
  if (placement === "assistant") {
    left = anchor.x + anchor.size + GAP + size.width < window.innerWidth ? anchor.x + anchor.size + GAP : anchor.x - size.width - GAP;
  } else {
    const preferLeft = anchor.x > size.width + GAP + VIEWPORT_PADDING;
    left = preferLeft ? anchor.x - size.width + anchor.size : anchor.x;
  }
  return clampToViewport(top, left, size);
}

/**
 * Options: label, anchor ({x, y, size} of the robot), defaultSize, storageKey, placement ("note" or
 * "assistant"), header (the element that drags the panel), body (children), backdropLevel (z-index of
 * the click-away layer), resizeLabel, onBackdrop(). Returns { element, backdrop, destroy }.
 */
export function createFloatingPanel({ label, anchor, defaultSize, storageKey, placement, header, body, backdropLevel, resizeLabel, onBackdrop, className = "" }) {
  const stored = readSize(storageKey);
  let size = stored ? clampSize(stored, defaultSize) : { ...defaultSize };
  let position = openingPosition(anchor, defaultSize, placement);
  let moved = false;
  let dragging = null;
  let resizing = null;

  const panel = h("div.rb-panel" + (className ? "." + className : ""), { role: "dialog", "aria-label": label }, header, body);
  const backdrop = h("button.rb-backdrop", { type: "button", "aria-label": `Close ${label.toLowerCase()}`, onclick: onBackdrop });
  backdrop.style.zIndex = String(backdropLevel);

  function paint() {
    panel.style.top = position.top + "px";
    panel.style.left = position.left + "px";
    panel.style.width = size.width + "px";
    panel.style.height = size.height + "px";
    panel.classList.toggle("is-moving", Boolean(dragging || resizing));
  }

  // --- moving ---------------------------------------------------------------------------------

  header.addEventListener("pointerdown", (event) => {
    // Only a plain drag on the header chrome: never a click on a button, and never text selection.
    if (event.button !== 0 || event.target.closest('button, input, textarea, [contenteditable="true"]')) return;
    event.preventDefault();
    header.setPointerCapture(event.pointerId);
    dragging = { x: event.clientX, y: event.clientY, top: position.top, left: position.left };
    header.classList.add("is-grabbing");
    paint();
  });
  header.addEventListener("pointermove", (event) => {
    if (!dragging) return;
    moved = true;
    position = clampToViewport(dragging.top + (event.clientY - dragging.y), dragging.left + (event.clientX - dragging.x), size);
    paint();
  });
  const endDrag = (event) => {
    if (!dragging) return;
    header.releasePointerCapture?.(event.pointerId);
    dragging = null;
    header.classList.remove("is-grabbing");
    paint();
  };
  header.addEventListener("pointerup", endDrag);
  header.addEventListener("pointercancel", endDrag);

  // --- resizing -------------------------------------------------------------------------------

  const grip = h(
    "span.rb-grip",
    { role: "separator", "aria-orientation": "vertical", "aria-label": resizeLabel, title: `${resizeLabel} (double-click to reset)` },
  );
  // Two short strokes reading as a corner grip, drawn rather than an icon so it sits flush in the rounded corner.
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 10 10");
  svg.setAttribute("aria-hidden", "true");
  svg.classList.add("rb-grip__icon");
  for (const d of ["M9 3 L3 9", "M9 7 L7 9"]) {
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", d);
    svg.append(path);
  }
  grip.append(svg);

  grip.addEventListener("pointerdown", (event) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    grip.setPointerCapture(event.pointerId);
    resizing = { x: event.clientX, y: event.clientY, width: size.width, height: size.height };
    grip.classList.add("is-resizing");
    paint();
  });
  grip.addEventListener("pointermove", (event) => {
    if (!resizing) return;
    // Written straight to the element during the drag; committed to the stored size on release.
    const next = clampSize({ width: resizing.width + (event.clientX - resizing.x), height: resizing.height + (event.clientY - resizing.y) }, defaultSize);
    panel.style.width = next.width + "px";
    panel.style.height = next.height + "px";
  });
  const endResize = (event) => {
    if (!resizing) return;
    grip.releasePointerCapture?.(event.pointerId);
    resizing = null;
    grip.classList.remove("is-resizing");
    const box = panel.getBoundingClientRect();
    size = clampSize({ width: box.width, height: box.height }, defaultSize);
    try {
      localStorage.setItem(storageKey, JSON.stringify(size));
    } catch {
      // Storage can be unavailable (private mode, quota); the size still applies for this session.
    }
    position = clampToViewport(position.top, position.left, size);
    paint();
  };
  grip.addEventListener("pointerup", endResize);
  grip.addEventListener("pointercancel", endResize);
  grip.addEventListener("dblclick", () => {
    size = { ...defaultSize };
    try {
      localStorage.removeItem(storageKey);
    } catch {
      // Nothing to recover from: the reset already happened in memory.
    }
    position = clampToViewport(position.top, position.left, size);
    paint();
  });
  panel.append(grip);

  const onWindowResize = () => {
    size = clampSize(size, defaultSize);
    // Once the writer has placed it, a resize only clamps it back inside the viewport: it never
    // jumps back to the robot.
    position = moved ? clampToViewport(position.top, position.left, size) : openingPosition(anchor, size, placement);
    paint();
  };
  window.addEventListener("resize", onWindowResize);

  paint();
  return {
    element: panel,
    backdrop,
    destroy() {
      window.removeEventListener("resize", onWindowResize);
      panel.remove();
      backdrop.remove();
    },
  };
}
