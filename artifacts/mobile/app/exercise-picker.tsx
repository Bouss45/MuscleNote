import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useState } from "react";
import {
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
import { Exercise, MuscleGroup, useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge, { MUSCLE_GROUP_LABELS } from "@/components/MuscleGroupBadge";

const ALL_GROUPS: MuscleGroup[] = ["dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio", "avant-bras"];

export default function ExercisePickerScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const {
    exercises,
    addExerciseToWorkout,
    currentWorkout,
    addCustomExercise,
    updateCustomExercise,
    deleteCustomExercise,
    isCustomExercise,
  } = useWorkout();

  const [search, setSearch] = useState("");
  const [selectedGroup, setSelectedGroup] = useState<MuscleGroup | null>(null);

  // Add custom form
  const [showAddCustom, setShowAddCustom] = useState(false);
  const [customName, setCustomName] = useState("");
  const [customGroup, setCustomGroup] = useState<MuscleGroup>("dos");

  // Edit custom form
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editGroup, setEditGroup] = useState<MuscleGroup>("dos");

  // Delete confirm
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom + 20;

  const addedIds = new Set(currentWorkout?.exercises.map((e) => e.exerciseId) ?? []);

  const filtered = exercises.filter((e) => {
    const matchGroup = selectedGroup ? e.muscleGroup === selectedGroup : true;
    const matchSearch = e.name.toLowerCase().includes(search.toLowerCase());
    return matchGroup && matchSearch;
  });

  function handleAdd(exerciseId: string) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    addExerciseToWorkout(exerciseId);
  }

  async function handleAddCustom() {
    if (!customName.trim()) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    await addCustomExercise(customName.trim(), customGroup);
    setCustomName("");
    setShowAddCustom(false);
  }

  function openEdit(ex: Exercise) {
    setEditingId(ex.id);
    setEditName(ex.name);
    setEditGroup(ex.muscleGroup);
    setDeletingId(null);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }

  function handleSaveEdit() {
    if (!editingId || !editName.trim()) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    updateCustomExercise(editingId, editName.trim(), editGroup);
    setEditingId(null);
  }

  function handleDelete(id: string) {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    deleteCustomExercise(id);
    setDeletingId(null);
    setEditingId(null);
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: topPad + 12, borderBottomColor: colors.border }]}>
        <View style={styles.headerTop}>
          <TouchableOpacity onPress={() => router.back()}>
            <Feather name="x" size={22} color={colors.foreground} />
          </TouchableOpacity>
          <Text style={[styles.title, { color: colors.foreground }]}>Exercices</Text>
          <TouchableOpacity onPress={() => { setShowAddCustom(!showAddCustom); setEditingId(null); }}>
            <Feather name="plus" size={22} color={colors.primary} />
          </TouchableOpacity>
        </View>

        <View style={[styles.searchBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Feather name="search" size={16} color={colors.mutedForeground} />
          <TextInput
            style={[styles.searchInput, { color: colors.foreground }]}
            placeholder="Chercher un exercice..."
            placeholderTextColor={colors.mutedForeground}
            value={search}
            onChangeText={setSearch}
          />
          {search.length > 0 && (
            <TouchableOpacity onPress={() => setSearch("")}>
              <Feather name="x" size={14} color={colors.mutedForeground} />
            </TouchableOpacity>
          )}
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.groupFilter}>
          <TouchableOpacity
            style={[styles.groupChip, { backgroundColor: selectedGroup === null ? colors.primary : colors.card, borderColor: selectedGroup === null ? colors.primary : colors.border }]}
            onPress={() => setSelectedGroup(null)}
          >
            <Text style={[styles.groupChipText, { color: selectedGroup === null ? colors.primaryForeground : colors.foreground }]}>Tous</Text>
          </TouchableOpacity>
          {ALL_GROUPS.map((g) => (
            <TouchableOpacity
              key={g}
              style={[styles.groupChip, { backgroundColor: selectedGroup === g ? colors.primary : colors.card, borderColor: selectedGroup === g ? colors.primary : colors.border }]}
              onPress={() => setSelectedGroup(selectedGroup === g ? null : g)}
            >
              <Text style={[styles.groupChipText, { color: selectedGroup === g ? colors.primaryForeground : colors.foreground }]}>
                {MUSCLE_GROUP_LABELS[g]}
              </Text>
            </TouchableOpacity>
          ))}
        </ScrollView>
      </View>

      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: bottomPad }}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* Add custom form */}
        {showAddCustom && (
          <View style={[styles.customBox, { backgroundColor: colors.card, borderColor: colors.primary + "55" }]}>
            <Text style={[styles.customTitle, { color: colors.foreground }]}>Nouvel exercice</Text>
            <TextInput
              style={[styles.customInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
              placeholder="Nom de l'exercice"
              placeholderTextColor={colors.mutedForeground}
              value={customName}
              onChangeText={setCustomName}
              autoFocus
            />
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
              <View style={{ flexDirection: "row", gap: 8 }}>
                {ALL_GROUPS.map((g) => (
                  <TouchableOpacity
                    key={g}
                    onPress={() => setCustomGroup(g)}
                    style={[styles.groupChip, { backgroundColor: customGroup === g ? colors.primary : colors.input, borderColor: customGroup === g ? colors.primary : colors.border }]}
                  >
                    <Text style={[styles.groupChipText, { color: customGroup === g ? colors.primaryForeground : colors.foreground }]}>
                      {MUSCLE_GROUP_LABELS[g]}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </ScrollView>
            <TouchableOpacity style={[styles.customSaveBtn, { backgroundColor: colors.primary }]} onPress={handleAddCustom}>
              <Text style={[styles.customSaveBtnText, { color: colors.primaryForeground }]}>Ajouter</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Exercise list */}
        {filtered.map((exercise) => {
          const added = addedIds.has(exercise.id);
          const custom = isCustomExercise(exercise.id);
          const isEditing = editingId === exercise.id;
          const isDeleting = deletingId === exercise.id;

          return (
            <View key={exercise.id}>
              {/* Edit form (inline, shown when editing this exercise) */}
              {isEditing && (
                <View style={[styles.customBox, { backgroundColor: colors.card, borderColor: colors.primary + "55", marginBottom: 0, borderBottomLeftRadius: 0, borderBottomRightRadius: 0 }]}>
                  <Text style={[styles.customTitle, { color: colors.foreground }]}>Modifier l'exercice</Text>
                  <TextInput
                    style={[styles.customInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                    value={editName}
                    onChangeText={setEditName}
                    autoFocus
                  />
                  <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
                    <View style={{ flexDirection: "row", gap: 8 }}>
                      {ALL_GROUPS.map((g) => (
                        <TouchableOpacity
                          key={g}
                          onPress={() => setEditGroup(g)}
                          style={[styles.groupChip, { backgroundColor: editGroup === g ? colors.primary : colors.input, borderColor: editGroup === g ? colors.primary : colors.border }]}
                        >
                          <Text style={[styles.groupChipText, { color: editGroup === g ? colors.primaryForeground : colors.foreground }]}>
                            {MUSCLE_GROUP_LABELS[g]}
                          </Text>
                        </TouchableOpacity>
                      ))}
                    </View>
                  </ScrollView>
                  <View style={{ flexDirection: "row", gap: 8 }}>
                    <TouchableOpacity
                      style={[styles.editCancelBtn, { borderColor: colors.border }]}
                      onPress={() => setEditingId(null)}
                    >
                      <Text style={[styles.editCancelText, { color: colors.foreground }]}>Annuler</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.customSaveBtn, { flex: 1, backgroundColor: colors.primary, flexDirection: "row", gap: 6 }]}
                      onPress={handleSaveEdit}
                    >
                      <Feather name="check" size={15} color={colors.primaryForeground} />
                      <Text style={[styles.customSaveBtnText, { color: colors.primaryForeground }]}>Enregistrer</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}

              {/* Exercise row */}
              <TouchableOpacity
                style={[
                  styles.exerciseRow,
                  {
                    backgroundColor: added ? colors.primary + "15" : colors.card,
                    borderColor: added ? colors.primary + "44" : isEditing ? colors.primary + "55" : colors.border,
                  },
                  isEditing && { borderTopLeftRadius: 0, borderTopRightRadius: 0, borderTopWidth: 0 },
                ]}
                onPress={() => !added && handleAdd(exercise.id)}
                disabled={added}
                activeOpacity={0.75}
              >
                <View style={styles.exerciseInfo}>
                  <Text style={[styles.exerciseName, { color: added ? colors.primary : colors.foreground }]}>
                    {exercise.name}
                  </Text>
                  <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
                </View>

                {/* Right side */}
                {added ? (
                  <Feather name="check" size={18} color={colors.primary} />
                ) : custom ? (
                  <View style={styles.customActions}>
                    <TouchableOpacity
                      onPress={() => { setDeletingId(null); openEdit(exercise); }}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      style={[styles.actionBtn, { borderColor: colors.border }]}
                    >
                      <Feather name="edit-2" size={13} color={colors.mutedForeground} />
                    </TouchableOpacity>
                    <TouchableOpacity
                      onPress={() => { setEditingId(null); setDeletingId(isDeleting ? null : exercise.id); Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); }}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      style={[styles.actionBtn, { borderColor: colors.border }]}
                    >
                      <Feather name="trash-2" size={13} color={colors.destructive} />
                    </TouchableOpacity>
                    <Feather name="plus" size={18} color={colors.mutedForeground} />
                  </View>
                ) : (
                  <Feather name="plus" size={18} color={colors.mutedForeground} />
                )}
              </TouchableOpacity>

              {/* Inline delete confirmation */}
              {isDeleting && (
                <View style={[styles.deleteConfirm, { backgroundColor: colors.destructive + "15", borderColor: colors.destructive + "44" }]}>
                  <Text style={[styles.deleteConfirmText, { color: colors.foreground }]}>
                    Supprimer « {exercise.name} » ?
                  </Text>
                  <View style={styles.deleteConfirmBtns}>
                    <TouchableOpacity
                      style={[styles.deleteCancelBtn, { borderColor: colors.border }]}
                      onPress={() => setDeletingId(null)}
                    >
                      <Text style={[styles.deleteCancelText, { color: colors.foreground }]}>Annuler</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.deleteOkBtn, { backgroundColor: colors.destructive }]}
                      onPress={() => handleDelete(exercise.id)}
                    >
                      <Feather name="trash-2" size={13} color="#fff" />
                      <Text style={styles.deleteOkText}>Supprimer</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}
            </View>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 20, paddingBottom: 12, borderBottomWidth: 1 },
  headerTop: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", marginBottom: 14 },
  title: { fontSize: 18, fontFamily: "Inter_700Bold" },
  searchBox: { flexDirection: "row", alignItems: "center", borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, gap: 8, marginBottom: 12 },
  searchInput: { flex: 1, fontSize: 15, fontFamily: "Inter_400Regular" },
  groupFilter: { marginBottom: 4 },
  groupChip: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, borderWidth: 1, marginRight: 8 },
  groupChipText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  exerciseRow: { flexDirection: "row", alignItems: "center", padding: 14, borderRadius: 12, borderWidth: 1, marginBottom: 8 },
  exerciseInfo: { flex: 1, gap: 6 },
  exerciseName: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  customActions: { flexDirection: "row", alignItems: "center", gap: 6 },
  actionBtn: { width: 28, height: 28, borderRadius: 8, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  // Add/edit form
  customBox: { borderRadius: 14, borderWidth: 1, padding: 16, marginBottom: 8 },
  customTitle: { fontSize: 15, fontFamily: "Inter_700Bold", marginBottom: 12 },
  customInput: { borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 15, fontFamily: "Inter_400Regular", marginBottom: 12 },
  customSaveBtn: { borderRadius: 10, padding: 12, alignItems: "center", justifyContent: "center" },
  customSaveBtnText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  editCancelBtn: { flex: 1, borderRadius: 10, borderWidth: 1, padding: 12, alignItems: "center" },
  editCancelText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  // Delete confirm
  deleteConfirm: { borderWidth: 1, borderTopWidth: 0, borderBottomLeftRadius: 12, borderBottomRightRadius: 12, padding: 12, gap: 10, marginBottom: 8 },
  deleteConfirmText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  deleteConfirmBtns: { flexDirection: "row", gap: 8 },
  deleteCancelBtn: { flex: 1, borderWidth: 1, borderRadius: 8, paddingVertical: 8, alignItems: "center" },
  deleteCancelText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  deleteOkBtn: { flex: 1, flexDirection: "row", gap: 6, borderRadius: 8, paddingVertical: 8, alignItems: "center", justifyContent: "center" },
  deleteOkText: { fontSize: 13, fontFamily: "Inter_600SemiBold", color: "#fff" },
});
