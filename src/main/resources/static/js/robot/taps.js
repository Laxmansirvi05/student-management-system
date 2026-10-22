// Tap counting for the robot: a double tap and a triple tap on one gesture stream.

export const TAP_WINDOW_MS = 600;
export const REQUIRED_TAPS = 3;
export const SETTLE_MS = 260;

/**
 * Distinguishes a double tap from a triple tap. The second tap cannot act at once, or every triple
 * tap would open the Quick Assistant on its way to opening the Quick Note. So the double-tap action
 * waits SETTLE_MS for a possible third tap and is cancelled if one arrives: the usual cost of
 * overloading one gesture, and the reason the wait is short enough not to feel slow.
 *
 * The clock and the timers are passed in so the logic can be tested without real time.
 */
export function createMultiTapDetector({ onDoubleTap, onTripleTap }, { now = Date.now, schedule = setTimeout, cancel = clearTimeout } = {}) {
  let timestamps = [];
  let pending = null;

  function clearPending() {
    if (pending !== null) {
      cancel(pending);
      pending = null;
    }
  }

  function reset() {
    clearPending();
    timestamps = [];
  }

  function registerTap() {
    const time = now();
    timestamps = timestamps.filter((stamp) => time - stamp <= TAP_WINDOW_MS);
    timestamps.push(time);
    if (timestamps.length >= REQUIRED_TAPS) {
      // A third tap beats the pending double: cancel it before it acts.
      clearPending();
      timestamps = [];
      onTripleTap();
      return;
    }
    if (timestamps.length === 2) {
      clearPending();
      pending = schedule(() => {
        pending = null;
        timestamps = [];
        onDoubleTap();
      }, SETTLE_MS);
    }
  }

  return { registerTap, reset };
}
