import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useEffect, useState } from "react";
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
interface Reminder {
  id: string;
  hour: number;
  minute: number;
  notifId?: string;
}

interface Supplement {
  id: string;
  name: string;
  dosage: string;
  unit: string;
  reminderEnabled: boolean;
  reminders: Reminder[];
}

interface TakenRecord {
  [supplementId: string]: string;
}

interface DailyTracker {
  calorieGoal: number;
  waterGoal: number;
  caloriesLog: { [date: string]: number };
  waterLog: { [date: string]: number };
}

const STORAGE_KEY = "@gymtracker_supplements";
const TAKEN_KEY = "@gymtracker_supplements_taken";
const TRACKER_KEY = "@gymtracker_daily_tracker";
const UNITS = ["g", "mg", "ml", "caps", "cp", "dose", "ui"];
const DEFAULT_TRACKER: DailyTracker = {
  calorieGoal: 2000,
  waterGoal: 8,
  caloriesLog: {},
  waterLog: {},
};

function genId() {
  return Date.now().toString() + Math.random().toString(36).substr(2, 6);
}
function todayStr() {
  return new Date().toISOString().slice(0, 10);
}
function pad2(n: number) {
  return String(n).padStart(2, "0");
}

// ─── Notification helpers ─────────────────────────────────────────────────────
async function requestNotifPermission(): Promise<boolean> {
  if (Platform.OS === "web") return false;
  try {
    const N = require("expo-notifications");
    const { status: existing } = await N.getPermissionsAsync();
    if (existing === "granted") return true;
    const { status } = await N.requestPermissionsAsync();
    return status === "granted";
  } catch { return false; }
}

async function scheduleReminder(sup: Supplement, r: Reminder): Promise<string | undefined> {
  if (Platform.OS === "web" || !sup.reminderEnabled) return undefined;
  try {
    const N = require("expo-notifications");
    const granted = await requestNotifPermission();
    if (!granted) return undefined;
    return await N.scheduleNotificationAsync({
      content: {
        title: `💊 ${sup.name}`,
        body: `N'oublie pas ta dose${sup.dosage ? ` (${sup.dosage} ${sup.unit})` : ""} !`,
        sound: true,
      },
      trigger: { type: "daily", hour: r.hour, minute: r.minute },
    });
  } catch { return undefined; }
}

async function cancelNotif(notifId?: string) {
  if (!notifId || Platform.OS === "web") return;
  try { await require("expo-notifications").cancelScheduledNotificationAsync(notifId); } catch {}
}

async function cancelAllReminders(reminders: Reminder[]) {
  for (const r of reminders) await cancelNotif(r.notifId);
}

async function scheduleAllReminders(sup: Supplement): Promise<Reminder[]> {
  return Promise.all(
    sup.reminders.map(async (r) => ({ ...r, notifId: await scheduleReminder(sup, r) }))
  );
}

function migrateSup(raw: any): Supplement {
  if (raw.reminders) return raw as Supplement;
  const reminder: Reminder = { id: genId(), hour: raw.reminderHour ?? 8, minute: raw.reminderMinute ?? 0, notifId: raw.notifId };
  return { id: raw.id, name: raw.name, dosage: raw.dosage ?? "", unit: raw.unit ?? "g", reminderEnabled: raw.reminderEnabled ?? false, reminders: raw.reminderEnabled ? [reminder] : [] };
}

// ─── TimePicker sub-component ─────────────────────────────────────────────────
function TimePicker({ hour, minute, onHour, onMinute, onRemove, showRemove }: {
  hour: number; minute: number;
  onHour: (h: number) => void; onMinute: (m: number) => void;
  onRemove?: () => void; showRemove: boolean;
}) {
  const colors = useColors();
  return (
    <View style={[tpStyles.row, { backgroundColor: colors.input, borderColor: colors.border }]}>
      <Feather name="clock" size={13} color={colors.mutedForeground} />
      <TouchableOpacity onPress={() => onHour((hour + 23) % 24)} style={tpStyles.arrow}><Feather name="chevron-left" size={15} color={colors.foreground} /></TouchableOpacity>
      <Text style={[tpStyles.val, { color: colors.foreground }]}>{pad2(hour)}</Text>
      <TouchableOpacity onPress={() => onHour((hour + 1) % 24)} style={tpStyles.arrow}><Feather name="chevron-right" size={15} color={colors.foreground} /></TouchableOpacity>
      <Text style={[tpStyles.sep, { color: colors.mutedForeground }]}>:</Text>
      <TouchableOpacity onPress={() => onMinute((minute + 45) % 60)} style={tpStyles.arrow}><Feather name="chevron-left" size={15} color={colors.foreground} /></TouchableOpacity>
      <Text style={[tpStyles.val, { color: colors.foreground }]}>{pad2(minute)}</Text>
      <TouchableOpacity onPress={() => onMinute((minute + 15) % 60)} style={tpStyles.arrow}><Feather name="chevron-right" size={15} color={colors.foreground} /></TouchableOpacity>
      <View style={{ flex: 1 }} />
      {showRemove && onRemove && (
        <TouchableOpacity onPress={onRemove} style={tpStyles.remove}><Feather name="x" size={14} color={colors.destructive} /></TouchableOpacity>
      )}
    </View>
  );
}
const tpStyles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: 4, borderRadius: 10, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 8 },
  arrow: { padding: 2 },
  val: { fontSize: 17, fontFamily: "Inter_700Bold", minWidth: 26, textAlign: "center" },
  sep: { fontSize: 17, fontFamily: "Inter_700Bold" },
  remove: { padding: 4 },
});

// ─── Progress bar ─────────────────────────────────────────────────────────────
function ProgressBar({ value, max, color }: { value: number; max: number; color: string }) {
  const pct = Math.min(1, max > 0 ? value / max : 0);
  return (
    <View style={pbStyles.track}>
      <View style={[pbStyles.fill, { width: `${Math.round(pct * 100)}%` as any, backgroundColor: color }]} />
    </View>
  );
}
const pbStyles = StyleSheet.create({
  track: { height: 6, borderRadius: 3, backgroundColor: "#ffffff18", overflow: "hidden", marginTop: 8 },
  fill: { height: 6, borderRadius: 3 },
});

// ─── Main component ───────────────────────────────────────────────────────────
export default function NutritionScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const topPad = Platform.OS === "web" ? 67 : insets.top;

  // ── Supplements ──
  const [supplements, setSupplements] = useState<Supplement[]>([]);
  const [taken, setTaken] = useState<TakenRecord>({});
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [formName, setFormName] = useState("");
  const [formDosage, setFormDosage] = useState("");
  const [formUnit, setFormUnit] = useState("g");
  const [formReminder, setFormReminder] = useState(true);
  const [formReminders, setFormReminders] = useState<Array<{ id: string; hour: number; minute: number }>>([{ id: genId(), hour: 8, minute: 0 }]);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [expandedRemId, setExpandedRemId] = useState<string | null>(null);

  // ── Daily tracker ──
  const [tracker, setTracker] = useState<DailyTracker>(DEFAULT_TRACKER);
  const [editCalGoal, setEditCalGoal] = useState(false);
  const [editWaterGoal, setEditWaterGoal] = useState(false);
  const [calGoalInput, setCalGoalInput] = useState("");
  const [waterGoalInput, setWaterGoalInput] = useState("");
  const [calInput, setCalInput] = useState("");

  const today = todayStr();
  const todayCalories = tracker.caloriesLog[today] ?? 0;
  const todayWater = tracker.waterLog[today] ?? 0;

  useEffect(() => {
    async function load() {
      try {
        const [supsRaw, takenRaw, trackerRaw] = await Promise.all([
          AsyncStorage.getItem(STORAGE_KEY),
          AsyncStorage.getItem(TAKEN_KEY),
          AsyncStorage.getItem(TRACKER_KEY),
        ]);
        if (supsRaw) setSupplements((JSON.parse(supsRaw) as any[]).map(migrateSup));
        if (takenRaw) setTaken(JSON.parse(takenRaw));
        if (trackerRaw) setTracker({ ...DEFAULT_TRACKER, ...JSON.parse(trackerRaw) });
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
  async function saveTracker(t: DailyTracker) {
    setTracker(t);
    await AsyncStorage.setItem(TRACKER_KEY, JSON.stringify(t));
  }

  // ── Calories ──
  async function addCalories(amount: number) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const updated = { ...tracker, caloriesLog: { ...tracker.caloriesLog, [today]: Math.max(0, todayCalories + amount) } };
    await saveTracker(updated);
  }
  async function setCaloriesToday(val: number) {
    const updated = { ...tracker, caloriesLog: { ...tracker.caloriesLog, [today]: Math.max(0, val) } };
    await saveTracker(updated);
  }
  async function saveCalGoal() {
    const v = parseInt(calGoalInput);
    if (!isNaN(v) && v > 0) await saveTracker({ ...tracker, calorieGoal: v });
    setEditCalGoal(false);
  }

  // ── Water ──
  async function addWater(delta: number) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const updated = { ...tracker, waterLog: { ...tracker.waterLog, [today]: Math.max(0, todayWater + delta) } };
    await saveTracker(updated);
  }
  async function saveWaterGoal() {
    const v = parseInt(waterGoalInput);
    if (!isNaN(v) && v > 0) await saveTracker({ ...tracker, waterGoal: v });
    setEditWaterGoal(false);
  }

  // ── Supplement form helpers ──
  function addFormReminder() {
    const last = formReminders[formReminders.length - 1];
    setFormReminders((prev) => [...prev, { id: genId(), hour: ((last?.hour ?? 8) + 2) % 24, minute: last?.minute ?? 0 }]);
  }
  function removeFormReminder(id: string) { setFormReminders((prev) => prev.filter((r) => r.id !== id)); }
  function updateFormReminder(id: string, field: "hour" | "minute", val: number) {
    setFormReminders((prev) => prev.map((r) => r.id === id ? { ...r, [field]: val } : r));
  }
  function resetForm() {
    setFormName(""); setFormDosage(""); setFormUnit("g");
    setFormReminder(true); setFormReminders([{ id: genId(), hour: 8, minute: 0 }]);
  }

  async function handleAdd() {
    if (!formName.trim()) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const base: Supplement = {
      id: genId(), name: formName.trim(), dosage: formDosage.trim(), unit: formUnit,
      reminderEnabled: formReminder,
      reminders: formReminder ? formReminders.map((r) => ({ ...r, notifId: undefined })) : [],
    };
    const withNotifs = { ...base, reminders: await scheduleAllReminders(base) };
    await saveSups([...supplements, withNotifs]);
    setShowForm(false); resetForm();
  }

  async function toggleMasterReminder(sup: Supplement) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    if (sup.reminderEnabled) {
      await cancelAllReminders(sup.reminders);
      await saveSups(supplements.map((s) =>
        s.id === sup.id ? { ...s, reminderEnabled: false, reminders: s.reminders.map((r) => ({ ...r, notifId: undefined })) } : s
      ));
    } else {
      const updated: Supplement = { ...sup, reminderEnabled: true };
      const reminders = await scheduleAllReminders(updated);
      await saveSups(supplements.map((s) => s.id === sup.id ? { ...updated, reminders } : s));
    }
  }

  async function addReminderToSup(sup: Supplement) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const last = sup.reminders[sup.reminders.length - 1];
    const newR: Reminder = { id: genId(), hour: ((last?.hour ?? 8) + 2) % 24, minute: last?.minute ?? 0 };
    if (sup.reminderEnabled) newR.notifId = await scheduleReminder(sup, newR);
    await saveSups(supplements.map((s) => s.id === sup.id ? { ...s, reminders: [...s.reminders, newR] } : s));
  }

  async function updateReminderOnSup(sup: Supplement, remId: string, field: "hour" | "minute", val: number) {
    const updatedReminders = await Promise.all(sup.reminders.map(async (r) => {
      if (r.id !== remId) return r;
      const newR = { ...r, [field]: val };
      if (sup.reminderEnabled) { await cancelNotif(r.notifId); newR.notifId = await scheduleReminder(sup, newR); }
      return newR;
    }));
    await saveSups(supplements.map((s) => s.id === sup.id ? { ...s, reminders: updatedReminders } : s));
  }

  async function removeReminderFromSup(sup: Supplement, remId: string) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const rem = sup.reminders.find((r) => r.id === remId);
    if (rem) await cancelNotif(rem.notifId);
    await saveSups(supplements.map((s) => s.id === sup.id ? { ...s, reminders: s.reminders.filter((r) => r.id !== remId) } : s));
  }

  async function toggleTaken(supId: string) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const newTaken = { ...taken };
    if (newTaken[supId] === today) delete newTaken[supId]; else newTaken[supId] = today;
    await saveTaken(newTaken);
  }

  async function handleDelete(sup: Supplement) {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    await cancelAllReminders(sup.reminders);
    await saveSups(supplements.filter((s) => s.id !== sup.id));
    setDeletingId(null);
  }

  const calPct = Math.min(1, tracker.calorieGoal > 0 ? todayCalories / tracker.calorieGoal : 0);
  const calColor = calPct > 1.05 ? "#ef4444" : calPct >= 0.9 ? "#22c55e" : colors.primary;
  const waterDots = Array.from({ length: Math.min(tracker.waterGoal, 12) });

  if (loading) {
    return (
      <View style={[s.container, { backgroundColor: colors.background, justifyContent: "center", alignItems: "center" }]}>
        <ActivityIndicator color={colors.primary} />
      </View>
    );
  }

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View style={[s.header, { paddingTop: topPad + 16, borderBottomColor: colors.border }]}>
        <Text style={[s.title, { color: colors.foreground }]}>Nutrition</Text>
        <TouchableOpacity
          onPress={() => { setShowForm(!showForm); setDeletingId(null); setExpandedRemId(null); }}
          style={[s.addBtn, { backgroundColor: colors.primary }]}
        >
          <Feather name={showForm ? "x" : "plus"} size={18} color={colors.primaryForeground} />
          <Text style={[s.addBtnText, { color: colors.primaryForeground }]}>
            {showForm ? "Annuler" : "Complément"}
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 100, gap: 12 }} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">

        {/* ══ SUIVI DU JOUR ══════════════════════════════════════════════════ */}
        <Text style={[s.sectionLabel, { color: colors.mutedForeground }]}>SUIVI DU JOUR</Text>

        <View style={s.trackingRow}>
          {/* ── Calories card ── */}
          <View style={[s.trackCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={s.trackCardHeader}>
              <Text style={{ fontSize: 18 }}>🔥</Text>
              <Text style={[s.trackCardTitle, { color: colors.foreground }]}>Calories</Text>
              <TouchableOpacity
                onPress={() => { setEditCalGoal(!editCalGoal); setCalGoalInput(String(tracker.calorieGoal)); setEditWaterGoal(false); }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Feather name="settings" size={12} color={colors.mutedForeground} />
              </TouchableOpacity>
            </View>

            <Text style={[s.trackBigNum, { color: calColor }]}>
              {todayCalories.toLocaleString("fr-FR")}
            </Text>
            <Text style={[s.trackGoalText, { color: colors.mutedForeground }]}>
              / {tracker.calorieGoal.toLocaleString("fr-FR")} kcal
            </Text>
            <ProgressBar value={todayCalories} max={tracker.calorieGoal} color={calColor} />

            {editCalGoal ? (
              <View style={[s.goalEditRow, { marginTop: 10 }]}>
                <TextInput
                  style={[s.goalInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                  value={calGoalInput}
                  onChangeText={setCalGoalInput}
                  keyboardType="number-pad"
                  placeholder="Objectif kcal"
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                  autoFocus
                />
                <TouchableOpacity onPress={saveCalGoal} style={[s.goalSaveBtn, { backgroundColor: colors.primary }]}>
                  <Feather name="check" size={14} color="#fff" />
                </TouchableOpacity>
              </View>
            ) : (
              <View style={[s.calBtnsRow, { marginTop: 10 }]}>
                <View style={[s.calInputWrap, { backgroundColor: colors.input, borderColor: colors.border }]}>
                  <TextInput
                    style={[s.calDirectInput, { color: colors.foreground }]}
                    value={calInput}
                    onChangeText={setCalInput}
                    keyboardType="number-pad"
                    placeholder="Ajouter…"
                    placeholderTextColor={colors.mutedForeground}
                    returnKeyType="done"
                    onSubmitEditing={() => {
                      const v = parseInt(calInput);
                      if (!isNaN(v)) { addCalories(v); setCalInput(""); }
                    }}
                  />
                </View>
                <TouchableOpacity
                  style={[s.calAddBtn, { backgroundColor: colors.primary + "22", borderColor: colors.primary + "44" }]}
                  onPress={() => {
                    const v = parseInt(calInput);
                    if (!isNaN(v) && v > 0) { addCalories(v); setCalInput(""); }
                  }}
                >
                  <Feather name="plus" size={16} color={colors.primary} />
                </TouchableOpacity>
                <TouchableOpacity
                  style={[s.calAddBtn, { backgroundColor: colors.muted, borderColor: colors.border }]}
                  onPress={() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); setCaloriesToday(0); }}
                >
                  <Feather name="rotate-ccw" size={14} color={colors.mutedForeground} />
                </TouchableOpacity>
              </View>
            )}
          </View>

          {/* ── Water card ── */}
          <View style={[s.trackCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={s.trackCardHeader}>
              <Text style={{ fontSize: 18 }}>💧</Text>
              <Text style={[s.trackCardTitle, { color: colors.foreground }]}>Eau</Text>
              <TouchableOpacity
                onPress={() => { setEditWaterGoal(!editWaterGoal); setWaterGoalInput(String(tracker.waterGoal)); setEditCalGoal(false); }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Feather name="settings" size={12} color={colors.mutedForeground} />
              </TouchableOpacity>
            </View>

            <Text style={[s.trackBigNum, { color: todayWater >= tracker.waterGoal ? "#22c55e" : colors.primary }]}>
              {todayWater}
            </Text>
            <Text style={[s.trackGoalText, { color: colors.mutedForeground }]}>
              / {tracker.waterGoal} verres
            </Text>

            {editWaterGoal ? (
              <View style={[s.goalEditRow, { marginTop: 10 }]}>
                <TextInput
                  style={[s.goalInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                  value={waterGoalInput}
                  onChangeText={setWaterGoalInput}
                  keyboardType="number-pad"
                  placeholder="Objectif verres"
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                  autoFocus
                />
                <TouchableOpacity onPress={saveWaterGoal} style={[s.goalSaveBtn, { backgroundColor: colors.primary }]}>
                  <Feather name="check" size={14} color="#fff" />
                </TouchableOpacity>
              </View>
            ) : (
              <>
                <View style={s.waterDots}>
                  {waterDots.map((_, i) => (
                    <TouchableOpacity key={i} onPress={() => addWater(i < todayWater ? -(todayWater - i) : i + 1 - todayWater)}>
                      <View style={[
                        s.waterDot,
                        { backgroundColor: i < todayWater ? (todayWater >= tracker.waterGoal ? "#22c55e" : colors.primary) : colors.muted },
                      ]} />
                    </TouchableOpacity>
                  ))}
                </View>
                <View style={s.waterBtns}>
                  <TouchableOpacity
                    style={[s.waterBtn, { backgroundColor: colors.muted, borderColor: colors.border }]}
                    onPress={() => addWater(-1)}
                    disabled={todayWater === 0}
                  >
                    <Feather name="minus" size={18} color={todayWater === 0 ? colors.mutedForeground + "44" : colors.foreground} />
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[s.waterBtn, { backgroundColor: colors.primary, borderColor: colors.primary }]}
                    onPress={() => addWater(1)}
                  >
                    <Feather name="plus" size={18} color="#fff" />
                  </TouchableOpacity>
                </View>
              </>
            )}
          </View>
        </View>

        {/* ══ COMPLÉMENTS ════════════════════════════════════════════════════ */}
        <View style={s.sectionHeaderRow}>
          <Text style={[s.sectionLabel, { color: colors.mutedForeground }]}>
            COMPLÉMENTS {supplements.length > 0 ? `(${supplements.length})` : ""}
          </Text>
        </View>

        {/* Add form */}
        {showForm && (
          <View style={[s.formCard, { backgroundColor: colors.card, borderColor: colors.primary + "55" }]}>
            <Text style={[s.formTitle, { color: colors.foreground }]}>Nouveau complément</Text>

            <Text style={[s.inputLabel, { color: colors.mutedForeground }]}>Nom</Text>
            <TextInput
              style={[s.input, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
              placeholder="Ex : Créatine, Whey, Oméga-3..."
              placeholderTextColor={colors.mutedForeground}
              value={formName} onChangeText={setFormName} autoFocus
            />

            <Text style={[s.inputLabel, { color: colors.mutedForeground }]}>Dosage par prise</Text>
            <View style={s.dosageRow}>
              <TextInput
                style={[s.input, { flex: 1, color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                placeholder="Ex : 1" placeholderTextColor={colors.mutedForeground}
                value={formDosage} onChangeText={setFormDosage} keyboardType="numeric"
              />
              <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginLeft: 8 }}>
                <View style={{ flexDirection: "row", gap: 6 }}>
                  {UNITS.map((u) => (
                    <TouchableOpacity key={u} onPress={() => setFormUnit(u)}
                      style={[s.unitChip, { backgroundColor: formUnit === u ? colors.primary : colors.card, borderColor: formUnit === u ? colors.primary : colors.border }]}>
                      <Text style={[s.unitChipText, { color: formUnit === u ? colors.primaryForeground : colors.foreground }]}>{u}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </ScrollView>
            </View>

            <View style={s.reminderRow}>
              <View style={s.reminderLeft}>
                <Feather name="bell" size={15} color={colors.mutedForeground} />
                <Text style={[s.inputLabel, { color: colors.foreground, marginBottom: 0 }]}>Rappels</Text>
              </View>
              <Switch value={formReminder}
                onValueChange={(v) => { setFormReminder(v); if (v && formReminders.length === 0) setFormReminders([{ id: genId(), hour: 8, minute: 0 }]); }}
                trackColor={{ true: colors.primary, false: colors.muted }} thumbColor="#fff"
              />
            </View>

            {formReminder && (
              <View style={{ gap: 8 }}>
                {formReminders.map((r) => (
                  <TimePicker key={r.id} hour={r.hour} minute={r.minute}
                    onHour={(h) => updateFormReminder(r.id, "hour", h)}
                    onMinute={(m) => updateFormReminder(r.id, "minute", m)}
                    onRemove={() => removeFormReminder(r.id)}
                    showRemove={formReminders.length > 1}
                  />
                ))}
                <TouchableOpacity onPress={addFormReminder}
                  style={[s.addReminderBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}>
                  <Feather name="plus" size={14} color={colors.primary} />
                  <Text style={[s.addReminderText, { color: colors.primary }]}>Ajouter un rappel</Text>
                </TouchableOpacity>
              </View>
            )}

            <TouchableOpacity
              style={[s.saveBtn, { backgroundColor: !formName.trim() ? colors.muted : colors.primary }]}
              onPress={handleAdd} disabled={!formName.trim()}
            >
              <Feather name="check" size={16} color={colors.primaryForeground} />
              <Text style={[s.saveBtnText, { color: colors.primaryForeground }]}>Enregistrer</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Empty state */}
        {supplements.length === 0 && !showForm && (
          <View style={s.emptyState}>
            <View style={[s.emptyIcon, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={{ fontSize: 28 }}>💊</Text>
            </View>
            <Text style={[s.emptyTitle, { color: colors.foreground }]}>Aucun complément</Text>
            <Text style={[s.emptyText, { color: colors.mutedForeground }]}>
              Ajoute tes compléments alimentaires et configure des rappels pour ne jamais en oublier
            </Text>
          </View>
        )}

        {/* Supplement cards */}
        {supplements.map((sup) => {
          const isTaken = taken[sup.id] === today;
          const isDeleting = deletingId === sup.id;
          const isExpanded = expandedRemId === sup.id;
          const reminderCount = sup.reminders.length;

          return (
            <View key={sup.id}>
              <View style={[s.supCard, { backgroundColor: isTaken ? colors.primary + "12" : colors.card, borderColor: isTaken ? colors.primary + "55" : colors.border }]}>
                <View style={s.supTop}>
                  <TouchableOpacity onPress={() => toggleTaken(sup.id)}
                    style={[s.takenCircle, { backgroundColor: isTaken ? colors.primary : "transparent", borderColor: isTaken ? colors.primary : colors.mutedForeground }]}>
                    {isTaken && <Feather name="check" size={14} color="#fff" />}
                  </TouchableOpacity>
                  <View style={s.supInfo}>
                    <Text style={[s.supName, { color: colors.foreground }]}>{sup.name}</Text>
                    {(sup.dosage || sup.unit) && (
                      <Text style={[s.supDosage, { color: colors.mutedForeground }]}>{sup.dosage} {sup.unit} par prise</Text>
                    )}
                  </View>
                  <TouchableOpacity onPress={() => { setDeletingId(isDeleting ? null : sup.id); setExpandedRemId(null); }}
                    style={[s.iconBtn, { borderColor: colors.border }]} hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}>
                    <Feather name="trash-2" size={13} color={colors.destructive} />
                  </TouchableOpacity>
                </View>

                <View style={[s.reminderSection, { borderTopColor: colors.border }]}>
                  <View style={s.masterRow}>
                    <TouchableOpacity style={s.masterLeft} onPress={() => setExpandedRemId(isExpanded ? null : sup.id)} activeOpacity={0.7}>
                      <Feather name={sup.reminderEnabled ? "bell" : "bell-off"} size={13} color={sup.reminderEnabled ? colors.primary : colors.mutedForeground} />
                      <Text style={[s.reminderSummary, { color: sup.reminderEnabled ? colors.primary : colors.mutedForeground }]}>
                        {sup.reminderEnabled
                          ? reminderCount === 0 ? "Rappels activés" : reminderCount === 1 ? `Rappel à ${pad2(sup.reminders[0].hour)}:${pad2(sup.reminders[0].minute)}` : `${reminderCount} rappels`
                          : "Pas de rappel"}
                      </Text>
                      {reminderCount > 0 && <Feather name={isExpanded ? "chevron-up" : "chevron-down"} size={13} color={colors.mutedForeground} />}
                    </TouchableOpacity>
                    <Switch value={sup.reminderEnabled} onValueChange={() => toggleMasterReminder(sup)}
                      trackColor={{ true: colors.primary, false: colors.muted }} thumbColor="#fff"
                      style={{ transform: [{ scale: 0.8 }] }}
                    />
                  </View>
                  {isExpanded && (
                    <View style={{ gap: 6, marginTop: 8 }}>
                      {sup.reminders.map((r) => (
                        <TimePicker key={r.id} hour={r.hour} minute={r.minute}
                          onHour={(h) => updateReminderOnSup(sup, r.id, "hour", h)}
                          onMinute={(m) => updateReminderOnSup(sup, r.id, "minute", m)}
                          onRemove={() => removeReminderFromSup(sup, r.id)}
                          showRemove={sup.reminders.length > 1}
                        />
                      ))}
                      <TouchableOpacity onPress={() => addReminderToSup(sup)}
                        style={[s.addReminderBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}>
                        <Feather name="plus" size={14} color={colors.primary} />
                        <Text style={[s.addReminderText, { color: colors.primary }]}>Ajouter un rappel</Text>
                      </TouchableOpacity>
                    </View>
                  )}
                </View>

                <View style={[s.takenBadge, { backgroundColor: isTaken ? colors.primary + "20" : colors.muted + "44" }]}>
                  <Text style={[s.takenText, { color: isTaken ? colors.primary : colors.mutedForeground }]}>
                    {isTaken ? "✓ Pris aujourd'hui" : "Non pris aujourd'hui"}
                  </Text>
                </View>
              </View>

              {isDeleting && (
                <View style={[s.deleteBox, { backgroundColor: colors.destructive + "15", borderColor: colors.destructive + "44" }]}>
                  <Text style={[s.deleteText, { color: colors.foreground }]}>Supprimer « {sup.name} » ?</Text>
                  <View style={s.deleteBtns}>
                    <TouchableOpacity style={[s.deleteCancelBtn, { borderColor: colors.border }]} onPress={() => setDeletingId(null)}>
                      <Text style={[s.deleteCancelText, { color: colors.foreground }]}>Annuler</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={[s.deleteOkBtn, { backgroundColor: colors.destructive }]} onPress={() => handleDelete(sup)}>
                      <Feather name="trash-2" size={13} color="#fff" />
                      <Text style={s.deleteOkText}>Supprimer</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              )}
            </View>
          );
        })}

        {supplements.some((s) => s.reminderEnabled) && (
          <View style={[s.infoBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Feather name="info" size={13} color={colors.mutedForeground} />
            <Text style={[s.infoText, { color: colors.mutedForeground }]}>
              {Platform.OS === "web"
                ? "Les rappels par notification sont disponibles uniquement sur l'application mobile (Android / iOS)."
                : "Les rappels sont des notifications locales. L'app doit avoir la permission d'envoyer des notifications."}
            </Text>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const s = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 20, paddingBottom: 14, borderBottomWidth: 1, flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  title: { fontSize: 28, fontFamily: "Inter_700Bold" },
  addBtn: { flexDirection: "row", alignItems: "center", gap: 6, paddingHorizontal: 14, paddingVertical: 9, borderRadius: 20 },
  addBtnText: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  sectionLabel: { fontSize: 11, fontFamily: "Inter_600SemiBold", letterSpacing: 0.8, marginBottom: 2 },
  sectionHeaderRow: { flexDirection: "row", alignItems: "center", marginTop: 4 },

  // ── Tracking ──
  trackingRow: { flexDirection: "row", gap: 10 },
  trackCard: { flex: 1, borderRadius: 16, borderWidth: 1, padding: 14 },
  trackCardHeader: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 6 },
  trackCardTitle: { flex: 1, fontSize: 13, fontFamily: "Inter_600SemiBold" },
  trackBigNum: { fontSize: 30, fontFamily: "Inter_700Bold", lineHeight: 34 },
  trackGoalText: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 2 },
  calBtnsRow: { flexDirection: "row", gap: 6, alignItems: "center" },
  calInputWrap: { flex: 1, borderRadius: 8, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 6 },
  calDirectInput: { fontSize: 14, fontFamily: "Inter_600SemiBold", includeFontPadding: false },
  calAddBtn: { width: 34, height: 34, borderRadius: 8, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  goalEditRow: { flexDirection: "row", gap: 6, alignItems: "center" },
  goalInput: { flex: 1, borderRadius: 8, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 7, fontSize: 14, fontFamily: "Inter_600SemiBold" },
  goalSaveBtn: { width: 34, height: 34, borderRadius: 8, alignItems: "center", justifyContent: "center" },
  waterDots: { flexDirection: "row", flexWrap: "wrap", gap: 5, marginTop: 8 },
  waterDot: { width: 10, height: 10, borderRadius: 5 },
  waterBtns: { flexDirection: "row", gap: 8, marginTop: 10 },
  waterBtn: { flex: 1, height: 36, borderRadius: 10, borderWidth: 1, alignItems: "center", justifyContent: "center" },

  // ── Form ──
  formCard: { borderRadius: 16, borderWidth: 1.5, padding: 16, gap: 10 },
  formTitle: { fontSize: 16, fontFamily: "Inter_700Bold", marginBottom: 4 },
  inputLabel: { fontSize: 12, fontFamily: "Inter_500Medium", marginBottom: 4 },
  input: { borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 15, fontFamily: "Inter_400Regular" },
  dosageRow: { flexDirection: "row", alignItems: "center" },
  unitChip: { paddingHorizontal: 12, paddingVertical: 6, borderRadius: 16, borderWidth: 1 },
  unitChipText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  reminderRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", paddingVertical: 4 },
  reminderLeft: { flexDirection: "row", alignItems: "center", gap: 8 },
  addReminderBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6, paddingVertical: 9, borderRadius: 10, borderWidth: 1, borderStyle: "dashed" },
  addReminderText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  saveBtn: { flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 8, borderRadius: 12, padding: 14, marginTop: 4 },
  saveBtnText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },

  // ── Empty ──
  emptyState: { alignItems: "center", paddingTop: 20, paddingBottom: 20, gap: 10 },
  emptyIcon: { width: 60, height: 60, borderRadius: 20, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  emptyTitle: { fontSize: 17, fontFamily: "Inter_700Bold" },
  emptyText: { fontSize: 13, fontFamily: "Inter_400Regular", textAlign: "center", lineHeight: 20, paddingHorizontal: 16 },

  // ── Supplement card ──
  supCard: { borderRadius: 16, borderWidth: 1, padding: 14, gap: 10 },
  supTop: { flexDirection: "row", alignItems: "center", gap: 12 },
  takenCircle: { width: 28, height: 28, borderRadius: 14, borderWidth: 2, alignItems: "center", justifyContent: "center" },
  supInfo: { flex: 1 },
  supName: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  supDosage: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
  iconBtn: { width: 30, height: 30, borderRadius: 8, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  reminderSection: { borderTopWidth: 1, paddingTop: 10 },
  masterRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  masterLeft: { flexDirection: "row", alignItems: "center", gap: 6, flex: 1 },
  reminderSummary: { fontSize: 13, fontFamily: "Inter_500Medium" },
  takenBadge: { borderRadius: 8, paddingHorizontal: 10, paddingVertical: 5, alignSelf: "flex-start" },
  takenText: { fontSize: 12, fontFamily: "Inter_500Medium" },

  // ── Delete ──
  deleteBox: { borderWidth: 1, borderTopWidth: 0, borderBottomLeftRadius: 12, borderBottomRightRadius: 12, padding: 12, gap: 10 },
  deleteText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  deleteBtns: { flexDirection: "row", gap: 8 },
  deleteCancelBtn: { flex: 1, borderWidth: 1, borderRadius: 8, paddingVertical: 9, alignItems: "center" },
  deleteCancelText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  deleteOkBtn: { flex: 1, flexDirection: "row", gap: 6, borderRadius: 8, paddingVertical: 9, alignItems: "center", justifyContent: "center" },
  deleteOkText: { fontSize: 13, fontFamily: "Inter_600SemiBold", color: "#fff" },

  // ── Info ──
  infoBox: { flexDirection: "row", gap: 10, borderRadius: 12, borderWidth: 1, padding: 14, alignItems: "flex-start" },
  infoText: { flex: 1, fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
});
