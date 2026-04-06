import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React from "react";
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
import { useWorkout } from "@/context/WorkoutContext";

export default function HomeScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { workouts, currentWorkout } = useWorkout();

  const topPad = Platform.OS === "web" ? 67 : insets.top + 10;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom + 90;

  const recentWorkouts = workouts.slice(0, 10);

  function formatDate(iso: string) {
    const d = new Date(iso);
    return d.toLocaleDateString("fr-FR", {
      weekday: "short",
      day: "numeric",
      month: "short",
    });
  }

  function formatTime(iso: string) {
    const d = new Date(iso);
    return d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
  }

  function getTotalSets(workout: typeof workouts[0]) {
    return workout.exercises.reduce((sum, e) => sum + e.sets.length, 0);
  }

  function getTotalVolume(workout: typeof workouts[0]) {
    return workout.exercises.reduce((sum, e) => {
      return sum + e.sets.reduce((s2, s) => s2 + s.reps * s.weight, 0);
    }, 0);
  }

  return (
    <ScrollView
      style={[styles.container, { backgroundColor: colors.background }]}
      contentContainerStyle={{ paddingTop: topPad, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
    >
      <View style={styles.header}>
        <Text style={[styles.appTitle, { color: colors.foreground }]}>
          GymTracker
        </Text>
        <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>
          {workouts.length} séance{workouts.length !== 1 ? "s" : ""} enregistrée{workouts.length !== 1 ? "s" : ""}
        </Text>
      </View>

      {currentWorkout ? (
        <TouchableOpacity
          style={[styles.activeCard, { backgroundColor: colors.primary + "22", borderColor: colors.primary }]}
          onPress={() => router.push("/workout")}
          activeOpacity={0.8}
        >
          <View style={styles.activeCardLeft}>
            <View style={[styles.activeDot, { backgroundColor: colors.primary }]} />
            <View>
              <Text style={[styles.activeTitle, { color: colors.primary }]}>
                {currentWorkout.name}
              </Text>
              <Text style={[styles.activeSubtitle, { color: colors.mutedForeground }]}>
                {currentWorkout.exercises.length} exercice{currentWorkout.exercises.length !== 1 ? "s" : ""} • {currentWorkout.bodyWeight} kg corps
              </Text>
            </View>
          </View>
          <Feather name="chevron-right" size={20} color={colors.primary} />
        </TouchableOpacity>
      ) : (
        <TouchableOpacity
          style={[styles.startBtn, { backgroundColor: colors.primary }]}
          onPress={() => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
            router.push("/start-workout");
          }}
          activeOpacity={0.85}
        >
          <Feather name="play" size={20} color={colors.primaryForeground} />
          <Text style={[styles.startBtnText, { color: colors.primaryForeground }]}>
            Nouvelle séance
          </Text>
        </TouchableOpacity>
      )}

      {/* Stats row */}
      {workouts.length > 0 && (
        <View style={styles.statsRow}>
          <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Text style={[styles.statValue, { color: colors.foreground }]}>
              {workouts.length}
            </Text>
            <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
              Séances
            </Text>
          </View>
          <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Text style={[styles.statValue, { color: colors.foreground }]}>
              {Math.round(workouts.reduce((s, w) => s + getTotalVolume(w), 0) / 1000)}t
            </Text>
            <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
              Volume total
            </Text>
          </View>
          <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Text style={[styles.statValue, { color: colors.foreground }]}>
              {workouts[0]?.bodyWeight ?? "—"} kg
            </Text>
            <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
              Dernier poids
            </Text>
          </View>
        </View>
      )}

      {/* Recent workouts */}
      {recentWorkouts.length > 0 && (
        <View style={styles.section}>
          <Text style={[styles.sectionTitle, { color: colors.foreground }]}>
            Séances récentes
          </Text>
          {recentWorkouts.map((workout) => (
            <View
              key={workout.id}
              style={[styles.workoutCard, { backgroundColor: colors.card, borderColor: colors.border }]}
            >
              <View style={styles.workoutCardTop}>
                <View>
                  <Text style={[styles.workoutDate, { color: colors.foreground }]}>
                    {workout.name || formatDate(workout.date)}
                  </Text>
                  <Text style={[styles.workoutTime, { color: colors.mutedForeground }]}>
                    {formatDate(workout.date)} · {formatTime(workout.date)}{workout.durationMinutes ? ` · ${workout.durationMinutes} min` : ""}
                  </Text>
                </View>
                <View style={styles.workoutRight}>
                  <Text style={[styles.bodyWeightBadge, { color: colors.primary, backgroundColor: colors.primary + "15" }]}>
                    {workout.bodyWeight} kg
                  </Text>
                </View>
              </View>
              <View style={styles.workoutStats}>
                <View style={styles.workoutStat}>
                  <Feather name="layers" size={12} color={colors.mutedForeground} />
                  <Text style={[styles.workoutStatText, { color: colors.mutedForeground }]}>
                    {workout.exercises.length} exercices
                  </Text>
                </View>
                <View style={styles.workoutStat}>
                  <Feather name="repeat" size={12} color={colors.mutedForeground} />
                  <Text style={[styles.workoutStatText, { color: colors.mutedForeground }]}>
                    {getTotalSets(workout)} séries
                  </Text>
                </View>
                <View style={styles.workoutStat}>
                  <Feather name="trending-up" size={12} color={colors.mutedForeground} />
                  <Text style={[styles.workoutStatText, { color: colors.mutedForeground }]}>
                    {Math.round(getTotalVolume(workout))} kg vol.
                  </Text>
                </View>
              </View>
            </View>
          ))}
        </View>
      )}

      {workouts.length === 0 && !currentWorkout && (
        <View style={styles.empty}>
          <Feather name="activity" size={48} color={colors.mutedForeground} />
          <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
            Prêt à s'entrainer ?
          </Text>
          <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
            Lance ta première séance pour commencer à suivre tes progrès
          </Text>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    marginBottom: 24,
  },
  appTitle: {
    fontSize: 32,
    fontFamily: "Inter_700Bold",
    letterSpacing: -0.5,
  },
  subtitle: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
    marginTop: 2,
  },
  activeCard: {
    marginHorizontal: 20,
    borderRadius: 14,
    borderWidth: 1.5,
    padding: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 20,
  },
  activeCardLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  activeDot: {
    width: 10,
    height: 10,
    borderRadius: 5,
  },
  activeTitle: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  activeSubtitle: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    marginTop: 2,
  },
  startBtn: {
    marginHorizontal: 20,
    borderRadius: 14,
    paddingVertical: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    marginBottom: 24,
  },
  startBtnText: {
    fontSize: 17,
    fontFamily: "Inter_700Bold",
  },
  statsRow: {
    flexDirection: "row",
    paddingHorizontal: 20,
    gap: 10,
    marginBottom: 28,
  },
  statCard: {
    flex: 1,
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    alignItems: "center",
    gap: 4,
  },
  statValue: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
  },
  statLabel: {
    fontSize: 10,
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
  workoutCard: {
    borderRadius: 12,
    borderWidth: 1,
    padding: 14,
    marginBottom: 10,
  },
  workoutCardTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    marginBottom: 10,
  },
  workoutDate: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  workoutTime: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    marginTop: 2,
  },
  workoutRight: {},
  bodyWeightBadge: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 8,
  },
  workoutStats: {
    flexDirection: "row",
    gap: 14,
  },
  workoutStat: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  workoutStatText: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
  },
  empty: {
    alignItems: "center",
    paddingHorizontal: 40,
    paddingTop: 60,
    gap: 12,
  },
  emptyTitle: {
    fontSize: 20,
    fontFamily: "Inter_700Bold",
    marginTop: 8,
  },
  emptyText: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
    lineHeight: 22,
  },
});
