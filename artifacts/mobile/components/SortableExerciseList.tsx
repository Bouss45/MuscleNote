import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useCallback, useRef, useState } from "react";
import { ScrollView, StyleSheet } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  runOnJS,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from "react-native-reanimated";
import { Exercise, WorkoutExercise } from "@/context/WorkoutContext";
import ExerciseCard from "./ExerciseCard";

const ITEM_GAP = 12;

export interface SortableItem {
  we: WorkoutExercise;
  exercise: Exercise;
}

interface SortableExerciseListProps {
  exercises: SortableItem[];
  onReorder: (ids: string[]) => void;
  bottomPad: number;
  header?: React.ReactNode;
  footer?: React.ReactNode;
  emptyState?: React.ReactNode;
}

interface ItemProps {
  index: number;
  activeIdx: ReturnType<typeof useSharedValue<number>>;
  dragDelta: ReturnType<typeof useSharedValue<number>>;
  heights: ReturnType<typeof useSharedValue<number[]>>;
  onDragStart: (index: number) => void;
  onDrop: (from: number, to: number) => void;
  children: React.ReactNode;
}

function computeProposedIdx(hs: number[], aIdx: number, delta: number, total: number): number {
  "worklet";
  let activeTopY = 0;
  for (let i = 0; i < aIdx; i++) activeTopY += (hs[i] ?? 80) + ITEM_GAP;
  const activeCenter = activeTopY + (hs[aIdx] ?? 80) / 2 + delta;

  let cumY = 0;
  let proposed = 0;
  for (let i = 0; i < total; i++) {
    const slotCenter = cumY + (hs[i] ?? 80) / 2;
    if (activeCenter > slotCenter) proposed = i + 1;
    cumY += (hs[i] ?? 80) + ITEM_GAP;
  }
  return Math.max(0, Math.min(total - 1, proposed));
}

function SortableExerciseItem({
  index,
  activeIdx,
  dragDelta,
  heights,
  onDragStart,
  onDrop,
  children,
}: ItemProps) {
  const colors = useColors();

  const animStyle = useAnimatedStyle(() => {
    const aIdx = activeIdx.value;
    const isActive = aIdx === index;

    if (aIdx === -1) {
      return {
        transform: [{ translateY: withSpring(0, { damping: 20, stiffness: 180 }) }, { scale: withSpring(1, { damping: 20 }) }],
        zIndex: 1,
        shadowOpacity: 0,
        elevation: 0,
      };
    }

    const hs = heights.value;
    const delta = dragDelta.value;
    const total = hs.length;
    const proposed = computeProposedIdx(hs, aIdx, delta, total);
    const activeH = (hs[aIdx] ?? 80) + ITEM_GAP;

    let shift = 0;
    if (!isActive) {
      if (aIdx < index && index <= proposed) shift = -activeH;
      else if (proposed <= index && index < aIdx) shift = activeH;
    }

    return {
      transform: [
        {
          translateY: isActive
            ? delta
            : withSpring(shift, { damping: 20, stiffness: 180 }),
        },
        {
          scale: isActive
            ? withSpring(1.04, { damping: 15 })
            : withSpring(1, { damping: 15 }),
        },
      ],
      zIndex: isActive ? 999 : 1,
      shadowColor: "#000",
      shadowOpacity: isActive ? 0.3 : withSpring(0, { damping: 20 }) as any,
      shadowRadius: isActive ? 14 : 0,
      elevation: isActive ? 14 : 0,
    };
  });

  const gesture = Gesture.Pan()
    .activateAfterLongPress(250)
    .onStart(() => {
      "worklet";
      activeIdx.value = index;
      dragDelta.value = 0;
      runOnJS(onDragStart)(index);
    })
    .onUpdate((e) => {
      "worklet";
      if (activeIdx.value === index) dragDelta.value = e.translationY;
    })
    .onEnd(() => {
      "worklet";
      const aIdx = activeIdx.value;
      if (aIdx !== index) return;
      const proposed = computeProposedIdx(heights.value, aIdx, dragDelta.value, heights.value.length);
      const from = aIdx;
      activeIdx.value = -1;
      dragDelta.value = 0;
      if (proposed !== from) runOnJS(onDrop)(from, proposed);
    })
    .onFinalize(() => {
      "worklet";
      if (activeIdx.value === index) {
        activeIdx.value = -1;
        dragDelta.value = 0;
      }
    });

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View
        style={[animStyle, styles.item]}
        onLayout={(e) => {
          const h = e.nativeEvent.layout.height;
          const cur = heights.value;
          const next = [...cur];
          while (next.length <= index) next.push(80);
          next[index] = h;
          heights.value = next;
        }}
      >
        {children}
      </Animated.View>
    </GestureDetector>
  );
}

export default function SortableExerciseList({
  exercises,
  onReorder,
  bottomPad,
  header,
  footer,
  emptyState,
}: SortableExerciseListProps) {
  const [isDragging, setIsDragging] = useState(false);
  const activeIdx = useSharedValue(-1);
  const dragDelta = useSharedValue(0);
  const heights = useSharedValue<number[]>([]);
  const orderRef = useRef<string[]>([]);
  orderRef.current = exercises.map((e) => e.exercise.id);

  const handleDragStart = useCallback(() => {
    setIsDragging(true);
  }, []);

  const handleDrop = useCallback(
    (from: number, to: number) => {
      setIsDragging(false);
      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      if (from === to) return;
      const ids = [...orderRef.current];
      const [moved] = ids.splice(from, 1);
      ids.splice(to, 0, moved);
      onReorder(ids);
    },
    [onReorder]
  );

  const handleDragCancel = useCallback(() => {
    setIsDragging(false);
  }, []);

  return (
    <ScrollView
      contentContainerStyle={{ padding: 16, paddingBottom: bottomPad }}
      showsVerticalScrollIndicator={false}
      scrollEnabled={!isDragging}
    >
      {header}

      {exercises.map((item, index) => (
        <SortableExerciseItem
          key={item.exercise.id}
          index={index}
          activeIdx={activeIdx}
          dragDelta={dragDelta}
          heights={heights}
          onDragStart={handleDragStart}
          onDrop={handleDrop}
        >
          <ExerciseCard
            exercise={item.exercise}
            workoutExercise={item.we}
            onNavigateHistory={() =>
              router.push({
                pathname: "/exercise-history",
                params: { exerciseId: item.exercise.id },
              })
            }
          />
        </SortableExerciseItem>
      ))}

      {exercises.length === 0 && emptyState}

      {footer}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  item: {
    marginBottom: ITEM_GAP,
  },
});
