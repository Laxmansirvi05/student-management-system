// The sign-in robot. Its face turns toward the pointer, the head leans a little the same way, a
// soft light follows the pointer across the black panel, and its eyes become two flat lines while
// a password is typed. It is decoration only: hidden from screen readers, and still when reduced
// motion is requested.

const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");

export function startMascot({ panel, passwordInputs = [] }) {
  const mascot = panel.querySelector(".mascot");
  if (!mascot) {
    return;
  }
  const face = mascot.querySelector(".mascot__face");
  const antenna = mascot.querySelector(".mascot__antenna");

  function follow(event) {
    if (reduceMotion.matches) {
      return;
    }
    const head = mascot.getBoundingClientRect();
    const dx = event.clientX - (head.left + head.width / 2);
    const dy = event.clientY - (head.top + head.height / 2);
    const distance = Math.hypot(dx, dy) || 1;
    // The further away the pointer is, the further the robot looks, up to a fixed limit.
    const reach = Math.min(1, distance / 260);
    const lookX = (dx / distance) * reach;
    const lookY = (dy / distance) * reach;
    // The face slides inside the helmet; the head leans; the antenna swings the other way.
    face.style.transform = `translate(${lookX * 16}px, ${lookY * 9}px)`;
    mascot.style.transform = `translate(${lookX * 8}px, ${lookY * 8}px)`;
    antenna.style.transform = `rotate(${lookX * -10}deg)`;

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
