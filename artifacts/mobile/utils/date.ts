// Calendar-day helpers. Daily logs (calories, water, supplements taken) are keyed
// by the *local* day — `toISOString().slice(0, 10)` would give the UTC day, which
// in France rolls over at 1–2 am instead of midnight.

function pad2(n: number) {
  return String(n).padStart(2, "0");
}

/** Local calendar day of `date` as "YYYY-MM-DD". */
export function dayKey(date: Date = new Date()): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** Local calendar day of an ISO timestamp as "YYYY-MM-DD". */
export function dayKeyOf(iso: string): string {
  return dayKey(new Date(iso));
}

/** Parse a "YYYY-MM-DD" day key as local noon (avoids `new Date(key)`, which is UTC midnight). */
export function parseDayKey(key: string): Date {
  const [y, m, d] = key.split("-").map(Number);
  return new Date(y, m - 1, d, 12);
}

/**
 * Build a local Date from user-typed parts, or null if they don't form a real
 * date/time (31/02, hour 25…) — `new Date()` would otherwise silently roll over.
 */
export function buildLocalDate(
  year: number,
  month: number, // 1–12
  day: number,
  hour = 12,
  minute = 0,
): Date | null {
  if (![year, month, day, hour, minute].every(Number.isInteger)) return null;
  if (year < 2000 || year > 2100 || hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;
  const d = new Date(year, month - 1, day, hour, minute);
  if (d.getFullYear() !== year || d.getMonth() !== month - 1 || d.getDate() !== day) return null;
  return d;
}
