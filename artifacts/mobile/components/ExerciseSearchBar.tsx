import { Feather } from "@expo/vector-icons";
import React, { useState } from "react";
import { StyleSheet, TextInput, TouchableOpacity, View } from "react-native";
import { MuscleGroup } from "@/constants/muscles";
import { usePreferences } from "@/context/PreferencesContext";
import { Exercise } from "@/context/WorkoutContext";
import { useColors } from "@/hooks/useColors";
import { useExerciseName } from "@/hooks/useExerciseName";
import { normalizeSearch } from "@/utils/text";
import MuscleGroupChips from "./MuscleGroupChips";

/** Search text + muscle-group filter over an exercise list (accent-insensitive, on display names). */
export function useExerciseSearch(exercises: Exercise[]) {
  const exerciseName = useExerciseName();
  const [search, setSearch] = useState("");
  const [group, setGroup] = useState<MuscleGroup | null>(null);
  const query = normalizeSearch(search);
  const filtered = exercises.filter(
    (e) =>
      (!group || e.muscleGroup === group) &&
      (!query || normalizeSearch(exerciseName(e)).includes(query)),
  );
  return { search, setSearch, group, setGroup, filtered };
}

interface Props {
  search: string;
  onSearch: (s: string) => void;
  group: MuscleGroup | null;
  onGroup: (g: MuscleGroup | null) => void;
}

/** Search box above a muscle-group filter row. */
export default function ExerciseSearchBar({ search, onSearch, group, onGroup }: Props) {
  const colors = useColors();
  const { t } = usePreferences();
  return (
    <View style={styles.container}>
      <View style={[styles.searchBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
        <Feather name="search" size={16} color={colors.mutedForeground} />
        <TextInput
          style={[styles.searchInput, { color: colors.foreground }]}
          placeholder={t("exercisePicker.searchPlaceholder")}
          placeholderTextColor={colors.mutedForeground}
          value={search}
          onChangeText={onSearch}
        />
        {search.length > 0 && (
          <TouchableOpacity onPress={() => onSearch("")}>
            <Feather name="x" size={14} color={colors.mutedForeground} />
          </TouchableOpacity>
        )}
      </View>
      <MuscleGroupChips selected={group} onSelect={onGroup} allowAll />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { gap: 12 },
  searchBox: {
    flexDirection: "row", alignItems: "center", borderRadius: 10, borderWidth: 1,
    paddingHorizontal: 12, paddingVertical: 10, gap: 8,
  },
  searchInput: { flex: 1, fontSize: 15, fontFamily: "Inter_400Regular" },
});
