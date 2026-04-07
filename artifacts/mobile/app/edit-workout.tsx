import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router, useLocalSearchParams } from "expo-router";
import React, { useMemo, useState } from "react";
import {
  Modal,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { Exercise, MuscleGroup, Set, WorkoutExercise, useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge from "@/components/MuscleGroupBadge";
import { MUSCLE_GROUP_LABELS } from "@/components/MuscleGroupBadge";

function generateId() {
  return Date.now().toString() + Math.random().toString(36).substr(2, 9);
}

const MUSCLE_GROUPS: MuscleGroup[] = [
  "dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio",
];

// ── Inline set editor ────────────────────────────────────────────────────────
interface SetEditorProps {
  set: Set;
  index: number;
  onChange: (reps: number, weight: number) => void;
  onRemove: () => void;
}
function SetEditor({ set, index, onChange, onRemove }: SetEditorProps) {
  const colors = useColors();
  const [reps, setReps] = useState(String(set.reps));
  const [weight, setWeight] = useState(String(set.weight));

  function commit() {
    const r = parseInt(reps) || 0;
    const w = parseFloat(weight) || 0;
    onChange(r, w);
  }

  return (
    <View style={[setStyles.row, { backgroundColor: colors.background, borderColor: colors.border }]}>
      <Text style={[setStyles.idx, { color: colors.mutedForeground }]}>{index + 1}</Text>
      <View style={setStyles.inputs}>
        <View style={setStyles.group}>
          <TextInput
            style={[setStyles.input, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
            value={reps}
            onChangeText={setReps}
            onBlur={commit}
            keyboardType="number-pad"
            maxLength={3}
            selectTextOnFocus
          />
          <Text style={[setStyles.unit, { color: colors.mutedForeground }]}>reps</Text>
        </View>
        <Text style={[setStyles.x, { color: colors.mutedForeground }]}>×</Text>
        <View style={setStyles.group}>
          <TextInput
            style={[setStyles.input, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
            value={weight}
            onChangeText={setWeight}
            onBlur={commit}
            keyboardType="decimal-pad"
            maxLength={6}
            selectTextOnFocus
          />
          <Text style={[setStyles.unit, { color: colors.mutedForeground }]}>kg</Text>
        </View>
      </View>
      <TouchableOpacity onPress={onRemove} style={setStyles.del}>
        <Feather name="trash-2" size={14} color={colors.destructive} />
      </TouchableOpacity>
    </View>
  );
}

const setStyles = StyleSheet.create({
  row: {
    flexDirection: "row", alignItems: "center",
    paddingHorizontal: 12, paddingVertical: 8,
    borderRadius: 10, borderWidth: 1, marginBottom: 8, gap: 8,
  },
  idx: { width: 20, fontSize: 13, fontFamily: "Inter_600SemiBold", textAlign: "center" },
  inputs: { flex: 1, flexDirection: "row", alignItems: "center", gap: 6 },
  group: { flexDirection: "row", alignItems: "center", gap: 4 },
  input: {
    width: 52, height: 36, borderRadius: 8, borderWidth: 1,
    textAlign: "center", fontSize: 15, fontFamily: "Inter_600SemiBold",
  },
  unit: { fontSize: 11, fontFamily: "Inter_400Regular" },
  x: { fontSize: 14, fontFamily: "Inter_400Regular" },
  del: { padding: 6 },
});

// ── Exercise picker modal ────────────────────────────────────────────────────
interface PickerProps {
  exercises: Exercise[];
  usedIds: string[];
  onPick: (id: string) => void;
  onClose: () => void;
}
function ExercisePicker({ exercises, usedIds, onPick, onClose }: PickerProps) {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const [search, setSearch] = useState("");
  const [filterGroup, setFilterGroup] = useState<MuscleGroup | null>(null);

  const filtered = exercises.filter((e) => {
    if (usedIds.includes(e.id)) return false;
    if (filterGroup && e.muscleGroup !== filterGroup) return false;
    if (search && !e.name.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  return (
    <Modal visible animationType="slide" onRequestClose={onClose}>
      <View style={[pickerStyles.container, { backgroundColor: colors.background, paddingTop: Platform.OS === "web" ? 67 : insets.top + 12 }]}>
        <View style={[pickerStyles.header, { borderBottomColor: colors.border }]}>
          <TouchableOpacity onPress={onClose} style={pickerStyles.closeBtn}>
            <Feather name="x" size={22} color={colors.foreground} />
          </TouchableOpacity>
          <Text style={[pickerStyles.title, { color: colors.foreground }]}>Ajouter un exercice</Text>
        </View>

        <View style={[pickerStyles.searchRow, { borderBottomColor: colors.border }]}>
          <Feather name="search" size={16} color={colors.mutedForeground} />
          <TextInput
            style={[pickerStyles.searchInput, { color: colors.foreground }]}
            value={search}
            onChangeText={setSearch}
            placeholder="Rechercher..."
            placeholderTextColor={colors.mutedForeground}
          />
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={pickerStyles.groupScroll} contentContainerStyle={pickerStyles.groupRow}>
          <TouchableOpacity
            style={[pickerStyles.groupChip, { backgroundColor: !filterGroup ? colors.primary : colors.card, borderColor: !filterGroup ? colors.primary : colors.border }]}
            onPress={() => setFilterGroup(null)}
          >
            <Text style={[pickerStyles.groupChipText, { color: !filterGroup ? "#fff" : colors.mutedForeground }]}>Tous</Text>
          </TouchableOpacity>
          {MUSCLE_GROUPS.map((g) => (
            <TouchableOpacity
              key={g}
              style={[pickerStyles.groupChip, { backgroundColor: filterGroup === g ? colors.primary : colors.card, borderColor: filterGroup === g ? colors.primary : colors.border }]}
              onPress={() => setFilterGroup(filterGroup === g ? null : g)}
            >
              <Text style={[pickerStyles.groupChipText, { color: filterGroup === g ? "#fff" : colors.mutedForeground }]}>
                {MUSCLE_GROUP_LABELS[g]}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>

        <ScrollView style={pickerStyles.list} contentContainerStyle={{ paddingBottom: insets.bottom + 20 }}>
          {filtered.map((ex) => (
            <TouchableOpacity
              key={ex.id}
              style={[pickerStyles.exRow, { borderBottomColor: colors.border }]}
              onPress={() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); onPick(ex.id); }}
            >
              <View style={{ flex: 1 }}>
                <Text style={[pickerStyles.exName, { color: colors.foreground }]}>{ex.name}</Text>
                <MuscleGroupBadge muscleGroup={ex.muscleGroup} small />
              </View>
              <Feather name="plus" size={18} color={colors.primary} />
            </TouchableOpacity>
          ))}
          {filtered.length === 0 && (
            <Text style={[pickerStyles.empty, { color: colors.mutedForeground }]}>Aucun exercice trouvé</Text>
          )}
        </ScrollView>
      </View>
    </Modal>
  );
}

const pickerStyles = StyleSheet.create({
  container: { flex: 1 },
  header: { flexDirection: "row", alignItems: "center", gap: 12, paddingHorizontal: 20, paddingBottom: 14, borderBottomWidth: 1 },
  closeBtn: { padding: 4 },
  title: { fontSize: 18, fontFamily: "Inter_700Bold" },
  searchRow: { flexDirection: "row", alignItems: "center", gap: 10, paddingHorizontal: 20, paddingVertical: 12, borderBottomWidth: 1 },
  searchInput: { flex: 1, fontSize: 15, fontFamily: "Inter_400Regular" },
  groupScroll: { maxHeight: 50 },
  groupRow: { paddingHorizontal: 20, gap: 8, paddingVertical: 8, flexDirection: "row" },
  groupChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, borderWidth: 1 },
  groupChipText: { fontSize: 12, fontFamily: "Inter_600SemiBold" },
  list: { flex: 1 },
  exRow: { flexDirection: "row", alignItems: "center", paddingHorizontal: 20, paddingVertical: 14, borderBottomWidth: 1, gap: 12 },
  exName: { fontSize: 15, fontFamily: "Inter_600SemiBold", marginBottom: 4 },
  empty: { textAlign: "center", paddingTop: 40, fontFamily: "Inter_400Regular", fontSize: 14 },
});

// ── Main edit screen ─────────────────────────────────────────────────────────
export default function EditWorkoutScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { workoutId } = useLocalSearchParams<{ workoutId: string }>();
  const { workouts, exercises, patchWorkout } = useWorkout();

  const workout = useMemo(() => workouts.find((w) => w.id === workoutId), [workouts, workoutId]);

  const [localExercises, setLocalExercises] = useState<WorkoutExercise[]>(
    () => workout?.exercises ?? []
  );
  const [showPicker, setShowPicker] = useState(false);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom + 20;

  if (!workout) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background, alignItems: "center", justifyContent: "center" }]}>
        <Text style={[styles.empty, { color: colors.mutedForeground }]}>Séance introuvable</Text>
        <TouchableOpacity onPress={() => router.back()}>
          <Text style={[styles.backLink, { color: colors.primary }]}>Retour</Text>
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

  function addSet(exerciseId: string) {
    setLocalExercises((prev) =>
      prev.map((e) => {
        if (e.exerciseId !== exerciseId) return e;
        const last = e.sets[e.sets.length - 1];
        return {
          ...e,
          sets: [...e.sets, { id: generateId(), reps: last?.reps ?? 10, weight: last?.weight ?? 20, completed: true }],
        };
      })
    );
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }

  function updateSet(exerciseId: string, setId: string, reps: number, weight: number) {
    setLocalExercises((prev) =>
      prev.map((e) => {
        if (e.exerciseId !== exerciseId) return e;
        return { ...e, sets: e.sets.map((s) => s.id === setId ? { ...s, reps, weight } : s) };
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
    patchWorkout(workoutId, { exercises: localExercises });
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    router.back();
  }

  const usedIds = localExercises.map((e) => e.exerciseId);

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
              <Text style={[styles.headerLabel, { color: colors.mutedForeground }]}>Modifier la séance</Text>
              <Text style={[styles.headerTitle, { color: colors.foreground }]} numberOfLines={1}>{workout.name}</Text>
            </View>
            <TouchableOpacity style={[styles.saveBtn, { backgroundColor: colors.primary }]} onPress={save}>
              <Feather name="check" size={16} color={colors.primaryForeground} />
              <Text style={[styles.saveBtnText, { color: colors.primaryForeground }]}>Enregistrer</Text>
            </TouchableOpacity>
          </View>
        </View>

        <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: bottomPad }} showsVerticalScrollIndicator={false}>
          {localExercises.map((we) => {
            const ex = exercises.find((e) => e.id === we.exerciseId);
            if (!ex) return null;
            return (
              <View key={we.exerciseId} style={[styles.exCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                {/* Exercise header */}
                <View style={styles.exHeader}>
                  <View style={{ flex: 1 }}>
                    <Text style={[styles.exName, { color: colors.foreground }]}>{ex.name}</Text>
                    <MuscleGroupBadge muscleGroup={ex.muscleGroup} small />
                  </View>
                  <TouchableOpacity onPress={() => removeExercise(ex.id)} style={styles.removeBtn}>
                    <Feather name="x" size={16} color={colors.mutedForeground} />
                  </TouchableOpacity>
                </View>

                {/* Sets */}
                <View style={styles.setsList}>
                  {we.sets.map((set, idx) => (
                    <SetEditor
                      key={set.id}
                      set={set}
                      index={idx}
                      onChange={(r, w) => updateSet(ex.id, set.id, r, w)}
                      onRemove={() => removeSet(ex.id, set.id)}
                    />
                  ))}
                </View>

                {/* Add set button */}
                <TouchableOpacity
                  style={[styles.addSetBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}
                  onPress={() => addSet(ex.id)}
                >
                  <Feather name="plus" size={15} color={colors.primary} />
                  <Text style={[styles.addSetText, { color: colors.primary }]}>Ajouter une série</Text>
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
            <Text style={[styles.addExText, { color: colors.primary }]}>Ajouter un exercice</Text>
          </TouchableOpacity>

          {localExercises.length === 0 && (
            <View style={styles.emptyState}>
              <Feather name="activity" size={36} color={colors.mutedForeground} />
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                Aucun exercice — appuie sur le bouton ci-dessus pour en ajouter
              </Text>
            </View>
          )}
        </ScrollView>
      </View>

      {showPicker && (
        <ExercisePicker
          exercises={exercises}
          usedIds={usedIds}
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
});
