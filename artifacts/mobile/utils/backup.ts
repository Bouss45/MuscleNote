// JSON backup format: `{ version, exportedAt, "<storage key>": <parsed value>, ... }`.
// The in-progress session (`@gymtracker_current_workout`) is deliberately not included.

export const BACKUP_KEYS = [
  "@gymtracker_workouts",
  "@gymtracker_exercises",
  "@gymtracker_body_weights",
  "@gymtracker_supplements",
  "@gymtracker_supplements_taken",
  "@gymtracker_daily_tracker",
  "@gymtracker_preferences",
] as const;

type BackupKey = (typeof BACKUP_KEYS)[number];

const isObj = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);
const isNum = (v: unknown) => typeof v === "number" && Number.isFinite(v);
const isStr = (v: unknown) => typeof v === "string";
const optional = (check: (v: unknown) => boolean) => (v: unknown) => v === undefined || v === null || check(v);
const arrayOf = (check: (v: unknown) => boolean) => (v: unknown) => Array.isArray(v) && v.every(check);

const isSet = (v: unknown) =>
  isObj(v) && isNum(v.reps) && isNum(v.weight) &&
  ["duration", "distance", "speed", "incline"].every((k) => optional(isNum)(v[k]));

const isWorkout = (v: unknown) =>
  isObj(v) && isStr(v.id) && isStr(v.date) && !Number.isNaN(Date.parse(v.date as string)) &&
  optional(isStr)(v.name) && optional(isNum)(v.bodyWeight) &&
  arrayOf((e) => isObj(e) && isStr(e.exerciseId) && arrayOf(isSet)(e.sets))(v.exercises);

const VALIDATORS: Record<BackupKey, (v: unknown) => boolean> = {
  "@gymtracker_workouts": arrayOf(isWorkout),
  "@gymtracker_exercises": arrayOf((e) => isObj(e) && isStr(e.id) && isStr(e.name) && isStr(e.muscleGroup)),
  "@gymtracker_body_weights": arrayOf((e) => isObj(e) && isStr(e.date) && isNum(e.weight)),
  "@gymtracker_supplements": arrayOf((e) => isObj(e) && isStr(e.id) && isStr(e.name)),
  "@gymtracker_supplements_taken": isObj,
  "@gymtracker_daily_tracker": isObj,
  "@gymtracker_preferences": isObj,
};

/** Fill fields that older app versions could leave out. */
function normalize(key: BackupKey, value: unknown): unknown {
  if (key !== "@gymtracker_workouts") return value;
  return (value as Record<string, unknown>[]).map((w) => ({ ...w, name: w.name ?? "", bodyWeight: w.bodyWeight ?? 0 }));
}

export type ParsedBackup =
  | { ok: true; pairs: [string, string][] }
  | { ok: false; error: "invalidFormat" | "noData" | "invalidKey"; key?: string };

/** Validate a backup file and turn it into AsyncStorage `multiSet` pairs. */
export function parseBackup(json: string): ParsedBackup {
  let data: unknown;
  try {
    data = JSON.parse(json);
  } catch {
    return { ok: false, error: "invalidFormat" };
  }
  if (!isObj(data)) return { ok: false, error: "invalidFormat" };

  const pairs: [string, string][] = [];
  for (const key of BACKUP_KEYS) {
    if (data[key] === undefined) continue;
    if (!VALIDATORS[key](data[key])) return { ok: false, error: "invalidKey", key };
    pairs.push([key, JSON.stringify(normalize(key, data[key]))]);
  }
  if (pairs.length === 0) return { ok: false, error: "noData" };
  return { ok: true, pairs };
}
