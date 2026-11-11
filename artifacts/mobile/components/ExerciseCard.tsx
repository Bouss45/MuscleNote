import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useState } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useColors } from "@/hooks/useColors";
import { useExerciseName } from "@/hooks/useExerciseName";
import { usePreferences } from "@/context/PreferencesContext";
import { useRestTimer } from "@/context/RestTimerContext";
import { Exercise, WorkoutExercise, useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge from "./MuscleGroupBadge";
import SetRow from "./SetRow";

interface Props {
  exercise: Exercise;
  workoutExercise: WorkoutExercise;
  onNavigateHistory: () => void;
}

export default function ExerciseCard({ exercise, workoutExercise, onNavigateHistory }: Props) {
  const colors = useColors();
  const { t } = usePreferences();
  const exerciseName = useExerciseName();
  const restTimer = useRestTimer();
  const { addSet, updateSet, removeSet, toggleSetComplete, removeExerciseFromWorkout, updateExerciseNote } = useWorkout();
  const [collapsed, setCollapsed] = useState(false);

  // Note
  const [showNote, setShowNote] = useState(!!workoutExercise.note);
  const [noteText, setNoteText] = useState(workoutExercise.note ?? "");

  const isCardio = exercise.muscleGroup === "cardio";

  function handleAddSet() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const lastSet = workoutExercise.sets[workoutExercise.sets.length - 1];
    if (isCardio) {
      addSet(exercise.id, 0, 0, {
        duration: lastSet?.duration ?? 30,
        distance: lastSet?.distance ?? 0,
        speed: lastSet?.speed ?? 0,
        incline: lastSet?.incline ?? 0,
      });
    } else {
      addSet(exercise.id, lastSet?.reps ?? 10, lastSet?.weight ?? 20);
    }
  }

  function handleNoteBlur() {
    if (!noteText.trim()) setShowNote(false);
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
            {exerciseName(exercise)}
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
          <TouchableOpacity
            onPress={() => {
              setShowNote(true);
              setCollapsed(false);
            }}
            style={styles.iconBtn}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="edit-3" size={15} color={noteText.trim() ? colors.primary : colors.mutedForeground} />
          </TouchableOpacity>
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
          {/* Note input */}
          {showNote && (
            <View style={[styles.noteRow, { backgroundColor: colors.input, borderColor: colors.border }]}>
              <Feather name="edit-3" size={13} color={colors.mutedForeground} style={{ marginTop: 2 }} />
              <TextInput
                style={[styles.noteInput, { color: colors.foreground }]}
                value={noteText}
                onChangeText={(v) => {
                  setNoteText(v);
                  updateExerciseNote(exercise.id, v);
                }}
                onBlur={handleNoteBlur}
                placeholder={t("exerciseCard.notePlaceholder")}
                placeholderTextColor={colors.mutedForeground}
                multiline
                autoFocus={!noteText}
                returnKeyType="done"
                blurOnSubmit
              />
              {noteText.trim() ? null : (
                <TouchableOpacity onPress={() => { setShowNote(false); }}>
                  <Feather name="x" size={14} color={colors.mutedForeground} />
                </TouchableOpacity>
              )}
            </View>
          )}
          {!showNote && noteText.trim() !== "" && (
            <TouchableOpacity onPress={() => setShowNote(true)} style={[styles.notePreview, { borderColor: colors.border }]}>
              <Feather name="edit-3" size={11} color={colors.mutedForeground} />
              <Text style={[styles.notePreviewText, { color: colors.mutedForeground }]} numberOfLines={1}>
                {noteText}
              </Text>
            </TouchableOpacity>
          )}

          {workoutExercise.sets.map((set, idx) => (
            <SetRow
              key={set.id}
              set={set}
              index={idx}
              isCardio={isCardio}
              onToggle={() => toggleSetComplete(exercise.id, set.id)}
              onUpdate={(data) => updateSet(exercise.id, set.id, data)}
              onRemove={() => removeSet(exercise.id, set.id)}
              onSetComplete={() => restTimer.start(exerciseName(exercise))}
            />
          ))}

          <TouchableOpacity
            style={[styles.addSetBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}
            onPress={handleAddSet}
          >
            <Feather name="plus" size={16} color={colors.primary} />
            <Text style={[styles.addSetText, { color: colors.primary }]}>
              {t("exerciseCard.addSet")}
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
  // Note
  noteRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 10,
    paddingVertical: 8,
    marginBottom: 10,
  },
  noteInput: {
    flex: 1,
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    maxHeight: 80,
  },
  notePreview: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingVertical: 6,
    paddingHorizontal: 2,
    borderBottomWidth: 1,
    marginBottom: 8,
  },
  notePreviewText: {
    flex: 1,
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    fontStyle: "italic",
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
