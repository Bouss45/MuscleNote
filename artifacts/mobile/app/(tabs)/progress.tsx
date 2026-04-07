import { Feather } from "@expo/vector-icons";
import { router } from "expo-router";
import React, { useMemo, useState } from "react";
import {
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { MuscleGroup, useWorkout } from "@/context/WorkoutContext";
import { MUSCLE_GROUP_LABELS } from "@/components/MuscleGroupBadge";
import LineChart, { DataPoint } from "@/components/LineChart";

type Tab = "weight" | MuscleGroup;

const MUSCLE_GROUPS: MuscleGroup[] = [
  "dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio",
];

const MUSCLE_COLORS: Record<MuscleGroup, string> = {
  dos: "#3b82f6",
  pecs: "#e85a0a",
  biceps: "#a855f7",
  triceps: "#8b5cf6",
  jambes: "#22c55e",
  épaules: "#f59e0b",
  abdos: "#ef4444",
  cardio: "#ec4899",
};

// Slightly different shades for multiple exercise charts
const CHART_PALETTE = ["#e85a0a", "#3b82f6", "#22c55e", "#a855f7", "#f59e0b"];

function formatShortDate(iso: string) {
  const d = new Date(iso);
  return d.toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit" });
}

export default function ProgressScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { workouts, exercises } = useWorkout();
  const [activeTab, setActiveTab] = useState<Tab>("weight");

  const topPad = Platform.OS === "web" ? 67 : insets.top + 10;
  const bottomPad = Platform.OS === "web" ? 34 + 84 : insets.bottom + 100;

  // ── Body weight chart ────────────────────────────────────────────────────
  const bodyWeightData: DataPoint[] = useMemo(() => {
    return [...workouts].reverse().slice(-20).map((w) => ({
      label: formatShortDate(w.date),
      value: w.bodyWeight,
    }));
  }, [workouts]);

  const bwStats = useMemo(() => {
    if (bodyWeightData.length === 0) return null;
    const vals = bodyWeightData.map((d) => d.value);
    const first = vals[0], last = vals[vals.length - 1];
    return { current: last, diff: last - first, min: Math.min(...vals), max: Math.max(...vals) };
  }, [bodyWeightData]);

  // ── Sessions count per muscle group (for tab badges) ─────────────────────
  const muscleSessionCount = useMemo(() => {
    const counts: Record<MuscleGroup, number> = {} as Record<MuscleGroup, number>;
    for (const g of MUSCLE_GROUPS) {
      counts[g] = workouts.filter((w) =>
        w.exercises.some((we) => {
          const ex = exercises.find((e) => e.id === we.exerciseId);
          return ex?.muscleGroup === g && we.sets.length > 0;
        })
      ).length;
    }
    return counts;
  }, [workouts, exercises]);

  // ── Top exercises per muscle group with per-session max weight ────────────
  const topExercisesByGroup = useMemo(() => {
    const result: Record<MuscleGroup, {
      exercise: typeof exercises[0];
      sessionCount: number;
      maxWeightData: DataPoint[];
      volumeData: DataPoint[];
      pr: number;
    }[]> = {} as any;

    for (const group of MUSCLE_GROUPS) {
      const groupExercises = exercises.filter((e) => e.muscleGroup === group);

      // Score each exercise by how many sessions it appears in
      const scored = groupExercises.map((ex) => {
        const relevantWorkouts = [...workouts]
          .reverse()
          .filter((w) => w.exercises.some((we) => we.exerciseId === ex.id && we.sets.length > 0));

        const maxWeightData: DataPoint[] = relevantWorkouts.slice(-15).map((w) => {
          const we = w.exercises.find((e) => e.exerciseId === ex.id)!;
          const maxW = Math.max(...we.sets.map((s) => s.weight));
          return { label: formatShortDate(w.date), value: maxW };
        });

        const volumeData: DataPoint[] = relevantWorkouts.slice(-15).map((w) => {
          const we = w.exercises.find((e) => e.exerciseId === ex.id)!;
          const vol = we.sets.reduce((s, set) => s + set.reps * set.weight, 0);
          return { label: formatShortDate(w.date), value: vol };
        });

        const pr = maxWeightData.length > 0 ? Math.max(...maxWeightData.map((d) => d.value)) : 0;

        return { exercise: ex, sessionCount: relevantWorkouts.length, maxWeightData, volumeData, pr };
      });

      result[group] = scored
        .filter((s) => s.sessionCount > 0)
        .sort((a, b) => b.sessionCount - a.sessionCount)
        .slice(0, 3);
    }

    return result;
  }, [workouts, exercises]);

  // Summary stats for active muscle group
  const activeMuscleGroup = activeTab !== "weight" ? activeTab as MuscleGroup : null;
  const muscleTopExercises = activeMuscleGroup ? topExercisesByGroup[activeMuscleGroup] : [];

  const muscleStats = useMemo(() => {
    if (!activeMuscleGroup || muscleTopExercises.length === 0) return null;
    const allPRs = muscleTopExercises.map((e) => e.pr);
    const allSessions = muscleSessionCount[activeMuscleGroup];
    const allVols = workouts
      .filter((w) => w.exercises.some((we) => {
        const ex = exercises.find((e) => e.id === we.exerciseId);
        return ex?.muscleGroup === activeMuscleGroup;
      }))
      .map((w) => w.exercises
        .filter((we) => exercises.find((e) => e.id === we.exerciseId)?.muscleGroup === activeMuscleGroup)
        .reduce((sum, we) => sum + we.sets.reduce((s, set) => s + set.reps * set.weight, 0), 0)
      );
    return {
      totalSessions: allSessions,
      peakWeight: Math.max(...allPRs, 0),
      peakVolume: allVols.length > 0 ? Math.max(...allVols) : 0,
    };
  }, [activeMuscleGroup, muscleTopExercises, muscleSessionCount, workouts, exercises]);

  const tabColor = activeTab === "weight"
    ? colors.info
    : MUSCLE_COLORS[activeTab as MuscleGroup] ?? colors.primary;

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ paddingTop: topPad, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
    >
      <Text style={[styles.pageTitle, { color: colors.foreground }]}>Progression</Text>

      {/* Tab selector */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.tabScroll} contentContainerStyle={styles.tabRow}>
        <TouchableOpacity
          style={[styles.tab, { backgroundColor: activeTab === "weight" ? colors.info + "22" : colors.card, borderColor: activeTab === "weight" ? colors.info : colors.border }]}
          onPress={() => setActiveTab("weight")}
        >
          <Feather name="user" size={14} color={activeTab === "weight" ? colors.info : colors.mutedForeground} />
          <Text style={[styles.tabText, { color: activeTab === "weight" ? colors.info : colors.mutedForeground }]}>
            Poids corps
          </Text>
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
                {MUSCLE_GROUP_LABELS[g]}
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
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Actuel</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: bwStats.diff > 0 ? colors.destructive : bwStats.diff < 0 ? colors.success : colors.foreground }]}>
                  {bwStats.diff > 0 ? "+" : ""}{bwStats.diff.toFixed(1)} kg
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Évolution</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: colors.foreground }]}>{bwStats.min}–{bwStats.max}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Min–Max</Text>
              </View>
            </View>
          )}

          <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={styles.chartHeader}>
              <Text style={[styles.chartTitle, { color: colors.foreground }]}>Poids de corps (kg)</Text>
              <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>20 dernières séances</Text>
            </View>
            <LineChart data={bodyWeightData} color={colors.info} unit=" kg" height={200} formatValue={(v) => `${v}`} />
          </View>

          {workouts.length === 0 && (
            <View style={styles.emptyState}>
              <Feather name="activity" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>Aucune séance encore</Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                Lance ta première séance pour voir tes progrès ici
              </Text>
            </View>
          )}
        </>
      )}

      {/* ── Muscle group ── */}
      {activeTab !== "weight" && activeMuscleGroup && (
        <>
          {/* Summary stats */}
          {muscleStats ? (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>{muscleStats.totalSessions}</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Séances</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>{muscleStats.peakWeight} kg</Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Charge max</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>
                  {Math.round(muscleStats.peakVolume / 100) / 10}t
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Vol. max</Text>
              </View>
            </View>
          ) : (
            <View style={styles.emptyState}>
              <Feather name="bar-chart-2" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
                Aucune séance {MUSCLE_GROUP_LABELS[activeMuscleGroup].toLowerCase()}
              </Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                Fais des exercices de {MUSCLE_GROUP_LABELS[activeMuscleGroup].toLowerCase()} pour voir ta progression
              </Text>
            </View>
          )}

          {/* Per-exercise charts — top 3 most used */}
          {muscleTopExercises.length > 0 && (
            <View style={[styles.sectionCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <View style={styles.sectionHeader}>
                <Text style={[styles.sectionTitle, { color: colors.foreground }]}>
                  Exercices les plus utilisés
                </Text>
                <Text style={[styles.sectionSub, { color: colors.mutedForeground }]}>
                  Charge maximale par séance
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
                        <Text style={[styles.exName, { color: colors.foreground }]}>{item.exercise.name}</Text>
                        <Text style={[styles.exMeta, { color: colors.mutedForeground }]}>
                          {item.sessionCount} séance{item.sessionCount > 1 ? "s" : ""}
                        </Text>
                      </View>
                      <View style={styles.exStats}>
                        {item.pr > 0 && (
                          <View style={[styles.prBadge, { backgroundColor: chartColor + "20" }]}>
                            <Feather name="award" size={11} color={chartColor} />
                            <Text style={[styles.prText, { color: chartColor }]}>{item.pr} kg</Text>
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
                              {trend > 0 ? "+" : ""}{trend.toFixed(1)} kg
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
                          unit=" kg"
                          height={130}
                          formatValue={(v) => `${v}`}
                        />
                      </View>
                    ) : (
                      <Text style={[styles.notEnoughData, { color: colors.mutedForeground }]}>
                        Fais au moins 2 séances pour voir le graphique
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
                Tous les exercices
              </Text>
              {exercises
                .filter((e) => e.muscleGroup === activeMuscleGroup)
                .map((ex) => {
                  const sessionCount = workouts.filter((w) =>
                    w.exercises.some((we) => we.exerciseId === ex.id && we.sets.length > 0)
                  ).length;
                  if (sessionCount === 0) return null;
                  const pb = workouts
                    .flatMap((w) => w.exercises.find((we) => we.exerciseId === ex.id)?.sets ?? [])
                    .reduce((max, s) => Math.max(max, s.weight), 0);
                  return (
                    <TouchableOpacity
                      key={ex.id}
                      style={[styles.exerciseRow, { borderBottomColor: colors.border }]}
                      onPress={() => router.push({ pathname: "/exercise-history", params: { exerciseId: ex.id } })}
                    >
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.exerciseName, { color: colors.foreground }]}>{ex.name}</Text>
                        <Text style={[styles.exerciseMeta, { color: colors.mutedForeground }]}>
                          {sessionCount} séance{sessionCount > 1 ? "s" : ""}
                        </Text>
                      </View>
                      <View style={styles.exerciseRight}>
                        {pb > 0 && (
                          <View style={styles.pbRow}>
                            <Feather name="award" size={12} color={tabColor} />
                            <Text style={[styles.pbText, { color: tabColor }]}>{pb} kg</Text>
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
});
