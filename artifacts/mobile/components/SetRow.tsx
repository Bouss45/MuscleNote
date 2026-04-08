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

interface Props {
  set: Set;
  index: number;
  isCardio?: boolean;
  onToggle: () => void;
  onUpdate: (data: SetUpdate) => void;
  onRemove: () => void;
  onSetComplete?: () => void;
}

export default function SetRow({ set, index, isCardio, onToggle, onUpdate, onRemove, onSetComplete }: Props) {
  const colors = useColors();

  // Strength fields
  const [reps, setReps] = useState(set.reps > 0 ? set.reps.toString() : "");
  const [weight, setWeight] = useState(set.weight > 0 ? set.weight.toString() : "");

  // Cardio fields
  const [duration, setDuration] = useState(set.duration != null ? set.duration.toString() : "");
  const [distance, setDistance] = useState(set.distance != null ? set.distance.toString() : "");
  const [speed, setSpeed] = useState(set.speed != null ? set.speed.toString() : "");
  const [incline, setIncline] = useState(set.incline != null ? set.incline.toString() : "");

  function handleToggle() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    onToggle();
    if (!set.completed) onSetComplete?.();
  }

  function handleBlur() {
    if (isCardio) {
      onUpdate({
        duration: parseFloat(duration) || 0,
        distance: parseFloat(distance) || 0,
        speed: parseFloat(speed) || 0,
        incline: parseFloat(incline) || 0,
      });
    } else {
      onUpdate({
        reps: parseInt(reps) || 0,
        weight: parseFloat(weight) || 0,
      });
    }
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
          backgroundColor: set.completed ? colors.primary + "18" : colors.card,
          borderColor: set.completed ? colors.primary + "44" : colors.border,
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
                value={duration}
                onChangeText={setDuration}
                onBlur={handleBlur}
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
                value={distance}
                onChangeText={setDistance}
                onBlur={handleBlur}
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
                value={speed}
                onChangeText={setSpeed}
                onBlur={handleBlur}
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
                value={incline}
                onChangeText={setIncline}
                onBlur={handleBlur}
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
              value={reps}
              onChangeText={setReps}
              onBlur={handleBlur}
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
              value={weight}
              onChangeText={setWeight}
              onBlur={handleBlur}
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
