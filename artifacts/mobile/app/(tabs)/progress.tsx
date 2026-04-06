import { Feather } from "@expo/vector-icons";
import { router } from "expo-router";
import React, { useMemo, useState } from "react";
import {
  Dimensions,
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
import MuscleGroupBadge, { MUSCLE_GROUP_LABELS } from "@/components/MuscleGroupBadge";

const SCREEN_W = Dimensions.get("window").width;

export default function ProgressScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { workouts, exercises, getExerciseHistory } = useWorkout();
  const [selectedGroup, setSelectedGroup] = useState<MuscleGroup | null>(null);

  const topPad = Platform.OS === "web" ? 67 : insets.top + 10;
  const bottomPad = Platform.OS === "web" ? 34 + 84 : insets.bottom + 100;

  const muscleGroups: MuscleGroup[] = ["dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio"];

  // Body weight chart data (last 10)
  const bodyWeightData = useMemo(() => {
    return workouts.slice(0, 10).reverse().map((w) => ({
      date: new Date(w.date),
      weight: w.bodyWeight,
    }));
  }, [workouts]);

  const maxBw = bodyWeightData.length > 0 ? Math.max(...bodyWeightData.map((d) => d.weight)) : 0;
  const minBw = bodyWeightData.length > 0 ? Math.min(...bodyWeightData.map((d) => d.weight)) : 0;
  const bwRange = maxBw - minBw || 1;

  const CHART_H = 110;

  // Top exercises by frequency
  const exerciseFrequency = useMemo(() => {
    const freq: Record<string, number> = {};
    for (const w of workouts) {
      for (const e of w.exercises) {
        freq[e.exerciseId] = (freq[e.exerciseId] ?? 0) + 1;
      }
    }
    return Object.entries(freq)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([id, count]) => ({
        exercise: exercises.find((e) => e.id === id),
        count,
      }))
      .filter((x) => x.exercise !== undefined) as { exercise: typeof exercises[0]; count: number }[];
  }, [workouts, exercises]);

  const filteredExercises = useMemo(() => {
    if (!selectedGroup) return exercises;
    return exercises.filter((e) => e.muscleGroup === selectedGroup);
  }, [exercises, selectedGroup]);

  function formatDate(d: Date) {
    return d.toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit" });
  }

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ paddingTop: topPad, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
    >
      <Text style={[styles.pageTitle, { color: colors.foreground }]}>
        Progression
      </Text>

      {/* Body weight chart */}
      {bodyWeightData.length >= 2 && (
        <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <View style={styles.cardHeader}>
            <Feather name="user" size={16} color={colors.primary} />
            <Text style={[styles.cardTitle, { color: colors.foreground }]}>
              Poids de corps
            </Text>
            <Text style={[styles.cardSubtitle, { color: colors.mutedForeground }]}>
              {workouts[0]?.bodyWeight} kg actuel
            </Text>
          </View>

          <View style={styles.chart}>
            <View style={styles.yAxisSmall}>
              <Text style={[styles.axisLabel, { color: colors.mutedForeground }]}>{maxBw}</Text>
              <Text style={[styles.axisLabel, { color: colors.mutedForeground }]}>{minBw}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <View style={[styles.chartArea, { height: CHART_H }]}>
                {[0, 0.5, 1].map((pct) => (
                  <View
                    key={pct}
                    style={[styles.gridLine, { bottom: CHART_H * pct, borderColor: colors.border }]}
                  />
                ))}
                {bodyWeightData.map((d, i) => {
                  const x = bodyWeightData.length > 1 ? (i / (bodyWeightData.length - 1)) * 100 : 50;
                  const y = ((d.weight - minBw) / bwRange) * 100;
                  return (
                    <View
                      key={i}
                      style={[
                        styles.dataPoint,
                        {
                          left: `${x}%`,
                          bottom: `${y}%`,
                          backgroundColor: colors.info,
                          transform: [{ translateX: -5 }, { translateY: 5 }],
                        },
                      ]}
                    />
                  );
                })}
              </View>
              <View style={[styles.xAxisRow, { height: 20 }]}>
                {bodyWeightData.map((d, i) => (
                  <Text
                    key={i}
                    style={[
                      styles.axisLabel,
                      {
                        color: colors.mutedForeground,
                        position: "absolute",
                        left: `${bodyWeightData.length > 1 ? (i / (bodyWeightData.length - 1)) * 100 : 50}%`,
                        transform: [{ translateX: -12 }],
                      },
                    ]}
                  >
                    {formatDate(d.date)}
                  </Text>
                ))}
              </View>
            </View>
          </View>
        </View>
      )}

      {/* Top exercises */}
      {exerciseFrequency.length > 0 && (
        <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <View style={styles.cardHeader}>
            <Feather name="star" size={16} color={colors.primary} />
            <Text style={[styles.cardTitle, { color: colors.foreground }]}>
              Exercices favoris
            </Text>
          </View>
          {exerciseFrequency.map(({ exercise, count }) => (
            <TouchableOpacity
              key={exercise.id}
              style={[styles.exerciseRow, { borderBottomColor: colors.border }]}
              onPress={() =>
                router.push({
                  pathname: "/exercise-history",
                  params: { exerciseId: exercise.id },
                })
              }
            >
              <View style={styles.exerciseRowLeft}>
                <Text style={[styles.exerciseName, { color: colors.foreground }]}>
                  {exercise.name}
                </Text>
                <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
              </View>
              <View style={styles.exerciseRowRight}>
                <Text style={[styles.countText, { color: colors.primary }]}>
                  {count}×
                </Text>
                <Feather name="chevron-right" size={14} color={colors.mutedForeground} />
              </View>
            </TouchableOpacity>
          ))}
        </View>
      )}

      {/* All exercises browse */}
      <View style={styles.sectionHeader}>
        <Text style={[styles.sectionTitle, { color: colors.foreground }]}>
          Tous les exercices
        </Text>
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.groupFilter}>
        <TouchableOpacity
          style={[
            styles.groupChip,
            {
              backgroundColor: selectedGroup === null ? colors.primary : colors.card,
              borderColor: selectedGroup === null ? colors.primary : colors.border,
            },
          ]}
          onPress={() => setSelectedGroup(null)}
        >
          <Text style={[styles.groupChipText, { color: selectedGroup === null ? colors.primaryForeground : colors.foreground }]}>
            Tous
          </Text>
        </TouchableOpacity>
        {muscleGroups.map((g) => (
          <TouchableOpacity
            key={g}
            style={[
              styles.groupChip,
              {
                backgroundColor: selectedGroup === g ? colors.primary : colors.card,
                borderColor: selectedGroup === g ? colors.primary : colors.border,
              },
            ]}
            onPress={() => setSelectedGroup(selectedGroup === g ? null : g)}
          >
            <Text style={[styles.groupChipText, { color: selectedGroup === g ? colors.primaryForeground : colors.foreground }]}>
              {MUSCLE_GROUP_LABELS[g]}
            </Text>
          </TouchableOpacity>
        ))}
      </ScrollView>

      <View style={styles.exerciseList}>
        {filteredExercises.map((exercise) => {
          const history = getExerciseHistory(exercise.id);
          const pb = history.length > 0
            ? Math.max(...history.flatMap((h) => h.sets.map((s) => s.weight)))
            : null;

          return (
            <TouchableOpacity
              key={exercise.id}
              style={[styles.exerciseCard, { backgroundColor: colors.card, borderColor: colors.border }]}
              onPress={() =>
                router.push({
                  pathname: "/exercise-history",
                  params: { exerciseId: exercise.id },
                })
              }
              activeOpacity={0.8}
            >
              <View style={styles.exerciseCardLeft}>
                <Text style={[styles.exerciseName, { color: colors.foreground }]}>
                  {exercise.name}
                </Text>
                <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
              </View>
              <View style={styles.exerciseCardRight}>
                {pb !== null && (
                  <View style={styles.pbRow}>
                    <Feather name="award" size={12} color={colors.primary} />
                    <Text style={[styles.pbText, { color: colors.primary }]}>
                      {pb} kg
                    </Text>
                  </View>
                )}
                {history.length > 0 && (
                  <Text style={[styles.histCount, { color: colors.mutedForeground }]}>
                    {history.length} séances
                  </Text>
                )}
                <Feather name="chevron-right" size={14} color={colors.mutedForeground} />
              </View>
            </TouchableOpacity>
          );
        })}
      </View>
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
    marginBottom: 20,
  },
  card: {
    marginHorizontal: 20,
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  cardHeader: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    marginBottom: 16,
  },
  cardTitle: {
    fontSize: 15,
    fontFamily: "Inter_700Bold",
    flex: 1,
  },
  cardSubtitle: {
    fontSize: 13,
    fontFamily: "Inter_500Medium",
  },
  chart: {
    flexDirection: "row",
    gap: 8,
  },
  yAxisSmall: {
    justifyContent: "space-between",
    height: 110,
    paddingVertical: 2,
    width: 32,
    alignItems: "flex-end",
  },
  chartArea: {
    flex: 1,
    position: "relative",
  },
  gridLine: {
    position: "absolute",
    left: 0,
    right: 0,
    borderTopWidth: 1,
    borderStyle: "dashed",
  },
  dataPoint: {
    position: "absolute",
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  xAxisRow: {
    position: "relative",
    marginTop: 4,
  },
  axisLabel: {
    fontSize: 10,
    fontFamily: "Inter_400Regular",
  },
  exerciseRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  exerciseRowLeft: {
    flex: 1,
    gap: 5,
  },
  exerciseRowRight: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  countText: {
    fontSize: 14,
    fontFamily: "Inter_700Bold",
  },
  sectionHeader: {
    paddingHorizontal: 20,
    marginBottom: 14,
  },
  sectionTitle: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
  },
  groupFilter: {
    paddingHorizontal: 20,
    marginBottom: 14,
  },
  groupChip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    borderWidth: 1,
    marginRight: 8,
  },
  groupChipText: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
  },
  exerciseList: {
    paddingHorizontal: 20,
  },
  exerciseCard: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    marginBottom: 8,
  },
  exerciseCardLeft: {
    flex: 1,
    gap: 5,
  },
  exerciseName: {
    fontSize: 14,
    fontFamily: "Inter_600SemiBold",
  },
  exerciseCardRight: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
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
  histCount: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
  },
});
