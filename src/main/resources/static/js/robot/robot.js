import { h } from "../dom.js";
import { mountScene, robotFallback } from "./robot-scene.js";
import { openQuickAssistant } from "./quick-assistant.js";
import { openQuickNote } from "./quick-note.js";
import { createMultiTapDetector } from "./taps.js";

// The floating robot, on every student page. It is a draggable, animated 3D mascot that opens two
// small windows: a triple tap (or Enter) opens the Quick Note, a double tap (or A) the Quick
// Assistant. Where it rests is remembered per browser. Ported from Opportunity Radar's robot.

const STORAGE_KEY = "robot_position_v1";
const EDGE_PADDING = 12;
const DRAG_THRESHOLD_PX = 6;
const LOAD_TIMEOUT_MS = 8000;
// The 3D scene starts in the first idle moment after load, and at the latest this long after it.
const IDLE_TIMEOUT_MS = 3000;
const IDLE_FALLBACK_MS = 1500;
const ROBOT_SIZE = 120;
// On a phone the 120px robot covered card text and buttons on every page.
const ROBOT_SIZE_COMPACT = 68;
const COMPACT_QUERY = "(max-width: 767px)";

let mounted = false;

function styleSheet(href) {
  if (!document.querySelector(`link[href="${href}"]`)) {
    document.head.append(h("link", { rel: "stylesheet", href }));
  }
}

function clamp(position, size) {
  const maxX = window.innerWidth - size - EDGE_PADDING;
  const maxY = window.innerHeight - size - EDGE_PADDING;
  return {
    x: Math.min(Math.max(position.x, EDGE_PADDING), Math.max(EDGE_PADDING, maxX)),
    y: Math.min(Math.max(position.y, EDGE_PADDING), Math.max(EDGE_PADDING, maxY)),
  };
}

function storedPosition() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) || "null");
    if (typeof parsed?.x === "number" && typeof parsed?.y === "number") return parsed;
  } catch {
    // Corrupt or unavailable storage: fall through to the default position.
  }
  return null;
}

/** Puts the robot on the page. Safe to call more than once. */
export function mountRobot() {
  if (mounted) return;
  mounted = true;
  styleSheet("/css/notes.css");
  styleSheet("/css/rich.css");
  styleSheet("/css/robot.css");

  const media = window.matchMedia(COMPACT_QUERY);
  let size = media.matches ? ROBOT_SIZE_COMPACT : ROBOT_SIZE;
  let position = clamp(storedPosition() || { x: window.innerWidth - size - 24, y: window.innerHeight - size - 96 }, size);
  let hovered = false;
  let dragging = false;
  let drag = null;
  let noteOpen = false;
  let assistantOpen = false;
  let sceneLoaded = false;
  let pulseTimer = null;
  let note = null;
  let assistant = null;

  const inner = h("div.rb-robot__inner");
  const fallback = robotFallback();
  inner.append(fallback);
  const wrapper = h(
    "div.rb-robot",
    {
      role: "button",
      tabindex: "0",
      "aria-label": "Assistant robot. Triple-tap or press Enter for a quick note, double-tap or press A for the quick assistant.",
      "aria-haspopup": "dialog",
      "aria-expanded": "false",
    },
    inner,
  );

  function paintSize() {
    wrapper.style.width = size + "px";
    wrapper.style.height = size + "px";
  }
  const place = (target) => {
    wrapper.style.transform = `translate(${target.x}px, ${target.y}px)`;
  };
  function setPosition(next) {
    position = clamp(next, size);
    place(position);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(position));
    } catch {
      // Best effort: a quota error in private browsing must not break dragging.
    }
  }

  function paintState() {
    inner.classList.toggle("is-dragging", dragging);
    inner.classList.toggle("is-hovered", hovered && !dragging);
    inner.classList.toggle("is-floating", !dragging && !noteOpen);
    wrapper.setAttribute("aria-expanded", String(noteOpen || assistantOpen));
  }

  function pulse() {
    const ring = h("span.rb-robot__ring", { "aria-hidden": "true" });
    inner.append(ring);
    clearTimeout(pulseTimer);
    pulseTimer = setTimeout(() => ring.remove(), 600);
  }

  const anchor = () => ({ x: position.x, y: position.y, size });

  function openNote() {
    pulse();
    if (noteOpen) return;
    noteOpen = true;
    paintState();
    note = openQuickNote({
      anchor: anchor(),
      onClose: () => {
        noteOpen = false;
        note = null;
        paintState();
        // Focus goes back to the robot rather than the page: a keyboard user who opened the note with
        // Enter must not lose their place when it closes.
        wrapper.focus();
      },
    });
  }

  function openAssistant() {
    pulse();
    if (assistantOpen) return;
    assistantOpen = true;
    paintState();
    assistant = openQuickAssistant({
      anchor: anchor(),
      onClose: () => {
        assistantOpen = false;
        assistant = null;
        paintState();
        wrapper.focus();
      },
    });
  }

  const taps = createMultiTapDetector({ onDoubleTap: openAssistant, onTripleTap: openNote });

  // Every pointer event on the robot is handled here, both dragging and tap counting, because they
  // are one gesture stream: a tap is exactly a press and release that never crossed the drag
  // threshold, and a finished drag must never also count as a tap.
  wrapper.addEventListener("pointerdown", (event) => {
    // Dragging stays available while one panel is open; only two open at once ends the gestures.
    if (noteOpen && assistantOpen) return;
    if (event.pointerType === "mouse" && event.button !== 0) return;
    drag = { id: event.pointerId, startX: event.clientX, startY: event.clientY, originX: position.x, originY: position.y, passed: false, frame: 0, latest: position };
    wrapper.setPointerCapture?.(event.pointerId);
  });
  wrapper.addEventListener("pointermove", (event) => {
    if (!drag || drag.id !== event.pointerId) return;
    const dx = event.clientX - drag.startX;
    const dy = event.clientY - drag.startY;
    if (!drag.passed) {
      if (Math.hypot(dx, dy) <= DRAG_THRESHOLD_PX) return;
      drag.passed = true;
      dragging = true;
      paintState();
    }
    drag.latest = { x: drag.originX + dx, y: drag.originY + dy };
    if (!drag.frame) {
      drag.frame = requestAnimationFrame(() => {
        if (drag) {
          place(drag.latest);
          drag.frame = 0;
        }
      });
    }
  });
  const finish = (event) => {
    if (!drag || drag.id !== event.pointerId) return;
    if (drag.frame) cancelAnimationFrame(drag.frame);
    if (drag.passed) {
      setPosition(drag.latest);
    } else {
      // A real tap: released close to where it started.
      taps.registerTap();
    }
    drag = null;
    dragging = false;
    paintState();
  };
  wrapper.addEventListener("pointerup", finish);
  wrapper.addEventListener("pointercancel", (event) => {
    if (!drag || drag.id !== event.pointerId) return;
    if (drag.frame) cancelAnimationFrame(drag.frame);
    // A cancelled gesture (the system took over) goes back to the last committed position and is never a tap.
    place(position);
    drag = null;
    dragging = false;
    paintState();
  });
  wrapper.addEventListener("pointerenter", () => ((hovered = true), paintState()));
  wrapper.addEventListener("pointerleave", () => ((hovered = false), paintState()));

  wrapper.addEventListener("keydown", (event) => {
    // One keypress per panel, rather than asking a keyboard user for a two- or three-tap gesture.
    if ((event.key === "Enter" || event.key === " ") && !noteOpen) {
      event.preventDefault();
      openNote();
    }
    if ((event.key === "a" || event.key === "A") && !assistantOpen) {
      event.preventDefault();
      openAssistant();
    }
  });

  media.addEventListener("change", () => {
    size = media.matches ? ROBOT_SIZE_COMPACT : ROBOT_SIZE;
    paintSize();
    setPosition(position);
  });
  window.addEventListener("resize", () => {
    position = clamp(position, size);
    place(position);
  });

  paintSize();
  place(position);
  paintState();
  document.body.append(wrapper);

  // The 3D scene replaces the badge once it is drawn, and is given up on after a few seconds.
  // As in the original: the badge shows only until the scene's code is in, then the canvas takes its
  // place (blank until drawn). If the scene fails, or is not drawn within LOAD_TIMEOUT_MS, the scene
  // is dropped and the badge comes back for good.
  function showBadge() {
    scene?.destroy();
    inner.querySelector(".rb-scene")?.remove();
    if (!fallback.isConnected) inner.append(fallback);
  }
  let scene = null;
  function startScene() {
    if (scene) return;
    scene = mountScene(inner, {
      onMount: () => fallback.remove(),
      onLoad: () => {
        sceneLoaded = true;
      },
      onFail: (error) => {
        console.error("[FloatingRobot] Spline scene failed to render:", error);
        showBadge();
      },
    });
    setTimeout(() => {
      if (!sceneLoaded) showBadge();
    }, LOAD_TIMEOUT_MS);
  }

  // The scene's runtime is about 2.5 MB and takes the main thread for a moment while it starts, so
  // it waits until the page is usable: the browser's first idle moment after load, or at once if
  // the student reaches for the robot. The badge and every gesture work before it arrives.
  for (const type of ["pointerenter", "pointerdown", "focus"]) {
    wrapper.addEventListener(type, startScene, { once: true });
  }
  whenIdle(startScene);
}

/** Runs `task` once the page has loaded and the browser has a quiet moment. */
function whenIdle(task) {
  const schedule = () => {
    if ("requestIdleCallback" in window) {
      window.requestIdleCallback(task, { timeout: IDLE_TIMEOUT_MS });
    } else {
      setTimeout(task, IDLE_FALLBACK_MS);
    }
  };
  if (document.readyState === "complete") {
    schedule();
  } else {
    window.addEventListener("load", schedule, { once: true });
  }
}
