import { Feather } from "@expo/vector-icons";
import { router, useLocalSearchParams } from "expo-router";
import React, { useMemo } from "react";
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
import { useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge from "@/components/MuscleGroupBadge";

const SCREEN_W = Dimensions.get("window").width;
const CHART_W = SCREEN_W - 48;
const CHART_H = 160;

export default function ExerciseHistoryScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { exerciseId } = useLocalSearchParams<{ exerciseId: string }>();
  const { exercises, getExerciseHistory } = useWorkout();

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom + 20;

  const exercise = exercises.find((e) => e.id === exerciseId);
  const history = getExerciseHistory(exerciseId ?? "");

  const chartData = useMemo(() => {
    return history.map((h) => {
      const maxWeight = Math.max(...h.sets.map((s) => s.weight));
      const maxVolume = h.sets.reduce((s, set) => s + set.reps * set.weight, 0);
      return {
        date: new Date(h.date),
        maxWeight,
        maxVolume,
        bodyWeight: h.bodyWeight,
        sets: h.sets,
      };
    });
  }, [history]);

  const maxW = chartData.length > 0 ? Math.max(...chartData.map((d) => d.maxWeight)) : 0;
  const minW = chartData.length > 0 ? Math.min(...chartData.map((d) => d.maxWeight)) : 0;
  const range = maxW - minW || 1;

  function formatDate(d: Date) {
    return d.toLocaleDateString("fr-FR", { day: "2-digit", month: "2-digit" });
  }

  function formatFull(d: Date) {
    return d.toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "short" });
  }

  const pb = chartData.length > 0 ? Math.max(...chartData.map((d) => d.maxWeight)) : null;

  if (!exercise) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background, alignItems: "center", justifyContent: "center" }]}>
        <Text style={{ color: colors.foreground }}>Exercice introuvable</Text>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ color: colors.primary, marginTop: 12 }}>Retour</Text>
        </TouchableOpacity>
      </View>
    );
  }

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ paddingTop: topPad + 12, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.header}>
        <TouchableOpacity onPress={() => router.back()} style={styles.backBtn}>
          <Feather name="arrow-left" size={22} color={colors.foreground} />
        </TouchableOpacity>
        <View style={{ flex: 1 }}>
          <Text style={[styles.title, { color: colors.foreground }]}>
            {exercise.name}
          </Text>
          <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
        </View>
      </View>

      {pb !== null && (
        <View style={[styles.pbCard, { backgroundColor: colors.primary + "18", borderColor: colors.primary + "55" }]}>
          <Feather name="award" size={20} color={colors.primary} />
          <View>
            <Text style={[styles.pbLabel, { color: colors.mutedForeground }]}>
              Record personnel
            </Text>
            <Text style={[styles.pbValue, { color: colors.primary }]}>
              {pb} kg
            </Text>
          </View>
        </View>
      )}

      {chartData.length >= 2 ? (
        <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Text style={[styles.chartTitle, { color: colors.foreground }]}>
            Charge maximale (kg)
          </Text>
          <View style={styles.chart}>
            {/* Y-axis labels */}
            <View style={styles.yAxis}>
              <Text style={[styles.axisLabel, { color: colors.mutedForeground }]}>{maxW}</Text>
              <Text style={[styles.axisLabel, { color: colors.mutedForeground }]}>{Math.round((maxW + minW) / 2)}</Text>
              <Text style={[styles.axisLabel, { color: colors.mutedForeground }]}>{minW}</Text>
            </View>

            {/* Chart area */}
            <View style={{ flex: 1 }}>
              <View style={[styles.chartArea, { height: CHART_H }]}>
                {/* Grid lines */}
                {[0, 0.5, 1].map((pct) => (
                  <View
                    key={pct}
                    style={[
                      styles.gridLine,
                      {
                        bottom: CHART_H * pct,
                        borderColor: colors.border,
                      },
                    ]}
                  />
                ))}

                {/* Data points and lines */}
                {chartData.map((d, i) => {
                  const x = (i / (chartData.length - 1)) * 100;
                  const y = ((d.maxWeight - minW) / range) * 100;
                  return (
                    <View
                      key={i}
                      style={[
                        styles.dataPoint,
                        {
                          left: `${x}%`,
                          bottom: `${y}%`,
                          backgroundColor: d.maxWeight === maxW ? colors.primary : colors.primary + "88",
                          transform: [{ translateX: -5 }, { translateY: 5 }],
                        },
                      ]}
                    />
                  );
                })}
              </View>

              {/* X-axis labels */}
              <View style={styles.xAxis}>
                {chartData.map((d, i) => (
                  <Text
                    key={i}
                    style={[
                      styles.axisLabel,
                      {
                        color: colors.mutedForeground,
                        position: "absolute",
                        left: `${(i / (chartData.length - 1)) * 100}%`,
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
      ) : chartData.length === 1 ? (
        <View style={[styles.oneDataCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Feather name="bar-chart-2" size={24} color={colors.mutedForeground} />
          <Text style={[styles.oneDataText, { color: colors.mutedForeground }]}>
            Fais encore des séances pour voir ta progression !
          </Text>
        </View>
      ) : null}

      {/* History list */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.foreground }]}>
          Historique
        </Text>

        {history.length === 0 ? (
          <View style={[styles.emptyCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Feather name="clock" size={24} color={colors.mutedForeground} />
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Aucune séance avec cet exercice
            </Text>
          </View>
        ) : (
          history.map((h, idx) => {
            const vol = h.sets.reduce((s, set) => s + set.reps * set.weight, 0);
            const maxKg = Math.max(...h.sets.map((s) => s.weight));
            return (
              <View
                key={idx}
                style={[styles.historyCard, { backgroundColor: colors.card, borderColor: colors.border }]}
              >
                <View style={styles.historyTop}>
                  <Text style={[styles.historyDate, { color: colors.foreground }]}>
                    {formatFull(new Date(h.date))}
                  </Text>
                  <Text style={[styles.historyBw, { color: colors.mutedForeground }]}>
                    {h.bodyWeight} kg corps
                  </Text>
                </View>

                <View style={styles.historySets}>
                  {h.sets.map((s, si) => (
                    <View key={si} style={[styles.historySet, { backgroundColor: colors.muted }]}>
                      <Text style={[styles.historySetText, { color: colors.foreground }]}>
                        {s.reps} × {s.weight} kg
                      </Text>
                    </View>
                  ))}
                </View>

                <View style={styles.historyStats}>
                  <View style={styles.historyStat}>
                    <Feather name="trending-up" size={12} color={colors.primary} />
                    <Text style={[styles.historyStatText, { color: colors.mutedForeground }]}>
                      Max {maxKg} kg
                    </Text>
                  </View>
                  <View style={styles.historyStat}>
                    <Feather name="layers" size={12} color={colors.mutedForeground} />
                    <Text style={[styles.historyStatText, { color: colors.mutedForeground }]}>
                      Volume {Math.round(vol)} kg
                    </Text>
                  </View>
                </View>
              </View>
            );
          })
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 14,
    paddingHorizontal: 20,
    marginBottom: 20,
  },
  backBtn: { paddingTop: 2 },
  title: {
    fontSize: 24,
    fontFamily: "Inter_700Bold",
    marginBottom: 6,
  },
  pbCard: {
    marginHorizontal: 20,
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    marginBottom: 16,
  },
  pbLabel: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
  },
  pbValue: {
    fontSize: 24,
    fontFamily: "Inter_700Bold",
  },
  chartCard: {
    marginHorizontal: 20,
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  chartTitle: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
    marginBottom: 14,
  },
  chart: {
    flexDirection: "row",
    gap: 8,
  },
  yAxis: {
    justifyContent: "space-between",
    height: CHART_H,
    paddingVertical: 2,
    width: 36,
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
  xAxis: {
    height: 20,
    position: "relative",
    marginTop: 4,
  },
  axisLabel: {
    fontSize: 10,
    fontFamily: "Inter_400Regular",
  },
  oneDataCard: {
    marginHorizontal: 20,
    borderRadius: 14,
    borderWidth: 1,
    padding: 20,
    alignItems: "center",
    gap: 10,
    marginBottom: 16,
  },
  oneDataText: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
  },
  section: {
    paddingHorizontal: 20,
  },
  sectionTitle: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
    marginBottom: 14,
  },
  emptyCard: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 24,
    alignItems: "center",
    gap: 10,
  },
  emptyText: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
  },
  historyCard: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    marginBottom: 10,
  },
  historyTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 10,
  },
  historyDate: {
    fontSize: 14,
    fontFamily: "Inter_600SemiBold",
  },
  historyBw: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
  },
  historySets: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 6,
    marginBottom: 10,
  },
  historySet: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 8,
  },
  historySetText: {
    fontSize: 13,
    fontFamily: "Inter_500Medium",
  },
  historyStats: {
    flexDirection: "row",
    gap: 14,
  },
  historyStat: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  historyStatText: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
  },
});
