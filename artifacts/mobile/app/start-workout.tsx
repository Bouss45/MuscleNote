import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useState } from "react";
import {
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { useWorkout } from "@/context/WorkoutContext";

export default function StartWorkoutScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { startWorkout, workouts } = useWorkout();
  const [bodyWeight, setBodyWeight] = useState(
    workouts.length > 0 ? workouts[0].bodyWeight.toString() : ""
  );

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom + 20;

  function handleStart() {
    const w = parseFloat(bodyWeight);
    if (!w || w <= 0) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    startWorkout(w);
    router.replace("/workout");
  }

  const quickWeights = [60, 65, 70, 75, 80, 85, 90, 95, 100];

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: colors.background, paddingTop: topPad + 20, paddingBottom: bottomPad },
      ]}
    >
      <TouchableOpacity
        onPress={() => router.back()}
        style={[styles.backBtn, { marginLeft: 20 }]}
      >
        <Feather name="x" size={22} color={colors.foreground} />
      </TouchableOpacity>

      <View style={styles.content}>
        <Text style={[styles.title, { color: colors.foreground }]}>
          Nouvelle séance
        </Text>
        <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>
          Ton poids de corps aujourd'hui ?
        </Text>

        <View style={styles.weightContainer}>
          <TextInput
            style={[
              styles.weightInput,
              {
                color: colors.foreground,
                backgroundColor: colors.card,
                borderColor: colors.primary,
              },
            ]}
            value={bodyWeight}
            onChangeText={setBodyWeight}
            keyboardType="decimal-pad"
            placeholder="75"
            placeholderTextColor={colors.mutedForeground}
            autoFocus
            maxLength={6}
          />
          <Text style={[styles.kgLabel, { color: colors.mutedForeground }]}>
            kg
          </Text>
        </View>

        <View style={styles.quickPicks}>
          {quickWeights.map((w) => (
            <TouchableOpacity
              key={w}
              style={[
                styles.quickBtn,
                {
                  backgroundColor:
                    bodyWeight === w.toString()
                      ? colors.primary
                      : colors.card,
                  borderColor:
                    bodyWeight === w.toString()
                      ? colors.primary
                      : colors.border,
                },
              ]}
              onPress={() => {
                Haptics.selectionAsync();
                setBodyWeight(w.toString());
              }}
            >
              <Text
                style={[
                  styles.quickBtnText,
                  {
                    color:
                      bodyWeight === w.toString()
                        ? colors.primaryForeground
                        : colors.foreground,
                  },
                ]}
              >
                {w}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <TouchableOpacity
          style={[
            styles.startBtn,
            {
              backgroundColor: bodyWeight && parseFloat(bodyWeight) > 0
                ? colors.primary
                : colors.muted,
            },
          ]}
          onPress={handleStart}
          disabled={!bodyWeight || parseFloat(bodyWeight) <= 0}
          activeOpacity={0.85}
        >
          <Feather
            name="play"
            size={20}
            color={
              bodyWeight && parseFloat(bodyWeight) > 0
                ? colors.primaryForeground
                : colors.mutedForeground
            }
          />
          <Text
            style={[
              styles.startBtnText,
              {
                color:
                  bodyWeight && parseFloat(bodyWeight) > 0
                    ? colors.primaryForeground
                    : colors.mutedForeground,
              },
            ]}
          >
            Commencer
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  backBtn: {
    marginBottom: 32,
  },
  content: {
    paddingHorizontal: 24,
  },
  title: {
    fontSize: 32,
    fontFamily: "Inter_700Bold",
    letterSpacing: -0.5,
    marginBottom: 8,
  },
  subtitle: {
    fontSize: 16,
    fontFamily: "Inter_400Regular",
    marginBottom: 40,
  },
  weightContainer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    marginBottom: 28,
  },
  weightInput: {
    fontSize: 52,
    fontFamily: "Inter_700Bold",
    textAlign: "center",
    width: 160,
    height: 90,
    borderRadius: 16,
    borderWidth: 2,
    letterSpacing: -1,
  },
  kgLabel: {
    fontSize: 28,
    fontFamily: "Inter_600SemiBold",
  },
  quickPicks: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    marginBottom: 40,
    justifyContent: "center",
  },
  quickBtn: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
  },
  quickBtnText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  startBtn: {
    borderRadius: 14,
    paddingVertical: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
  },
  startBtnText: {
    fontSize: 17,
    fontFamily: "Inter_700Bold",
  },
});
