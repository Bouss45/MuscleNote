import { TranslationKey } from "@/constants/i18n";
import { usePreferences } from "@/context/PreferencesContext";
import { Exercise, isDefaultExercise } from "@/context/WorkoutContext";

/**
 * Display name of an exercise in the current language: built-in exercises are
 * translated (`exercise.<id>` keys), custom ones keep the name the user typed.
 */
export function useExerciseName() {
  const { t } = usePreferences();
  return (exercise: Exercise) =>
    isDefaultExercise(exercise.id) ? t(`exercise.${exercise.id}` as TranslationKey) : exercise.name;
}
