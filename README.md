# MuscleNote

Carnet de musculation pour Android, **hors ligne** : séances, progression, nutrition et compléments, sans compte ni serveur. Toutes les données restent sur le téléphone (sauvegarde automatique dans un dossier au choix, envoi à Health Connect si on l'active). L'app ne se connecte que pour vérifier les mises à jour (quand elle n'est pas installée par Obtainium) et pour envoyer un avis, seulement quand on appuie sur « Envoyer ».

Application native (Kotlin, Jetpack Compose), en français et en anglais. Elle n'est pas publiée sur le Play Store : chaque version est une [Release GitHub](https://github.com/Bouss45/MuscleNote/releases) avec son APK signé.

## Installation avec Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installe les apps directement depuis leurs Releases GitHub et prévient des mises à jour.

1. Installer Obtainium (APK de ses [Releases](https://github.com/ImranR98/Obtainium/releases), ou via F-Droid / IzzyOnDroid).
2. Dans Obtainium : **Ajouter une app**, coller l'URL `https://github.com/Bouss45/MuscleNote`, puis **Ajouter**.
3. **Installer** : Obtainium télécharge l'APK de la dernière Release (`MuscleNote-v1.0.<n>.apk`). Android demande d'autoriser Obtainium à installer des apps la première fois.
4. Les mises à jour apparaissent ensuite dans Obtainium à chaque nouvelle Release.

Sans Obtainium : télécharger l'APK depuis la page [Releases](https://github.com/Bouss45/MuscleNote/releases) et l'ouvrir sur le téléphone. L'app vérifie alors elle-même, une fois par jour, s'il existe une nouvelle Release : une annonce « Mise à jour disponible » apparaît sur l'Accueil, avec un bouton qui télécharge le nouvel APK (à ouvrir pour l'installer). **Paramètres → Mises à jour** permet aussi de vérifier tout de suite. Quand Obtainium installe l'app, cette vérification se désactive : c'est lui qui suit les Releases.

Toutes les versions sont signées avec la même clé : une mise à jour s'installe par-dessus la précédente et garde les données. Les versions de test de la transition (pré-releases `v0.1.*`) sont la même app (`com.musclenote.android`) : les Releases `v1.0.*` s'installent par-dessus.

Permissions : notifications (minuteur de repos, rappels de compléments), alarmes exactes, redémarrage (reprogrammer les rappels et la sauvegarde automatique), vibration, Internet (vérification des mises à jour, qui ne lit que la dernière Release GitHub et n'envoie aucune donnée ; envoi d'un avis, uniquement sur appui de « Envoyer »), maintien de l'appareil éveillé pendant la sauvegarde automatique (WorkManager), écriture des séances et du poids dans Health Connect (accordée dans Health Connect, seulement si on active l'option). Aucune permission de stockage, de localisation ni de caméra, aucune lecture dans Health Connect.

### Venir de l'ancienne app (Expo)

L'ancienne MuscleNote (`com.musclenote.app`) est une autre app : ses données se transfèrent en un geste.

1. Dans l'ancienne app, terminer la séance en cours s'il y en a une.
2. **Paramètres → Exporter mes données**, puis choisir **MuscleNote** (la nouvelle app) dans la feuille de partage.
3. La nouvelle app s'ouvre sur Paramètres et demande de confirmer l'import.

Une fois les données vérifiées, l'ancienne app peut être désinstallée.

## Fonctionnalités

### Séances
- Préréglages (Push, Pull, Legs, Pecs, Dos…) ou nom libre ; la séance est **pré-remplie** avec les exercices et séries de la dernière séance du même type.
- 42 exercices intégrés en 9 groupes musculaires, plus des **exercices personnalisés** (archivés plutôt que supprimés s'ils servent encore à l'historique).
- Recherche insensible aux accents, filtre par groupe, **glisser-déposer** des exercices, note par exercice, échauffement guidé.
- Exercices **cardio** : durée, distance, vitesse, inclinaison.
- Validation des séries ; seules les séries validées comptent dans les statistiques.
- **Minuteur de repos** : notification avec compte à rebours sur l'écran verrouillé, alarme exacte en fin de repos.
- Après coup : modification complète d'une séance (renommage, séries, poids de corps), de sa date, ou suppression.

### Accueil
- **Série hebdomadaire** : une semaine est validée à partir de 3 séances ; une semaine manquée est tolérée. Meilleure série et calendrier des 8 dernières semaines.
- Séances, volume total, dernier poids, **temps passé à la salle**.

### Progrès
- Poids de corps (séances + pesées manuelles), calories et eau avec objectifs datés.
- Par groupe musculaire : charge et volume max, exercices les plus utilisés avec tendance.
- Historique par exercice avec records et **1RM estimé** (Epley).

### Nutrition
- Calories et eau du jour, objectifs.
- Compléments avec **rappels quotidiens** à l'heure choisie (alarmes exactes, reprogrammées après un redémarrage ou un changement d'heure).

### Paramètres
- Langue (système, français, anglais), thème (système, clair, sombre), durée du repos.
- **Sauvegarde / restauration JSON** (importe aussi les sauvegardes de l'ancienne app).
- **Sauvegarde automatique** : chaque jour, une sauvegarde JSON dans un dossier choisi (par exemple synchronisé par Syncthing ou Nextcloud) ; les 14 plus récentes sont gardées, « Sauvegarder maintenant » à tout moment.
- **Health Connect** (si disponible sur le téléphone) : les séances (musculation, début, fin, nom) et le poids (séances et pesées) sont envoyés à Health Connect, mises à jour et suppressions comprises ; « Envoyer l'historique » pour le passé.
- **Export CSV** (séances, séries, pesées, nutrition), lisible dans Excel ou Google Sheets.
- Mises à jour (sans Obtainium) : version installée, vérification, téléchargement de la nouvelle version.
- Rapport de plantage local, à partager ou supprimer.
- **Ton avis** : une idée, un bug ou une remarque envoyés au développeur (serveur Discord), avec la version de l'app et d'Android, le modèle du téléphone et la langue, affichés sous le formulaire ; aucune donnée d'entraînement.

## Stack technique

- Kotlin, Jetpack Compose, Material 3, Navigation Compose
- Coroutines + `StateFlow`, kotlinx.serialization (un fichier JSON par document, écritures atomiques)
- `AlarmManager` (alarmes exactes) pour le minuteur et les rappels
- AppCompat pour la langue par application, Inter via les polices téléchargeables Google
- `sh.calvin.reorderable` pour le glisser-déposer, graphiques dessinés au `Canvas`
- WorkManager pour la sauvegarde automatique, `androidx.health.connect` pour Health Connect
- minSdk 26 (Android 8.0), compileSdk / targetSdk 37

## Structure

```
.
├── SPEC.md                         # spécification de référence (comportement, données, décisions)
├── CLAUDE.md                       # notes d'architecture pour le développement
├── .github/workflows/
│   ├── release.yml                 # tests, APK signé, notes de version, Release GitHub
│   └── checks.yml                  # tests et build de chaque pull request
└── android-app/                    # projet Gradle
    ├── release-notes/              # notes de version des Releases (module JVM)
    └── app/src/
        ├── main/java/com/musclenote/android/
        │   ├── data/           # modèle, dépôts, règles métier pures, sauvegarde, export CSV
        │   ├── backup/         # sauvegarde automatique (dossier choisi, WorkManager)
        │   ├── health/         # Health Connect (écriture, politique de confidentialité)
        │   ├── notifications/  # canaux, minuteur de repos, rappels de compléments
        │   └── ui/             # écrans Compose, composants, thème
        ├── main/res/           # textes FR (values) et EN (values-en), icônes
        └── test/               # tests JVM des règles métier
```

## Développement

Prérequis : JDK 21 et le SDK Android (ou un simple push : la CI construit tout). Depuis `android-app/` :

```sh
./gradlew :app:testDebugUnitTest :release-notes:test   # tests JVM
./gradlew :app:assembleDebug                            # APK de debug
```

## Releases

`.github/workflows/release.yml` tourne à chaque push sur `main` qui touche `android-app/` : tests JVM, build de l'APK release signé, vérification de la signature, puis **Release GitHub** `v1.0.<n>` (`versionName` 1.0.<n>, `versionCode` 1000 + n). Son texte liste les titres des commits depuis la Release précédente, sans les fusions ni les commits qui ne touchent que la documentation ou la CI : un titre de commit décrit donc le changement pour l'utilisateur, en français. Chaque pull request vers `main` passe d'abord par `checks.yml` (tests JVM et APK release non signé) ; seule la fusion publie une Release. Les secrets du dépôt fournissent la clé de signature (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`) et l'URL du webhook Discord des avis (`DISCORD_WEBHOOK_URL` ; sans elle, la section « Ton avis » est masquée). Cette URL n'est pas dans le dépôt, mais elle est lisible dans l'APK publié : le webhook doit poster dans un salon privé, et se régénère en cas d'abus.

## Données

Tout est stocké dans le stockage privé de l'app, en fichiers JSON. Pour changer de téléphone : **Paramètres → Exporter mes données** (ou la dernière sauvegarde automatique), puis **Importer des données** sur le nouvel appareil.

## Ancienne app Expo

L'app Expo / React Native d'origine a été retirée du dépôt ; sa dernière version est conservée au tag [`expo-final`](https://github.com/Bouss45/MuscleNote/tree/expo-final).
