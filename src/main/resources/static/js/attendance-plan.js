// The attendance planner's arithmetic (FR-ATT-003), with no DOM so it can be tested. All counts are
// whole classes and a target is a percentage such as 65 or 75.5. Comparisons are done in whole
// numbers (hundredths of a percent), never with floating-point percentages, so 65% of 100 classes is
// exactly 65 and not 66.

/** The target in hundredths of a percent, as a whole number (65 -> 6500, 75.5 -> 7550). */
const basis = (target) => Math.round(Number(target) * 100);

/**
 * How many classes in a row must be attended to reach the target, or (when it is already reached)
 * how many can be missed in a row and still keep it.
 */
export function plan(attended, held, target) {
  const t = basis(target);
  if (held > 0 && attended * 10000 >= t * held) {
    return { reached: true, classes: Math.floor((attended * 10000 - t * held) / t) };
  }
  if (t >= 10000) return { reached: false, classes: null }; // 100% cannot be reached after a miss
  return { reached: false, classes: Math.ceil((t * held - attended * 10000) / (10000 - t)) };
}

/**
 * For a whole term of `total` classes: the fewest to attend so that attended / total is at least the
 * target, and the most that can be missed. 48 classes at 65% -> attend 32 (31 is 64.58%), miss 16.
 * When classes have already happened (`held`, of which `attended`), it also says how many of the
 * remaining ones must still be attended, or that the target can no longer be reached.
 */
export function termPlan(total, target, attended = 0, held = 0) {
  const classes = Math.max(0, Math.floor(total));
  const t = basis(target);
  const mustAttend = Math.floor((t * classes + 9999) / 10000); // ceiling, in whole numbers
  const canMiss = classes - mustAttend;
  const remaining = Math.max(0, classes - held);
  const stillNeeded = Math.max(0, mustAttend - attended);
  const possible = stillNeeded <= remaining;
  const best = classes === 0 ? 0 : ((attended + remaining) * 100) / classes;
  return { total: classes, mustAttend, canMiss, remaining, stillNeeded, possible, bestPercent: best, missedSoFar: Math.max(0, held - attended), canStillMiss: Math.max(0, remaining - stillNeeded) };
}
