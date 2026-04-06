import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useState } from "react";
import {
  Alert,
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
import ExerciseCard from "@/components/ExerciseCard";

export default function WorkoutScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { currentWorkout, exercises, endWorkout } = useWorkout();

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 + 80 : insets.bottom + 90;

  if (!currentWorkout) {
    return (
      <View
        style={[
          styles.container,
          {
            backgroundColor: colors.background,
            alignItems: "center",
            justifyContent: "center",
          },
        ]}
      >
        <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
          Aucune séance active
        </Text>
        <TouchableOpacity onPress={() => router.replace("/")}>
          <Text style={[styles.backLink, { color: colors.primary }]}>
            Retour
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  function getElapsed() {
    const diff = Date.now() - new Date(currentWorkout!.date).getTime();
    const min = Math.floor(diff / 60000);
    const sec = Math.floor((diff % 60000) / 1000);
    return `${min}:${sec.toString().padStart(2, "0")}`;
  }

  function handleFinish() {
    Alert.alert(
      "Terminer la séance ?",
      "La séance sera sauvegardée.",
      [
        { text: "Annuler", style: "cancel" },
        {
          text: "Terminer",
          style: "destructive",
          onPress: () => {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
            router.replace("/");
            endWorkout();
          },
        },
      ]
    );
  }

  const currentExercises = currentWorkout.exercises
    .map((we) => ({
      we,
      exercise: exercises.find((e) => e.id === we.exerciseId),
    }))
    .filter((x) => x.exercise !== undefined) as {
    we: typeof currentWorkout.exercises[0];
    exercise: typeof exercises[0];
  }[];

  const completedCount = currentWorkout.exercises.reduce(
    (sum, e) => sum + e.sets.filter((s) => s.completed).length,
    0
  );
  const totalSets = currentWorkout.exercises.reduce(
    (sum, e) => sum + e.sets.length,
    0
  );

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View
        style={[
          styles.header,
          {
            paddingTop: topPad + 12,
            borderBottomColor: colors.border,
            backgroundColor: colors.background,
          },
        ]}
      >
        <View style={styles.headerTop}>
          <View>
            <Text style={[styles.sessionLabel, { color: colors.mutedForeground }]}>
              Séance en cours
            </Text>
            <Text style={[styles.bodyWeight, { color: colors.foreground }]}>
              {currentWorkout.bodyWeight} kg
            </Text>
          </View>
          <View style={styles.timerBadge}>
            <Feather name="clock" size={14} color={colors.primary} />
            <Text style={[styles.timerText, { color: colors.primary }]}>
              {getElapsed()}
            </Text>
          </View>
        </View>
        {totalSets > 0 && (
          <View style={styles.progressRow}>
            <View style={[styles.progressBar, { backgroundColor: colors.muted }]}>
              <View
                style={[
                  styles.progressFill,
                  {
                    backgroundColor: colors.primary,
                    width: `${(completedCount / totalSets) * 100}%`,
                  },
                ]}
              />
            </View>
            <Text style={[styles.progressLabel, { color: colors.mutedForeground }]}>
              {completedCount}/{totalSets} séries
            </Text>
          </View>
        )}
      </View>

      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: bottomPad }}
        showsVerticalScrollIndicator={false}
      >
        {currentExercises.map(({ we, exercise }) => (
          <ExerciseCard
            key={exercise.id}
            exercise={exercise}
            workoutExercise={we}
            onNavigateHistory={() =>
              router.push({
                pathname: "/exercise-history",
                params: { exerciseId: exercise.id },
              })
            }
          />
        ))}

        <TouchableOpacity
          style={[
            styles.addExerciseBtn,
            { backgroundColor: colors.card, borderColor: colors.border },
          ]}
          onPress={() => router.push("/exercise-picker")}
          activeOpacity={0.8}
        >
          <Feather name="plus-circle" size={20} color={colors.primary} />
          <Text style={[styles.addExerciseBtnText, { color: colors.primary }]}>
            Ajouter un exercice
          </Text>
        </TouchableOpacity>

        {currentExercises.length === 0 && (
          <View style={styles.emptyExercises}>
            <Feather name="dumbbell" size={36} color={colors.mutedForeground} />
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Ajoute des exercices pour commencer
            </Text>
          </View>
        )}
      </ScrollView>

      {/* Finish button */}
      <View
        style={[
          styles.footer,
          {
            paddingBottom: Platform.OS === "web" ? 34 : insets.bottom + 16,
            borderTopColor: colors.border,
            backgroundColor: colors.background,
          },
        ]}
      >
        <TouchableOpacity
          style={[styles.finishBtn, { borderColor: colors.destructive }]}
          onPress={handleFinish}
          activeOpacity={0.85}
        >
          <Feather name="check-square" size={18} color={colors.destructive} />
          <Text style={[styles.finishBtnText, { color: colors.destructive }]}>
            Terminer la séance
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    paddingBottom: 14,
    borderBottomWidth: 1,
  },
  headerTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-end",
    marginBottom: 12,
  },
  sessionLabel: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    marginBottom: 2,
  },
  bodyWeight: {
    fontSize: 24,
    fontFamily: "Inter_700Bold",
  },
  timerBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  timerText: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
  },
  progressRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  progressBar: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    overflow: "hidden",
  },
  progressFill: {
    height: 4,
    borderRadius: 2,
  },
  progressLabel: {
    fontSize: 11,
    fontFamily: "Inter_500Medium",
  },
  addExerciseBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    padding: 16,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: "dashed",
    marginBottom: 12,
  },
  addExerciseBtnText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  emptyExercises: {
    alignItems: "center",
    gap: 12,
    paddingVertical: 40,
  },
  emptyText: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
  },
  footer: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
  },
  finishBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    padding: 14,
    borderRadius: 12,
    borderWidth: 1.5,
  },
  finishBtnText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  emptyTitle: {
    fontSize: 20,
    fontFamily: "Inter_700Bold",
    marginBottom: 10,
  },
  backLink: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
});
