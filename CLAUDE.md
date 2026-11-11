# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A pnpm-workspace repo whose **only package is the Expo / React Native app in `artifacts/mobile/`** — "MuscleNote", a French-language gym workout tracker (with an in-app English option). It is a personal app installed from EAS-built APKs; publishing on the Google Play Store is **not** planned, so store-compliance work is out of scope. (The on-device AsyncStorage keys keep the legacy `@gymtracker_` prefix so existing users don't lose data on upgrade.)

The mobile app is **fully standalone and offline**: it imports no `@workspace/*` packages, makes no network calls, and persists everything to on-device AsyncStorage.

It was scaffolded from a Replit "PNPM_WORKSPACE" template. The unused template packages (`api-server`, `mockup-sandbox`, `lib/*`, `scripts`) and the Replit scaffolding (`.replit`, `.replitignore`, `attached_assets/`, `.agents/`) have since been **removed** — only `artifacts/mobile` remains under `artifacts/*`. The workspace wrapper (root `package.json`, `pnpm-workspace.yaml`) is kept because the app resolves `react` / `react-dom` through the `catalog:`.

## Commands

Run from the repo root; the active app lives in `artifacts/mobile`.

- Dev server: `pnpm --filter @workspace/mobile run dev` (= `expo start`)
- Typecheck the app: `pnpm --filter @workspace/mobile run typecheck` (= `tsc -p tsconfig.json --noEmit`)
- Typecheck the whole workspace: `pnpm run typecheck`

There is **no test suite** (no jest/test config) and **no lint script** — typecheck is the only static check. To catch Metro/bundling errors without a device, `EXPO_OFFLINE=1 npx expo export --platform android --output-dir <tmp>` (from `artifacts/mobile`) builds the Hermes bundle. The pure helpers in `utils/` have no RN imports and can be exercised directly with `node --experimental-strip-types`. (The root `pnpm run build` exists but only runs the typecheck, since no package defines `build`.)

### Builds go through EAS, not a workspace `build` script

The app intentionally has no local `build`/`serve` script; release artifacts come from EAS (`artifacts/mobile/eas.json`). Run these from inside `artifacts/mobile`:

- Preview APK: `eas build --platform android --profile preview`
- Store AAB / submit profiles also exist (`production`, `eas submit` → `google-service-account.json`), but are unused since the app isn't published.

### Installing dependencies (gotcha)

`pnpm` is mandatory. The root `preinstall` hook aborts with `Use pnpm instead` whenever `$npm_config_user_agent` doesn't start with `pnpm/` — which happens in non-interactive shells *even when you are calling pnpm*. Workaround:

```sh
npm_config_user_agent="pnpm/0.0.0 node/0.0.0 linux x64" pnpm install
```

`pnpm-workspace.yaml` enforces `minimumReleaseAge: 1440` (a package version must be ≥1 day old before it can be installed) as a supply-chain defense — do not disable it. `react`/`react-dom` are pinned to exactly `19.1.0` (Expo requirement) via the workspace catalog. The `overrides` block in that file is a Replit leftover that strips every non-linux-x64 native binary (esbuild, lightningcss, rollup, ngrok…); it is harmless on Linux but would likely need trimming to develop on macOS/Windows. The gitignored `.local/` directory is likewise leftover Replit agent state, not app code.

## Native rewrite — `android-app/` (branch `native`)

A Kotlin + Jetpack Compose rewrite of the Expo app is in progress in `android-app/`; **`SPEC.md` is its single reference** (behaviour, data format, theme, strings, decisions in § 11). Identity: `applicationId`/namespace `com.musclenote.android`, label « MuscleNote 2 », minSdk 26, compile/targetSdk 37. It coexists with the Expo app; data moves over by sharing a JSON backup (SPEC § 5.5).

- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`), committed wrapper (checksum-pinned `distributionSha256Sum`). AGP 9 compiles Kotlin itself ("built-in Kotlin"): **never apply `org.jetbrains.kotlin.android` in a module** — the root build only declares it `apply false` to pin the Kotlin version (keep it equal to the Compose compiler plugin version).
- There is no local Android SDK: builds run in CI. `.github/workflows/native-dev.yml` triggers on pushes to `native` touching `android-app/**`, builds a signed release APK (`versionCode = run_number`, `versionName = 0.1.<run_number>`; keystore decoded from secrets into `$RUNNER_TEMP`, passed through `ANDROID_KEYSTORE_PATH` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_ALIAS` / `ANDROID_KEY_PASSWORD`) and publishes the GitHub pre-release `v0.1.<run_number>`. Follow with `gh run watch`, debug with `gh run view --log-failed`. The repo is public: never print signing details in CI logs.
- Strings: `res/values/strings.xml` (FR, default) + `res/values-en/strings.xml`, keys converted from `constants/i18n/*.ts` (SPEC § 9.4). Theme tokens: `ui/theme/Color.kt` (`MuscleNoteTheme.colors`), Inter through Google downloadable fonts. Icons: Material Symbols vectors in `res/drawable`. Charts: the shared Canvas `ui/components/LineChart.kt` (SPEC § 2.13); date/number display helpers (`DateFormats`, `formatNumber`, `formatInteger`) in `ui/components/DisplayNames.kt`.
- Data: one repository per domain in `data/repository/` (`StateFlow` + `Mutex`, one `JsonDocument` file per document); pure business rules live beside them (`data/workout/WorkoutRules.kt`; statistics — streak, gym time, volume, 1RM, per-exercise history — in `WorkoutStats.kt`, Progrès tab calculations (§ 3.10) in `data/progress/ProgressStats.kt`, weigh-in rules in `data/nutrition/BodyWeightRules.kt`, all counting completed sets only) and are what the JVM tests target. Device-only settings (not in backups) go to `local_preferences.json` via `PreferencesRepository.localPreferences`. Screens never keep their own copy of persisted data and call repository mutations through `AppContainer.launchMutation { … }` (undispatched start on the fair `Mutex`, so keystrokes apply in order and survive leaving the screen). The in-progress session is written with a 300 ms debounce and flushed on `ON_STOP`.
- Notifications (`notifications/`, shared by the rest timer and the future supplement reminders): `AppChannel` (the three SPEC § 7.1 channels, names re-applied in the app language at app start and every activity creation), `ExactAlarms` (exact `RTC_WAKEUP`, inexact fallback), `AppNotifications.post` (silently skipped when notifications are denied), texts built through `Context.withAppLocale()`. Rest timer: `RestTimerRepository` (one rest, `rest_timer.json`; Android effects behind `RestTimerEffects`, a no-op in JVM tests) + `RestTimerNotifier` + `RestTimerReceiver`; the end alarm is the only source of the « rest finished » event, and `AppContainer.endWorkout()` / 24 h finalisation stop the rest.
- Supplement reminders (SPEC § 7.3): pure rules in `data/nutrition/` (`NutritionRules`, `ReminderSchedule` — next local wall-clock occurrence, DST-safe), Android side `SupplementReminderScheduler` behind the `SupplementReminders` interface (serial queue re-reading the latest supplements; alarm identity = `musclenote://reminder/{supplementId}/{reminderId}`), `SupplementReminderReceiver` (re-reads, notifies, reschedules tomorrow) and `ReminderResyncReceiver` (boot, package replaced, time/time-zone change, exact-alarm permission). `NutritionRepository` syncs alarms after every supplement change; `AppContainer` does a full resync at start-up and after an import. The current local day is `AppContainer.currentDay` (midnight timer, foreground, time-zone change); UI reads it through `rememberToday()`.
- Backup & export (Paramètres, `ui/settings/BackupViewModel.kt`): JSON v2 backup through `data/backup/` (`BackupWriter`; `BackupReader` = parse → version check → per-key validation → migrations → normalisation, v1 still imported) and `AppContainer.importBackup`, which also receives files shared by the Expo app (`SEND`/`VIEW` intent filters on `MainActivity`); CSV export is the pure `data/export/CsvExport.kt` (headers/format supplied by `CsvExportTexts`). Files are shared from `cacheDir/exports/` through the `FileProvider` (`res/xml/file_paths.xml`).
- Crash reports (SPEC § 10.9): `CrashHandler` is installed first in `MuscleNoteApplication.onCreate`, writes `filesDir/crashes/crash-<ms>.txt` through the pure `data/crash/CrashReports.kt` (5 most recent kept), flushes the in-progress session, then delegates to the default handler; Paramètres shows `CrashReportCard` only when a report exists (share the latest / delete all). Nothing is sent over the network.

## Mobile app architecture

Stack: Expo SDK 54, React Native 0.81 (New Architecture **on**), expo-router 6 (typed routes), React 19 with the **React Compiler enabled** (`app.json` `experiments.reactCompiler` + `babel.config.js`). Because components/values are auto-memoized, prefer algorithmic / data-structure wins over hand-written `useMemo`/`useCallback`. One file opts out with a top-of-file `"use no memo"` — `components/SortableExerciseList.tsx` — because its Reanimated worklets are incompatible with the compiler. Path alias `@/*` → `artifacts/mobile/`.

**Typography:** Inter (`@expo-google-fonts/inter`) loaded in `app/_layout.tsx` — weights 400/500/600/700. Always reference fonts as `fontFamily: "Inter_400Regular"` (or 500Medium/600SemiBold/700Bold) in `StyleSheet`.

### Routing (`app/`, expo-router file-based)

`app/_layout.tsx` is the root. It nests providers `SafeAreaProvider → PreferencesProvider (+ <NotificationChannels/>) → ErrorBoundary → WorkoutProvider → NutritionProvider → RestTimerProvider → GestureHandlerRootView → KeyboardProvider` and declares the navigation Stack. `app/(tabs)/_layout.tsx` defines four tabs — `index` (Accueil), `progress` (Progrès), `supplements` (Nutrition), `settings` (Paramètres) — via two implementations chosen at runtime: `NativeTabLayout` (iOS 26+ liquid glass, lazily `require`d) and `ClassicTabLayout` (everything else, including Android). Non-tab screens: `start-workout` and `exercise-picker` (modals), plus `workout`, `exercise-history`, `edit-workout`.

### Data layer — three providers in `context/`

All persisted state lives in providers, never in screens (a screen holding its own copy of storage goes stale and re-saves old data, e.g. after a backup import). Each provider follows the same pattern: React state for rendering **plus a ref holding the latest committed value**; every mutation computes from the ref, then calls a `commitX(next)` that updates ref + state + storage in one place. State updaters therefore stay pure (no nested `setState`, no I/O inside updaters) and rapid successive actions never read stale data. Storage goes through `utils/storage.ts`: `readJSON(key, validator)` backs up an unreadable value to `<key>_corrupt_<ts>` instead of letting the next save overwrite it; `writeJSON` logs failures instead of throwing.

- **`WorkoutContext.tsx`** (the heart of the app) — `exercises`, `workouts`, `currentWorkout`; `useWorkout()`.
- **`NutritionContext.tsx`** — daily calorie/water tracker, supplements + their reminder notifications, manual weigh-ins, and `today` (local day key, refreshed on resume / midnight); `useNutrition()`. Shared by the Nutrition and Progrès tabs and the Excel export.
- **`RestTimerContext.tsx`** — one session-wide rest timer (`start(exerciseName)` / `stop()`), shown by `components/RestTimerBanner.tsx` in the workout footer; `useRestTimer()`.

The rest of this section is about `WorkoutContext`. Core types: `Exercise`, `Set` (strength fields `reps`/`weight` plus **optional cardio fields** `duration`/`distance`/`speed`/`incline`), `WorkoutExercise`, `Workout` (optional `durationMinutes`).

AsyncStorage keys (all prefixed `@gymtracker_`):

| Key | Written by | Holds |
|---|---|---|
| `_workouts` | WorkoutContext | finished-workout history |
| `_exercises` | WorkoutContext | **custom exercises only** (merged on top of `DEFAULT_EXERCISES`) |
| `_current_workout` | WorkoutContext | the in-progress session |
| `_supplements`, `_supplements_taken`, `_daily_tracker` | NutritionContext | supplements (with reminders), taken-today record, calorie/water logs keyed by **local** day `"YYYY-MM-DD"` |
| `_body_weights` | NutritionContext | manual weigh-ins `{date, weight}[]` (one per local day); merged into the Progrès body-weight chart (a weigh-in overrides that day's session weight) |
| `_preferences` | `PreferencesContext` | `{ language, restSeconds }` — app language (`fr`/`en`) and rest-timer duration |

Behaviors to know before editing the context:

- `currentWorkout` is written to storage on every change (`commitCurrent`); on app load, a current workout older than 24 h is auto-finalized into history (`MAX_SESSION_MS`) with `durationMinutes` left unset (its real length is unknown).
- `startWorkout(bodyWeight, name)` pre-loads exercises/sets from the most recent session with the **same name** (sets reset to not-completed, weights/reps/cardio fields carried over). `addExerciseToWorkout` similarly pre-fills from the last session that used that exercise.
- `endWorkout()` stamps `durationMinutes` from elapsed time.
- `reloadData()` (= `loadData`) re-reads every key from storage; the settings **import** flow calls it (together with `useNutrition().reload` and `usePreferences().reload`) after overwriting storage.
- The built-in catalog (`DEFAULT_EXERCISE_DEFS` → `DEFAULT_EXERCISES`) lives in this file; "custom vs default" is decided purely by id membership (`isDefaultExercise(id)`). Built-in **display names come from i18n keys `exercise.<id>`** — always render names through `useExerciseName()` (`hooks/useExerciseName.ts`), never `exercise.name` directly. Look exercises up with the `exerciseById` map.
- `deleteCustomExercise(id)` removes the exercise, or only marks it `archived: true` if any session still references it (archived exercises are hidden from pickers but keep labelling history). Sets carried over into a new session are cloned through the `cloneSetsForReuse` helper (fresh ids, `completed` reset, cardio fields preserved).
- `reorderExercises(ids)` — reorders the exercises of the **in-progress session** (drag & drop in `SortableExerciseList`).
- `updateExerciseNote(exerciseId, note)` — writes a free-text note on the in-progress `WorkoutExercise`.
- `updateWorkoutDate(workoutId, isoDate)` — edits the timestamp of a finished workout (used by the home-screen date-edit modal).
- `deleteWorkout(workoutId)` — permanently removes a finished workout from history.
- `patchWorkout(workoutId, { exercises?, bodyWeight?, name? })` — partial update of a finished workout's fields (used by `edit-workout.tsx`).

### Theming — `constants/colors.ts` + `hooks/useColors.ts`

A single dark/maroon palette is defined under a `light` key. `useColors()` returns that palette plus the `radius` token; it would switch to a `dark` key if one existed (none does, so the app is effectively always this theme). Use `useColors()` rather than hardcoding colors.

### Preferences & i18n — `context/PreferencesContext.tsx` + `constants/i18n/`

`PreferencesProvider` (mounted just under `SafeAreaProvider`) exposes `usePreferences()` → `t(key, params?)`, `language` (`"fr" | "en"`), `locale`, `restSeconds`, plus `setLanguage` / `setRestSeconds`, all persisted to `@gymtracker_preferences`. The i18n is homemade (no library): `constants/i18n/fr.ts` is the **source of truth** (flat dotted keys, `{{var}}` interpolation, `_one` plural suffix selected via `t(key, { count })`); `en.ts` is typed `Record<TranslationKey, string>` so the typechecker flags any untranslated key; `translate()` falls back en → fr → key. For any new user-facing string, call `t()` and add the FR key to `fr.ts` **and** the EN to `en.ts`. **Coverage:** everything user-facing goes through `t()` — screens, shared components, chart labels, built-in exercise names (`exercise.*`), Excel sheet/column names (`excel.*`) and Android notification channel names (`channel.*`); only short unit labels such as `min` / `reps` / `kg` remain hardcoded. Use `locale` from `usePreferences()` for any `toLocaleString` / date formatting. The language switcher and rest-timer presets live in the Settings "PRÉFÉRENCES" section.

### Utilities & shared components

- `utils/number.ts` — `parseDecimal` / `parseInteger` for **every** numeric `TextInput` (French keyboards type `62,5`; bare `parseFloat` would store 62).
- `utils/date.ts` — `dayKey()` (local `"YYYY-MM-DD"`; never `toISOString().slice(0, 10)`, which is the UTC day), `dayKeyOf(iso)`, `parseDayKey(key)`, and `buildLocalDate(...)` which rejects impossible dates (31/02, 25 h) instead of rolling them over.
- `utils/backup.ts` — `BACKUP_KEYS` and `parseBackup(json)` (validates each key's shape before import). `utils/storage.ts`, `utils/id.ts` (`generateId`), `utils/text.ts` (`normalizeSearch`, accent-insensitive), `utils/strength.ts` (Epley 1RM).
- `constants/muscles.ts` — `MuscleGroup`, `MUSCLE_GROUPS` (display order) and `MUSCLE_COLORS`.
- `components/SetRow.tsx` — set editor used both live (with completion checkbox, `onToggle`) and in `edit-workout` (no `onToggle`). It **commits on every keystroke** — committing on blur loses the value when the user taps Save/Finish without leaving the field.
- `components/ExerciseSearchBar.tsx` (`useExerciseSearch` + search box) and `components/MuscleGroupChips.tsx` — shared by `exercise-picker` and the `edit-workout` picker modal.
- `components/LineChart.tsx` — custom SVG chart (via `react-native-svg`, not a chart library) used by Progrès and exercise history; it sizes itself to its container. Cardio has no load, so cardio charts/records use session duration (min) and distance instead of max weight.

### Home screen — `app/(tabs)/index.tsx`

Three feature areas worth knowing before editing:

- **Streak tracker** (`computeStreak`): counts consecutive qualifying ISO weeks (≥ 3 sessions = validated; partial weeks show in amber; the current in-progress week is never penalised). One missed week is tolerated before the streak breaks (`consecutiveMisses > 1` breaks it). Displays current streak, all-time best, and an 8-week mini calendar. The `isAlive` flag turns the card primary-coloured while the streak is active.
- **Gym time card** (`gymTimeStats`): sessions shorter than 10 min are replaced by the average duration of same-name sessions (to avoid polluting stats with accidental short records). Shows cumulative and average duration.
- **Per-workout actions**: each finished workout card has edit (→ `edit-workout`), date-edit (modal inline in this screen, calls `updateWorkoutDate`), and delete (two-step confirm, calls `deleteWorkout`) actions.

### Notifications — expo-notifications

Two Android channels are (re)created, with translated names, by `<NotificationChannels/>` in `app/_layout.tsx`: `supplements` and `rest-timer` (pass `channelId` in the **trigger**, not the content).

- **Rest timer** (`RestTimerContext`; duration = `restSeconds` preference, default 180 s). Double-alert avoidance: if it expires while the app is in the **foreground** it only fires haptics and cancels the scheduled OS notification; in the **background** the scheduled notification fires.
- **Supplement reminders** (`NutritionContext`): daily notifications tagged `data.kind = "supplement"`. All (un)scheduling runs through a serial queue that re-reads the latest state, so overlapping taps can't orphan a notification. On load, after an import and on language change, `resyncAllReminders()` cancels every supplement notification the OS holds and reschedules from the data (stored `notifId`s are never trusted across devices/versions).

### Data export / import — `app/(tabs)/settings.tsx`

JSON backup exports/imports the seven `BACKUP_KEYS` (every `@gymtracker_*` key except `_current_workout`). Import validates the file with `parseBackup`, writes via `multiSet`, then reloads all three persisted providers (workouts, nutrition, preferences). Excel export builds a translated 4-sheet workbook (sessions / sets / weigh-ins / nutrition) with `xlsx` (lazily `require`d), writes base64 through `expo-file-system/legacy`, and shares the file via `expo-sharing`. The app targets Android/iOS only — web code paths were removed.

### App config (`app.json`)

App identity: `name = "MuscleNote"`, `slug` / `scheme` = `musclenote`, `android.package = com.musclenote.app`. Note the mismatch with the AsyncStorage key prefix, which deliberately stays `@gymtracker_` for backward compatibility (see "What this repo is"). Known leftovers, deliberately left alone since the app isn't published: `expo-notifications` is pinned to `~0.29` while SDK 54 expects `~0.32` (`npx expo install --check` lists this and a few minor mismatches), and the unused `expo-location` / `expo-image-picker` still add location/camera permissions to the APK. The adaptive icon comes from `assets/images/icon.png`; the Android notification icon is a dedicated white-on-transparent mark at `assets/images/notification-icon.png` (Android renders the small status-bar icon as a monochrome silhouette, so the full-colour logo can't be used there); the home-screen background is `assets/images/home-bg.png`. Declared permissions: `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`.
