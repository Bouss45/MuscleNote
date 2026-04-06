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

  // ── Body weight chart data ──────────────────────────────────────────────────
  const bodyWeightData: DataPoint[] = useMemo(() => {
    return [...workouts]
      .reverse()
      .slice(-20)
      .map((w) => ({
        label: formatShortDate(w.date),
        value: w.bodyWeight,
      }));
  }, [workouts]);

  // Body weight stats
  const bwStats = useMemo(() => {
    if (bodyWeightData.length === 0) return null;
    const vals = bodyWeightData.map((d) => d.value);
    const first = vals[0];
    const last = vals[vals.length - 1];
    const diff = last - first;
    return { current: last, diff, min: Math.min(...vals), max: Math.max(...vals) };
  }, [bodyWeightData]);

  // ── Per-muscle-group volume chart data ────────────────────────────────────
  const muscleVolumeData = useMemo(() => {
    const result: Record<MuscleGroup, DataPoint[]> = {} as Record<MuscleGroup, DataPoint[]>;

    for (const group of MUSCLE_GROUPS) {
      const sessionPoints: DataPoint[] = [];

      const sorted = [...workouts].reverse();
      for (const workout of sorted) {
        let groupVolume = 0;
        let groupMaxWeight = 0;

        for (const we of workout.exercises) {
          const ex = exercises.find((e) => e.id === we.exerciseId);
          if (!ex || ex.muscleGroup !== group) continue;
          for (const s of we.sets) {
            groupVolume += s.reps * s.weight;
            if (s.weight > groupMaxWeight) groupMaxWeight = s.weight;
          }
        }

        if (groupVolume > 0) {
          sessionPoints.push({
            label: formatShortDate(workout.date),
            value: groupVolume,
          });
        }
      }

      result[group] = sessionPoints.slice(-15);
    }

    return result;
  }, [workouts, exercises]);

  // Per-muscle max weight chart data
  const muscleMaxData = useMemo(() => {
    const result: Record<MuscleGroup, DataPoint[]> = {} as Record<MuscleGroup, DataPoint[]>;

    for (const group of MUSCLE_GROUPS) {
      const sessionPoints: DataPoint[] = [];

      const sorted = [...workouts].reverse();
      for (const workout of sorted) {
        let groupMaxWeight = 0;

        for (const we of workout.exercises) {
          const ex = exercises.find((e) => e.id === we.exerciseId);
          if (!ex || ex.muscleGroup !== group) continue;
          for (const s of we.sets) {
            if (s.weight > groupMaxWeight) groupMaxWeight = s.weight;
          }
        }

        if (groupMaxWeight > 0) {
          sessionPoints.push({
            label: formatShortDate(workout.date),
            value: groupMaxWeight,
          });
        }
      }

      result[group] = sessionPoints.slice(-15);
    }

    return result;
  }, [workouts, exercises]);

  // Sessions count per muscle group
  const muscleSessionCount = useMemo(() => {
    const counts: Record<MuscleGroup, number> = {} as Record<MuscleGroup, number>;
    for (const g of MUSCLE_GROUPS) counts[g] = muscleVolumeData[g].length;
    return counts;
  }, [muscleVolumeData]);

  const activeMuscleGroup = activeTab !== "weight" ? activeTab as MuscleGroup : null;
  const volumeData = activeMuscleGroup ? muscleVolumeData[activeMuscleGroup] : [];
  const maxData = activeMuscleGroup ? muscleMaxData[activeMuscleGroup] : [];

  // Summary stats for active muscle group
  const muscleStats = useMemo(() => {
    if (!activeMuscleGroup || volumeData.length === 0) return null;
    const vols = volumeData.map((d) => d.value);
    const maxWeights = maxData.map((d) => d.value);
    return {
      totalSessions: vols.length,
      peakVolume: Math.max(...vols),
      peakWeight: maxWeights.length > 0 ? Math.max(...maxWeights) : 0,
      latestVolume: vols[vols.length - 1],
    };
  }, [activeMuscleGroup, volumeData, maxData]);

  const tabColor = activeTab === "weight" ? colors.info : MUSCLE_COLORS[activeTab as MuscleGroup] ?? colors.primary;

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ paddingTop: topPad, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
    >
      <Text style={[styles.pageTitle, { color: colors.foreground }]}>
        Progression
      </Text>

      {/* ── Tab selector ──────────────────────────────────────────────────── */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.tabScroll}
        contentContainerStyle={styles.tabRow}
      >
        {/* Body weight tab */}
        <TouchableOpacity
          style={[
            styles.tab,
            {
              backgroundColor: activeTab === "weight" ? colors.info + "22" : colors.card,
              borderColor: activeTab === "weight" ? colors.info : colors.border,
            },
          ]}
          onPress={() => setActiveTab("weight")}
        >
          <Feather
            name="user"
            size={14}
            color={activeTab === "weight" ? colors.info : colors.mutedForeground}
          />
          <Text
            style={[
              styles.tabText,
              { color: activeTab === "weight" ? colors.info : colors.mutedForeground },
            ]}
          >
            Poids corps
          </Text>
        </TouchableOpacity>

        {/* Muscle group tabs */}
        {MUSCLE_GROUPS.map((g) => {
          const active = activeTab === g;
          const c = MUSCLE_COLORS[g];
          const count = muscleSessionCount[g];
          return (
            <TouchableOpacity
              key={g}
              style={[
                styles.tab,
                {
                  backgroundColor: active ? c + "22" : colors.card,
                  borderColor: active ? c : colors.border,
                  opacity: count === 0 ? 0.5 : 1,
                },
              ]}
              onPress={() => setActiveTab(g)}
            >
              <Text
                style={[
                  styles.tabText,
                  { color: active ? c : colors.mutedForeground },
                ]}
              >
                {MUSCLE_GROUP_LABELS[g]}
              </Text>
              {count > 0 && (
                <View style={[styles.tabBadge, { backgroundColor: active ? c : colors.muted }]}>
                  <Text style={[styles.tabBadgeText, { color: active ? "#fff" : colors.mutedForeground }]}>
                    {count}
                  </Text>
                </View>
              )}
            </TouchableOpacity>
          );
        })}
      </ScrollView>

      {/* ── Body weight section ───────────────────────────────────────────── */}
      {activeTab === "weight" && (
        <>
          {bwStats && (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: colors.info }]}>
                  {bwStats.current} kg
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Actuel</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[
                  styles.statValue,
                  { color: bwStats.diff > 0 ? colors.destructive : bwStats.diff < 0 ? colors.success : colors.foreground },
                ]}>
                  {bwStats.diff > 0 ? "+" : ""}{bwStats.diff.toFixed(1)} kg
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Évolution</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: colors.foreground }]}>
                  {bwStats.min}–{bwStats.max}
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Min–Max</Text>
              </View>
            </View>
          )}

          <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={styles.chartHeader}>
              <Text style={[styles.chartTitle, { color: colors.foreground }]}>
                Poids de corps (kg)
              </Text>
              <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>
                20 dernières séances
              </Text>
            </View>
            <LineChart
              data={bodyWeightData}
              color={colors.info}
              unit=" kg"
              height={200}
              formatValue={(v) => `${v}`}
            />
          </View>

          {workouts.length === 0 && (
            <View style={styles.emptyState}>
              <Feather name="activity" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
                Aucune séance encore
              </Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                Lance ta première séance pour voir tes progrès ici
              </Text>
            </View>
          )}
        </>
      )}

      {/* ── Muscle group section ──────────────────────────────────────────── */}
      {activeTab !== "weight" && activeMuscleGroup && (
        <>
          {muscleStats ? (
            <View style={styles.statsRow}>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>
                  {muscleStats.totalSessions}
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Séances</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>
                  {muscleStats.peakWeight} kg
                </Text>
                <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>Charge max</Text>
              </View>
              <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <Text style={[styles.statValue, { color: tabColor }]}>
                  {Math.round(muscleStats.peakVolume / 1000 * 10) / 10}t
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
                Fais des exercices de {MUSCLE_GROUP_LABELS[activeMuscleGroup].toLowerCase()} pour voir tes progrès
              </Text>
            </View>
          )}

          {/* Volume chart */}
          <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={styles.chartHeader}>
              <Text style={[styles.chartTitle, { color: colors.foreground }]}>
                Volume total (kg)
              </Text>
              <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>
                Somme reps × charge par séance
              </Text>
            </View>
            <LineChart
              data={volumeData}
              color={tabColor}
              unit=" kg"
              height={190}
              formatValue={(v) => v >= 1000 ? `${(v / 1000).toFixed(1)}t` : `${Math.round(v)}`}
            />
          </View>

          {/* Max weight chart */}
          <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={styles.chartHeader}>
              <Text style={[styles.chartTitle, { color: colors.foreground }]}>
                Charge maximale (kg)
              </Text>
              <Text style={[styles.chartSub, { color: colors.mutedForeground }]}>
                Meilleure charge soulevée par séance
              </Text>
            </View>
            <LineChart
              data={maxData}
              color={tabColor}
              unit=" kg"
              height={190}
              formatValue={(v) => `${v}`}
            />
          </View>

          {/* Best exercises for this group */}
          {volumeData.length > 0 && (
            <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={[styles.chartTitle, { color: colors.foreground, marginBottom: 14 }]}>
                Exercices {MUSCLE_GROUP_LABELS[activeMuscleGroup].toLowerCase()}
              </Text>
              {exercises
                .filter((e) => e.muscleGroup === activeMuscleGroup)
                .map((ex) => {
                  const history = workouts
                    .filter((w) => w.exercises.some((we) => we.exerciseId === ex.id))
                    .length;
                  if (history === 0) return null;
                  const pb = workouts
                    .flatMap((w) => w.exercises.find((we) => we.exerciseId === ex.id)?.sets ?? [])
                    .reduce((max, s) => Math.max(max, s.weight), 0);
                  return (
                    <TouchableOpacity
                      key={ex.id}
                      style={[styles.exerciseRow, { borderBottomColor: colors.border }]}
                      onPress={() =>
                        router.push({
                          pathname: "/exercise-history",
                          params: { exerciseId: ex.id },
                        })
                      }
                    >
                      <View style={{ flex: 1 }}>
                        <Text style={[styles.exerciseName, { color: colors.foreground }]}>
                          {ex.name}
                        </Text>
                        <Text style={[styles.exerciseMeta, { color: colors.mutedForeground }]}>
                          {history} séance{history > 1 ? "s" : ""}
                        </Text>
                      </View>
                      <View style={styles.exerciseRight}>
                        {pb > 0 && (
                          <View style={styles.pbRow}>
                            <Feather name="award" size={12} color={tabColor} />
                            <Text style={[styles.pbText, { color: tabColor }]}>
                              {pb} kg
                            </Text>
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
  pageTitle: {
    fontSize: 32,
    fontFamily: "Inter_700Bold",
    letterSpacing: -0.5,
    paddingHorizontal: 20,
    marginBottom: 16,
  },
  tabScroll: {
    marginBottom: 20,
  },
  tabRow: {
    paddingHorizontal: 20,
    gap: 8,
    flexDirection: "row",
    alignItems: "center",
  },
  tab: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1.5,
  },
  tabText: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
  },
  tabBadge: {
    minWidth: 18,
    height: 18,
    borderRadius: 9,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 4,
  },
  tabBadgeText: {
    fontSize: 10,
    fontFamily: "Inter_700Bold",
  },
  statsRow: {
    flexDirection: "row",
    paddingHorizontal: 20,
    gap: 10,
    marginBottom: 16,
  },
  statCard: {
    flex: 1,
    borderRadius: 12,
    borderWidth: 1,
    padding: 12,
    alignItems: "center",
    gap: 4,
  },
  statValue: {
    fontSize: 16,
    fontFamily: "Inter_700Bold",
  },
  statLabel: {
    fontSize: 10,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
  },
  chartCard: {
    marginHorizontal: 20,
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
    overflow: "hidden",
  },
  chartHeader: {
    marginBottom: 12,
  },
  chartTitle: {
    fontSize: 14,
    fontFamily: "Inter_700Bold",
  },
  chartSub: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    marginTop: 2,
  },
  emptyState: {
    alignItems: "center",
    paddingHorizontal: 40,
    paddingVertical: 40,
    gap: 10,
  },
  emptyTitle: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
    marginTop: 4,
  },
  emptyText: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
    lineHeight: 20,
  },
  exerciseRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  exerciseName: {
    fontSize: 14,
    fontFamily: "Inter_600SemiBold",
    marginBottom: 2,
  },
  exerciseMeta: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
  },
  exerciseRight: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  pbRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 3,
  },
  pbText: {
    fontSize: 13,
    fontFamily: "Inter_700Bold",
  },
});
