import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { useColors } from "@/hooks/useColors";
import { MuscleGroup } from "@/context/WorkoutContext";

const MUSCLE_GROUP_COLORS: Record<MuscleGroup, string> = {
  dos: "#3b82f6",
  pecs: "#f97316",
  biceps: "#a855f7",
  triceps: "#8b5cf6",
  jambes: "#22c55e",
  épaules: "#f59e0b",
  abdos: "#ef4444",
  cardio: "#ec4899",
  "avant-bras": "#14b8a6",
};

export const MUSCLE_GROUP_LABELS: Record<MuscleGroup, string> = {
  dos: "Dos",
  pecs: "Pecs",
  biceps: "Biceps",
  triceps: "Triceps",
  jambes: "Jambes",
  épaules: "Épaules",
  abdos: "Abdos",
  cardio: "Cardio",
  "avant-bras": "Avant-bras",
};

interface Props {
  muscleGroup: MuscleGroup;
  small?: boolean;
}

export default function MuscleGroupBadge({ muscleGroup, small }: Props) {
  const color = MUSCLE_GROUP_COLORS[muscleGroup];
  const label = MUSCLE_GROUP_LABELS[muscleGroup];

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
