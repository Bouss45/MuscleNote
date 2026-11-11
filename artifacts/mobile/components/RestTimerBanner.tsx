import { Feather } from "@expo/vector-icons";
import React from "react";
import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { usePreferences } from "@/context/PreferencesContext";
import { useRestTimer } from "@/context/RestTimerContext";
import { useColors } from "@/hooks/useColors";

function formatRest(s: number) {
  return `${Math.floor(s / 60)}:${(s % 60).toString().padStart(2, "0")}`;
}

/** Banner for the session-wide rest timer; renders nothing when no rest is running. */
export default function RestTimerBanner() {
  const colors = useColors();
  const { t } = usePreferences();
  const { secondsLeft, total, stop } = useRestTimer();
  if (secondsLeft === null) return null;

  const pct = total > 0 ? secondsLeft / total : 0;
  const color = pct > 0.5 ? colors.success : pct > 0.25 ? "#f59e0b" : "#ef4444";

  return (
    <View style={[styles.banner, { backgroundColor: color + "22", borderColor: color + "55" }]}>
      <View style={styles.left}>
        <Feather name="clock" size={14} color={color} />
        <Text style={[styles.label, { color }]}>{t("restTimer.label")}</Text>
        <Text style={[styles.count, { color }]}>{formatRest(secondsLeft)}</Text>
      </View>
      <View style={[styles.progress, { backgroundColor: colors.border }]}>
        <View style={[styles.fill, { backgroundColor: color, width: `${pct * 100}%` }]} />
      </View>
      <TouchableOpacity onPress={stop} style={styles.skip} hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}>
        <Feather name="skip-forward" size={14} color={color} />
        <Text style={[styles.skipText, { color }]}>{t("restTimer.skip")}</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  banner: {
    flexDirection: "row", alignItems: "center", gap: 10, marginBottom: 10,
    borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10,
  },
  left: { flexDirection: "row", alignItems: "center", gap: 5 },
  label: { fontSize: 12, fontFamily: "Inter_400Regular" },
  count: { fontSize: 17, fontFamily: "Inter_700Bold" },
  progress: { flex: 1, height: 4, borderRadius: 2, overflow: "hidden" },
  fill: { height: 4, borderRadius: 2 },
  skip: { flexDirection: "row", alignItems: "center", gap: 3 },
  skipText: { fontSize: 12, fontFamily: "Inter_600SemiBold" },
});
