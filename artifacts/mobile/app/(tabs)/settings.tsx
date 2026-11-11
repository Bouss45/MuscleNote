import { Feather } from "@expo/vector-icons";
import * as DocumentPicker from "expo-document-picker";
import * as FileSystem from "expo-file-system/legacy";
import * as Sharing from "expo-sharing";
import React, { useRef, useState } from "react";
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useColors } from "@/hooks/useColors";
import { useExerciseName } from "@/hooks/useExerciseName";
import { usePreferences } from "@/context/PreferencesContext";
import { useNutrition } from "@/context/NutritionContext";
import { LANGUAGES, TranslationKey } from "@/constants/i18n";
import { useWorkout } from "@/context/WorkoutContext";
import { BACKUP_KEYS, parseBackup } from "@/utils/backup";
import { dayKey, parseDayKey } from "@/utils/date";

// Rest-timer presets offered in Settings (seconds)
const REST_PRESETS = [60, 90, 120, 150, 180, 240, 300];

type Status = { type: "success" | "error"; message: string } | null;

export default function SettingsScreen() {
  const colors = useColors();
  const { t, locale, language, setLanguage, restSeconds, setRestSeconds, reload: reloadPreferences } = usePreferences();
  const insets = useSafeAreaInsets();
  const { workouts, exercises, exerciseById, isCustomExercise, reloadData } = useWorkout();
  const { bodyWeights, tracker, reload: reloadNutrition } = useNutrition();
  const exerciseName = useExerciseName();
  const [exporting, setExporting] = useState(false);
  const [exportingExcel, setExportingExcel] = useState(false);
  const [importing, setImporting] = useState(false);
  const [status, setStatus] = useState<Status>(null);
  const [confirmImport, setConfirmImport] = useState(false);
  const pendingImportRef = useRef<string | null>(null);

  const topPad = insets.top;

  function formatRestLabel(sec: number) {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    if (m === 0) return t("settings.restTimer.seconds", { count: s });
    if (s === 0) return t("settings.restTimer.minutes", { minutes: m });
    return t("settings.restTimer.minutesSeconds", { minutes: m, seconds: s });
  }

  async function shareFile(path: string, options: Sharing.SharingOptions): Promise<boolean> {
    if (!(await Sharing.isAvailableAsync())) {
      setStatus({ type: "error", message: t("settings.export.noShare") });
      return false;
    }
    await Sharing.shareAsync(path, options);
    return true;
  }

  // ─── Export ───────────────────────────────────────────────────────────────
  async function handleExport() {
    setExporting(true);
    setStatus(null);
    try {
      const pairs = await AsyncStorage.multiGet(BACKUP_KEYS);
      const data: Record<string, unknown> = { version: 1, exportedAt: new Date().toISOString() };
      for (const [key, value] of pairs) {
        if (value !== null) {
          try { data[key] = JSON.parse(value); } catch { data[key] = value; }
        }
      }
      const path = FileSystem.cacheDirectory + `musclenote_backup_${dayKey()}.json`;
      await FileSystem.writeAsStringAsync(path, JSON.stringify(data, null, 2), { encoding: "utf8" });
      if (await shareFile(path, { mimeType: "application/json", dialogTitle: t("settings.share.json") })) {
        setStatus({ type: "success", message: t("settings.export.successShare") });
      }
    } catch (e) {
      setStatus({ type: "error", message: t("settings.export.error", { error: String(e) }) });
    } finally {
      setExporting(false);
    }
  }

  // ─── Export Excel ────────────────────────────────────────────────────────
  async function handleExportExcel() {
    setExportingExcel(true);
    setStatus(null);
    try {
      const XLSX = require("xlsx");
      const col = (k: string) => t(`excel.col.${k}` as TranslationKey);
      const fmtDate = (d: Date) => d.toLocaleDateString(locale);

      // Sheet 1 — sessions overview
      const sessionRows = workouts.map((w) => {
        const d = new Date(w.date);
        return {
          [col("date")]: fmtDate(d),
          [col("time")]: d.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" }),
          [col("sessionName")]: w.name || "",
          [col("duration")]: w.durationMinutes ?? "",
          [col("bodyWeight")]: w.bodyWeight || "",
          [col("exercises")]: w.exercises.length,
          [col("totalSets")]: w.exercises.reduce((s, e) => s + e.sets.length, 0),
          [col("totalVolume")]: w.exercises.reduce(
            (s, e) => s + e.sets.reduce((s2, set) => s2 + set.reps * set.weight, 0), 0,
          ),
        };
      });

      // Sheet 2 — every set
      const setRows: Record<string, unknown>[] = [];
      for (const w of workouts) {
        for (const we of w.exercises) {
          const ex = exerciseById.get(we.exerciseId);
          we.sets.forEach((set, i) => {
            const row: Record<string, unknown> = {
              [col("date")]: fmtDate(new Date(w.date)),
              [col("session")]: w.name || "",
              [col("exercise")]: ex ? exerciseName(ex) : we.exerciseId,
              [col("muscleGroup")]: ex ? t(`muscle.${ex.muscleGroup}` as TranslationKey) : "",
              [col("setNumber")]: i + 1,
            };
            if (ex?.muscleGroup === "cardio") {
              row[col("duration")] = set.duration ?? "";
              row[col("distance")] = set.distance ?? "";
              row[col("speed")] = set.speed ?? "";
              row[col("incline")] = set.incline ?? "";
            } else {
              row[col("reps")] = set.reps || "";
              row[col("weight")] = set.weight || "";
            }
            setRows.push(row);
          });
        }
      }

      // Sheet 3 — manual weigh-ins
      const weightRows = bodyWeights.map((e) => ({
        [col("date")]: fmtDate(new Date(e.date)),
        [col("weight")]: e.weight,
      }));

      // Sheet 4 — nutrition (calories + water)
      const days = [...new Set([...Object.keys(tracker.caloriesLog), ...Object.keys(tracker.waterLog)])].sort();
      const nutritionRows = days.map((day) => ({
        [col("date")]: fmtDate(parseDayKey(day)),
        [col("calories")]: tracker.caloriesLog[day] ?? 0,
        [col("water")]: tracker.waterLog[day] ?? 0,
      }));

      const wb = XLSX.utils.book_new();
      const sheets: [TranslationKey, Record<string, unknown>[]][] = [
        ["excel.sheet.sessions", sessionRows],
        ["excel.sheet.sets", setRows],
        ["excel.sheet.bodyWeight", weightRows],
        ["excel.sheet.nutrition", nutritionRows],
      ];
      for (const [name, rows] of sheets) {
        if (rows.length > 0) XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(rows), t(name));
      }
      if (!wb.SheetNames.length) {
        setStatus({ type: "error", message: t("settings.export.noData") });
        return;
      }

      const path = FileSystem.cacheDirectory + `musclenote_${dayKey()}.xlsx`;
      await FileSystem.writeAsStringAsync(path, XLSX.write(wb, { type: "base64", bookType: "xlsx" }), {
        encoding: FileSystem.EncodingType.Base64,
      });
      const shared = await shareFile(path, {
        mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        dialogTitle: t("settings.share.excel"),
        UTI: "com.microsoft.excel.xlsx",
      });
      if (shared) setStatus({ type: "success", message: t("settings.export.successExcel") });
    } catch (e) {
      setStatus({ type: "error", message: t("settings.export.errorExcel", { error: String(e) }) });
    } finally {
      setExportingExcel(false);
    }
  }

  // ─── Import ───────────────────────────────────────────────────────────────
  async function processImport(json: string) {
    const parsed = parseBackup(json);
    if (!parsed.ok) {
      const reason =
        parsed.error === "noData" ? t("settings.import.noMuscleNote")
        : parsed.error === "invalidKey" ? t("settings.import.invalidKey", { key: parsed.key ?? "" })
        : t("settings.import.invalidFormat");
      setStatus({ type: "error", message: t("settings.import.error", { error: reason }) });
      return;
    }
    try {
      await AsyncStorage.multiSet(parsed.pairs);
      // Every provider re-reads storage, so no screen keeps (and later re-saves) stale data.
      await Promise.all([reloadData(), reloadNutrition(), reloadPreferences()]);
      setStatus({ type: "success", message: t("settings.import.success") });
    } catch (e) {
      setStatus({ type: "error", message: t("settings.import.error", { error: String(e) }) });
    }
  }

  async function handleImport() {
    setStatus(null);
    setConfirmImport(false);
    setImporting(true);
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ["application/json", "text/plain", "*/*"],
        copyToCacheDirectory: true,
      });
      if (result.canceled || !result.assets?.[0]) return;
      pendingImportRef.current = await FileSystem.readAsStringAsync(result.assets[0].uri, { encoding: "utf8" });
      setConfirmImport(true);
    } catch (e) {
      setStatus({ type: "error", message: t("settings.import.readError", { error: String(e) }) });
    } finally {
      setImporting(false);
    }
  }

  async function handleConfirmImport() {
    if (!pendingImportRef.current) return;
    setImporting(true);
    setConfirmImport(false);
    await processImport(pendingImportRef.current);
    pendingImportRef.current = null;
    setImporting(false);
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: topPad + 16, borderBottomColor: colors.border }]}>
        <Text style={[styles.title, { color: colors.foreground }]}>{t("settings.title")}</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: 20, gap: 12 }} showsVerticalScrollIndicator={false}>

        {/* Stats summary */}
        <View style={[styles.statsCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Text style={[styles.sectionLabel, { color: colors.mutedForeground }]}>{t("settings.myData")}</Text>
          <View style={styles.statsRow}>
            <View style={styles.statItem}>
              <Text style={[styles.statValue, { color: colors.foreground }]}>{workouts.length}</Text>
              <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("settings.stat.sessions")}</Text>
            </View>
            <View style={[styles.statDivider, { backgroundColor: colors.border }]} />
            <View style={styles.statItem}>
              <Text style={[styles.statValue, { color: colors.foreground }]}>
                {exercises.filter((e) => isCustomExercise(e.id) && !e.archived).length}
              </Text>
              <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>{t("settings.stat.customExercises")}</Text>
            </View>
          </View>
        </View>

        {/* Preferences section */}
        <Text style={[styles.sectionTitle, { color: colors.mutedForeground }]}>{t("settings.section.preferences")}</Text>

        {/* Language */}
        <View style={[styles.prefCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <View style={styles.prefHeader}>
            <View style={[styles.iconCircle, { backgroundColor: colors.info + "20" }]}>
              <Feather name="globe" size={20} color={colors.info} />
            </View>
            <View style={styles.actionText}>
              <Text style={[styles.actionTitle, { color: colors.foreground }]}>{t("settings.language.title")}</Text>
              <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>{t("settings.language.desc")}</Text>
            </View>
          </View>
          <View style={styles.chipWrap}>
            {LANGUAGES.map((l) => {
              const active = language === l.code;
              return (
                <TouchableOpacity
                  key={l.code}
                  style={[styles.langChip, { backgroundColor: active ? colors.primary : colors.muted, borderColor: active ? colors.primary : colors.border }]}
                  onPress={() => setLanguage(l.code)}
                  activeOpacity={0.8}
                >
                  <Text style={{ fontSize: 15 }}>{l.flag}</Text>
                  <Text style={[styles.chipText, { color: active ? colors.primaryForeground : colors.foreground }]}>{l.label}</Text>
                  {active && <Feather name="check" size={13} color={colors.primaryForeground} />}
                </TouchableOpacity>
              );
            })}
          </View>
        </View>

        {/* Rest timer */}
        <View style={[styles.prefCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <View style={styles.prefHeader}>
            <View style={[styles.iconCircle, { backgroundColor: colors.primary + "20" }]}>
              <Feather name="clock" size={20} color={colors.primary} />
            </View>
            <View style={styles.actionText}>
              <Text style={[styles.actionTitle, { color: colors.foreground }]}>{t("settings.restTimer.title")}</Text>
              <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>{t("settings.restTimer.desc")}</Text>
            </View>
          </View>
          <View style={styles.chipWrap}>
            {REST_PRESETS.map((sec) => {
              const active = restSeconds === sec;
              return (
                <TouchableOpacity
                  key={sec}
                  style={[styles.restChip, { backgroundColor: active ? colors.primary : colors.muted, borderColor: active ? colors.primary : colors.border }]}
                  onPress={() => setRestSeconds(sec)}
                  activeOpacity={0.8}
                >
                  <Text style={[styles.chipText, { color: active ? colors.primaryForeground : colors.foreground }]}>{formatRestLabel(sec)}</Text>
                </TouchableOpacity>
              );
            })}
          </View>
        </View>

        {/* Backup section */}
        <Text style={[styles.sectionTitle, { color: colors.mutedForeground }]}>{t("settings.section.backup")}</Text>

        {/* Export */}
        <TouchableOpacity
          style={[styles.actionCard, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={handleExport}
          disabled={exporting || importing}
          activeOpacity={0.75}
        >
          <View style={[styles.iconCircle, { backgroundColor: colors.primary + "20" }]}>
            {exporting
              ? <ActivityIndicator size="small" color={colors.primary} />
              : <Feather name="upload" size={20} color={colors.primary} />}
          </View>
          <View style={styles.actionText}>
            <Text style={[styles.actionTitle, { color: colors.foreground }]}>{t("settings.export.title")}</Text>
            <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>
              {t("settings.export.desc")}
            </Text>
          </View>
          <Feather name="chevron-right" size={18} color={colors.mutedForeground} />
        </TouchableOpacity>

        {/* Export Excel */}
        <TouchableOpacity
          style={[styles.actionCard, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={handleExportExcel}
          disabled={exporting || exportingExcel || importing}
          activeOpacity={0.75}
        >
          <View style={[styles.iconCircle, { backgroundColor: "#21734620" }]}>
            {exportingExcel
              ? <ActivityIndicator size="small" color="#217346" />
              : <Feather name="grid" size={20} color="#217346" />}
          </View>
          <View style={styles.actionText}>
            <Text style={[styles.actionTitle, { color: colors.foreground }]}>{t("settings.exportExcel.title")}</Text>
            <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>
              {t("settings.exportExcel.desc")}
            </Text>
          </View>
          <Feather name="chevron-right" size={18} color={colors.mutedForeground} />
        </TouchableOpacity>

        {/* Import */}
        <TouchableOpacity
          style={[styles.actionCard, { backgroundColor: colors.card, borderColor: colors.border }]}
          onPress={handleImport}
          disabled={exporting || importing}
          activeOpacity={0.75}
        >
          <View style={[styles.iconCircle, { backgroundColor: "#3B82F620" }]}>
            {importing
              ? <ActivityIndicator size="small" color="#3B82F6" />
              : <Feather name="download" size={20} color="#3B82F6" />}
          </View>
          <View style={styles.actionText}>
            <Text style={[styles.actionTitle, { color: colors.foreground }]}>{t("settings.import.title")}</Text>
            <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>
              {t("settings.import.desc")}
            </Text>
          </View>
          <Feather name="chevron-right" size={18} color={colors.mutedForeground} />
        </TouchableOpacity>

        {/* Import confirmation */}
        {confirmImport && (
          <View style={[styles.confirmBox, { backgroundColor: "#3B82F615", borderColor: "#3B82F644" }]}>
            <Feather name="alert-circle" size={18} color="#3B82F6" />
            <View style={{ flex: 1 }}>
              <Text style={[styles.confirmTitle, { color: colors.foreground }]}>
                {t("settings.import.confirmTitle")}
              </Text>
              <Text style={[styles.confirmDesc, { color: colors.mutedForeground }]}>
                {t("settings.import.confirmDesc", { count: workouts.length })}
              </Text>
            </View>
            <View style={styles.confirmBtns}>
              <TouchableOpacity
                style={[styles.cancelBtn, { borderColor: colors.border }]}
                onPress={() => { setConfirmImport(false); pendingImportRef.current = null; }}
              >
                <Text style={[styles.cancelBtnText, { color: colors.foreground }]}>{t("common.cancel")}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.okBtn, { backgroundColor: "#3B82F6" }]}
                onPress={handleConfirmImport}
              >
                <Feather name="check" size={14} color="#fff" />
                <Text style={styles.okBtnText}>{t("common.confirm")}</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}

        {/* Status message */}
        {status && (
          <View style={[
            styles.statusBox,
            {
              backgroundColor: status.type === "success" ? colors.primary + "15" : "#EF444415",
              borderColor: status.type === "success" ? colors.primary + "44" : "#EF444444",
            },
          ]}>
            <Feather
              name={status.type === "success" ? "check-circle" : "alert-circle"}
              size={16}
              color={status.type === "success" ? colors.primary : "#EF4444"}
            />
            <Text style={[styles.statusText, { color: status.type === "success" ? colors.primary : "#EF4444" }]}>
              {status.message}
            </Text>
          </View>
        )}

        {/* Info note */}
        <View style={[styles.infoBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Feather name="info" size={14} color={colors.mutedForeground} />
          <Text style={[styles.infoText, { color: colors.mutedForeground }]}>
            {t("settings.info")}
          </Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 20, paddingBottom: 16, borderBottomWidth: 1 },
  title: { fontSize: 28, fontFamily: "Inter_700Bold" },
  sectionTitle: { fontSize: 11, fontFamily: "Inter_600SemiBold", letterSpacing: 1, marginTop: 8, marginBottom: 4 },
  statsCard: { borderRadius: 16, borderWidth: 1, padding: 16, marginBottom: 4 },
  sectionLabel: { fontSize: 12, fontFamily: "Inter_500Medium", marginBottom: 12 },
  statsRow: { flexDirection: "row", alignItems: "center" },
  statItem: { flex: 1, alignItems: "center", gap: 2 },
  statDivider: { width: 1, height: 32 },
  statValue: { fontSize: 24, fontFamily: "Inter_700Bold" },
  statLabel: { fontSize: 12, fontFamily: "Inter_400Regular" },
  actionCard: { flexDirection: "row", alignItems: "center", gap: 14, borderRadius: 16, borderWidth: 1, padding: 16 },
  iconCircle: { width: 44, height: 44, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  actionText: { flex: 1, gap: 3 },
  actionTitle: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  actionDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  prefCard: { borderRadius: 16, borderWidth: 1, padding: 16, gap: 14 },
  prefHeader: { flexDirection: "row", alignItems: "center", gap: 14 },
  chipWrap: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  langChip: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, paddingVertical: 9, borderRadius: 12, borderWidth: 1 },
  restChip: { paddingHorizontal: 14, paddingVertical: 9, borderRadius: 12, borderWidth: 1 },
  chipText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  confirmBox: { borderRadius: 14, borderWidth: 1, padding: 14, gap: 10 },
  confirmTitle: { fontSize: 14, fontFamily: "Inter_600SemiBold", marginBottom: 2 },
  confirmDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  confirmBtns: { flexDirection: "row", gap: 8, marginTop: 4 },
  cancelBtn: { flex: 1, borderWidth: 1, borderRadius: 8, paddingVertical: 9, alignItems: "center" },
  cancelBtnText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  okBtn: { flex: 1, flexDirection: "row", gap: 6, borderRadius: 8, paddingVertical: 9, alignItems: "center", justifyContent: "center" },
  okBtnText: { fontSize: 13, fontFamily: "Inter_600SemiBold", color: "#fff" },
  statusBox: { flexDirection: "row", gap: 10, borderRadius: 12, borderWidth: 1, padding: 14, alignItems: "flex-start" },
  statusText: { flex: 1, fontSize: 13, fontFamily: "Inter_500Medium", lineHeight: 20 },
  infoBox: { flexDirection: "row", gap: 10, borderRadius: 12, borderWidth: 1, padding: 14, alignItems: "flex-start" },
  infoText: { flex: 1, fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
});
