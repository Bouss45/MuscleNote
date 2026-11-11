import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router, useLocalSearchParams } from "expo-router";
import React, { useState } from "react";
import {
  Modal,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { useExerciseName } from "@/hooks/useExerciseName";
import { usePreferences } from "@/context/PreferencesContext";
import { Exercise, SetUpdate, WorkoutExercise, useWorkout } from "@/context/WorkoutContext";
import ExerciseSearchBar, { useExerciseSearch } from "@/components/ExerciseSearchBar";
import MuscleGroupBadge from "@/components/MuscleGroupBadge";
import SetRow from "@/components/SetRow";
import { generateId } from "@/utils/id";
import { parseDecimal } from "@/utils/number";

// ── Exercise picker modal ────────────────────────────────────────────────────
interface PickerProps {
  exercises: Exercise[];
  onPick: (id: string) => void;
  onClose: () => void;
}
function ExercisePickerModal({ exercises, onPick, onClose }: PickerProps) {
  const colors = useColors();
  const { t } = usePreferences();
  const insets = useSafeAreaInsets();
  const exerciseName = useExerciseName();
  const { search, setSearch, group, setGroup, filtered } = useExerciseSearch(exercises);

  return (
    <Modal visible animationType="slide" onRequestClose={onClose}>
      <View style={[pickerStyles.container, { backgroundColor: colors.background, paddingTop: insets.top + 12 }]}>
        <View style={[pickerStyles.header, { borderBottomColor: colors.border }]}>
          <View style={pickerStyles.titleRow}>
            <TouchableOpacity onPress={onClose} style={pickerStyles.closeBtn}>
              <Feather name="x" size={22} color={colors.foreground} />
            </TouchableOpacity>
            <Text style={[pickerStyles.title, { color: colors.foreground }]}>{t("workout.addExercise")}</Text>
          </View>
          <ExerciseSearchBar search={search} onSearch={setSearch} group={group} onGroup={setGroup} />
        </View>

        <ScrollView
          style={pickerStyles.list}
          contentContainerStyle={{ paddingBottom: insets.bottom + 20 }}
          keyboardShouldPersistTaps="handled"
        >
          {filtered.map((ex) => (
            <TouchableOpacity
              key={ex.id}
              style={[pickerStyles.exRow, { borderBottomColor: colors.border }]}
              onPress={() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); onPick(ex.id); }}
            >
              <View style={{ flex: 1 }}>
                <Text style={[pickerStyles.exName, { color: colors.foreground }]}>{exerciseName(ex)}</Text>
                <MuscleGroupBadge muscleGroup={ex.muscleGroup} small />
              </View>
              <Feather name="plus" size={18} color={colors.primary} />
            </TouchableOpacity>
          ))}
          {filtered.length === 0 && (
            <Text style={[pickerStyles.empty, { color: colors.mutedForeground }]}>{t("editWorkout.noExerciseFound")}</Text>
          )}
        </ScrollView>
      </View>
    </Modal>
  );
}

const pickerStyles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 20, paddingBottom: 14, borderBottomWidth: 1, gap: 14 },
  titleRow: { flexDirection: "row", alignItems: "center", gap: 12 },
  closeBtn: { padding: 4 },
  title: { fontSize: 18, fontFamily: "Inter_700Bold" },
  list: { flex: 1 },
  exRow: { flexDirection: "row", alignItems: "center", paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, gap: 12 },
  exName: { fontSize: 15, fontFamily: "Inter_600SemiBold", marginBottom: 4 },
  empty: { textAlign: "center", paddingTop: 40, fontFamily: "Inter_400Regular", fontSize: 14 },
});

// ── Main edit screen ─────────────────────────────────────────────────────────
export default function EditWorkoutScreen() {
  const colors = useColors();
  const { t } = usePreferences();
  const insets = useSafeAreaInsets();
  const { workoutId } = useLocalSearchParams<{ workoutId: string }>();
  const { workouts, exercises, exerciseById, patchWorkout } = useWorkout();
  const exerciseName = useExerciseName();

  const workout = workouts.find((w) => w.id === workoutId);

  const [localExercises, setLocalExercises] = useState<WorkoutExercise[]>(
    () => workout?.exercises ?? []
  );
  const [localBodyWeight, setLocalBodyWeight] = useState(
    workout && workout.bodyWeight > 0 ? String(workout.bodyWeight) : ""
  );
  const [showPicker, setShowPicker] = useState(false);

  const topPad = insets.top;
  const bottomPad = insets.bottom + 20;

  if (!workout) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background, alignItems: "center", justifyContent: "center" }]}>
        <Text style={[styles.empty, { color: colors.mutedForeground }]}>{t("editWorkout.notFound")}</Text>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={[styles.backLink, { color: colors.primary }]}>{t("common.back")}</Text>
        </TouchableOpacity>
      </View>
    );
  }

  function addExercise(exerciseId: string) {
    setShowPicker(false);
    setLocalExercises((prev) => {
      if (prev.find((e) => e.exerciseId === exerciseId)) return prev;
      return [...prev, { exerciseId, sets: [{ id: generateId(), reps: 10, weight: 20, completed: true }] }];
    });
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
  }

  function removeExercise(exerciseId: string) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setLocalExercises((prev) => prev.filter((e) => e.exerciseId !== exerciseId));
  }

  function addSet(exerciseId: string, isCardio = false) {
    setLocalExercises((prev) =>
      prev.map((e) => {
        if (e.exerciseId !== exerciseId) return e;
        const last = e.sets[e.sets.length - 1];
        const newSet = isCardio
          ? { id: generateId(), reps: 0, weight: 0, completed: true, duration: last?.duration ?? 30, distance: last?.distance ?? 0, speed: last?.speed ?? 0, incline: last?.incline ?? 0 }
          : { id: generateId(), reps: last?.reps ?? 10, weight: last?.weight ?? 20, completed: true };
        return { ...e, sets: [...e.sets, newSet] };
      })
    );
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }

  function updateSet(exerciseId: string, setId: string, data: SetUpdate) {
    setLocalExercises((prev) =>
      prev.map((e) => {
        if (e.exerciseId !== exerciseId) return e;
        return { ...e, sets: e.sets.map((s) => s.id === setId ? { ...s, ...data } : s) };
      })
    );
  }

  function removeSet(exerciseId: string, setId: string) {
    setLocalExercises((prev) =>
      prev.map((e) => {
        if (e.exerciseId !== exerciseId) return e;
        return { ...e, sets: e.sets.filter((s) => s.id !== setId) };
      })
    );
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }

  function save() {
    const bw = parseDecimal(localBodyWeight);
    patchWorkout(workoutId, {
      exercises: localExercises,
      bodyWeight: bw > 0 ? bw : workout?.bodyWeight ?? 0,
    });
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    router.back();
  }

  const usedIds = new Set(localExercises.map((e) => e.exerciseId));
  const pickable = exercises.filter((e) => !e.archived && !usedIds.has(e.id));

  return (
    <>
      <View style={[styles.container, { backgroundColor: colors.background }]}>
        {/* Header */}
        <View style={[styles.header, { paddingTop: topPad + 12, borderBottomColor: colors.border, backgroundColor: colors.background }]}>
          <View style={styles.headerRow}>
            <TouchableOpacity onPress={() => router.back()} style={styles.backBtn} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
              <Feather name="chevron-left" size={22} color={colors.foreground} />
            </TouchableOpacity>
            <View style={{ flex: 1 }}>
              <Text style={[styles.headerLabel, { color: colors.mutedForeground }]}>{t("editWorkout.headerLabel")}</Text>
              <Text style={[styles.headerTitle, { color: colors.foreground }]} numberOfLines={1}>{workout.name}</Text>
            </View>
            <TouchableOpacity style={[styles.saveBtn, { backgroundColor: colors.primary }]} onPress={save}>
              <Feather name="check" size={16} color={colors.primaryForeground} />
              <Text style={[styles.saveBtnText, { color: colors.primaryForeground }]}>{t("common.save")}</Text>
            </TouchableOpacity>
          </View>
        </View>

        <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: bottomPad }} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">

          {/* Body weight card */}
          <View style={[styles.bwCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Feather name="user" size={15} color={colors.mutedForeground} />
            <Text style={[styles.bwLabel, { color: colors.mutedForeground }]}>{t("common.bodyWeight")}</Text>
            <View style={[styles.bwInputWrap, { backgroundColor: colors.input, borderColor: colors.border }]}>
              <TextInput
                style={[styles.bwInput, { color: colors.foreground }]}
                value={localBodyWeight}
                onChangeText={setLocalBodyWeight}
                keyboardType="decimal-pad"
                maxLength={6}
                placeholder={workout.bodyWeight > 0 ? String(workout.bodyWeight) : "—"}
                placeholderTextColor={colors.mutedForeground}
                selectTextOnFocus
              />
              <Text style={[styles.bwUnit, { color: colors.mutedForeground }]}>kg</Text>
            </View>
          </View>

          {localExercises.map((we) => {
            const ex = exerciseById.get(we.exerciseId);
            if (!ex) return null;
            return (
              <View key={we.exerciseId} style={[styles.exCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                {/* Exercise header */}
                <View style={styles.exHeader}>
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.exName, { color: colors.foreground }]}>{exerciseName(ex)}</Text>
                    <MuscleGroupBadge muscleGroup={ex.muscleGroup} small />
                  </View>
                  <TouchableOpacity onPress={() => removeExercise(ex.id)} style={styles.removeBtn}>
                    <Feather name="x" size={16} color={colors.mutedForeground} />
                  </TouchableOpacity>
                </View>

                {/* Sets */}
                <View style={styles.setsList}>
                  {we.sets.map((set, idx) => (
                    <SetRow
                      key={set.id}
                      set={set}
                      index={idx}
                      isCardio={ex.muscleGroup === "cardio"}
                      onUpdate={(data) => updateSet(ex.id, set.id, data)}
                      onRemove={() => removeSet(ex.id, set.id)}
                    />
                  ))}
                </View>

                {/* Add set button */}
                <TouchableOpacity
                  style={[styles.addSetBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}
                  onPress={() => addSet(ex.id, ex.muscleGroup === "cardio")}
                >
                  <Feather name="plus" size={15} color={colors.primary} />
                  <Text style={[styles.addSetText, { color: colors.primary }]}>{t("exerciseCard.addSet")}</Text>
                </TouchableOpacity>
              </View>
            );
          })}

          {/* Add exercise button */}
          <TouchableOpacity
            style={[styles.addExBtn, { backgroundColor: colors.card, borderColor: colors.border }]}
            onPress={() => setShowPicker(true)}
            activeOpacity={0.8}
          >
            <Feather name="plus-circle" size={20} color={colors.primary} />
            <Text style={[styles.addExText, { color: colors.primary }]}>{t("workout.addExercise")}</Text>
          </TouchableOpacity>

          {localExercises.length === 0 && (
            <View style={styles.emptyState}>
              <Feather name="activity" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                {t("editWorkout.emptyExercises")}
              </Text>
            </View>
          )}
        </ScrollView>
      </View>

      {showPicker && (
        <ExercisePickerModal
          exercises={pickable}
          onPick={addExercise}
          onClose={() => setShowPicker(false)}
        />
      )}
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 20, paddingBottom: 14, borderBottomWidth: 1 },
  headerRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  backBtn: { width: 34, height: 34, alignItems: "center", justifyContent: "center" },
  headerLabel: { fontSize: 11, fontFamily: "Inter_400Regular", marginBottom: 1 },
  headerTitle: { fontSize: 18, fontFamily: "Inter_700Bold" },
  saveBtn: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, paddingVertical: 9, borderRadius: 10 },
  saveBtnText: { fontSize: 14, fontFamily: "Inter_700Bold" },
  exCard: { borderRadius: 14, borderWidth: 1, marginBottom: 12, overflow: "hidden" },
  exHeader: { flexDirection: "row", alignItems: "center", padding: 14, gap: 10 },
  exName: { fontSize: 16, fontFamily: "Inter_600SemiBold", marginBottom: 4 },
  removeBtn: { padding: 8 },
  setsList: { paddingHorizontal: 14 },
  addSetBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6, margin: 14, marginTop: 4, paddingVertical: 10, borderRadius: 10, borderWidth: 1, borderStyle: "dashed" },
  addSetText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  addExBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 10, padding: 16, borderRadius: 14, borderWidth: 1.5, borderStyle: "dashed", marginBottom: 12 },
  addExText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  emptyState: { alignItems: "center", gap: 12, paddingVertical: 40, paddingHorizontal: 30 },
  emptyText: { fontSize: 14, fontFamily: "Inter_400Regular", textAlign: "center", lineHeight: 22 },
  empty: { fontSize: 16, fontFamily: "Inter_600SemiBold", marginBottom: 10 },
  backLink: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  bwCard: {
    flexDirection: "row", alignItems: "center", gap: 8,
    paddingHorizontal: 14, paddingVertical: 10,
    borderRadius: 12, borderWidth: 1, marginBottom: 14,
  },
  bwLabel: { flex: 1, fontSize: 13, fontFamily: "Inter_600SemiBold" },
  bwInputWrap: {
    flexDirection: "row", alignItems: "center", gap: 4,
    borderRadius: 8, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 6,
  },
  bwInput: {
    minWidth: 48, fontSize: 15, fontFamily: "Inter_700Bold",
    textAlign: "right", includeFontPadding: false,
  },
  bwUnit: { fontSize: 12, fontFamily: "Inter_400Regular" },
});
