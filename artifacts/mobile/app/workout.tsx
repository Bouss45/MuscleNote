import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useState } from "react";
import {
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useColors } from "@/hooks/useColors";
import { useWorkout } from "@/context/WorkoutContext";
import ExerciseCard from "@/components/ExerciseCard";

const WARMUP_ITEMS = [
  { icon: "wind",       label: "Cardio léger",         detail: "5-10 min vélo ou rameur" },
  { icon: "rotate-cw", label: "Rotation des épaules",  detail: "15 reps chaque côté" },
  { icon: "move",       label: "Mobilité des hanches",  detail: "10 reps cercles" },
  { icon: "arrow-down", label: "Étirements dynamiques", detail: "1 min jambes & dos" },
  { icon: "activity",   label: "Squats à vide",         detail: "15 reps lents" },
];

function WarmupCard() {
  const colors = useColors();
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
              Échauffement
            </Text>
            <Text style={[warmupStyles.cardSub, { color: colors.mutedForeground }]}>
              {checked.size}/{WARMUP_ITEMS.length} exercices
            </Text>
          </View>
        </View>
        <View style={warmupStyles.cardHeaderRight}>
          {allDone && (
            <View style={[warmupStyles.doneBadge, { backgroundColor: colors.primary }]}>
              <Feather name="check" size={11} color="#fff" />
              <Text style={warmupStyles.doneBadgeText}>Fait !</Text>
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
                    {item.label}
                  </Text>
                  <Text style={[warmupStyles.itemDetail, { color: colors.mutedForeground }]}>
                    {item.detail}
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
  const insets = useSafeAreaInsets();
  const { currentWorkout, exercises, endWorkout } = useWorkout();
  const [confirming, setConfirming] = useState(false);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 + 80 : insets.bottom + 90;

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
          Aucune séance active
        </Text>
        <TouchableOpacity onPress={() => router.replace("/")}>
          <Text style={[styles.backLink, { color: colors.primary }]}>
            Retour
          </Text>
        </TouchableOpacity>
      </View>
    );
  }

  function getElapsed() {
    const diff = Date.now() - new Date(currentWorkout!.date).getTime();
    const min = Math.floor(diff / 60000);
    const sec = Math.floor((diff % 60000) / 1000);
    return `${min}:${sec.toString().padStart(2, "0")}`;
  }

  function handleFinish() {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setConfirming(true);
  }

  function handleConfirmFinish() {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    router.replace("/");
    endWorkout();
  }

  const currentExercises = currentWorkout.exercises
    .map((we) => ({
      we,
      exercise: exercises.find((e) => e.id === we.exerciseId),
    }))
    .filter((x) => x.exercise !== undefined) as {
    we: typeof currentWorkout.exercises[0];
    exercise: typeof exercises[0];
  }[];

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
                Séance en cours
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
              {getElapsed()}
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
              {completedCount}/{totalSets} séries
            </Text>
          </View>
        )}
      </View>

      <ScrollView
        contentContainerStyle={{ padding: 16, paddingBottom: bottomPad }}
        showsVerticalScrollIndicator={false}
      >
        {/* Warmup card — always shown at top */}
        <WarmupCard />

        {currentExercises.map(({ we, exercise }) => (
          <ExerciseCard
            key={exercise.id}
            exercise={exercise}
            workoutExercise={we}
            onNavigateHistory={() =>
              router.push({
                pathname: "/exercise-history",
                params: { exerciseId: exercise.id },
              })
            }
          />
        ))}

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
            Ajouter un exercice
          </Text>
        </TouchableOpacity>

        {currentExercises.length === 0 && (
          <View style={styles.emptyExercises}>
            <Feather name="activity" size={36} color={colors.mutedForeground} />
            <Text style={[styles.emptyText, { color: colors.mutedForeground }]}>
              Ajoute des exercices pour commencer
            </Text>
          </View>
        )}
      </ScrollView>

      {/* Finish button / inline confirmation */}
      <View
        style={[
          styles.footer,
          {
            paddingBottom: Platform.OS === "web" ? 34 : insets.bottom + 16,
            borderTopColor: colors.border,
            backgroundColor: colors.background,
          },
        ]}
      >
        {confirming ? (
          <View style={styles.confirmRow}>
            <Text style={[styles.confirmText, { color: colors.foreground }]}>
              Terminer la séance ?
            </Text>
            <View style={styles.confirmBtns}>
              <TouchableOpacity
                style={[styles.confirmCancel, { borderColor: colors.border }]}
                onPress={() => setConfirming(false)}
                activeOpacity={0.8}
              >
                <Text style={[styles.confirmCancelText, { color: colors.foreground }]}>
                  Annuler
                </Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.confirmOk, { backgroundColor: colors.destructive }]}
                onPress={handleConfirmFinish}
                activeOpacity={0.85}
              >
                <Feather name="check" size={16} color="#fff" />
                <Text style={styles.confirmOkText}>Terminer</Text>
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
              Terminer la séance
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
