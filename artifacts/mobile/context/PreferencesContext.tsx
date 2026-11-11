import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import {
  Language,
  LOCALE,
  TranslateParams,
  TranslationKey,
  translate,
} from "@/constants/i18n";
import { isObject, readJSON, writeJSON } from "@/utils/storage";

const PREFS_KEY = "@gymtracker_preferences";
const DEFAULT_REST_SECONDS = 180; // 3 minutes
const DEFAULT_LANGUAGE: Language = "fr";

interface Preferences {
  language: Language;
  restSeconds: number;
}

const DEFAULT_PREFS: Preferences = { language: DEFAULT_LANGUAGE, restSeconds: DEFAULT_REST_SECONDS };

interface PreferencesContextType {
  language: Language;
  /** BCP-47 locale tag for Intl formatting, derived from `language`. */
  locale: string;
  restSeconds: number;
  setLanguage: (language: Language) => void;
  setRestSeconds: (restSeconds: number) => void;
  t: (key: TranslationKey, params?: TranslateParams) => string;
  /** Re-read preferences from storage (after a backup import). */
  reload: () => Promise<void>;
}

const PreferencesContext = createContext<PreferencesContextType | undefined>(undefined);

export function PreferencesProvider({ children }: { children: React.ReactNode }) {
  const [prefs, setPrefs] = useState<Preferences>(DEFAULT_PREFS);
  const prefsRef = useRef(DEFAULT_PREFS);

  useEffect(() => {
    reload();
  }, []);

  async function reload() {
    const p = await readJSON<Partial<Preferences>>(PREFS_KEY, isObject);
    const next: Preferences = {
      language: p?.language === "fr" || p?.language === "en" ? p.language : DEFAULT_LANGUAGE,
      restSeconds:
        typeof p?.restSeconds === "number" && p.restSeconds > 0 ? p.restSeconds : DEFAULT_REST_SECONDS,
    };
    prefsRef.current = next;
    setPrefs(next);
  }

  function update(patch: Partial<Preferences>) {
    const next = { ...prefsRef.current, ...patch };
    prefsRef.current = next;
    setPrefs(next);
    writeJSON(PREFS_KEY, next);
  }

  return (
    <PreferencesContext.Provider
      value={{
        language: prefs.language,
        locale: LOCALE[prefs.language],
        restSeconds: prefs.restSeconds,
        setLanguage: (language) => update({ language }),
        setRestSeconds: (restSeconds) => update({ restSeconds }),
        t: (key, params) => translate(prefs.language, key, params),
        reload,
      }}
    >
      {children}
    </PreferencesContext.Provider>
  );
}

export function usePreferences() {
  const ctx = useContext(PreferencesContext);
  if (!ctx) throw new Error("usePreferences must be used within PreferencesProvider");
  return ctx;
}
