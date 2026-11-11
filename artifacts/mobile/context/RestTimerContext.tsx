import * as Haptics from "expo-haptics";
import * as Notifications from "expo-notifications";
import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import { AppState, Platform } from "react-native";
import { usePreferences } from "@/context/PreferencesContext";

// One rest timer for the whole session (starting a new rest replaces the running
// one), living above the navigator so it survives leaving the workout screen.
// Timestamp-based so it stays correct across screen lock / background.
//
// Double-alert avoidance: an OS notification is scheduled for the end of the rest;
// if the timer expires while the app is in the foreground we only fire haptics and
// cancel that notification, otherwise the notification fires on its own.

interface RestTimerContextType {
  /** Seconds remaining, or null when no rest is running. */
  secondsLeft: number | null;
  /** Duration of the running rest, in seconds. */
  total: number;
  start: (exerciseName: string) => void;
  stop: () => void;
}

const RestTimerContext = createContext<RestTimerContextType | undefined>(undefined);

export function RestTimerProvider({ children }: { children: React.ReactNode }) {
  const { t, restSeconds } = usePreferences();
  const [endsAt, setEndsAt] = useState<number | null>(null);
  const [total, setTotal] = useState(restSeconds);
  const [now, setNow] = useState(Date.now());
  const notifIdRef = useRef<string | null>(null);
  // Bumped on every start/stop, so a notification whose scheduling resolves
  // after the rest was already replaced or skipped gets cancelled right away.
  const generationRef = useRef(0);

  function cancelNotif() {
    generationRef.current++;
    const id = notifIdRef.current;
    notifIdRef.current = null;
    if (id) Notifications.cancelScheduledNotificationAsync(id).catch(() => {});
  }

  useEffect(() => {
    if (endsAt === null) return;
    const tick = () => {
      const n = Date.now();
      setNow(n);
      if (n >= endsAt) {
        setEndsAt(null);
        if (AppState.currentState === "active") {
          Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
          cancelNotif();
        }
      }
    };
    tick();
    const id = setInterval(tick, 1000);
    const sub = AppState.addEventListener("change", (s) => s === "active" && tick());
    return () => {
      clearInterval(id);
      sub.remove();
    };
  }, [endsAt]);

  function start(exerciseName: string) {
    cancelNotif();
    setTotal(restSeconds);
    setNow(Date.now());
    setEndsAt(Date.now() + restSeconds * 1000);
    const generation = generationRef.current;
    Notifications.scheduleNotificationAsync({
      content: {
        title: t("restTimer.notifTitle"),
        body: t("restTimer.notifBody", { exercise: exerciseName }),
        sound: true,
      },
      trigger: {
        type: Notifications.SchedulableTriggerInputTypes.TIME_INTERVAL,
        seconds: restSeconds,
        ...(Platform.OS === "android" && { channelId: "rest-timer" }),
      },
    })
      .then((id) => {
        if (generation === generationRef.current) notifIdRef.current = id;
        else Notifications.cancelScheduledNotificationAsync(id).catch(() => {});
      })
      .catch(() => {});
  }

  function stop() {
    setEndsAt(null);
    cancelNotif();
  }

  const secondsLeft = endsAt === null ? null : Math.max(0, Math.round((endsAt - now) / 1000));

  return (
    <RestTimerContext.Provider value={{ secondsLeft, total, start, stop }}>
      {children}
    </RestTimerContext.Provider>
  );
}

export function useRestTimer() {
  const ctx = useContext(RestTimerContext);
  if (!ctx) throw new Error("useRestTimer must be used within RestTimerProvider");
  return ctx;
}
