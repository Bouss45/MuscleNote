import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useState } from "react";
import {
  FlatList,
  Image,
  KeyboardAvoidingView,
  Modal,
  Platform,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { usePreferences } from "@/context/PreferencesContext";
import { useWorkout, Workout } from "@/context/WorkoutContext";
import { buildLocalDate } from "@/utils/date";

// ── Streak computation ────────────────────────────────────────────────────────
/** Returns ISO week key "YYYY-WW" for a given Date */
function getWeekKey(d: Date): string {
  const date = new Date(d);
  date.setHours(12, 0, 0, 0);
  const day = date.getDay() || 7;
  date.setDate(date.getDate() + 4 - day); // anchor on Thursday
  const yearStart = new Date(date.getFullYear(), 0, 1);
  const weekNo = Math.ceil(((date.getTime() - yearStart.getTime()) / 86400000 + 1) / 7);
  return `${date.getFullYear()}-${String(weekNo).padStart(2, "0")}`;
}

/** Returns the week key for N weeks before a given key */
function shiftWeek(key: string, delta: number): string {
  const [y, w] = key.split("-").map(Number);
  // Monday of ISO week 1 of year y
  const jan4 = new Date(y, 0, 4); // Jan 4 is always in week 1
  const dayOfWeek = jan4.getDay() || 7;
  const monday1 = new Date(jan4);
  monday1.setDate(jan4.getDate() - (dayOfWeek - 1));
  // Monday of week w
  const target = new Date(monday1);
  target.setDate(monday1.getDate() + (w - 1) * 7 + delta * 7);
  return getWeekKey(target);
}

interface WeekCell {
  count: number;                        // sessions logged that week
  status: "active" | "pause" | "empty"; // validated (3+) / partial (1-2) / none
  isCurrent: boolean;                   // the in-progress week
}

interface StreakResult {
  current: number;   // qualifying weeks in current streak
  best: number;      // best streak ever
  isAlive: boolean;  // streak still going (not broken yet)
  lastWeeks: WeekCell[]; // last 8 weeks, oldest → current
}

function computeStreak(workouts: Workout[]): StreakResult {
  // Count sessions per week
  const weekCounts = new Map<string, number>();
  for (const w of workouts) {
    const key = getWeekKey(new Date(w.date));
    weekCounts.set(key, (weekCounts.get(key) || 0) + 1);
  }
  if (weekCounts.size === 0) return { current: 0, best: 0, isAlive: false, lastWeeks: [] };

  const nowKey = getWeekKey(new Date());

  // Walk backwards from current week to compute current streak
  // Rules: count only weeks with ≥ 3 sessions; allow at most 1 consecutive miss
  let current = 0;
  let consecutiveMisses = 0;
  let isAlive = true;
  let week = nowKey;
  const earliest = Array.from(weekCounts.keys()).sort()[0];

  while (week >= earliest) {
    const count = weekCounts.get(week) || 0;
    if (count >= 3) {
      current++;
      consecutiveMisses = 0;
    } else {
      // Current week in progress: skip without penalty
      if (week === nowKey) {
        week = shiftWeek(week, -1);
        continue;
      }
      consecutiveMisses++;
      if (consecutiveMisses > 1) {
        isAlive = false;
        break;
      }
    }
    week = shiftWeek(week, -1);
  }

  // Compute best streak by scanning all weeks forward
  const allWeeks = new Set<string>();
  for (const key of weekCounts.keys()) allWeeks.add(key);
  // Add all weeks in range
  let scan = Array.from(allWeeks).sort()[0];
  while (scan <= nowKey) {
    allWeeks.add(scan);
    scan = shiftWeek(scan, 1);
  }
  const sortedWeeks = Array.from(allWeeks).sort();

  let best = 0;
  let tempCount = 0;
  let tempMisses = 0;
  for (const k of sortedWeeks) {
    const count = weekCounts.get(k) || 0;
    if (k === nowKey) break; // don't include current week in best
    if (count >= 3) {
      tempCount++;
      tempMisses = 0;
      best = Math.max(best, tempCount);
    } else {
      tempMisses++;
      if (tempMisses > 1) { tempCount = 0; tempMisses = 0; }
    }
  }
  best = Math.max(best, current);

  // Last 8 weeks for the mini calendar (oldest → current), with session counts
  const lastWeeks: WeekCell[] = [];
  for (let i = 7; i >= 0; i--) {
    const k = shiftWeek(nowKey, -i);
    const count = weekCounts.get(k) || 0;
    const isCurrent = k === nowKey;
    let status: WeekCell["status"];
    if (isCurrent && count < 3) status = "empty"; // current week still in progress
    else if (count >= 3) status = "active";
    else if (weekCounts.has(k)) status = "pause";
    else status = "empty";
    lastWeeks.push({ count, status, isCurrent });
  }

  return { current, best, isAlive, lastWeeks };
}

export default function HomeScreen() {
  const colors = useColors();
  const { t, locale } = usePreferences();
  const insets = useSafeAreaInsets();
  const { workouts, currentWorkout, updateWorkoutDate, deleteWorkout } = useWorkout();

  const topPad = insets.top + 10;
  const bottomPad = insets.bottom + 90;

  // Date edit modal state
  const [editingWorkout, setEditingWorkout] = useState<Workout | null>(null);
  const [editDay, setEditDay] = useState("");
  const [editMonth, setEditMonth] = useState("");
  const [editYear, setEditYear] = useState("");
  const [editHour, setEditHour] = useState("");
  const [editMinute, setEditMinute] = useState("");
  const [dateError, setDateError] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<string | null>(null);

  const streak = computeStreak(workouts);

  const gymTimeStats = (() => {
    const MIN_DURATION = 10;

    // Average duration of valid sessions (>= 10 min) grouped by workout name
    const durationsByName = new Map<string, number[]>();
    for (const w of workouts) {
      if ((w.durationMinutes ?? 0) >= MIN_DURATION) {
        const list = durationsByName.get(w.name) ?? [];
        list.push(w.durationMinutes!);
        durationsByName.set(w.name, list);
      }
    }
    const avgByName = new Map<string, number>();
    for (const [name, durations] of durationsByName) {
      avgByName.set(name, Math.round(durations.reduce((a, b) => a + b, 0) / durations.length));
    }

    let totalMinutes = 0;
    let validCount = 0;
    for (const w of workouts) {
      const dur = w.durationMinutes ?? 0;
      if (dur >= MIN_DURATION) {
        totalMinutes += dur;
        validCount++;
      } else {
        // Replace short session with the average of same-name sessions, if available
        const avg = avgByName.get(w.name);
        if (avg !== undefined) {
          totalMinutes += avg;
          validCount++;
        }
      }
    }

    const avgMinutes = validCount > 0 ? Math.round(totalMinutes / validCount) : 0;
    return { totalMinutes, avgMinutes, validCount };
  })();

  function formatDate(iso: string) {
    const d = new Date(iso);
    return d.toLocaleDateString(locale, {
      weekday: "short",
      day: "numeric",
      month: "short",
    });
  }

  function formatTime(iso: string) {
    const d = new Date(iso);
    return d.toLocaleTimeString(locale, { hour: "2-digit", minute: "2-digit" });
  }

  function formatGymTime(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h === 0) return `${m}m`;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  }

  function getTotalSets(workout: Workout) {
    return workout.exercises.reduce((sum, e) => sum + e.sets.length, 0);
  }

  function getTotalVolume(workout: Workout) {
    return workout.exercises.reduce((sum, e) => {
      return sum + e.sets.reduce((s2, s) => s2 + s.reps * s.weight, 0);
    }, 0);
  }

  function openEditDate(workout: Workout) {
    const d = new Date(workout.date);
    setEditDay(String(d.getDate()));
    setEditMonth(String(d.getMonth() + 1));
    setEditYear(String(d.getFullYear()));
    setEditHour(String(d.getHours()).padStart(2, "0"));
    setEditMinute(String(d.getMinutes()).padStart(2, "0"));
    setDateError(false);
    setEditingWorkout(workout);
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
  }

  function saveEditDate() {
    if (!editingWorkout) return;
    const d = buildLocalDate(
      parseInt(editYear, 10),
      parseInt(editMonth, 10),
      parseInt(editDay, 10),
      editHour.trim() ? parseInt(editHour, 10) : 12,
      editMinute.trim() ? parseInt(editMinute, 10) : 0,
    );
    if (!d) {
      setDateError(true);
      Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      return;
    }
    updateWorkoutDate(editingWorkout.id, d.toISOString());
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    setEditingWorkout(null);
  }

  function handleDelete(id: string) {
    deleteWorkout(id);
    setConfirmDelete(null);
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
  }

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <Image
        source={require("@/assets/images/home-bg.png")}
        style={styles.bgLogo}
        resizeMode="cover"
        blurRadius={6}
      />
      <FlatList
        data={workouts}
        keyExtractor={(w) => w.id}
        style={[styles.container, { backgroundColor: "transparent" }]}
        contentContainerStyle={{ paddingTop: topPad, paddingBottom: bottomPad }}
        showsVerticalScrollIndicator={false}
        ListHeaderComponent={
          <>
            {currentWorkout ? (
              <TouchableOpacity
                style={[styles.activeCard, { backgroundColor: colors.primary + "22", borderColor: colors.primary }]}
                onPress={() => router.push("/workout")}
                activeOpacity={0.8}
              >
                <View style={styles.activeCardLeft}>
                  <View style={[styles.activeDot, { backgroundColor: colors.primary }]} />
                  <View>
                    <Text style={[styles.activeTitle, { color: colors.primary }]}>
                      {currentWorkout.name}
                    </Text>
                    <Text style={[styles.activeSubtitle, { color: colors.mutedForeground }]}>
                      {t("home.activeSubtitle", { exercises: t("common.exercisesCount", { count: currentWorkout.exercises.length }), weight: currentWorkout.bodyWeight })}
                    </Text>
                  </View>
                </View>
                <Feather name="chevron-right" size={20} color={colors.primary} />
              </TouchableOpacity>
            ) : (
              <TouchableOpacity
                style={[styles.startBtn, { backgroundColor: colors.primary }]}
                onPress={() => {
                  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                  router.push("/start-workout");
                }}
                activeOpacity={0.85}
              >
                <Feather name="play" size={20} color={colors.primaryForeground} />
                <Text style={[styles.startBtnText, { color: colors.primaryForeground }]}>
                  {t("home.newSession")}
                </Text>
              </TouchableOpacity>
            )}

            {/* Streak card */}
            {workouts.length > 0 && (
              <View style={[
                styles.streakCard,
                {
                  backgroundColor: streak.current > 0 && streak.isAlive
                    ? colors.primary + "15"
                    : colors.card,
                  borderColor: streak.current > 0 && streak.isAlive
                    ? colors.primary
                    : colors.border,
                },
              ]}>
                <View style={styles.streakTop}>
                  <View style={styles.streakLeft}>
                    <View style={[styles.streakIconCircle, { backgroundColor: streak.current > 0 && streak.isAlive ? colors.primary + "25" : colors.muted }]}>
                      <Feather name="zap" size={18} color={streak.current > 0 && streak.isAlive ? colors.primary : colors.mutedForeground} />
                    </View>
                    <View>
                      <Text style={[styles.streakLabel, { color: colors.mutedForeground }]}>{t("home.streak.current")}</Text>
                      <View style={styles.streakCountRow}>
                        <Text style={[styles.streakCount, { color: streak.current > 0 && streak.isAlive ? colors.primary : colors.foreground }]}>
                          {streak.current}
                        </Text>
                        <Text style={[styles.streakUnit, { color: colors.mutedForeground }]}>
                          {t("home.streak.weekUnit", { count: streak.current })}
                        </Text>
                      </View>
                    </View>
                  </View>
                  <View style={styles.streakRight}>
                    <Text style={[styles.streakBestLabel, { color: colors.mutedForeground }]}>{t("home.streak.best")}</Text>
                    <Text style={[styles.streakBest, { color: colors.foreground }]}>
                      {t("home.streak.bestValue", { count: streak.best })}
                    </Text>
                  </View>
                </View>

                {/* Mini week calendar — sessions per week over the last 8 weeks */}
                {streak.lastWeeks.length > 0 && (
                  <View style={styles.weeksSection}>
                    <Text style={[styles.weeksHeading, { color: colors.mutedForeground }]}>
                      {t("home.streak.last8weeks")}
                    </Text>
                    <View style={styles.weeksRow}>
                      {streak.lastWeeks.map((w, i) => {
                        const bg =
                          w.status === "active" ? colors.primary :
                          w.status === "pause" ? colors.primary + "33" :
                          colors.muted;
                        const fg =
                          w.status === "active" ? colors.primaryForeground :
                          w.status === "pause" ? colors.foreground :
                          colors.mutedForeground;
                        return (
                          <View
                            key={i}
                            style={[
                              styles.weekCell,
                              { backgroundColor: bg },
                              w.isCurrent && { borderWidth: 1.5, borderColor: colors.primary },
                            ]}
                          >
                            <Text style={[styles.weekCellCount, { color: fg }]}>{w.count}</Text>
                          </View>
                        );
                      })}
                    </View>
                    <View style={styles.legendRow}>
                      <View style={styles.legendItem}>
                        <View style={[styles.legendDot, { backgroundColor: colors.primary }]} />
                        <Text style={[styles.legendText, { color: colors.mutedForeground }]}>{t("home.streak.legendValidated")}</Text>
                      </View>
                      <View style={styles.legendItem}>
                        <View style={[styles.legendDot, { backgroundColor: colors.primary + "33" }]} />
                        <Text style={[styles.legendText, { color: colors.mutedForeground }]}>{t("home.streak.legendPartial")}</Text>
                      </View>
                      <View style={styles.legendItem}>
                        <View style={[styles.legendDot, { backgroundColor: colors.muted }]} />
                        <Text style={[styles.legendText, { color: colors.mutedForeground }]}>{t("home.streak.legendNone")}</Text>
                      </View>
                    </View>
                  </View>
                )}

                <Text style={[styles.streakHint, { color: colors.mutedForeground }]}>
                  {streak.current === 0
                    ? t("home.streak.hintStart")
                    : streak.isAlive
                    ? t("home.streak.hintAlive")
                    : t("home.streak.hintOver")}
                </Text>
              </View>
            )}

            {/* Stats row */}
            {workouts.length > 0 && (
              <View style={styles.statsRow}>
                <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                  <Text style={[styles.statValue, { color: colors.foreground }]}>
                    {workouts.length}
                  </Text>
                  <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
                    {t("home.stat.sessions")}
                  </Text>
                </View>
                <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                  <Text style={[styles.statValue, { color: colors.foreground }]}>
                    {Math.round(workouts.reduce((s, w) => s + getTotalVolume(w), 0) / 1000)}t
                  </Text>
                  <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
                    {t("home.stat.totalVolume")}
                  </Text>
                </View>
                <View style={[styles.statCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                  <Text style={[styles.statValue, { color: colors.foreground }]}>
                    {workouts[0]?.bodyWeight ?? "—"} kg
                  </Text>
                  <Text style={[styles.statLabel, { color: colors.mutedForeground }]}>
                    {t("home.stat.lastWeight")}
                  </Text>
                </View>
              </View>
            )}

            {/* Gym time card */}
            {gymTimeStats.validCount > 0 && (
              <View style={[styles.gymTimeCard, { backgroundColor: colors.card, borderColor: colors.border }]}>
                <View style={[styles.gymTimeIconCircle, { backgroundColor: colors.primary + "20" }]}>
                  <Feather name="clock" size={18} color={colors.primary} />
                </View>
                <View style={styles.gymTimeTexts}>
                  <Text style={[styles.gymTimeValue, { color: colors.foreground }]}>
                    {formatGymTime(gymTimeStats.totalMinutes)}
                  </Text>
                  <Text style={[styles.gymTimeLabel, { color: colors.mutedForeground }]}>
                    {t("home.gymTime", { avg: formatGymTime(gymTimeStats.avgMinutes) })}
                  </Text>
                </View>
              </View>
            )}

            {workouts.length > 0 && (
              <Text style={[styles.sectionTitle, styles.section, { color: colors.foreground }]}>
                {t("home.recentSessions")}
              </Text>
            )}
          </>
        }
        ListEmptyComponent={
          currentWorkout ? null : (
            <View style={styles.empty}>
              <Feather name="activity" size={48} color={colors.mutedForeground} />
              <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
                {t("home.empty.title")}
              </Text>
              <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
                {t("home.empty.text")}
              </Text>
            </View>
          )
        }
        renderItem={({ item: workout }) => (
          <View style={styles.section}>
            <View
              style={[styles.workoutCard, { backgroundColor: colors.card, borderColor: colors.border }]}
            >
              <View style={styles.workoutCardTop}>
                <View style={styles.workoutCardLeft}>
                  <Text style={[styles.workoutName, { color: colors.foreground }]}>
                    {workout.name || formatDate(workout.date)}
                  </Text>
                  <Text style={[styles.workoutTime, { color: colors.mutedForeground }]}>
                    {formatDate(workout.date)} · {formatTime(workout.date)}
                    {workout.durationMinutes ? ` · ${t("home.minutes", { count: workout.durationMinutes })}` : ""}
                  </Text>
                </View>
                <View style={styles.workoutCardActions}>
                  <Text style={[styles.bodyWeightBadge, { color: colors.primary, backgroundColor: colors.primary + "15" }]}>
                    {workout.bodyWeight} kg
                  </Text>
                  <TouchableOpacity
                    onPress={() => router.push({ pathname: "/edit-workout", params: { workoutId: workout.id } })}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                    style={[styles.iconBtn, { borderColor: colors.border }]}
                  >
                    <Feather name="edit-2" size={14} color={colors.mutedForeground} />
                  </TouchableOpacity>
                  <TouchableOpacity
                    onPress={() => openEditDate(workout)}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                    style={[styles.iconBtn, { borderColor: colors.border }]}
                  >
                    <Feather name="calendar" size={14} color={colors.mutedForeground} />
                  </TouchableOpacity>
                  <TouchableOpacity
                    onPress={() => setConfirmDelete(workout.id)}
                    hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                    style={[styles.iconBtn, { borderColor: colors.border }]}
                  >
                    <Feather name="trash-2" size={14} color={colors.destructive} />
                  </TouchableOpacity>
                </View>
              </View>

              {confirmDelete === workout.id ? (
                <View style={styles.deleteConfirmRow}>
                  <Text style={[styles.deleteConfirmText, { color: colors.foreground }]}>
                    {t("home.deleteConfirm")}
                  </Text>
                  <View style={styles.deleteConfirmBtns}>
                    <TouchableOpacity
                      onPress={() => setConfirmDelete(null)}
                      style={[styles.deleteCancelBtn, { borderColor: colors.border }]}
                    >
                      <Text style={[styles.deleteCancelText, { color: colors.foreground }]}>{t("common.cancel")}</Text>
                    </TouchableOpacity>
                    <TouchableOpacity
                      onPress={() => handleDelete(workout.id)}
                      style={[styles.deleteOkBtn, { backgroundColor: colors.destructive }]}
                    >
                      <Text style={styles.deleteOkText}>{t("common.delete")}</Text>
                    </TouchableOpacity>
                  </View>
                </View>
              ) : (
                <View style={styles.workoutStats}>
                  <View style={styles.workoutStat}>
                    <Feather name="layers" size={12} color={colors.mutedForeground} />
                    <Text style={[styles.workoutStatText, { color: colors.mutedForeground }]}>
                      {t("common.exercisesCount", { count: workout.exercises.length })}
                    </Text>
                  </View>
                  <View style={styles.workoutStat}>
                    <Feather name="repeat" size={12} color={colors.mutedForeground} />
                    <Text style={[styles.workoutStatText, { color: colors.mutedForeground }]}>
                      {t("common.setsCount", { count: getTotalSets(workout) })}
                    </Text>
                  </View>
                  <View style={styles.workoutStat}>
                    <Feather name="trending-up" size={12} color={colors.mutedForeground} />
                    <Text style={[styles.workoutStatText, { color: colors.mutedForeground }]}>
                      {t("home.volumeShort", { value: Math.round(getTotalVolume(workout)) })}
                    </Text>
                  </View>
                </View>
              )}
            </View>
          </View>
        )}
      />

      {/* Date edit modal */}
      <Modal
        visible={!!editingWorkout}
        transparent
        animationType="fade"
        onRequestClose={() => setEditingWorkout(null)}
      >
        <KeyboardAvoidingView
          behavior={Platform.OS === "ios" ? "padding" : "height"}
          style={styles.modalOverlay}
        >
          <TouchableOpacity
            style={StyleSheet.absoluteFill}
            activeOpacity={1}
            onPress={() => setEditingWorkout(null)}
          />
          <View style={[styles.modalBox, { backgroundColor: colors.card, borderColor: colors.border }]}>
            <Text style={[styles.modalTitle, { color: colors.foreground }]}>
              {t("home.editDate")}
            </Text>
            <Text style={[styles.modalSubtitle, { color: colors.mutedForeground }]}>
              {editingWorkout?.name}
            </Text>

            <View style={styles.dateRow}>
              <View style={styles.dateField}>
                <Text style={[styles.dateLabel, { color: colors.mutedForeground }]}>{t("home.date.day")}</Text>
                <TextInput
                  style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]}
                  value={editDay}
                  onChangeText={setEditDay}
                  keyboardType="number-pad"
                  maxLength={2}
                  placeholder={t("date.day")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                />
              </View>
              <Text style={[styles.dateSep, { color: colors.mutedForeground }]}>/</Text>
              <View style={styles.dateField}>
                <Text style={[styles.dateLabel, { color: colors.mutedForeground }]}>{t("home.date.month")}</Text>
                <TextInput
                  style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]}
                  value={editMonth}
                  onChangeText={setEditMonth}
                  keyboardType="number-pad"
                  maxLength={2}
                  placeholder={t("date.month")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                />
              </View>
              <Text style={[styles.dateSep, { color: colors.mutedForeground }]}>/</Text>
              <View style={[styles.dateField, { flex: 2 }]}>
                <Text style={[styles.dateLabel, { color: colors.mutedForeground }]}>{t("home.date.year")}</Text>
                <TextInput
                  style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]}
                  value={editYear}
                  onChangeText={setEditYear}
                  keyboardType="number-pad"
                  maxLength={4}
                  placeholder={t("date.year")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                />
              </View>
            </View>

            <View style={styles.timeRow}>
              <View style={styles.dateField}>
                <Text style={[styles.dateLabel, { color: colors.mutedForeground }]}>{t("home.date.hour")}</Text>
                <TextInput
                  style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]}
                  value={editHour}
                  onChangeText={setEditHour}
                  keyboardType="number-pad"
                  maxLength={2}
                  placeholder={t("date.hh")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                />
              </View>
              <Text style={[styles.dateSep, { color: colors.mutedForeground }]}>:</Text>
              <View style={styles.dateField}>
                <Text style={[styles.dateLabel, { color: colors.mutedForeground }]}>{t("home.date.minute")}</Text>
                <TextInput
                  style={[styles.dateInput, { color: colors.foreground, backgroundColor: colors.background, borderColor: colors.border }]}
                  value={editMinute}
                  onChangeText={setEditMinute}
                  keyboardType="number-pad"
                  maxLength={2}
                  placeholder={t("date.mm")}
                  placeholderTextColor={colors.mutedForeground}
                  selectTextOnFocus
                />
              </View>
            </View>

            {dateError && (
              <Text style={[styles.dateError, { color: colors.destructive }]}>{t("date.invalid")}</Text>
            )}

            <View style={styles.modalBtns}>
              <TouchableOpacity
                style={[styles.modalCancel, { borderColor: colors.border }]}
                onPress={() => setEditingWorkout(null)}
              >
                <Text style={[styles.modalCancelText, { color: colors.foreground }]}>{t("common.cancel")}</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.modalSave, { backgroundColor: colors.primary }]}
                onPress={saveEditDate}
              >
                <Feather name="check" size={16} color={colors.primaryForeground} />
                <Text style={[styles.modalSaveText, { color: colors.primaryForeground }]}>{t("common.save")}</Text>
              </TouchableOpacity>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  bgLogo: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    width: "100%",
    height: "100%",
    opacity: 0.35,
  },
  activeCard: {
    marginHorizontal: 20, borderRadius: 14, borderWidth: 1.5,
    padding: 16, flexDirection: "row", alignItems: "center",
    justifyContent: "space-between", marginBottom: 20,
  },
  activeCardLeft: { flexDirection: "row", alignItems: "center", gap: 12 },
  activeDot: { width: 10, height: 10, borderRadius: 5 },
  activeTitle: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  activeSubtitle: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
  startBtn: {
    marginHorizontal: 20, borderRadius: 14, paddingVertical: 16,
    flexDirection: "row", alignItems: "center", justifyContent: "center",
    gap: 10, marginBottom: 24,
  },
  startBtnText: { fontSize: 17, fontFamily: "Inter_700Bold" },
  statsRow: { flexDirection: "row", paddingHorizontal: 20, gap: 10, marginBottom: 10 },
  statCard: {
    flex: 1, borderRadius: 12, borderWidth: 1,
    padding: 14, alignItems: "center", gap: 4,
  },
  statValue: { fontSize: 18, fontFamily: "Inter_700Bold" },
  statLabel: { fontSize: 10, fontFamily: "Inter_400Regular", textAlign: "center" },
  gymTimeCard: {
    marginHorizontal: 20, borderRadius: 12, borderWidth: 1,
    padding: 14, flexDirection: "row", alignItems: "center",
    gap: 12, marginBottom: 28,
  },
  gymTimeIconCircle: {
    width: 40, height: 40, borderRadius: 20,
    alignItems: "center", justifyContent: "center",
  },
  gymTimeTexts: { flex: 1 },
  gymTimeValue: { fontSize: 22, fontFamily: "Inter_700Bold" },
  gymTimeLabel: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 2 },
  section: { paddingHorizontal: 20 },
  sectionTitle: { fontSize: 18, fontFamily: "Inter_700Bold", marginBottom: 14 },
  workoutCard: { borderRadius: 12, borderWidth: 1, padding: 14, marginBottom: 10 },
  workoutCardTop: {
    flexDirection: "row", justifyContent: "space-between",
    alignItems: "flex-start", marginBottom: 10,
  },
  workoutCardLeft: { flex: 1, marginRight: 8 },
  workoutName: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  workoutTime: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
  workoutCardActions: { flexDirection: "row", alignItems: "center", gap: 6 },
  bodyWeightBadge: {
    fontSize: 13, fontFamily: "Inter_600SemiBold",
    paddingHorizontal: 10, paddingVertical: 4, borderRadius: 8,
  },
  iconBtn: {
    width: 28, height: 28, borderRadius: 8, borderWidth: 1,
    alignItems: "center", justifyContent: "center",
  },
  workoutStats: { flexDirection: "row", gap: 14 },
  workoutStat: { flexDirection: "row", alignItems: "center", gap: 4 },
  workoutStatText: { fontSize: 12, fontFamily: "Inter_400Regular" },
  deleteConfirmRow: { gap: 8 },
  deleteConfirmText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  deleteConfirmBtns: { flexDirection: "row", gap: 8 },
  deleteCancelBtn: {
    flex: 1, paddingVertical: 8, borderRadius: 8, borderWidth: 1,
    alignItems: "center",
  },
  deleteCancelText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  deleteOkBtn: { flex: 1, paddingVertical: 8, borderRadius: 8, alignItems: "center" },
  deleteOkText: { fontSize: 13, fontFamily: "Inter_600SemiBold", color: "#fff" },
  empty: {
    alignItems: "center", paddingHorizontal: 40, paddingTop: 60, gap: 12,
  },
  emptyTitle: { fontSize: 20, fontFamily: "Inter_700Bold", marginTop: 8 },
  emptyText: {
    fontSize: 14, fontFamily: "Inter_400Regular",
    textAlign: "center", lineHeight: 22,
  },
  // Streak card
  streakCard: {
    marginHorizontal: 20, borderRadius: 16, borderWidth: 1.5,
    padding: 16, marginBottom: 16, gap: 12,
  },
  streakTop: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  streakLeft: { flexDirection: "row", alignItems: "center", gap: 12 },
  streakIconCircle: { width: 40, height: 40, borderRadius: 12, alignItems: "center", justifyContent: "center" },
  streakLabel: { fontSize: 11, fontFamily: "Inter_400Regular", marginBottom: 2 },
  streakCountRow: { flexDirection: "row", alignItems: "baseline", gap: 4 },
  streakCount: { fontSize: 28, fontFamily: "Inter_700Bold" },
  streakUnit: { fontSize: 14, fontFamily: "Inter_400Regular" },
  streakRight: { alignItems: "flex-end" },
  streakBestLabel: { fontSize: 10, fontFamily: "Inter_400Regular", marginBottom: 2 },
  streakBest: { fontSize: 16, fontFamily: "Inter_700Bold" },
  weeksSection: { gap: 8 },
  weeksHeading: { fontSize: 10, fontFamily: "Inter_500Medium" },
  weeksRow: { flexDirection: "row", gap: 5 },
  weekCell: { flex: 1, height: 34, borderRadius: 8, alignItems: "center", justifyContent: "center" },
  weekCellCount: { fontSize: 13, fontFamily: "Inter_700Bold" },
  legendRow: { flexDirection: "row", gap: 14, flexWrap: "wrap", marginTop: 1 },
  legendItem: { flexDirection: "row", alignItems: "center", gap: 5 },
  legendDot: { width: 9, height: 9, borderRadius: 3 },
  legendText: { fontSize: 10, fontFamily: "Inter_400Regular" },
  streakHint: { fontSize: 11, fontFamily: "Inter_400Regular" },
  // Modal
  modalOverlay: {
    flex: 1, backgroundColor: "rgba(0,0,0,0.6)",
    alignItems: "center", justifyContent: "center", padding: 24,
  },
  modalBox: {
    width: "100%", maxWidth: 360, borderRadius: 20,
    borderWidth: 1, padding: 24, gap: 16,
  },
  modalTitle: { fontSize: 18, fontFamily: "Inter_700Bold" },
  modalSubtitle: { fontSize: 13, fontFamily: "Inter_400Regular", marginTop: -8 },
  dateRow: { flexDirection: "row", alignItems: "flex-end", gap: 6 },
  timeRow: { flexDirection: "row", alignItems: "flex-end", gap: 6 },
  dateField: { flex: 1, gap: 4 },
  dateLabel: { fontSize: 11, fontFamily: "Inter_500Medium" },
  dateInput: {
    borderWidth: 1, borderRadius: 10, paddingHorizontal: 12,
    paddingVertical: 10, fontSize: 16, fontFamily: "Inter_600SemiBold",
    textAlign: "center",
  },
  dateSep: {
    fontSize: 20, fontFamily: "Inter_600SemiBold",
    paddingBottom: 10,
  },
  dateError: { fontSize: 13, fontFamily: "Inter_500Medium", marginTop: -4 },
  modalBtns: { flexDirection: "row", gap: 10, marginTop: 4 },
  modalCancel: {
    flex: 1, paddingVertical: 13, borderRadius: 12, borderWidth: 1,
    alignItems: "center", justifyContent: "center",
  },
  modalCancelText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  modalSave: {
    flex: 1, paddingVertical: 13, borderRadius: 12,
    flexDirection: "row", alignItems: "center", justifyContent: "center", gap: 6,
  },
  modalSaveText: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
});
