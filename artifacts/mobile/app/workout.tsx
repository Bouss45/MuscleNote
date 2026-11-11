import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useEffect, useState } from "react";
import {
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { usePreferences } from "@/context/PreferencesContext";
import { useRestTimer } from "@/context/RestTimerContext";
import { useWorkout } from "@/context/WorkoutContext";
import { TranslationKey } from "@/constants/i18n";
import RestTimerBanner from "@/components/RestTimerBanner";
import SortableExerciseList from "@/components/SortableExerciseList";

const WARMUP_ITEMS: { icon: string; labelKey: TranslationKey; detailKey: TranslationKey }[] = [
  { icon: "wind",       labelKey: "workout.warmup.cardioLabel",    detailKey: "workout.warmup.cardioDetail" },
  { icon: "rotate-cw",  labelKey: "workout.warmup.shouldersLabel", detailKey: "workout.warmup.shouldersDetail" },
  { icon: "move",       labelKey: "workout.warmup.hipsLabel",      detailKey: "workout.warmup.hipsDetail" },
  { icon: "arrow-down", labelKey: "workout.warmup.stretchLabel",   detailKey: "workout.warmup.stretchDetail" },
  { icon: "activity",   labelKey: "workout.warmup.squatLabel",     detailKey: "workout.warmup.squatDetail" },
];

function WarmupCard() {
  const colors = useColors();
  const { t } = usePreferences();
  const [checked, setChecked] = useState<Set<number>>(new Set());
  const [collapsed, setCollapsed] = useState(false);
  const [dismissed, setDismissed] = useState(false);

  if (dismissed) return null;

  const allDone = checked.size === WARMUP_ITEMS.length;

  function toggle(i: number) {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setChecked((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });
  }

  return (
    <View style={[
      warmupStyles.card,
      {
        backgroundColor: allDone ? colors.primary + "18" : colors.card,
        borderColor: allDone ? colors.primary : colors.border,
      },
    ]}>
      {/* Header row */}
      <TouchableOpacity
        style={warmupStyles.cardHeader}
        onPress={() => setCollapsed((c) => !c)}
        activeOpacity={0.8}
      >
        <View style={warmupStyles.cardHeaderLeft}>
          <View style={[warmupStyles.iconCircle, { backgroundColor: colors.primary + "20" }]}>
            <Feather name="zap" size={14} color={colors.primary} />
          </View>
          <View>
            <Text style={[warmupStyles.cardTitle, { color: colors.foreground }]}>
              {t("workout.warmup.title")}
            </Text>
            <Text style={[warmupStyles.cardSub, { color: colors.mutedForeground }]}>
              {t("workout.warmup.count", { done: checked.size, total: WARMUP_ITEMS.length })}
            </Text>
          </View>
        </View>
        <View style={warmupStyles.cardHeaderRight}>
          {allDone && (
            <View style={[warmupStyles.doneBadge, { backgroundColor: colors.primary }]}>
              <Feather name="check" size={11} color="#fff" />
              <Text style={warmupStyles.doneBadgeText}>{t("workout.warmup.done")}</Text>
            </View>
          )}
          <TouchableOpacity
            onPress={() => setDismissed(true)}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="x" size={16} color={colors.mutedForeground} />
          </TouchableOpacity>
          <Feather
            name={collapsed ? "chevron-down" : "chevron-up"}
            size={16}
            color={colors.mutedForeground}
          />
        </View>
      </TouchableOpacity>

      {/* Progress bar */}
      <View style={[warmupStyles.progressBar, { backgroundColor: colors.muted }]}>
        <View style={[
          warmupStyles.progressFill,
          {
            backgroundColor: colors.primary,
            width: `${(checked.size / WARMUP_ITEMS.length) * 100}%`,
          },
        ]} />
      </View>

      {/* Items list */}
      {!collapsed && (
        <View style={warmupStyles.itemsList}>
          {WARMUP_ITEMS.map((item, i) => {
            const done = checked.has(i);
            return (
              <TouchableOpacity
                key={i}
                style={[
                  warmupStyles.item,
                  { borderBottomColor: colors.border },
                  i === WARMUP_ITEMS.length - 1 && { borderBottomWidth: 0 },
                ]}
                onPress={() => toggle(i)}
                activeOpacity={0.7}
              >
                <View style={[
                  warmupStyles.checkbox,
                  {
                    borderColor: done ? colors.primary : colors.border,
                    backgroundColor: done ? colors.primary : "transparent",
                  },
                ]}>
                  {done && <Feather name="check" size={11} color="#fff" />}
                </View>
                <View style={warmupStyles.itemText}>
                  <Text style={[
                    warmupStyles.itemLabel,
                    { color: done ? colors.mutedForeground : colors.foreground },
                    done && warmupStyles.strikethrough,
                  ]}>
                    {t(item.labelKey)}
                  </Text>
                  <Text style={[warmupStyles.itemDetail, { color: colors.mutedForeground }]}>
                    {t(item.detailKey)}
                  </Text>
                </View>
                <View style={[warmupStyles.itemIconCircle, { backgroundColor: colors.muted }]}>
                  <Feather name={item.icon as any} size={13} color={colors.mutedForeground} />
                </View>
              </TouchableOpacity>
            );
          })}
        </View>
      )}
    </View>
  );
}

export default function WorkoutScreen() {
  const colors = useColors();
  const { t } = usePreferences();
  const insets = useSafeAreaInsets();
  const { currentWorkout, exerciseById, endWorkout, reorderExercises } = useWorkout();
  const restTimer = useRestTimer();
  const [confirming, setConfirming] = useState(false);
  const [elapsed, setElapsed] = useState("0:00");

  const topPad = insets.top;
  const bottomPad = insets.bottom + 90;

  useEffect(() => {
    if (!currentWorkout) return;
    function tick() {
      const diff = Date.now() - new Date(currentWorkout!.date).getTime();
      const min = Math.floor(diff / 60000);
      const sec = Math.floor((diff % 60000) / 1000);
      setElapsed(`${min}:${sec.toString().padStart(2, "0")}`);
    }
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [currentWorkout?.date]);

  if (!currentWorkout) {
    return (
      <View
        style={[
          styles.container,
          {
            backgroundColor: colors.background,
            alignItems: "center",
            justifyContent: "center",
          },
        ]}
      >
        <Text style={[styles.emptyTitle, { color: colors.foreground }]}>
          {t("workout.noActive")}
        </Text>
        <TouchableOpacity onPress={() => router.replace("/")}>
          <Text style={[styles.backLink, { color: colors.primary }]}>
            {t("common.back")}
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  function handleFinish() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setConfirming(true);
  }

  function handleConfirmFinish() {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    restTimer.stop();
    router.replace("/");
    endWorkout();
  }

  const currentExercises = currentWorkout.exercises.flatMap((we) => {
    const exercise = exerciseById.get(we.exerciseId);
    return exercise ? [{ we, exercise }] : [];
  });

  const completedCount = currentWorkout.exercises.reduce(
    (sum, e) => sum + e.sets.filter((s) => s.completed).length,
    0
  );
  const totalSets = currentWorkout.exercises.reduce(
    (sum, e) => sum + e.sets.length,
    0
  );

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Header */}
      <View
        style={[
          styles.header,
          {
            paddingTop: topPad + 12,
            borderBottomColor: colors.border,
            backgroundColor: colors.background,
          },
        ]}
      >
        <View style={styles.headerTop}>
          <View style={styles.headerLeft}>
            <TouchableOpacity
              onPress={() => router.replace("/")}
              hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
              style={styles.backBtn}
            >
              <Feather name="chevron-left" size={22} color={colors.foreground} />
            </TouchableOpacity>
            <View>
              <Text style={[styles.sessionLabel, { color: colors.mutedForeground }]}>
                {t("workout.inProgress")}
              </Text>
              <Text style={[styles.bodyWeight, { color: colors.foreground }]}>
                {currentWorkout.name}
              </Text>
              <Text style={[styles.sessionWeight, { color: colors.mutedForeground }]}>
                {currentWorkout.bodyWeight} kg
              </Text>
            </View>
          </View>
          <View style={styles.timerBadge}>
            <Feather name="clock" size={14} color={colors.primary} />
            <Text style={[styles.timerText, { color: colors.primary }]}>
              {elapsed}
            </Text>
          </View>
        </View>
        {totalSets > 0 && (
          <View style={styles.progressRow}>
            <View style={[styles.progressBar, { backgroundColor: colors.muted }]}>
              <View
                style={[
                  styles.progressFill,
                  {
                    backgroundColor: colors.primary,
                    width: `${(completedCount / totalSets) * 100}%`,
                  },
                ]}
              />
            </View>
            <Text style={[styles.progressLabel, { color: colors.mutedForeground }]}>
              {t("workout.setsProgress", { done: completedCount, total: totalSets })}
            </Text>
          </View>
        )}
      </View>

      <SortableExerciseList
        exercises={currentExercises}
        onReorder={reorderExercises}
        bottomPad={bottomPad}
        header={<WarmupCard />}
        footer={
          <TouchableOpacity
            style={[
              styles.addExerciseBtn,
              { backgroundColor: colors.card, borderColor: colors.border },
            ]}
            onPress={() => router.push("/exercise-picker")}
            activeOpacity={0.8}
          >
            <Feather name="plus-circle" size={20} color={colors.primary} />
            <Text style={[styles.addExerciseBtnText, { color: colors.primary }]}>
              {t("workout.addExercise")}
            </Text>
          </TouchableOpacity>
        }
        emptyState={
          <View style={styles.emptyExercises}>
            <Feather name="activity" size={36} color={colors.mutedForeground} />
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              {t("workout.emptyExercises")}
            </Text>
          </View>
        }
      />

      {/* Finish button / inline confirmation */}
      <View
        style={[
          styles.footer,
          {
            paddingBottom: insets.bottom + 16,
            borderTopColor: colors.border,
            backgroundColor: colors.background,
          },
        ]}
      >
        <RestTimerBanner />
        {confirming ? (
          <View style={styles.confirmRow}>
            <Text style={[styles.confirmText, { color: colors.foreground }]}>
              {t("workout.finishConfirm")}
            </Text>
            <View style={styles.confirmBtns}>
              <TouchableOpacity
                style={[styles.confirmCancel, { borderColor: colors.border }]}
                onPress={() => setConfirming(false)}
                activeOpacity={0.8}
              >
                <Text style={[styles.confirmCancelText, { color: colors.foreground }]}>
                  {t("common.cancel")}
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.confirmOk, { backgroundColor: colors.destructive }]}
                onPress={handleConfirmFinish}
                activeOpacity={0.85}
              >
                <Feather name="check" size={16} color="#fff" />
                <Text style={styles.confirmOkText}>{t("workout.finishShort")}</Text>
              </TouchableOpacity>
            </View>
          </View>
        ) : (
          <TouchableOpacity
            style={[styles.finishBtn, { borderColor: colors.destructive }]}
            onPress={handleFinish}
            activeOpacity={0.85}
          >
            <Feather name="check-square" size={18} color={colors.destructive} />
            <Text style={[styles.finishBtnText, { color: colors.destructive }]}>
              {t("workout.finish")}
            </Text>
          </TouchableOpacity>
        )}
      </View>
    </View>
  );
}

const warmupStyles = StyleSheet.create({
  card: {
    borderRadius: 14,
    borderWidth: 1,
    marginBottom: 12,
    overflow: "hidden",
  },
  cardHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    padding: 14,
  },
  cardHeaderLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  iconCircle: {
    width: 32,
    height: 32,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  cardTitle: {
    fontSize: 14,
    fontFamily: "Inter_600SemiBold",
  },
  cardSub: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    marginTop: 1,
  },
  cardHeaderRight: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  doneBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 6,
  },
  doneBadgeText: {
    fontSize: 11,
    fontFamily: "Inter_600SemiBold",
    color: "#fff",
  },
  progressBar: {
    height: 3,
    width: "100%",
  },
  progressFill: {
    height: 3,
  },
  itemsList: {
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  item: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 10,
    borderBottomWidth: 1,
  },
  checkbox: {
    width: 20,
    height: 20,
    borderRadius: 6,
    borderWidth: 1.5,
    alignItems: "center",
    justifyContent: "center",
  },
  itemText: {
    flex: 1,
  },
  itemLabel: {
    fontSize: 13,
    fontFamily: "Inter_500Medium",
  },
  strikethrough: {
    textDecorationLine: "line-through",
    opacity: 0.5,
  },
  itemDetail: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    marginTop: 1,
  },
  itemIconCircle: {
    width: 28,
    height: 28,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
  },
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    paddingHorizontal: 20,
    paddingBottom: 14,
    borderBottomWidth: 1,
  },
  headerTop: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 12,
  },
  headerLeft: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  backBtn: {
    width: 34,
    height: 34,
    alignItems: "center",
    justifyContent: "center",
  },
  sessionLabel: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    marginBottom: 2,
  },
  bodyWeight: {
    fontSize: 20,
    fontFamily: "Inter_700Bold",
  },
  sessionWeight: {
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    marginTop: 1,
  },
  timerBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
  },
  timerText: {
    fontSize: 18,
    fontFamily: "Inter_700Bold",
  },
  progressRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  progressBar: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    overflow: "hidden",
  },
  progressFill: {
    height: 4,
    borderRadius: 2,
  },
  progressLabel: {
    fontSize: 11,
    fontFamily: "Inter_500Medium",
  },
  addExerciseBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    padding: 16,
    borderRadius: 14,
    borderWidth: 1.5,
    borderStyle: "dashed",
    marginBottom: 12,
  },
  addExerciseBtnText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  emptyExercises: {
    alignItems: "center",
    gap: 12,
    paddingVertical: 40,
  },
  emptyText: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
    textAlign: "center",
  },
  footer: {
    paddingHorizontal: 20,
    paddingTop: 12,
    borderTopWidth: 1,
  },
  finishBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    padding: 14,
    borderRadius: 12,
    borderWidth: 1.5,
  },
  finishBtnText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  confirmRow: {
    gap: 12,
  },
  confirmText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
    textAlign: "center",
  },
  confirmBtns: {
    flexDirection: "row",
    gap: 10,
  },
  confirmCancel: {
    flex: 1,
    paddingVertical: 13,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: "center",
    justifyContent: "center",
  },
  confirmCancelText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  confirmOk: {
    flex: 1,
    flexDirection: "row",
    paddingVertical: 13,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
  },
  confirmOkText: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
    color: "#fff",
  },
  emptyTitle: {
    fontSize: 20,
    fontFamily: "Inter_700Bold",
    marginBottom: 10,
  },
  backLink: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
});
