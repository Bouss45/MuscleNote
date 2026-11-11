// Estimated one-rep max (1RM) — Epley formula: weight × (1 + reps/30).
// For a single rep the lifted weight *is* the 1RM. Returns a rounded kg value.

export function estimateOneRepMax(weight: number, reps: number): number {
  if (weight <= 0 || reps <= 0) return 0;
  if (reps === 1) return weight;
  return Math.round(weight * (1 + reps / 30));
}

/** Best estimated 1RM across a list of sets (reps/weight). */
export function bestOneRepMax(sets: { weight: number; reps: number }[]): number {
  let best = 0;
  for (const s of sets) {
    const e = estimateOneRepMax(s.weight, s.reps);
    if (e > best) best = e;
  }
  return best;
}
