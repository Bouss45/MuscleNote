// Numeric input parsing. French keyboards type "62,5", which parseFloat would
// silently truncate to 62 — always accept a comma as the decimal separator.

/** Parse a user-typed decimal ("62,5", "62.5", " 70 "). Returns 0 when empty/invalid. */
export function parseDecimal(input: string): number {
  const n = parseFloat(input.trim().replace(",", "."));
  return Number.isFinite(n) ? n : 0;
}

/** Parse a user-typed integer. Returns 0 when empty/invalid. */
export function parseInteger(input: string): number {
  const n = parseInt(input.trim(), 10);
  return Number.isFinite(n) ? n : 0;
}
