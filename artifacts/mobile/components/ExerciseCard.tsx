import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useEffect, useRef, useState } from "react";
import {
  AppState,
  AppStateStatus,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useColors } from "@/hooks/useColors";
import { Exercise, WorkoutExercise } from "@/context/WorkoutContext";
import { useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge from "./MuscleGroupBadge";
import SetRow from "./SetRow";

const REST_SECONDS = 180; // 3 minutes

interface Props {
  exercise: Exercise;
  workoutExercise: WorkoutExercise;
  onNavigateHistory: () => void;
}

export default function ExerciseCard({ exercise, workoutExercise, onNavigateHistory }: Props) {
  const colors = useColors();
  const { addSet, updateSet, removeSet, toggleSetComplete, removeExerciseFromWorkout, updateExerciseNote } = useWorkout();
  const [collapsed, setCollapsed] = useState(false);

  // Note
  const [showNote, setShowNote] = useState(!!workoutExercise.note);
  const [noteText, setNoteText] = useState(workoutExercise.note ?? "");

  // Rest timer — timestamp-based so it survives screen lock / background
  const [restLeft, setRestLeft] = useState<number | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const restEndRef = useRef<number | null>(null); // absolute timestamp when rest ends

  // Recalculate remaining time from the stored end timestamp
  function tick() {
    if (restEndRef.current === null) return;
    const left = Math.round((restEndRef.current - Date.now()) / 1000);
    if (left <= 0) {
      clearInterval(timerRef.current!);
      timerRef.current = null;
      restEndRef.current = null;
      setRestLeft(null);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } else {
      setRestLeft(left);
    }
  }

  // When app comes back to foreground, immediately recalculate
  useEffect(() => {
    const sub = AppState.addEventListener("change", (state: AppStateStatus) => {
      if (state === "active" && restEndRef.current !== null) {
        tick();
      }
    });
    return () => {
      sub.remove();
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  function startRestTimer() {
    if (timerRef.current) clearInterval(timerRef.current);
    restEndRef.current = Date.now() + REST_SECONDS * 1000;
    setRestLeft(REST_SECONDS);
    timerRef.current = setInterval(tick, 1000);
  }

  function stopRestTimer() {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    restEndRef.current = null;
    setRestLeft(null);
  }

  function formatRest(s: number) {
    return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, "0")}`;
  }

  function getTimerColor() {
    if (restLeft === null) return colors.primary;
    const pct = restLeft / REST_SECONDS;
    if (pct > 0.5) return "#16A34A";
    if (pct > 0.25) return "#f59e0b";
    return "#ef4444";
  }

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
    updateExerciseNote(exercise.id, noteText);
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
                onChangeText={setNoteText}
                onBlur={handleNoteBlur}
                placeholder="Ajouter une note…"
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
              onSetComplete={startRestTimer}
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

          {/* Rest timer */}
          {restLeft !== null && (
            <View style={[styles.timerBanner, { backgroundColor: getTimerColor() + "22", borderColor: getTimerColor() + "55" }]}>
              <View style={styles.timerLeft}>
                <Feather name="clock" size={13} color={getTimerColor()} />
                <Text style={[styles.timerLabel, { color: getTimerColor() }]}>Repos</Text>
                <Text style={[styles.timerCount, { color: getTimerColor() }]}>
                  {formatRest(restLeft)}
                </Text>
              </View>
              <View style={[styles.timerProgress, { backgroundColor: colors.border }]}>
                <View style={[styles.timerFill, { backgroundColor: getTimerColor(), width: `${(restLeft / REST_SECONDS) * 100}%` as any }]} />
              </View>
              <TouchableOpacity onPress={stopRestTimer} style={styles.timerSkip}>
                <Feather name="skip-forward" size={13} color={getTimerColor()} />
                <Text style={[styles.timerSkipText, { color: getTimerColor() }]}>Passer</Text>
              </TouchableOpacity>
            </View>
          )}
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
  // Rest timer
  timerBanner: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    marginTop: 10,
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  timerLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  timerLabel: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
  },
  timerCount: {
    fontSize: 15,
    fontFamily: "Inter_700Bold",
  },
  timerProgress: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    overflow: "hidden",
  },
  timerFill: {
    height: 4,
    borderRadius: 2,
  },
  timerSkip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 3,
  },
  timerSkipText: {
    fontSize: 11,
    fontFamily: "Inter_600SemiBold",
  },
});
