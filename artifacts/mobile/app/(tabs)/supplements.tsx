import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import React, { useState } from "react";
import {
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { usePreferences } from "@/context/PreferencesContext";
import { useNutrition } from "@/context/NutritionContext";
import { generateId } from "@/utils/id";
import { parseDecimal, parseInteger } from "@/utils/number";

const UNITS = ["g", "mg", "ml", "caps", "cp", "dose", "ui"];

function pad2(n: number) {
  return String(n).padStart(2, "0");
}

// ─── TimePicker sub-component ─────────────────────────────────────────────────
// Arrows report a delta (±1 hour / ±1 quarter-hour) rather than a new value, so
// rapid taps accumulate correctly even before the previous one has re-rendered.
function TimePicker({ hour, minute, onShift, onRemove, showRemove }: {
  hour: number; minute: number;
  onShift: (field: "hour" | "minute", delta: number) => void;
  onRemove?: () => void; showRemove: boolean;
}) {
  const colors = useColors();
  return (
    <View style={[tpStyles.row, { backgroundColor: colors.input, borderColor: colors.border }]}>
      <Feather name="clock" size={13} color={colors.mutedForeground} />
      <TouchableOpacity onPress={() => onShift("hour", -1)} style={tpStyles.arrow}><Feather name="chevron-left" size={15} color={colors.foreground} /></TouchableOpacity>
      <Text style={[tpStyles.val, { color: colors.foreground }]}>{pad2(hour)}</Text>
      <TouchableOpacity onPress={() => onShift("hour", 1)} style={tpStyles.arrow}><Feather name="chevron-right" size={15} color={colors.foreground} /></TouchableOpacity>
      <Text style={[tpStyles.sep, { color: colors.mutedForeground }]}>:</Text>
      <TouchableOpacity onPress={() => onShift("minute", -1)} style={tpStyles.arrow}><Feather name="chevron-left" size={15} color={colors.foreground} /></TouchableOpacity>
      <Text style={[tpStyles.val, { color: colors.foreground }]}>{pad2(minute)}</Text>
      <TouchableOpacity onPress={() => onShift("minute", 1)} style={tpStyles.arrow}><Feather name="chevron-right" size={15} color={colors.foreground} /></TouchableOpacity>
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
  const { t, locale } = usePreferences();
  const insets = useSafeAreaInsets();
  const topPad = insets.top;
  const {
    today, tracker, supplements, taken,
    addCalories, setCaloriesToday, setCalorieGoal, addWater, setWaterGoal,
    toggleTaken, addSupplement, deleteSupplement, setRemindersEnabled,
    addReminder, shiftReminder, removeReminder,
  } = useNutrition();

  // ── Supplement form ──
  const [showForm, setShowForm] = useState(false);
  const [formName, setFormName] = useState("");
  const [formDosage, setFormDosage] = useState("");
  const [formUnit, setFormUnit] = useState("g");
  const [formReminder, setFormReminder] = useState(true);
  const [formReminders, setFormReminders] = useState<Array<{ id: string; hour: number; minute: number }>>([{ id: generateId(), hour: 8, minute: 0 }]);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [expandedRemId, setExpandedRemId] = useState<string | null>(null);

  // ── Daily tracker inputs ──
  const [editCalGoal, setEditCalGoal] = useState(false);
  const [editWaterGoal, setEditWaterGoal] = useState(false);
  const [calGoalInput, setCalGoalInput] = useState("");
  const [waterGoalInput, setWaterGoalInput] = useState("");
  const [calInput, setCalInput] = useState("");
  const [calMode, setCalMode] = useState<"add" | "set">("add");
  const [waterInput, setWaterInput] = useState("");

  const todayCalories = tracker.caloriesLog[today] ?? 0;
  const todayWater = tracker.waterLog[today] ?? 0;

  function tap() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }

  // ── Calories ──
  function submitCalories() {
    const v = parseInteger(calInput);
    if (!calInput.trim() || v < 0) return;
    tap();
    if (calMode === "add") addCalories(v);
    else setCaloriesToday(v);
    setCalInput("");
  }
  function saveCalGoal() {
    const v = parseInteger(calGoalInput);
    if (v > 0) setCalorieGoal(v);
    setEditCalGoal(false);
  }

  // ── Water ──
  function submitWater() {
    const v = parseInteger(waterInput);
    if (v <= 0) return;
    tap();
    addWater(v);
    setWaterInput("");
  }
  function saveWaterGoal() {
    const v = parseDecimal(waterGoalInput);
    if (v > 0) setWaterGoal(Math.round(v * 1000));
    setEditWaterGoal(false);
  }

  // ── Supplement form helpers ──
  function addFormReminder() {
    setFormReminders((prev) => {
      const last = prev[prev.length - 1];
      return [...prev, { id: generateId(), hour: ((last?.hour ?? 8) + 2) % 24, minute: last?.minute ?? 0 }];
    });
  }
  function removeFormReminder(id: string) { setFormReminders((prev) => prev.filter((r) => r.id !== id)); }
  function shiftFormReminder(id: string, field: "hour" | "minute", delta: number) {
    setFormReminders((prev) => prev.map((r) => {
      if (r.id !== id) return r;
      return field === "hour"
        ? { ...r, hour: (r.hour + delta + 24) % 24 }
        : { ...r, minute: (r.minute + delta * 15 + 60) % 60 };
    }));
  }
  function resetForm() {
    setFormName(""); setFormDosage(""); setFormUnit("g");
    setFormReminder(true); setFormReminders([{ id: generateId(), hour: 8, minute: 0 }]);
  }

  function handleAdd() {
    if (!formName.trim()) return;
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    addSupplement({
      name: formName.trim(),
      dosage: formDosage.trim(),
      unit: formUnit,
      reminderEnabled: formReminder,
      reminders: formReminders,
    });
    setShowForm(false); resetForm();
  }

  function handleDelete(supId: string) {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    deleteSupplement(supId);
    setDeletingId(null);
  }

  const calPct = Math.min(1, tracker.calorieGoal > 0 ? todayCalories / tracker.calorieGoal : 0);
  const calColor = calPct > 1.05 ? "#ef4444" : calPct >= 0.9 ? "#22c55e" : colors.primary;

  return (
    <View style={[s.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View style={[s.header, { paddingTop: topPad + 16, borderBottomColor: colors.border }]}>
        <Text style={[s.title, { color: colors.foreground }]}>{t("nutrition.title")}</Text>
        <TouchableOpacity
          onPress={() => { setShowForm(!showForm); setDeletingId(null); setExpandedRemId(null); }}
          style={[s.addBtn, { backgroundColor: colors.primary }]}
        >
          <Feather name={showForm ? "x" : "plus"} size={18} color={colors.primaryForeground} />
          <Text style={[s.addBtnText, { color: colors.primaryForeground }]}>
            {showForm ? t("common.cancel") : t("nutrition.supplement")}
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView contentContainerStyle={{ padding: 16, paddingBottom: 100, gap: 12 }} showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">

        {/* ══ SUIVI DU JOUR ══════════════════════════════════════════════════ */}
        <Text style={[s.sectionLabel, { color: colors.mutedForeground }]}>{t("nutrition.sectionToday")}</Text>

        <View style={s.trackingStack}>
          {/* ── Calories card ── */}
          <View style={[s.trackCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={s.trackCardHeader}>
              <Text style={{ fontSize: 18 }}>🔥</Text>
              <Text style={[s.trackCardTitle, { color: colors.foreground }]}>{t("progress.tab.calories")}</Text>
              <TouchableOpacity
                onPress={() => { setEditCalGoal(!editCalGoal); setCalGoalInput(String(tracker.calorieGoal)); setEditWaterGoal(false); }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Feather name="settings" size={12} color={colors.mutedForeground} />
              </TouchableOpacity>
            </View>

            <View style={s.trackValueRow}>
              <Text style={[s.trackBigNum, { color: calColor }]}>
                {todayCalories.toLocaleString(locale)}
              </Text>
              <Text style={[s.trackGoalText, { color: colors.mutedForeground }]}>
                / {tracker.calorieGoal.toLocaleString(locale)} kcal
              </Text>
            </View>
            <ProgressBar value={todayCalories} max={tracker.calorieGoal} color={calColor} />

            {editCalGoal ? (
              <View style={[s.goalEditRow, { marginTop: 10 }]}>
                <TextInput
                  style={[s.goalInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                  value={calGoalInput}
                  onChangeText={setCalGoalInput}
                  keyboardType="number-pad"
                  placeholder={t("nutrition.calGoalPlaceholder")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                  autoFocus
                />
                <TouchableOpacity onPress={saveCalGoal} style={[s.goalSaveBtn, { backgroundColor: colors.primary }]}>
                  <Feather name="check" size={14} color="#fff" />
                </TouchableOpacity>
              </View>
            ) : (
              <View style={{ marginTop: 10, gap: 6 }}>
                <View style={s.calModeRow}>
                  <TouchableOpacity
                    onPress={() => setCalMode("add")}
                    style={[s.calModeBtn, { backgroundColor: calMode === "add" ? colors.primary : colors.muted, borderColor: calMode === "add" ? colors.primary : colors.border }]}
                  >
                    <Text style={[s.calModeBtnText, { color: calMode === "add" ? "#fff" : colors.mutedForeground }]}>{t("nutrition.calAdd")}</Text>
                  </TouchableOpacity>
                  <TouchableOpacity
                    onPress={() => setCalMode("set")}
                    style={[s.calModeBtn, { backgroundColor: calMode === "set" ? colors.primary : colors.muted, borderColor: calMode === "set" ? colors.primary : colors.border }]}
                  >
                    <Text style={[s.calModeBtnText, { color: calMode === "set" ? "#fff" : colors.mutedForeground }]}>{t("nutrition.calSet")}</Text>
                  </TouchableOpacity>
                </View>
                <View style={s.calBtnsRow}>
                  <View style={[s.calInputWrap, { flex: 1, backgroundColor: colors.input, borderColor: colors.border }]}>
                    <TextInput
                      style={[s.calDirectInput, { color: colors.foreground }]}
                      value={calInput}
                      onChangeText={setCalInput}
                      keyboardType="number-pad"
                      placeholder={t("nutrition.calPlaceholder")}
                      placeholderTextColor={colors.mutedForeground}
                      returnKeyType="done"
                      onSubmitEditing={submitCalories}
                    />
                  </View>
                  <TouchableOpacity
                    style={[s.calAddBtn, { backgroundColor: colors.primary + "22", borderColor: colors.primary + "44" }]}
                    onPress={submitCalories}
                  >
                    <Feather name={calMode === "add" ? "plus" : "check"} size={16} color={colors.primary} />
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[s.calAddBtn, { backgroundColor: colors.muted, borderColor: colors.border }]}
                    onPress={() => { tap(); setCaloriesToday(0); }}
                  >
                    <Feather name="rotate-ccw" size={14} color={colors.mutedForeground} />
                  </TouchableOpacity>
                </View>
              </View>
            )}
          </View>

          {/* ── Water card ── */}
          <View style={[s.trackCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <View style={s.trackCardHeader}>
              <Text style={{ fontSize: 18 }}>💧</Text>
              <Text style={[s.trackCardTitle, { color: colors.foreground }]}>{t("progress.tab.water")}</Text>
              <TouchableOpacity
                onPress={() => { setEditWaterGoal(!editWaterGoal); setWaterGoalInput((tracker.waterGoal / 1000).toFixed(1)); setEditCalGoal(false); }}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Feather name="settings" size={12} color={colors.mutedForeground} />
              </TouchableOpacity>
            </View>

            <View style={s.trackValueRow}>
              <Text style={[s.trackBigNum, { color: todayWater >= tracker.waterGoal ? "#22c55e" : colors.primary }]}>
                {todayWater >= 1000 ? (todayWater / 1000).toFixed(1) : todayWater}
                <Text style={{ fontSize: 18 }}>{todayWater >= 1000 ? " L" : " ml"}</Text>
              </Text>
              <Text style={[s.trackGoalText, { color: colors.mutedForeground }]}>
                / {(tracker.waterGoal / 1000).toFixed(1)} L
              </Text>
            </View>
            <ProgressBar value={todayWater} max={tracker.waterGoal} color={todayWater >= tracker.waterGoal ? "#22c55e" : colors.primary} />

            {editWaterGoal ? (
              <View style={[s.goalEditRow, { marginTop: 10 }]}>
                <TextInput
                  style={[s.goalInput, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                  value={waterGoalInput}
                  onChangeText={setWaterGoalInput}
                  keyboardType="decimal-pad"
                  placeholder={t("nutrition.waterGoalPlaceholder")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                  autoFocus
                />
                <TouchableOpacity onPress={saveWaterGoal} style={[s.goalSaveBtn, { backgroundColor: colors.primary }]}>
                  <Feather name="check" size={14} color="#fff" />
                </TouchableOpacity>
              </View>
            ) : (
              <View style={[s.calBtnsRow, { marginTop: 10 }]}>
                <View style={[s.calInputWrap, { flex: 1, backgroundColor: colors.input, borderColor: colors.border }]}>
                  <TextInput
                    style={[s.calDirectInput, { color: colors.foreground }]}
                    value={waterInput}
                    onChangeText={setWaterInput}
                    keyboardType="number-pad"
                    placeholder={t("nutrition.waterPlaceholder")}
                    placeholderTextColor={colors.mutedForeground}
                    returnKeyType="done"
                    onSubmitEditing={submitWater}
                  />
                </View>
                <TouchableOpacity
                  style={[s.calAddBtn, { backgroundColor: colors.primary + "22", borderColor: colors.primary + "44" }]}
                  onPress={submitWater}
                >
                  <Feather name="plus" size={16} color={colors.primary} />
                </TouchableOpacity>
                <TouchableOpacity
                  style={[s.calAddBtn, { backgroundColor: colors.muted, borderColor: colors.border }]}
                  onPress={() => { tap(); addWater(-todayWater); }}
                  disabled={todayWater === 0}
                >
                  <Feather name="rotate-ccw" size={14} color={todayWater === 0 ? colors.mutedForeground + "44" : colors.mutedForeground} />
                </TouchableOpacity>
              </View>
            )}
          </View>
        </View>

        {/* ══ COMPLÉMENTS ════════════════════════════════════════════════════ */}
        <View style={s.sectionHeaderRow}>
          <Text style={[s.sectionLabel, { color: colors.mutedForeground }]}>
            {t("nutrition.supplementsSection")} {supplements.length > 0 ? `(${supplements.length})` : ""}
          </Text>
        </View>

        {/* Add form */}
        {showForm && (
          <View style={[s.formCard, { backgroundColor: colors.card, borderColor: colors.primary + "55" }]}>
            <Text style={[s.formTitle, { color: colors.foreground }]}>{t("nutrition.newSupplement")}</Text>

            <Text style={[s.inputLabel, { color: colors.mutedForeground }]}>{t("nutrition.name")}</Text>
            <TextInput
              style={[s.input, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
              placeholder={t("nutrition.namePlaceholder")}
              placeholderTextColor={colors.mutedForeground}
              value={formName} onChangeText={setFormName} autoFocus
            />

            <Text style={[s.inputLabel, { color: colors.mutedForeground }]}>{t("nutrition.dosagePerIntake")}</Text>
            <View style={{ gap: 8 }}>
              <TextInput
                style={[s.input, { color: colors.foreground, backgroundColor: colors.input, borderColor: colors.border }]}
                placeholder={t("nutrition.dosagePlaceholder")} placeholderTextColor={colors.mutedForeground}
                value={formDosage} onChangeText={setFormDosage} keyboardType="decimal-pad"
              />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
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
                <Text style={[s.inputLabel, { color: colors.foreground, marginBottom: 0 }]}>{t("nutrition.reminders")}</Text>
              </View>
              <Switch value={formReminder}
                onValueChange={(v) => { setFormReminder(v); if (v && formReminders.length === 0) setFormReminders([{ id: generateId(), hour: 8, minute: 0 }]); }}
                trackColor={{ true: colors.primary, false: colors.muted }} thumbColor="#fff"
              />
            </View>

            {formReminder && (
              <View style={{ gap: 8 }}>
                {formReminders.map((r) => (
                  <TimePicker key={r.id} hour={r.hour} minute={r.minute}
                    onShift={(field, delta) => shiftFormReminder(r.id, field, delta)}
                    onRemove={() => removeFormReminder(r.id)}
                    showRemove={formReminders.length > 1}
                  />
                ))}
                <TouchableOpacity onPress={addFormReminder}
                  style={[s.addReminderBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}>
                  <Feather name="plus" size={14} color={colors.primary} />
                  <Text style={[s.addReminderText, { color: colors.primary }]}>{t("nutrition.addReminder")}</Text>
                </TouchableOpacity>
              </View>
            )}

            <TouchableOpacity
              style={[s.saveBtn, { backgroundColor: !formName.trim() ? colors.muted : colors.primary }]}
              onPress={handleAdd} disabled={!formName.trim()}
            >
              <Feather name="check" size={16} color={colors.primaryForeground} />
              <Text style={[s.saveBtnText, { color: colors.primaryForeground }]}>{t("common.save")}</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Empty state */}
        {supplements.length === 0 && !showForm && (
          <View style={s.emptyState}>
            <View style={[s.emptyIcon, { backgroundColor: colors.card, borderColor: colors.border }]}>
              <Text style={{ fontSize: 28 }}>💊</Text>
            </View>
            <Text style={[s.emptyTitle, { color: colors.foreground }]}>{t("nutrition.emptyTitle")}</Text>
            <Text style={[s.emptyText, { color: colors.mutedForeground }]}>
              {t("nutrition.emptyText")}
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
                  <TouchableOpacity onPress={() => { Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium); toggleTaken(sup.id); }}
                    style={[s.takenCircle, { backgroundColor: isTaken ? colors.primary : "transparent", borderColor: isTaken ? colors.primary : colors.mutedForeground }]}>
                    {isTaken && <Feather name="check" size={14} color="#fff" />}
                  </TouchableOpacity>
                  <View style={s.supInfo}>
                    <Text style={[s.supName, { color: colors.foreground }]}>{sup.name}</Text>
                    {(sup.dosage || sup.unit) && (
                      <Text style={[s.supDosage, { color: colors.mutedForeground }]}>{t("nutrition.perIntake", { dosage: sup.dosage, unit: sup.unit })}</Text>
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
                          ? reminderCount === 0 ? t("nutrition.remindersOn") : reminderCount === 1 ? t("nutrition.reminderAt", { time: `${pad2(sup.reminders[0].hour)}:${pad2(sup.reminders[0].minute)}` }) : t("nutrition.remindersCount", { count: reminderCount })
                          : t("nutrition.noReminder")}
                      </Text>
                      {reminderCount > 0 && <Feather name={isExpanded ? "chevron-up" : "chevron-down"} size={13} color={colors.mutedForeground} />}
                    </TouchableOpacity>
                    <Switch value={sup.reminderEnabled} onValueChange={(v) => { tap(); setRemindersEnabled(sup.id, v); }}
                      trackColor={{ true: colors.primary, false: colors.muted }} thumbColor="#fff"
                      style={{ transform: [{ scale: 0.8 }] }}
                    />
                  </View>
                  {isExpanded && (
                    <View style={{ gap: 6, marginTop: 8 }}>
                      {sup.reminders.map((r) => (
                        <TimePicker key={r.id} hour={r.hour} minute={r.minute}
                          onShift={(field, delta) => shiftReminder(sup.id, r.id, field, delta)}
                          onRemove={() => { tap(); removeReminder(sup.id, r.id); }}
                          showRemove={sup.reminders.length > 1}
                        />
                      ))}
                      <TouchableOpacity onPress={() => { tap(); addReminder(sup.id); }}
                        style={[s.addReminderBtn, { borderColor: colors.primary + "55", backgroundColor: colors.primary + "10" }]}>
                        <Feather name="plus" size={14} color={colors.primary} />
                        <Text style={[s.addReminderText, { color: colors.primary }]}>{t("nutrition.addReminder")}</Text>
                      </TouchableOpacity>
                    </View>
                  )}
                </View>

                <View style={[s.takenBadge, { backgroundColor: isTaken ? colors.primary + "20" : colors.muted + "44" }]}>
                  <Text style={[s.takenText, { color: isTaken ? colors.primary : colors.mutedForeground }]}>
                    {isTaken ? t("nutrition.takenYes") : t("nutrition.takenNo")}
                  </Text>
                </View>
              </View>

              {isDeleting && (
                <View style={[s.deleteBox, { backgroundColor: colors.destructive + "15", borderColor: colors.destructive + "44" }]}>
                  <Text style={[s.deleteText, { color: colors.foreground }]}>{t("nutrition.deleteConfirm", { name: sup.name })}</Text>
                  <View style={s.deleteBtns}>
                    <TouchableOpacity style={[s.deleteCancelBtn, { borderColor: colors.border }]} onPress={() => setDeletingId(null)}>
                      <Text style={[s.deleteCancelText, { color: colors.foreground }]}>{t("common.cancel")}</Text>
                    </TouchableOpacity>
                    <TouchableOpacity style={[s.deleteOkBtn, { backgroundColor: colors.destructive }]} onPress={() => handleDelete(sup.id)}>
                      <Feather name="trash-2" size={13} color="#fff" />
                      <Text style={s.deleteOkText}>{t("common.delete")}</Text>
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
              {t("nutrition.infoMobile")}
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
  trackingStack: { gap: 10 },
  trackCard: { borderRadius: 16, borderWidth: 1, padding: 14 },
  trackCardHeader: { flexDirection: "row", alignItems: "center", gap: 6, marginBottom: 6 },
  trackCardTitle: { flex: 1, fontSize: 13, fontFamily: "Inter_600SemiBold" },
  trackValueRow: { flexDirection: "row", alignItems: "baseline", gap: 6, flexWrap: "wrap" },
  trackBigNum: { fontSize: 24, fontFamily: "Inter_700Bold", lineHeight: 28 },
  trackGoalText: { fontSize: 11, fontFamily: "Inter_400Regular" },
  calModeRow: { flexDirection: "row", gap: 6 },
  calModeBtn: { flex: 1, borderRadius: 8, borderWidth: 1, paddingVertical: 6, alignItems: "center" },
  calModeBtnText: { fontSize: 11, fontFamily: "Inter_600SemiBold" },
  calBtnsRow: { flexDirection: "row", gap: 6, alignItems: "center" },
  calInputWrap: { flex: 1, borderRadius: 8, borderWidth: 1, paddingHorizontal: 8, paddingVertical: 6 },
  calDirectInput: { fontSize: 14, fontFamily: "Inter_600SemiBold", includeFontPadding: false },
  calAddBtn: { width: 34, height: 34, borderRadius: 8, borderWidth: 1, alignItems: "center", justifyContent: "center" },
  goalEditRow: { flexDirection: "row", gap: 6, alignItems: "center" },
  goalInput: { flex: 1, borderRadius: 8, borderWidth: 1, paddingHorizontal: 10, paddingVertical: 7, fontSize: 14, fontFamily: "Inter_600SemiBold" },
  goalSaveBtn: { width: 34, height: 34, borderRadius: 8, alignItems: "center", justifyContent: "center" },

  // ── Form ──
  formCard: { borderRadius: 16, borderWidth: 1.5, padding: 16, gap: 10 },
  formTitle: { fontSize: 16, fontFamily: "Inter_700Bold", marginBottom: 4 },
  inputLabel: { fontSize: 12, fontFamily: "Inter_500Medium", marginBottom: 4 },
  input: { borderRadius: 10, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10, fontSize: 15, fontFamily: "Inter_400Regular" },
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
