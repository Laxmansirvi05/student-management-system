// What a person chose in Settings and wants remembered on this device. It is kept in the browser
// only: nothing here is an account detail, and nothing here is sent to the server.

const REDUCE_MOTION = "reduce-motion";

export function reduceMotionChosen() {
  try {
    return localStorage.getItem(REDUCE_MOTION) === "1";
  } catch {
    // Storage can be switched off in the browser. The page then simply follows the device.
    return false;
  }
}

/** Marks the page, so the styles and js/motion.js both leave the animations out. */
export function applyPreferences() {
  document.documentElement.toggleAttribute("data-reduce-motion", reduceMotionChosen());
}

export function setReduceMotion(on) {
  try {
    if (on) {
      localStorage.setItem(REDUCE_MOTION, "1");
    } else {
      localStorage.removeItem(REDUCE_MOTION);
    }
  } catch {
    // Nothing to remember it in. It still applies until the page is left.
  }
  document.documentElement.toggleAttribute("data-reduce-motion", on);
}

applyPreferences();
