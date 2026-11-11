import AsyncStorage from "@react-native-async-storage/async-storage";

/**
 * Read and parse a JSON value. Returns `undefined` when the key is absent.
 *
 * If the stored value is unreadable (bad JSON, or rejected by `isValid`), the raw
 * string is first copied to `<key>_corrupt_<timestamp>` and `undefined` is
 * returned: the app then starts from empty state, and the next save can no longer
 * destroy the only copy of the user's data.
 */
export async function readJSON<T>(
  key: string,
  isValid: (v: unknown) => boolean = () => true,
): Promise<T | undefined> {
  let raw: string | null;
  try {
    raw = await AsyncStorage.getItem(key);
  } catch (e) {
    console.warn(`[storage] read ${key} failed`, e);
    return undefined;
  }
  if (raw === null) return undefined;
  try {
    const value: unknown = JSON.parse(raw);
    if (isValid(value)) return value as T;
  } catch {}
  console.warn(`[storage] ${key} is unreadable — raw value backed up`);
  await AsyncStorage.setItem(`${key}_corrupt_${Date.now()}`, raw).catch(() => {});
  return undefined;
}

/** Serialize and write a JSON value; failures are logged, never thrown. */
export function writeJSON(key: string, value: unknown): Promise<void> {
  return AsyncStorage.setItem(key, JSON.stringify(value)).catch((e) => {
    console.warn(`[storage] write ${key} failed`, e);
  });
}

export function removeKey(key: string): Promise<void> {
  return AsyncStorage.removeItem(key).catch((e) => {
    console.warn(`[storage] remove ${key} failed`, e);
  });
}

export const isArray = (v: unknown) => Array.isArray(v);
export const isObject = (v: unknown) => typeof v === "object" && v !== null && !Array.isArray(v);
