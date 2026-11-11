export type MuscleGroup =
  | "dos"
  | "pecs"
  | "biceps"
  | "triceps"
  | "jambes"
  | "épaules"
  | "abdos"
  | "cardio"
  | "avant-bras";

/** Display order of the muscle groups (filters, Progrès tabs, pickers). */
export const MUSCLE_GROUPS: MuscleGroup[] = [
  "dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio", "avant-bras",
];

export const MUSCLE_COLORS: Record<MuscleGroup, string> = {
  dos: "#3b82f6",
  pecs: "#f97316",
  biceps: "#a855f7",
  triceps: "#8b5cf6",
  jambes: "#22c55e",
  épaules: "#f59e0b",
  abdos: "#ef4444",
  cardio: "#ec4899",
  "avant-bras": "#14b8a6",
};
