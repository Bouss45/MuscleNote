import { Feather } from "@expo/vector-icons";
import { router, useLocalSearchParams } from "expo-router";
import React from "react";
import {
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { useExerciseName } from "@/hooks/useExerciseName";
import { Set as WorkoutSet, useWorkout } from "@/context/WorkoutContext";
import { usePreferences } from "@/context/PreferencesContext";
import { bestOneRepMax } from "@/utils/strength";
import LineChart from "@/components/LineChart";
import MuscleGroupBadge from "@/components/MuscleGroupBadge";

/** Cardio sets have no load: summarize them as duration / distance. */
function formatCardioSet(s: WorkoutSet): string {
  const parts = [`${s.duration ?? 0} min`];
  if (s.distance) parts.push(`${s.distance} km`);
  if (s.speed) parts.push(`${s.speed} km/h`);
  if (s.incline) parts.push(`${s.incline} %`);
  return parts.join(" · ");
}

export default function ExerciseHistoryScreen() {
  const colors = useColors();
  const { t, locale } = usePreferences();
  const insets = useSafeAreaInsets();
  const { exerciseId } = useLocalSearchParams<{ exerciseId: string }>();
  const { exerciseById, getExerciseHistory } = useWorkout();
  const exerciseName = useExerciseName();

  const topPad = insets.top;
  const bottomPad = insets.bottom + 20;

  const exercise = exerciseById.get(exerciseId ?? "");
  const isCardio = exercise?.muscleGroup === "cardio";
  const unit = isCardio ? " min" : " kg";
  const history = getExerciseHistory(exerciseId ?? "");

  // One point per session: max load, or total duration for cardio.
  const chartData = history.map((h) => ({
    label: new Date(h.date).toLocaleDateString(locale, { day: "2-digit", month: "2-digit" }),
    value: isCardio
      ? h.sets.reduce((sum, s) => sum + (s.duration ?? 0), 0)
      : h.sets.reduce((max, s) => Math.max(max, s.weight), 0),
  }));

  function formatFull(d: Date) {
    return d.toLocaleDateString(locale, { weekday: "short", day: "numeric", month: "short" });
  }

  const pb = chartData.length > 0 ? Math.max(...chartData.map((d) => d.value)) : null;
  const best1rm = isCardio ? 0 : history.reduce((best, h) => Math.max(best, bestOneRepMax(h.sets)), 0);

  if (!exercise) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background, alignItems: "center", justifyContent: "center" }]}>
        <Text style={{ color: colors.foreground }}>{t("exerciseHistory.notFound")}</Text>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={{ color: colors.primary, marginTop: 12 }}>{t("common.back")}</Text>
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
            {exerciseName(exercise)}
          </Text>
          <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
        </View>
      </View>

      {pb !== null && (
        <View style={styles.statRow}>
          <View style={[styles.pbCard, { backgroundColor: colors.primary + "18", borderColor: colors.primary + "55" }]}>
            <Feather name="award" size={20} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text style={[styles.pbLabel, { color: colors.mutedForeground }]}>
                {t(isCardio ? "exerciseHistory.longest" : "exerciseHistory.pr")}
              </Text>
              <Text style={[styles.pbValue, { color: colors.primary }]}>
                {pb}{unit}
              </Text>
            </View>
          </View>
          {best1rm > 0 && (
            <View style={[styles.pbCard, { backgroundColor: colors.info + "18", borderColor: colors.info + "55" }]}>
              <Feather name="zap" size={20} color={colors.info} />
              <View style={{ flex: 1 }}>
                <Text style={[styles.pbLabel, { color: colors.mutedForeground }]}>
                  {t("exerciseHistory.oneRm")}
                </Text>
                <Text style={[styles.pbValue, { color: colors.info }]}>
                  {best1rm} kg
                </Text>
              </View>
            </View>
          )}
        </View>
      )}

      {chartData.length >= 2 ? (
        <View style={[styles.chartCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Text style={[styles.chartTitle, { color: colors.foreground }]}>
            {t(isCardio ? "exerciseHistory.durationChart" : "exerciseHistory.maxLoad")}
          </Text>
          <LineChart data={chartData} color={colors.primary} unit={unit} height={180} formatValue={(v) => `${Math.round(v * 10) / 10}`} />
        </View>
      ) : chartData.length === 1 ? (
        <View style={[styles.oneDataCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Feather name="bar-chart-2" size={24} color={colors.mutedForeground} />
          <Text style={[styles.oneDataText, { color: colors.mutedForeground }]}>
            {t("exerciseHistory.needMore")}
          </Text>
        </View>
      ) : null}

      {/* History list */}
      <View style={styles.section}>
        <Text style={[styles.sectionTitle, { color: colors.foreground }]}>
          {t("exerciseHistory.historyTitle")}
        </Text>

        {history.length === 0 ? (
          <View style={[styles.emptyCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Feather name="clock" size={24} color={colors.mutedForeground} />
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              {t("exerciseHistory.empty")}
            </Text>
          </View>
        ) : (
          history.map((h) => {
            const vol = h.sets.reduce((s, set) => s + set.reps * set.weight, 0);
            const maxKg = h.sets.reduce((max, s) => Math.max(max, s.weight), 0);
            const row1rm = isCardio ? 0 : bestOneRepMax(h.sets);
            return (
              <View
                key={h.workoutId}
                style={[styles.historyCard, { backgroundColor: colors.card, borderColor: colors.border }]}
              >
                <View style={styles.historyTop}>
                  <Text style={[styles.historyDate, { color: colors.foreground }]}>
                    {formatFull(new Date(h.date))}
                  </Text>
                  <Text style={[styles.historyBw, { color: colors.mutedForeground }]}>
                    {t("exerciseHistory.bodyWeightSuffix", { weight: h.bodyWeight })}
                  </Text>
                </View>

                <View style={styles.historySets}>
                  {h.sets.map((s, si) => (
                    <View key={si} style={[styles.historySet, { backgroundColor: colors.muted }]}>
                      <Text style={[styles.historySetText, { color: colors.foreground }]}>
                        {isCardio ? formatCardioSet(s) : `${s.reps} × ${s.weight} kg`}
                      </Text>
                    </View>
                  ))}
                </View>

                {!isCardio && <View style={styles.historyStats}>
                  <View style={styles.historyStat}>
                    <Feather name="trending-up" size={12} color={colors.primary} />
                    <Text style={[styles.historyStatText, { color: colors.mutedForeground }]}>
                      {t("exerciseHistory.max", { value: maxKg })}
                    </Text>
                  </View>
                  <View style={styles.historyStat}>
                    <Feather name="layers" size={12} color={colors.mutedForeground} />
                    <Text style={[styles.historyStatText, { color: colors.mutedForeground }]}>
                      {t("exerciseHistory.volume", { value: Math.round(vol) })}
                    </Text>
                  </View>
                  {row1rm > 0 && (
                    <View style={styles.historyStat}>
                      <Feather name="zap" size={12} color={colors.info} />
                      <Text style={[styles.historyStatText, { color: colors.mutedForeground }]}>
                        {t("exerciseHistory.rowOneRm", { value: row1rm })}
                      </Text>
                    </View>
                  )}
                </View>}
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
  statRow: {
    flexDirection: "row",
    paddingHorizontal: 20,
    gap: 10,
    marginBottom: 16,
  },
  pbCard: {
    flex: 1,
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
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
