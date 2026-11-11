import { fr, type TranslationKey } from "./fr";
import { en } from "./en";

export type Language = "fr" | "en";
export type { TranslationKey } from "./fr";
export type TranslateParams = Record<string, string | number>;

export const translations: Record<Language, Partial<Record<TranslationKey, string>>> = {
  fr,
  en,
};

export const LANGUAGES: { code: Language; label: string; flag: string }[] = [
  { code: "fr", label: "Français", flag: "🇫🇷" },
  { code: "en", label: "English", flag: "🇬🇧" },
];

/** Locale tag used for Intl date/number formatting. */
export const LOCALE: Record<Language, string> = {
  fr: "fr-FR",
  en: "en-US",
};

/**
 * Translate `key` into `language`, with `{{var}}` interpolation.
 * Pluralization: when `params.count === 1`, a `<key>_one` variant is preferred.
 * Falls back to the French string, then to the raw key, if a translation is absent.
 */
export function translate(
  language: Language,
  key: TranslationKey,
  params?: TranslateParams,
): string {
  const dict = translations[language] ?? {};

  let lookupKey = key as string;
  if (params && typeof params.count === "number" && params.count === 1) {
    const oneKey = `${key}_one`;
    if (
      (dict as Record<string, string>)[oneKey] !== undefined ||
      (fr as Record<string, string>)[oneKey] !== undefined
    ) {
      lookupKey = oneKey;
    }
  }

  let str =
    (dict as Record<string, string>)[lookupKey] ??
    (fr as Record<string, string>)[lookupKey] ??
    (fr as Record<string, string>)[key] ??
    key;

  if (params) {
    for (const k of Object.keys(params)) {
      str = str.split(`{{${k}}}`).join(String(params[k]));
    }
  }
  return str;
}
