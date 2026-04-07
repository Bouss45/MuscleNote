import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useState } from "react";
import {
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useColors } from "@/hooks/useColors";
import { Set } from "@/context/WorkoutContext";

interface Props {
  set: Set;
  index: number;
  onToggle: () => void;
  onUpdate: (reps: number, weight: number) => void;
  onRemove: () => void;
}

export default function SetRow({ set, index, onToggle, onUpdate, onRemove }: Props) {
  const colors = useColors();
  const [reps, setReps] = useState(set.reps.toString());
  const [weight, setWeight] = useState(set.weight.toString());

  function handleToggle() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    onToggle();
  }

  function handleBlur() {
    const r = parseInt(reps) || 0;
    const w = parseFloat(weight) || 0;
    onUpdate(r, w);
  }

  return (
    <View
      style={[
        styles.row,
        {
          backgroundColor: set.completed
            ? colors.primary + "18"
            : colors.card,
          borderColor: set.completed ? colors.primary + "44" : colors.border,
        },
      ]}
    >
      <Text style={[styles.index, { color: colors.mutedForeground }]}>
        {index + 1}
      </Text>

      <View style={styles.inputs}>
        <View style={styles.inputGroup}>
          <TextInput
            style={[
              styles.input,
              {
                color: colors.foreground,
                backgroundColor: colors.input,
                borderColor: colors.border,
              },
            ]}
            value={reps}
            onChangeText={setReps}
            onBlur={handleBlur}
            keyboardType="number-pad"
            maxLength={3}
            selectTextOnFocus
          />
          <Text style={[styles.unit, { color: colors.mutedForeground }]}>
            reps
          </Text>
        </View>

        <Text style={[styles.x, { color: colors.mutedForeground }]}>×</Text>

        <View style={styles.inputGroup}>
          <TextInput
            style={[
              styles.input,
              {
                color: colors.foreground,
                backgroundColor: colors.input,
                borderColor: colors.border,
              },
            ]}
            value={weight}
            onChangeText={setWeight}
            onBlur={handleBlur}
            keyboardType="decimal-pad"
            maxLength={6}
            selectTextOnFocus
          />
          <Text style={[styles.unit, { color: colors.mutedForeground }]}>
            kg
          </Text>
        </View>
      </View>

      <TouchableOpacity
        onPress={handleToggle}
        style={[
          styles.checkBtn,
          {
            backgroundColor: set.completed ? colors.primary : "transparent",
            borderColor: set.completed ? colors.primary : colors.border,
          },
        ]}
      >
        {set.completed && (
          <Feather name="check" size={14} color={colors.primaryForeground} />
        )}
      </TouchableOpacity>

      <TouchableOpacity onPress={onRemove} style={styles.deleteBtn}>
        <Feather name="trash-2" size={14} color={colors.mutedForeground} />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 8,
    gap: 8,
  },
  index: {
    width: 20,
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
    textAlign: "center",
  },
  inputs: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
  },
  inputGroup: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
  },
  input: {
    width: 52,
    paddingVertical: 8,
    borderRadius: 8,
    borderWidth: 1,
    textAlign: "center",
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
    includeFontPadding: false,
  },
  unit: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
  },
  x: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
  },
  checkBtn: {
    width: 30,
    height: 30,
    borderRadius: 15,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
  },
  deleteBtn: {
    padding: 4,
  },
});
