import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { usePreferences } from "@/context/PreferencesContext";
import { TranslationKey } from "@/constants/i18n";
import { MUSCLE_COLORS, MuscleGroup } from "@/constants/muscles";

interface Props {
  muscleGroup: MuscleGroup;
  small?: boolean;
}

export default function MuscleGroupBadge({ muscleGroup, small }: Props) {
  const { t } = usePreferences();
  const color = MUSCLE_COLORS[muscleGroup];
  const label = t(`muscle.${muscleGroup}` as TranslationKey);

  return (
    <View
      style={[
        styles.badge,
        { backgroundColor: color + "22", borderColor: color + "55" },
        small && styles.small,
      ]}
    >
      <Text style={[styles.text, { color }, small && styles.smallText]}>
        {label}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 20,
    borderWidth: 1,
  },
  text: {
    fontSize: 12,
    fontFamily: "Inter_600SemiBold",
  },
  small: {
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  smallText: {
    fontSize: 10,
  },
});
