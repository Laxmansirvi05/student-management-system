// Decorative motion shared by pages: content that rises into view, cards that lean toward the
// pointer, numbers that count up, gauges that fill, and the sliding thumb of a tab control.
// Nothing here changes what a page says, and all of it is skipped or instant when the device
// asks for reduced motion.

import { h } from "./dom.js";

const SVG = "http://www.w3.org/2000/svg";
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const canHover = window.matchMedia("(hover: hover) and (pointer: fine)");

const revealer =
  "IntersectionObserver" in window
    ? new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) {
              entry.target.classList.add("is-in");
              revealer.unobserve(entry.target);
            }
          }
        },
        { rootMargin: "0px 0px -6% 0px" },
      )
    : null;

/** Lets every element with the class "reveal" inside root rise in when it is scrolled to. */
export function reveal(root = document) {
  for (const element of root.querySelectorAll(".reveal:not(.is-in)")) {
    if (revealer) {
      revealer.observe(element);
    } else {
      element.classList.add("is-in");
    }
  }
}

/** Makes every element with data-tilt inside root lean a few degrees toward the pointer. */
export function enableTilt(root = document, maxDegrees = 4) {
  for (const element of root.querySelectorAll("[data-tilt]")) {
    element.addEventListener("pointermove", (event) => {
      if (reduceMotion.matches || !canHover.matches) {
        return;
      }
      const box = element.getBoundingClientRect();
      const x = (event.clientX - box.left) / box.width;
      const y = (event.clientY - box.top) / box.height;
      element.style.setProperty("--tilt-x", `${(0.5 - y) * maxDegrees}deg`);
      element.style.setProperty("--tilt-y", `${(x - 0.5) * maxDegrees}deg`);
      element.style.setProperty("--shine-x", `${x * 100}%`);
      element.style.setProperty("--shine-y", `${y * 100}%`);
      element.classList.add("is-tilting");
    });
    element.addEventListener("pointerleave", () => {
      element.style.setProperty("--tilt-x", "0deg");
      element.style.setProperty("--tilt-y", "0deg");
      element.classList.remove("is-tilting");
    });
  }
}

/**
 * Shows a number, counting up to it over about two thirds of a second.
 * decimals keeps "82.50" at two places; suffix adds for example "%".
 */
export function countUp(element, target, { decimals = 0, suffix = "" } = {}) {
  const show = (value) => {
    element.textContent = value.toFixed(decimals) + suffix;
  };
  if (reduceMotion.matches || !Number.isFinite(target)) {
    show(target);
    return;
  }
  // A large value such as a year starts close to the end, so the count stays short.
  const start = target > 200 ? target - 24 : 0;
  const duration = 800;
  const began = performance.now();
  function step(now) {
    const progress = Math.min(1, (now - began) / duration);
    const eased = 1 - Math.pow(1 - progress, 3);
    show(start + (target - start) * eased);
    if (progress < 1) {
      requestAnimationFrame(step);
    }
  }
  requestAnimationFrame(step);
}

/**
 * A circular gauge that fills to a percentage. Whatever is passed as label is placed in the
 * middle. kind may be "success" or "danger" to change the colour.
 */
export function ring(percentage, { size = 120, stroke = 9, label = null, kind = "" } = {}) {
  const radius = (size - stroke) / 2;
  const length = 2 * Math.PI * radius;
  const svg = document.createElementNS(SVG, "svg");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("viewBox", `0 0 ${size} ${size}`);
  svg.setAttribute("aria-hidden", "true");
  const circle = (className) => {
    const shape = document.createElementNS(SVG, "circle");
    shape.setAttribute("class", className);
    shape.setAttribute("cx", String(size / 2));
    shape.setAttribute("cy", String(size / 2));
    shape.setAttribute("r", String(radius));
    shape.setAttribute("stroke-width", String(stroke));
    return shape;
  };
  const value = circle("ring__value");
  value.setAttribute("stroke-dasharray", String(length));
  value.setAttribute("stroke-dashoffset", String(length));
  svg.append(circle("ring__track"), value);
  const element = h("div.ring" + (kind ? ".ring--" + kind : ""), svg, label ? h("div.ring__label", label) : null);
  // Two frames later, so the browser draws the empty ring first and then animates the fill.
  requestAnimationFrame(() =>
    requestAnimationFrame(() => {
      const share = Math.max(0, Math.min(100, percentage)) / 100;
      value.setAttribute("stroke-dashoffset", String(length * (1 - share)));
    }),
  );
  return element;
}

/** A thin bar filled to a percentage. kind: "success", "warning", "danger" or "neutral". */
export function bar(percentage, kind = "") {
  return h(
    "div.bar" + (kind ? ".bar--" + kind : ""),
    h("div.bar__fill", { style: { "--value": Math.max(0, Math.min(100, percentage)) } }),
  );
}

/**
 * Wires a tab control: buttons with role="tab" inside an element with the class "segmented".
 * onChange receives the value of the chosen tab (its data-value).
 */
export function segmented(control, onChange) {
  const options = [...control.querySelectorAll('[role="tab"]')];
  const thumb = h("span.segmented__thumb", { "aria-hidden": "true" });
  control.prepend(thumb);

  function moveThumb() {
    const chosen = options.find((option) => option.getAttribute("aria-selected") === "true");
    if (chosen) {
      thumb.style.width = chosen.offsetWidth + "px";
      thumb.style.transform = `translateX(${chosen.offsetLeft}px)`;
    }
  }

  function choose(option, announce = true) {
    for (const other of options) {
      const selected = other === option;
      other.setAttribute("aria-selected", String(selected));
      other.tabIndex = selected ? 0 : -1;
    }
    moveThumb();
    if (announce) {
      onChange(option.dataset.value);
    }
  }

  options.forEach((option, index) => {
    option.addEventListener("click", () => choose(option));
    option.addEventListener("keydown", (event) => {
      const step = event.key === "ArrowRight" ? 1 : event.key === "ArrowLeft" ? -1 : 0;
      if (step) {
        const next = options[(index + step + options.length) % options.length];
        next.focus();
        choose(next);
      }
    });
  });
  window.addEventListener("resize", moveThumb);
  choose(options.find((option) => option.getAttribute("aria-selected") === "true") || options[0], false);
  return { choose: (value) => choose(options.find((option) => option.dataset.value === value)) };
}

/**
 * Swaps the content of a container with a short cross-fade, used when a filter or tab changes.
 * render must put the new content in place.
 */
export function swap(container, render) {
  if (reduceMotion.matches || !container.animate) {
    render();
    reveal(container);
    return;
  }
  const leaving = container.animate(
    [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "translateY(6px)" }],
    { duration: 130, easing: "ease-out" },
  );
  let done = false;
  const enter = () => {
    if (done) {
      return;
    }
    done = true;
    render();
    reveal(container);
    container.animate(
      [{ opacity: 0, transform: "translateY(10px)" }, { opacity: 1, transform: "none" }],
      { duration: 320, easing: "cubic-bezier(0.2, 0.7, 0.2, 1)" },
    );
  };
  leaving.onfinish = enter;
  // A tab in the background does not finish animations, so a timer makes sure the new content
  // is always put in place.
  setTimeout(enter, 220);
}

/**
 * Shrinks an element away, then calls done. done always runs once, even when the tab is in the
 * background and the browser does not finish the animation.
 */
export function leave(element, done) {
  if (reduceMotion.matches || !element.animate) {
    done();
    return;
  }
  let finished = false;
  const finish = () => {
    if (!finished) {
      finished = true;
      done();
    }
  };
  const leaving = element.animate(
    [{ opacity: 1, transform: "none" }, { opacity: 0, transform: "scale(0.94)" }],
    { duration: 220, easing: "ease-out", fill: "forwards" },
  );
  leaving.onfinish = finish;
  setTimeout(finish, 320);
}
