import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useState } from "react";
import {
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useColors } from "@/hooks/useColors";
import { Set, SetUpdate } from "@/context/WorkoutContext";
import { parseDecimal, parseInteger } from "@/utils/number";

interface Props {
  set: Set;
  index: number;
  isCardio?: boolean;
  /** Omit to hide the completion checkbox (editing a finished session). */
  onToggle?: () => void;
  onUpdate: (data: SetUpdate) => void;
  onRemove: () => void;
  onSetComplete?: () => void;
}

type Field = "reps" | "weight" | "duration" | "distance" | "speed" | "incline";

function initial(v: number | undefined, hideZero: boolean) {
  if (v == null || (hideZero && v === 0)) return "";
  return v.toString();
}

export default function SetRow({ set, index, isCardio, onToggle, onUpdate, onRemove, onSetComplete }: Props) {
  const colors = useColors();

  // Raw text per field (keeps "62," visible while typing). Every keystroke is
  // committed right away — waiting for blur loses the value when the user taps
  // "Save"/"Finish" without leaving the field first.
  const [text, setText] = useState<Record<Field, string>>({
    reps: initial(set.reps, true),
    weight: initial(set.weight, true),
    duration: initial(set.duration, false),
    distance: initial(set.distance, false),
    speed: initial(set.speed, false),
    incline: initial(set.incline, false),
  });

  function change(field: Field, value: string) {
    setText((prev) => ({ ...prev, [field]: value }));
    onUpdate({ [field]: field === "reps" ? parseInteger(value) : parseDecimal(value) });
  }

  function handleToggle() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    onToggle?.();
    if (!set.completed) onSetComplete?.();
  }

  const inputStyle = [
    styles.input,
    {
      color: colors.foreground,
      backgroundColor: colors.input,
      borderColor: colors.border,
    },
  ];

  return (
    <View
      style={[
        styles.row,
        {
          backgroundColor: onToggle && set.completed ? colors.primary + "18" : colors.card,
          borderColor: onToggle && set.completed ? colors.primary + "44" : colors.border,
        },
      ]}
    >
      <Text style={[styles.index, { color: colors.mutedForeground }]}>
        {index + 1}
      </Text>

      {isCardio ? (
        // ── Cardio: 2×2 grid ──────────────────────────────────────────────
        <View style={styles.cardioGrid}>
          <View style={styles.cardioRow}>
            <View style={styles.cardioField}>
              <TextInput
                style={inputStyle}
                value={text.duration}
                onChangeText={(v) => change("duration", v)}
                keyboardType="decimal-pad"
                maxLength={4}
                placeholder="0"
                placeholderTextColor={colors.mutedForeground}
                selectTextOnFocus
              />
              <Text style={[styles.unit, { color: colors.mutedForeground }]}>min</Text>
            </View>
            <View style={styles.cardioField}>
              <TextInput
                style={inputStyle}
                value={text.distance}
                onChangeText={(v) => change("distance", v)}
                keyboardType="decimal-pad"
                maxLength={5}
                placeholder="0"
                placeholderTextColor={colors.mutedForeground}
                selectTextOnFocus
              />
              <Text style={[styles.unit, { color: colors.mutedForeground }]}>km</Text>
            </View>
          </View>
          <View style={styles.cardioRow}>
            <View style={styles.cardioField}>
              <TextInput
                style={inputStyle}
                value={text.speed}
                onChangeText={(v) => change("speed", v)}
                keyboardType="decimal-pad"
                maxLength={4}
                placeholder="0"
                placeholderTextColor={colors.mutedForeground}
                selectTextOnFocus
              />
              <Text style={[styles.unit, { color: colors.mutedForeground }]}>km/h</Text>
            </View>
            <View style={styles.cardioField}>
              <TextInput
                style={inputStyle}
                value={text.incline}
                onChangeText={(v) => change("incline", v)}
                keyboardType="decimal-pad"
                maxLength={4}
                placeholder="0"
                placeholderTextColor={colors.mutedForeground}
                selectTextOnFocus
              />
              <Text style={[styles.unit, { color: colors.mutedForeground }]}>% inc.</Text>
            </View>
          </View>
        </View>
      ) : (
        // ── Strength: reps × weight ────────────────────────────────────────
        <View style={styles.inputs}>
          <View style={styles.inputGroup}>
            <TextInput
              style={inputStyle}
              value={text.reps}
              onChangeText={(v) => change("reps", v)}
              keyboardType="number-pad"
              maxLength={3}
              placeholder="0"
              placeholderTextColor={colors.mutedForeground}
              selectTextOnFocus
            />
            <Text style={[styles.unit, { color: colors.mutedForeground }]}>reps</Text>
          </View>
          <Text style={[styles.x, { color: colors.mutedForeground }]}>×</Text>
          <View style={styles.inputGroup}>
            <TextInput
              style={inputStyle}
              value={text.weight}
              onChangeText={(v) => change("weight", v)}
              keyboardType="decimal-pad"
              maxLength={6}
              placeholder="0"
              placeholderTextColor={colors.mutedForeground}
              selectTextOnFocus
            />
            <Text style={[styles.unit, { color: colors.mutedForeground }]}>kg</Text>
          </View>
        </View>
      )}

      {onToggle && (
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
      )}

      <TouchableOpacity onPress={onRemove} style={styles.deleteBtn}>
        <Feather name="trash-2" size={14} color={onToggle ? colors.mutedForeground : colors.destructive} />
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 10,
    borderWidth: 1,
    marginBottom: 8,
    gap: 6,
  },
  index: {
    width: 18,
    fontSize: 13,
    fontFamily: "Inter_600SemiBold",
    textAlign: "center",
  },
  // Strength
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
  // Cardio
  cardioGrid: {
    flex: 1,
    gap: 4,
  },
  cardioRow: {
    flexDirection: "row",
    gap: 8,
  },
  cardioField: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
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
