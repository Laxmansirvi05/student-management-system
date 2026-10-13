// Decorative motion shared by pages: cards that lean toward the pointer, and numbers that count
// up. Nothing here changes what a page says, and all of it is skipped when the device asks for
// reduced motion or has no hovering pointer.

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const canHover = window.matchMedia("(hover: hover) and (pointer: fine)");

/** Makes every element with data-tilt lean a few degrees toward the pointer. */
export function enableTilt(root = document, maxDegrees = 5) {
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

/** Shows a whole number, counting up to it over about half a second. */
export function countUp(element, target) {
  if (reduceMotion.matches || !Number.isFinite(target)) {
    element.textContent = String(target);
    return;
  }
  // Large values such as a year start close to the end, so the count stays short.
  const start = Math.max(0, target - 24);
  const duration = 650;
  const began = performance.now();
  function step(now) {
    const progress = Math.min(1, (now - began) / duration);
    const eased = 1 - Math.pow(1 - progress, 3);
    element.textContent = String(Math.round(start + (target - start) * eased));
    if (progress < 1) {
      requestAnimationFrame(step);
    }
  }
  requestAnimationFrame(step);
}
