import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useState } from "react";
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useColors } from "@/hooks/useColors";
import { Exercise, WorkoutExercise } from "@/context/WorkoutContext";
import { useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge from "./MuscleGroupBadge";
import SetRow from "./SetRow";

interface Props {
  exercise: Exercise;
  workoutExercise: WorkoutExercise;
  onNavigateHistory: () => void;
}

export default function ExerciseCard({ exercise, workoutExercise, onNavigateHistory }: Props) {
  const colors = useColors();
  const { addSet, updateSet, removeSet, toggleSetComplete, removeExerciseFromWorkout } = useWorkout();
  const [collapsed, setCollapsed] = useState(false);

  function handleAddSet() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const lastSet = workoutExercise.sets[workoutExercise.sets.length - 1];
    addSet(exercise.id, lastSet?.reps ?? 10, lastSet?.weight ?? 20);
  }

  const completedSets = workoutExercise.sets.filter((s) => s.completed).length;
  const totalSets = workoutExercise.sets.length;

  return (
    <View style={[styles.card, { backgroundColor: colors.card, borderColor: colors.border }]}>
      <TouchableOpacity
        style={styles.header}
        onPress={() => setCollapsed(!collapsed)}
        activeOpacity={0.8}
      >
        <View style={styles.headerLeft}>
          <Text style={[styles.name, { color: colors.foreground }]}>
            {exercise.name}
          </Text>
          <View style={styles.badges}>
            <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
            {totalSets > 0 && (
              <View style={[styles.progressBadge, { backgroundColor: completedSets === totalSets ? colors.success + "22" : colors.muted }]}>
                <Text style={[styles.progressText, { color: completedSets === totalSets ? colors.success : colors.mutedForeground }]}>
                  {completedSets}/{totalSets}
                </Text>
              </View>
            )}
          </View>
        </View>
        <View style={styles.headerRight}>
          <TouchableOpacity onPress={onNavigateHistory} style={styles.iconBtn}>
            <Feather name="trending-up" size={16} color={colors.primary} />
          </TouchableOpacity>
          <TouchableOpacity
            onPress={() => {
              Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
              removeExerciseFromWorkout(exercise.id);
            }}
            style={styles.iconBtn}
          >
            <Feather name="x" size={16} color={colors.mutedForeground} />
          </TouchableOpacity>
        </View>
      </TouchableOpacity>

      {!collapsed && (
        <View style={styles.body}>
          {workoutExercise.sets.map((set, idx) => (
            <SetRow
              key={set.id}
              set={set}
              index={idx}
              onToggle={() => toggleSetComplete(exercise.id, set.id)}
              onUpdate={(reps, weight) => updateSet(exercise.id, set.id, reps, weight)}
              onRemove={() => removeSet(exercise.id, set.id)}
            />
          ))}

          <TouchableOpacity
            style={[styles.addSetBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}
            onPress={handleAddSet}
          >
            <Feather name="plus" size={16} color={colors.primary} />
            <Text style={[styles.addSetText, { color: colors.primary }]}>
              Ajouter une série
            </Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 12,
    overflow: "hidden",
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
  },
  headerLeft: {
    flex: 1,
    gap: 6,
  },
  name: {
    fontSize: 16,
    fontFamily: "Inter_600SemiBold",
  },
  badges: {
    flexDirection: "row",
    gap: 6,
    alignItems: "center",
  },
  progressBadge: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 10,
  },
  progressText: {
    fontSize: 11,
    fontFamily: "Inter_600SemiBold",
  },
  headerRight: {
    flexDirection: "row",
    gap: 4,
  },
  iconBtn: {
    padding: 8,
  },
  body: {
    paddingHorizontal: 14,
    paddingBottom: 14,
  },
  addSetBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    borderStyle: "dashed",
  },
  addSetText: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
  },
});
