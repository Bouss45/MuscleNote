---
name: Workout exercise pre-load paths
description: Two separate code paths pre-fill an exercise from the last session — they must stay in sync
---

# Two exercise pre-load paths in WorkoutContext

When an exercise is pre-populated from the user's previous session, there are TWO
independent code paths that must both copy the SAME fields:

1. `startWorkout` — pre-loads all exercises when a session with a matching name is started.
2. `addExerciseToWorkout` — pre-loads a single exercise added mid-session.

**Rule:** both must carry over, from the last matching session's sets:
`reps`, `weight`, and the optional cardio fields `duration`, `distance`, `speed`,
`incline`, AND the per-exercise `note`.

**Why:** A bug shipped where `addExerciseToWorkout` only copied `reps`/`weight`,
silently dropping treadmill ("tapis roulant") cardio values and the note. The
cardio exercises (treadmill, bike) rely entirely on those optional fields, so the
omission made it look like values "weren't carried over from one session to another."

**How to apply:** Any change to how sessions pre-fill exercises must be applied to
BOTH functions, not just one. Keep the spread of optional cardio fields identical.
