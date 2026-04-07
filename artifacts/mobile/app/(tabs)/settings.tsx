import { Feather } from "@expo/vector-icons";
import React, { useRef, useState } from "react";
import {
  ActivityIndicator,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useColors } from "@/hooks/useColors";
import { useWorkout } from "@/context/WorkoutContext";

// Keys stored in AsyncStorage that we export/import
const EXPORT_KEYS = [
  "@gymtracker_workouts",
  "@gymtracker_exercises",
  "@gymtracker_body_weights",
];

type Status = { type: "success" | "error"; message: string } | null;

export default function SettingsScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { workouts, exercises, isCustomExercise } = useWorkout();
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [status, setStatus] = useState<Status>(null);
  const [confirmImport, setConfirmImport] = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const pendingImportRef = useRef<string | null>(null);

  const topPad = Platform.OS === "web" ? 67 : insets.top;

  // ─── Export ───────────────────────────────────────────────────────────────
  async function handleExport() {
    setExporting(true);
    setStatus(null);
    try {
      // Gather all keys
      const pairs = await AsyncStorage.multiGet(EXPORT_KEYS);
      const data: Record<string, unknown> = { version: 1, exportedAt: new Date().toISOString() };
      for (const [key, value] of pairs) {
        if (value !== null) {
          try { data[key] = JSON.parse(value); } catch { data[key] = value; }
        }
      }
      const json = JSON.stringify(data, null, 2);
      const filename = `gymtracker_backup_${new Date().toISOString().slice(0, 10)}.json`;

      if (Platform.OS === "web") {
        // Web: trigger download via Blob
        const blob = new Blob([json], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
        setStatus({ type: "success", message: "Fichier téléchargé avec succès !" });
      } else {
        // Mobile: write to temp file and share
        const FileSystem = require("expo-file-system");
        const Sharing = require("expo-sharing");
        const path = FileSystem.cacheDirectory + filename;
        await FileSystem.writeAsStringAsync(path, json, { encoding: "utf8" });
        const canShare = await Sharing.isAvailableAsync();
        if (canShare) {
          await Sharing.shareAsync(path, { mimeType: "application/json", dialogTitle: "Exporter mes données GymTracker" });
          setStatus({ type: "success", message: "Fichier partagé avec succès !" });
        } else {
          setStatus({ type: "error", message: "Le partage de fichiers n'est pas disponible sur cet appareil." });
        }
      }
    } catch (e) {
      setStatus({ type: "error", message: "Erreur lors de l'export : " + String(e) });
    } finally {
      setExporting(false);
    }
  }

  // ─── Import ───────────────────────────────────────────────────────────────
  async function processImport(json: string) {
    try {
      const data = JSON.parse(json);
      if (!data || typeof data !== "object") throw new Error("Format invalide");

      const pairs: [string, string][] = [];
      for (const key of EXPORT_KEYS) {
        if (data[key] !== undefined) {
          pairs.push([key, JSON.stringify(data[key])]);
        }
      }
      if (pairs.length === 0) throw new Error("Aucune donnée GymTracker trouvée dans ce fichier");

      await AsyncStorage.multiSet(pairs);
      setStatus({ type: "success", message: `Import réussi ! Redémarre l'application pour voir tes données.` });
    } catch (e) {
      setStatus({ type: "error", message: "Erreur lors de l'import : " + String(e) });
    }
  }

  async function handleImport() {
    setStatus(null);
    setConfirmImport(false);

    if (Platform.OS === "web") {
      // Web: trigger hidden file input
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
        fileInputRef.current.click();
      }
      return;
    }

    // Mobile: use document picker
    setImporting(true);
    try {
      const DocumentPicker = require("expo-document-picker");
      const result = await DocumentPicker.getDocumentAsync({
        type: ["application/json", "text/plain", "*/*"],
        copyToCacheDirectory: true,
      });
      if (result.canceled || !result.assets?.[0]) {
        setImporting(false);
        return;
      }
      const FileSystem = require("expo-file-system");
      const json = await FileSystem.readAsStringAsync(result.assets[0].uri, { encoding: "utf8" });
      pendingImportRef.current = json;
      setImporting(false);
      setConfirmImport(true);
    } catch (e) {
      setStatus({ type: "error", message: "Impossible de lire le fichier : " + String(e) });
      setImporting(false);
    }
  }

  // Web file input change handler
  async function handleWebFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setImporting(true);
    try {
      const text = await file.text();
      pendingImportRef.current = text;
      setImporting(false);
      setConfirmImport(true);
    } catch {
      setStatus({ type: "error", message: "Impossible de lire le fichier sélectionné." });
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
      {/* Hidden file input for web import */}
      {Platform.OS === "web" && (
        <input
          ref={fileInputRef}
          type="file"
          accept=".json,application/json"
          style={{ display: "none" }}
          onChange={handleWebFileChange as any}
        />
      )}

      {/* Header */}
      <View style={[styles.header, { paddingTop: topPad + 16, borderBottomColor: colors.border }]}>
        <Text style={[styles.title, { color: colors.foreground }]}>Paramètres</Text>
      </View>

      <ScrollView contentContainerStyle={{ padding: 20, gap: 12 }} showsVerticalScrollIndicator={false}>

        {/* Stats summary */}
        <View style={[styles.statsCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
          <Text style={[styles.sectionLabel, { color: colors.mutedForeground }]}>Mes données</Text>
          <View style={styles.statsRow}>
            <View style={styles.statItem}>
              <Text style={[styles.statValue, { color: colors.foreground }]}>{workouts.length}</Text>
              <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>séances</Text>
            </View>
            <View style={[styles.statDivider, { backgroundColor: colors.border }]} />
            <View style={styles.statItem}>
              <Text style={[styles.statValue, { color: colors.foreground }]}>
                {exercises.filter((e) => isCustomExercise(e.id)).length}
              </Text>
              <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>exercices perso</Text>
            </View>
          </View>
        </View>

        {/* Backup section */}
        <Text style={[styles.sectionTitle, { color: colors.mutedForeground }]}>SAUVEGARDE</Text>

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
            <Text style={[styles.actionTitle, { color: colors.foreground }]}>Exporter mes données</Text>
            <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>
              Télécharge un fichier JSON avec toutes tes séances, exercices et poids de corps
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
            <Text style={[styles.actionTitle, { color: colors.foreground }]}>Importer des données</Text>
            <Text style={[styles.actionDesc, { color: colors.mutedForeground }]}>
              Restaure tes données depuis un fichier exporté — utile lors d'un changement de téléphone
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
                Écraser les données actuelles ?
              </Text>
              <Text style={[styles.confirmDesc, { color: colors.mutedForeground }]}>
                Tes {workouts.length} séances actuelles seront remplacées par celles du fichier importé.
              </Text>
            </View>
            <View style={styles.confirmBtns}>
              <TouchableOpacity
                style={[styles.cancelBtn, { borderColor: colors.border }]}
                onPress={() => { setConfirmImport(false); pendingImportRef.current = null; }}
              >
                <Text style={[styles.cancelBtnText, { color: colors.foreground }]}>Annuler</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.okBtn, { backgroundColor: "#3B82F6" }]}
                onPress={handleConfirmImport}
              >
                <Feather name="check" size={14} color="#fff" />
                <Text style={styles.okBtnText}>Confirmer</Text>
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
            Le fichier exporté contient toutes tes séances, tes exercices personnalisés et l'historique de poids de corps. Garde-le en lieu sûr.
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
