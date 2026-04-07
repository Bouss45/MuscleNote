import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { useColors } from "@/hooks/useColors";

// ─── Types ────────────────────────────────────────────────────────────────────
interface Supplement {
  id: string;
  name: string;
  dosage: string;
  unit: string;
  reminderEnabled: boolean;
  reminderHour: number;
  reminderMinute: number;
  notifId?: string;
}

interface TakenRecord {
  [supplementId: string]: string; // ISO date string "YYYY-MM-DD"
}

const STORAGE_KEY = "@gymtracker_supplements";
const TAKEN_KEY = "@gymtracker_supplements_taken";
const UNITS = ["g", "mg", "ml", "caps", "cp", "dose", "ui"];

function generateId() {
  return Date.now().toString() + Math.random().toString(36).substr(2, 6);
}

function todayStr() {
  return new Date().toISOString().slice(0, 10);
}

function pad2(n: number) {
  return String(n).padStart(2, "0");
}

// ─── Notification helpers (mobile only) ───────────────────────────────────────
async function requestNotifPermission(): Promise<boolean> {
  if (Platform.OS === "web") return false;
  try {
    const Notifications = require("expo-notifications");
    const { status: existing } = await Notifications.getPermissionsAsync();
    if (existing === "granted") return true;
    const { status } = await Notifications.requestPermissionsAsync();
    return status === "granted";
  } catch {
    return false;
  }
}

async function scheduleSupplementNotif(sup: Supplement): Promise<string | undefined> {
  if (Platform.OS === "web" || !sup.reminderEnabled) return undefined;
  try {
    const Notifications = require("expo-notifications");
    const granted = await requestNotifPermission();
    if (!granted) return undefined;
    const id = await Notifications.scheduleNotificationAsync({
      content: {
        title: `💊 ${sup.name}`,
        body: `N'oublie pas ta dose${sup.dosage ? ` (${sup.dosage} ${sup.unit})` : ""} !`,
        sound: true,
      },
      trigger: {
        type: "daily",
        hour: sup.reminderHour,
        minute: sup.reminderMinute,
      },
    });
    return id;
  } catch {
    return undefined;
  }
}

async function cancelNotif(notifId?: string) {
  if (!notifId || Platform.OS === "web") return;
  try {
    const Notifications = require("expo-notifications");
    await Notifications.cancelScheduledNotificationAsync(notifId);
  } catch {}
}

// ─── Component ───────────────────────────────────────────────────────────────
export default function SupplementsScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const topPad = Platform.OS === "web" ? 67 : insets.top;

  const [supplements, setSupplements] = useState<Supplement[]>([]);
  const [taken, setTaken] = useState<TakenRecord>({});
  const [loading, setLoading] = useState(true);

  // Add form state
  const [showForm, setShowForm] = useState(false);
  const [formName, setFormName] = useState("");
  const [formDosage, setFormDosage] = useState("");
  const [formUnit, setFormUnit] = useState("g");
  const [formHour, setFormHour] = useState(8);
  const [formMinute, setFormMinute] = useState(0);
  const [formReminder, setFormReminder] = useState(true);

  // Edit state
  const [editingId, setEditingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // Load from storage
  useEffect(() => {
    async function load() {
      try {
        const [supsRaw, takenRaw] = await Promise.all([
          AsyncStorage.getItem(STORAGE_KEY),
          AsyncStorage.getItem(TAKEN_KEY),
        ]);
        if (supsRaw) setSupplements(JSON.parse(supsRaw));
        if (takenRaw) setTaken(JSON.parse(takenRaw));
      } catch {}
      setLoading(false);
    }
    load();
  }, []);

  async function saveSups(list: Supplement[]) {
    setSupplements(list);
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  }

  async function saveTaken(record: TakenRecord) {
    setTaken(record);
    await AsyncStorage.setItem(TAKEN_KEY, JSON.stringify(record));
  }

  // ── Add supplement ──
  async function handleAdd() {
    if (!formName.trim()) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const newSup: Supplement = {
      id: generateId(),
      name: formName.trim(),
      dosage: formDosage.trim(),
      unit: formUnit,
      reminderEnabled: formReminder,
      reminderHour: formHour,
      reminderMinute: formMinute,
    };
    if (formReminder) {
      newSup.notifId = await scheduleSupplementNotif(newSup);
    }
    await saveSups([...supplements, newSup]);
    setShowForm(false);
    resetForm();
  }

  function resetForm() {
    setFormName(""); setFormDosage(""); setFormUnit("g");
    setFormHour(8); setFormMinute(0); setFormReminder(true);
  }

  // ── Toggle reminder ──
  async function toggleReminder(sup: Supplement) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    let updated: Supplement;
    if (sup.reminderEnabled) {
      await cancelNotif(sup.notifId);
      updated = { ...sup, reminderEnabled: false, notifId: undefined };
    } else {
      const newSup = { ...sup, reminderEnabled: true };
      newSup.notifId = await scheduleSupplementNotif(newSup);
      updated = newSup;
    }
    await saveSups(supplements.map((s) => s.id === sup.id ? updated : s));
  }

  // ── Mark taken ──
  async function toggleTaken(supId: string) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const today = todayStr();
    const newTaken = { ...taken };
    if (newTaken[supId] === today) {
      delete newTaken[supId];
    } else {
      newTaken[supId] = today;
    }
    await saveTaken(newTaken);
  }

  // ── Delete ──
  async function handleDelete(sup: Supplement) {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    await cancelNotif(sup.notifId);
    await saveSups(supplements.filter((s) => s.id !== sup.id));
    setDeletingId(null);
  }

  if (loading) {
    return (
      <View style={[styles.container, { backgroundColor: colors.background, justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View style={[styles.header, { paddingTop: topPad + 16, borderBottomColor: colors.border }]}>
        <Text style={[styles.title, { color: colors.foreground }]}>Compléments</Text>
        <TouchableOpacity
          onPress={() => { setShowForm(!showForm); setEditingId(null); setDeletingId(null); }}
          style={[styles.addBtn, { backgroundColor: colors.primary }]}
        >
          <Feather name={showForm ? "x" : "plus"} size={18} color={colors.primaryForeground} />
          <Text style={[styles.addBtnText, { color: colors.primaryForeground }]}>
            {showForm ? "Annuler" : "Ajouter"}
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: 100, gap: 12 }}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* ── Add form ── */}
        {showForm && (
          <View style={[styles.formCard, { backgroundColor: colors.card, borderColor: colors.primary + "55" }]}>
            <Text style={[styles.formTitle, { color: colors.foreground }]}>Nouveau complément</Text>

            <Text style={[styles.inputLabel, { color: colors.mutedForeground }]}>Nom</Text>
            <TextInput
              style={[styles.input, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
              placeholder="Ex : Créatine, Whey, Oméga-3..."
              placeholderTextColor={colors.mutedForeground}
              value={formName}
              onChangeText={setFormName}
              autoFocus
            />

            <Text style={[styles.inputLabel, { color: colors.mutedForeground }]}>Dosage</Text>
            <View style={styles.dosageRow}>
              <TextInput
                style={[styles.input, { flex: 1, color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                placeholder="Ex : 5"
                placeholderTextColor={colors.mutedForeground}
                value={formDosage}
                onChangeText={setFormDosage}
                keyboardType="numeric"
              />
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginLeft: 8 }}>
                <View style={{ flexDirection: "row", gap: 6 }}>
                  {UNITS.map((u) => (
                    <TouchableOpacity
                      key={u}
                      onPress={() => setFormUnit(u)}
                      style={[styles.unitChip, { backgroundColor: formUnit === u ? colors.primary : colors.card, borderColor: formUnit === u ? colors.primary : colors.border }]}
                    >
                      <Text style={[styles.unitChipText, { color: formUnit === u ? colors.primaryForeground : colors.foreground }]}>{u}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </ScrollView>
            </View>

            <View style={styles.reminderRow}>
              <View style={styles.reminderLeft}>
                <Feather name="bell" size={15} color={colors.mutedForeground} />
                <Text style={[styles.inputLabel, { color: colors.foreground, marginBottom: 0 }]}>Rappel quotidien</Text>
              </View>
              <Switch
                value={formReminder}
                onValueChange={setFormReminder}
                trackColor={{ true: colors.primary, false: colors.muted }}
                thumbColor="#fff"
              />
            </View>

            {formReminder && (
              <View style={[styles.timeRow, { backgroundColor: colors.input, borderColor: colors.border }]}>
                <Feather name="clock" size={14} color={colors.mutedForeground} />
                <Text style={[styles.timeLabel, { color: colors.mutedForeground }]}>Heure :</Text>
                <TouchableOpacity onPress={() => setFormHour((formHour + 23) % 24)} style={styles.timeArrow}>
                  <Feather name="chevron-left" size={16} color={colors.foreground} />
                </TouchableOpacity>
                <Text style={[styles.timeValue, { color: colors.foreground }]}>{pad2(formHour)}:{pad2(formMinute)}</Text>
                <TouchableOpacity onPress={() => setFormHour((formHour + 1) % 24)} style={styles.timeArrow}>
                  <Feather name="chevron-right" size={16} color={colors.foreground} />
                </TouchableOpacity>
                <TouchableOpacity onPress={() => setFormMinute((formMinute + 45) % 60)} style={styles.timeArrow}>
                  <Feather name="chevron-left" size={16} color={colors.foreground} />
                </TouchableOpacity>
                <Text style={[styles.timeLabel, { color: colors.mutedForeground }]}>min</Text>
                <TouchableOpacity onPress={() => setFormMinute((formMinute + 15) % 60)} style={styles.timeArrow}>
                  <Feather name="chevron-right" size={16} color={colors.foreground} />
                </TouchableOpacity>
              </View>
            )}

            <TouchableOpacity
              style={[styles.saveBtn, { backgroundColor: !formName.trim() ? colors.muted : colors.primary }]}
              onPress={handleAdd}
              disabled={!formName.trim()}
            >
              <Feather name="check" size={16} color={colors.primaryForeground} />
              <Text style={[styles.saveBtnText, { color: colors.primaryForeground }]}>Enregistrer</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* ── Empty state ── */}
        {supplements.length === 0 && !showForm && (
          <View style={styles.emptyState}>
            <View style={[styles.emptyIcon, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={{ fontSize: 32 }}>💊</Text>
            </View>
            <Text style={[styles.emptyTitle, { color: colors.foreground }]}>Aucun complément</Text>
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Ajoute tes compléments alimentaires et configure des rappels pour ne jamais en oublier
            </Text>
          </View>
        )}

        {/* ── Supplement cards ── */}
        {supplements.map((sup) => {
          const isTaken = taken[sup.id] === todayStr();
          const isDeleting = deletingId === sup.id;

          return (
            <View key={sup.id}>
              <View style={[
                styles.supCard,
                {
                  backgroundColor: isTaken ? colors.primary + "12" : colors.card,
                  borderColor: isTaken ? colors.primary + "55" : colors.border,
                },
              ]}>
                {/* Top row */}
                <View style={styles.supTop}>
                  {/* Taken button */}
                  <TouchableOpacity
                    onPress={() => toggleTaken(sup.id)}
                    style={[
                      styles.takenCircle,
                      {
                        backgroundColor: isTaken ? colors.primary : "transparent",
                        borderColor: isTaken ? colors.primary : colors.mutedForeground,
                      },
                    ]}
                  >
                    {isTaken && <Feather name="check" size={14} color="#fff" />}
                  </TouchableOpacity>

                  {/* Info */}
                  <View style={styles.supInfo}>
                    <Text style={[styles.supName, { color: colors.foreground }]}>{sup.name}</Text>
                    {(sup.dosage || sup.unit) && (
                      <Text style={[styles.supDosage, { color: colors.mutedForeground }]}>
                        {sup.dosage} {sup.unit}
                      </Text>
                    )}
                  </View>

                  {/* Actions */}
                  <View style={styles.supActions}>
                    <TouchableOpacity
                      onPress={() => { setDeletingId(isDeleting ? null : sup.id); setEditingId(null); }}
                      style={[styles.iconBtn, { borderColor: colors.border }]}
                      hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
                    >
                      <Feather name="trash-2" size={13} color={colors.destructive} />
                    </TouchableOpacity>
                  </View>
                </View>

                {/* Reminder row */}
                <View style={styles.supReminder}>
                  <View style={styles.supReminderLeft}>
                    <Feather
                      name={sup.reminderEnabled ? "bell" : "bell-off"}
                      size={13}
                      color={sup.reminderEnabled ? colors.primary : colors.mutedForeground}
                    />
                    <Text style={[styles.supReminderText, { color: sup.reminderEnabled ? colors.primary : colors.mutedForeground }]}>
                      {sup.reminderEnabled
                        ? `Rappel à ${pad2(sup.reminderHour)}:${pad2(sup.reminderMinute)}`
                        : "Pas de rappel"}
                    </Text>
                  </View>
                  <Switch
                    value={sup.reminderEnabled}
                    onValueChange={() => toggleReminder(sup)}
                    trackColor={{ true: colors.primary, false: colors.muted }}
                    thumbColor="#fff"
                    style={{ transform: [{ scale: 0.8 }] }}
                  />
                </View>

                {/* Taken status */}
                <View style={[styles.takenBadge, { backgroundColor: isTaken ? colors.primary + "20" : colors.muted + "44" }]}>
                  <Text style={[styles.takenText, { color: isTaken ? colors.primary : colors.mutedForeground }]}>
                    {isTaken ? "✓ Pris aujourd'hui" : "Non pris aujourd'hui"}
                  </Text>
                </View>
              </View>

              {/* Delete confirm */}
              {isDeleting && (
                <View style={[styles.deleteBox, { backgroundColor: colors.destructive + "15", borderColor: colors.destructive + "44" }]}>
                  <Text style={[styles.deleteText, { color: colors.foreground }]}>
                    Supprimer « {sup.name} » ?
                  </Text>
                  <View style={styles.deleteBtns}>
                    <TouchableOpacity
                      style={[styles.deleteCancelBtn, { borderColor: colors.border }]}
                      onPress={() => setDeletingId(null)}
                    >
                      <Text style={[styles.deleteCancelText, { color: colors.foreground }]}>Annuler</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      style={[styles.deleteOkBtn, { backgroundColor: colors.destructive }]}
                      onPress={() => handleDelete(sup)}
                    >
                      <Feather name="trash-2" size={13} color="#fff" />
                      <Text style={styles.deleteOkText}>Supprimer</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}
            </View>
          );
        })}

        {/* Notification note (mobile only) */}
        {supplements.some((s) => s.reminderEnabled) && Platform.OS !== "web" && (
          <View style={[styles.infoBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Feather name="info" size={13} color={colors.mutedForeground} />
            <Text style={[styles.infoText, { color: colors.mutedForeground }]}>
              Les rappels sont des notifications locales. L'app doit avoir la permission d'envoyer des notifications.
            </Text>
          </View>
        )}
        {supplements.some((s) => s.reminderEnabled) && Platform.OS === "web" && (
          <View style={[styles.infoBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Feather name="info" size={13} color={colors.mutedForeground} />
            <Text style={[styles.infoText, { color: colors.mutedForeground }]}>
              Les rappels par notification sont disponibles uniquement sur l'application mobile (Android / iOS).
            </Text>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    paddingHorizontal: 20, paddingBottom: 14, borderBottomWidth: 1,
    flexDirection: "row", alignItems: "center", justifyContent: "space-between",
  },
  title: { fontSize: 28, fontFamily: "Inter_700Bold" },
  addBtn: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, paddingVertical: 9, borderRadius: 20 },
  addBtnText: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  // Form
  formCard: { borderRadius: 16, borderWidth: 1.5, padding: 16, gap: 10 },
  formTitle: { fontSize: 16, fontFamily: "Inter_700Bold", marginBottom: 4 },
  inputLabel: { fontSize: 12, fontFamily: "Inter_500Medium", marginBottom: 4 },
  input: { borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 15, fontFamily: "Inter_400Regular" },
  dosageRow: { flexDirection: "row", alignItems: "center" },
  unitChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, borderWidth: 1 },
  unitChipText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  reminderRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: 4 },
  reminderLeft: { flexDirection: "row", alignItems: "center", gap: 8 },
  timeRow: { flexDirection: "row", alignItems: "center", gap: 6, borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10 },
  timeLabel: { fontSize: 13, fontFamily: "Inter_400Regular" },
  timeValue: { fontSize: 18, fontFamily: "Inter_700Bold", minWidth: 52, textAlign: "center" },
  timeArrow: { padding: 2 },
  saveBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, borderRadius: 12, padding: 14, marginTop: 4 },
  saveBtnText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  // Empty
  emptyState: { alignItems: "center", paddingTop: 60, gap: 14 },
  emptyIcon: { width: 72, height: 72, borderRadius: 24, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  emptyTitle: { fontSize: 20, fontFamily: "Inter_700Bold" },
  emptyText: { fontSize: 14, fontFamily: "Inter_400Regular", textAlign: "center", lineHeight: 22, paddingHorizontal: 32 },
  // Supplement card
  supCard: { borderRadius: 16, borderWidth: 1, padding: 14, gap: 10 },
  supTop: { flexDirection: "row", alignItems: "center", gap: 12 },
  takenCircle: { width: 28, height: 28, borderRadius: 14, borderWidth: 2, alignItems: "center", justifyContent: "center" },
  supInfo: { flex: 1 },
  supName: { fontSize: 16, fontFamily: "Inter_600SemiBold" },
  supDosage: { fontSize: 13, fontFamily: "Inter_400Regular", marginTop: 2 },
  supActions: { flexDirection: "row", gap: 6 },
  iconBtn: { width: 30, height: 30, borderRadius: 8, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  supReminder: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  supReminderLeft: { flexDirection: "row", alignItems: "center", gap: 6 },
  supReminderText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  takenBadge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 5, alignSelf: "flex-start" },
  takenText: { fontSize: 12, fontFamily: "Inter_500Medium" },
  // Delete
  deleteBox: { borderWidth: 1, borderTopWidth: 0, borderBottomLeftRadius: 12, borderBottomRightRadius: 12, padding: 12, gap: 10 },
  deleteText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  deleteBtns: { flexDirection: "row", gap: 8 },
  deleteCancelBtn: { flex: 1, borderWidth: 1, borderRadius: 8, paddingVertical: 9, alignItems: "center" },
  deleteCancelText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  deleteOkBtn: { flex: 1, flexDirection: "row", gap: 6, borderRadius: 8, paddingVertical: 9, alignItems: "center", justifyContent: "center" },
  deleteOkText: { fontSize: 13, fontFamily: "Inter_600SemiBold", color: "#fff" },
  // Info
  infoBox: { flexDirection: "row", gap: 10, borderRadius: 12, borderWidth: 1, padding: 14, alignItems: "flex-start" },
  infoText: { flex: 1, fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
});
