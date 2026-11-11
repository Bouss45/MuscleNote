# MuscleNote

Carnet de musculation pour Android (et iOS), entièrement **hors ligne** : séances, progression, nutrition et compléments, sans compte ni serveur. Toutes les données restent sur le téléphone.

Application personnelle, distribuée sous forme d'APK via EAS — elle n'est pas publiée sur le Play Store.

## Fonctionnalités

### Séances
- Démarrage d'une séance nommée : les exercices, séries, charges et répétitions de la dernière séance du même nom sont **pré-remplis** automatiquement.
- Catalogue d'une quarantaine d'exercices intégrés, répartis en 9 groupes musculaires (dos, pecs, biceps, triceps, jambes, épaules, abdos, cardio, avant-bras), plus des **exercices personnalisés**.
- Recherche d'exercices insensible aux accents, filtre par groupe musculaire.
- Réorganisation des exercices par **glisser-déposer**, note libre par exercice.
- Exercices **cardio** : durée, distance, vitesse, inclinaison.
- **Minuteur de repos** (durée réglable) avec notification si l'app est en arrière-plan.
- Après coup : modification complète d'une séance, de sa date, ou suppression.

### Progrès
- Courbes du poids de corps (poids saisi en séance + pesées manuelles), des calories et de l'hydratation.
- Progression par groupe musculaire : charge max, volume max, exercices les plus utilisés.
- Historique par exercice avec **1RM estimé** (formule d'Epley).

### Accueil
- **Série hebdomadaire** : une semaine est validée à partir de 3 séances ; une semaine manquée est tolérée. Record personnel et calendrier des 8 dernières semaines.
- **Temps passé à la salle** : cumul et durée moyenne des séances.

### Nutrition
- Suivi quotidien des calories et de l'eau, avec objectifs.
- Compléments alimentaires avec **rappels quotidiens** par notification.
- Pesées manuelles.

### Paramètres
- Langue : **français** ou **anglais**.
- Durée du minuteur de repos.
- **Sauvegarde / restauration JSON** de toutes les données.
- **Export Excel** (séances, séries, pesées, nutrition) partageable.

## Stack technique

- [Expo](https://expo.dev) SDK 54, React Native 0.81 (New Architecture), React 19 avec le **React Compiler**
- expo-router 6 (routes typées), TypeScript
- AsyncStorage pour la persistance, expo-notifications, react-native-reanimated, react-native-svg (graphiques maison)
- i18n maison (`constants/i18n/`), `xlsx` pour l'export Excel

## Structure

```
.
├── package.json, pnpm-workspace.yaml   # wrapper pnpm (catalogue react/react-dom)
└── artifacts/mobile/                   # l'application Expo
    ├── app/            # écrans (expo-router) — onglets dans app/(tabs)/
    ├── components/     # composants partagés (SetRow, LineChart, RestTimerBanner…)
    ├── context/        # état persistant : Workout, Nutrition, RestTimer, Preferences
    ├── constants/      # couleurs, groupes musculaires, traductions (i18n/fr.ts, en.ts)
    ├── hooks/          # useColors, useExerciseName
    └── utils/          # helpers purs : dates, nombres, stockage, sauvegarde, 1RM…
```

## Développement

Prérequis : Node.js et **pnpm** (npm/yarn sont refusés par le hook `preinstall`).

```sh
# Installation (la variable contourne le hook preinstall dans un shell non interactif)
npm_config_user_agent="pnpm/0.0.0 node/0.0.0 linux x64" pnpm install

# Serveur de développement Expo
pnpm --filter @workspace/mobile run dev

# Vérification des types (seul contrôle statique, il n'y a pas de tests)
pnpm run typecheck
```

Pour vérifier que le bundle compile sans appareil :

```sh
cd artifacts/mobile
EXPO_OFFLINE=1 npx expo export --platform android --output-dir /tmp/musclenote-export
```

## Build de l'APK

Les builds passent par [EAS](https://docs.expo.dev/build/introduction/), depuis `artifacts/mobile` :

```sh
cd artifacts/mobile
eas build --platform android --profile preview
```

Le profil `preview` produit un APK installable directement sur le téléphone.

## Données

Tout est stocké localement dans AsyncStorage, sous des clés préfixées `@gymtracker_` (ancien nom conservé pour ne pas perdre les données existantes lors d'une mise à jour). Pour changer de téléphone, utiliser **Paramètres → Exporter mes données**, puis **Importer des données** sur le nouvel appareil.

## Documentation développeur

Voir [`CLAUDE.md`](CLAUDE.md) pour le détail de l'architecture (contextes, conventions i18n, notifications, pièges connus).
