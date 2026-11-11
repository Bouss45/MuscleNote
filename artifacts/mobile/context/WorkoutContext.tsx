import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import { fr } from "@/constants/i18n/fr";
import { MuscleGroup } from "@/constants/muscles";
import { generateId } from "@/utils/id";
import { isArray, isObject, readJSON, removeKey, writeJSON } from "@/utils/storage";

export type { MuscleGroup } from "@/constants/muscles";

export interface Exercise {
  id: string;
  name: string;
  muscleGroup: MuscleGroup;
  /** Custom exercise "deleted" while still referenced by a session: hidden from the picker, kept for history. */
  archived?: boolean;
}

export interface Set {
  id: string;
  reps: number;
  weight: number;
  completed: boolean;
  // Cardio fields (optional)
  duration?: number;  // minutes
  distance?: number;  // km
  speed?: number;     // km/h
  incline?: number;   // %
}

export type SetUpdate = Partial<Pick<Set, "reps" | "weight" | "duration" | "distance" | "speed" | "incline">>;

export interface WorkoutExercise {
  exerciseId: string;
  sets: Set[];
  note?: string;
}

export interface Workout {
  id: string;
  name: string;
  date: string;
  bodyWeight: number;
  exercises: WorkoutExercise[];
  durationMinutes?: number;
}

export interface ExerciseHistory {
  date: string;
  bodyWeight: number;
  sets: Set[];
  workoutId: string;
}

// Built-in catalog. Display names come from the i18n dictionaries
// (`exercise.<id>` keys); the French name is the stored fallback.
const DEFAULT_EXERCISE_DEFS: [id: string, muscleGroup: MuscleGroup][] = [
  ["deadlift", "dos"], ["pullup", "dos"], ["row", "dos"], ["latpull", "dos"],
  ["tiragebucheron", "dos"], ["tirageh", "dos"],
  ["bench", "pecs"], ["incline", "pecs"], ["fly", "pecs"], ["dips", "pecs"],
  ["dbench", "pecs"], ["dincline", "pecs"], ["pecfly", "pecs"],
  ["curl", "biceps"], ["hammercurl", "biceps"], ["concentcurl", "biceps"],
  ["pushdown", "triceps"], ["overheadtri", "triceps"], ["skullcrusher", "triceps"], ["barfront", "triceps"],
  ["squat", "jambes"], ["legpress", "jambes"], ["legcurl", "jambes"], ["legext", "jambes"],
  ["lunges", "jambes"], ["calfraize", "jambes"], ["adductorpress", "jambes"],
  ["abductorpress", "jambes"], ["calfsit", "jambes"],
  ["ohp", "épaules"], ["lateralraise", "épaules"], ["frontraise", "épaules"],
  ["crunch", "abdos"], ["plank", "abdos"], ["legrise", "abdos"],
  ["treadmill", "cardio"], ["bike", "cardio"],
  ["wristcurl", "avant-bras"], ["wristext", "avant-bras"], ["reversecurl", "avant-bras"],
  ["pinchgrip", "avant-bras"], ["farmerwalk", "avant-bras"],
];

const DEFAULT_EXERCISES: Exercise[] = DEFAULT_EXERCISE_DEFS.map(([id, muscleGroup]) => ({
  id,
  muscleGroup,
  name: (fr as Record<string, string>)[`exercise.${id}`] ?? id,
}));

// O(1) membership test for "is this a built-in exercise?"
const DEFAULT_EXERCISE_IDS = new Set(DEFAULT_EXERCISES.map((e) => e.id));

export function isDefaultExercise(id: string): boolean {
  return DEFAULT_EXERCISE_IDS.has(id);
}

interface WorkoutContextType {
  /** Every known exercise, archived ones included (needed to label old sessions). */
  exercises: Exercise[];
  exerciseById: Map<string, Exercise>;
  workouts: Workout[];
  currentWorkout: Workout | null;
  startWorkout: (bodyWeight: number, name: string) => void;
  endWorkout: () => void;
  addExerciseToWorkout: (exerciseId: string) => void;
  removeExerciseFromWorkout: (exerciseId: string) => void;
  addSet: (exerciseId: string, reps: number, weight: number, extras?: SetUpdate) => void;
  updateSet: (exerciseId: string, setId: string, data: SetUpdate) => void;
  removeSet: (exerciseId: string, setId: string) => void;
  toggleSetComplete: (exerciseId: string, setId: string) => void;
  getExerciseHistory: (exerciseId: string) => ExerciseHistory[];
  addCustomExercise: (name: string, muscleGroup: MuscleGroup) => void;
  updateCustomExercise: (id: string, name: string, muscleGroup: MuscleGroup) => void;
  deleteCustomExercise: (id: string) => void;
  isCustomExercise: (id: string) => boolean;
  reorderExercises: (ids: string[]) => void;
  updateExerciseNote: (exerciseId: string, note: string) => void;
  updateWorkoutDate: (workoutId: string, newDate: string) => void;
  deleteWorkout: (workoutId: string) => void;
  patchWorkout: (workoutId: string, updates: Partial<Pick<Workout, "exercises" | "bodyWeight" | "name">>) => void;
  reloadData: () => Promise<void>;
}

const WorkoutContext = createContext<WorkoutContextType | undefined>(undefined);

const STORAGE_KEY = "@gymtracker_workouts";
const EXERCISES_KEY = "@gymtracker_exercises";
const CURRENT_WORKOUT_KEY = "@gymtracker_current_workout";

const MAX_SESSION_MS = 24 * 60 * 60 * 1000; // auto-save after 24 h

/** Clone sets for reuse in a new session: fresh ids, reset completion, carry over cardio fields. */
function cloneSetsForReuse(sets: Set[]): Set[] {
  return sets.map((s) => ({
    id: generateId(),
    reps: s.reps,
    weight: s.weight,
    completed: false,
    ...(s.duration != null ? { duration: s.duration } : {}),
    ...(s.distance != null ? { distance: s.distance } : {}),
    ...(s.speed != null ? { speed: s.speed } : {}),
    ...(s.incline != null ? { incline: s.incline } : {}),
  }));
}

function sortNewestFirst(list: Workout[]): Workout[] {
  return [...list].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
}

function mapExercise(w: Workout, exerciseId: string, fn: (e: WorkoutExercise) => WorkoutExercise): Workout {
  return { ...w, exercises: w.exercises.map((e) => (e.exerciseId === exerciseId ? fn(e) : e)) };
}

export function WorkoutProvider({ children }: { children: React.ReactNode }) {
  const [exercises, setExercisesState] = useState<Exercise[]>(DEFAULT_EXERCISES);
  const [workouts, setWorkoutsState] = useState<Workout[]>([]);
  const [currentWorkout, setCurrentState] = useState<Workout | null>(null);

  // Refs always hold the latest committed value, so every mutation below derives
  // from fresh data and React state updaters stay pure (no nested setState/IO).
  const exercisesRef = useRef<Exercise[]>(DEFAULT_EXERCISES);
  const workoutsRef = useRef<Workout[]>([]);
  const currentRef = useRef<Workout | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  function commitWorkouts(next: Workout[]) {
    workoutsRef.current = next;
    setWorkoutsState(next);
    writeJSON(STORAGE_KEY, next);
  }

  // The in-progress session is persisted on every change so it survives app closure.
  function commitCurrent(next: Workout | null) {
    currentRef.current = next;
    setCurrentState(next);
    if (next) writeJSON(CURRENT_WORKOUT_KEY, next);
    else removeKey(CURRENT_WORKOUT_KEY);
  }

  function updateCurrent(fn: (w: Workout) => Workout) {
    if (currentRef.current) commitCurrent(fn(currentRef.current));
  }

  function commitExercises(next: Exercise[]) {
    exercisesRef.current = next;
    setExercisesState(next);
    writeJSON(EXERCISES_KEY, next.filter((e) => !DEFAULT_EXERCISE_IDS.has(e.id)));
  }

  async function loadData() {
    const [savedWorkouts, custom, saved] = await Promise.all([
      readJSON<Workout[]>(STORAGE_KEY, isArray),
      readJSON<Exercise[]>(EXERCISES_KEY, isArray),
      readJSON<Workout>(CURRENT_WORKOUT_KEY, isObject),
    ]);

    const restored = sortNewestFirst(savedWorkouts ?? []);
    workoutsRef.current = restored;
    setWorkoutsState(restored);

    const allExercises = [...DEFAULT_EXERCISES, ...(custom ?? []).filter((e) => !DEFAULT_EXERCISE_IDS.has(e.id))];
    exercisesRef.current = allExercises;
    setExercisesState(allExercises);

    if (saved && Date.now() - new Date(saved.date).getTime() > MAX_SESSION_MS) {
      // Session forgotten for more than 24 h — file it into history. Its real
      // duration is unknown, so leave it unset rather than recording ~24 h.
      commitWorkouts(sortNewestFirst([saved, ...restored]));
      commitCurrent(null);
    } else {
      currentRef.current = saved ?? null;
      setCurrentState(saved ?? null);
    }
  }

  function startWorkout(bodyWeight: number, name: string) {
    // Most recent session with the same name (case-insensitive)
    const key = name.trim().toLowerCase();
    const lastSame = workoutsRef.current.find((w) => w.name.trim().toLowerCase() === key);

    // Pre-load exercises from that session: same order, same weights/reps, sets reset to not done
    const preloaded: WorkoutExercise[] = lastSame
      ? lastSame.exercises.map((we) => ({
          exerciseId: we.exerciseId,
          note: we.note,
          sets: cloneSetsForReuse(we.sets),
        }))
      : [];

    commitCurrent({
      id: generateId(),
      name,
      date: new Date().toISOString(),
      bodyWeight,
      exercises: preloaded,
    });
  }

  function endWorkout() {
    const cur = currentRef.current;
    if (!cur) return;
    const finished: Workout = {
      ...cur,
      durationMinutes: Math.round((Date.now() - new Date(cur.date).getTime()) / 60000),
    };
    commitWorkouts([finished, ...workoutsRef.current]);
    commitCurrent(null);
  }

  function addExerciseToWorkout(exerciseId: string) {
    if (!currentRef.current || currentRef.current.exercises.some((e) => e.exerciseId === exerciseId)) return;
    // Pre-fill from the most recent past session that used this exercise
    // (sets reset to not completed, cardio fields carried over).
    const lastExercise = workoutsRef.current
      .find((w) => w.exercises.some((e) => e.exerciseId === exerciseId))
      ?.exercises.find((e) => e.exerciseId === exerciseId);
    updateCurrent((w) => ({
      ...w,
      exercises: [
        ...w.exercises,
        { exerciseId, sets: cloneSetsForReuse(lastExercise?.sets ?? []), note: lastExercise?.note },
      ],
    }));
  }

  function removeExerciseFromWorkout(exerciseId: string) {
    updateCurrent((w) => ({ ...w, exercises: w.exercises.filter((e) => e.exerciseId !== exerciseId) }));
  }

  function addSet(exerciseId: string, reps: number, weight: number, extras?: SetUpdate) {
    const newSet: Set = { id: generateId(), reps, weight, completed: false, ...extras };
    updateCurrent((w) => mapExercise(w, exerciseId, (e) => ({ ...e, sets: [...e.sets, newSet] })));
  }

  function updateSet(exerciseId: string, setId: string, data: SetUpdate) {
    updateCurrent((w) =>
      mapExercise(w, exerciseId, (e) => ({
        ...e,
        sets: e.sets.map((s) => (s.id === setId ? { ...s, ...data } : s)),
      })),
    );
  }

  function removeSet(exerciseId: string, setId: string) {
    updateCurrent((w) =>
      mapExercise(w, exerciseId, (e) => ({ ...e, sets: e.sets.filter((s) => s.id !== setId) })),
    );
  }

  function toggleSetComplete(exerciseId: string, setId: string) {
    updateCurrent((w) =>
      mapExercise(w, exerciseId, (e) => ({
        ...e,
        sets: e.sets.map((s) => (s.id === setId ? { ...s, completed: !s.completed } : s)),
      })),
    );
  }

  /** Reorders the exercises of the in-progress session (drag & drop on the workout screen). */
  function reorderExercises(ids: string[]) {
    updateCurrent((w) => {
      const byId = new Map(w.exercises.map((e) => [e.exerciseId, e]));
      const reordered = ids.map((id) => byId.get(id)).filter((e): e is WorkoutExercise => !!e);
      // Keep any exercise the caller didn't list (e.g. one whose definition is missing).
      const rest = w.exercises.filter((e) => !ids.includes(e.exerciseId));
      return { ...w, exercises: [...reordered, ...rest] };
    });
  }

  function updateExerciseNote(exerciseId: string, note: string) {
    updateCurrent((w) => mapExercise(w, exerciseId, (e) => ({ ...e, note })));
  }

  function getExerciseHistory(exerciseId: string): ExerciseHistory[] {
    const history: ExerciseHistory[] = [];
    for (const workout of workouts) {
      const found = workout.exercises.find((e) => e.exerciseId === exerciseId);
      if (found && found.sets.length > 0) {
        history.push({ date: workout.date, bodyWeight: workout.bodyWeight, sets: found.sets, workoutId: workout.id });
      }
    }
    return history.reverse();
  }

  function updateWorkoutDate(workoutId: string, newDate: string) {
    commitWorkouts(
      sortNewestFirst(workoutsRef.current.map((w) => (w.id === workoutId ? { ...w, date: newDate } : w))),
    );
  }

  function deleteWorkout(workoutId: string) {
    commitWorkouts(workoutsRef.current.filter((w) => w.id !== workoutId));
  }

  function patchWorkout(workoutId: string, updates: Partial<Pick<Workout, "exercises" | "bodyWeight" | "name">>) {
    commitWorkouts(workoutsRef.current.map((w) => (w.id === workoutId ? { ...w, ...updates } : w)));
  }

  function addCustomExercise(name: string, muscleGroup: MuscleGroup) {
    commitExercises([...exercisesRef.current, { id: generateId(), name, muscleGroup }]);
  }

  function updateCustomExercise(id: string, name: string, muscleGroup: MuscleGroup) {
    commitExercises(exercisesRef.current.map((e) => (e.id === id ? { ...e, name, muscleGroup } : e)));
  }

  /** Removes a custom exercise — or only archives it if a session still references it. */
  function deleteCustomExercise(id: string) {
    const uses = (w: Workout | null) => !!w?.exercises.some((e) => e.exerciseId === id);
    const referenced = uses(currentRef.current) || workoutsRef.current.some(uses);
    commitExercises(
      referenced
        ? exercisesRef.current.map((e) => (e.id === id ? { ...e, archived: true } : e))
        : exercisesRef.current.filter((e) => e.id !== id),
    );
  }

  const exerciseById = new Map(exercises.map((e) => [e.id, e]));

  return (
    <WorkoutContext.Provider
      value={{
        exercises,
        exerciseById,
        workouts,
        currentWorkout,
        startWorkout,
        endWorkout,
        addExerciseToWorkout,
        removeExerciseFromWorkout,
        addSet,
        updateSet,
        removeSet,
        toggleSetComplete,
        getExerciseHistory,
        addCustomExercise,
        updateCustomExercise,
        deleteCustomExercise,
        isCustomExercise: (id) => !DEFAULT_EXERCISE_IDS.has(id),
        reorderExercises,
        updateExerciseNote,
        updateWorkoutDate,
        deleteWorkout,
        patchWorkout,
        reloadData: loadData,
      }}
    >
      {children}
    </WorkoutContext.Provider>
  );
}

export function useWorkout() {
  const ctx = useContext(WorkoutContext);
  if (!ctx) throw new Error("useWorkout must be used within WorkoutProvider");
  return ctx;
}
