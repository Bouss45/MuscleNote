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
import { useWorkout } from "@/context/WorkoutContext";

const PRESET_GROUPS = [
  {
    label: "PPL",
    presets: ["Push", "Pull", "Legs"],
  },
  {
    label: "Split",
    presets: ["Pecs / Triceps", "Dos / Biceps", "Épaules", "Jambes", "Bras"],
  },
  {
    label: "Autre",
    presets: ["Full Body", "Isolation", "Cardio"],
  },
];

export default function StartWorkoutScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { startWorkout, workouts } = useWorkout();
  const [bodyWeight, setBodyWeight] = useState(
    workouts.length > 0 ? workouts[0].bodyWeight.toString() : ""
  );
  const [selectedName, setSelectedName] = useState<string | null>(null);
  const [customName, setCustomName] = useState("");
  const [step, setStep] = useState<"name" | "weight">("name");

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom + 20;

  const finalName = selectedName === "__custom__"
    ? customName.trim()
    : selectedName ?? "";

  function handleSelectPreset(name: string) {
    Haptics.selectionAsync();
    setSelectedName(name);
    setCustomName("");
  }

  function handleNext() {
    if (!finalName) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setStep("weight");
  }

  function handleStart() {
    const w = parseFloat(bodyWeight);
    if (!w || w <= 0 || !finalName) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    startWorkout(w, finalName);
    router.replace("/workout");
  }

  const quickWeights = [55, 60, 65, 70, 75, 80, 85, 90, 95, 100];

  return (
    <View
      style={[
        styles.container,
        { backgroundColor: colors.background, paddingTop: topPad + 20, paddingBottom: bottomPad },
      ]}
    >
      <TouchableOpacity
        onPress={() => {
          if (step === "weight") {
            setStep("name");
          } else {
            router.back();
          }
        }}
        style={styles.backBtn}
      >
        <Feather
          name={step === "weight" ? "arrow-left" : "x"}
          size={22}
          color={colors.foreground}
        />
      </TouchableOpacity>

      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {step === "name" ? (
          <>
            <Text style={[styles.title, { color: colors.foreground }]}>
              Type de séance
            </Text>
            <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>
              Choisis ou nomme ta séance
            </Text>

            {PRESET_GROUPS.map((group) => (
              <View key={group.label} style={styles.group}>
                <Text style={[styles.groupLabel, { color: colors.mutedForeground }]}>
                  {group.label}
                </Text>
                <View style={styles.presetRow}>
                  {group.presets.map((name) => {
                    const active = selectedName === name;
                    return (
                      <TouchableOpacity
                        key={name}
                        style={[
                          styles.presetChip,
                          {
                            backgroundColor: active ? colors.primary : colors.card,
                            borderColor: active ? colors.primary : colors.border,
                          },
                        ]}
                        onPress={() => handleSelectPreset(name)}
                        activeOpacity={0.75}
                      >
                        <Text
                          style={[
                            styles.presetChipText,
                            { color: active ? colors.primaryForeground : colors.foreground },
                          ]}
                        >
                          {name}
                        </Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            ))}

            {/* Custom name */}
            <View style={styles.group}>
              <Text style={[styles.groupLabel, { color: colors.mutedForeground }]}>
                Personnalisé
              </Text>
              <TouchableOpacity
                style={[
                  styles.customNameBox,
                  {
                    backgroundColor: selectedName === "__custom__" ? colors.primary + "15" : colors.card,
                    borderColor: selectedName === "__custom__" ? colors.primary : colors.border,
                  },
                ]}
                onPress={() => {
                  Haptics.selectionAsync();
                  setSelectedName("__custom__");
                }}
                activeOpacity={0.85}
              >
                <Feather
                  name="edit-2"
                  size={15}
                  color={selectedName === "__custom__" ? colors.primary : colors.mutedForeground}
                />
                {selectedName === "__custom__" ? (
                  <TextInput
                    style={[styles.customNameInput, { color: colors.foreground }]}
                    value={customName}
                    onChangeText={setCustomName}
                    placeholder="Ex: Épaules / Mollets"
                    placeholderTextColor={colors.mutedForeground}
                    autoFocus
                    maxLength={30}
                  />
                ) : (
                  <Text style={[styles.customNamePlaceholder, { color: colors.mutedForeground }]}>
                    Nom personnalisé...
                  </Text>
                )}
              </TouchableOpacity>
            </View>

            <TouchableOpacity
              style={[
                styles.nextBtn,
                {
                  backgroundColor: finalName ? colors.primary : colors.muted,
                  marginTop: 8,
                },
              ]}
              onPress={handleNext}
              disabled={!finalName}
              activeOpacity={0.85}
            >
              <Text
                style={[
                  styles.nextBtnText,
                  { color: finalName ? colors.primaryForeground : colors.mutedForeground },
                ]}
              >
                Suivant
              </Text>
              <Feather
                name="arrow-right"
                size={18}
                color={finalName ? colors.primaryForeground : colors.mutedForeground}
              />
            </TouchableOpacity>
          </>
        ) : (
          <>
            <View style={[styles.selectedNameBadge, { backgroundColor: colors.primary + "18", borderColor: colors.primary + "55" }]}>
              <Text style={[styles.selectedNameText, { color: colors.primary }]}>
                {finalName}
              </Text>
            </View>

            <Text style={[styles.title, { color: colors.foreground }]}>
              Poids de corps
            </Text>
            <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>
              Ton poids aujourd'hui ?
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
                        bodyWeight === w.toString() ? colors.primary : colors.card,
                      borderColor:
                        bodyWeight === w.toString() ? colors.primary : colors.border,
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
                styles.nextBtn,
                {
                  backgroundColor:
                    bodyWeight && parseFloat(bodyWeight) > 0
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
                size={18}
                color={
                  bodyWeight && parseFloat(bodyWeight) > 0
                    ? colors.primaryForeground
                    : colors.mutedForeground
                }
              />
              <Text
                style={[
                  styles.nextBtnText,
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
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  backBtn: {
    marginLeft: 24,
    marginBottom: 24,
  },
  scrollContent: {
    paddingHorizontal: 24,
    paddingBottom: 40,
  },
  title: {
    fontSize: 30,
    fontFamily: "Inter_700Bold",
    letterSpacing: -0.5,
    marginBottom: 6,
  },
  subtitle: {
    fontSize: 15,
    fontFamily: "Inter_400Regular",
    marginBottom: 28,
  },
  group: {
    marginBottom: 20,
  },
  groupLabel: {
    fontSize: 11,
    fontFamily: "Inter_600SemiBold",
    letterSpacing: 0.8,
    textTransform: "uppercase",
    marginBottom: 10,
  },
  presetRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
  },
  presetChip: {
    paddingHorizontal: 18,
    paddingVertical: 11,
    borderRadius: 12,
    borderWidth: 1.5,
  },
  presetChipText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  customNameBox: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    borderRadius: 12,
    borderWidth: 1.5,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  customNameInput: {
    flex: 1,
    fontSize: 15,
    fontFamily: "Inter_500Medium",
  },
  customNamePlaceholder: {
    flex: 1,
    fontSize: 15,
    fontFamily: "Inter_400Regular",
  },
  nextBtn: {
    borderRadius: 14,
    paddingVertical: 16,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
  },
  nextBtnText: {
    fontSize: 17,
    fontFamily: "Inter_700Bold",
  },
  selectedNameBadge: {
    borderRadius: 10,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 8,
    alignSelf: "flex-start",
    marginBottom: 24,
  },
  selectedNameText: {
    fontSize: 14,
    fontFamily: "Inter_700Bold",
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
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
  },
  quickBtnText: {
    fontSize: 14,
    fontFamily: "Inter_600SemiBold",
  },
});
