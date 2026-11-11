/** Unique-enough local id: timestamp + random suffix. */
export function generateId(): string {
  return Date.now().toString() + Math.random().toString(36).slice(2, 11);
}
