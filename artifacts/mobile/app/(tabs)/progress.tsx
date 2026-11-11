import { Feather } from "@expo/vector-icons";
import { router } from "expo-router";
import React, { useMemo, useState } from "react";
import {
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { useExerciseName } from "@/hooks/useExerciseName";
import { usePreferences } from "@/context/PreferencesContext";
import { useNutrition } from "@/context/NutritionContext";
import { TranslationKey } from "@/constants/i18n";
import { MUSCLE_COLORS, MUSCLE_GROUPS, MuscleGroup } from "@/constants/muscles";
import { Set as WorkoutSet, useWorkout } from "@/context/WorkoutContext";
import LineChart, { DataPoint } from "@/components/LineChart";
import { buildLocalDate, dayKeyOf, parseDayKey } from "@/utils/date";
import { parseDecimal } from "@/utils/number";

type Tab = "weight" | "calories" | "eau" | MuscleGroup;

// Slightly different shades for multiple exercise charts
const CHART_PALETTE = ["#16A34A", "#3b82f6", "#a855f7", "#f59e0b", "#ec4899"];

function formatShortDate(d: Date, locale: string) {
  return d.toLocaleDateString(locale, { day: "2-digit", month: "2-digit" });
}

/** Per-session metric for a muscle group: max load (kg), or total duration (min) for cardio. */
function sessionMetric(sets: WorkoutSet[], cardio: boolean): number {
  if (cardio) return sets.reduce((sum, s) => sum + (s.duration ?? 0), 0);
  return sets.reduce((max, s) => Math.max(max, s.weight), 0);
}

export default function ProgressScreen() {
  const colors = useColors();
  const { t, locale } = usePreferences();
  const insets = useSafeAreaInsets();
  const { workouts, exercises, exerciseById } = useWorkout();
  const exerciseName = useExerciseName();
  const { tracker, bodyWeights, addBodyWeight, removeBodyWeight, today } = useNutrition();
  const [activeTab, setActiveTab] = useState<Tab>("weight");
  const [showWeighIn, setShowWeighIn] = useState(false);
  const [weighInError, setWeighInError] = useState(false);
  const [weighInWeight, setWeighInWeight] = useState("");
  const [weighInDay, setWeighInDay] = useState("");
  const [weighInMonth, setWeighInMonth] = useState("");
  const [weighInYear, setWeighInYear] = useState("");

  function openWeighIn() {
    const now = new Date();
    setWeighInWeight("");
    setWeighInDay(String(now.getDate()));
    setWeighInMonth(String(now.getMonth() + 1));
    setWeighInYear(String(now.getFullYear()));
    setWeighInError(false);
    setShowWeighIn(true);
  }

  function saveWeighIn() {
    const w = parseDecimal(weighInWeight);
    const date = buildLocalDate(parseInt(weighInYear, 10), parseInt(weighInMonth, 10), parseInt(weighInDay, 10));
    if (w <= 0 || w > 500 || !date) {
      setWeighInError(true);
      return;
    }
    addBodyWeight(date.toISOString(), w);
    setShowWeighIn(false);
  }

  const topPad = insets.top + 10;
  const bottomPad = insets.bottom + 100;

  // ── Body weight chart ────────────────────────────────────────────────────
  const bodyWeightData: DataPoint[] = useMemo(() => {
    // Merge session-derived body weights with manual weigh-ins, one point per day
    // (a manual weigh-in overrides the session value for that day).
    const byDay = new Map<string, number>();
    for (const w of workouts) {
      if (!w.bodyWeight) continue;
      const day = dayKeyOf(w.date);
      if (!byDay.has(day)) byDay.set(day, w.bodyWeight); // newest session of the day wins
    }
    for (const e of bodyWeights) byDay.set(dayKeyOf(e.date), e.weight);
    return Array.from(byDay.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([day, value]) => ({ label: formatShortDate(parseDayKey(day), locale), value }));
  }, [workouts, bodyWeights, locale]);

  const bwStats = useMemo(() => {
    if (bodyWeightData.length === 0) return null;
    const vals = bodyWeightData.map((d) => d.value);
    const first = vals[0], last = vals[vals.length - 1];
    return { current: last, diff: last - first, min: Math.min(...vals), max: Math.max(...vals) };
  }, [bodyWeightData]);

  // ── Nutrition data ────────────────────────────────────────────────────────
  const calData: DataPoint[] = useMemo(() => {
    return Object.entries(tracker.caloriesLog)
      .filter(([, v]) => v > 0)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([day, value]) => ({ label: formatShortDate(parseDayKey(day), locale), value }));
  }, [tracker, locale]);

  const waterData: DataPoint[] = useMemo(() => {
    return Object.entries(tracker.waterLog)
      .filter(([, v]) => v > 0)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([day, value]) => ({ label: formatShortDate(parseDayKey(day), locale), value }));
  }, [tracker, locale]);

  const calStats = useMemo(() => {
    if (calData.length === 0) return null;
    const vals = calData.map((d) => d.value);
    const last7 = calData.slice(-7).map((d) => d.value);
    const avg7 = last7.length > 0 ? Math.round(last7.reduce((a, b) => a + b, 0) / last7.length) : 0;
    const best = Math.max(...vals);
    const todayVal = tracker.caloriesLog[today] ?? 0;
    const daysHit = vals.filter((v) => v >= tracker.calorieGoal).length;
    return { today: todayVal, avg7, best, daysHit, total: vals.length };
  }, [calData, tracker, today]);

  const waterStats = useMemo(() => {
    if (waterData.length === 0) return null;
    const vals = waterData.map((d) => d.value);
    const last7 = waterData.slice(-7).map((d) => d.value);
    const avg7 = last7.length > 0 ? Math.round((last7.reduce((a, b) => a + b, 0) / last7.length) * 10) / 10 : 0;
    const best = Math.max(...vals);
    const todayVal = tracker.waterLog[today] ?? 0;
    const daysHit = vals.filter((v) => v >= tracker.waterGoal).length;
    return { today: todayVal, avg7, best, daysHit, total: vals.length };
  }, [waterData, tracker, today]);

  // ── Sessions count per muscle group (for tab badges) ─────────────────────
  const muscleSessionCount = useMemo(() => {
    const counts: Record<MuscleGroup, number> = {} as Record<MuscleGroup, number>;
    for (const g of MUSCLE_GROUPS) counts[g] = 0;
    for (const w of workouts) {
      const groups = new Set<MuscleGroup>();
      for (const we of w.exercises) {
        if (we.sets.length === 0) continue;
        const ex = exerciseById.get(we.exerciseId);
        if (ex) groups.add(ex.muscleGroup);
      }
      for (const g of groups) counts[g]++;
    }
    return counts;
  }, [workouts, exerciseById]);

  const activeMuscleGroup = (activeTab !== "weight" && activeTab !== "calories" && activeTab !== "eau") ? activeTab as MuscleGroup : null;
  // Cardio has no load: chart session duration and show duration/distance records instead.
  const isCardio = activeMuscleGroup === "cardio";
  const metricUnit = isCardio ? " min" : " kg";

  // ── Top 3 exercises for the active muscle group, with per-session max weight ──
  // Only the active group is computed (the chart for other groups is never rendered).
  const muscleTopExercises = useMemo(() => {
    if (!activeMuscleGroup) return [];
    const oldestFirst = [...workouts].reverse();
    const scored = exercises
      .filter((e) => e.muscleGroup === activeMuscleGroup)
      .map((ex) => {
        const relevantWorkouts = oldestFirst.filter((w) =>
          w.exercises.some((we) => we.exerciseId === ex.id && we.sets.length > 0)
        );
        const maxWeightData: DataPoint[] = relevantWorkouts.map((w) => {
          const we = w.exercises.find((e) => e.exerciseId === ex.id)!;
          return { label: formatShortDate(new Date(w.date), locale), value: sessionMetric(we.sets, isCardio) };
        });
        const pr = maxWeightData.length > 0 ? Math.max(...maxWeightData.map((d) => d.value)) : 0;
        return { exercise: ex, sessionCount: relevantWorkouts.length, maxWeightData, pr };
      });
    return scored
      .filter((s) => s.sessionCount > 0)
      .sort((a, b) => b.sessionCount - a.sessionCount)
      .slice(0, 3);
  }, [activeMuscleGroup, isCardio, workouts, exercises, locale]);

  const muscleStats = useMemo(() => {
    if (!activeMuscleGroup || muscleTopExercises.length === 0) return null;
    const allPRs = muscleTopExercises.map((e) => e.pr);
    // Per session: total volume (kg) — or, for cardio, total distance (km).
    let peakVolume = 0;
    for (const w of workouts) {
      let vol = 0;
      for (const we of w.exercises) {
        if (exerciseById.get(we.exerciseId)?.muscleGroup !== activeMuscleGroup) continue;
        for (const set of we.sets) vol += isCardio ? set.distance ?? 0 : set.reps * set.weight;
      }
      if (vol > peakVolume) peakVolume = vol;
    }
    return {
      totalSessions: muscleSessionCount[activeMuscleGroup],
      peakWeight: Math.max(...allPRs, 0),
      peakVolume,
    };
  }, [activeMuscleGroup, isCardio, muscleTopExercises, muscleSessionCount, workouts, exerciseById]);

  const tabColor = activeTab === "weight"
    ? colors.info
    : activeTab === "calories"
      ? "#e85a0a"
      : activeTab === "eau"
        ? "#3b82f6"
        : MUSCLE_COLORS[activeTab as MuscleGroup] ?? colors.primary;

  return (
    <>
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ paddingTop: topPad, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
    >
      <Text style={[styles.pageTitle, { color: colors.foreground }]}>{t("progress.title")}</Text>

      {/* Tab selector */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabScroll} contentContainerStyle={styles.tabRow}>
        <TouchableOpacity
          style={[styles.tab, { backgroundColor: activeTab === "weight" ? colors.info + "22" : colors.card, borderColor: activeTab === "weight" ? colors.info : colors.border }]}
          onPress={() => setActiveTab("weight")}
        >
          <Feather name="user" size={14} color={activeTab === "weight" ? colors.info : colors.mutedForeground} />
          <Text style={[styles.tabText, { color: activeTab === "weight" ? colors.info : colors.mutedForeground }]}>
            {t("progress.tab.weight")}
          </Text>
        </TouchableOpacity>

        {/* Calories tab */}
        <TouchableOpacity
          style={[styles.tab, { backgroundColor: activeTab === "calories" ? "#e85a0a22" : colors.card, borderColor: activeTab === "calories" ? "#e85a0a" : colors.border }]}
          onPress={() => setActiveTab("calories")}
        >
          <Text style={{ fontSize: 13, lineHeight: 16 }}>🔥</Text>
          <Text style={[styles.tabText, { color: activeTab === "calories" ? "#e85a0a" : colors.mutedForeground }]}>{t("progress.tab.calories")}</Text>
          {(calStats?.total ?? 0) > 0 && (
            <View style={[styles.tabBadge, { backgroundColor: activeTab === "calories" ? "#e85a0a" : colors.muted }]}>
              <Text style={[styles.tabBadgeText, { color: activeTab === "calories" ? "#fff" : colors.mutedForeground }]}>{calStats!.total}</Text>
            </View>
          )}
        </TouchableOpacity>

        {/* Eau tab */}
        <TouchableOpacity
          style={[styles.tab, { backgroundColor: activeTab === "eau" ? "#3b82f622" : colors.card, borderColor: activeTab === "eau" ? "#3b82f6" : colors.border }]}
          onPress={() => setActiveTab("eau")}
        >
          <Text style={{ fontSize: 13, lineHeight: 16 }}>💧</Text>
          <Text style={[styles.tabText, { color: activeTab === "eau" ? "#3b82f6" : colors.mutedForeground }]}>{t("progress.tab.water")}</Text>
          {(waterStats?.total ?? 0) > 0 && (
            <View style={[styles.tabBadge, { backgroundColor: activeTab === "eau" ? "#3b82f6" : colors.muted }]}>
              <Text style={[styles.tabBadgeText, { color: activeTab === "eau" ? "#fff" : colors.mutedForeground }]}>{waterStats!.total}</Text>
            </View>
          )}
        </TouchableOpacity>

        {MUSCLE_GROUPS.map((g) => {
          const active = activeTab === g;
          const c = MUSCLE_COLORS[g];
          const count = muscleSessionCount[g];
          return (
            <TouchableOpacity
              key={g}
              style={[styles.tab, { backgroundColor: active ? c + "22" : colors.card, borderColor: active ? c : colors.border, opacity: count === 0 ? 0.5 : 1 }]}
              onPress={() => setActiveTab(g)}
            >
              <Text style={[styles.tabText, { color: active ? c : colors.mutedForeground }]}>
                {t(`muscle.${g}` as TranslationKey)}
              </Text>
              {count > 0 && (
                <View style={[styles.tabBadge, { backgroundColor: active ? c : colors.muted }]}>
                  <Text style={[styles.tabBadgeText, { color: active ? "#fff" : colors.mutedForeground }]}>{count}</Text>
                </View>
              )}
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* ── Body weight ── */}
      {activeTab === "weight" && (
        <>
          {bwStats && (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: colors.info }]}>{bwStats.current} kg</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.weight.current")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: bwStats.diff > 0 ? colors.destructive : bwStats.diff < 0 ? colors.success : colors.foreground }]}>
                  {bwStats.diff > 0 ? "+" : ""}{bwStats.diff.toFixed(1)} kg
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.weight.change")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: colors.foreground }]}>{bwStats.min}–{bwStats.max}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.weight.minMax")}</Text>
              </View>
            </View>
          )}

          <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={styles.chartHeader}>
              <Text style={[styles.chartTitle, { color: colors.foreground }]}>{t("progress.weight.chartTitle")}</Text>
              <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>{t("common.daysCount", { count: bodyWeightData.length })}</Text>
            </View>
            <LineChart data={bodyWeightData} color={colors.info} unit=" kg" height={200} formatValue={(v) => `${v}`} />
          </View>

          {/* Body weight log — manual weigh-ins (also fill the chart on rest days) */}
          <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={styles.logHeader}>
              <Text style={[styles.chartTitle, { color: colors.foreground }]}>{t("bodyWeight.logTitle")}</Text>
              <TouchableOpacity
                style={[styles.logAddBtn, { backgroundColor: colors.primary }]}
                onPress={openWeighIn}
                activeOpacity={0.85}
              >
                <Feather name="plus" size={14} color={colors.primaryForeground} />
                <Text style={[styles.logAddText, { color: colors.primaryForeground }]}>{t("bodyWeight.add")}</Text>
              </TouchableOpacity>
            </View>
            {bodyWeights.length === 0 ? (
              <Text style={[styles.logEmpty, { color: colors.mutedForeground }]}>{t("bodyWeight.empty")}</Text>
            ) : (
              bodyWeights.map((e) => (
                <View key={e.date} style={[styles.logRow, { borderTopColor: colors.border }]}>
                  <View style={styles.logRowLeft}>
                    <Feather name="calendar" size={13} color={colors.mutedForeground} />
                    <Text style={[styles.logRowDate, { color: colors.foreground }]}>
                      {new Date(e.date).toLocaleDateString(locale, { day: "numeric", month: "short", year: "numeric" })}
                    </Text>
                  </View>
                  <View style={styles.logRowRight}>
                    <Text style={[styles.logRowWeight, { color: colors.info }]}>{e.weight} kg</Text>
                    <TouchableOpacity onPress={() => removeBodyWeight(e.date)} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
                      <Feather name="trash-2" size={14} color={colors.destructive} />
                    </TouchableOpacity>
                  </View>
                </View>
              ))
            )}
          </View>

          {workouts.length === 0 && (
            <View style={styles.emptyState}>
              <Feather name="activity" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>{t("progress.empty.noSession")}</Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                {t("progress.empty.noSessionHint")}
              </Text>
            </View>
          )}
        </>
      )}

      {/* ── Calories ── */}
      {activeTab === "calories" && (
        <>
          {calStats ? (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: "#e85a0a" }]}>{calStats.today.toLocaleString(locale)}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.stat.today")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: "#e85a0a" }]}>{calStats.avg7.toLocaleString(locale)}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.stat.avg7")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: "#e85a0a" }]}>{calStats.daysHit}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.stat.goalDaysHit")}</Text>
              </View>
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Text style={{ fontSize: 36 }}>🔥</Text>
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>{t("progress.cal.noData")}</Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                {t("progress.cal.noDataHint")}
              </Text>
            </View>
          )}
          {calData.length >= 2 && (
            <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <View style={styles.chartHeader}>
                <Text style={[styles.chartTitle, { color: colors.foreground }]}>{t("progress.cal.chartTitle")}</Text>
                <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>
                  {t("progress.cal.chartSub", { days: calData.length, goal: tracker.calorieGoal.toLocaleString(locale) })}
                </Text>
              </View>
              <LineChart
                data={calData}
                color="#e85a0a"
                unit=" kcal"
                height={200}
                goalLine={tracker.calorieGoal}
                formatValue={(v) => v >= 1000 ? `${Math.round(v / 100) / 10}k` : `${Math.round(v)}`}
              />
            </View>
          )}
          {calStats && (
            <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={[styles.chartTitle, { color: colors.foreground, marginBottom: 14 }]}>{t("progress.recap.title")}</Text>
              <View style={styles.recapRow}>
                <Text style={[styles.recapLabel, { color: colors.mutedForeground }]}>{t("progress.recap.dailyGoal")}</Text>
                <Text style={[styles.recapVal, { color: colors.foreground }]}>{tracker.calorieGoal.toLocaleString(locale)} kcal</Text>
              </View>
              <View style={[styles.recapRow, { borderTopWidth: 1, borderTopColor: colors.border }]}>
                <Text style={[styles.recapLabel, { color: colors.mutedForeground }]}>{t("progress.recap.bestDay")}</Text>
                <Text style={[styles.recapVal, { color: "#e85a0a" }]}>{calStats.best.toLocaleString(locale)} kcal</Text>
              </View>
              <View style={[styles.recapRow, { borderTopWidth: 1, borderTopColor: colors.border }]}>
                <Text style={[styles.recapLabel, { color: colors.mutedForeground }]}>{t("progress.recap.daysTracked")}</Text>
                <Text style={[styles.recapVal, { color: colors.foreground }]}>{calStats.total}</Text>
              </View>
            </View>
          )}
        </>
      )}

      {/* ── Eau ── */}
      {activeTab === "eau" && (
        <>
          {waterStats ? (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: "#3b82f6" }]}>{(waterStats.today / 1000).toFixed(1)}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.stat.todayLiters")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: "#3b82f6" }]}>{(waterStats.avg7 / 1000).toFixed(1)}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.stat.avg7")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: "#3b82f6" }]}>{waterStats.daysHit}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.stat.goalDaysHit")}</Text>
              </View>
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Text style={{ fontSize: 36 }}>💧</Text>
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>{t("progress.cal.noData")}</Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                {t("progress.water.noDataHint")}
              </Text>
            </View>
          )}
          {waterData.length >= 2 && (
            <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <View style={styles.chartHeader}>
                <Text style={[styles.chartTitle, { color: colors.foreground }]}>{t("progress.water.chartTitle")}</Text>
                <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>
                  {t("progress.water.chartSub", { days: waterData.length, goal: (tracker.waterGoal / 1000).toFixed(1) })}
                </Text>
              </View>
              <LineChart
                data={waterData.map((d) => ({ ...d, value: Math.round(d.value / 100) / 10 }))}
                color="#3b82f6"
                unit=" L"
                height={200}
                goalLine={tracker.waterGoal / 1000}
                formatValue={(v) => `${v}`}
              />
            </View>
          )}
          {waterStats && (
            <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={[styles.chartTitle, { color: colors.foreground, marginBottom: 14 }]}>{t("progress.recap.title")}</Text>
              <View style={styles.recapRow}>
                <Text style={[styles.recapLabel, { color: colors.mutedForeground }]}>{t("progress.recap.dailyGoal")}</Text>
                <Text style={[styles.recapVal, { color: colors.foreground }]}>{(tracker.waterGoal / 1000).toFixed(1)} L</Text>
              </View>
              <View style={[styles.recapRow, { borderTopWidth: 1, borderTopColor: colors.border }]}>
                <Text style={[styles.recapLabel, { color: colors.mutedForeground }]}>{t("progress.recap.bestDay")}</Text>
                <Text style={[styles.recapVal, { color: "#3b82f6" }]}>{(waterStats.best / 1000).toFixed(1)} L</Text>
              </View>
              <View style={[styles.recapRow, { borderTopWidth: 1, borderTopColor: colors.border }]}>
                <Text style={[styles.recapLabel, { color: colors.mutedForeground }]}>{t("progress.recap.daysTracked")}</Text>
                <Text style={[styles.recapVal, { color: colors.foreground }]}>{waterStats.total}</Text>
              </View>
            </View>
          )}
        </>
      )}

      {/* ── Muscle group ── */}
      {activeTab !== "weight" && activeTab !== "calories" && activeTab !== "eau" && activeMuscleGroup && (
        <>
          {/* Summary stats */}
          {muscleStats ? (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>{muscleStats.totalSessions}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("progress.muscle.sessions")}</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>{muscleStats.peakWeight}{metricUnit}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
                  {t(isCardio ? "progress.muscle.maxDuration" : "progress.muscle.maxLoad")}
                </Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>
                  {isCardio
                    ? `${Math.round(muscleStats.peakVolume * 10) / 10} km`
                    : `${Math.round(muscleStats.peakVolume / 100) / 10}t`}
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
                  {t(isCardio ? "progress.muscle.maxDistance" : "progress.muscle.maxVolume")}
                </Text>
              </View>
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Feather name="bar-chart-2" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
                {t("progress.muscle.empty", { group: t(`muscle.${activeMuscleGroup}` as TranslationKey).toLowerCase() })}
              </Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                {t("progress.muscle.emptyHint", { group: t(`muscle.${activeMuscleGroup}` as TranslationKey).toLowerCase() })}
              </Text>
            </View>
          )}

          {/* Per-exercise charts — top 3 most used */}
          {muscleTopExercises.length > 0 && (
            <View style={[styles.sectionCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <View style={styles.sectionHeader}>
                <Text style={[styles.sectionTitle, { color: colors.foreground }]}>
                  {t("progress.muscle.topExercises")}
                </Text>
                <Text style={[styles.sectionSub, { color: colors.mutedForeground }]}>
                  {t(isCardio ? "progress.muscle.topExercisesSubCardio" : "progress.muscle.topExercisesSub")}
                </Text>
              </View>

              {muscleTopExercises.map((item, i) => {
                const chartColor = CHART_PALETTE[i % CHART_PALETTE.length];
                const trend = item.maxWeightData.length >= 2
                  ? item.maxWeightData[item.maxWeightData.length - 1].value - item.maxWeightData[0].value
                  : null;

                return (
                  <View key={item.exercise.id} style={[styles.exSection, i < muscleTopExercises.length - 1 && { borderBottomWidth: 1, borderBottomColor: colors.border }]}>
                    {/* Exercise header */}
                    <TouchableOpacity
                      style={styles.exHeader}
                      onPress={() => router.push({ pathname: "/exercise-history", params: { exerciseId: item.exercise.id } })}
                      activeOpacity={0.7}
                    >
                      <View style={[styles.rankBadge, { backgroundColor: chartColor + "22" }]}>
                        <Text style={[styles.rankText, { color: chartColor }]}>#{i + 1}</Text>
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.exName, { color: colors.foreground }]}>{exerciseName(item.exercise)}</Text>
                        <Text style={[styles.exMeta, { color: colors.mutedForeground }]}>
                          {t("common.sessionsCount", { count: item.sessionCount })}
                        </Text>
                      </View>
                      <View style={styles.exStats}>
                        {item.pr > 0 && (
                          <View style={[styles.prBadge, { backgroundColor: chartColor + "20" }]}>
                            <Feather name="award" size={11} color={chartColor} />
                            <Text style={[styles.prText, { color: chartColor }]}>{item.pr}{metricUnit}</Text>
                          </View>
                        )}
                        {trend !== null && trend !== 0 && (
                          <View style={styles.trendRow}>
                            <Feather
                              name={trend > 0 ? "trending-up" : "trending-down"}
                              size={12}
                              color={trend > 0 ? colors.success : colors.destructive}
                            />
                            <Text style={[styles.trendText, { color: trend > 0 ? colors.success : colors.destructive }]}>
                              {trend > 0 ? "+" : ""}{trend.toFixed(1)}{metricUnit}
                            </Text>
                          </View>
                        )}
                        <Feather name="chevron-right" size={14} color={colors.mutedForeground} />
                      </View>
                    </TouchableOpacity>

                    {/* Chart */}
                    {item.maxWeightData.length >= 2 ? (
                      <View style={styles.miniChart}>
                        <LineChart
                          data={item.maxWeightData}
                          color={chartColor}
                          unit={metricUnit}
                          height={130}
                          formatValue={(v) => `${v}`}
                        />
                      </View>
                    ) : (
                      <Text style={[styles.notEnoughData, { color: colors.mutedForeground }]}>
                        {t("progress.muscle.needTwo")}
                      </Text>
                    )}
                  </View>
                );
              })}
            </View>
          )}

          {/* All exercises list for this group */}
          {muscleTopExercises.length > 0 && (
            <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={[styles.chartTitle, { color: colors.foreground, marginBottom: 14 }]}>
                {t("progress.muscle.allExercises")}
              </Text>
              {exercises
                .filter((e) => e.muscleGroup === activeMuscleGroup)
                .map((ex) => {
                  const sessionCount = workouts.filter((w) =>
                    w.exercises.some((we) => we.exerciseId === ex.id && we.sets.length > 0)
                  ).length;
                  if (sessionCount === 0) return null;
                  const pb = workouts.reduce((max, w) => {
                    const we = w.exercises.find((e) => e.exerciseId === ex.id);
                    return we ? Math.max(max, sessionMetric(we.sets, isCardio)) : max;
                  }, 0);
                  return (
                    <TouchableOpacity
                      key={ex.id}
                      style={[styles.exerciseRow, { borderBottomColor: colors.border }]}
                      onPress={() => router.push({ pathname: "/exercise-history", params: { exerciseId: ex.id } })}
                    >
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.exerciseName, { color: colors.foreground }]}>{exerciseName(ex)}</Text>
                        <Text style={[styles.exerciseMeta, { color: colors.mutedForeground }]}>
                          {t("common.sessionsCount", { count: sessionCount })}
                        </Text>
                      </View>
                      <View style={styles.exerciseRight}>
                        {pb > 0 && (
                          <View style={styles.pbRow}>
                            <Feather name="award" size={12} color={tabColor} />
                            <Text style={[styles.pbText, { color: tabColor }]}>{pb}{metricUnit}</Text>
                          </View>
                        )}
                        <Feather name="chevron-right" size={14} color={colors.mutedForeground} />
                      </View>
                    </TouchableOpacity>
                  );
                })}
            </View>
          )}
        </>
      )}
    </ScrollView>

    <Modal visible={showWeighIn} transparent animationType="fade" onRequestClose={() => setShowWeighIn(false)}>
      <View style={styles.modalOverlay}>
        <TouchableOpacity style={StyleSheet.absoluteFill} activeOpacity={1} onPress={() => setShowWeighIn(false)} />
        <View style={[styles.modalBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Text style={[styles.modalTitle, { color: colors.foreground }]}>{t("bodyWeight.newEntry")}</Text>
          <Text style={[styles.modalLabel, { color: colors.mutedForeground }]}>{t("bodyWeight.weightLabel")}</Text>
          <TextInput
            style={[styles.modalInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]}
            value={weighInWeight}
            onChangeText={setWeighInWeight}
            keyboardType="decimal-pad"
            placeholder={t("bodyWeight.weightPlaceholder")}
            placeholderTextColor={colors.mutedForeground}
            autoFocus
          />
          <Text style={[styles.modalLabel, { color: colors.mutedForeground }]}>{t("bodyWeight.dateLabel")}</Text>
          <View style={styles.dateRow}>
            <TextInput style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]} value={weighInDay} onChangeText={setWeighInDay} keyboardType="number-pad" maxLength={2} placeholder={t("date.day")} placeholderTextColor={colors.mutedForeground} selectTextOnFocus />
            <Text style={[styles.dateSep, { color: colors.mutedForeground }]}>/</Text>
            <TextInput style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]} value={weighInMonth} onChangeText={setWeighInMonth} keyboardType="number-pad" maxLength={2} placeholder={t("date.month")} placeholderTextColor={colors.mutedForeground} selectTextOnFocus />
            <Text style={[styles.dateSep, { color: colors.mutedForeground }]}>/</Text>
            <TextInput style={[styles.dateInput, { flex: 2, color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]} value={weighInYear} onChangeText={setWeighInYear} keyboardType="number-pad" maxLength={4} placeholder={t("date.year")} placeholderTextColor={colors.mutedForeground} selectTextOnFocus />
          </View>
          {weighInError && (
            <Text style={[styles.modalError, { color: colors.destructive }]}>{t("bodyWeight.invalidEntry")}</Text>
          )}
          <View style={styles.modalBtns}>
            <TouchableOpacity style={[styles.modalCancel, { borderColor: colors.border }]} onPress={() => setShowWeighIn(false)}>
              <Text style={[styles.modalCancelText, { color: colors.foreground }]}>{t("common.cancel")}</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.modalSave, { backgroundColor: colors.primary }]} onPress={saveWeighIn}>
              <Feather name="check" size={16} color={colors.primaryForeground} />
              <Text style={[styles.modalSaveText, { color: colors.primaryForeground }]}>{t("common.save")}</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  pageTitle: { fontSize: 32, fontFamily: "Inter_700Bold", letterSpacing: -0.5, paddingHorizontal: 20, marginBottom: 16 },
  tabScroll: { marginBottom: 20 },
  tabRow: { paddingHorizontal: 20, gap: 8, flexDirection: "row", alignItems: "center" },
  tab: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, paddingVertical: 8, borderRadius: 20, borderWidth: 1.5 },
  tabText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  tabBadge: { minWidth: 18, height: 18, borderRadius: 9, alignItems: "center", justifyContent: "center", paddingHorizontal: 4 },
  tabBadgeText: { fontSize: 10, fontFamily: "Inter_700Bold" },
  statsRow: { flexDirection: "row", paddingHorizontal: 20, gap: 10, marginBottom: 16 },
  statCard: { flex: 1, borderRadius: 12, borderWidth: 1, padding: 12, alignItems: "center", gap: 4 },
  statValue: { fontSize: 16, fontFamily: "Inter_700Bold" },
  statLabel: { fontSize: 10, fontFamily: "Inter_400Regular", textAlign: "center" },
  chartCard: { marginHorizontal: 20, borderRadius: 14, borderWidth: 1, padding: 16, marginBottom: 16, overflow: "hidden" },
  chartHeader: { marginBottom: 12 },
  chartTitle: { fontSize: 14, fontFamily: "Inter_700Bold" },
  chartSub: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 2 },
  // Per-exercise section card
  sectionCard: { marginHorizontal: 20, borderRadius: 14, borderWidth: 1, marginBottom: 16, overflow: "hidden" },
  sectionHeader: { padding: 16, paddingBottom: 12 },
  sectionTitle: { fontSize: 14, fontFamily: "Inter_700Bold" },
  sectionSub: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 2 },
  exSection: { paddingBottom: 4 },
  exHeader: { flexDirection: "row", alignItems: "center", paddingHorizontal: 16, paddingVertical: 12, gap: 10 },
  rankBadge: { width: 28, height: 28, borderRadius: 8, alignItems: "center", justifyContent: "center" },
  rankText: { fontSize: 11, fontFamily: "Inter_700Bold" },
  exName: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  exMeta: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 1 },
  exStats: { flexDirection: "row", alignItems: "center", gap: 6 },
  prBadge: { flexDirection: "row", alignItems: "center", gap: 3, paddingHorizontal: 7, paddingVertical: 3, borderRadius: 8 },
  prText: { fontSize: 12, fontFamily: "Inter_700Bold" },
  trendRow: { flexDirection: "row", alignItems: "center", gap: 2 },
  trendText: { fontSize: 11, fontFamily: "Inter_600SemiBold" },
  miniChart: { paddingHorizontal: 16, paddingBottom: 12 },
  notEnoughData: { fontSize: 12, fontFamily: "Inter_400Regular", textAlign: "center", paddingVertical: 16, paddingHorizontal: 20 },
  // Exercises list
  exerciseRow: { flexDirection: "row", alignItems: "center", paddingVertical: 12, borderBottomWidth: 1 },
  exerciseName: { fontSize: 14, fontFamily: "Inter_600SemiBold", marginBottom: 2 },
  exerciseMeta: { fontSize: 11, fontFamily: "Inter_400Regular" },
  exerciseRight: { flexDirection: "row", alignItems: "center", gap: 8 },
  pbRow: { flexDirection: "row", alignItems: "center", gap: 3 },
  pbText: { fontSize: 13, fontFamily: "Inter_700Bold" },
  emptyState: { alignItems: "center", paddingHorizontal: 40, paddingVertical: 40, gap: 10 },
  emptyTitle: { fontSize: 18, fontFamily: "Inter_700Bold", marginTop: 4 },
  emptyText: { fontSize: 13, fontFamily: "Inter_400Regular", textAlign: "center", lineHeight: 20 },
  recapRow: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", paddingVertical: 11 },
  recapLabel: { fontSize: 13, fontFamily: "Inter_400Regular" },
  recapVal: { fontSize: 14, fontFamily: "Inter_700Bold" },
  // Body weight log
  logHeader: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", marginBottom: 12 },
  logAddBtn: { flexDirection: "row", alignItems: "center", gap: 5, paddingHorizontal: 12, paddingVertical: 7, borderRadius: 18 },
  logAddText: { fontSize: 12, fontFamily: "Inter_600SemiBold" },
  logEmpty: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  logRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: 11, borderTopWidth: 1 },
  logRowLeft: { flexDirection: "row", alignItems: "center", gap: 8 },
  logRowDate: { fontSize: 13, fontFamily: "Inter_500Medium" },
  logRowRight: { flexDirection: "row", alignItems: "center", gap: 14 },
  logRowWeight: { fontSize: 14, fontFamily: "Inter_700Bold" },
  // Weigh-in modal
  modalOverlay: { flex: 1, backgroundColor: "rgba(0,0,0,0.6)", alignItems: "center", justifyContent: "center", padding: 24 },
  modalBox: { width: "100%", maxWidth: 360, borderRadius: 20, borderWidth: 1, padding: 24, gap: 12 },
  modalTitle: { fontSize: 18, fontFamily: "Inter_700Bold", marginBottom: 4 },
  modalLabel: { fontSize: 12, fontFamily: "Inter_500Medium" },
  modalInput: { borderWidth: 1, borderRadius: 10, paddingHorizontal: 12, paddingVertical: 11, fontSize: 16, fontFamily: "Inter_600SemiBold" },
  dateRow: { flexDirection: "row", alignItems: "center", gap: 6 },
  dateInput: { flex: 1, borderWidth: 1, borderRadius: 10, paddingHorizontal: 10, paddingVertical: 10, fontSize: 15, fontFamily: "Inter_600SemiBold", textAlign: "center" },
  dateSep: { fontSize: 18, fontFamily: "Inter_600SemiBold" },
  modalError: { fontSize: 13, fontFamily: "Inter_500Medium" },
  modalBtns: { flexDirection: "row", gap: 10, marginTop: 4 },
  modalCancel: { flex: 1, paddingVertical: 13, borderRadius: 12, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  modalCancelText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  modalSave: { flex: 1, paddingVertical: 13, borderRadius: 12, flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6 },
  modalSaveText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
});
