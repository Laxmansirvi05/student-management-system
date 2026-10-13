// The sign-in mascot. Its eyes follow the pointer, it leans a little toward it, a soft light
// follows the pointer across the black panel, and it shuts its eyes while a password is typed.
// It is decoration only: hidden from screen readers, and still when reduced motion is requested.

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

export function startMascot({ panel, passwordInputs = [] }) {
  const mascot = panel.querySelector(".mascot");
  if (!mascot) {
    return;
  }
  const pupils = mascot.querySelectorAll(".mascot__pupil");

  function follow(event) {
    if (reduceMotion.matches) {
      return;
    }
    const face = mascot.getBoundingClientRect();
    const dx = event.clientX - (face.left + face.width / 2);
    const dy = event.clientY - (face.top + face.height / 2);
    const distance = Math.hypot(dx, dy) || 1;
    // The pupils move at most 5 units inside the eye; the head leans at most 6 pixels.
    const reach = Math.min(1, distance / 260);
    const eyeX = (dx / distance) * 5 * reach;
    const eyeY = (dy / distance) * 5 * reach;
    for (const pupil of pupils) {
      pupil.style.transform = `translate(${eyeX}px, ${eyeY}px)`;
    }
    mascot.style.transform = `translate(${(dx / distance) * 6 * reach}px, ${(dy / distance) * 6 * reach}px)`;

    const area = panel.getBoundingClientRect();
    panel.style.setProperty("--glow-x", `${event.clientX - area.left}px`);
    panel.style.setProperty("--glow-y", `${event.clientY - area.top}px`);
  }

  window.addEventListener("pointermove", follow, { passive: true });

  for (const input of passwordInputs) {
    input.addEventListener("focus", () => mascot.classList.add("mascot--shy"));
    input.addEventListener("blur", () => mascot.classList.remove("mascot--shy"));
  }
}
