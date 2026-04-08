import AsyncStorage from "@react-native-async-storage/async-storage";
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";

export type MuscleGroup =
  | "dos"
  | "pecs"
  | "biceps"
  | "triceps"
  | "jambes"
  | "épaules"
  | "abdos"
  | "cardio"
  | "avant-bras";

export interface Exercise {
  id: string;
  name: string;
  muscleGroup: MuscleGroup;
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

const DEFAULT_EXERCISES: Exercise[] = [
  // Dos
  { id: "deadlift", name: "Soulevé de terre", muscleGroup: "dos" },
  { id: "pullup", name: "Traction", muscleGroup: "dos" },
  { id: "row", name: "Rowing barre", muscleGroup: "dos" },
  { id: "latpull", name: "Tirage vertical", muscleGroup: "dos" },
  { id: "tiragebucheron", name: "Tirage bûcheron", muscleGroup: "dos" },
  { id: "tirageh", name: "Tirage horizontale", muscleGroup: "dos" },
  // Pecs
  { id: "bench", name: "Développé couché", muscleGroup: "pecs" },
  { id: "incline", name: "Développé incliné", muscleGroup: "pecs" },
  { id: "fly", name: "Écarté haltères", muscleGroup: "pecs" },
  { id: "dips", name: "Dips", muscleGroup: "pecs" },
  { id: "dbench", name: "Développé couché haltères", muscleGroup: "pecs" },
  { id: "dincline", name: "Développé incliné haltères", muscleGroup: "pecs" },
  { id: "pecfly", name: "Pec Fly", muscleGroup: "pecs" },
  // Biceps
  { id: "curl", name: "Curl barre", muscleGroup: "biceps" },
  { id: "hammercurl", name: "Curl marteau", muscleGroup: "biceps" },
  { id: "concentcurl", name: "Curl concentré", muscleGroup: "biceps" },
  // Triceps
  { id: "pushdown", name: "Pushdown câble", muscleGroup: "triceps" },
  { id: "overheadtri", name: "Extension nuque", muscleGroup: "triceps" },
  { id: "skullcrusher", name: "Skull crusher", muscleGroup: "triceps" },
  { id: "barfront", name: "Barre au front", muscleGroup: "triceps" },
  // Jambes
  { id: "squat", name: "Squat", muscleGroup: "jambes" },
  { id: "legpress", name: "Presse à cuisses", muscleGroup: "jambes" },
  { id: "legcurl", name: "Leg curl couché", muscleGroup: "jambes" },
  { id: "legext", name: "Leg extension", muscleGroup: "jambes" },
  { id: "lunges", name: "Fentes", muscleGroup: "jambes" },
  { id: "calfraize", name: "Mollets debout", muscleGroup: "jambes" },
  { id: "adductorpress", name: "Adducteur press", muscleGroup: "jambes" },
  { id: "abductorpress", name: "Abducteur press", muscleGroup: "jambes" },
  { id: "calfsit", name: "Mollets assis", muscleGroup: "jambes" },
  // Épaules
  { id: "ohp", name: "Développé militaire", muscleGroup: "épaules" },
  { id: "lateralraise", name: "Élévations latérales", muscleGroup: "épaules" },
  { id: "frontraise", name: "Élévations frontales", muscleGroup: "épaules" },
  // Abdos
  { id: "crunch", name: "Crunch", muscleGroup: "abdos" },
  { id: "plank", name: "Gainage", muscleGroup: "abdos" },
  { id: "legrise", name: "Relevé de jambes", muscleGroup: "abdos" },
  // Cardio
  { id: "treadmill", name: "Tapis roulant", muscleGroup: "cardio" },
  { id: "bike", name: "Vélo", muscleGroup: "cardio" },
  // Avant-bras
  { id: "wristcurl", name: "Curl poignet", muscleGroup: "avant-bras" },
  { id: "wristext", name: "Extension poignet", muscleGroup: "avant-bras" },
  { id: "reversecurl", name: "Curl inversé", muscleGroup: "avant-bras" },
  { id: "pinchgrip", name: "Pince (grip)", muscleGroup: "avant-bras" },
  { id: "farmerwalk", name: "Farmer's walk", muscleGroup: "avant-bras" },
];

interface WorkoutContextType {
  exercises: Exercise[];
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
  updateExerciseNote: (exerciseId: string, note: string) => void;
  updateWorkoutDate: (workoutId: string, newDate: string) => void;
  deleteWorkout: (workoutId: string) => void;
  patchWorkout: (workoutId: string, updates: Partial<Pick<Workout, "exercises" | "bodyWeight" | "name">>) => void;
}

const WorkoutContext = createContext<WorkoutContextType | undefined>(undefined);

const STORAGE_KEY = "@gymtracker_workouts";
const EXERCISES_KEY = "@gymtracker_exercises";

function generateId(): string {
  return Date.now().toString() + Math.random().toString(36).substr(2, 9);
}

export function WorkoutProvider({ children }: { children: React.ReactNode }) {
  const [exercises, setExercises] = useState<Exercise[]>(DEFAULT_EXERCISES);
  const [workouts, setWorkouts] = useState<Workout[]>([]);
  const [currentWorkout, setCurrentWorkout] = useState<Workout | null>(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    try {
      const [workoutsData, exercisesData] = await Promise.all([
        AsyncStorage.getItem(STORAGE_KEY),
        AsyncStorage.getItem(EXERCISES_KEY),
      ]);
      if (workoutsData) {
        setWorkouts(JSON.parse(workoutsData));
      }
      if (exercisesData) {
        const custom = JSON.parse(exercisesData) as Exercise[];
        setExercises([...DEFAULT_EXERCISES, ...custom]);
      }
    } catch (e) {
      // ignore
    }
  }

  async function saveWorkouts(w: Workout[]) {
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(w));
  }

  const startWorkout = useCallback((bodyWeight: number, name: string) => {
    const w: Workout = {
      id: generateId(),
      name,
      date: new Date().toISOString(),
      bodyWeight,
      exercises: [],
    };
    setCurrentWorkout(w);
  }, []);

  const endWorkout = useCallback(() => {
    if (!currentWorkout) return;
    const finished = { ...currentWorkout, durationMinutes: Math.round((Date.now() - new Date(currentWorkout.date).getTime()) / 60000) };
    setWorkouts((prev) => {
      const updated = [finished, ...prev];
      saveWorkouts(updated);
      return updated;
    });
    setCurrentWorkout(null);
  }, [currentWorkout]);

  const addExerciseToWorkout = useCallback((exerciseId: string) => {
    // Find the most recent past workout that contains this exercise
    const lastSession = workouts.find((w) =>
      w.exercises.some((e) => e.exerciseId === exerciseId)
    );
    const lastSets = lastSession
      ? lastSession.exercises.find((e) => e.exerciseId === exerciseId)?.sets ?? []
      : [];

    // Pre-fill with last session's sets (reset completed to false)
    const defaultSets: Set[] = lastSets.length > 0
      ? lastSets.map((s) => ({
          id: generateId(),
          reps: s.reps,
          weight: s.weight,
          completed: false,
        }))
      : [];

    setCurrentWorkout((prev) => {
      if (!prev) return prev;
      if (prev.exercises.find((e) => e.exerciseId === exerciseId)) return prev;
      return {
        ...prev,
        exercises: [...prev.exercises, { exerciseId, sets: defaultSets }],
      };
    });
  }, [workouts]);

  const removeExerciseFromWorkout = useCallback((exerciseId: string) => {
    setCurrentWorkout((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        exercises: prev.exercises.filter((e) => e.exerciseId !== exerciseId),
      };
    });
  }, []);

  const addSet = useCallback(
    (exerciseId: string, reps: number, weight: number, extras?: SetUpdate) => {
      setCurrentWorkout((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          exercises: prev.exercises.map((e) => {
            if (e.exerciseId !== exerciseId) return e;
            const newSet: Set = {
              id: generateId(),
              reps,
              weight,
              completed: false,
              ...extras,
            };
            return { ...e, sets: [...e.sets, newSet] };
          }),
        };
      });
    },
    []
  );

  const updateSet = useCallback(
    (exerciseId: string, setId: string, data: SetUpdate) => {
      setCurrentWorkout((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          exercises: prev.exercises.map((e) => {
            if (e.exerciseId !== exerciseId) return e;
            return {
              ...e,
              sets: e.sets.map((s) =>
                s.id === setId ? { ...s, ...data } : s
              ),
            };
          }),
        };
      });
    },
    []
  );

  const removeSet = useCallback((exerciseId: string, setId: string) => {
    setCurrentWorkout((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        exercises: prev.exercises.map((e) => {
          if (e.exerciseId !== exerciseId) return e;
          return { ...e, sets: e.sets.filter((s) => s.id !== setId) };
        }),
      };
    });
  }, []);

  const toggleSetComplete = useCallback(
    (exerciseId: string, setId: string) => {
      setCurrentWorkout((prev) => {
        if (!prev) return prev;
        return {
          ...prev,
          exercises: prev.exercises.map((e) => {
            if (e.exerciseId !== exerciseId) return e;
            return {
              ...e,
              sets: e.sets.map((s) =>
                s.id === setId ? { ...s, completed: !s.completed } : s
              ),
            };
          }),
        };
      });
    },
    []
  );

  const updateExerciseNote = useCallback((exerciseId: string, note: string) => {
    setCurrentWorkout((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        exercises: prev.exercises.map((e) =>
          e.exerciseId === exerciseId ? { ...e, note } : e
        ),
      };
    });
  }, []);

  const getExerciseHistory = useCallback(
    (exerciseId: string): ExerciseHistory[] => {
      const history: ExerciseHistory[] = [];
      for (const workout of workouts) {
        const found = workout.exercises.find(
          (e) => e.exerciseId === exerciseId
        );
        if (found && found.sets.length > 0) {
          history.push({
            date: workout.date,
            bodyWeight: workout.bodyWeight,
            sets: found.sets,
            workoutId: workout.id,
          });
        }
      }
      return history.reverse();
    },
    [workouts]
  );

  const updateWorkoutDate = useCallback((workoutId: string, newDate: string) => {
    setWorkouts((prev) => {
      const updated = prev
        .map((w) => (w.id === workoutId ? { ...w, date: newDate } : w))
        .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
      saveWorkouts(updated);
      return updated;
    });
  }, []);

  const deleteWorkout = useCallback((workoutId: string) => {
    setWorkouts((prev) => {
      const updated = prev.filter((w) => w.id !== workoutId);
      saveWorkouts(updated);
      return updated;
    });
  }, []);

  const patchWorkout = useCallback((workoutId: string, updates: Partial<Pick<Workout, "exercises" | "bodyWeight" | "name">>) => {
    setWorkouts((prev) => {
      const updated = prev.map((w) => w.id === workoutId ? { ...w, ...updates } : w);
      saveWorkouts(updated);
      return updated;
    });
  }, []);

  const addCustomExercise = useCallback(
    async (name: string, muscleGroup: MuscleGroup) => {
      const newEx: Exercise = {
        id: generateId(),
        name,
        muscleGroup,
      };
      setExercises((prev) => {
        const updated = [...prev, newEx];
        const custom = updated.filter(
          (e) => !DEFAULT_EXERCISES.find((d) => d.id === e.id)
        );
        AsyncStorage.setItem(EXERCISES_KEY, JSON.stringify(custom));
        return updated;
      });
    },
    []
  );

  const updateCustomExercise = useCallback(
    (id: string, name: string, muscleGroup: MuscleGroup) => {
      setExercises((prev) => {
        const updated = prev.map((e) => e.id === id ? { ...e, name, muscleGroup } : e);
        const custom = updated.filter((e) => !DEFAULT_EXERCISES.find((d) => d.id === e.id));
        AsyncStorage.setItem(EXERCISES_KEY, JSON.stringify(custom));
        return updated;
      });
    },
    []
  );

  const deleteCustomExercise = useCallback((id: string) => {
    setExercises((prev) => {
      const updated = prev.filter((e) => e.id !== id);
      const custom = updated.filter((e) => !DEFAULT_EXERCISES.find((d) => d.id === e.id));
      AsyncStorage.setItem(EXERCISES_KEY, JSON.stringify(custom));
      return updated;
    });
  }, []);

  const isCustomExercise = useCallback(
    (id: string) => !DEFAULT_EXERCISES.find((e) => e.id === id),
    []
  );

  return (
    <WorkoutContext.Provider
      value={{
        exercises,
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
        isCustomExercise,
        updateExerciseNote,
        updateWorkoutDate,
        deleteWorkout,
        patchWorkout,
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
