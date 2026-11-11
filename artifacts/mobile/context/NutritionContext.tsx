import * as Notifications from "expo-notifications";
import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import { AppState, Platform } from "react-native";
import { usePreferences } from "@/context/PreferencesContext";
import { dayKey } from "@/utils/date";
import { generateId } from "@/utils/id";
import { isArray, isObject, readJSON, writeJSON } from "@/utils/storage";

// Nutrition-side data shared by the Nutrition and Progrès tabs: daily calorie /
// water tracker, supplements (+ their scheduled reminders) and manual weigh-ins.
// Keeping it in one provider means every screen sees the same, fresh state, and
// `reload()` after a backup import updates all of them at once.

export interface Reminder {
  id: string;
  hour: number;
  minute: number;
  notifId?: string;
}

export interface Supplement {
  id: string;
  name: string;
  dosage: string;
  unit: string;
  reminderEnabled: boolean;
  reminders: Reminder[];
}

/** supplementId → day key ("YYYY-MM-DD") of the last day it was taken. */
export type TakenRecord = Record<string, string>;

export interface DailyTracker {
  calorieGoal: number;
  waterGoal: number;   // ml
  caloriesLog: Record<string, number>; // day key → kcal
  waterLog: Record<string, number>;    // day key → ml
  bottleSize: number;  // ml per bottle
}

export interface BodyWeightEntry {
  date: string; // ISO timestamp (local noon of the weigh-in day)
  weight: number;
}

export interface NewSupplement {
  name: string;
  dosage: string;
  unit: string;
  reminderEnabled: boolean;
  reminders: { hour: number; minute: number }[];
}

const SUPPLEMENTS_KEY = "@gymtracker_supplements";
const TAKEN_KEY = "@gymtracker_supplements_taken";
const TRACKER_KEY = "@gymtracker_daily_tracker";
const BODYWEIGHTS_KEY = "@gymtracker_body_weights";

const DEFAULT_TRACKER: DailyTracker = {
  calorieGoal: 2000,
  waterGoal: 2000,
  caloriesLog: {},
  waterLog: {},
  bottleSize: 500,
};

/** Marks our daily reminders so stale ones can be found and cancelled. */
const SUPPLEMENT_NOTIF_KIND = "supplement";

interface NutritionContextType {
  /** Today's local day key — refreshed when the app resumes or the day rolls over. */
  today: string;
  tracker: DailyTracker;
  supplements: Supplement[];
  taken: TakenRecord;
  bodyWeights: BodyWeightEntry[];
  addCalories: (amount: number) => void;
  setCaloriesToday: (value: number) => void;
  setCalorieGoal: (kcal: number) => void;
  addWater: (deltaMl: number) => void;
  setWaterGoal: (ml: number) => void;
  toggleTaken: (supplementId: string) => void;
  addSupplement: (sup: NewSupplement) => void;
  deleteSupplement: (supplementId: string) => void;
  setRemindersEnabled: (supplementId: string, enabled: boolean) => void;
  addReminder: (supplementId: string) => void;
  /** Move a reminder by `delta` hours or quarter-hours (wraps around). */
  shiftReminder: (supplementId: string, reminderId: string, field: "hour" | "minute", delta: number) => void;
  removeReminder: (supplementId: string, reminderId: string) => void;
  addBodyWeight: (dateIso: string, weight: number) => void;
  removeBodyWeight: (dateIso: string) => void;
  reload: () => Promise<void>;
}

const NutritionContext = createContext<NutritionContextType | undefined>(undefined);

// ─── Data migrations ──────────────────────────────────────────────────────────
// Old supplements had a single reminderHour/reminderMinute instead of a list.
function migrateSupplement(raw: any): Supplement {
  if (raw.reminders) return raw as Supplement;
  const reminder: Reminder = { id: generateId(), hour: raw.reminderHour ?? 8, minute: raw.reminderMinute ?? 0, notifId: raw.notifId };
  return {
    id: raw.id,
    name: raw.name,
    dosage: raw.dosage ?? "",
    unit: raw.unit ?? "g",
    reminderEnabled: raw.reminderEnabled ?? false,
    reminders: raw.reminderEnabled ? [reminder] : [],
  };
}

// Old trackers counted water in glasses (≤ 30) instead of millilitres.
function migrateTracker(raw: any): DailyTracker {
  if (raw.bottleSize === undefined && raw.waterGoal !== undefined && raw.waterGoal <= 30) {
    raw.waterGoal = raw.waterGoal * 250;
    if (raw.waterLog) {
      raw.waterLog = Object.fromEntries(
        Object.entries(raw.waterLog).map(([k, v]) => [k, (v as number) * 250]),
      );
    }
    raw.bottleSize = 500;
  }
  return { ...DEFAULT_TRACKER, ...raw };
}

function sortWeighIns(list: BodyWeightEntry[]): BodyWeightEntry[] {
  return [...list].sort((a, b) => b.date.localeCompare(a.date));
}

async function hasNotifPermission(ask: boolean): Promise<boolean> {
  try {
    const { status } = await Notifications.getPermissionsAsync();
    if (status === "granted") return true;
    if (!ask) return false;
    return (await Notifications.requestPermissionsAsync()).status === "granted";
  } catch {
    return false;
  }
}

export function NutritionProvider({ children }: { children: React.ReactNode }) {
  const { t, language } = usePreferences();
  const [today, setToday] = useState(dayKey());
  const [tracker, setTrackerState] = useState<DailyTracker>(DEFAULT_TRACKER);
  const [supplements, setSupplementsState] = useState<Supplement[]>([]);
  const [taken, setTakenState] = useState<TakenRecord>({});
  const [bodyWeights, setBodyWeightsState] = useState<BodyWeightEntry[]>([]);

  // Latest committed values, so rapid successive actions never work on stale data.
  const trackerRef = useRef(DEFAULT_TRACKER);
  const supplementsRef = useRef<Supplement[]>([]);
  const takenRef = useRef<TakenRecord>({});
  const bodyWeightsRef = useRef<BodyWeightEntry[]>([]);
  // Notification (un)scheduling is async: run those jobs one at a time, each
  // reading the latest supplements, so overlapping taps can't orphan a reminder.
  const notifQueue = useRef<Promise<void>>(Promise.resolve());

  useEffect(() => {
    reload();
  }, []);

  // Notification texts are localized: reschedule when the language changes.
  useEffect(() => {
    resyncAllReminders();
  }, [language]);

  // Keep `today` current across midnight and app resumes.
  useEffect(() => {
    const refresh = () => setToday(dayKey());
    const sub = AppState.addEventListener("change", (s) => s === "active" && refresh());
    const id = setInterval(refresh, 60_000);
    return () => {
      sub.remove();
      clearInterval(id);
    };
  }, []);

  function commitTracker(next: DailyTracker) {
    trackerRef.current = next;
    setTrackerState(next);
    writeJSON(TRACKER_KEY, next);
  }
  function commitSupplements(next: Supplement[]) {
    supplementsRef.current = next;
    setSupplementsState(next);
    writeJSON(SUPPLEMENTS_KEY, next);
  }
  function commitTaken(next: TakenRecord) {
    takenRef.current = next;
    setTakenState(next);
    writeJSON(TAKEN_KEY, next);
  }
  function commitBodyWeights(next: BodyWeightEntry[]) {
    bodyWeightsRef.current = next;
    setBodyWeightsState(next);
    writeJSON(BODYWEIGHTS_KEY, next);
  }

  async function reload() {
    const [sups, takenRaw, trackerRaw, weighIns] = await Promise.all([
      readJSON<any[]>(SUPPLEMENTS_KEY, isArray),
      readJSON<TakenRecord>(TAKEN_KEY, isObject),
      readJSON<any>(TRACKER_KEY, isObject),
      readJSON<BodyWeightEntry[]>(BODYWEIGHTS_KEY, isArray),
    ]);
    const nextSups = (sups ?? []).map(migrateSupplement);
    supplementsRef.current = nextSups;
    setSupplementsState(nextSups);
    takenRef.current = takenRaw ?? {};
    setTakenState(takenRef.current);
    trackerRef.current = trackerRaw ? migrateTracker(trackerRaw) : DEFAULT_TRACKER;
    setTrackerState(trackerRef.current);
    bodyWeightsRef.current = sortWeighIns(weighIns ?? []);
    setBodyWeightsState(bodyWeightsRef.current);
    // Stored notification ids may be stale (imported backup, earlier bugs): rebuild them.
    resyncAllReminders();
  }

  // ─── Notification jobs ────────────────────────────────────────────────────
  function enqueue(job: () => Promise<void>) {
    notifQueue.current = notifQueue.current.then(job).catch((e) => console.warn("[reminders]", e));
  }

  async function schedule(sup: Supplement, r: Reminder): Promise<string | undefined> {
    try {
      return await Notifications.scheduleNotificationAsync({
        content: {
          title: `💊 ${sup.name}`,
          body: sup.dosage
            ? t("nutrition.notifBodyDosage", { dosage: sup.dosage, unit: sup.unit })
            : t("nutrition.notifBody"),
          sound: true,
          data: { kind: SUPPLEMENT_NOTIF_KIND },
        },
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.DAILY,
          hour: r.hour,
          minute: r.minute,
          ...(Platform.OS === "android" && { channelId: "supplements" }),
        },
      });
    } catch (e) {
      console.warn("[reminders] schedule failed", e);
      return undefined;
    }
  }

  async function cancel(notifId?: string) {
    if (!notifId) return;
    await Notifications.cancelScheduledNotificationAsync(notifId).catch(() => {});
  }

  /** Store a freshly scheduled id — or cancel it if its reminder vanished meanwhile. */
  async function storeNotifId(supId: string, remId: string, notifId: string | undefined) {
    const exists = supplementsRef.current.some((s) => s.id === supId && s.reminders.some((r) => r.id === remId));
    if (!exists) return cancel(notifId);
    commitSupplements(
      supplementsRef.current.map((s) =>
        s.id !== supId ? s : { ...s, reminders: s.reminders.map((r) => (r.id === remId ? { ...r, notifId } : r)) },
      ),
    );
  }

  /** Bring one supplement's scheduled notifications in line with its current state. */
  function syncSupplement(supId: string, askPermission: boolean) {
    enqueue(async () => {
      const sup = supplementsRef.current.find((s) => s.id === supId);
      if (!sup) return;
      const allowed = sup.reminderEnabled && (await hasNotifPermission(askPermission));
      for (const rem of sup.reminders) {
        // Re-read: an earlier iteration may have committed a newer version.
        const latest = supplementsRef.current.find((s) => s.id === supId)?.reminders.find((r) => r.id === rem.id);
        if (!latest) continue;
        await cancel(latest.notifId);
        await storeNotifId(supId, rem.id, allowed ? await schedule(sup, latest) : undefined);
      }
    });
  }

  /** Cancel every supplement reminder the OS holds and reschedule from our data. */
  function resyncAllReminders() {
    enqueue(async () => {
      const scheduled = await Notifications.getAllScheduledNotificationsAsync().catch(() => []);
      for (const n of scheduled) {
        const isSupplement =
          n.content.data?.kind === SUPPLEMENT_NOTIF_KIND || (n.trigger as { type?: string } | null)?.type === "daily";
        if (isSupplement) await cancel(n.identifier);
      }
      const allowed = await hasNotifPermission(false);
      for (const sup of supplementsRef.current) {
        for (const rem of sup.reminders) {
          await storeNotifId(sup.id, rem.id, allowed && sup.reminderEnabled ? await schedule(sup, rem) : undefined);
        }
      }
    });
  }

  // ─── Daily tracker ────────────────────────────────────────────────────────
  function setDayValue(log: "caloriesLog" | "waterLog", value: number) {
    const cur = trackerRef.current;
    commitTracker({ ...cur, [log]: { ...cur[log], [dayKey()]: Math.max(0, value) } });
  }

  function addCalories(amount: number) {
    setDayValue("caloriesLog", (trackerRef.current.caloriesLog[dayKey()] ?? 0) + amount);
  }
  function setCaloriesToday(value: number) {
    setDayValue("caloriesLog", value);
  }
  function setCalorieGoal(kcal: number) {
    commitTracker({ ...trackerRef.current, calorieGoal: kcal });
  }
  function addWater(deltaMl: number) {
    setDayValue("waterLog", (trackerRef.current.waterLog[dayKey()] ?? 0) + deltaMl);
  }
  function setWaterGoal(ml: number) {
    commitTracker({ ...trackerRef.current, waterGoal: ml });
  }

  // ─── Supplements ──────────────────────────────────────────────────────────
  function toggleTaken(supplementId: string) {
    const next = { ...takenRef.current };
    const d = dayKey();
    if (next[supplementId] === d) delete next[supplementId];
    else next[supplementId] = d;
    commitTaken(next);
  }

  function updateSupplement(supId: string, fn: (s: Supplement) => Supplement) {
    commitSupplements(supplementsRef.current.map((s) => (s.id === supId ? fn(s) : s)));
  }

  function addSupplement(input: NewSupplement) {
    const sup: Supplement = {
      id: generateId(),
      name: input.name,
      dosage: input.dosage,
      unit: input.unit,
      reminderEnabled: input.reminderEnabled,
      reminders: input.reminderEnabled ? input.reminders.map((r) => ({ id: generateId(), hour: r.hour, minute: r.minute })) : [],
    };
    commitSupplements([...supplementsRef.current, sup]);
    syncSupplement(sup.id, true);
  }

  function deleteSupplement(supId: string) {
    const sup = supplementsRef.current.find((s) => s.id === supId);
    commitSupplements(supplementsRef.current.filter((s) => s.id !== supId));
    const ids = sup?.reminders.map((r) => r.notifId) ?? [];
    enqueue(async () => {
      for (const id of ids) await cancel(id);
    });
  }

  function setRemindersEnabled(supId: string, enabled: boolean) {
    updateSupplement(supId, (s) => ({ ...s, reminderEnabled: enabled }));
    syncSupplement(supId, enabled);
  }

  function addReminder(supId: string) {
    updateSupplement(supId, (s) => {
      const last = s.reminders[s.reminders.length - 1];
      return {
        ...s,
        reminders: [...s.reminders, { id: generateId(), hour: ((last?.hour ?? 8) + 2) % 24, minute: last?.minute ?? 0 }],
      };
    });
    syncSupplement(supId, true);
  }

  function shiftReminder(supId: string, remId: string, field: "hour" | "minute", delta: number) {
    updateSupplement(supId, (s) => ({
      ...s,
      reminders: s.reminders.map((r) => {
        if (r.id !== remId) return r;
        return field === "hour"
          ? { ...r, hour: (r.hour + delta + 24) % 24 }
          : { ...r, minute: (r.minute + delta * 15 + 60) % 60 };
      }),
    }));
    syncSupplement(supId, false);
  }

  function removeReminder(supId: string, remId: string) {
    const notifId = supplementsRef.current.find((s) => s.id === supId)?.reminders.find((r) => r.id === remId)?.notifId;
    updateSupplement(supId, (s) => ({ ...s, reminders: s.reminders.filter((r) => r.id !== remId) }));
    enqueue(() => cancel(notifId));
  }

  // ─── Body weight log ──────────────────────────────────────────────────────
  /** Add a weigh-in; replaces any existing entry for the same local day. */
  function addBodyWeight(dateIso: string, weight: number) {
    const day = dayKey(new Date(dateIso));
    commitBodyWeights(
      sortWeighIns([...bodyWeightsRef.current.filter((e) => dayKey(new Date(e.date)) !== day), { date: dateIso, weight }]),
    );
  }

  function removeBodyWeight(dateIso: string) {
    commitBodyWeights(bodyWeightsRef.current.filter((e) => e.date !== dateIso));
  }

  return (
    <NutritionContext.Provider
      value={{
        today,
        tracker,
        supplements,
        taken,
        bodyWeights,
        addCalories,
        setCaloriesToday,
        setCalorieGoal,
        addWater,
        setWaterGoal,
        toggleTaken,
        addSupplement,
        deleteSupplement,
        setRemindersEnabled,
        addReminder,
        shiftReminder,
        removeReminder,
        addBodyWeight,
        removeBodyWeight,
        reload,
      }}
    >
      {children}
    </NutritionContext.Provider>
  );
}

export function useNutrition() {
  const ctx = useContext(NutritionContext);
  if (!ctx) throw new Error("useNutrition must be used within NutritionProvider");
  return ctx;
}
