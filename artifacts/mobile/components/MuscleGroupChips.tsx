import React from "react";
import { ScrollView, StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { TranslationKey } from "@/constants/i18n";
import { MUSCLE_GROUPS, MuscleGroup } from "@/constants/muscles";
import { usePreferences } from "@/context/PreferencesContext";
import { useColors } from "@/hooks/useColors";

interface Props {
  selected: MuscleGroup | null;
  onSelect: (group: MuscleGroup | null) => void;
  /** Show a leading "All" chip (selects null). Filters use it; forms don't. */
  allowAll?: boolean;
  /** Background of unselected chips. */
  chipBackground?: string;
}

/** Horizontal row of muscle-group chips, used as a filter or as a form field. */
export default function MuscleGroupChips({ selected, onSelect, allowAll, chipBackground }: Props) {
  const colors = useColors();
  const { t } = usePreferences();

  function chip(key: string, label: string, active: boolean, onPress: () => void) {
    return (
      <TouchableOpacity
        key={key}
        onPress={onPress}
        style={[
          styles.chip,
          {
            backgroundColor: active ? colors.primary : chipBackground ?? colors.card,
            borderColor: active ? colors.primary : colors.border,
          },
        ]}
      >
        <Text style={[styles.chipText, { color: active ? colors.primaryForeground : colors.foreground }]}>
          {label}
        </Text>
      </TouchableOpacity>
    );
  }

  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} keyboardShouldPersistTaps="handled">
      <View style={styles.row}>
        {allowAll && chip("all", t("common.all"), selected === null, () => onSelect(null))}
        {MUSCLE_GROUPS.map((g) =>
          chip(g, t(`muscle.${g}` as TranslationKey), selected === g, () =>
            onSelect(allowAll && selected === g ? null : g),
          ),
        )}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 7, borderRadius: 20, borderWidth: 1 },
  chipText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
});
