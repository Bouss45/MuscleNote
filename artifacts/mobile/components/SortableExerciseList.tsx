// Disable React Compiler for this file — Reanimated worklets are incompatible with it
"use no memo";

import * as Haptics from "expo-haptics";
import { router } from "expo-router";
import React, { useCallback, useMemo, useRef, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
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
  onDragStart: (index: number) => void;
  onDrop: (from: number, deltaY: number) => void;
  children: React.ReactNode;
}

function SortableExerciseItem({
  index,
  activeIdx,
  dragDelta,
  onDragStart,
  onDrop,
  children,
}: ItemProps) {
  const animStyle = useAnimatedStyle(() => {
    const isActive = activeIdx.value === index;
    if (!isActive) {
      return { transform: [{ translateY: 0 }, { scale: 1 }], zIndex: 1, opacity: 1 };
    }
    return {
      transform: [
        { translateY: dragDelta.value },
        { scale: withSpring(1.04, { damping: 15, stiffness: 200 }) },
      ],
      zIndex: 999,
      opacity: 0.95,
    };
  });

  const gesture = useMemo(
    () =>
      Gesture.Pan()
        .activateAfterLongPress(250)
        .onStart(() => {
          "worklet";
          activeIdx.value = index;
          dragDelta.value = 0;
          runOnJS(onDragStart)(index);
        })
        .onUpdate((e) => {
          "worklet";
          if (activeIdx.value === index) {
            dragDelta.value = e.translationY;
          }
        })
        .onEnd(() => {
          "worklet";
          const from = activeIdx.value;
          const delta = dragDelta.value;
          activeIdx.value = -1;
          dragDelta.value = 0;
          if (from === index) runOnJS(onDrop)(from, delta);
        })
        .onFinalize(() => {
          "worklet";
          if (activeIdx.value === index) {
            activeIdx.value = -1;
            dragDelta.value = 0;
          }
        }),
    [index]
  );

  return (
    <GestureDetector gesture={gesture}>
      <Animated.View style={[animStyle, styles.item]}>
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

  // Track item heights and cumulative Y positions via onLayout
  const heights = useRef<number[]>([]);
  const orderRef = useRef<string[]>([]);
  orderRef.current = exercises.map((e) => e.exercise.id);

  const handleDragStart = useCallback(() => {
    setIsDragging(true);
  }, []);

  const handleDrop = useCallback(
    (from: number, deltaY: number) => {
      setIsDragging(false);

      const hs = heights.current;
      if (!hs.length) return;

      // Compute cumulative Y positions for each slot
      const tops: number[] = [];
      let y = 0;
      for (let i = 0; i < hs.length; i++) {
        tops.push(y);
        y += (hs[i] ?? 80) + ITEM_GAP;
      }

      // Absolute Y center of dragged item after move
      const fromTop = tops[from] ?? 0;
      const fromHeight = hs[from] ?? 80;
      const newCenter = fromTop + fromHeight / 2 + deltaY;

      // Find target slot
      let to = from;
      for (let i = 0; i < tops.length; i++) {
        const slotCenter = (tops[i] ?? 0) + (hs[i] ?? 80) / 2;
        if (newCenter > slotCenter) to = i;
      }
      to = Math.max(0, Math.min(hs.length - 1, to));

      if (to === from) return;

      Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
      const ids = [...orderRef.current];
      const [moved] = ids.splice(from, 1);
      ids.splice(to, 0, moved);
      onReorder(ids);
    },
    [onReorder]
  );

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
          onDragStart={handleDragStart}
          onDrop={handleDrop}
        >
          <View
            onLayout={(e) => {
              heights.current[index] = e.nativeEvent.layout.height;
            }}
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
          </View>
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
