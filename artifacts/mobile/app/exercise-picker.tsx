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
import { MuscleGroup, useWorkout } from "@/context/WorkoutContext";
import MuscleGroupBadge, { MUSCLE_GROUP_LABELS } from "@/components/MuscleGroupBadge";

const ALL_GROUPS: MuscleGroup[] = ["dos", "pecs", "biceps", "triceps", "jambes", "épaules", "abdos", "cardio"];

export default function ExercisePickerScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { exercises, addExerciseToWorkout, currentWorkout, addCustomExercise } = useWorkout();
  const [search, setSearch] = useState("");
  const [selectedGroup, setSelectedGroup] = useState<MuscleGroup | null>(null);
  const [showAddCustom, setShowAddCustom] = useState(false);
  const [customName, setCustomName] = useState("");
  const [customGroup, setCustomGroup] = useState<MuscleGroup>("dos");

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

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={[styles.header, { paddingTop: topPad + 12, borderBottomColor: colors.border }]}>
        <View style={styles.headerTop}>
          <TouchableOpacity onPress={() => router.back()}>
            <Feather name="x" size={22} color={colors.foreground} />
          </TouchableOpacity>
          <Text style={[styles.title, { color: colors.foreground }]}>
            Exercices
          </Text>
          <TouchableOpacity onPress={() => setShowAddCustom(!showAddCustom)}>
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
            style={[
              styles.groupChip,
              {
                backgroundColor: selectedGroup === null ? colors.primary : colors.card,
                borderColor: selectedGroup === null ? colors.primary : colors.border,
              },
            ]}
            onPress={() => setSelectedGroup(null)}
          >
            <Text
              style={[
                styles.groupChipText,
                { color: selectedGroup === null ? colors.primaryForeground : colors.foreground },
              ]}
            >
              Tous
            </Text>
          </TouchableOpacity>
          {ALL_GROUPS.map((g) => (
            <TouchableOpacity
              key={g}
              style={[
                styles.groupChip,
                {
                  backgroundColor: selectedGroup === g ? colors.primary : colors.card,
                  borderColor: selectedGroup === g ? colors.primary : colors.border,
                },
              ]}
              onPress={() => setSelectedGroup(selectedGroup === g ? null : g)}
            >
              <Text
                style={[
                  styles.groupChipText,
                  { color: selectedGroup === g ? colors.primaryForeground : colors.foreground },
                ]}
              >
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
        {showAddCustom && (
          <View style={[styles.customBox, { backgroundColor: colors.card, borderColor: colors.primary + "55" }]}>
            <Text style={[styles.customTitle, { color: colors.foreground }]}>
              Exercice personnalisé
            </Text>
            <TextInput
              style={[styles.customInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
              placeholder="Nom de l'exercice"
              placeholderTextColor={colors.mutedForeground}
              value={customName}
              onChangeText={setCustomName}
            />
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginBottom: 12 }}>
              <View style={{ flexDirection: "row", gap: 8 }}>
                {ALL_GROUPS.map((g) => (
                  <TouchableOpacity
                    key={g}
                    onPress={() => setCustomGroup(g)}
                    style={[
                      styles.groupChip,
                      {
                        backgroundColor: customGroup === g ? colors.primary : colors.input,
                        borderColor: customGroup === g ? colors.primary : colors.border,
                      },
                    ]}
                  >
                    <Text style={[styles.groupChipText, { color: customGroup === g ? colors.primaryForeground : colors.foreground }]}>
                      {MUSCLE_GROUP_LABELS[g]}
                    </Text>
                  </TouchableOpacity>
                ))}
              </View>
            </ScrollView>
            <TouchableOpacity
              style={[styles.customSaveBtn, { backgroundColor: colors.primary }]}
              onPress={handleAddCustom}
            >
              <Text style={[styles.customSaveBtnText, { color: colors.primaryForeground }]}>
                Ajouter
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {filtered.map((exercise) => {
          const added = addedIds.has(exercise.id);
          return (
            <TouchableOpacity
              key={exercise.id}
              style={[
                styles.exerciseRow,
                {
                  backgroundColor: added ? colors.primary + "15" : colors.card,
                  borderColor: added ? colors.primary + "44" : colors.border,
                },
              ]}
              onPress={() => handleAdd(exercise.id)}
              disabled={added}
              activeOpacity={0.75}
            >
              <View style={styles.exerciseInfo}>
                <Text style={[styles.exerciseName, { color: added ? colors.primary : colors.foreground }]}>
                  {exercise.name}
                </Text>
                <MuscleGroupBadge muscleGroup={exercise.muscleGroup} small />
              </View>
              {added ? (
                <Feather name="check" size={18} color={colors.primary} />
              ) : (
                <Feather name="plus" size={18} color={colors.mutedForeground} />
              )}
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    paddingHorizontal: 20,
    paddingBottom: 12,
    borderBottomWidth: 1,
  },
  headerTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 14,
  },
  title: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
  },
  searchBox: {
    flexDirection: "row",
    alignItems: "center",
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    gap: 8,
    marginBottom: 12,
  },
  searchInput: {
    flex: 1,
    fontSize: 15,
    fontFamily: "Inter_400Regular",
  },
  groupFilter: {
    marginBottom: 4,
  },
  groupChip: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 20,
    borderWidth: 1,
    marginRight: 8,
  },
  groupChipText: {
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
  },
  exerciseRow: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 8,
  },
  exerciseInfo: {
    flex: 1,
    gap: 6,
  },
  exerciseName: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  customBox: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 16,
    marginBottom: 16,
  },
  customTitle: {
    fontSize: 15,
    fontFamily: "Inter_700Bold",
    marginBottom: 12,
  },
  customInput: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
    fontFamily: "Inter_400Regular",
    marginBottom: 12,
  },
  customSaveBtn: {
    borderRadius: 10,
    padding: 12,
    alignItems: "center",
  },
  customSaveBtnText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
});
