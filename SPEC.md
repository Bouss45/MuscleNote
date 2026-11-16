# SPEC.md — MuscleNote, spécification de référence pour la réécriture native

> **Rôle de ce document.** C'est la référence unique pour réécrire l'app Expo/React Native
> (`artifacts/mobile/`) en Android natif (Kotlin + Jetpack Compose). Il décrit le
> comportement **cible de l'app native** : le comportement de l'app Expo, auquel
> s'appliquent les décisions prises au § 11.
>
> **L'app Expo de référence est conservée au tag `expo-final`** (dernière version, commit
> `2292cf5` de `main`) ; elle a été retirée du dépôt lors de la bascule vers l'app native.
> Pour lire un de ses fichiers : `git show expo-final:artifacts/mobile/<chemin>` ; pour
> l'extraire en entier : `git worktree add ../musclenote-expo expo-final`.
>
> - **🔄 Dn** marque un écart volontaire par rapport à l'app Expo ; `n` renvoie à la décision
>   n° n du § 11. Le comportement Expo d'origine est rappelé quand il compte (import des
>   anciennes sauvegardes, migration).
> - Ce qui est propre à iOS ou au web est omis.
> - Les points encore à trancher sont au § 12 ; l'état de l'implémentation au § 13.
>
> Conventions : les chemins de fichiers de l'app Expo sont relatifs à `artifacts/mobile/`
> au tag `expo-final`. Les clés
> `xxx.yyy` entre backticks sont des clés de traduction (cf. § 9.4). « Jour local » = jour
> calendaire dans le fuseau du téléphone. Une **série validée** = série dont
> `completed == true` (cf. § 3.16).

---

## Sommaire

1. [Vue d'ensemble](#1-vue-densemble)
2. [Écrans et navigation](#2-écrans-et-navigation)
3. [Règles métier](#3-règles-métier)
4. [Modèle de données et stockage](#4-modèle-de-données-et-stockage)
5. [Sauvegarde JSON, import et transfert](#5-sauvegarde-json-import-et-transfert)
6. [Catalogue des exercices et préréglages](#6-catalogue-des-exercices-et-préréglages)
7. [Notifications](#7-notifications)
8. [Export CSV](#8-export-csv)
9. [Thème et textes](#9-thème-et-textes)
10. [Cas particuliers](#10-cas-particuliers)
11. [Décisions](#11-décisions)
12. [Questions restantes](#12-questions-restantes)
13. [État d'avancement](#13-état-davancement)
14. [Envoyer un avis](#14-envoyer-un-avis)
15. [Évolutions](#15-évolutions)

---

## 1. Vue d'ensemble

- **MuscleNote** : carnet de musculation personnel, en français et en anglais, qui suit **la
  langue du système** (🔄 D9). Installé par APK (Releases GitHub, suivies par Obtainium),
  **pas de publication Play Store**.
- **Hors ligne** : aucun compte, aucune donnée d'entraînement envoyée. **Deux accès réseau
  seulement** :
  - la vérification des mises à jour (🔄 D25, D39, § 3.17, § 15.14) : une requête `GET` à l'API
    publique des Releases GitHub (dernière Release, ni brouillon ni préversion), **aucune donnée
    de l'app envoyée** ; automatiquement **une fois par jour, vers 18 h, en arrière-plan**,
    seulement si la notification « Nouvelle version disponible » est activée (quelle que soit la
    source d'installation), et à chaque « Vérifier maintenant » de Paramètres ;
  - l'envoi d'un avis (🔄 D26, § 14), **uniquement quand l'utilisateur appuie sur
    « Envoyer »** : le type, le message, le pseudo facultatif et les quatre informations
    techniques affichées sous le formulaire. C'est le seul envoi de données de l'app.

  La police est embarquée dans l'app (🔄 D36, DESIGN.md § 3) : plus aucun accès, même indirect,
  aux polices téléchargeables de Google Play Services.

  Deux écritures hors de l'app, **sur le téléphone** et seulement si l'utilisateur les active :
  la sauvegarde automatique dans un dossier qu'il choisit (🔄 D28, § 15.2) et l'envoi des
  séances et du poids à Health Connect (🔄 D29, § 15.3). Aucune ne passe par le réseau.
- **Identité Android** (🔄 D5, D22) : nouvel `applicationId` `com.musclenote.android`, nom
  affiché **« MuscleNote »** (un suffixe « 2 » le distinguait de l'app Expo pendant la
  transition, retiré à la bascule), orientation **portrait** uniquement. L'app Expo
  (`com.musclenote.app`, « MuscleNote ») peut rester installée à côté le temps du transfert
  des données, qui se fait par partage (§ 5.5, D4).
- **Socle technique** :
  - Kotlin, Jetpack Compose, Material 3, Navigation Compose ;
  - coroutines + `StateFlow` ; kotlinx.serialization ;
  - stockage en fichiers JSON (§ 4.2, 🔄 D2), pas de Room ;
  - `AlarmManager` (alarmes exactes) pour le minuteur et les rappels (🔄 D6, D7) ;
  - AppCompat pour la langue par application (🔄 D9) ;
  - police Archivo embarquée (`res/font/archivo.ttf`, DESIGN.md § 3 ; 🔄 D36 remplace l'Inter
    téléchargée de D12) ;
  - `sh.calvin.reorderable` pour le glisser-déposer (🔄 D14) ;
  - graphiques dessinés au `Canvas` Compose (🔄 D13) ;
  - WorkManager (`androidx.work`) pour la sauvegarde automatique quotidienne (🔄 D28) ;
  - `androidx.health.connect:connect-client` pour Health Connect (🔄 D29).
- **SDK** (D8) : `minSdk 26` (Android 8.0), `compileSdk` / `targetSdk` = dernière API stable,
  soit **37** (Android 17) au démarrage du projet.
- **Permissions** (🔄 D19 — uniquement celles qui servent) :
  | permission | usage |
  |---|---|
  | `POST_NOTIFICATIONS` | minuteur de repos et rappels (demandée à l'exécution, Android 13+) |
  | `USE_EXACT_ALARM` | alarmes exactes, Android 13+ (accordée d'office ; acceptable hors Play Store) |
  | `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) | alarmes exactes, Android 12–12L |
  | `RECEIVE_BOOT_COMPLETED` | reprogrammer les rappels après redémarrage ; WorkManager s'en sert aussi pour la sauvegarde automatique (🔄 D28) |
  | `VIBRATE` | vibration de fin de repos depuis le récepteur d'alarme |
  | `POST_PROMOTED_NOTIFICATIONS` (🔄 D31) | proposer la notification « Séance en cours » en Live Update (Android 16+, § 15.5) ; permission normale, sans demande, que l'utilisateur peut couper dans les réglages de notification de l'app |
  | `INTERNET` (🔄 D25, D26) | vérification des mises à jour (§ 3.17, § 15.14) et envoi d'un avis à la demande de l'utilisateur (§ 14), rien d'autre |
  | `ACCESS_NETWORK_STATE` (🔄 D39) | ajoutée par WorkManager, gardée depuis D39 : la vérification quotidienne des mises à jour attend le réseau (contrainte `CONNECTED`, § 15.14.2) ; permission normale, sans demande, qui ne donne accès à aucun contenu |
  | `WAKE_LOCK` (🔄 D28) | ajoutée par WorkManager : exécution de la sauvegarde automatique (§ 15.2) ; permission normale, sans demande |
  | `health.WRITE_EXERCISE` (🔄 D29) | écrire les séances dans Health Connect (§ 15.3) ; accordée dans l'écran de Health Connect, quand l'utilisateur active l'option |
  | `health.WRITE_WEIGHT` (🔄 D29) | écrire le poids (un par jour : la pesée, sinon celui de la séance) dans Health Connect (§ 15.3) ; idem |
  Aucune permission de stockage (fichiers via le sélecteur système, le dossier de la sauvegarde
  automatique avec une autorisation persistante, et `FileProvider`), de localisation ni de
  caméra ; aucune permission de **lecture** Health Connect. `FOREGROUND_SERVICE`, déclarée par
  WorkManager, est retirée du manifeste fusionné (§ 15.2) ; `ACCESS_NETWORK_STATE` l'était aussi
  jusqu'à D39.
- Quatre domaines fonctionnels :
  1. **Séances** (démarrer, saisir et valider les séries, terminer, éditer l'historique) ;
  2. **Progrès** (graphiques poids de corps, calories, eau, par groupe musculaire) ;
  3. **Nutrition** (calories et eau du jour, compléments + rappels) ;
  4. **Paramètres** (langue, thème, minuteur, notifications — rappels, 🔄 D38, et nouvelle version,
     🔄 D39 —, sauvegarde/restauration JSON,
     sauvegarde automatique, export CSV, Health Connect, mises à jour, rapport de plantage, envoi d'un
     avis).

---

## 2. Écrans et navigation

### 2.1 Arborescence

> 🔄 D33 — **arborescence et barre d'onglets remplacées par le § 15.8.1** : onglets Accueil,
> Journal, Progrès, Nutrition ; Paramètres ouvert par l'engrenage de l'Accueil ; mini-barre de la
> séance en cours ; retour prédictif. Les écrans plein écran ci-dessous ne changent pas.

```
Racine (Navigation Compose, aucun en-tête système)
├── Onglets (NavigationBar Material 3, 4 destinations)
│   ├── home        « Accueil »      icône `home`
│   ├── progress    « Progrès »      icône `trending_up`
│   ├── nutrition   « Nutrition »    icône `favorite`
│   └── settings    « Paramètres »   icône `settings`
├── start-workout                    choix du nom + poids → démarre la séance (plein écran)
├── workout                          séance en cours
├── exercise-picker                  ajout d'exercices à la séance en cours (plein écran)
├── exercise-history/{exerciseId}    historique d'un exercice
└── edit-workout/{workoutId}         édition d'une séance terminée
```

- Barre d'onglets : fond `background`, séparateur haut 1 dp `border`, élément actif
  `primary` (icône remplie), inactif `mutedForeground` (icône contour), icônes 24 dp,
  libellés traduits (`tabs.*`). Chaque écran dessine son propre en-tête sous la barre de
  statut (edge-to-edge, gestion des insets).
- Icônes : **Material Symbols** (🔄 D11), correspondance au § 9.3.
- **Entrées externes** :
  - notification du minuteur de repos → écran `workout` ;
  - notification de rappel de complément → onglet `nutrition` ;
  - fichier JSON partagé ou ouvert avec MuscleNote → confirmation d'import (§ 5.4).
- Plus d'écran d'erreur fatale (🔄 D17) : un plantage écrit un rapport local (§ 10.9).

### 2.2 Accueil

> 🔄 D33 — **disposition remplacée par le § 15.8.2** (gabarit hub : une surface « En cours » ou
> « Prochaine séance », puis « Dernière séance » et « Cette semaine ») ; la liste de toutes les
> séances, avec la modification de la date et la suppression, passe au **Journal** (§ 15.8.4). Plus
> d'image de fond. Restent valables ci-dessous, sous leur nouvelle forme : les conditions de
> l'annonce de mise à jour (D25) et de la carte de transfert (D4/D5), le calcul de la série
> hebdomadaire (§ 3.6) et les règles de la modale « Modifier la date ».

Fond : `background` + image `home-bg.png` plein écran, `ContentScale.Crop`, flou (rayon
6 dp, `Modifier.blur`, Android 12+ ; sans flou en dessous), opacité **0,35 en thème sombre,
0,12 en thème clair** (à ajuster visuellement). Liste défilante verticale (`LazyColumn`,
marges latérales 20 dp) contenant dans l'ordre :

1. **Bouton principal** :
   - pas de séance en cours : gros bouton `primary` « Nouvelle séance » (icône play) →
     `start-workout` ;
   - séance en cours : carte bordée `primary` (fond `primary` à 13 %) avec point coloré, nom
     affiché de la séance (§ 3.1) et sous-titre `home.activeSubtitle`
     (« N exercices · 78 kg corps ») → `workout`.

   Juste dessous, 🔄 D25 — **annonce de mise à jour**, seulement si la vérification est active
   (app installée sans Obtainium) et qu'une Release plus récente que la version installée est
   connue, non masquée (§ 3.17) : icône `download`, « Mise à jour disponible », « MuscleNote
   {version} est disponible (version installée : {installée}). », bouton `primary`
   [Télécharger] (APK dans le navigateur, sinon page de la Release) et ✕ (masque l'annonce
   jusqu'à la version suivante).
2. **Carte de transfert** (🔄 D4/D5) — seulement si aucune séance terminée, aucune séance en
   cours, et carte non masquée : « Tu viens de l'ancienne MuscleNote ? » + mode d'emploi
   (§ 5.5) + bouton ✕ (masquage mémorisé dans les préférences locales, non sauvegardé).
3. **Carte « Série en cours »** (streak) — seulement s'il existe ≥ 1 séance terminée. Calcul
   au § 3.6. Contenu :
   - icône éclair, « Série en cours » + nombre + « semaine(s) » ; à droite « Meilleure » +
     « N sem. » ;
   - mini-calendrier des **8 dernières semaines** (`home.streak.last8weeks` — « cette
     semaine à droite ») : 8 cases égales (hauteur 34, rayon 8) affichant chacune le nombre
     de séances de la semaine ;
   - légende Validée (3+) / Partielle (1-2) / Aucune ;
   - phrase d'aide : `current == 0` → `home.streak.hintStart` ; sinon `isAlive` →
     `home.streak.hintAlive` ; sinon `home.streak.hintOver`.
   La carte passe en `primary` (fond `primary` à 8 %, bordure et compteur `primary`) quand
   `current > 0 && isAlive`, sinon `card`/`border`.
   Couleurs des cases :
   - validée : fond `primary`, texte `primaryForeground` ;
   - **partielle : fond `warning` à 20 %, texte `foreground`** (🔄 D15 — ambre, comme
     annoncé dans la doc ; l'app Expo utilisait `primary` à 20 %) ;
   - aucune : fond `muted`, texte `mutedForeground` ;
   - semaine courante : bordure 1,5 dp `primary` en plus.
4. **Ligne de 3 statistiques** (si ≥ 1 séance) :
   - « Séances » = nombre de séances terminées ;
   - « Volume total » = `round(Σ reps×poids des séries validées / 1000)` + `t` (🔄 D15) ;
   - « Dernier poids » = `bodyWeight` de la séance la plus récente + ` kg`.
5. **Carte « temps en salle »** (si `n > 0`, § 3.7) : icône horloge, total formaté et
   `home.gymTime` (« Temps total en salle · moy. 1h 05m/séance »).
6. Titre « Séances récentes » puis **toutes les séances terminées**, de la plus récente à la
   plus ancienne. Chaque carte :
   - titre = nom affiché (§ 3.1 ; date formatée si vide) ;
   - sous-titre = date courte (jour de semaine abrégé, jour, mois abrégé — « mar. 11 nov. »)
     · heure `HH:mm` · `N min` si `durationMinutes` est non nul ;
   - badge `78 kg` (poids de corps de la séance) ;
   - 3 boutons icônes 28×28 : **éditer** (→ `edit-workout`), **date** (modale ci-dessous),
     **supprimer** (rouge) ;
   - ligne de stats (🔄 D15, séries validées uniquement) : `N exercices` (exercices ayant
     ≥ 1 série validée) · `N séries` (validées) · `home.volumeShort` (« 5230 kg vol. »,
     arrondi à l'unité) ;
   - **suppression en deux temps** : la poubelle remplace la ligne de stats par
     « Supprimer cette séance ? » + [Annuler] [Supprimer (rouge)].
7. **État vide** (aucune séance, pas de séance en cours) : icône `fitness_center`,
   « Prêt à s'entrainer ? » + `home.empty.text`.

**Modale « Modifier la date »** (voile `rgba(0,0,0,0.6)`, boîte max 360 dp, rayon 20) :
sous-titre = nom affiché ; champs numériques Jour / Mois / Année (2/2/4 caractères max) puis
Heure : Min (2/2), préremplis avec la date de la séance (heure et minute sur 2 chiffres).
Heure ou minute vide → 12 h / 00. Validation `buildLocalDate` (§ 10.3) ; si invalide :
« Date ou heure invalide » en rouge + vibration d'erreur, la modale reste ouverte. Sinon la
date est enregistrée et la liste retriée. Toucher le voile ou [Annuler] ferme sans
enregistrer.

### 2.3 Démarrer une séance

> 🔄 D36 — **remplacé par le formulaire en bottom sheet du § 15.11.1** (étapes « Type de séance »
> puis « Poids de corps » au pavé numérique, posé sur l'écran qui l'ouvre). Les règles ci-dessous
> (préréglages, nom de 30 caractères, poids prérempli, raccourcis, permission puis `startWorkout`)
> restent valables. 🔄 D38 — **le poids devient facultatif** (§ 15.13.2) : champ vide avec la dernière
> pesée en aide, « Passer », pas de champ si le poids du jour est déjà connu ; plus de préremplissage.

Deux étapes dans le même écran ; bouton en haut à gauche : ✕ (fermer) à l'étape 1,
← (retour à l'étape 1) à l'étape 2. Le retour système suit la même logique.

**Étape 1 — nom** (« Type de séance », « Choisis ou nomme ta séance ») :
- Trois groupes de puces de **préréglages** (§ 6.3), libellés traduits :
  - PPL : Push, Pull, Legs
  - Split : Pecs, Dos, Épaules, Jambes, Bras
  - Autre : Full Body, Isolation, Cardio
- Groupe « Personnalisé » : boîte qui devient un champ texte une fois touchée
  (placeholder « Ex: Épaules / Mollets », **30 caractères max**, focus auto).
- Sélectionner une puce désélectionne le nom personnalisé (et vide le champ).
- « Suivant → » actif si un préréglage est choisi ou si le texte perso (`trim()`) est non vide.
- 🔄 D3 : un préréglage est retenu par son **identifiant** (`push`, `legsSplit`…), pas par
  son libellé traduit.

**Étape 2 — poids de corps** :
- Badge rappelant le nom choisi, titre « Poids de corps », sous-titre « Ton poids aujourd'hui ? ».
- Grand champ décimal (6 caractères max, placeholder `75`, unité `kg`, focus auto),
  **prérempli avec le `bodyWeight` de la séance terminée la plus récente** (vide sinon).
- Raccourcis : 55, 60, 65, 70, 75, 80, 85, 90, 95, 100 (la puce égale au texte saisi est
  surlignée).
- « ▶ Commencer » actif si `parseDecimal(saisie) > 0`. Action :
  1. 🔄 D6 : si `POST_NOTIFICATIONS` n'est pas accordée et peut encore être demandée,
     la demander (une fois ; un refus n'empêche pas de démarrer) ;
  2. `startWorkout(poids, préréglage | nom)` (§ 3.1) ;
  3. **remplace** cet écran par `workout` dans la pile.

### 2.4 Séance en cours

> 🔄 D32 — **disposition remplacée par le § 15.7** (gabarit « en direct » de DESIGN.md, colonne
> « Préc. », suggestion de charge, pavé numérique intégré, repos dans la liste, confirmations en
> bottom sheet). Restent valables ci-dessous : l'échauffement (non enregistré), le glisser-déposer,
> la fin ou l'abandon de la séance (D15) ; les règles du § 3 ne changent pas.

Si aucune séance n'est en cours : « Aucune séance active » + lien « Retour » (→ accueil).

**En-tête** : ← (retour à l'accueil, la séance reste en cours), libellé « Séance en cours »,
nom affiché de la séance, `78 kg`, et à droite un **chronomètre** `m:ss` (minutes non
bornées, ex. `75:03`) calculé depuis `date`, rafraîchi chaque seconde. Si la séance contient
≥ 1 série : barre de progression (séries validées / total) et « 3/12 séries ».

**Corps** (`LazyColumn`, padding 16) :
1. **Carte Échauffement** (état local à l'écran, **non persisté**) : titre « Échauffement »,
   « N/5 exercices », barre de progression 3 dp, repliable, masquable (✕, jusqu'au prochain
   affichage). 5 éléments cochables (case 20 dp ; libellé barré à 50 % quand coché) :
   | icône | libellé | détail |
   |---|---|---|
   | `air` | Cardio léger | 5-10 min vélo ou rameur |
   | `rotate_right` | Rotation des épaules | 15 reps chaque côté |
   | `open_with` | Mobilité des hanches | 10 reps cercles |
   | `arrow_downward` | Étirements dynamiques | 1 min jambes & dos |
   | `accessibility_new` | Squats à vide | 15 reps lents |
   Tout coché : carte en `primary` + badge « ✓ Fait ! ».
2. **Une carte par exercice** (ordre de la séance ; exercices à définition introuvable
   masqués). Voir § 2.5.
   **Glisser-déposer** (🔄 D14) : `LazyColumn` + bibliothèque `sh.calvin.reorderable`.
   Appui long sur une carte pour la soulever (légère mise à l'échelle et élévation), les
   autres cartes s'écartent en direct ; vibration légère au déplacement ; au relâchement
   `reorderExercises(ids)`. Fonctionne quel que soit le nombre d'exercices (liste paresseuse).
3. État vide : « Ajoute des exercices pour commencer ».
4. Bouton « ⊕ Ajouter un exercice » → `exercise-picker`.

**Pied de page fixe** :
- **Bandeau du minuteur de repos** (§ 7.2), visible seulement pendant un repos.
- Bouton contour rouge « Terminer la séance ». Toucher → confirmation en ligne :
  - s'il y a ≥ 1 série validée : « Terminer la séance ? » + [Annuler] [✓ Terminer (rouge)] →
    arrête le minuteur, `endWorkout()` (§ 3.3), retour à l'accueil ;
  - 🔄 D15 — s'il n'y a **aucune** série validée : « Aucune série validée : la séance ne
    sera pas enregistrée. » + [Annuler] [Abandonner (rouge)] → arrête le minuteur, supprime
    la séance en cours **sans l'ajouter à l'historique**, retour à l'accueil.

### 2.5 Carte d'exercice (séance en cours)

> 🔄 D32 — en séance, la carte devient un **bloc exercice** (§ 15.7.1) : note et retrait passent
> par le menu ⋮, « + Ajouter une série » par la ligne « + Série ». Les règles d'une nouvelle série
> ci-dessous restent valables.

- **En-tête** (toucher = replier/déplier) : nom affiché (§ 6), badge du groupe, badge
  `validées/total` (vert `success` quand tout est validé, sinon `muted`). Trois icônes :
  - **note** (`edit_note`, `primary` si une note existe) ;
  - **historique** (`trending_up`) → `exercise-history/{id}` ;
  - **✕ retirer de la séance** — 🔄 D15 : confirmation en ligne « Retirer « Nom » de la
    séance ? » + [Annuler] [Retirer (rouge)].
- **Note** : champ multiligne « Ajouter une note… », enregistré à chaque frappe ; affiché
  d'office si une note existe ; se masque s'il perd le focus vide ; masqué avec une note →
  aperçu d'une ligne (toucher → réaffiche le champ).
- **Séries** : une ligne par série (§ 2.6), avec case de validation et minuteur.
- **« + Ajouter une série »** (nouvelle série toujours non validée) :
  - force : mêmes `reps`/`weight` que la dernière série, ou **10 reps × 20 kg** ;
  - cardio (groupe `cardio`) : `reps = 0`, `weight = 0`, `duration`/`distance`/`speed`/
    `incline` repris de la dernière série, ou **30 min / 0 / 0 / 0**.

### 2.6 Ligne de série

Utilisée en séance et dans `edit-workout`, **avec une case de validation dans les deux cas**
(🔄 D15). Le minuteur de repos n'existe qu'en séance.

> 🔄 D32 — **en séance**, la ligne de série et le clavier du système sont remplacés par la ligne
> du § 15.7.1 et le pavé numérique du § 15.7.4 (mêmes longueurs maximales, enregistrement à
> chaque touche). L'éditeur de séance terminée (§ 2.9) garde la ligne ci-dessous.

- Numéro de série (1, 2, 3…).
- **Force** : `[reps] reps × [poids] kg`. Reps : clavier numérique, 3 caractères max, entier
  (`parseInteger`). Poids : clavier décimal, 6 caractères max (`parseDecimal`, virgule ou
  point).
- **Cardio** : grille 2×2 — `[durée] min` (4 car.), `[distance] km` (5 car.),
  `[vitesse] km/h` (4 car.), `[inclinaison] % inc.` (4 car.), tous décimaux.
- 🔄 D20 — règles de saisie (identiques pour tous les champs) :
  - une valeur 0 est affichée comme champ **vide** avec placeholder `0` (l'app Expo affichait
    `0` pour le cardio) ;
  - **enregistrement à chaque frappe** : la valeur parsée est appliquée immédiatement à
    l'état en mémoire (jamais au seul blur — sinon elle est perdue si l'on touche
    Terminer/Enregistrer sans quitter le champ). Le texte brut est conservé pendant la
    saisie (`62,` reste visible). L'écriture disque est différée (§ 4.2) ;
  - tout le texte est sélectionné au focus ; action IME « Suivant » passe au champ suivant
    de la ligne, « OK » sur le dernier.
- Case de validation : bascule `completed`. **En séance, passer de non validée à validée
  démarre le minuteur de repos** (§ 7.2) avec le nom affiché de l'exercice. Ligne validée :
  fond `primary` à 9 %, bordure `primary` à 27 %.
- Poubelle : supprime la série immédiatement (sans confirmation).

### 2.7 Sélecteur d'exercices

> 🔄 D36 — **remplacé par la bottom sheet du § 15.11.2** (lignes à cocher, appui long sur un
> exercice perso pour le modifier ou le supprimer). Le filtre, l'ordre, l'archivage et les ajouts
> multiples ci-dessous restent valables.

Agit sur la **séance en cours**. Reste ouvert après chaque ajout (ajouts multiples).

- En-tête : ✕ (fermer), titre « Exercices », **+** (affiche/masque le formulaire de création).
- **Recherche** (« Chercher un exercice... », ✕ pour vider) + **puces de groupes** avec
  « Tous » en tête (toucher la puce active la désélectionne). Filtre : groupe ET texte ; le
  texte est comparé au **nom affiché** normalisé (§ 10.6).
- Les exercices **archivés** ne sont pas proposés.
- **Formulaire « Nouvel exercice »** : nom (focus auto) + puces de groupe (sans « Tous »,
  défaut **`dos`**) + [Ajouter]. Nom vide → rien. Pas de contrôle de doublon.
- **Liste** (ordre : catalogue § 6 puis exercices perso par ordre de création). Chaque ligne :
  nom + badge du groupe.
  - Déjà dans la séance : fond `primary` à 8 %, nom en `primary`, coche ✓, désactivée.
  - Sinon : toucher → `addExerciseToWorkout(id)` (§ 3.2), icône **+** à droite.
  - Exercice **perso** non ajouté : boutons **éditer** et **supprimer** en plus.
    - Éditer : formulaire en ligne au-dessus (« Modifier l'exercice », nom prérempli, puces
      de groupe, [Annuler] [✓ Enregistrer]).
    - Supprimer : confirmation en ligne « Supprimer « Nom » ? » + `exercisePicker.deleteHint`
      → `deleteCustomExercise` (§ 3.9).
  - Les exercices intégrés ne sont ni modifiables ni supprimables.

### 2.8 Historique d'un exercice

> 🔄 D34 — **remplacé par la fiche exercice du § 15.9.1** (gabarit fiche : la dernière séance de la
> période en chiffre héros, courbe touchable, périodes 1M – Tout, record personnel et 1RM estimé en
> rangée de stats, liste des séances avec meilleure série, 1RM, volume et record de la période).
> Restent valables ci-dessous : la sélection des séances et des séries, le record et le 1RM ; la
> métrique gagne les reps au poids du corps (§ 15.9.3).

Paramètre `exerciseId`. Introuvable → « Exercice introuvable » + Retour.

- En-tête : ←, nom affiché, badge du groupe.
- `history` = séances **terminées** où l'exercice a **≥ 1 série validée** ; seules les séries
  validées sont prises en compte et affichées (🔄 D15).
- Cartes de record (si `history` non vide) :
  - **Force** : « Record personnel » = max des poids des séries (`X kg`) ; « 1RM estimé »
    (couleur `info`) = meilleur 1RM Epley (§ 3.5), affiché s'il est > 0.
  - **Cardio** : « Séance la plus longue » = max de la durée totale d'une séance (`X min`) ;
    pas de 1RM.
- Graphique (§ 2.13) si ≥ 2 séances, **chronologique** (plus ancienne à gauche) :
  « Charge maximale (kg) » ou « Durée par séance (min) » ; libellés X `dd/MM` ; valeurs
  arrondies au dixième. Une seule séance → « Fais encore des séances pour voir ta
  progression ! ».
- **Liste « Historique »** : 🔄 D15 — **plus récente en haut**. Chaque carte : date (jour de
  semaine abrégé, jour, mois abrégé), « 78 kg corps », puces de séries (`8 × 80 kg`, ou
  `20 min · 3.2 km · 9.5 km/h · 2 %` en cardio — éléments nuls autres que la durée omis).
  Force : « Max X kg », « Volume X kg » (Σ reps×poids arrondi), « 1RM ~X kg » (si > 0).
- Vide : « Aucune séance avec cet exercice ». La séance en cours n'est pas incluse.

### 2.9 Éditer une séance terminée

> 🔄 D36 — **disposition remplacée par la fiche du § 15.11.3** (type et poids de corps en lignes
> qui ouvrent un formulaire, blocs exercice et séries au pavé numérique, « Enregistrer » en bas).
> La copie de travail, la sortie confirmée, les séries ajoutées et l'enregistrement ci-dessous
> restent valables.

Paramètre `workoutId`. Introuvable → « Séance introuvable » + Retour.

Travaille sur une **copie locale** ; rien n'est enregistré avant [✓ Enregistrer].

- **Sortie** (← ou retour système) : 🔄 D15 — s'il y a des modifications non enregistrées,
  dialogue « Abandonner les modifications ? » [Continuer l'édition] [Abandonner] ; sinon
  sortie directe.
- **En-tête** : ←, « Modifier la séance », nom affiché de la séance avec icône crayon,
  bouton Enregistrer. 🔄 D15 — toucher le nom ouvre « Renommer la séance » : mêmes puces de
  préréglages + champ perso qu'au § 2.3 étape 1 ; choisir un préréglage fixe
  `preset` (et `name` = son libellé courant) ; un nom perso fixe `name` et efface `preset`.
- **Poids de corps** : champ décimal 6 car., prérempli si > 0 (placeholder = ancien poids ou
  `—`). À l'enregistrement : valeur parsée ≤ 0 → l'ancien poids est conservé.
- Une carte par exercice : nom, badge, ✕ (retirer, immédiat dans la copie), lignes de série
  **avec case de validation** (pas de minuteur), « + Ajouter une série » :
  - force : copie de la dernière série ou 10 × 20 kg, **validée** ;
  - cardio : copie de la dernière série ou 30 min / 0 / 0 / 0, **validée**.
- « ⊕ Ajouter un exercice » → écran plein (recherche + puces comme § 2.7) listant les
  exercices non archivés **et absents de la séance** ; toucher l'ajoute et ferme l'écran.
  Série initiale validée : force **10 × 20 kg** ; 🔄 D15 cardio **30 min / 0 / 0 / 0** (l'app
  Expo créait une série de force même pour le cardio). Liste vide → « Aucun exercice trouvé ».
- Aucun exercice → « Aucun exercice — appuie sur le bouton ci-dessus pour en ajouter ».
- Non modifiables ici : ordre des exercices, date (depuis l'accueil), durée, notes.
- Enregistrer = `patchWorkout(id, { exercises, bodyWeight, name, preset })` puis retour.

### 2.10 Progrès

> 🔄 D34 — **disposition remplacée par le § 15.9.2** (gabarit hub) : surface « Poids de corps »,
> sections « Exercices » (puces des groupes, stats, une ligne par exercice avec mini-courbe → fiche
> exercice), « Calories et eau », « Pesées ». Le titre devient « Progrès ». Restent valables
> ci-dessous : les valeurs affichées et leurs calculs (§ 3.10), et les règles de la pesée.

Titre « Progression ». Rangée horizontale défilante d'onglets (puces), dans cet ordre :

1. **Poids corps** (icône `person`, couleur `info`) ;
2. **🔥 Calories** (couleur `calories`) + badge = nombre de jours avec calories > 0 ;
3. **💧 Eau** (couleur `water`) + badge = nombre de jours avec eau > 0 ;
4. un onglet par groupe musculaire (ordre § 6.2, couleur du groupe) + badge = nombre de
   séances touchant ce groupe (§ 3.10) ; opacité 50 % si 0.

Onglet actif : fond couleur à 13 %, bordure et texte couleur ; badge plein couleur, texte
blanc. Onglet par défaut : Poids corps. Calculs au § 3.10.

**Onglet Poids corps**
- 3 stats (si ≥ 1 point) : « Actuel » (dernier point, `X kg`), « Évolution » (dernier −
  premier, 1 décimale, `+` si positif ; `destructive` si > 0, `success` si < 0),
  « Min–Max » (`min–max`).
- Graphique « Poids de corps (kg) » + « N jours », hauteur 200, couleur `info`.
- Carte **« Journal de poids »** : « + Ajouter une pesée » ; pesées manuelles (plus récente en
  haut) : date (jour, mois abrégé, année), `X kg`, poubelle (immédiate). Vide :
  `bodyWeight.empty`.
- Aucune séance : « Aucune séance encore » + « Lance ta première séance… ».
- **Modale « Nouvelle pesée »** : Poids (kg) (décimal, focus auto, placeholder « Ex : 75.5 ») ;
  Date JJ / MM / AAAA préremplie avec aujourd'hui. Validation : `0 < poids ≤ 500` et date
  valide (§ 10.3), sinon « Vérifie le poids et la date ». Enregistre à **midi local** (§ 3.11).

**Onglet Calories**
- 3 stats : « Aujourd'hui », « Moy. 7 jours » (§ 3.10, `—` si aucun jour saisi dans la
  fenêtre), « Jours objectif atteint » (objectif **du jour concerné**, 🔄 D15). Nombres au
  format de la locale. Pas de données → « Aucune donnée » + indication.
- Graphique si ≥ 2 jours : « Calories (kcal) », sous-titre « N jours · Objectif X kcal »
  (objectif actuel), ligne d'objectif pointillée « Objectif » au niveau de l'objectif
  **actuel**, valeurs ≥ 1000 affichées `2.5k`.
- « Récapitulatif » : Objectif quotidien (actuel), Meilleure journée, Jours suivis.

**Onglet Eau** — identique, en litres : « Aujourd'hui (L) » `x.x`, moyenne 7 jours `x.x`,
jours objectif atteint ; graphique « Hydratation (L) » (valeurs en L au dixième, ligne
d'objectif actuel en L), sous-titre « N jours · Objectif X L/jour » ; récapitulatif en L.

**Onglet groupe musculaire** (séries validées uniquement, 🔄 D15)
- 3 stats : « Séances », « Charge max » (`X kg`) ou en cardio « Durée max » (`X min`),
  « Vol. max » (`x.x t`) ou en cardio « Distance max » (`x.x km`). Aucun exercice utilisé →
  « Aucune séance {groupe} » + « Fais des exercices de {groupe}… » (groupe en minuscules).
- Carte **« Exercices les plus utilisés »** (sous-titre « Charge maximale par séance » ou
  « Durée totale par séance ») : top 3, chacun avec rang `#1` coloré (palette
  `#16A34A`, `#3b82f6`, `#a855f7`), nom, « N séances », badge record (icône
  `emoji_events`), tendance (`trending_up` vert / `trending_down` rouge, `+x.x kg`, masquée
  si 0 ou < 2 points), chevron → historique ; mini-graphique hauteur 130 si ≥ 2 séances,
  sinon « Fais au moins 2 séances pour voir le graphique ».
- Carte **« Tous les exercices »** : chaque exercice du groupe utilisé au moins une fois :
  nom, « N séances », record, chevron → historique.

### 2.11 Nutrition

> 🔄 D35 — **disposition remplacée par le § 15.10.1** (gabarit hub) : surface « Aujourd'hui »
> (calories en grand, état par rapport à l'objectif, eau et compléments pris), saisies au pavé
> numérique en formulaires (§ 15.10.2), compléments en lignes avec la case « pris aujourd'hui » et
> une sheet par complément pour ses rappels. Les règles ci-dessous restent, sauf le ⟲, remplacé par
> « Total du jour ».

En-tête : titre « Nutrition » + bouton `primary` « + Complément » (devient « ✕ Annuler »
quand le formulaire est ouvert).

**Section « SUIVI DU JOUR »**

*Carte Calories* (🔥) :
- Grand nombre = kcal du jour (format locale) + « / 2 000 kcal » ; barre de progression
  (hauteur 6, **remplissage** plafonné à 100 %).
- Couleur (🔄 D15, calculée sur le ratio **non plafonné** `r = kcal / objectif`) :
  `r > 1,05` → `destructive` ; `r ≥ 0,9` → `success` ; sinon `primary`. (Dans l'app Expo le
  rouge était inatteignable.)
- Engrenage → éditeur d'objectif (champ entier, prérempli, ✓ ; valeur ≤ 0 ignorée). Un
  changement d'objectif s'applique **à partir d'aujourd'hui** (§ 3.12).
- Sinon : bascule **« + Ajouter »** / **« = Définir »**, champ entier « kcal… » (✓ clavier ou
  bouton), bouton ⟲ (remet le jour à 0).
  - Ajouter : `kcal_du_jour += v` ; Définir : `kcal_du_jour = v`. Champ vide ou v < 0 → rien.
    Résultat borné à ≥ 0.

*Carte Eau* (💧) :
- Grand nombre : < 1000 ml → `750 ml`, sinon `1.5 L` (1 décimale) ; « / 2.0 L ». `success`
  si objectif du jour atteint, sinon `primary`.
- Engrenage → objectif saisi **en litres** (décimal, prérempli `x.x`), stocké en ml
  (`round(v × 1000)`), valeur ≤ 0 ignorée, appliqué à partir d'aujourd'hui.
- Champ entier « ml… » + bouton **+** (v ≤ 0 → rien) ; ⟲ (remet à 0, désactivé si déjà 0).

**Section « COMPLÉMENTS (N) »**

*Formulaire « Nouveau complément »* :
- Nom (focus auto, placeholder « Ex : Créatine, Whey, Oméga-3... »).
- Dosage par prise (texte libre au clavier décimal, **stocké tel que saisi**, ex. `1,5`).
- Unité : puces `g` (défaut), `mg`, `ml`, `caps`, `cp`, `dose`, `ui` (non traduites).
- Interrupteur « Rappels » (activé par défaut). Activé → liste de rappels ; initialement
  **08:00**. « + Ajouter un rappel » ajoute `heure du dernier + 2 h` (modulo 24), même minute.
  Bouton ✕ par rappel seulement s'il y en a > 1. Réactiver l'interrupteur avec une liste vide
  recrée 08:00.
- **Choix de l'heure** (🔄 D7) : chaque rappel s'affiche `HH:mm` (icône horloge) ; le toucher
  ouvre le **`TimePicker` Material 3** en dialogue (« Heure du rappel »), format 24 h ou 12 h
  selon le réglage système ; **toute minute 0–59** est possible (l'app Expo avançait par pas
  de 15 min).
- [✓ Enregistrer] actif si le nom (trim) est non vide. Rappels désactivés → complément créé
  **sans** rappels. Rappels activés → demande de `POST_NOTIFICATIONS` si nécessaire.

*Carte de complément* :
- Rond à cocher « pris aujourd'hui » (§ 3.12) ; nom ; « {dosage} {unité} par prise » (ligne
  masquée si `dosage` est vide — 🔄 D15, l'app Expo affichait « g par prise ») ; poubelle →
  confirmation en ligne « Supprimer « Nom » ? ».
- Ligne rappels : cloche (`notifications` / `notifications_off`) + résumé :
  - désactivés → « Pas de rappel » ;
  - activés et 0 rappel → « Rappels activés » ; 1 → « Rappel à 08:00 » ; n → « n rappels ».
  Toucher le résumé déplie la liste des rappels (modifiables, appliqués immédiatement) +
  « Ajouter un rappel ». Interrupteur → active/désactive (activer demande la permission si
  nécessaire).
- Badge bas : « ✓ Pris aujourd'hui » ou « Non pris aujourd'hui ».
- Carte cochée : fond `primary` à 7 %, bordure `primary` à 33 %.

Vide (formulaire fermé) : 💊 « Aucun complément » + texte d'aide.
Si ≥ 1 complément a ses rappels activés : encart info `nutrition.infoMobile`.

### 2.12 Paramètres

> 🔄 D35 — **disposition remplacée par le § 15.10.3** : liste groupée (Préférences, Données, À
> propos), chaque réglage ouvert en formulaire ; mêmes réglages et mêmes règles. Thème : Sombre
> (par défaut), Clair, Système (§ 15.10.4).

En-tête « Paramètres ». Contenu (padding 20, espacement 12) :

1. **Carte « Mes données »** : nombre de séances terminées | nombre d'exercices perso
   **non archivés**.
2. Section **« PRÉFÉRENCES »** :
   - **Langue** (🔄 D9 — icône `language`) : puces « Langue du système » (défaut),
     `🇫🇷 Français`, `🇬🇧 English`. Appliqué via
     `AppCompatDelegate.setApplicationLocales` (mémorisé par AppCompat, visible aussi dans
     les réglages système de langue par app sur Android 13+). Effet immédiat.
   - **Thème** (🔄 D10 — icône `contrast`) : puces « Système » (défaut), « Clair »,
     « Sombre ». Effet immédiat.
   - **Minuteur de repos** : puces 60, 90, 120, 150, 180, 240, 300 s, libellées `1 min`,
     `1 min 30`, `2 min`, `2 min 30`, `3 min`, `4 min`, `5 min` (< 60 s → `N s`). Défaut 180.
   - **Notifications** (🔄 D6, ligne affichée seulement si `POST_NOTIFICATIONS` est refusée
     ou les notifications de l'app désactivées) : « Notifications désactivées — minuteur et
     rappels silencieux » + bouton « Ouvrir les réglages » (réglages de notification de
     l'app).
3. Section **« SAUVEGARDE »** — cartes-actions (icône ronde, titre, description, chevron) :
   - **Exporter mes données** (`upload`, `primary`) → JSON v2 (§ 5.6) puis feuille de
     partage. Succès : « Fichier partagé avec succès ! ».
   - **Exporter en CSV** (🔄 D1 — `table_view`, vert `#217346`) → § 8. Succès : « Fichiers
     CSV exportés ! ».
   - **Importer des données** (`download`, bleu `#3B82F6`) → sélecteur de fichiers système
     (`OpenDocument`, types `application/json`, `text/plain`, `*/*`) puis confirmation
     (§ 5.4).
   Pendant une opération, l'icône devient un indicateur de chargement et les actions sont
   désactivées.
   - **Sauvegarde automatique** (🔄 D28) : carte avec interrupteur, dossier, dernière sauvegarde
     et [Sauvegarder maintenant], décrite au § 15.2.
4. **Encart de statut** sous les actions : succès (fond `primary` à 8 %, texte `primary`,
   icône `check_circle`) ou erreur (fond `destructive` à 8 %, texte `destructive`, icône
   `error`), avec le message (`settings.export.*` / `settings.import.*`, erreur technique
   dans `{{error}}`).
5. **Encart info** permanent : `settings.info`.
6. Section **« SANTÉ »** (🔄 D29, seulement si Health Connect est disponible) : carte Health
   Connect (interrupteur, autorisations, dernier envoi, [Envoyer l'historique]), § 15.3.
7. Section **« APPLICATION »** :
   - **Mises à jour** (🔄 D25, icône `download`, § 3.17) : « Version {installée} · … » —
     installée par Obtainium : « mises à jour gérées par Obtainium », rien d'autre ; sinon
     « pas encore de vérification », « à jour (vérification du {date heure}) » ou « la {version}
     est disponible » (en `primary`), « Vérification impossible : pas de connexion ? » en rouge
     après un échec ; boutons [Vérifier] (indicateur pendant la requête) et, si une mise à
     jour est disponible, [Télécharger] ;
   - **Rapport de plantage** (🔄 D17, affiché seulement si un rapport existe, § 10.9) :
     « Dernier plantage le {date} » + [Partager] (feuille de partage, `text/plain`)
     [Supprimer] (efface tous les rapports).
8. Section **« TON AVIS »** (🔄 D26, seulement si l'URL du webhook a été fournie au build) :
   formulaire d'envoi d'un avis, décrit au § 14.1.

### 2.13 Graphique linéaire

> 🔄 D34 — **remplacé par la courbe `TrendCurve` du design system** (DESIGN.md § 9.1) : trait fin,
> ni grille ni remplissage, point final, dates du premier et du dernier point, toucher (§ 15.9) ;
> variantes compacte et mini, ligne d'objectif. `ui/components/LineChart.kt` est retiré.

Composant maison dessiné au **`Canvas` Compose** (🔄 D13), utilisé partout.
- Largeur = largeur du parent ; hauteur paramétrable (130 / 180 / 200 dp).
- Marges : gauche 44, droite 16, haut 16, bas 36 (dp).
- < 2 points : texte centré « Aucune donnée » (0) ou « Une seule séance — continue pour voir
  ta progression ! » (1).
- Échelle Y de `min` à `max` des valeurs (ligne d'objectif incluse) ; plage nulle → 1.
- 3 graduations Y (min, milieu, max) : lignes pointillées `border` (4/4) + libellés 10 sp à
  gauche.
- Courbe lissée : Bézier cubique entre points successifs, points de contrôle à
  `x = milieu`, `y = y0` puis `y1` ; trait 2,5 ; remplissage en dégradé vertical de la
  couleur, 25 % → 2 %.
- Points : cercle r 3,5 fond `background`, contour 2 ; **point max** : r 5 plein, étiquette
  valeur + unité au-dessus (11 sp gras).
- Ligne d'objectif : pointillés 6/4, épaisseur 1,5, opacité 0,5, libellé « Objectif » 9 sp
  aligné à droite.
- Libellés X : premier, dernier, et tous les `max(1, floor(n/5))` points ; 9,5 sp.

---

## 3. Règles métier

### 3.1 Démarrer une séance, nom affiché et pré-remplissage

**Clé de séance** (🔄 D3) — sert au pré-remplissage et au temps en salle :
- séance avec `preset` → `"preset:" + preset` ;
- sinon → `"name:" + normalizeSearch(name)` (minuscules, sans accents, trim — § 10.6).

**Nom affiché** : `preset` connu → libellé traduit `startWorkout.preset.<preset>` dans la
langue courante ; sinon `name` ; si vide → date formatée.

`startWorkout(bodyWeight, preset | name)` :
1. Cherche la **séance terminée la plus récente ayant la même clé**.
2. Si trouvée : la nouvelle séance reprend **ses exercices dans le même ordre**, avec pour
   chacun sa **note** et **toutes ses séries** (validées ou non — c'est le plan de la
   séance) **clonées** : nouvel `id`, `completed = false`, `reps`/`weight` conservés,
   `duration`/`distance`/`speed`/`incline` conservés s'ils sont définis.
3. Sinon : séance vide.
4. Nouvelle séance : `id` généré, `preset` (ou absent), `name` = libellé du préréglage dans la
   langue courante (conservé pour la compatibilité, § 5) ou nom perso `trim()`,
   `date = maintenant`, `bodyWeight`, pas de `durationMinutes`.
5. La séance en cours est persistée à chaque modification (§ 4.2).

### 3.2 Ajouter un exercice à la séance en cours

`addExerciseToWorkout(id)` : sans effet si l'exercice est déjà dans la séance. Sinon ajoute en
fin de liste l'exercice avec la note et les séries (clonées comme au § 3.1) de la **séance
terminée la plus récente contenant cet exercice** ; aucune → 0 série.

### 3.3 Terminer une séance

`endWorkout()` (🔄 D15) :
- s'il n'y a **aucune série validée** → la séance est **abandonnée** (supprimée, pas
  d'historique) ; l'UI a prévenu l'utilisateur (§ 2.4) ;
- sinon `durationMinutes = round((maintenant − date) / 60 000)`, la séance est ajoutée en
  tête de l'historique **avec toutes ses séries** (les non validées restent stockées comme
  plan pour le prochain pré-remplissage, mais ne comptent dans aucune statistique), et la
  séance en cours est effacée.
- 🔄 D37 : une séance terminée s'affiche ensuite en **résumé** (§ 15.12) ; un abandon revient à
  l'Accueil, sans résumé.

### 3.4 Finalisation automatique des séances de plus de 24 h

Contrôle effectué au **chargement** des données **et à chaque retour de l'app au premier
plan** (`ProcessLifecycleOwner` `ON_START`) (🔄 D15 — l'app Expo ne vérifiait qu'au
démarrage). Si une séance en cours existe et `maintenant − date > 24 h` (86 400 000 ms) :
- ≥ 1 série validée → déplacée dans l'historique **sans `durationMinutes`** (durée réelle
  inconnue) ;
- aucune série validée → abandonnée ;
- dans les deux cas la séance en cours est effacée et le minuteur de repos arrêté.

### 3.5 1RM estimé (Epley)

```
estimateOneRepMax(poids, reps) =
    0                              si poids ≤ 0 ou reps ≤ 0
    poids                          si reps == 1
    round(poids × (1 + reps / 30)) sinon            (arrondi à l'entier, en kg)
bestOneRepMax(séries) = max(0, estimateOneRepMax(s.weight, s.reps) pour chaque série validée)
```
`round` = arrondi « demi vers +∞ » (`Math.round` JS). En Kotlin : `roundToInt()` /
`roundToLong()` (mêmes règles) — **pas** `kotlin.math.round`, qui arrondit les demis au pair.
Valable pour tous les arrondis de ce document. Non calculé pour le cardio.

### 3.6 Série hebdomadaire (streak)

- **Semaine** = semaine ISO (lundi → dimanche, heure locale), clé `"AAAA-SS"` (année ISO de la
  semaine + numéro sur 2 chiffres). En Kotlin : `IsoFields.WEEK_BASED_YEAR` et
  `IsoFields.WEEK_OF_WEEK_BASED_YEAR` sur la date locale.
- `count(semaine)` = nombre de séances terminées dont la date (locale) tombe dans la semaine.
- **Semaine validée** : `count ≥ 3`.
- **Série actuelle** (`current`, `isAlive`) : partir de la semaine courante et remonter tant
  que la semaine ≥ la plus ancienne semaine ayant une séance :
  - semaine validée → `current++`, `manques = 0` ;
  - sinon, si c'est la semaine **courante** → ignorée (jamais pénalisée) ;
  - sinon `manques++` ; si `manques > 1` → `isAlive = false`, arrêt.
  - **Une** semaine non validée isolée est tolérée (ne compte pas, ne casse pas) ; deux
    consécutives cassent la série. Boucle terminée sans casser → `isAlive = true`.
- **Meilleure série** (`best`) : toutes les semaines, de la plus ancienne à celle qui précède
  la semaine courante, même règle (validée → `tmp++`, `best = max(best, tmp)`,
  `manques = 0` ; sinon `manques++`, et si `manques > 1` → `tmp = 0`, `manques = 0`). Puis
  `best = max(best, current)`.
- **Mini-calendrier** (8 semaines, `courante − 7` → `courante`) :
  - semaine courante avec `count < 3` → « aucune » (le nombre reste affiché) ;
  - `count ≥ 3` → « validée » ; `1 ≤ count ≤ 2` → « partielle » ; 0 → « aucune ».
- Aucune séance → `current = best = 0`, carte masquée.

### 3.7 Temps passé à la salle

```
MIN = 10 min
Pour chaque clé de séance (§ 3.1) :                                   🔄 D3/D15
    moyenne(clé) = round(moyenne des durationMinutes ≥ MIN des séances de cette clé)
total = 0 ; n = 0
Pour chaque séance terminée :
    d = durationMinutes (absent → 0)
    si d ≥ MIN                    → total += d ; n++
    sinon si moyenne(clé) existe  → total += moyenne(clé) ; n++
    sinon                         → séance ignorée
moyenneAffichée = n > 0 ? round(total / n) : 0
```
(L'app Expo regroupait par nom exact, sensible à la casse.) Carte affichée si `n > 0`.
Format : `h = min / 60` (entier), `m = min % 60` → `"{m}m"` si h = 0, `"{h}h"` si m = 0,
sinon `"{h}h {m}m"`.

### 3.8 Volume

`volume(séance) = Σ reps × weight` sur les **séries validées** de tous les exercices (le cardio
vaut 0). Affiché arrondi à l'entier (kg) sur les cartes, en tonnes arrondies à l'entier sur
l'accueil (`Xt`), en tonnes au dixième dans Progrès.

### 3.9 Exercices personnalisés et archivage

- Création : `{ id: généré, name, muscleGroup }`, ajouté en fin de liste.
- Modification : nom + groupe (les séances ne stockent que l'`exerciseId`).
- **Suppression** : si l'exercice est référencé par **la séance en cours ou au moins une
  séance terminée** (présence de l'`exerciseId`, même sans série), il est seulement
  **archivé** (`archived: true`) : caché des sélecteurs, mais toujours utilisé pour nommer
  l'historique, Progrès et l'export. Sinon il est supprimé définitivement.
- Un exercice archivé n'est jamais désarchivé automatiquement.
- « Perso vs intégré » est décidé **uniquement** par l'appartenance de l'id au catalogue
  (§ 6.1). Un exercice stocké dont l'id est un id intégré est ignoré au chargement.

### 3.10 Statistiques de l'écran Progrès

- **Poids de corps (graphique)** : un point par **jour local**. Séances (de la plus récente à
  la plus ancienne) avec `bodyWeight ≠ 0` : pour chaque jour, la séance la plus récente du
  jour donne la valeur ; puis chaque **pesée manuelle** écrase la valeur de son jour. Tri par
  jour croissant, libellés `dd/MM`.
- **Calories / eau** : un point par jour où la valeur est **> 0**, tri croissant.
  - `aujourd'hui` = valeur du jour courant (0 si absente) ;
  - `moy. 7 jours` (🔄 D15) = moyenne des valeurs > 0 des jours de la fenêtre calendaire
    **[aujourd'hui − 6 j ; aujourd'hui]** (les jours non saisis sont ignorés, pas comptés à
    0) ; aucun jour saisi dans la fenêtre → `—`. Calories arrondies à l'entier ; eau
    affichée en L `x.x` ;
  - `jours objectif atteint` (🔄 D15) = nombre de jours dont la valeur ≥ **l'objectif en
    vigueur ce jour-là** (`goalFor(jour)`, § 3.12) ;
  - `meilleure journée` = max ; `jours suivis` = nombre de points.
- **Groupes musculaires** (seules les **séries validées** comptent, 🔄 D15) :
  - `séances(groupe)` = nombre de séances terminées contenant ≥ 1 exercice du groupe avec
    ≥ 1 série validée ;
  - métrique par séance d'un exercice : force = **poids max** des séries validées ; cardio =
    **somme des durées** (min) ;
  - pour chaque exercice du groupe (archivés inclus) : séances où il a ≥ 1 série validée
    (chronologique), points (date `dd/MM`, métrique), record = max des points ;
  - **top 3** = exercices ayant ≥ 1 séance, triés par nombre de séances décroissant
    (égalité : ordre du catalogue puis des perso) ;
  - « Charge max » / « Durée max » = max des records du top 3 ;
  - « Vol. max » = max, sur les séances, de `Σ reps×poids` des exercices du groupe, affiché
    `round(v / 100) / 10` + `t` ; cardio : max de `Σ distance` → `x.x km` ;
  - tendance = dernier point − premier point (si ≥ 2 points), 1 décimale.
  - 🔄 D34 (§ 15.9.3) : métrique au poids du corps = reps max par séance (charge ajoutée dès qu'il
    y a du lest) ; « Charge max » = max des records en kg de **tous** les exercices du groupe ; plus
    de top 3 : les exercices sont triés par date de dernière séance ; la tendance reste dernier −
    premier point.

### 3.11 Pesées manuelles

- Une pesée = `{ date, weight }`, `date` = **midi local** du jour choisi (instant ISO UTC).
- Ajouter une pesée **remplace** toute pesée du **même jour local**.
- Suppression par égalité exacte de `date`.
- Liste triée par `date` décroissante.
- Validation : `0 < poids ≤ 500` kg, date valide (§ 10.3).

### 3.12 Calories, eau, objectifs et compléments « pris »

- Journaux indexés par **jour local** `"AAAA-MM-JJ"` (§ 10.2).
- `addCalories(v)` : `log[aujourd'hui] = max(0, ancien + v)` ; `setCaloriesToday(v)` :
  `max(0, v)`. Idem `addWater(Δml)` (⟲ = `addWater(−valeur_du_jour)`).
- Calories : entiers. Eau : saisie en ml entiers, objectif saisi en L.
- **Objectifs datés** (🔄 D15 — l'app Expo appliquait l'objectif actuel rétroactivement) :
  - `calorieGoalHistory` / `waterGoalHistory` : `{ "AAAA-MM-JJ": valeur }`, chaque entrée =
    objectif en vigueur **à partir** de ce jour ; il existe toujours une entrée
    `"0000-01-01"` (valable pour tout le passé) ;
  - `goalFor(jour)` = valeur de la plus grande clé ≤ `jour` ;
  - changer l'objectif le jour J écrit `history[J] = v` (remplace une éventuelle entrée du même
    jour) ; `calorieGoal` / `waterGoal` = objectif courant (= `goalFor(aujourd'hui)`) ;
  - défauts : **2000 kcal**, **2000 ml**.
- `bottleSize` est supprimé du modèle (🔄 D15 — inutilisé) ; ignoré à l'import.
- **« Pris aujourd'hui »** : `taken[supplementId] = jour local de la prise`. Basculer : si la
  valeur vaut aujourd'hui → l'entrée est supprimée, sinon elle vaut aujourd'hui. « Pris » ssi
  `taken[id] == aujourd'hui`.
  🔄 D15 — nettoyage : supprimer un complément supprime son entrée ; au chargement, on ne
  garde que les entrées dont le complément existe **et** dont la valeur est aujourd'hui.
- 🔄 D41 — **historique des prises** (`Supplement.intakes`, § 15.15.4) : cocher « pris aujourd'hui »
  enregistre aussi la prise du jour dans le complément (`{ jour: doses }`, une dose), la décocher la
  retire ; **un seul enregistrement par complément et par jour**. « Pris aujourd'hui » se lit
  toujours dans `taken`, inchangé ; l'historique sert à la sheet « Journée » (§ 15.15.2).
  🔄 D42 : la coche enregistre les **doses par jour** du complément (1 par défaut, § 15.16.4), et
  l'historique décompte le **stock** (§ 15.16.4).
- 🔄 D42 — **protéines** (§ 15.16.1) : `proteinLog` indexé par jour local comme les calories, grammes
  entiers, `addProtein(v)` / `setProtein(v)` bornés à ≥ 0 ; objectif **non daté**, calculé chaque jour
  depuis la dernière pesée connue (g par kg) ou valeur fixe.
- Le « jour courant » est un `StateFlow` recalculé à minuit (timer jusqu'au prochain minuit
  local) et à chaque retour au premier plan ; changement de fuseau
  (`ACTION_TIMEZONE_CHANGED`) → recalcul.

### 3.13 Minuteur de repos (règles)

Durée = préférence `restSeconds` (défaut **180 s** ; préréglages 60, 90, 120, 150, 180, 240,
300 s). Comportement et notifications au § 7.2.

### 3.14 Préférences

- `restSeconds` : entier > 0, sinon 180.
- `theme` (🔄 D10) : `"system"` | `"light"` | `"dark"` ; défaut `"dark"` (🔄 D35, § 15.10.4 ; c'était
  `"system"`).
- **Langue** (🔄 D9) : n'est plus une préférence de l'app ; elle est gérée par AppCompat
  (langue du système par défaut, surcharge possible FR/EN). Un changement de langue recrée
  l'activité, renomme les canaux de notification ; les textes des notifications sont
  calculés au moment où elles sont affichées, donc rien à reprogrammer.
- Locale de formatage : la locale effective de l'app (`fr-*` ou `en-*`), c'est-à-dire celle
  des textes affichés : la première locale de la liste (langues du système, ou choix FR / EN
  de l'app) dont la langue a des textes, région comprise (`en-GB`, `fr-CA`) ; aucune →
  français, comme les textes (§ 9.4).
- `weighInReminder` (🔄 D38) : rappel de pesée (activé, heure, jours), désactivé par défaut,
  07:30, tous les jours (§ 15.13.3, § 15.13.5).
- Préférences locales non sauvegardées : carte de transfert masquée (§ 2.2) ; copie de la
  langue choisie dans l'app, relue par les notifications construites hors activité avant
  Android 13 (§ 9.4).

### 3.15 Identifiants

Les nouveaux ids sont des UUID v4 (`UUID.randomUUID().toString()`). Les ids sont des
**chaînes opaques** : à l'import, toute chaîne non vide est acceptée (l'app Expo générait
`Date.now()` + 9 caractères base 36, ex. `"1762871234567k3j9x2a1b"`).

### 3.16 Séries validées

Règle transversale (🔄 D15) : **seules les séries validées (`completed == true`) comptent**
dans les statistiques (accueil, historique d'exercice, Progrès, records, 1RM, temps en salle
via les séances retenues) et dans l'export CSV. Les séries non validées sont conservées
dans les séances (plan pour le pré-remplissage, modifiables dans l'éditeur) et dans la
sauvegarde JSON. Les anciennes séances importées depuis l'app Expo ont toutes leurs séries
marquées validées (§ 5.3), pour que leurs statistiques ne changent pas.

### 3.17 Vérification des mises à jour (🔄 D25)

> 🔄 D39 — **complété par le § 15.14** (notification de mise à jour) : la vérification
> automatique n'a plus lieu au retour au premier plan mais **une fois par jour vers 18 h, en
> arrière-plan, réseau requis**, et **seulement si la notification « Nouvelle version
> disponible » est activée**, quelle que soit la source d'installation (réglage coupé : aucune
> requête automatique) ; « Vérifier maintenant » marche dans tous les cas. La comparaison
> passe par le `versionCode` (tag `v1.0.n` → `1000 + n`). Requête, lecture de la réponse,
> enregistrement et installation ci-dessous restent valables.

- **Active seulement sans gestionnaire de mises à jour** : désactivée si l'app a été installée
  (ou sa dernière mise à jour faite) par Obtainium (`dev.imranr.obtainium`,
  `dev.imranr.obtainium.fdroid`) ou au nom du Play Store (`com.android.vending`, nom
  qu'Obtainium peut emprunter via Shizuku) — `PackageManager.getInstallSourceInfo`
  (installateur et initiateur, Android 11+), `getInstallerPackageName` avant. Relu à chaque
  démarrage.
- **Requête** : `GET https://api.github.com/repos/Bouss45/MuscleNote/releases/latest` (dernière
  Release, ni brouillon ni pré-release), HTTPS, délai 10 s, en-tête `User-Agent:
  MuscleNote-Android/{version}` ; aucune donnée de l'app n'est envoyée. Version = tag sans le
  `v` ; APK = premier fichier `.apk` de la Release.
- **Quand** : au retour au premier plan, si la dernière vérification réussie date de 24 h ou
  plus (ou n'a jamais eu lieu) ; après un échec, pas de nouvel essai automatique avant 1 h.
  [Vérifier] dans Paramètres vérifie tout de suite. Une vérification à la fois.
- **Comparaison** : nombres de la version un à un (`1.0.10` > `1.0.9`, nombre manquant = 0),
  contre le `versionName` installé.
- **Enregistré** dans `update_check.json` (§ 4.2, hors sauvegarde) : instant de la dernière
  vérification réussie, dernière version vue, page et APK, version dont l'annonce a été
  masquée. Un échec ne modifie rien d'enregistré.
- **Installation** : [Télécharger] ouvre l'APK dans le navigateur ; l'utilisateur l'ouvre et
  Android l'installe par-dessus (même signature, `versionCode` supérieur), données conservées.
  Pas de téléchargement ni d'installation par l'app (aucune permission supplémentaire).

---

## 4. Modèle de données et stockage

### 4.1 Types

Légende : **req.** = toujours présent ; *opt.* = peut être absent.

**`Exercise`** (seuls les exercices perso sont stockés)
| champ | type | | description |
|---|---|---|---|
| `id` | string | req. | id du catalogue (§ 6.1) ou id généré |
| `name` | string | req. | nom saisi (perso) |
| `muscleGroup` | `MuscleGroup` | req. | un des 9 groupes |
| `archived` | boolean | *opt.* | `true` = supprimé mais encore référencé |
| `primaryMuscles` (🔄 D43) | string[] | *opt.* | muscles principaux, ids du § 15.17.1 ; omis s'il est vide (§ 15.17.8) |
| `secondaryMuscles` (🔄 D43) | string[] | *opt.* | muscles secondaires, idem |

**`MuscleGroup`** — enum Kotlin sérialisé en **chaînes exactes, accents compris** :
`"dos"`, `"pecs"`, `"biceps"`, `"triceps"`, `"jambes"`, `"épaules"`, `"abdos"`, `"cardio"`,
`"avant-bras"`.

**`Set`** (série)
| champ | type | | unité | description |
|---|---|---|---|---|
| `id` | string | req. | | |
| `reps` | entier | req. | répétitions | 0 pour le cardio |
| `weight` | décimal | req. | kg | 0 pour le cardio |
| `completed` | boolean | req. | | série validée |
| `duration` | décimal | *opt.* | **minutes** | cardio |
| `distance` | décimal | *opt.* | **km** | cardio |
| `speed` | décimal | *opt.* | **km/h** | cardio |
| `incline` | décimal | *opt.* | **%** | cardio |

Un exercice est « cardio » ssi `muscleGroup == "cardio"` ; c'est ce qui décide des champs
saisis/affichés, pas la présence des champs.

**`WorkoutExercise`**
| champ | type | | description |
|---|---|---|---|
| `exerciseId` | string | req. | unique dans une séance |
| `sets` | `Set[]` | req. | ordre d'affichage |
| `note` | string | *opt.* | note libre |

**`Workout`** (séance)
| champ | type | | unité | description |
|---|---|---|---|---|
| `id` | string | req. | | |
| `name` | string | req. | | nom perso, ou libellé du préréglage au démarrage ; peut être `""` |
| `preset` | `PresetId` | *opt.* | | 🔄 D3 — identifiant de préréglage (§ 6.3) ; prioritaire sur `name` pour l'affichage et la clé |
| `date` | string | req. | instant ISO 8601 UTC (ex. `2026-11-10T17:05:12.345Z`) | début de séance |
| `bodyWeight` | décimal | req. | kg | 0 = inconnu ; 🔄 D38 : 0 = non saisi (pesée passée, ou jour déjà pesé), toujours écrit, lu absent comme 0 (§ 15.13.5) |
| `exercises` | `WorkoutExercise[]` | req. | | ordre de la séance |
| `durationMinutes` | entier | *opt.* | minutes | absent si en cours ou auto-finalisée |

**`BodyWeightEntry`** (pesée) : `date` instant ISO UTC (midi local du jour) req. ;
`weight` décimal kg req.

**`Supplement`**
| champ | type | | description |
|---|---|---|---|
| `id` | string | req. | |
| `name` | string | req. | |
| `dosage` | string | req. | texte libre (peut être `""`, peut contenir une virgule) |
| `unit` | string | req. | `g` `mg` `ml` `caps` `cp` `dose` `ui` |
| `reminderEnabled` | boolean | req. | interrupteur global |
| `reminders` | `Reminder[]` | req. | conservés même si désactivés |
| `intakes` (🔄 D41) | objet | opt. | historique des prises : `{ "AAAA-MM-JJ": doses }`, un enregistrement par jour local, doses ≥ 1 ; omis s'il est vide (§ 15.15.4) |
| `dosesPerDay` (🔄 D42) | entier | opt. | doses comptées par une coche « pris aujourd'hui », 1 à 20 ; omis à 1 (§ 15.16.8) |
| `stock` (🔄 D42) | objet | opt. | stock suivi : `{ doses, day, dayIntake? }` — doses comptées le jour local `day`, après `dayIntake` doses prises ce jour-là ; absent = stock non suivi (§ 15.16.8) |
| `lowStockDays` (🔄 D42) | entier | opt. | seuil du stock bas, en jours, 0 à 99 ; omis à 7 (§ 15.16.8) |

**`Reminder`** : `id` string req. ; `hour` 0–23 req. ; `minute` 0–59 req.
(🔄 D7 — le champ Expo `notifId` est supprimé ; les alarmes sont identifiées par
`supplementId` + `reminderId`, § 7.3.)

**`TakenRecord`** : objet `{ [supplementId]: "AAAA-MM-JJ" }`.

**`DailyTracker`**
| champ | type | unité | défaut |
|---|---|---|---|
| `calorieGoal` | entier | kcal | 2000 (objectif courant) |
| `waterGoal` | entier | **ml** | 2000 (objectif courant) |
| `calorieGoalHistory` | `{ "AAAA-MM-JJ": entier }` | kcal | `{ "0000-01-01": 2000 }` — 🔄 D15 |
| `waterGoalHistory` | `{ "AAAA-MM-JJ": entier }` | ml | `{ "0000-01-01": 2000 }` — 🔄 D15 |
| `caloriesLog` | `{ "AAAA-MM-JJ": entier }` | kcal | `{}` |
| `waterLog` | `{ "AAAA-MM-JJ": entier }` | ml | `{}` |
| `proteinLog` (🔄 D42, opt.) | `{ "AAAA-MM-JJ": entier }` | g | `{}`, omis s'il est vide (§ 15.16.8) |
| `proteinGoal` (🔄 D42, opt.) | `{ mode: "perKg" \| "fixed", perKg, grams? }` | g/kg, g | `{ "mode": "perKg", "perKg": 1.6 }`, omis au défaut (§ 15.16.8) |

Les journaux peuvent contenir des jours à 0 (après ⟲).

**`Preferences`** : `{ restSeconds: entier, theme: "system" | "light" | "dark", weighInReminder? }`
(défaut 180 et `"dark"`, 🔄 D35) ; 🔄 D38 — `weighInReminder` : `{ enabled, hour, minute, days }`,
omis au défaut (désactivé, 07:30, tous les jours ; § 15.13.5).

**`RestTimerState`** (non sauvegardé) : `{ endsAt: instant ms, total: s, exerciseName }`.

### 4.2 Stockage natif (🔄 D2)

« Au plus simple » : un **fichier JSON par document**, dans `filesDir/data/`, sérialisé avec
kotlinx.serialization (même forme que les valeurs du format de sauvegarde § 5) :

| fichier | contenu |
|---|---|
| `workouts.json` | `Workout[]` terminées, triées par `date` décroissante |
| `exercises.json` | `Exercise[]` perso (archivés inclus) |
| `current_workout.json` | `Workout` en cours (fichier absent s'il n'y en a pas) |
| `body_weights.json` | `BodyWeightEntry[]` |
| `supplements.json` | `Supplement[]` |
| `supplements_taken.json` | `TakenRecord` |
| `daily_tracker.json` | `DailyTracker` |
| `preferences.json` | `Preferences` |
| `local_preferences.json` | `{ migrateCardDismissed, appLanguage? }` — préférences locales (§ 3.14), hors sauvegarde |
| `rest_timer.json` | `RestTimerState` (absent hors repos) |
| `update_check.json` | `{ checkedAt, latestVersion?, pageUrl?, apkUrl?, dismissedVersion? }` — vérification des mises à jour (§ 3.17), hors sauvegarde |
| `feedback.json` | `{ lastSentAt, blockedUntil }` — délais entre deux avis (§ 14.3), hors sauvegarde |
| `auto_backup.json` | `{ enabled, folderUri?, folderName?, lastSuccessAt?, lastFailureAt?, failure?, failureDetail? }` — sauvegarde automatique (§ 15.2), hors sauvegarde |
| `health_connect.json` | `{ enabled, sent, pending, lastVersion, lastSyncAt?, lastError? }` — envois à Health Connect (§ 15.3), hors sauvegarde |
| `meta.json` | `{ "schemaVersion": 2 }` — version du schéma de stockage |

Volume attendu : quelques centaines de séances (< 1–2 Mo), entièrement chargées en mémoire au
démarrage.

**Architecture et robustesse** :
- Un **dépôt** par domaine (séances, nutrition, préférences, minuteur), singleton
  d'application, exposant des `StateFlow`. Chaque mutation est sérialisée (`Mutex`), part de
  la **dernière valeur validée** en mémoire, puis met à jour mémoire + UI et planifie
  l'écriture en un seul point. L'UI ne garde jamais sa propre copie des données persistées
  (exception : la copie de travail de l'éditeur de séance, § 2.9).
- Écriture **atomique** (`AtomicFile` : fichier temporaire puis renommage) sur un
  dispatcher IO. Les écritures de la séance en cours sont **regroupées** (délai 300 ms) et
  forcées à la mise en arrière-plan (`ON_STOP`) ; les autres documents sont écrits
  immédiatement.
- Lecture d'un fichier illisible (JSON invalide ou mauvaise forme) → le fichier est
  **renommé** `<nom>.corrupt-<timestamp ms>.json` et le domaine repart de l'état vide (la
  prochaine écriture ne détruit donc pas l'unique copie).
- Une écriture qui échoue est journalisée, jamais propagée à l'UI.
- Après un import, **tous** les dépôts rechargent depuis le disque.
- Évolutions futures : `meta.json.schemaVersion` + migrations à la lecture, sur le même
  modèle que les sauvegardes (§ 5.3).

### 4.3 Ancien stockage (app Expo, pour référence)

AsyncStorage, clés `@gymtracker_workouts`, `_exercises`, `_current_workout`,
`_body_weights`, `_supplements`, `_supplements_taken`, `_daily_tracker`, `_preferences` ; sur
Android dans la base SQLite `databases/RKStorage`, table
`catalystLocalStorage(key TEXT PRIMARY KEY, value TEXT)`. Inaccessible depuis un autre
`applicationId` (bac à sable Android) : l'app native ne le lit jamais, les données arrivent
par le transfert du § 5.5 (D4).

---

## 5. Sauvegarde JSON, import et transfert

### 5.1 Versions du format (🔄 D18)

| version | produite par | importée par l'app native |
|---|---|---|
| 1 (ou `version` absent) | app Expo | oui, via la migration § 5.3 |
| 2 | app native | oui |
| > 2 | version future | refusée : « Ce fichier vient d'une version plus récente de MuscleNote » |

Principe d'évolution : un objet racine, une clé par document, un champ `version` entier ;
chaque nouvelle version fournit une migration `vN → vN+1`, et l'import applique la chaîne de
migrations jusqu'à la version courante avant de valider. La v2 ne fait qu'**ajouter** des
champs à la v1 (et en retirer d'inutiles) : les fichiers v2 restent lisibles par l'app Expo
(qui ignore les champs inconnus), ce qui permet de revenir en arrière pendant la transition.

### 5.2 Structure v2 et exemple

Objet racine :
- `"version": 2` ;
- `"exportedAt"` : instant ISO UTC de l'export (informatif) ;
- puis, dans cet ordre, les clés **avec leur préfixe historique** (conservé pour la
  compatibilité) et leur valeur JSON :
  `@gymtracker_workouts`, `@gymtracker_exercises`, `@gymtracker_body_weights`,
  `@gymtracker_supplements`, `@gymtracker_supplements_taken`, `@gymtracker_daily_tracker`,
  `@gymtracker_preferences`.
- La séance en cours n'est **jamais** exportée.
- En v2, toutes les clés sont toujours présentes (documents vides si besoin) ; les champs
  optionnels absents sont **omis** (jamais `null`).

```json
{
  "version": 2,
  "exportedAt": "2026-11-11T18:42:07.123Z",
  "@gymtracker_workouts": [
    {
      "id": "5f0c2a9e-3b1d-4c7a-9e2f-1a2b3c4d5e6f",
      "name": "Push",
      "preset": "push",
      "date": "2026-11-10T17:05:12.345Z",
      "bodyWeight": 78.5,
      "exercises": [
        {
          "exerciseId": "bench",
          "note": "Prise un peu plus serrée",
          "sets": [
            { "id": "0b6f0f0e-0001-4000-8000-000000000001", "reps": 8, "weight": 80, "completed": true },
            { "id": "0b6f0f0e-0001-4000-8000-000000000002", "reps": 6, "weight": 82.5, "completed": false }
          ]
        },
        {
          "exerciseId": "1760000000000abcdefghi",
          "sets": [
            { "id": "0b6f0f0e-0001-4000-8000-000000000003", "reps": 12, "weight": 30, "completed": true }
          ]
        },
        {
          "exerciseId": "treadmill",
          "sets": [
            {
              "id": "0b6f0f0e-0001-4000-8000-000000000004",
              "reps": 0, "weight": 0, "completed": true,
              "duration": 20, "distance": 3.2, "speed": 9.5, "incline": 2
            }
          ]
        }
      ],
      "durationMinutes": 72
    },
    {
      "id": "1755000000000aaaaaaaaa",
      "name": "Épaules / Mollets",
      "date": "2026-11-08T16:30:00.000Z",
      "bodyWeight": 78.9,
      "exercises": [
        {
          "exerciseId": "ohp",
          "sets": [ { "id": "1755000000001bbbbbbbbb", "reps": 10, "weight": 40, "completed": true } ]
        }
      ],
      "durationMinutes": 48
    }
  ],
  "@gymtracker_exercises": [
    { "id": "1760000000000abcdefghi", "name": "Hip thrust", "muscleGroup": "jambes" },
    { "id": "1755000000000zyxwvutsr", "name": "Shrug", "muscleGroup": "dos", "archived": true }
  ],
  "@gymtracker_body_weights": [
    { "date": "2026-11-09T11:00:00.000Z", "weight": 78.2 }
  ],
  "@gymtracker_supplements": [
    {
      "id": "1758000000000mnbvcxzlk",
      "name": "Créatine",
      "dosage": "5",
      "unit": "g",
      "reminderEnabled": true,
      "reminders": [
        { "id": "1758000000001poiuytrez", "hour": 8, "minute": 0 },
        { "id": "1758000000002lkjhgfdsq", "hour": 20, "minute": 35 }
      ]
    }
  ],
  "@gymtracker_supplements_taken": {
    "1758000000000mnbvcxzlk": "2026-11-11"
  },
  "@gymtracker_daily_tracker": {
    "calorieGoal": 2800,
    "waterGoal": 2500,
    "calorieGoalHistory": { "0000-01-01": 2500, "2026-11-01": 2800 },
    "waterGoalHistory": { "0000-01-01": 2500 },
    "caloriesLog": { "2026-11-10": 2650, "2026-11-11": 1200 },
    "waterLog": { "2026-11-10": 0, "2026-11-11": 1500 }
  },
  "@gymtracker_preferences": { "restSeconds": 150, "theme": "system" }
}
```
(La pesée du 9 novembre est à midi heure de Paris = `11:00Z` en hiver.)

Différences v2 / v1 : `Workout.preset` ajouté ; séries toujours avec `id` et `completed` ;
`Reminder.notifId` retiré ; `DailyTracker.bottleSize` retiré, `calorieGoalHistory` /
`waterGoalHistory` ajoutés ; `Preferences.language` retiré, `theme` ajouté.

### 5.3 Validation et migration à l'import

**Validation de forme** (identique en v1 et v2 ; une clé absente du fichier est ignorée) :
| clé | règle |
|---|---|
| workouts | tableau ; chaque élément objet avec `id` string non vide (§ 3.15), `date` string parseable en instant, `name` string/absent/null, `bodyWeight` nombre fini/absent/null, `preset` string/absent, `exercises` tableau d'objets `{ exerciseId: string, sets: tableau }`, chaque set objet avec `reps` et `weight` nombres finis et `duration`/`distance`/`speed`/`incline` nombres finis, absents ou null |
| exercises | tableau d'objets avec `id` (non vide), `name`, `muscleGroup` strings |
| body_weights | tableau d'objets `{ date: string parseable en instant, weight: nombre fini }` |
| supplements | tableau d'objets avec `id` (non vide) et `name` strings |
| supplements_taken | objet |
| daily_tracker | objet |
| preferences | objet |

Une clé invalide → **tout l'import est refusé** : « contenu invalide pour @gymtracker_… ».
Aucune clé reconnue → « Aucune donnée MuscleNote trouvée dans ce fichier ». JSON invalide
ou racine non-objet → « Format invalide ».

**Migration v1 → v2** (appliquée aux fichiers v1 avant normalisation) :
- **Séances** :
  - `name` absent/null → `""` ; `bodyWeight` absent/null → `0` ;
  - `preset` déduit du nom (🔄 D3) : `normalizeSearch(name)` comparé aux libellés FR puis EN
    des préréglages (§ 6.3) ; correspondance → `preset` renseigné (`name` conservé) ;
    « Legs » → `legs` (en anglais le préréglage Split « Jambes » s'appelait aussi « Legs » :
    ces séances sont rattachées à `legs`) ; pas de correspondance → pas de `preset` ;
  - séries : `id` absent → généré ; **`completed` forcé à `true`** pour toutes les séries
    des séances terminées (l'app Expo comptait toutes les séries ; cela préserve les
    statistiques historiques — D21) ; champs cardio `null` → absents ;
    `reps` non entier → arrondi.
- **Exercices** : 🔄 D16 — `muscleGroup` inconnu → normalisé (`normalizeSearch`, espaces et
  tirets ignorés) puis comparé aux 9 ids et aux libellés FR/EN des groupes (ex. `"Epaules"`,
  `"shoulders"`, `"avant bras"` → `épaules` / `avant-bras`) ; toujours inconnu → rangé dans
  **`dos`** (groupe par défaut du formulaire).
- **Compléments** : ancien format (sans `reminders`) :
  `{ id, name, dosage?, unit?, reminderEnabled?, reminderHour?, reminderMinute?, notifId? }` →
  `dosage ?? ""`, `unit ?? "g"`, `reminderEnabled ?? false`,
  `reminders = reminderEnabled ? [{ id: nouveau, hour: reminderHour ?? 8, minute: reminderMinute ?? 0 }] : []` ;
  `notifId` supprimé partout.
- **Tracker** : ancien format « verres » (`bottleSize` absent **et** `waterGoal ≤ 30`) →
  `waterGoal × 250` et chaque valeur de `waterLog × 250` ; champs manquants → défauts ;
  `bottleSize` supprimé ; `calorieGoalHistory = { "0000-01-01": calorieGoal }`,
  `waterGoalHistory = { "0000-01-01": waterGoal }`.
- **Pris aujourd'hui** : seules les entrées dont la valeur est aujourd'hui sont gardées.
- **Préférences** : `language` ignoré (la langue suit le système, D9) ; `restSeconds`
  validé (> 0 sinon 180) ; `theme = "dark"` (🔄 D35 : l'app Expo n'avait que le thème sombre).

**Normalisation v2** (après migration) : `preset` inconnu → retiré ; `theme` invalide ou absent →
`"dark"` (🔄 D35) ; si `calorieGoal` diffère de `goalFor(aujourd'hui)` (fichier v2 modifié par
l'app Expo), on ajoute `history[aujourd'hui] = calorieGoal` (idem eau) ; historique
d'objectif absent ou vide → `{ "0000-01-01": objectif courant }` ; entrées `taken` de
compléments inconnus retirées. 🔄 D38 : `weighInReminder` des préférences lu selon le § 15.13.5
(absent ou invalide → rappel désactivé à 07:30, tous les jours). 🔄 D41 : `intakes` d'un complément
lu selon le § 15.15.4 (absent → historique vide ; entrées invalides écartées). 🔄 D42 : `dosesPerDay`,
`stock` et `lowStockDays` d'un complément, `proteinLog` et `proteinGoal` du tracker lus selon le
§ 15.16.8 (absents → 1 dose par jour, stock non suivi, seuil de 7 jours, aucune protéine, objectif de
1,6 g par kg ; valeurs invalides → ces défauts).

### 5.4 Déroulé d'un import

1. Source : sélecteur de fichiers (Paramètres) **ou** intent externe (🔄 D4/D5) —
   `ACTION_SEND` (`EXTRA_STREAM`) ou `ACTION_VIEW` avec type `application/json` ou
   `text/plain`. Le fichier est lu en UTF-8.
2. **Confirmation** : « Écraser les données actuelles ? » / « Tes N séances actuelles seront
   remplacées par celles du fichier importé. » [Annuler] [✓ Confirmer].
3. Lecture, migration (§ 5.3), validation. Erreur → message, rien n'est écrit.
4. Écriture : **seuls les documents présents dans le fichier sont remplacés** ; les autres
   données sont conservées. La séance en cours n'est pas touchée.
5. Rechargement de tous les dépôts (séances avec auto-finalisation § 3.4, nutrition,
   préférences — le thème peut changer) et **resynchronisation complète des rappels**
   (§ 7.3 : les alarmes des rappels disparus sont annulées, les autres reprogrammées).
6. Message « Import réussi ! Tes données sont à jour. » ou l'erreur. Via un intent externe,
   l'app s'ouvre sur Paramètres pour afficher ce message.

### 5.5 Transfert depuis l'app Expo (🔄 D4/D5)

Avec un nouvel `applicationId`, l'app native ne peut pas lire le stockage de l'app Expo. Le
transfert retenu se fait **en un geste, sans modifier l'app Expo** ; les deux apps
cohabitent pendant la transition :
1. dans l'app Expo : Paramètres → « Exporter mes données » ;
2. dans la feuille de partage : choisir **MuscleNote**, l'app native (l'app native déclare
   un `intent-filter` `ACTION_SEND` pour `application/json`) ;
3. l'app native affiche la confirmation d'import (§ 5.4) et migre le fichier v1.

La séance en cours n'étant pas exportée par l'app Expo, il faut la terminer avant le
transfert (la carte de l'accueil le précise). La carte de transfert (§ 2.2) explique ces
étapes tant que l'app native n'a aucune donnée.

### 5.6 Export

Fichier `musclenote_backup_AAAA-MM-JJ.json` (jour local), UTF-8, indentation 2 espaces,
format v2, écrit dans `cacheDir/exports/` puis partagé via `FileProvider` + feuille de
partage (`application/json`).

---

## 6. Catalogue des exercices et préréglages

### 6.1 Exercices intégrés

42 exercices. L'**ordre ci-dessous est l'ordre d'affichage**. Les noms affichés viennent des
chaînes `exercise.<id>` ; ils ne sont jamais lus depuis le stockage.

| # | id | nom FR | nom EN | groupe |
|---|---|---|---|---|
| 1 | `deadlift` | Soulevé de terre | Deadlift | dos |
| 2 | `pullup` | Traction | Pull-up | dos |
| 3 | `row` | Rowing barre | Barbell row | dos |
| 4 | `latpull` | Tirage vertical | Lat pulldown | dos |
| 5 | `tiragebucheron` | Tirage bûcheron | One-arm dumbbell row | dos |
| 6 | `tirageh` | Tirage horizontale | Seated cable row | dos |
| 7 | `bench` | Développé couché | Bench press | pecs |
| 8 | `incline` | Développé incliné | Incline bench press | pecs |
| 9 | `fly` | Écarté haltères | Dumbbell fly | pecs |
| 10 | `dips` | Dips | Dips | pecs |
| 11 | `dbench` | Développé couché haltères | Dumbbell bench press | pecs |
| 12 | `dincline` | Développé incliné haltères | Incline dumbbell press | pecs |
| 13 | `pecfly` | Pec Fly | Pec fly | pecs |
| 14 | `curl` | Curl barre | Barbell curl | biceps |
| 15 | `hammercurl` | Curl marteau | Hammer curl | biceps |
| 16 | `concentcurl` | Curl concentré | Concentration curl | biceps |
| 17 | `pushdown` | Pushdown câble | Cable pushdown | triceps |
| 18 | `overheadtri` | Extension nuque | Overhead triceps extension | triceps |
| 19 | `skullcrusher` | Skull crusher | Skull crusher | triceps |
| 20 | `barfront` | Barre au front | Lying barbell extension | triceps |
| 21 | `squat` | Squat | Squat | jambes |
| 22 | `legpress` | Presse à cuisses | Leg press | jambes |
| 23 | `legcurl` | Leg curl couché | Lying leg curl | jambes |
| 24 | `legext` | Leg extension | Leg extension | jambes |
| 25 | `lunges` | Fentes | Lunges | jambes |
| 26 | `calfraize` | Mollets debout | Standing calf raise | jambes |
| 27 | `adductorpress` | Adducteur press | Adductor machine | jambes |
| 28 | `abductorpress` | Abducteur press | Abductor machine | jambes |
| 29 | `calfsit` | Mollets assis | Seated calf raise | jambes |
| 30 | `ohp` | Développé militaire | Overhead press | épaules |
| 31 | `lateralraise` | Élévations latérales | Lateral raise | épaules |
| 32 | `frontraise` | Élévations frontales | Front raise | épaules |
| 33 | `crunch` | Crunch | Crunch | abdos |
| 34 | `plank` | Gainage | Plank | abdos |
| 35 | `legrise` | Relevé de jambes | Leg raise | abdos |
| 36 | `treadmill` | Tapis roulant | Treadmill | cardio |
| 37 | `bike` | Vélo | Stationary bike | cardio |
| 38 | `wristcurl` | Curl poignet | Wrist curl | avant-bras |
| 39 | `wristext` | Extension poignet | Wrist extension | avant-bras |
| 40 | `reversecurl` | Curl inversé | Reverse curl | avant-bras |
| 41 | `pinchgrip` | Pince (grip) | Pinch grip | avant-bras |
| 42 | `farmerwalk` | Farmer's walk | Farmer's walk | avant-bras |

⚠️ Les ids sont **figés** : ils sont référencés par les séances sauvegardées (fautes
comprises : `calfraize`, `legrise`).

🔄 D43 — chaque exercice intégré a aussi ses **muscles principaux et secondaires** (§ 15.17.2),
donnée embarquée dans l'app comme les noms : jamais stockée ni sauvegardée.

### 6.2 Groupes musculaires (ordre d'affichage, libellés, couleur)

| id | FR | EN | couleur | texte en thème clair (🔄 D23) |
|---|---|---|---|---|
| `dos` | Dos | Back | `#3b82f6` | `#1d4ed8` |
| `pecs` | Pecs | Chest | `#f97316` | `#c2410c` |
| `biceps` | Biceps | Biceps | `#a855f7` | `#7e22ce` |
| `triceps` | Triceps | Triceps | `#8b5cf6` | `#6d28d9` |
| `jambes` | Jambes | Legs | `#22c55e` | `#15803d` |
| `épaules` | Épaules | Shoulders | `#f59e0b` | `#b45309` |
| `abdos` | Abdos | Abs | `#ef4444` | `#b91c1c` |
| `cardio` | Cardio | Cardio | `#ec4899` | `#be185d` |
| `avant-bras` | Avant-bras | Forearms | `#14b8a6` | `#0f766e` |

> 🔄 D36 — les couleurs de groupe sont retirées avec l'ancien thème : les groupes s'affichent en
> texte (puces neutres, sous-titres), DESIGN.md § 2. Les colonnes de couleur ci-dessus et le
> badge ci-dessous ne valent plus que pour mémoire.

Badge de groupe : fond couleur à 13 %, bordure couleur à 33 %, texte couleur, forme pilule ;
taille normale 12 sp (padding 10×4), petite 10 sp (padding 7×2), Inter 600. Mêmes couleurs
dans les deux thèmes pour les fonds, bordures et courbes ; 🔄 D23 — en thème clair, le **texte
et les icônes** en couleur de groupe (badges, onglets et statistiques de Progrès) prennent la
nuance foncée de la dernière colonne : la couleur de base sur fond clair tombait à 1,9 – 3,6:1,
la nuance foncée donne 4,5 – 6,1:1. Thème sombre inchangé (4,1 – 7,4:1).

### 6.3 Préréglages de séance (🔄 D3)

Identifiants stockés dans `Workout.preset` ; libellé = chaîne `startWorkout.preset.<id>`.

| groupe | id | FR | EN |
|---|---|---|---|
| PPL | `push` | Push | Push |
| PPL | `pull` | Pull | Pull |
| PPL | `legs` | Legs | Legs |
| Split | `pecs` | Pecs | Chest |
| Split | `dos` | Dos | Back |
| Split | `shoulders` | Épaules | Shoulders |
| Split | `legsSplit` | Jambes | Legs (split) |
| Split | `arms` | Bras | Arms |
| Autre | `fullBody` | Full Body | Full Body |
| Autre | `isolation` | Isolation | Isolation |
| Autre | `cardio` | Cardio | Cardio |

⚠️ Dans l'app Expo, `legs` et `legsSplit` avaient le même libellé anglais « Legs » (deux
préréglages distincts, clés différentes). 🔄 D24 — l'app native affiche « Legs (split) » pour
`legsSplit` en anglais. Les anciennes séances anglaises nommées « Legs » restent rattachées à
`legs` à l'import (§ 5.3).

---

## 7. Notifications

### 7.1 Permissions, canaux et apparence

- **`POST_NOTIFICATIONS`** (Android 13+, 🔄 D6) demandée : au démarrage d'une séance (§ 2.3),
  à la création d'un complément avec rappels, à l'activation des rappels ou à l'ajout d'un
  rappel — seulement si elle peut encore être demandée. Refus : le minuteur fonctionne dans
  l'app (bandeau + vibration au premier plan) sans notification ; les rappels ne sonnent
  pas ; Paramètres affiche la ligne « Notifications désactivées » (§ 2.12).
- **Alarmes exactes** : `USE_EXACT_ALARM` (13+) / `SCHEDULE_EXACT_ALARM` (12–12L, accordée
  par défaut). Si `AlarmManager.canScheduleExactAlarms()` renvoie malgré tout `false`, repli
  sur `setAndAllowWhileIdle` (inexact).
- **Canaux** (noms traduits, mis à jour à chaque démarrage et changement de langue) :

| id | nom FR | nom EN | importance | son | vibration | écran verrouillé |
|---|---|---|---|---|---|---|
| `workout-ongoing` (🔄 D31) | Séance en cours | Workout in progress | LOW | non | non | public |
| `rest-timer` | Timer de repos | Rest timer | HIGH | défaut | `[0, 250, 250, 250]` ms | public |
| `supplements` | Rappels suppléments | Supplement reminders | HIGH | défaut | `[0, 250, 250, 250]` ms | privé |
| `weigh-in` (🔄 D38) | Rappel de pesée | Weigh-in reminder | HIGH | défaut | `[0, 250, 250, 250]` ms | privé |
| `updates` (🔄 D39) | Mises à jour | Updates | DEFAULT | défaut | non | public |

- 🔄 D31 : le canal `rest-timer-ongoing` (« Repos en cours », 🔄 D6) des versions jusqu'à la
  v1.0.6 est **supprimé** au démarrage ; la notification « Séance en cours » (§ 15.5) le remplace.
- Petite icône : silhouette blanche sur transparent (vecteur dérivé de
  `assets/images/notification-icon.png`), couleur d'accent `#9B1C1C`. Pas de badge.
  🔄 D33 : l'haltère du logo en silhouette blanche, accent graphite `#17181B` (§ 15.8.5).
- Notification affichée app ouverte : oui (comportement Android standard des canaux HIGH),
  sauf la fin de repos au premier plan (§ 7.2).
- Tous les `PendingIntent` sont `FLAG_IMMUTABLE`.

### 7.2 Minuteur de repos (🔄 D6)

- **Un seul** minuteur pour toute l'app (un nouveau repos remplace le précédent), géré par
  le dépôt du minuteur, indépendant de l'écran : il continue si l'on quitte la séance.
  **Basé sur l'instant de fin** `endsAt = maintenant + restSeconds` ; l'état
  (`endsAt`, `total`, nom de l'exercice) est persisté dans `rest_timer.json` pour survivre à
  la mort du processus.
- **Démarrage** : en validant une série en séance (non validée → validée). Dévalider ne fait
  rien. Durée = `restSeconds` au moment du démarrage.
- **Au démarrage du repos** :
  1. 🔄 D31 — le repos s'affiche dans la notification **« Séance en cours »** (§ 15.5) : titre
     « {séance} · Repos », compte à rebours **géré par le système** jusqu'à `endsAt`, prochaine
     série, actions « −30 s », « +30 s », « Passer ». (Jusqu'à la v1.0.6 : notification à part
     « Repos en cours » sur `rest-timer-ongoing`, action « Passer ».) Pas de service de premier
     plan : aucune mise à jour périodique n'est nécessaire ;
  2. **alarme exacte** `setExactAndAllowWhileIdle(RTC_WAKEUP, endsAt)` vers le récepteur de
     fin de repos (une seule alarme active, remplacée à chaque démarrage).
- **Fin du repos** (récepteur d'alarme = source unique de l'événement, pas de double
  alerte) : supprime `rest_timer.json`, remet la notification de séance hors repos (§ 15.5), puis :
  - app **au premier plan** (`ProcessLifecycleOwner` ≥ `STARTED`) : vibration « succès »
    seulement, pas de notification ;
  - app **en arrière-plan** ou processus tué : notification sur `rest-timer` : titre
    « Repos terminé 💪 », texte « C'est parti pour {exercice} ! », son + vibration,
    `VISIBILITY_PUBLIC`, auto-annulable ; toucher → `workout` ; 🔄 D31 : action « Valider la
    série » quand une série reste à valider (§ 15.5).
- **Arrêt** (action « Passer » de la notification, bouton « Passer » du bandeau, fin ou
  abandon de séance) : annule l'alarme et l'état persisté ; la notification de séance repasse
  hors repos (ou disparaît avec la séance).
- 🔄 D31 — **« −30 s » / « +30 s »** (notification de séance, § 15.5) : `endsAt` recule ou avance
  de 30 s, l'alarme est reprogrammée ; `total` suit (sans descendre sous le temps restant), pour
  que la barre du bandeau garde son sens. « −30 s » avec moins de 30 s restantes arrête le repos
  comme « Passer » (sans alerte).
- 🔄 D32 — le bandeau ci-dessous est remplacé par le **bandeau de repos dans la liste** de la
  séance (§ 15.7.1) : jaune, avec « −30 s », « +30 s » et « Passer ».
- **Bandeau** (pied de l'écran de séance) : icône horloge, « Repos », compte à rebours
  `m:ss` (17 sp gras, recalculé chaque seconde depuis `endsAt`), barre de progression
  (restant / total), bouton « ⏭ Passer ». Couleur selon la part restante : > 50 % →
  `success` ; > 25 % → `#f59e0b` ; sinon `destructive` (fond couleur à 13 %, bordure à 33 %).
  Le bandeau disparaît quand `maintenant ≥ endsAt`.

### 7.3 Rappels de compléments (🔄 D7)

- Pour chaque rappel `(hour, minute)` d'un complément dont `reminderEnabled` est vrai :
  **une alarme exacte** (`setExactAndAllowWhileIdle`, `RTC_WAKEUP`) à la **prochaine
  occurrence** de cette heure locale. `AlarmManager` n'ayant pas de répétition exacte, le
  récepteur programme l'occurrence du lendemain à chaque déclenchement.
- Identité de l'alarme : `PendingIntent` dont l'URI de données est
  `musclenote://reminder/{supplementId}/{reminderId}` (unique, permet l'annulation sans
  stocker d'id système).
- Prochaine occurrence : `ZonedDateTime.of(aujourd'hui, LocalTime.of(h, m), fuseau)`, +1 jour
  si ≤ maintenant. Heure inexistante (passage à l'heure d'été) → décalée après le saut ;
  heure ambiguë (passage à l'heure d'hiver) → première occurrence.
- **À la sonnerie**, le récepteur relit les données : complément ou rappel disparu, ou rappels
  désactivés → rien (et pas de reprogrammation) ; sinon notification sur `supplements` :
  - titre `💊 {nom}` ;
  - texte `N'oublie pas ta dose ({dosage} {unité}) !` si `dosage` non vide, sinon
    `N'oublie pas ta dose !` — **dans la langue courante de l'app au moment de l'affichage** ;
  - son ; toucher → onglet Nutrition ;
  - puis reprogrammation au lendemain.
- **Synchronisation d'un complément** (création, activation/désactivation, ajout,
  modification ou suppression d'un rappel, suppression du complément) : annuler les alarmes
  concernées puis reprogrammer celles qui doivent exister.
- **Resynchronisation complète** — au démarrage de l'app, après un import, et via les
  récepteurs système `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED` et `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` : reprogrammer toutes
  les alarmes attendues ; lors d'un import, annuler d'abord celles des rappels qui
  existaient avant et n'existent plus.
- Toutes ces opérations passent par une **file sérielle** (une à la fois, chacune relisant
  l'état le plus récent), et le récepteur se « répare » lui-même (vérification ci-dessus) :
  aucune notification orpheline possible.
- 🔄 D38 — le **rappel de pesée** (§ 15.13.4) suit le même mécanisme : alarme exacte à la prochaine
  occurrence d'un jour coché, relecture à la sonnerie, mêmes resynchronisations.

---

## 8. Export CSV

🔄 D1 — remplace l'export Excel (« au plus simple » : pas de bibliothèque).

- Déclenché depuis Paramètres (« Exporter en CSV »).
- Produit **jusqu'à 4 fichiers** (un par ancienne feuille), seulement pour les tables non
  vides ; aucune → « Aucune donnée à exporter. ».
- Noms : `musclenote_sessions_AAAA-MM-JJ.csv`, `musclenote_sets_AAAA-MM-JJ.csv`,
  `musclenote_bodyweight_AAAA-MM-JJ.csv`, `musclenote_nutrition_AAAA-MM-JJ.csv` (jour local).
- Écrits dans `cacheDir/exports/`, partagés ensemble via `FileProvider` +
  `ACTION_SEND_MULTIPLE` (`text/csv`).
- **Format** (lisible directement par Excel et Google Sheets) :
  - UTF-8 **avec BOM** ; fins de ligne CRLF ; première ligne = en-têtes traduits (chaînes
    `excel.col.*`) ;
  - séparateur et décimales selon la langue de l'app : **FR → `;` et virgule décimale**,
    **EN → `,` et point décimal** ; pas de séparateur de milliers ;
  - guillemets RFC 4180 : un champ contenant le séparateur, `"`, CR ou LF est entouré de `"`,
    les `"` internes sont doublés ;
  - dates **ISO `AAAA-MM-JJ`** (jour local), heures `HH:mm` ;
  - cellule vide = champ vide.
- Seules les **séries validées** sont exportées et comptées (🔄 D15).

**`sessions`** (une ligne par séance terminée, plus récente d'abord)
| en-tête FR | en-tête EN | valeur |
|---|---|---|
| Date | Date | jour local |
| Heure | Time | `HH:mm` |
| Nom de la séance | Session name | nom affiché (§ 3.1) |
| Durée (min) | Duration (min) | `durationMinutes` ou vide |
| Poids de corps (kg) | Body weight (kg) | `bodyWeight` ou vide si 0 |
| Exercices | Exercises | exercices ayant ≥ 1 série validée |
| Séries totales | Total sets | séries validées |
| Volume total (kg) | Total volume (kg) | Σ reps×poids des séries validées |

**`sets`** (une ligne par série validée ; séances plus récentes d'abord, puis ordre des
exercices et des séries ; **colonnes fixes**, celles qui ne s'appliquent pas restent vides)
| en-tête FR | en-tête EN | valeur |
|---|---|---|
| Date | Date | jour local de la séance |
| Séance | Session | nom affiché de la séance |
| Exercice | Exercise | nom affiché (ou l'`exerciseId` si inconnu) |
| Groupe musculaire | Muscle group | libellé traduit (vide si inconnu) |
| Série n° | Set # | rang de la série dans l'exercice (1, 2, 3…) |
| Répétitions | Reps | force : `reps` (vide si 0) |
| Poids (kg) | Weight (kg) | force : `weight` (vide si 0) |
| Durée (min) | Duration (min) | cardio : `duration` |
| Distance (km) | Distance (km) | cardio : `distance` |
| Vitesse (km/h) | Speed (km/h) | cardio : `speed` |
| Inclinaison (%) | Incline (%) | cardio : `incline` |

**`bodyweight`** (pesées **manuelles**, plus récente d'abord) : Date | Poids (kg).

**`nutrition`** (un jour par ligne, union des jours des deux journaux, ordre croissant, jours
à 0 inclus) : Date | Calories (kcal) | Eau (ml) — valeur absente → 0.

---

## 9. Thème et textes

> 🔄 D36 — **les § 9.1 à 9.3 décrivent l'ancien thème, retiré** : couleurs, typographie (Inter),
> formes, espacements et icônes sont ceux de `DESIGN.md` (§ 2 à 8), avec le thème Sombre par
> défaut (§ 15.10.4). Restent valables : le splash et le fond de fenêtre au thème choisi dans
> l'app (§ 9.1, fin), les retours haptiques (§ 9.3) et les textes (§ 9.4, 9.5).

### 9.1 Couleurs (🔄 D10 — thème clair ajouté)

Deux palettes ; choix « Système / Clair / Sombre » (§ 2.12), défaut = thème du système. Pas
de couleurs dynamiques (Material You). La palette sombre est celle de l'app Expo ; la palette
claire est une **proposition** à valider visuellement.

| token | sombre | clair | usage principal |
|---|---|---|---|
| `background` | `#080508` | `#FAF7F8` | fond des écrans |
| `foreground` | `#F8F8F8` | `#1C1014` | texte principal |
| `card` | `#150A10` | `#FFFFFF` | cartes |
| `cardForeground` | `#F8F8F8` | `#1C1014` | |
| `primary` | `#9B1C1C` | `#9B1C1C` | accent (boutons, actif, graphiques) |
| `primaryForeground` | `#FFFFFF` | `#FFFFFF` | texte sur `primary` |
| `secondary` | `#1E0E18` | `#F3E9ED` | (peu utilisé) |
| `secondaryForeground` | `#D0D0D0` | `#3D2B32` | |
| `muted` | `#120A10` | `#F2EBEE` | fonds neutres, puces inactives |
| `mutedForeground` | `#8A8A8A` | `#6E6266` | texte secondaire, placeholders |
| `accent` | `#E11D48` | `#E11D48` | (peu utilisé) |
| `destructive` | `#EF4444` | `#DC2626` | suppression, « Terminer » |
| `border` | `#2D1520` | `#E6D7DD` | bordures, séparateurs |
| `input` | `#1A0A14` | `#F7F1F3` | fond des champs |
| `success` | `#22C55E` | `#16A34A` | complet, baisse de poids |
| `warning` | `#EAB308` | `#CA8A04` | semaines partielles (§ 2.2) |
| `info` | `#38BDF8` | `#0369A1` | poids de corps, 1RM |
| `calories` | `#E85A0A` | `#C2410C` | Calories (Progrès, onglet) |
| `water` | `#3B82F6` | `#2563EB` | Eau (Progrès, onglet) |
| `progressTrack` | `#FFFFFF` à 9 % | `#000000` à 8 % | piste des barres nutrition |

Autres couleurs fixes (deux thèmes) : CSV `#217346` ; import `#3B82F6` ; ambre du minuteur
`#F59E0B` ; palette top 3 Progrès `#16A34A`, `#3B82F6`, `#A855F7` ; voile des modales
`rgba(0,0,0,0.6)` ; couleurs des groupes musculaires (§ 6.2) ; fond du splash
(`windowSplashScreenBackground`) `#0A0F0B` en sombre, `#FAF7F8` en clair. Les barres de
statut/navigation suivent le thème (icônes claires en sombre, foncées en clair).
🔄 D23 — en thème clair, le texte et les icônes dans ces couleurs prennent une nuance foncée :
ambre du minuteur `#B45309`, top 3 `#166534`, `#1D4ED8`, `#7E22CE`, groupes au § 6.2 (fonds,
bordures et courbes inchangés).

Le splash et le fond de fenêtre suivent le thème **choisi dans l'app** : sur Android 12+, le
choix est transmis au système (`UiModeManager.setApplicationNightMode`, « Système » = pas de
surcharge), ce qui recrée l'activité quand le thème effectif change ; avant Android 12, le
splash suit le thème du système.
🔄 D33 : fond du splash et de la fenêtre au jeton `background` de DESIGN.md (`#0F1012` en sombre,
`#FFFFFF` en clair) et icône du logo sur le splash (§ 15.8.5).

**Transparences** : l'app Expo concaténait un suffixe d'alpha (`#RRGGBB` + `AA`). En Compose,
`Color(0xAARRGGBB)` met l'alpha **au début** : utiliser `couleur.copy(alpha = …)`. Les
pourcentages de ce document correspondent aux suffixes Expo : `10`→6 %, `12`→7 %, `15`→8 %,
`18`→9 %, `20`/`22`→13 %, `25`→15 %, `33`→20 %, `44`→27 %, `55`→33 %.

### 9.2 Typographie (🔄 D12)

- Police **Inter** via les **Google Downloadable Fonts** (`ui-text-google-fonts`, fournisseur
  `com.google.android.gms.fonts`, certificats `font_certs.xml`), graisses 400 Regular,
  500 Medium, 600 SemiBold, 700 Bold. Repli automatique sur la police système (Roboto) tant
  que la police n'est pas disponible (premier lancement hors ligne, appareil sans Play
  Services).
- Tailles en sp. Tailles courantes : 10, 11, 12, 13, 14, 15, 16, 17, 18, 20, 22, 28.
  Repères : titres d'écran 20–28 Bold ; titres de section 18 Bold ; noms dans les cartes 15
  SemiBold ; texte secondaire 11–13 Regular ; chiffres de stat 18–28 Bold ; boutons
  principaux 15–17 SemiBold/Bold ; champs de série 15 SemiBold centrés.

### 9.3 Formes, espacements et icônes

- Rayon de référence 12 dp. Rayons utilisés : 8 (petits boutons, champs de série, icônes
  28×28), 10 (lignes de série, recherche, rappels), 12 (cartes de stat, cartes de séance),
  14 (bouton principal, cartes d'exercice, échauffement), 16 (carte de série hebdo), 20
  (puces/badges pilule, modales).
- Bordures 1 dp (1,5 dp pour les éléments mis en avant).
- Marges latérales d'écran : 20 dp (16 dp dans les listes de séance/édition).
- Retours haptiques : léger (ajouts, sélection), moyen (valider une série, retirer), fort
  (démarrer une séance), confirmations succès/erreur/avertissement (enregistrement, date
  invalide, suppression) — via `HapticFeedback` / `View.performHapticFeedback`.
- **Icônes : Material Symbols** (🔄 D11), style *Outlined*, graisse 400, en vecteurs
  (`res/drawable`, importés depuis Google Fonts Icons ; pas de dépendance
  `material-icons-extended`). Variante remplie pour l'onglet actif. Correspondance avec
  les icônes Feather de l'app Expo :

| Feather (Expo) | Material Symbols | usage |
|---|---|---|
| `home` | `home` | onglet Accueil |
| `trending-up` / `trending-down` | `trending_up` / `trending_down` | onglet Progrès, historique, tendances |
| `heart` | `favorite` | onglet Nutrition |
| `settings` | `settings` | onglet Paramètres, objectifs |
| `play` | `play_arrow` | démarrer |
| `chevron-left/right` | `chevron_left` / `chevron_right` | navigation |
| `chevron-up/down` | `expand_less` / `expand_more` | replier/déplier |
| `arrow-left` / `arrow-right` | `arrow_back` / `arrow_forward` | retour, suivant |
| `x` | `close` | fermer, retirer |
| `check` | `check` | valider |
| `check-square` | `check_box` | terminer la séance |
| `plus` / `plus-circle` | `add` / `add_circle` | ajouter |
| `trash-2` | `delete` | supprimer |
| `edit-2` | `edit` | éditer |
| `edit-3` | `edit_note` | note |
| `calendar` | `calendar_today` | date |
| `clock` | `schedule` | durée, minuteur, rappels |
| `zap` | `bolt` | série hebdo, échauffement, 1RM |
| `award` | `emoji_events` | records |
| `layers` | `layers` | exercices |
| `repeat` | `repeat` | séries |
| `activity` | `fitness_center` | états vides |
| `bar-chart-2` | `bar_chart` | graphique vide |
| `user` | `person` | poids de corps |
| `search` | `search` | recherche |
| `skip-forward` | `skip_next` | passer le repos |
| `bell` / `bell-off` | `notifications` / `notifications_off` | rappels |
| `info` | `info` | encarts d'information |
| `rotate-ccw` | `restart_alt` | remise à zéro |
| `globe` | `language` | langue |
| — | `contrast` | thème (nouveau) |
| — | `forum` / `send` | envoi d'un avis (nouveau, § 14) |
| `upload` / `download` | `upload` / `download` | export / import |
| `grid` | `table_view` | export CSV |
| `alert-circle` / `check-circle` | `error` / `check_circle` | statut |
| `wind`, `rotate-cw`, `move`, `arrow-down`, `activity` | `air`, `rotate_right`, `open_with`, `arrow_downward`, `accessibility_new` | échauffement |

### 9.4 Textes et traductions (🔄 D9)

- **Les sources de vérité des textes existants sont `artifacts/mobile/constants/i18n/fr.ts`
  (référence) et `artifacts/mobile/constants/i18n/en.ts`, au tag `expo-final`.** Ils contiennent toutes les
  chaînes visibles : écrans, composants, libellés de graphiques, exercices intégrés
  (`exercise.*`), groupes (`muscle.*`), en-têtes d'export (`excel.*`), canaux de
  notification (`channel.*`). Ce document cite certains textes pour la lisibilité ; en cas
  d'écart, ces fichiers font foi. Les chaînes **nouvelles** de l'app native sont au § 9.5.
- Conversion : `res/values/strings.xml` (FR, langue par défaut) et
  `res/values-en/strings.xml` (EN) ; clés à points → noms snake_case
  (`home.streak.hintAlive` → `home_streak_hint_alive`) ; `{{var}}` → arguments positionnels
  (`%1$s`, `%1$d`) ; couples `clé` / `clé_one` → `<plurals>` (règles CLDR : en français
  `one` couvre 0 et 1, donc « 0 séance » — plus correct que le « 0 séances » de l'app Expo).
  Les chaînes `excel.col.*` gardent leur rôle (en-têtes CSV) ; les clés devenues inutiles
  (`excel.sheet.*`, `settings.exportExcel.*`, `settings.export.successExcel`,
  `settings.export.errorExcel`, `settings.share.excel`) ne sont pas reprises.
- **Langue** : celle du système par défaut ; surcharge par app « Français » / « English »
  (`AppCompatDelegate.setApplicationLocales`, `res/xml/locales_config.xml` déclarant `fr` et
  `en`, `autoStoreLocales` pour Android < 13). Système dans une autre langue → français
  (ressources par défaut), textes **et** formats (§ 3.14). Les récepteurs (notifications)
  construisent leur texte avec la locale de l'app (`AppCompatDelegate.getApplicationLocales()`) ;
  avant Android 13, AppCompat ne la connaît qu'après la création d'une activité : dans un
  processus relancé par une alarme, les récepteurs relisent la copie gardée dans les
  préférences locales (§ 3.14).
- Restent non traduits : unités courtes (`kg`, `reps`, `min`, `km`, `km/h`, `% inc.`, `t`,
  `L`, `ml`, `kcal`), unités de compléments, libellés de langue (`Français 🇫🇷`,
  `English 🇬🇧`).
- Nombres et dates formatés selon la locale effective de l'app (`2 000` en FR, `2,000` en
  EN ; `java.time` + `DateTimeFormatter.ofLocalizedDate` / motifs équivalents à ceux de
  l'app Expo).

### 9.5 Nouvelles chaînes (à ajouter en FR et EN)

| clé | FR | EN |
|---|---|---|
| `settings.language.system` | Langue du système | System language |
| `settings.theme.title` | Thème | Theme |
| `settings.theme.desc` | Apparence de l'application | App appearance |
| `settings.theme.system` | Système | System |
| `settings.theme.light` | Clair | Light |
| `settings.theme.dark` | Sombre | Dark |
| `settings.notifications.denied` | Notifications désactivées — minuteur et rappels silencieux | Notifications are off — timer and reminders are silent |
| `settings.notifications.open` | Ouvrir les réglages | Open settings |
| `settings.exportCsv.title` | Exporter en CSV | Export as CSV |
| `settings.exportCsv.desc` | Génère des fichiers CSV (séances, séries, poids, nutrition) lisibles dans Excel ou Sheets | Creates CSV files (sessions, sets, weight, nutrition) for Excel or Sheets |
| `settings.export.successCsv` | Fichiers CSV exportés ! | CSV files exported! |
| `settings.share.csv` | Exporter en CSV | Export as CSV |
| `settings.import.newerVersion` | Ce fichier vient d'une version plus récente de MuscleNote | This file comes from a newer version of MuscleNote |
| `settings.crash.title` | Rapport de plantage | Crash report |
| `settings.crash.desc` | Dernier plantage le {{date}} | Last crash on {{date}} |
| `settings.crash.share` | Partager | Share |
| `home.migrate.title` | Tu viens de l'ancienne MuscleNote ? | Coming from the old MuscleNote? |
| `home.migrate.text` | Termine ta séance en cours dans l'ancienne app, puis : Paramètres → Exporter mes données → choisis MuscleNote. | Finish any session in progress in the old app, then: Settings → Export my data → pick MuscleNote. |
| `workout.finishEmpty` | Aucune série validée : la séance ne sera pas enregistrée. | No completed set: this session won't be saved. |
| `workout.abandon` | Abandonner | Discard |
| `exerciseCard.removeConfirm` | Retirer « {{name}} » de la séance ? | Remove “{{name}}” from this session? |
| `exerciseCard.remove` | Retirer | Remove |
| `editWorkout.discardTitle` | Abandonner les modifications ? | Discard changes? |
| `editWorkout.discard` | Abandonner | Discard |
| `editWorkout.keepEditing` | Continuer l'édition | Keep editing |
| `editWorkout.rename` | Renommer la séance | Rename session |
| `channel.workoutOngoing` (🔄 D31, remplace `channel.restTimerOngoing` et `restTimer.ongoing*`) | Séance en cours | Workout in progress |
| `nutrition.reminderTimeTitle` | Heure du rappel | Reminder time |
| `home.update.title` | Mise à jour disponible | Update available |
| `home.update.text` | MuscleNote {{version}} est disponible (version installée : {{installed}}). | MuscleNote {{version}} is available (installed version: {{installed}}). |
| `update.download` | Télécharger | Download |
| `settings.section.app` | APPLICATION | APP |
| `settings.update.title` | Mises à jour | Updates |
| `settings.update.managed` | Version {{installed}} · mises à jour gérées par Obtainium | Version {{installed}} · updates managed by Obtainium |
| `settings.update.never` | Version {{installed}} · pas encore de vérification | Version {{installed}} · not checked yet |
| `settings.update.upToDate` | Version {{installed}} · à jour (vérification du {{date}}) | Version {{installed}} · up to date (checked on {{date}}) |
| `settings.update.available` | Version {{installed}} · la {{version}} est disponible | Version {{installed}} · {{version}} is available |
| `settings.update.failed` | Vérification impossible : pas de connexion ? | Couldn't check: no connection? |
| `settings.update.check` | Vérifier | Check now |

Textes de l'envoi d'un avis (`feedback.*`) : § 14.6.

---

## 10. Cas particuliers

### 10.1 Virgule décimale
Tous les champs numériques passent par :
- `parseDecimal(s)` = interprétation de `s.trim()` après remplacement de la **première**
  virgule par un point, en lisant le **plus long préfixe numérique** (comme `parseFloat`
  JS : `"62,5kg"` → 62,5 ; `"1,2,3"` → 1,2) ; **0** si invalide/vide ;
- `parseInteger(s)` = plus long préfixe entier de `s.trim()` (`"12,5"` → 12) ; **0** si
  invalide.
Les claviers français tapent `62,5` : sans ce remplacement on stockerait 62. En Kotlin, ne pas
utiliser `toDouble()` brut. Exception : le `dosage` des compléments est un texte libre
stocké tel quel.

### 10.2 Jour local vs UTC
- Les journaux quotidiens (calories, eau, « pris aujourd'hui », historiques d'objectifs)
  sont indexés par le **jour local** `AAAA-MM-JJ` (`LocalDate.now()`), jamais par le jour
  UTC (en France, il bascule à 1 h ou 2 h du matin).
- Les dates de séances et de pesées sont stockées en **instant UTC** mais toujours
  interprétées en local (regroupement par jour, semaine ISO, affichage).
- Un jour `AAAA-MM-JJ` converti en instant prend **midi local** (pas minuit UTC).
- Pesées à midi local → robustes à un changement de fuseau de ±11 h.

### 10.3 Dates impossibles
`buildLocalDate(année, mois 1–12, jour, heure = 12, minute = 0)` retourne `null` si une valeur
n'est pas entière, si `année ∉ [2000, 2100]`, `heure ∉ [0, 23]`, `minute ∉ [0, 59]`, ou si
la date n'existe pas (31/02, 31/04…) — en Kotlin, construire un `LocalDateTime` dans un
`try` (`DateTimeException`) plutôt que de laisser déborder. Utilisé pour la modale de date de
séance et la modale de pesée.

### 10.4 Séance en cours
- Persistée à chaque changement (écriture regroupée ≤ 300 ms, forcée à la mise en
  arrière-plan, § 4.2) → survit à la fermeture de l'app.
- Non incluse dans la sauvegarde JSON.
- Le chronomètre et `durationMinutes` reposent sur `date` (début de séance).

### 10.5 Données incohérentes
- Une séance peut référencer un `exerciseId` inconnu (import, ancien bug) : masqué en séance
  et en édition (mais conservé à l'enregistrement de l'édition), ignoré par Progrès, exporté
  sous son id dans le CSV.
- `exerciseId` est unique dans une séance (les ajouts refusent les doublons).
- Nom affiché vide → l'accueil affiche la date.

### 10.6 Recherche et normalisation
`normalizeSearch(s)` = décomposition NFD (`java.text.Normalizer`), suppression des
diacritiques combinants (U+0300–U+036F), minuscules, `trim()`. Recherche par « contient » sur
le nom **affiché** (donc dans la langue courante pour les exercices intégrés). Utilisé aussi
pour la clé de séance (§ 3.1) et les correspondances de migration (§ 5.3).

### 10.7 Groupes musculaires accentués
`"épaules"` et `"avant-bras"` sont stockés tels quels (UTF-8, `é` en NFC). Ne pas les
transformer en identifiants ASCII dans le format de sauvegarde : enum Kotlin avec
`@SerialName("épaules")` etc.

### 10.8 Comportements notables
- Supprimer une série ou une pesée : immédiat. Retirer un exercice de la séance en cours,
  supprimer une séance, un exercice perso ou un complément : confirmation en ligne.
  Quitter l'éditeur avec des modifications : dialogue (§ 2.9).
  🔄 D32 — en séance, supprimer une série, retirer un exercice, terminer ou abandonner la séance
  demandent une confirmation en bottom sheet (§ 15.7.1) ; l'éditeur garde la suppression immédiate
  d'une série (il travaille sur une copie).
- Le dernier poids de l'accueil vient de la dernière séance, pas des pesées manuelles.
- Le pré-remplissage et le temps en salle utilisent la même clé de séance (§ 3.1).

### 10.9 Rapport de plantage local (🔄 D17)
- `Thread.setDefaultUncaughtExceptionHandler` installé dans `Application.onCreate` : écrit
  `filesDir/crashes/crash-<timestamp ms>.txt` (date locale, version de l'app,
  version d'Android et modèle de l'appareil, trace complète de l'exception), garde les
  **5 plus récents**, force l'écriture de la séance en cours si possible, puis délègue au
  gestionnaire par défaut (l'app se ferme normalement).
- Aucun envoi réseau. Paramètres permet de partager ou supprimer les rapports (§ 2.12).

### 10.10 Alarmes et changements d'heure
- Rappels : heure **locale murale** ; recalcul de la prochaine occurrence à chaque
  déclenchement et lors de `TIME_SET` / `TIMEZONE_CHANGED` (§ 7.3).
- Minuteur de repos : basé sur un instant absolu ; un changement d'heure du système pendant
  un repos est sans effet sur sa durée réelle.

---

## 11. Décisions

Réponses données aux questions ouvertes de la première version de ce document, et leur
traduction dans la spec. D4, D5 et D8 ont été complétées, et D21–D22 ajoutées, en réponse
aux questions de la deuxième version (cf. § 12). D23–D24 ont été ajoutées lors de la
vérification finale (§ 13), D25 et D26 après la bascule, D27 à D29 avec le lot 1 des évolutions
(§ 15), D30 avec le correctif de Health Connect (§ 15.3), D31 avec la notification de séance
(§ 15.5), D32 avec la refonte de l'écran de séance en cours (§ 15.7), D33 avec la navigation,
l'Accueil, le Journal et le logo (§ 15.8), D34 avec la fiche exercice et Progrès (§ 15.9), D35 avec
Nutrition et Paramètres (§ 15.10), D36 avec les derniers écrans et les finitions de la refonte
(§ 15.11), D37 avec le résumé de fin de séance (§ 15.12), D38 avec la pesée facultative et le rappel
de pesée (§ 15.13), D39 avec la notification de mise à jour (§ 15.14), D40 avec les listes groupées
de la maquette v2 (DESIGN.md, § 15.6), D41 avec le calendrier du Journal (§ 15.15), D42 avec les
protéines, le stock des compléments et la moyenne sur 7 jours (§ 15.16), D43 avec la carte du corps
et les muscles travaillés (§ 15.17).

| n° | Sujet | Réponse | Ce qui est spécifié |
|---|---|---|---|
| D1 | Export Excel ou CSV | « fait au plus simple » | CSV, 4 fichiers, mêmes tables (§ 8) |
| D2 | Persistance | « oui, fait au plus simple » | un fichier JSON par document, même forme que la sauvegarde ; le format § 5 reste le format d'échange (§ 4.2) |
| D3 | Langue des noms de séance | stocker l'identifiant de préréglage | `Workout.preset`, clé de séance, migration des anciens noms (§ 3.1, 5.3, 6.3) |
| D4 | Migration des données | automatique, puis option (b) : nouvel applicationId + transfert par partage | transfert en un geste par la feuille de partage, sans modifier l'app Expo ; les deux apps cohabitent (§ 5.5) |
| D5 | `applicationId` | un nouvel applicationId : `com.musclenote.android` | `com.musclenote.android` (§ 1) |
| D6 | Minuteur de repos | visible sur l'écran (verrouillé) + demander les permissions | notification en cours avec compte à rebours système + alarme exacte de fin, demande de `POST_NOTIFICATIONS` (§ 7.1, 7.2) |
| D7 | Rappels de compléments | vrai sélecteur d'heure + alarme exacte | `TimePicker` M3, toute minute ; alarmes exactes reprogrammées chaque jour (§ 2.11, 7.3) |
| D8 | minSdk / targetSdk | minSdk 26, targetSdk = dernière API stable | minSdk 26 ; compileSdk / targetSdk 37 (Android 17) au démarrage du projet (§ 1) |
| D9 | Langue | langue système | langue du système par défaut + surcharge FR/EN par AppCompat ; plus de préférence `language` (§ 2.12, 3.14, 9.4) |
| D10 | Thème | ajouter un thème clair | palette claire + choix Système/Clair/Sombre (§ 2.12, 9.1) |
| D11 | Icônes | Material Symbols | table de correspondance (§ 9.3) |
| D12 | Police | Google Downloadable Fonts | Inter via GDF, repli Roboto (§ 9.2) |
| D13 | Graphiques | Canvas | Canvas Compose maison (§ 2.13) |
| D14 | Glisser-déposer | au plus simple et scalable | `sh.calvin.reorderable` sur `LazyColumn` (§ 2.4) |
| D15 | Bizarreries | à corriger | voir le détail ci-dessous |
| D16 | `muscleGroup` inconnu à l'import | ranger dans un groupe | normalisation puis groupe `dos` par défaut (§ 5.3) |
| D17 | Écran d'erreur fatale | rapport de crash local | § 10.9, § 2.12 |
| D18 | Format d'export JSON | au plus scalable | `version: 2` + chaîne de migrations, v1 toujours importée, v2 lisible par l'app Expo (§ 5.1) |
| D19 | Permissions | ne demander que les permissions utiles | liste fermée (§ 1) ; `INTERNET` y est entrée avec D25 (mises à jour) et sert aussi à D26 (avis), seul envoi de données, toujours déclenché par l'utilisateur ; `WAKE_LOCK` avec D28 (WorkManager, dont `ACCESS_NETWORK_STATE` et `FOREGROUND_SERVICE` sont retirées) ; `ACCESS_NETWORK_STATE` rendue avec D39 (vérification quotidienne qui attend le réseau) ; `health.WRITE_EXERCISE` et `health.WRITE_WEIGHT` avec D29, accordées dans Health Connect quand l'utilisateur active l'option, aucune permission de lecture ; `POST_PROMOTED_NOTIFICATIONS` avec D31 (Live Update, sans demande) |
| D20 | Ergonomie de saisie | au plus utile | enregistrement à chaque frappe gardé, 0 affiché vide partout, IME « Suivant » (§ 2.6) |
| D21 | Anciennes séries non cochées | toutes marquées validées à la migration | migration v1 → v2 : `completed = true` pour toutes les séries des séances terminées ; les statistiques passées ne changent pas (§ 3.16, 5.3) |
| D22 | Nom affiché pendant la transition | « MuscleNote » suivi d'un « 2 » | nom de transition distinct de l'app Expo, cible du partage au § 5.5 ; retiré à la bascule : `app_name` = « MuscleNote » (FR et EN) (§ 1, 9.5) |
| D23 | Contraste des couleurs de groupe en thème clair | texte foncé en thème clair | texte et icônes en nuance foncée (contraste ≥ 4,5:1) en thème clair : groupes, ambre du minuteur, top 3 ; fonds, bordures, courbes et thème sombre inchangés (§ 6.2, 9.1) |
| D24 | Libellé EN de `legsSplit` (« Legs » en double) | « Legs (split) » | libellé anglais « Legs (split) » ; anciennes séances « Legs » toujours rattachées à `legs` (§ 5.3, 6.3) |
| D25 | Mises à jour d'une app installée sans Obtainium | vérification intégrée | requête quotidienne à l'API GitHub, permission `INTERNET`, annonce sur l'Accueil et carte dans Paramètres ; désactivée quand Obtainium installe l'app (§ 1, 2.2, 2.12, 3.17) ; complétée par D39 (vérification quotidienne vers 18 h seulement si la notification est activée, avec ou sans Obtainium) |
| D26 | Retours des utilisateurs | envoyer un avis depuis l'app vers le serveur Discord du développeur | section « Ton avis » dans Paramètres ; webhook Discord dont l'URL vient d'un secret GitHub injecté au build (section masquée sans elle) ; envoi seulement sur appui, aucune donnée d'entraînement, aucune mention possible, un envoi par minute (§ 1, 2.12, 14) |
| D27 | Notes de version | lister les commits depuis la Release précédente | titres des commits depuis le tag de la dernière Release, sans fusions ni commits qui ne touchent que la doc ou la CI, calculés par un module JVM testé ; titres de commit en français, écrits pour l'utilisateur ; vérification des pull requests (§ 15.1) |
| D28 | Sauvegarde automatique | export JSON quotidien dans un dossier choisi | WorkManager toutes les 24 h, dossier du sélecteur système avec autorisation persistante, même nom que l'export manuel (remplacé le même jour), 14 fichiers gardés, « Sauvegarder maintenant », message si le dossier est inaccessible (§ 1, 2.12, 15.2) |
| D29 | Health Connect | écrire les séances et le poids | option masquée sans Health Connect ; `ExerciseSessionRecord` musculation et `WeightRecord` (séances et pesées), `clientRecordId` stables pour mettre à jour et supprimer, « Envoyer l'historique » ; activité de politique de confidentialité ; aucune lecture (§ 1, 2.12, 15.3) |
| D30 | Health Connect : poids en double, séances absentes, compteur (retour d'un utilisateur, v1.0.5) | un poids par jour, durée estimée, ne compter que le confirmé | un `WeightRecord` par jour local avec la règle de Progrès (§ 3.10 : la pesée remplace le poids de séance), anciens enregistrements supprimés ; séance de moins de 10 min ou sans durée envoyée avec l'estimation du temps en salle (§ 3.7, sinon 10 min), sans jamais finir après sa fin dans l'app ; envoi par type, seuls les enregistrements confirmés par Health Connect sont comptés et retenus ; état de la carte (écrit, dernier envoi, dernière erreur datée) (§ 15.3) |
| D31 | Notification de séance avec actions | une notification « Séance en cours » qui remplace celle du repos | séance, chronomètre, exercice et prochaine série ; « Valider la série » hors repos, « −30 s », « +30 s », « Passer » pendant le repos ; Live Update sur Android 16+ (`POST_PROMOTED_NOTIFICATIONS`, sans demande), sinon notification en cours ; montres : l'alerte de fin de repos, relayée par Android, vibre et propose « Valider la série » (une notification en cours n'est pas relayée) ; pas d'app Wear OS, notée comme piste ; pas de service de premier plan (§ 1, 7.1, 7.2, 15.5) |
| D32 | Refonte « Studio épuré », étape 2 : séance en cours | gabarit « en direct » ; colonne « Préc. », suggestion de charge, pavé numérique intégré, « + Série » toujours visible, repos jaune | « Préc. » = série de même rang de la dernière séance de l'exercice, recopiée au toucher ; +2,5 kg (+1 rep au poids du corps) si toutes les séries ont tenu les reps de la première, sinon même charge, jamais appliquée d'office ; pavé avec virgule, effacer, pas −/+, « Suivant » puis « Valider la série » ; confirmations en bottom sheet ; aucun changement du format JSON (§ 2.4 – 2.6, 7.2, 10.8, 15.7) |
| D33 | Refonte « Studio épuré », étape 3 : navigation, Accueil, Journal, logo | 4 onglets (Accueil, Journal, Progrès, Nutrition), Paramètres par l'engrenage, mini-barre partout ; Accueil à une seule surface (reprendre ou démarrer), dernière séance, séances de la semaine ; Journal groupé par semaine ; logo de la maquette, marque jaune pour la preview | Paramètres hors barre (l'onglet Accueil y reste sélectionné) ; mini-barre sur Journal, Progrès, Nutrition et Paramètres (sur l'Accueil, la surface la remplace) ; la séance n'est jamais deux fois dans la pile, « Réduire » revient à l'écran d'origine ; retour prédictif avec transitions en fondu ; Accueil : surface « En cours » ou « Prochaine séance » avec type suggéré (types réguliers des 42 derniers jours, le moins récent d'abord) qui ouvre « Démarrer » à l'étape du poids, puis « Dernière séance » et « Cette semaine » (séances de la semaine, série, 8 semaines) ; Journal : totaux, une section par semaine ISO (du lundi, à cheval sur deux années comprise), ligne date · durée · volume, toucher → éditeur, appui long → date ou suppression ; icône adaptative avec couche monochrome, splash, petite icône de notification ; aucun changement du format JSON (§ 2.1, 2.2, 7.1, 9.1, 15.8) |
| D34 | Refonte « Studio épuré », étape 4 : fiche exercice et Progrès | fiche exercice au gabarit fiche (chiffre héros = dernière séance de la période, variation depuis le début de la période, courbe fine touchable, périodes 1M – Tout, historique date · meilleure série · volume) ; Progrès au gabarit hub en gardant tout son contenu, exercices avec mini-courbe et variation → fiche ; rien de l'existant retiré (record personnel, 1RM estimé, 1RM par séance), seule la nouvelle fonctionnalité Records est hors périmètre ; reps au poids du corps ; pesée au pavé numérique ; aucun changement du format JSON | héros = métrique de la dernière séance de la période, variation = dernière − premier point (au toucher : point − premier point), verte / rouge / grise, défilement 250 ms au changement de période ; record de la période marqué dans la liste ; courbe `TrendCurve` en Canvas (toucher → point le plus proche, réduction au-delà de 120 points en gardant creux et pics, description TalkBack période · départ · arrivée · variation), Tout par défaut ; record personnel et 1RM estimé en rangée de stats ; séances : toutes, « 1RM ~ », toucher → éditeur ; métrique au poids du corps = reps max, charge ajoutée dès qu'il y a du lest ; Progrès : surface Poids de corps (évolution en gris faute d'objectif), Exercices (puces des groupes utilisés, stats, lignes par date de dernière séance avec mini-courbe, dernière valeur et variation, charge max sur tous les exercices en kg), Calories et eau (stats, courbe avec objectif, récapitulatif), Pesées (suppression confirmée) ; « Nouvelle pesée » au gabarit formulaire avec pavé numérique et sélecteur de date (§ 2.8, 2.10, 2.13, 3.10, 15.9) |
| D35 | Refonte « Studio épuré », étape 5a : Nutrition et Paramètres | Nutrition au gabarit hub avec toutes ses fonctions, rappels compris, une seule surface : ce qui compte aujourd'hui ; quantités au pavé numérique ; pas d'objectif de protéines ; Paramètres en liste groupée, mêmes réglages, chaque réglage en formulaire ; thème Sombre (par défaut), Clair, Système ; export, sauvegarde, Health Connect et avis inchangés ; aucun changement du format JSON sauf le thème | Nutrition : surface « Aujourd'hui » (kcal du jour en chiffre héros, « encore … » gris, « dans l'objectif » vert, « +… kcal » rouge selon le ratio de D15, piste, eau et compléments pris, boutons Calories et Eau), compléments en lignes (case « pris aujourd'hui » séparée, toucher → sheet du complément : rappels, suppression confirmée), « Nouveau complément » en sheet au clavier (nom, dosage texte libre, unités, rappels) ; formulaires Calories et Eau au pavé : « Ajouter » ou « Total du jour » (remplace ⟲ ; nouvelle règle `setWater`), « Nouveau total », objectif quotidien à partir d'aujourd'hui ; heure au `TimePicker` avec « Supprimer » ; Paramètres : Mes données, Préférences (langue, thème, minuteur, notifications), Données (sauvegarde automatique, exports, import, Health Connect), À propos (mises à jour, rapport de plantage, avis), messages sans couleur ; thème par défaut `"dark"` (absent, invalide, import v1), choix déjà enregistré gardé ; tendances de calories et d'eau toujours dans Progrès, protéines et stock hors périmètre (§ 2.11, 2.12, 3.14, 4.1, 5.3, 15.10) |
| D36 | Refonte « Studio épuré », étape 5b : finitions avant la fusion | plus aucune couleur, taille ou police en dur hors du design system, ancien thème et icônes inutiles retirés ; contrastes en sombre et en clair, texte à 200 % sans rien de coupé, TalkBack ; mise à jour depuis la v1.0 sans perte | « Démarrer une séance » en formulaire (bottom sheet : type puis poids au pavé), sélecteur d'exercices en bottom sheet (lignes à cocher, appui long pour un exercice perso), éditeur de séance au gabarit fiche (type et poids en lignes, séries au pavé, « Enregistrer » en bas) ; `ui/theme/`, Inter et les polices téléchargeables, couleurs de groupe, 52 icônes retirés ; aplat `live` en clair `#9E7100` ; composants qui grandissent avec le texte, chiffres qui rétrécissent, cibles de 48 dp, libellés TalkBack des séries ; notes de version sans les commits qui ne touchent que les tests ; aucun changement du format JSON (§ 2.3, 2.7, 2.9, 15.1, 15.11) |
| D37 | Résumé de fin de séance | affiché après « Terminer la séance » (gabarit fiche) et consultable ensuite depuis le Journal ; chiffre héros = volume total, variation par rapport à la dernière séance du même programme (verte, rouge ou grise, rien la première fois) ; durée, nombre de séries, puis chaque exercice avec sa meilleure série et un repère si un record est battu (charge ou 1RM estimé, règles de records existantes) ; un seul bouton « Terminer », qui ramène à l'Accueil ; aucun changement du format JSON, tout est calculé | résumé à la place de la séance en cours dans la pile (au-dessus de l'Accueil), notification de séance retirée comme avant ; toucher une séance du Journal ou la « Dernière séance » de l'Accueil ouvre son résumé (l'éditeur reste dans l'appui long et dans ⋮) ; même programme = même clé de séance (§ 3.1), parmi les séances plus anciennes ; variation au kg ; records = record personnel (métrique de la fiche exercice : charge, reps au poids du corps, durée en cardio) et 1RM estimé, strictement supérieurs à ceux des séances plus anciennes — égalité ou première fois : pas de record ; « Records battus · n » en vert en tête de la liste (§ 3.3, 15.7.1, 15.8.1, 15.8.4, 15.12) |
| D38 | Pesée facultative et rappel de pesée | au début de séance, pesée facultative : champ vide avec la dernière pesée (valeur et ancienneté), « Passer » qui n'enregistre aucune pesée, champ masqué si une pesée existe déjà aujourd'hui ; les calculs prennent la dernière pesée connue, ou ne s'affichent pas ; section « Rappels » de Paramètres (rappels de Nutrition et rappel de pesée), raccourci depuis la courbe de poids de Progrès ; rappel de pesée désactivé par défaut, 7 h 30, jours de la semaine, rien si déjà pesé ce jour-là (Health Connect compris si l'app le lit), toucher → saisie de la pesée au pavé, reprogrammé après un redémarrage comme les rappels de Nutrition ; poids rendu facultatif sans migration destructive, anciennes données relues telles quelles | « pesé ce jour-là » = la règle du poids d'un jour de Progrès (pesée, sinon séance avec un poids, séance en cours comprise) ; Health Connect n'est pas lu (D29, aucune permission de lecture) ; aide « Dernière pesée : 78,4 kg, il y a 3 jours. », « Passer » = séance à `bodyWeight` 0, « Commencer » exige un poids ; déjà pesé → l'étape n'a plus de champ, la séance démarre sans poids ; poids d'une séance = le sien, sinon la dernière pesée connue à son jour, affiché dans l'en-tête de la séance et la surface « En cours », jamais recopié (Progrès, Health Connect, CSV, sauvegarde inchangés) ; « Rappels » entre « Préférences » et « Données » : rappel de pesée puis une ligne par complément (sa sheet de Nutrition) ; bouton `notifications` sur la surface « Poids de corps » ; réglage dans les préférences (sauvegardé), alarme exacte à la prochaine occurrence d'un jour coché (heure locale murale, changements d'heure), relecture à la sonnerie, canal `weigh-in`, toucher → Progrès et « Nouvelle pesée » ; `bodyWeight` toujours écrit et lu absent = 0, `weighInReminder` facultatif omis au défaut ; aucune version, migration ni permission nouvelle (§ 2.3, 3.14, 4.1, 5.3, 7.1, 7.3, 15.7.1, 15.8.2, 15.9.2, 15.10.3, 15.11.1, 15.13) |
| D39 | Notification de mise à jour | une requête par jour, vers 18 h, à l'API publique des Releases GitHub (dernière Release non préversion), réseau requis, aucune donnée envoyée ; notification « Nouvelle version disponible » désactivée par défaut si Obtainium a installé l'app (source d'installation), activée sinon, modifiable dans les deux cas ; une seule fois par version, seule la dernière notifiée (versions intermédiaires sautées), la nouvelle remplace celle affichée ; titre et première ligne des notes, actions « Voir » (Obtainium s'il a installé l'app, sinon la page de la Release) et « Ignorer cette version » ; section « Rappels » de Paramètres renommée « Notifications » (rappel de pesée, compléments, nouvelle version) ; ligne « Version x · à jour / y disponible » avec « Vérifier maintenant », même notification coupée ; rien dans la variante preview | tâche WorkManager unique `update-check` à la prochaine 18 h locale, reprogrammée à chaque passage, contrainte réseau `CONNECTED` (`ACCESS_NETWORK_STATE` gardée), pas de nouvel essai avant le lendemain ; elle remplace la vérification au premier plan de D25 et ne tourne que si la notification est activée, quelle que soit la source d'installation (réglage coupé : aucune requête automatique, seul « Vérifier maintenant » contacte GitHub ; l'annonce de l'Accueil n'apparaît alors qu'après une vérification manuelle) ; tag `v1.0.n` → versionCode `1000 + n` (règle de `release.yml`) comparé au versionCode installé, autre forme de tag → nombres de la version (D25) ; réglage `notifyEnabled` et `notifiedVersion` dans `update_check.json` (propre à l'appareil, hors sauvegarde ; absent = défaut selon la source d'installation, relue à chaque démarrage) ; notifiée si plus récente que l'installée et que la dernière notifiée, et pas ignorée ; enregistrée comme notifiée seulement si elle a pu s'afficher ; canal `updates` (DEFAULT), un seul identifiant de notification ; titre « MuscleNote 1.0.43 disponible », texte = premier élément de liste des notes de version (§ 15.1) ; toucher = « Voir » ; « Ignorer cette version » = « Plus tard » de l'Accueil (même version masquée) ; notification retirée au démarrage d'une version au moins aussi récente ; « Notifications désactivées » rejoint la section « Notifications » ; « À propos » : « Version 1.0.42 · à jour », « Vérifier maintenant », « Télécharger la 1.0.43 » ou « Ouvrir Obtainium » ; `BuildConfig.UPDATE_NOTIFICATIONS` faux en preview ; aucun composant nouveau (§ 1, 3.17, 7.1, 15.8.2, 15.10.3, 15.13.3, 15.14) |
| D40 | Listes groupées (maquette « v2 listes groupées » du 15/11/2026) | appliquer les groupes, les titres de section et les espacements de la maquette à Journal, Paramètres et Nutrition tels qu'ils sont, sans coder les fonctions qu'elle dessine en plus (calendrier du mois et objectif de séances du Journal, protéines, stock des compléments et moyenne sur 7 jours de Nutrition), notées comme maquettes disponibles ; couleurs v2 pour toute l'app | jetons v2 (en clair fond #F2F2F0 et groupes blancs, en sombre groupes #1A1B1F, ligne pressée dans un groupe, filets plus nets), barre d'onglets sur la surface des groupes sans filet ; sur les trois écrans, étiquettes en capitales et groupes tonaux (rayon 20, sans bordure), marges de 16, 8 entre étiquette et groupe, 32 entre groupes ; Accueil, Progrès, fiches, séance et résumé inchangés hors couleurs ; ni donnée, ni texte, ni comportement nouveau ; non repris : icônes, interrupteur et section « Connexions » de Paramètres, case ronde de Nutrition, filtre du Journal (DESIGN.md § 2, 9.1, 10.1, 12, 13 ; § 15.6, 15.8.4, 15.10.1, 15.10.3) |
| D41 | Calendrier du Journal | en haut du Journal, le calendrier du mois de la maquette : semaine du lundi, flèches et glissement pour changer de mois, jour avec séance = rond plein, aujourd'hui = rond vide, résumé des séances du mois sous la grille ; toucher un jour ouvre une sheet « Journée » en listes groupées (séances → détail, pesée, compléments pris, calories et eau ; sections vides masquées ; jour sans rien = message court) ; historique des prises de compléments gardé désormais (date et nombre de doses par complément, champ facultatif, un enregistrement par complément et par jour, sans migration), affiché pour tous les jours à partir de cette version ; pas de réglage d'objectif : le Journal reprend la règle de la série de l'Accueil (validée à 3 séances), « 3/3 · validée » en vert, sinon gris, jamais rouge ; liste par semaine inchangée ; seules les données existantes ; chaque jour lu par TalkBack | composant `MonthCalendar` dans le groupe du héros du Journal (absent quand le Journal est vide), mois accessibles de la plus ancienne donnée au mois courant, 6 rangées réservées, rangées de 48 (cibles tactiles) ; jour d'une séance = jour local de son début (une séance commencée avant minuit reste sur la veille) ; `Supplement.intakes` `{ jour: doses }`, une dose par coche aujourd'hui, retirée en décochant, `taken` inchangé, ancien fichier relu avec un historique vide, une coche d'aujourd'hui antérieure à la mise à jour compte pour une dose ; poids du jour = règle de Progrès (pesée, sinon poids saisi au début d'une séance) ; sheet groupée (fond `background`, groupes `surfaceRaised`) ; compteur des semaines « n/3 » avec `WorkoutStats.VALIDATED_WEEK_SESSIONS` ; objectif réglable (Accueil et Journal ensemble) noté en piste (§ 3.6, 3.12, 4.1, 5.3, 15.6, 15.8.4, 15.15) |
| D42 | Protéines, stock des compléments et moyenne sur 7 jours (maquette `T2-Nutrition`) | objectif de protéines calculé à partir de la dernière pesée (grammes par kg, réglable de 1,2 à 2,2, 1,6 par défaut) ou valeur fixe saisie au pavé ; sans aucune pesée, valeur fixe uniquement ; ligne « Protéines » comme sur la maquette (« 112 g », « objectif 150 g »), en gris, puis en vert une fois l'objectif atteint ; **eau et protéines identiques, comme sur la maquette** : deux lignes, chacune avec son bouton + (ajout au pavé), le toucher de la ligne ouvre son détail ; protéines du jour dans la sheet « Journée », à côté des calories et de l'eau ; stock des compléments en doses, saisi au pavé, décompté à partir de l'historique des prises ; jours restants = stock / doses par jour, affichés comme sur la maquette, en rouge au seuil ou en dessous (7 jours par défaut, réglable par complément) ; « Racheté » ajoute des doses ; sans stock saisi, rien ne s'affiche ; doses par jour = réglage du complément ; « Moyenne sur 7 jours » (calories, eau, protéines) comme sur la maquette, sur les seuls jours avec des données, sans aujourd'hui ; champs facultatifs, sans migration, anciennes données relues telles quelles ; Progrès, export CSV et notification de stock bas en pistes | groupe du héros de la maquette : légende « Aujourd'hui · calories » et bouton + plein des calories, chiffre héros, état et piste inchangés, puis les lignes « Eau » et « Protéines » (valeur grise puis verte avec « objectif … atteint », jamais la couleur seule ; bouton + → saisie en « Ajouter » ; ligne → détail : aujourd'hui → « Total du jour », objectif, moyenne sur 7 jours, « Ajouter ») ; rangée de stats et boutons « Calories » / « Eau » retirés, « 1 sur 3 pris » à droite de l'étiquette « Compléments » ; protéines saisies comme les calories (grammes entiers, pas ±5 g) ; dernière pesée = dernière pesée connue du § 15.13.1 (pesée, sinon poids saisi au début d'une séance, séance en cours comprise) ; objectif = poids × facteur arrondi au gramme (demi vers le haut, calcul exact), non daté (il ne vaut que pour le jour même) ; « par kg » sans poids connu → la valeur fixe, sinon pas d'objectif ; doses par jour de 1 à 20, 1 par défaut, une coche enregistre ce nombre de doses dans l'historique (au lieu d'une) ; stock = doses comptées un jour donné moins les doses prises depuis, jamais sous 0 ; jours restants = partie entière, rouges dès qu'ils sont ≤ au seuil (« à 7 jours »), sans notification ; « Racheté » et « Stock bas » seulement quand le stock est suivi, « Ne plus suivre le stock » dans son formulaire ; moyenne de J − 7 à J − 1 par mesure, jours à 0 exclus, « — » pour une mesure sans jour, groupe masqué sans aucune donnée ; pas de lien « Tendances » ; section « Calories et eau » de la « Journée » renommée « Nutrition », protéines sans objectif (non daté), comptées pour les mois accessibles ; variantes de composants : `StudioIconButton` plein, `ListRowTrailing.ValueAction`, valeur avant la case de `ListRowTrailing.Check`, `DailySurface` redessinée (§ 3.12, 4.1, 5.3, 15.6, 15.10.1, 15.15.2, 15.15.4, 15.16) |
| D43 | Carte du corps et muscles travaillés (maquette `T2-Carte-corps`, SVG `design/claude-design/corps/`) | liste fixe des muscles (ids du SVG) avec nom courant, nom savant et rôle en une phrase ; muscles principaux et secondaires de chaque exercice intégré en donnée embarquée, jamais dans les fichiers de l'utilisateur ; exercices perso : choix des muscles dans leur formulaire, champ facultatif, sans migration ; fiche exercice : section « Muscles travaillés » en bas (petite carte + noms) ; sélecteur d'exercices : filtre « par muscle » avec la grande carte, toucher un muscle filtre la liste ; toucher un muscle (fiche ou filtre) ouvre la sheet « Muscle » ; écran de la séance en cours inchangé ; chaque muscle de la carte lu par TalkBack | 17 muscles, ordre et ids figés (§ 15.17.1) ; au moins un principal par exercice intégré, aucun muscle à la fois principal et secondaire (§ 15.17.2) ; le groupe musculaire reste (classement, cardio, filtre « Par groupe ») ; `Exercise.primaryMuscles` / `secondaryMuscles` facultatifs, omis vides, valeurs inconnues écartées à la lecture, un ancien fichier relu sans muscle ; formulaire perso : puces à choix multiple « Muscles principaux » et « Muscles secondaires », un muscle dans une seule des deux ; fiche : dernière section, carte de 150 dp et noms (principaux puis secondaires), section absente pour un exercice perso sans muscle ; sélecteur : contrôle segmenté « Par groupe » / « Par muscle », carte de 360 dp en tête de liste, plusieurs muscles possibles (union), principaux d'abord, puces des muscles choisis et « Effacer » ; sheet « Muscle » : carte d'une vue, nom, nom savant, rôle, exercices qui le travaillent (principal / secondaire), étape du sélecteur ou bottom sheet de la fiche ; toucher hors d'une zone → la plus proche à 12 dp ; composant nouveau `BodyMap` (formes du SVG pour Compose, toucher par muscle, un élément TalkBack par muscle) et jetons de la carte (§ 4.1, 6.1, 15.6, 15.9.1, 15.11.2, 15.17) |

**Détail de D15 (corrections)** :
| bizarrerie de l'app Expo | correction |
|---|---|
| rouge « calories > 105 % » inatteignable | couleur calculée sur le ratio non plafonné (§ 2.11) |
| éditeur : ajouter un exercice cardio crée une série 10 × 20 kg | série cardio 30 min / 0 / 0 / 0 (§ 2.9) |
| historique d'exercice du plus ancien au plus récent | liste du plus récent au plus ancien, graphique chronologique (§ 2.8) |
| séries non cochées comptées dans les stats | seules les séries validées comptent ; case de validation aussi dans l'éditeur ; anciennes séries migrées validées (§ 3.16, 5.3) |
| séance vide enregistrable | sans série validée, la séance est abandonnée après avertissement (§ 2.4, 3.3) |
| auto-finalisation 24 h seulement au démarrage | aussi à chaque retour au premier plan (§ 3.4) |
| « moy. 7 jours » = 7 derniers jours saisis | fenêtre calendaire de 7 jours (§ 3.10) |
| objectif appliqué rétroactivement | objectifs datés (§ 3.12) |
| `bottleSize` inutilisé | supprimé (§ 3.12, 5.3) |
| entrées `taken` jamais nettoyées | nettoyées à la suppression et au chargement (§ 3.12) |
| semaines partielles en `primary` alors que la doc dit « ambre » | ambre (`warning`) (§ 2.2) |
| retrait d'un exercice en séance sans confirmation | confirmation en ligne (§ 2.5) |
| éditeur quitté sans confirmation | dialogue si modifications non enregistrées (§ 2.9) |
| nom de séance non modifiable | renommage dans l'éditeur (§ 2.9) |
| (relevé en écrivant) « g par prise » affiché sans dosage | ligne masquée si dosage vide (§ 2.11) |
| (relevé en écrivant) temps en salle groupé par nom sensible à la casse | même clé que le pré-remplissage (§ 3.7) |

---

## 12. Questions restantes

Aucune. Les quatre questions de la version précédente ont été tranchées :

1. **Migration automatique (D4) contre nouvel `applicationId` (D5)** → option **(b)** :
   nouvel `applicationId` `com.musclenote.android` + transfert en un geste par la feuille de
   partage ; l'app Expo n'est pas modifiée et les deux apps cohabitent (D4, § 5.5). Les
   options écartées étaient (a) réutiliser `com.musclenote.app` avec la clé EAS pour lire
   `RKStorage` (§ 4.3) et (c) republier l'app Expo avec un bouton de transfert.
2. **minSdk / targetSdk** → minSdk 26, targetSdk = dernière API stable (D8, § 1).
3. **Anciennes séries non cochées** → toutes marquées validées à la migration (D21, § 5.3).
4. **Nom affiché** → « MuscleNote » suivi d'un « 2 » pendant la transition, « MuscleNote »
   depuis la bascule (D22, § 1).

---

## 13. État d'avancement

Vérification finale avant la bascule (11 novembre 2026) :
l'app native (`android-app/`, branche `native`) a été comparée à ce document section par
section ; tableaux mis à jour à la fin de la refonte « Studio épuré » (15 novembre 2026, § 13.6).
Les chemins ci-dessous sont relatifs à `android-app/app/src/main/java/com/musclenote/android/`.

### 13.1 Fait

Tout ce que décrivent les § 1 à 10 est implémenté, avec les remplacements de la refonte (§ 15.7 –
15.11, § 15.12, § 15.13). Les règles métier pures sont couvertes par 620 tests JVM (48 classes, `app/src/test/`),
plus 10 pour les notes de version (`release-notes/src/test/`), lancés par la CI avant la
construction de l'APK (à la bascule : 245 tests, 23 classes).

| § | sujet | où |
|---|---|---|
| 1 | identité (`com.musclenote.android`, « MuscleNote », portrait), SDK 26 / 37, socle technique ; permissions = la liste fermée de D19, vérifiée dans le manifeste fusionné de l'APK (`INTERNET` ajoutée ensuite par D25, § 13.6) | `app/build.gradle.kts`, `AndroidManifest.xml` |
| 2.1 | navigation, 4 onglets, entrées externes (notifications, fichier partagé) | `ui/MuscleNoteApp.kt`, `MainActivity.kt` |
| 2.2 | Accueil : fond flouté, séance en cours, carte de transfert, série hebdo, stats, temps en salle, séances avec édition / date / suppression | `ui/home/` |
| 2.3 – 2.6 | démarrer une séance, séance en cours (chronomètre, échauffement, glisser-déposer, fin ou abandon), carte d'exercice, ligne de série | `ui/workout/` |
| 2.7 – 2.9 | sélecteur d'exercices, historique d'un exercice (fiche), éditeur de séance (copie de travail, renommage, sortie confirmée) | `ui/workout/`, `ui/exercise/` |
| 2.10 | Progrès : poids corps et pesées, calories, eau, groupes musculaires | `ui/progress/`, `data/progress/ProgressStats.kt` |
| 2.11 | Nutrition : suivi du jour, compléments, rappels au `TimePicker` | `ui/nutrition/` |
| 2.12 | Paramètres : mes données, langue, thème, minuteur, notifications, sauvegarde, export CSV, import, rapport de plantage | `ui/settings/` |
| 2.13 | courbes au `Canvas` (🔄 D34, § 15.9) | `ui/designsystem/components/TrendCurve.kt` |
| 3 | règles métier (séance, 1RM, série hebdo, temps en salle, volume, archivage, Progrès, pesées, nutrition, préférences, séries validées) | `data/workout/`, `data/progress/`, `data/nutrition/`, `data/repository/` |
| 4 | modèle, un fichier JSON par document, écritures atomiques et différées, fichiers illisibles mis de côté | `data/model/`, `data/storage/` |
| 5 | sauvegarde v2, import v1 / v2 (validation, migration, normalisation), transfert depuis l'app Expo | `data/backup/`, `ui/settings/BackupViewModel.kt` |
| 6 | 42 exercices intégrés, 9 groupes, 11 préréglages | `data/catalog/`, `data/model/` |
| 7 | canaux, minuteur de repos (notification en cours, alarme exacte), rappels de compléments (alarmes exactes, resynchronisations) | `notifications/`, `data/repository/RestTimerRepository.kt` |
| 8 | export CSV | `data/export/CsvExport.kt`, `ui/settings/CsvExportTexts.kt` |
| 9 | thème : design system « Studio épuré » (DESIGN.md ; 🔄 D36 : l'ancien thème est retiré), Archivo embarquée, icônes Material Symbols Rounded, textes FR / EN | `ui/designsystem/`, `res/` |
| 10 | virgule décimale, jour local, dates impossibles, recherche, rapport de plantage | `data/util/`, `CrashHandler.kt`, `data/crash/` |

Textes : les 320 clés de `constants/i18n/fr.ts` (tag `expo-final`) sont reprises, sauf celles que le § 9.4
écarte (Excel) et trois sans objet dans l'app native : `notFound.*` (écran 404
d'expo-router ; une route inconnue n'existe pas) et `settings.export.noShare` (Android a
toujours une feuille de partage ; son absence donne l'erreur d'export générique). Toutes les
clés du § 9.5 existent en FR et EN (même jeu de clés et d'arguments, vérifié par un test).

### 13.2 Corrigé lors de la vérification

| écart relevé | correction |
|---|---|
| Dates et nombres formatés dans la première langue du système même quand l'app s'affiche dans une autre (système en allemand → textes en français, dates en allemand ; liste [de, en] → en-têtes CSV anglais avec séparateur français) | locale de formatage = celle des textes (§ 3.14) ; `data/util/AppLocales.kt` |
| Avant Android 13, notification construite dans un processus relancé par une alarme : texte dans la langue du système au lieu de la langue choisie dans l'app | copie de la langue dans les préférences locales (§ 3.14, § 9.4) |
| Splash et fond de fenêtre au thème du système, pas au choix Clair / Sombre de l'app (flash de l'autre thème au lancement) | Android 12+ : `setApplicationNightMode` (§ 9.1) ; `ui/designsystem/theme/SystemNightMode.kt` (`ui/theme/` jusqu'à l'étape 5b) |
| Texte en couleur de groupe peu lisible en thème clair (1,9 – 3,6:1) | décision D23 : nuance foncée en thème clair (§ 6.2, 9.1) |
| Deux préréglages « Legs » en anglais | décision D24 : « Legs (split) » (§ 6.3) |

### 13.3 Manque

Rien de fonctionnel. Les deux propositions à valider à l'œil de la bascule sont tombées avec la
refonte : la palette claire est celle de DESIGN.md (§ 2) et l'image de fond de l'Accueil a été
retirée (étape 3). Restent à vérifier **sur l'appareil** les points listés étape par étape au
§ 13.6, regroupés à la fin de l'étape 5b.

### 13.4 Diffère (interprétations et limites connues)

Écarts mineurs, assumés, par rapport à une lecture littérale du document :
- **Changement de thème** (Android 12+) : appliqué tout de suite à l'interface, puis
  l'activité est recréée une fois (comme pour un changement de langue) pour que le système
  prenne le nouveau thème ; avant Android 12, le splash suit le thème du système (pas d'API).
- **Langue des canaux de notification** : dans un processus relancé par une alarme avant
  Android 13, les noms de canaux restent dans la langue du système jusqu'à la prochaine
  ouverture de l'app (ils ne sont visibles que dans les réglages système).
- **Permission `POST_NOTIFICATIONS`** « seulement si elle peut encore être demandée »
  (§ 2.3, 7.1) : l'app la demande tant qu'elle n'est pas accordée ; après deux refus,
  Android ne montre plus la demande et répond « refusé » aussitôt, sans bloquer.
- **Notification « Repos en cours »** (jusqu'à la v1.0.6) : `setTimeoutAfter` recevait le temps
  restant, pour rester juste quand la notification était republiée dans une autre langue en
  cours de repos. 🔄 D31 : remplacée par la notification « Séance en cours » (§ 15.5), sans
  délai d'expiration (elle vit autant que la séance).
- **Partage vers l'app** (§ 5.4) : `ACTION_SEND` est déclaré pour `application/json` seulement
  (type utilisé par l'app Expo), pour ne pas apparaître dans tous les partages de texte ;
  `ACTION_VIEW` accepte `application/json` et `text/plain`.
- **`DUMP`** apparaît dans le manifeste fusionné : c'est la permission qui *protège* le
  récepteur d'`androidx.profileinstaller`, pas une permission demandée par l'app.

### 13.5 Bascule

L'app native a remplacé l'app Expo sur `main` : nom affiché « MuscleNote », app Expo retirée
du dépôt (conservée au tag `expo-final`), Releases GitHub `v1.0.<n>` publiées par
`.github/workflows/release.yml` à chaque push sur `main` touchant `android-app/`
(`versionCode` = 1000 + n, au-dessus des pré-releases `v0.1.*` de la transition, supprimées).

### 13.6 Après la bascule

- Vérification des mises à jour intégrée pour une app installée sans Obtainium (🔄 D25,
  § 3.17) ; elle a ajouté la permission `INTERNET`.
- Envoi d'un avis vers le serveur Discord du développeur (🔄 D26, § 14) : section « Ton avis » de
  Paramètres (`ui/settings/FeedbackCard.kt`), règles pures `data/feedback/FeedbackRules.kt`, dépôt
  `data/repository/FeedbackRepository.kt`, envoi `feedback/DiscordWebhookSender.kt` ; URL du webhook
  injectée au build par `release.yml` depuis le secret `DISCORD_WEBHOOK_URL`. Tests JVM
  `FeedbackRulesTest` (corps JSON, échappement, troncature, `allowed_mentions`, validation,
  délais, réponse 429) et `FeedbackRepositoryTest` (succès, échecs, délai entre deux envois). Les
  deux accès réseau de l'app passent par `INTERNET` ; l'avis est le seul envoi de données.
- Lot 1 des évolutions (§ 15, 🔄 D27 – D29) :
  - notes de version : module JVM `android-app/release-notes/` lancé par `release.yml`, tests
    `ReleaseNotesTest` (lecture de `git log`, fusions et commits de doc / CI écartés, texte) ;
    vérification des pull requests par `checks.yml` ;
  - sauvegarde automatique : règles `data/backup/AutoBackupRules.kt`, dépôt
    `data/repository/AutoBackupRepository.kt`, dossier et tâche WorkManager dans `backup/`, carte
    `ui/settings/AutoBackupCard.kt` ; tests `AutoBackupRulesTest` (noms, rotation des 14) et
    `AutoBackupRepositoryTest` (activation, fichier du jour, rotation, échecs, dossier perdu) ;
  - Health Connect : conversion `data/health/HealthRecords.kt`, dépôt
    `data/repository/HealthSyncRepository.kt`, SDK et écran de politique de confidentialité dans
    `health/`, carte `ui/settings/HealthConnectCard.kt` ; tests `HealthRecordsTest` (séance,
    poids, pesée, décalages horaires, empreintes), `HealthConnectRecordsTest` (enregistrements du
    SDK, permissions) et `HealthSyncRepositoryTest` (file, mises à jour, suppressions, échecs,
    autorisations partielles).
  - Restent à vérifier **sur l'appareil** : l'écran d'autorisation de Health Connect (Android 14+
    et app Health Connect avant), le sélecteur de dossier avec un fournisseur autre que le stockage
    interne, et le passage quotidien de la tâche.
- Correctif de Health Connect (🔄 D30, § 15.3) : un poids par jour (`BodyWeightRules.dailyWeights`,
  partagé avec Progrès), durée estimée (`WorkoutStats.countedMinutes`), envoi type par type et
  comptage des écritures confirmées ; tests de reproduction du signalement dans
  `HealthRecordsTest` et `HealthSyncRepositoryTest`.
- Notification de séance (🔄 D31, § 15.5) : règles pures `data/workout/WorkoutNotificationRules.kt`
  (prochaine série, contenu), `RestTimerRules.adjust` (±30 s), `WorkoutRepository.completeNextSet`,
  publication `notifications/WorkoutNotifier.kt` derrière `WorkoutNotifications`, actions dans
  `RestTimerReceiver` ; tests `WorkoutNotificationRulesTest` (prochaine série, textes FR et EN
  lus dans les ressources), `RestTimerRulesTest` et `RestTimerRepositoryTest` (±30 s, alerte avec
  « Valider la série »), `WorkoutRepositoryTest` (validation depuis la notification). À vérifier
  **sur l'appareil** : la promotion en Live Update (Android 16+, règles du fabricant), le relais
  de l'alerte et de son action sur une montre, les actions écran verrouillé.
- Refonte « Studio épuré » (DESIGN.md), branche `redesign` : design system dans
  `ui/designsystem/` (étape 1) ; **séance en cours** (🔄 D32, § 15.7, étape 2) : écran
  `ui/workout/WorkoutScreen.kt` au gabarit « en direct » (blocs `LiveExerciseBlock.kt`, pavé
  `WorkoutKeypad.kt`, bottom sheets `WorkoutSheets.kt`), posé dans `StudioTheme` par la navigation ;
  règles pures `data/workout/SessionInsights.kt` (séance de référence, « Préc. », suggestion, place
  du repos) et `data/workout/SetEntryRules.kt` (saisie au pavé) ; dépôt : `completeSet`,
  `updateExerciseSets`. Design system : case à cocher de `ListRow`, puce à action, « Préc. »
  touchable et série validée en saisie dans `SetRow`, « Supprimer la série » dans l'en-tête du
  pavé, champ texte `StudioTextField`, gabarit « en direct » à en-tête fixe et liste paresseuse.
  Tests `SetEntryRulesTest` (séparateur de la langue, décimales, longueurs, pas arrondis, champ et
  série suivants), `SessionInsightsTest` (référence, rang, recopie, suggestion et son application,
  repos) et `WorkoutRepositoryTest` (valider sans dévalider, séries d'un exercice). Les anciens
  `ExerciseCard`, `RestTimerBanner` et `WarmupCard` sont retirés. À vérifier **sur l'appareil** :
  saisie au pavé, défilement vers le champ en saisie, recopie de « Préc. », suggestion, repos et
  notification de séance.
- Refonte, **étape 3** (🔄 D33, § 15.8) sur `redesign` : navigation `ui/MuscleNoteApp.kt` (onglets
  `TopLevelDestination`, Paramètres `Routes.SETTINGS` poussé au-dessus de l'Accueil, mini-barre,
  `openWorkout()` qui revient à la séance si elle est dans la pile, fondus, retour prédictif
  `enableOnBackInvokedCallback`) ; Accueil `ui/home/HomeScreen.kt` et Journal
  `ui/journal/JournalScreen.kt` au design system, lignes et actions de séance partagées
  `ui/journal/SessionRows.kt` (« Modifier la date » en formulaire), horloges `ui/workout/LiveClock.kt` ;
  « Démarrer une séance » accepte un type (`Routes.startWorkout`) ; règles pures
  `data/home/HomeRules.kt` et `data/journal/JournalRules.kt`. Design system : appui long de
  `ListRow`, puces sur plusieurs lignes de `NextSessionCard`, mini-barre hors repos, champ numérique
  de `StudioTextField`, gabarit `HubLazyTemplate`. Tests `HomeRulesTest` (surface, types réguliers,
  fenêtre de 42 jours, ordre, égalités, jours locaux, semaine en cours) et `JournalRulesTest`
  (semaine du lundi au jour local, semaine à cheval sur deux années, dates illisibles, libellés,
  durée, volume, totaux). Logo : `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`,
  `ic_notification.xml`, splash dans `values-v31/themes.xml`, bandeau de la preview dans
  `app/src/preview/res/drawable/`. Retirés : `UpdateBanner`, `WorkoutDateDialog`, image `home-bg`,
  anciennes images de l'icône. À
  vérifier **sur l'appareil** : retour prédictif, notification touchée depuis chaque onglet,
  appui long, « Modifier la date », logo.
- Refonte, **étape 4** (🔄 D34, § 15.9) sur `redesign` : fiche exercice
  `ui/exercise/ExerciseFicheScreen.kt` (gabarit `FicheLazyTemplate`, textes communs
  `ui/exercise/ExerciseFormats.kt`) et Progrès `ui/progress/ProgressScreen.kt` (hub paresseux), pesée
  au pavé `ui/progress/WeighInSheet.kt` ; règles pures `data/progress/ProgressCurves.kt` (périodes,
  résumé, variation, réduction des points) et `ProgressStats` (métrique au poids du corps, meilleure
  série, tri des exercices, charge max en kg), géométrie de la courbe
  `ui/designsystem/components/CurveGeometry.kt`. Design system : `TrendCurve` (texte si vide, ligne
  d'objectif, variante mini), fin de ligne `ListRowTrailing.CurveValue` et `onClickLabel`, héros qui
  suit le doigt sans défiler, descriptions TalkBack du sélecteur de période, légende et état vide de
  `MetricSurface`, phrase facultative de `EmptyState`, `FicheLazyTemplate`. Tests `ProgressCurvesTest`
  (périodes, fin de mois, période vide, un point, valeurs égales, record, réduction), `CurveGeometryTest`
  (abscisses, point le plus proche, égalités, bords), `ProgressStatsTest` (tri, reps au poids du corps,
  lest, charge max, meilleure série) et `SetEntryRulesTest` (pas de 0,1 kg). Retirés :
  `ui/history/ExerciseHistoryScreen.kt`, `ui/components/LineChart.kt`, `ui/components/FormDialog.kt`,
  les onglets et cartes de Progrès, les couleurs du top 3. À vérifier **sur l'appareil** : glisser sur la
  courbe (sans faire défiler la fiche), défilement du chiffre au changement de période, TalkBack sur la
  courbe et les périodes, sélecteur de date de la pesée, suppression d'une pesée.
- Refonte, **étape 5a** (🔄 D35, § 15.10) sur `redesign` : Nutrition `ui/nutrition/NutritionScreen.kt`
  (gabarit hub, surface « Aujourd'hui », compléments) et ses sheets `ui/nutrition/NutritionSheets.kt`
  (calories et eau au pavé, objectifs, nouveau complément, complément, choix de l'heure) ; Paramètres
  `ui/settings/SettingsScreen.kt` (liste groupée, hub de second niveau) et ses formulaires
  (`PreferenceSheets.kt`, `AutoBackupSettings.kt`, `HealthConnectSettings.kt`, `UpdateRows.kt`,
  `CrashReportSettings.kt`, `FeedbackSheet.kt`), tous posés dans `StudioTheme` par la navigation ;
  thème sombre par défaut (`Preferences.DEFAULT`, lecture d'une sauvegarde, migration v1). Règles
  pures `data/nutrition/NutritionRules.kt` (état des calories, objectif d'eau, compléments pris,
  résumé des rappels, « Total du jour » de l'eau, validation des saisies) et saisie au pavé partagée
  avec la séance (`EntryFormat`, `NutritionEntry`, `SetEntryRules`). Design system : case séparée de
  `ListRow`, surface `DailySurface`, piste `ProgressTrack`, `minLines` / `enabled` de
  `StudioTextField`, icône `pill`. Tests `NutritionEntryTest` (entiers, litres, longueurs, pas,
  validation, nouveau total, objectifs), `NutritionRulesTest` (état des calories, total d'eau, objectif
  d'eau, compléments pris, résumé des rappels), `NutritionRepositoryTest` (total d'eau du jour),
  `BackupV2Test` / `BackupV1MigrationTest` (thème sombre par défaut, « system » gardé) et
  `RepositoryLoadTest` (sans préférences : sombre ; choix enregistré gardé). Retirés : les cartes de
  Nutrition (`DailyTrackingCards.kt`, `SupplementCard.kt`, `SupplementForm.kt`, `ReminderInputs.kt`)
  et de Paramètres (`AppSwitch`, `StatusBox`, `CardIcon`, `CardButton`, `MyDataCard`…). À vérifier
  **sur l'appareil** : saisies au pavé et passage à l'objectif (retour), case « pris aujourd'hui »,
  sheet d'un complément et choix de l'heure, permission des rappels, changement de langue et de thème
  depuis leur sheet (recréation de l'activité), sélecteur de dossier, autorisation Health Connect,
  envoi d'un avis au clavier dans la sheet, import d'un fichier partagé vers l'app.
- Refonte, **étape 5b** (🔄 D36, § 15.11) sur `redesign`, dernière avant la fusion : « Démarrer une
  séance » en bottom sheet `ui/workout/StartWorkoutSheet.kt` (formulaire du poids partagé avec
  l'éditeur, `BodyWeightForm.kt` ; type de séance `WorkoutNameFields.kt`), posée par
  `ui/MuscleNoteApp.kt` (routes `start-workout` et `exercise-picker` retirées) ; sélecteur
  d'exercices `ui/workout/ExercisePickerSheet.kt` ; éditeur `ui/workout/EditWorkoutScreen.kt` au
  gabarit fiche ; politique de confidentialité de Health Connect au design system. Ancien thème
  `ui/theme/`, Inter, `font_certs.xml`, dépendance `ui-text-google-fonts`, composants
  `InlineConfirm`, `MuscleGroupBadge`, `NotFoundScreen`, `ScreenHeader`, `DashedBorder`, barre de
  recherche, ancienne ligne de série, 52 icônes et 6 textes retirés (§ 15.11.5) ; `StudioTheme`
  posé une fois par `MainActivity`. Audit : plus aucune couleur, taille, police ou graisse en dur
  hors de `ui/designsystem/` (jetons `bodyStrong`, `captionStrong`, composants `StatusMessage`,
  `LabeledField`). Accessibilité (§ 15.11.4) : contrastes calculés sur les jetons (aplat `live` en
  clair foncé), rendus à 200 % de tous les écrans, règles ATF sur tous les aperçus. Notes de
  version : commits de tests écartés (§ 15.1, `ReleaseNotesTest`). Mise à jour depuis la v1.0.8
  vérifiée en release sur un émulateur Android 16 (§ 15.11.8) : données identiques, séance en cours
  et réglages gardés. README : captures du nouveau design (`docs/captures/`, même émulateur).
  Restent à vérifier **sur l'appareil**, pour toute la refonte : saisie au pavé et défilement vers
  le champ (séance, éditeur), « Démarrer » depuis l'Accueil, le Journal et Progrès (permission des
  notifications), sélecteur (ajouts multiples, appui long sur un exercice perso, clavier), retour
  dans une sheet à étapes, glisser sur une courbe, TalkBack et grand texte (200 %) sur un vrai
  téléphone, notification de séance et Live Update (Android 16+), sélecteur de dossier, Health
  Connect, import d'un fichier partagé.
- **Résumé de fin de séance** (🔄 D37, § 15.12), branche `feat/resume-seance` : écran
  `ui/summary/WorkoutSummaryScreen.kt` (gabarit `FicheLazyTemplate`, route `workout-summary/{workoutId}`),
  ouvert à la fin de la séance et depuis les lignes de séance (`ui/journal/SessionRows.kt`) ; règles
  pures `data/workout/WorkoutSummaryRules.kt` (séance précédente du même programme, variation du
  volume, records battus). Aucun composant nouveau. Tests `WorkoutSummaryRulesTest` (première séance
  du programme, autre programme plus récent ignoré, égalité « identique », hausse et baisse, séances
  plus récentes ignorées ; records : égalité, première fois, charge, 1RM seul, reps au poids du corps,
  premier lest, cardio, séries non validées). À vérifier **sur l'appareil** : « Terminer la séance »
  → résumé → « Terminer » → Accueil, retour système depuis le résumé, notification retirée, ⋮ et
  suppression depuis le résumé d'une séance du Journal.
- **Pesée facultative et rappel de pesée** (🔄 D38, § 15.13), branche `feat/pesee-rappel` : règles
  pures `data/nutrition/BodyWeightRules.kt` (poids du jour, dernière pesée connue, poids d'une séance,
  étape du poids) et `data/nutrition/WeighInReminderRules.kt` (réglage, décision à la sonnerie, résumé
  des jours), prochaine occurrence par jour de la semaine dans `ReminderSchedule` ; réglage
  `Preferences.weighInReminder` ; alarme `notifications/WeighInReminderScheduler.kt` derrière
  `WeighInReminders`, récepteur `WeighInReminderReceiver`, resynchronisation par
  `ReminderResyncReceiver`, toucher → `MainActivity.ACTION_OPEN_WEIGH_IN` (Progrès, « Nouvelle
  pesée ») ; sheet `ui/settings/ReminderSettings.kt`, section « Rappels » de Paramètres (la sheet d'un
  complément, `SupplementSheetHost`, est partagée avec Nutrition), bouton de la surface de Progrès ;
  « Passer » et l'étape sans champ dans `ui/workout/StartWorkoutSheet.kt` ; poids affiché d'une
  séance `ui/workout/SessionWeight.kt`. `ExerciseSession` perd le poids de corps, que rien ne lisait.
  Design system : icône `notifications`, second bouton de `MetricSurface` (`SurfaceAction`), puces de
  filtre à choix multiple. Tests `BodyWeightRulesTest`, `WeighInReminderRulesTest`,
  `ReminderScheduleTest`, `CsvExportTest`, `HealthRecordsTest`, `BackupV2Test`, `RepositoryLoadTest`. À vérifier **sur l'appareil** : sonnerie du rappel, toucher →
  « Nouvelle pesée », silence un jour déjà pesé, reprogrammation après un redémarrage, « Passer » et
  l'étape sans champ.
- **Notification de mise à jour** (🔄 D39, § 15.14), branche `feat/notif-maj` : règles pures
  `data/update/UpdateRules.kt` (`versionCode` d'un tag `v1.0.n`, comparaison, décision de notifier,
  réglage par défaut, Obtainium à ouvrir, prochaine 18:00, première ligne des notes) ; dépôt
  `data/repository/UpdateRepository.kt` (vérification quotidienne `dailyCheck`, « Vérifier
  maintenant » `checkNow` sans notification, réglage, version ignorée, retrait au démarrage) derrière
  `UpdateNotifications` et `UpdateCheckScheduler` ; tâche WorkManager `update/UpdateCheckWorker.kt`
  (unique `update-check`, ponctuelle, reprogrammée par elle-même, contrainte réseau), notification
  `notifications/UpdateNotifier.kt` (canal `updates`, id unique) et « Ignorer cette version »
  `notifications/UpdateNotificationReceiver.kt` ; source d'installation et « Voir »
  `update/InstallSource.kt` (`<queries>` Obtainium) ; `ACCESS_NETWORK_STATE` rendue ;
  `BuildConfig.UPDATE_NOTIFICATIONS` faux en preview. La vérification au retour au premier plan est
  retirée. Paramètres : section « Notifications » (`settings_section_notifications`, « Notifications
  désactivées » déplacée, ligne à cocher `updateNotifyRow`), « À propos » refait (`updateRows`) ;
  `SettingsRow` gagne la case. Tests `UpdateRulesTest` (tag et `versionCode`, autre schéma, réponses
  valides et invalides, notes, notifier ou non, intermédiaire sautée, remplacement, retrait, source
  d'installation, réglage par défaut, 18:00 et changements d'heure) et `UpdateRepositoryTest` (une
  notification par version, échec sans rien enregistrer, version ignorée, intermédiaire sautée,
  notification remplacée, affichage impossible retenté, « Vérifier maintenant » sans notification,
  aucune requête automatique réglage coupé — Obtainium ou non —, programmation au démarrage et au
  changement du réglage, preview, retrait au démarrage). À vérifier **sur l'appareil** : passage de
  la tâche vers 18 h (et après un redémarrage), attente du réseau, « Voir » vers Obtainium et vers la
  page de la Release, « Ignorer cette version », notification retirée après la mise à jour.
- **Listes groupées** (🔄 D40, DESIGN.md « v2 listes groupées »), branche `feat/listes-groupees` :
  jetons v2 dans `ui/designsystem/theme/StudioColors.kt` (nouveau `surfaceGroupPressed`) et
  `StudioDimens.kt` (`screenGrouped`, `group`), fond de fenêtre et du splash clairs `#F2F2F0` ;
  composant `ui/designsystem/components/ListGroup.kt` (`ListGroup`, `ListGroupSegment`,
  `GroupPosition`, `LocalInListGroup`), `ListRow` dans un groupe (retrait, filet en retrait, 56 sans
  sous-titre, ligne pressée), étiquette `SectionHeader(grouped = true)`, `HighlightSurface(grouped)`
  pour `DailySurface`, barre d'onglets sans filet ; `HubTemplate` / `HubLazyTemplate(grouped = true)`
  et `HubSections.group` / `groupBlock` ; écrans `ui/journal/JournalScreen.kt`,
  `ui/settings/SettingsScreen.kt` (`SettingsSection`), `ui/nutrition/NutritionScreen.kt`. Aperçus :
  groupes, étiquettes, rangée de stats groupée, planche Composants 1 v2, hub du Journal groupé ; test
  JVM `GroupPositionTest`. Export de la maquette mis à jour (`design/claude-design/`). À vérifier
  **sur l'appareil** : thème clair (fond gris, groupes blancs, barre d'onglets), ligne pressée dans un
  groupe, défilement du Journal.
- **Calendrier du Journal** (🔄 D41, § 15.15), branche `feat/calendrier` : règles pures
  `data/journal/CalendarRules.kt` (grille du mois, jours à séance, mois accessibles, journée,
  semaines « n/3 ») ; historique des prises `Supplement.intakes` (`NutritionRules.recordIntake`,
  écrit par `NutritionRepository.toggleTaken` sans resynchroniser les alarmes, lu par
  `BackupNormalizer`) ; composant `ui/designsystem/components/MonthCalendar.kt` (une page de
  `HorizontalPager` par mois) et icône `ds_ic_chevron_left` ; `SheetSurface` / `StudioBottomSheet
  (grouped = true)` ; `SectionHeader(trailingDescription)` ; écran `ui/journal/JournalScreen.kt`
  (calendrier en `highlight`, `JournalCalendar.kt`), sheet `ui/journal/DaySheet.kt` (`SessionRow
  (timeOfDay)`) ; `DateFormats.monthTitle` / `monthName` / `fullDate(day, today)` /
  `fullDateInSentence`. Tests JVM `CalendarRulesTest`, `SupplementIntakesTest`,
  `NutritionRepositoryTest`, `BackupV2Test`. Aperçus : `MonthCalendarPreview`, hub du Journal avec
  son calendrier, sheet « Journée » (journée pleine, aujourd'hui, jour vide), écran Journal ;
  historique des prises dans `PreviewData`. À vérifier **sur l'appareil** : glissement entre les
  mois dans la liste, ouverture de la sheet et passage au résumé, TalkBack sur les jours, coche d'un
  complément puis sa journée.
- **Protéines, stock des compléments et moyenne sur 7 jours** (🔄 D42, § 15.16), branche
  `feat/proteines-stock` : modèle `ProteinGoal` / `ProteinGoalMode`, `DailyTracker.proteinLog` /
  `proteinGoal`, `Supplement.dosesPerDay` / `stock` (`SupplementStock`) / `lowStockDays` (facultatifs,
  omis au défaut, lus par `BackupNormalizer`) ; règles pures `data/nutrition/ProteinRules.kt` (objectif
  par kg exact, valeur fixe, dernière pesée connue, formulaire), `data/nutrition/StockRules.kt` (stock
  restant, rachat, jours restants, stock bas, doses par jour), `NutritionRules` (protéines du jour,
  `recordIntake(doses)`, `weekAverages`, formats du pavé) ; `NutritionRepository` (protéines, objectif,
  stock et doses sans resynchroniser les alarmes) ; `CalendarRules.DayJournal.proteinGrams`. Écran
  `ui/nutrition/NutritionScreen.kt` (groupe « Aujourd'hui » de la maquette, jours de stock, moyenne
  sur 7 jours) et `NutritionSheets.kt` (`MeasureSheet` : saisie, détail, objectif ; objectif de
  protéines ; sheet d'un complément par étapes : doses par jour, stock, « Ajouter du stock », stock
  bas) ; sheet « Journée » (`ui/journal/DaySheet.kt`, section « Nutrition »). Design system :
  `StudioIconButton(filled)`, `ListRowTrailing.ValueAction`, valeur avant la case de
  `ListRowTrailing.Check`, `DailySurface` redessinée, `highlightedHelp`. Tests JVM `ProteinRulesTest`,
  `SupplementStockTest`, `NutritionAveragesTest`, `BackupV2Test`, `CalendarRulesTest`,
  `NutritionRepositoryTest`. Aperçus : onglet Nutrition (stocks et protéines dans `PreviewData`),
  groupe « Aujourd'hui », saisie et objectif des protéines (avec et sans pesée), détail de l'eau et des
  protéines, sheet d'un complément (stock suivi, stock bas, non suivi), « Ajouter du stock »,
  « Stock », `IconButtonFilledPreview`, `ListRowCheckPreview`, `DailySurfacePreview`. À vérifier **sur
  l'appareil** : boutons + et toucher des lignes Eau et Protéines, retour entre détail, saisie et
  objectif, coche d'un complément à 2 doses par jour puis son stock, « Racheté » et le seuil ouvert
  depuis lui, TalkBack sur les jours de stock et sur « 1 sur 3 pris », texte agrandi dans le groupe
  « Aujourd'hui ».
- **Carte du corps et muscles travaillés** (🔄 D43, § 15.17), branche `feat/carte-corps` : spécifié, en
  cours d'implémentation.

---

## 14. Envoyer un avis

🔄 D26. Un formulaire de Paramètres envoie une idée, un bug ou une remarque sur le serveur
Discord du développeur, par un webhook. C'est le **seul envoi de données** de l'app, et il n'a
lieu que lorsque l'utilisateur appuie sur « Envoyer » : pas d'envoi automatique, pas de file
d'attente, pas de nouvel essai en arrière-plan. L'autre accès réseau de l'app, la vérification des
mises à jour (§ 3.17, 🔄 D39 § 15.14), lit la dernière Release sur l'API publique de GitHub, une fois
par jour en arrière-plan ou sur « Vérifier maintenant », **sans rien envoyer** de l'app.

### 14.1 Formulaire (Paramètres, ligne « Envoyer un avis »)

🔄 D35 — depuis la refonte de Paramètres (§ 15.10.3), le formulaire est une **bottom sheet**
ouverte par la ligne « Envoyer un avis » / « Une idée, un bug ? » de la section « À propos »,
**affichée seulement si l'URL du webhook a été fournie au build** (§ 14.5) ; sinon la ligne est
absente. Fait des composants du design system (DESIGN.md), de haut en bas :

- **Titre** « Ton avis » et aide « Une idée, un bug ? Envoie-le directement au développeur. ».
- **Type** : puces de filtre « Idée » (choisie par défaut), « Bug », « Autre » ; choix unique.
- **Message** : `StudioTextField` multiligne (4 lignes visibles, grandit jusqu'à 8 puis défile),
  invite « Décris ton idée ou le problème… », majuscule en début de phrase. **1 000 caractères au
  plus** : la saisie s'arrête à la limite et un collage trop long est tronqué. Caractère = point de
  code Unicode (un emoji simple compte pour 1 et n'est jamais coupé en deux). Compteur
  « {n} / 1 000 » aligné à droite sous le champ, en légende (`textSecondary`, puis `textPrimary`
  appuyé à 1 000 : pas de rouge, réservé à la baisse), nombres formatés dans la locale de l'app
  (§ 3.14).
- **Pseudo** (facultatif) : `StudioTextField` d'une ligne, invite « Pseudo (facultatif) »,
  32 caractères au plus, touche « Terminé ».
- **Bouton principal « Envoyer »**, actif seulement si le message contient autre chose que des
  espaces, qu'aucun envoi n'est en cours et qu'aucun délai ne court (§ 14.3). Pendant l'envoi :
  « Envoi… », champs désactivés (texte `textTertiary`), puces sans effet. Pendant un délai : bouton
  désactivé et, dessous, « Prochain envoi possible dans {délai} », décompté à la seconde (« 42 s » ;
  à partir d'une minute, en minutes arrondies au-dessus : « 3 min »).
- **Informations jointes**, en légende sous le bouton : « Joint automatiquement : MuscleNote
  {version} · Android {version} · {modèle} · langue {langue}. Rien d'autre : aucune donnée
  d'entraînement. », avec exactement les valeurs envoyées (§ 14.2).
- **Résultat** (§ 14.4) en phrase sous le formulaire (`StatusMessage` : `textPrimary` 600, sans
  couleur) ; il disparaît dès que le formulaire est modifié.
- **Brouillon** gardé en mémoire tant que l'app tourne : fermer la sheet, changer d'onglet, de
  langue ou de thème ne le perd pas ; l'envoi continue si la sheet est fermée. Il n'est pas écrit
  sur disque.

### 14.2 Requête

- `POST` HTTPS vers l'URL du webhook ; en-têtes `Content-Type: application/json; charset=utf-8`
  et `User-Agent: MuscleNote-Android/{version}` ; délais d'attente **5 s** (connexion) et
  **10 s** (lecture) ; redirections non suivies. `HttpURLConnection` sur `Dispatchers.IO`, sans
  bibliothèque ajoutée.
- Corps : un message Discord avec un seul embed, construit avec kotlinx.serialization (guillemets,
  barres obliques inverses, retours à la ligne et caractères de contrôle correctement échappés) :
  ```json
  {
    "username": "MuscleNote",
    "allowed_mentions": { "parse": [] },
    "embeds": [{
      "title": "💡 Idée",
      "description": "Pouvoir réordonner les compléments.",
      "color": 16096779,
      "fields": [
        { "name": "Pseudo", "value": "Théo", "inline": true },
        { "name": "Version", "value": "1.0.42 (1042)", "inline": true },
        { "name": "Android", "value": "15 (API 35)", "inline": true },
        { "name": "Appareil", "value": "Google Pixel 8", "inline": true },
        { "name": "Langue", "value": "fr-FR", "inline": true }
      ]
    }]
  }
  ```
- **Type** → titre et couleur de l'embed : Idée « 💡 Idée », ambre `#F59E0B` (16096779) ; Bug
  « 🐞 Bug », rouge `#EF4444` (15680580) ; Autre « 💬 Autre », bleu `#3B82F6` (3900150). Les
  libellés de l'embed sont toujours en français (lus par le développeur), quelle que soit la
  langue de l'app.
- **Message** : espaces de début et de fin retirés, puis tronqué à 1 000 caractères (déjà
  garanti par la saisie ; la troncature protège des limites de Discord).
- **Pseudo** : retours à la ligne remplacés par des espaces, espaces de début et de fin retirés,
  tronqué à 32 caractères ; vide → « anonyme » (Discord refuse un champ vide).
- **`allowed_mentions.parse = []`** : aucune mention (`@everyone`, `@here`, rôle, membre) ne peut
  notifier qui que ce soit, même écrite dans le message ou le pseudo.
- **Informations jointes** (mêmes sources que le rapport de plantage, § 10.9) : version =
  `versionName (versionCode)` ; Android = `Build.VERSION.RELEASE (API SDK_INT)` ; appareil =
  `Build.MANUFACTURER Build.MODEL` ; langue = locale des textes de l'app (§ 3.14), ex. `fr-FR`.
- **Rien d'autre** : ni séance, ni poids, ni nutrition, ni identifiant d'appareil. Comme pour
  toute requête, Discord voit l'adresse IP du téléphone.

### 14.3 Anti-abus (côté app)

- **Un envoi par minute au plus** : après un envoi réussi, le bouton reste désactivé 60 s.
- **Réponse 429** (trop de requêtes) : délai lu dans `retry_after` du corps JSON (secondes,
  décimales possibles), à défaut dans l'en-tête `Retry-After`, à défaut 60 s ; arrondi à la
  seconde supérieure, plafonné à 1 h ; aucun envoi avant sa fin.
- Les deux instants (`lastSentAt`, `blockedUntil`) sont enregistrés dans `feedback.json` (§ 4.2,
  hors sauvegarde) : fermer l'app ne remet pas le délai à zéro. Si l'horloge du téléphone recule,
  l'attente ne dépasse jamais 60 s après un envoi, ni 1 h après un 429.
- Un échec réseau ou une erreur autre que 429 n'impose aucun délai : on peut réessayer tout de
  suite.
- Ces limites évitent les doubles envois et l'emballement ; elles ne protègent pas le webhook
  lui-même (§ 14.5).

### 14.4 Résultats

| issue | message | formulaire |
|---|---|---|
| réponse 2xx | succès : « Merci, ton avis a été envoyé ! » (+ retour haptique) | vidé : type « Idée », message et pseudo vides |
| pas de réseau, délai dépassé, erreur TLS | « Envoi impossible : vérifie ta connexion. Ton message est conservé. » | conservé |
| 429 | « Trop d'envois pour le moment. Ton message est conservé. » + délai sous le bouton (§ 14.3) | conservé |
| autre 4xx / 5xx | « L'envoi a échoué (erreur {code}). Ton message est conservé ; réessaie plus tard. » | conservé |

### 14.5 URL du webhook

- Elle vient du **secret GitHub `DISCORD_WEBHOOK_URL`**, passé en variable d'environnement par
  `release.yml` à la seule étape de build de l'APK release, et injecté par
  `app/build.gradle.kts` dans `BuildConfig.FEEDBACK_WEBHOOK_URL`. Elle n'est jamais dans le dépôt.
- Absente (build local, build debug des tests) → chaîne vide → section masquée. Présente mais
  différente de `https://discord.com/api/webhooks/<id>/<jeton>` (ou `discordapp.com`,
  `canary.discord.com`, `ptb.discord.com` ; espaces autour ignorés) → le build échoue, sans
  afficher l'URL.
- **Limite assumée** : l'URL est incluse dans l'APK, qui est public. Quiconque le décompile peut
  la lire et poster dans le salon sans passer par les limites de l'app (§ 14.3). Parades : un
  webhook dans un **salon privé** que seul le développeur voit ; en cas d'abus, supprimer le
  webhook sur Discord, en créer un autre, mettre à jour le secret et publier une Release (les
  versions précédentes obtiennent alors une erreur 401 / 404, donc le message d'échec).

### 14.6 Textes (FR et EN)

| clé | FR | EN |
|---|---|---|
| `feedback.title` (titre de la sheet) | Ton avis | Your feedback |
| `feedback.desc` | Une idée, un bug ? Envoie-le directement au développeur. | An idea, a bug? Send it straight to the developer. |
| `feedback.type.idea` | Idée | Idea |
| `feedback.type.bug` | Bug | Bug |
| `feedback.type.other` | Autre | Other |
| `feedback.message.placeholder` | Décris ton idée ou le problème… | Describe your idea or the problem… |
| `feedback.counter` | {{count}} / {{max}} | {{count}} / {{max}} |
| `feedback.name.placeholder` | Pseudo (facultatif) | Nickname (optional) |
| `feedback.send` | Envoyer | Send |
| `feedback.wait` | Prochain envoi possible dans {{delay}} | You can send again in {{delay}} |
| `feedback.attached` | Joint automatiquement : MuscleNote {{app}} · Android {{android}} · {{device}} · langue {{language}}. Rien d'autre : aucune donnée d'entraînement. | Attached automatically: MuscleNote {{app}} · Android {{android}} · {{device}} · language {{language}}. Nothing else: no workout data. |
| `feedback.success` | Merci, ton avis a été envoyé ! | Thanks, your feedback has been sent! |
| `feedback.error.network` | Envoi impossible : vérifie ta connexion. Ton message est conservé. | Couldn't send: check your connection. Your message is kept. |
| `feedback.error.rateLimited` | Trop d'envois pour le moment. Ton message est conservé. | Too many messages right now. Your message is kept. |
| `feedback.error.server` | L'envoi a échoué (erreur {{code}}). Ton message est conservé ; réessaie plus tard. | Sending failed (error {{code}}). Your message is kept; try again later. |

Le délai (`{{delay}}`) réutilise les formats du minuteur (`settings.restTimer.seconds` /
`settings.restTimer.minutes` : « 42 s », « 3 min »). La ligne de Paramètres et l'état « Envoi… »
ont leurs textes au § 15.10.7 (`feedback_row_title`, `feedback_row_subtitle`, `feedback_sending`) ;
l'ancien titre de section « TON AVIS » est retiré.

---

## 15. Évolutions

Fonctionnalités ajoutées après la bascule, par lots ; chacune a sa décision au § 11. Le **lot 1**
(notes de version, sauvegarde automatique, Health Connect) ne change pas le design : il réutilise
les cartes, interrupteurs, boutons et encarts existants de Paramètres (§ 2.12, § 2.11, § 9).
**Correctif de Health Connect** après la v1.0.5 (🔄 D30) : un poids par jour, durée des séances
courtes ou sans durée, comptage de ce que Health Connect confirme (§ 15.3). **Notification de
séance** avec actions (🔄 D31, § 15.5) : elle remplace la notification de repos, sans écran ni
réglage nouveau. **Refonte « Studio épuré »** (DESIGN.md), écran par écran : la séance en cours
(🔄 D32, § 15.7) passe au gabarit « en direct », avec la colonne « Préc. », la suggestion de charge
et un pavé numérique intégré, sans donnée nouvelle ; puis la navigation, l'Accueil, le Journal et
le logo (🔄 D33, § 15.8), sans donnée nouvelle non plus ; puis la fiche exercice et Progrès (🔄 D34,
§ 15.9), toujours sans donnée nouvelle ; puis Nutrition et Paramètres (🔄 D35, § 15.10), dont seul le
thème par défaut change (sombre) ; enfin les derniers écrans (démarrer, sélecteur, éditeur) et les
finitions — ancien thème retiré, accessibilité, mise à jour vérifiée (🔄 D36, § 15.11). **Résumé de
fin de séance** (🔄 D37, § 15.12) : la première nouveauté après la refonte, calculée sur les séances
existantes, sans donnée nouvelle ni composant nouveau. **Pesée facultative et rappel de pesée**
(🔄 D38, § 15.13) : « Passer » au début de séance, section « Rappels » de Paramètres, un réglage
nouveau dans les préférences, sans changement de version du format. **Notification de mise à jour**
(🔄 D39, § 15.14) : la vérification des mises à jour passe en tâche quotidienne vers 18 h et prévient
d'une nouvelle version par une notification ; « Rappels » devient « Notifications » ; aucun composant
nouveau, rien dans la sauvegarde. **Listes groupées** (🔄 D40) : la maquette « v2 listes groupées »
appliquée à Journal, Paramètres et Nutrition (groupes, étiquettes, espacements) et ses couleurs à
toute l'app ; un composant nouveau (`ListGroup`), aucune donnée ni fonction nouvelle. **Calendrier
du Journal** (🔄 D41, § 15.15) : le mois en tête du Journal, une sheet « Journée » qui rassemble les
données d'un jour, les semaines validées comme sur l'Accueil ; l'historique des prises de compléments
s'ajoute au format (champ facultatif) ; un composant nouveau (`MonthCalendar`). **Protéines, stock des
compléments et moyenne sur 7 jours** (🔄 D42, § 15.16) : trois fonctions de la maquette Nutrition,
avec des champs facultatifs dans le tracker et les compléments, le groupe « Aujourd'hui » de la
maquette (lignes « Eau » et « Protéines ») et les protéines dans la sheet « Journée » ; aucun
composant nouveau, des variantes (`StudioIconButton` plein, valeurs de fin de `ListRow`,
`DailySurface`). **Carte du corps et muscles travaillés** (🔄 D43, § 15.17) : les muscles principaux et
secondaires de chaque exercice (embarqués pour le catalogue, facultatifs pour un exercice perso), la
section « Muscles travaillés » de la fiche exercice, le filtre « Par muscle » du sélecteur et la sheet
« Muscle » ; deux champs facultatifs sur les exercices perso ; un composant nouveau (`BodyMap`).

### 15.1 Notes de version (🔄 D27)

**Texte de chaque Release** publiée par `release.yml` (§ 13.5) :

```markdown
MuscleNote 1.0.12 (versionCode 1012), commit 1a2b3c4.

## Changements
- Sauvegarde automatique : choix du dossier et sauvegarde quotidienne
- Health Connect : envoi des séances et du poids

Tous les commits : https://github.com/Bouss45/MuscleNote/compare/v1.0.11...v1.0.12
```

- **Release précédente** : la dernière Release (ni brouillon ni pré-release) au moment du run, lue
  avant de créer la nouvelle. Les commits examinés sont ceux de `<son tag>..<commit construit>`,
  listés du plus ancien au plus récent. Le dépôt est récupéré avec tout son historique
  (`fetch-depth: 0`).
- **Commits retenus** : ni les commits de fusion (plus d'un parent), ni ceux qui ne touchent que la
  documentation, la CI ou les tests. Documentation = tout fichier `*.md` (`SPEC.md`, `README.md`,
  `CLAUDE.md`…) ; CI = tout fichier sous `.github/` ; tests (🔄 D36) = tout fichier d'un dossier de
  tests Gradle, `src/test/`, `src/androidTest/` et leurs variantes (`src/testDebug/`…), où que soit
  le module (rien n'en entre dans l'APK ; les aperçus de `src/debug/` ne sont pas des tests). Un
  commit qui touche aussi autre chose est retenu ; un commit sans fichier modifié ne l'est pas.
- **Une ligne par commit** : son titre (première ligne du message), tel quel. Au plus 50 lignes ;
  au-delà, une dernière ligne « … et N autres changements ».
- **Aucun commit retenu** (seule la CI a changé, par exemple) : « Aucun changement dans l'app :
  nouvelle compilation. » à la place de la liste.
- **Pas de Release précédente**, ou tag introuvable dans l'historique : « Liste des changements
  indisponible. », sans lien de comparaison.
- **Relance d'un run** dont la Release existe déjà : l'APK est remplacé, le texte ne change pas.
- **Calcul** : la sélection et la mise en forme sont du Kotlin pur, dans un petit module JVM
  `android-app/release-notes/` (sans Android), couvert par des tests JVM lancés à l'étape « Tests
  unitaires ». Le workflow lui passe la sortie de `git log` (parents, titre et fichiers de chaque
  commit) et publie le Markdown produit.
- **Règle d'écriture** (`CLAUDE.md`) : le titre d'un commit décrit le changement **pour
  l'utilisateur, en français** (« Sauvegarde automatique : choix du dossier », pas « Refactor du
  BackupViewModel ») ; il finit tel quel dans les notes de version.
- **Vérification des pull requests** : nouveau workflow `checks.yml`, sur chaque pull request vers
  `main` touchant `android-app/**` ou ce workflow : tests JVM (app et notes de version) et APK
  release non signé (R8 compris), sans secret ni Release. Une branche est validée avant sa fusion ;
  seule la fusion publie une Release.

### 15.2 Sauvegarde automatique (🔄 D28)

**Carte « Sauvegarde automatique »**, dans la section « SAUVEGARDE » de Paramètres, après
« Importer des données » (§ 2.12, point 3) ; style de la carte du minuteur (icône ronde `folder`
en `primary`, titre, description) avec, à droite, l'interrupteur des rappels (§ 2.11).

- Description : « Chaque jour, une sauvegarde JSON dans le dossier de ton choix. Les 14 plus
  récentes sont gardées. »
- **Interrupteur** :
  - activé sans dossier retenu → sélecteur de dossier du système (`ACTION_OPEN_DOCUMENT_TREE`) ;
    dossier choisi → sauvegarde automatique activée et **première sauvegarde tout de suite** ;
    sélecteur annulé → reste désactivée ;
  - activé avec un dossier déjà retenu → réactivée sur ce dossier, première sauvegarde tout de
    suite (dossier inaccessible → message ci-dessous) ;
  - désactivé → plus de sauvegarde planifiée ; le dossier reste retenu et les fichiers déjà écrits
    ne sont pas touchés.
- **Activée**, sous l'en-tête :
  - « Dossier : {nom} » + bouton [Changer] (même sélecteur ; l'autorisation de l'ancien dossier
    est rendue) ;
  - « Dernière sauvegarde : {date heure} » ou « Aucune sauvegarde pour l'instant »
    (`mutedForeground`) ;
  - après un échec, en `destructive` : dossier inaccessible → « Le dossier de sauvegarde n'est plus
    accessible (supprimé, renommé ou autorisation retirée). Choisis-le à nouveau. » + bouton
    [Choisir un dossier] ; autre erreur → « Échec de la sauvegarde du {date heure} : {erreur} » ;
  - bouton [Sauvegarder maintenant] : indicateur pendant l'écriture, désactivé pendant une autre
    sauvegarde ; succès → retour haptique et « Dernière sauvegarde » à jour.

**Dossier** : choisi dans le sélecteur du système ; l'app garde une **autorisation persistante** de
lecture et d'écriture (`takePersistableUriPermission`), sans permission de stockage. Android ne
propose pas la racine du stockage, `Download` ni `Android/data` : on choisit ou crée un
sous-dossier (par ex. `Documents/MuscleNote`). Un dossier synchronisé par une autre app (Syncthing,
Nextcloud…) met les sauvegardes hors du téléphone.

**Planification** : WorkManager, tâche périodique unique `auto-backup`, toutes les 24 h, sans
contrainte ; la première échéance est 24 h après l'activation (la sauvegarde de l'activation est
faite directement). Android choisit le moment exact dans la période (la mise en veille peut la
retarder). Planifiée à l'activation, annulée à la désactivation ; au démarrage de l'app, planifiée
de nouveau si elle est activée et absente (`KEEP`). WorkManager la garde après un redémarrage ou
une mise à jour de l'app.

**Fichier** : même contenu que « Exporter mes données » (§ 5.6) — JSON v2 (§ 5.2), UTF-8, séance
en cours exclue — et même nom, `musclenote_backup_AAAA-MM-JJ.json` (jour local de la sauvegarde).
- Un fichier de ce nom existe déjà dans le dossier (sauvegarde du même jour) → son contenu est
  **remplacé** ; sinon il est créé (`application/json`).
- **Rotation**, seulement après une écriture réussie : parmi les fichiers du dossier nommés
  exactement `musclenote_backup_AAAA-MM-JJ.json` (date valide), les **14 aux dates les plus
  récentes** sont gardés, les autres supprimés. Rien d'autre n'est touché (copies `… (1).json`,
  autres noms, sous-dossiers) ; un export manuel enregistré dans ce dossier sous ce nom compte parmi
  les 14.
- Une sauvegarde à la fois : la tâche planifiée et le bouton partagent un verrou.

**État** — `auto_backup.json` (§ 4.2), propre à l'appareil, hors sauvegarde, non touché par un
import : `{ enabled, folderUri?, folderName?, lastSuccessAt?, lastFailureAt?, failure?,
failureDetail? }`, `failure` = `"folder"` (dossier inaccessible) ou `"write"`. Une réussite efface
l'échec.
- **Dossier inaccessible** : autorisation persistante absente, dossier introuvable, illisible ou
  qui refuse la création (`SecurityException`, `FileNotFoundException`,
  `IllegalArgumentException`…). La tâche se termine sans nouvel essai immédiat et réessaie à
  l'échéance suivante (carte SD remise, par ex.). Le message reste affiché jusqu'à la prochaine
  réussite. Pas de notification : l'état se lit dans Paramètres.

**Permissions** : WorkManager ajoute au manifeste fusionné `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`
(déjà déclarée), `ACCESS_NETWORK_STATE` et `FOREGROUND_SERVICE`. Les deux dernières sont
**retirées** (`tools:node="remove"`) : la tâche n'a ni contrainte réseau ni travail au premier
plan. 🔄 D39 : `ACCESS_NETWORK_STATE` est rendue pour la vérification quotidienne des mises à jour,
qui attend le réseau (§ 15.14.2) ; `FOREGROUND_SERVICE` reste retirée. `WAKE_LOCK` (permission
normale, accordée à l'installation) reste, WorkManager peut s'en servir pour exécuter la tâche.

### 15.3 Health Connect (🔄 D29, D30)

Health Connect est le stockage de santé du téléphone (intégré au système depuis Android 14 ; app
Health Connect sur Android 8 à 13). MuscleNote y **écrit** ses séances et le poids de corps, que
d'autres apps de santé lisent avec l'accord de l'utilisateur. Rien ne passe par le réseau ;
MuscleNote ne **lit** rien dans Health Connect.

**Disponibilité** : `HealthConnectClient.getSdkStatus(context) == SDK_AVAILABLE`, relu à chaque
affichage de Paramètres (retour au premier plan compris). Sinon (Android 8 à 13 sans l'app Health
Connect, ou avec une version trop ancienne) la section est **masquée**, titre compris, et rien
n'est envoyé.

**Installation hors Play Store** : pas d'obstacle. Health Connect ne regarde pas la source
d'installation ; la déclaration des permissions de santé dans la Play Console ne concerne que la
distribution par le Play Store, et des apps distribuées par F-Droid (Gadgetbridge, openScale sync)
écrivent dans Health Connect. La condition est l'activité de politique de confidentialité
ci-dessous : sans elle, Health Connect refuse la demande sans afficher d'écran.

**Manifeste** :
- permissions `android.permission.health.WRITE_EXERCISE` et `android.permission.health.WRITE_WEIGHT`
  (aucune permission de lecture) ;
- `<queries>` vers `com.google.android.apps.healthdata` (app Health Connect, Android 8 à 13) ;
- **activité de politique de confidentialité** `HealthPermissionsActivity`, exigée par Health
  Connect qui l'ouvre depuis son écran d'autorisation et ses réglages :
  - Android 14 et plus : `activity-alias` exporté vers cette activité, action
    `android.intent.action.VIEW_PERMISSION_USAGE`, catégorie
    `android.intent.category.HEALTH_PERMISSIONS`,
    `android:permission="android.permission.START_VIEW_PERMISSION_USAGE"` ;
  - Android 13 et moins : l'activité elle-même, exportée, action
    `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`.

**Écran de politique de confidentialité** (thème, langue et en-tête avec retour de l'app) : titre
« MuscleNote et Health Connect », puis :
- **Ce qui est écrit** : tes séances terminées (type musculation, début, fin, nom) et ton poids,
  une mesure par jour (ta pesée, sinon le poids saisi au début de ta dernière séance du jour).
- **Quand** : quand tu termines, modifies ou supprimes une séance, quand tu ajoutes ou supprimes
  une pesée, et quand tu appuies sur « Envoyer l'historique ».
- **Rien d'autre** : MuscleNote ne lit aucune donnée de Health Connect et n'envoie rien sur
  internet ; tes données restent sur ton téléphone.
- **Arrêter** : coupe l'interrupteur Health Connect dans Paramètres. Les données déjà écrites
  s'effacent depuis les réglages de Health Connect.

**Carte « Health Connect »** — nouvelle section « SANTÉ » de Paramètres, entre « SAUVEGARDE » et
« APPLICATION » ; même style que la carte de sauvegarde automatique (icône ronde `monitor_heart`
en `primary`, interrupteur) :
- Description : « Envoie tes séances de musculation et ton poids à Health Connect, pour les
  retrouver dans tes autres apps de santé. »
- **Interrupteur** :
  - activé → écran d'autorisation de Health Connect pour les deux permissions ; au retour, au moins
    une accordée → activé ; aucune → reste désactivé, « Autorisation refusée : rien n'est
    envoyé. » ;
  - désactivé → plus aucun envoi ; ce qui est déjà dans Health Connect y reste (on l'efface depuis
    Health Connect) ; les autorisations ne sont pas retirées (elles se gèrent dans Health Connect).
- **Activé**, sous l'en-tête :
  - autorisation manquante (refusée en partie, ou retirée depuis Health Connect) : « Séances non
    autorisées : elles ne sont pas envoyées. » et / ou « Poids non autorisé : il n'est pas
    envoyé. » + bouton [Autoriser] (redemande ; si Health Connect ne montre plus la demande,
    ouvre ses réglages des données de MuscleNote) ;
  - « Dernier envoi : {date heure} » (dernier envoi dont Health Connect a confirmé au moins une
    écriture ou suppression) ou « Rien d'envoyé pour l'instant » (`mutedForeground`) ;
  - 🔄 D30 — dès que le registre n'est pas vide, « Écrit dans Health Connect : {n séances},
    {m mesures de poids} » (`mutedForeground`) : ce que Health Connect a confirmé et qui y est
    encore, d'après le registre ;
  - 🔄 D30 — tant que la dernière erreur n'est pas effacée par un envoi sans erreur, en
    `destructive` : « Dernière erreur ({date heure}) : {détail} Nouvel essai à la prochaine
    modification ou au prochain lancement. », {détail} = « Séances : échec ({erreur}). »,
    « Poids : échec ({erreur}). » ou, quand Health Connect ne répond pas du tout, « Envoi
    impossible ({erreur}). » ;
  - bouton [Envoyer l'historique] : envoie toutes les séances terminées et tous les poids
    (indicateur pendant l'envoi) ; résultat dans un encart sous la carte (style § 2.12, point 4),
    🔄 D30 : « Health Connect a confirmé {n séances} et {m mesures de poids}. » — **seulement les
    écritures que Health Connect a confirmées pendant cet envoi** —, puis une ligne par type en
    échec (« Séances : échec ({erreur}). ») et par type non autorisé ; encart rouge si un type a
    échoué. Health Connect ne répond pas → « Envoi impossible ({erreur}). ». Ce qui était déjà
    envoyé est remplacé, jamais dupliqué.

**Enregistrements écrits** :

| source | enregistrement | champs | `clientRecordId` |
|---|---|---|---|
| séance terminée | `ExerciseSessionRecord` | type `EXERCISE_TYPE_STRENGTH_TRAINING` (musculation) ; début et fin : voir « Durée » ci-dessous ; décalage horaire du fuseau du téléphone à chacun de ces instants ; titre = nom affiché (§ 3.1 : préréglage dans la langue de l'app au moment de l'envoi, sinon nom, sinon date) ; ni notes, ni segments | `musclenote-session-{id}` |
| poids d'un jour local (🔄 D30) | `WeightRecord` | la règle du graphique de Progrès (§ 3.10) : la pesée du jour (§ 3.11) si elle existe, instant = sa `date` (midi local) ; sinon le `bodyWeight > 0` de la séance la plus récente du jour, instant = début de cette séance ; poids en kg | `musclenote-weight-{AAAA-MM-JJ}` |

- **Un seul poids par jour** (🔄 D30). La v1.0.5 écrivait un poids par séance et un par pesée
  (`musclenote-weight-workout-{id}`, `musclenote-weight-weighin-{date}`) : une séance et une pesée
  le même jour donnaient deux mesures, et l'encart annonçait « 2 mesures de poids » pour une seule
  pesée. Au premier envoi après la mise à jour, ces anciens enregistrements (ceux du registre)
  sont supprimés, et le poids de leur jour est écrit dans le même envoi (le jour se lit dans
  l'empreinte d'époque `{instant}|{kg}`). Ajouter une pesée un jour qui avait déjà un poids de
  séance remplace l'enregistrement du jour (même `clientRecordId`).
- **Durée** (🔄 D30) :
  - au moins 10 min (seuil du temps en salle, § 3.7) → début = `date`, fin = début +
    `durationMinutes` ;
  - moins de 10 min (séance saisie après coup) → l'estimation du temps en salle (§ 3.7 :
    moyenne arrondie des séances d'au moins 10 min de même clé, § 3.1), sinon 10 min ; la séance
    **finit** au moment où elle a été terminée dans l'app (`date` + `durationMinutes`) et commence
    l'estimation plus tôt ;
  - sans durée (finalisée automatiquement après 24 h, § 3.4) → début = `date`, fin = début +
    l'estimation (même règle). Avant D30 elle n'était pas envoyée du tout ; une séance existante
    de ce type n'est envoyée que par [Envoyer l'historique] ou quand elle est modifiée.
  - Ainsi la fin est toujours après le début (Health Connect l'exige) et Health Connect montre le
    même temps que « Temps en salle ». Une séance de 0 min partait avec 1 min en v1.0.5.
- **Type d'exercice** : `EXERCISE_TYPE_STRENGTH_TRAINING` (« Musculation » dans Health Connect) ;
  `EXERCISE_TYPE_WEIGHTLIFTING` désigne l'haltérophilie. Une séance écrite est visible dans l'app
  Health Connect (Données et accès › Activité › Exercice) ; ce qu'en montrent les autres apps
  (Fitbit / Google Health…) dépend de ce qu'elles importent et de leurs autorisations de lecture,
  hors du contrôle de MuscleNote (non vérifiable sans appareil).
- Poids hors de ]0 ; 1000] kg (limites de Health Connect) : non envoyé ce jour-là.
- Métadonnées : saisie manuelle (`Metadata.manualEntry`), `clientRecordId` ci-dessus,
  `clientRecordVersion` = instant de l'envoi en ms, toujours supérieur au précédent
  (`max(dernière version + 1, maintenant)`). Le préfixe de l'identifiant donne le type
  (`musclenote-session-` / `musclenote-weight-`). Health Connect **remplace** l'enregistrement de même
  `clientRecordId` quand la version est plus grande : modifier une séance ou une pesée met à jour
  Health Connect sans doublon ; supprimer supprime par `clientRecordId`.

**Envois automatiques** (interrupteur activé) :
- **Déclencheurs** : fin d'une séance (et finalisation après 24 h) ; modification dans l'éditeur
  (nom, préréglage, poids) ou de la date (Accueil) ; suppression d'une séance ; ajout, remplacement
  ou suppression d'une pesée ; import d'une sauvegarde (§ 5.4 : ce qui a changé est envoyé, ce qui
  a disparu est supprimé). Changer seulement les exercices ou les séries d'une séance ne renvoie
  rien (ils ne font pas partie de l'enregistrement).
- Ce qui existait avant l'activation n'est envoyé que par [Envoyer l'historique], ou quand il est
  modifié.
- **Mécanisme** : à chaque changement des séances ou des pesées, l'app compare l'avant et l'après
  et met en attente les `clientRecordId` concernés (enregistrés sur disque) ; la file est traitée
  une opération à la fois, 🔄 D30 **type par type** (séances, puis poids ; pour chacun
  suppressions puis écritures, par lots de 100). Une écriture ne compte que si Health Connect la
  confirme (identifiants renvoyés par `insertRecords`) : seule une écriture confirmée entre dans
  le registre et quitte la file ; un type en échec n'empêche pas l'autre. Seul un enregistrement
  du registre est supprimé dans Health Connect (un enregistrement jamais écrit quitte simplement
  la file). Un enregistrement dont le type n'est pas autorisé reste en attente jusqu'à
  l'autorisation. Échec (Health Connect
  indisponible, autorisation retirée, erreur) → la file est gardée et retentée au prochain
  changement, au retour au premier plan et sur [Envoyer l'historique] ; pas de tâche de fond.
- **Registre** — `health_connect.json` (§ 4.2), propre à l'appareil, hors sauvegarde :
  `{ enabled, sent: { clientRecordId: empreinte }, pending: [clientRecordId], lastVersion,
  lastSyncAt?, lastError?, lastErrorAt?, lastErrorType? }` (`lastErrorType` = `Session`, `Weight`,
  ou absent quand Health Connect ne répond pas). L'empreinte résume ce qui est écrit : début et fin,
  préréglage et nom d'une séance ; instant et poids d'un poids. Au démarrage, un enregistrement envoyé dont l'empreinte a changé ou dont
  la source a disparu (modifiée pendant que l'interrupteur était coupé, par exemple) est remis en
  attente. Changer la langue de l'app ne renvoie rien : le titre d'une séance change de langue à son
  prochain envoi.

### 15.4 Textes (FR et EN)

| clé | FR | EN |
|---|---|---|
| `settings.autoBackup.title` | Sauvegarde automatique | Automatic backup |
| `settings.autoBackup.desc` | Chaque jour, une sauvegarde JSON dans le dossier de ton choix. Les 14 plus récentes sont gardées. | Every day, a JSON backup in the folder of your choice. The 14 most recent are kept. |
| `settings.autoBackup.folder` | Dossier : {{name}} | Folder: {{name}} |
| `settings.autoBackup.change` | Changer | Change |
| `settings.autoBackup.last` | Dernière sauvegarde : {{date}} | Last backup: {{date}} |
| `settings.autoBackup.never` | Aucune sauvegarde pour l'instant | No backup yet |
| `settings.autoBackup.folderLost` | Le dossier de sauvegarde n'est plus accessible (supprimé, renommé ou autorisation retirée). Choisis-le à nouveau. | The backup folder can't be reached anymore (deleted, renamed or access removed). Choose it again. |
| `settings.autoBackup.chooseFolder` | Choisir un dossier | Choose a folder |
| `settings.autoBackup.failed` | Échec de la sauvegarde du {{date}} : {{error}} | Backup of {{date}} failed: {{error}} |
| `settings.autoBackup.now` | Sauvegarder maintenant | Back up now |
| `settings.section.health` | SANTÉ | HEALTH |
| `health.title` | Health Connect | Health Connect |
| `health.desc` | Envoie tes séances de musculation et ton poids à Health Connect, pour les retrouver dans tes autres apps de santé. | Sends your strength sessions and body weight to Health Connect, so your other health apps can use them. |
| `health.denied` | Autorisation refusée : rien n'est envoyé. | Permission denied: nothing is sent. |
| `health.missingSessions` | Séances non autorisées : elles ne sont pas envoyées. | Sessions not allowed: they aren't sent. |
| `health.missingWeight` | Poids non autorisé : il n'est pas envoyé. | Weight not allowed: it isn't sent. |
| `health.allow` | Autoriser | Allow |
| `health.last` | Dernier envoi : {{date}} | Last sent: {{date}} |
| `health.never` | Rien d'envoyé pour l'instant | Nothing sent yet |
| `health.written` (🔄 D30) | Écrit dans Health Connect : {{sessions}}, {{weights}} | Written to Health Connect: {{sessions}}, {{weights}} |
| `health.failed` (🔄 D30) | Dernière erreur ({{date}}) : {{detail}} Nouvel essai à la prochaine modification ou au prochain lancement. | Last error ({{date}}): {{detail}} Will retry on the next change or the next launch. |
| `health.unavailable` (🔄 D30) | Envoi impossible ({{error}}). | Couldn't send ({{error}}). |
| `health.typeFailedSessions` (🔄 D30) | Séances : échec ({{error}}). | Sessions: failed ({{error}}). |
| `health.typeFailedWeight` (🔄 D30) | Poids : échec ({{error}}). | Weight: failed ({{error}}). |
| `health.sendHistory` | Envoyer l'historique | Send history |
| `health.historySent` (🔄 D30) | Health Connect a confirmé {{sessions}} et {{weights}}. | Health Connect confirmed {{sessions}} and {{weights}}. |
| `health.sessions_one` / `_other` | {{count}} séance / {{count}} séances | {{count}} session / {{count}} sessions |
| `health.weights_one` / `_other` | {{count}} mesure de poids / {{count}} mesures de poids | {{count}} weight entry / {{count}} weight entries |
| `health.privacy.title` | MuscleNote et Health Connect | MuscleNote and Health Connect |
| `health.privacy.written` (🔄 D30) | Ce qui est écrit : tes séances terminées (type musculation, début, fin, nom) et ton poids, une mesure par jour (ta pesée, sinon le poids saisi au début de ta dernière séance du jour). | What is written: your finished sessions (strength training, start, end, name) and your body weight, one entry per day (your weigh-in, otherwise the weight entered when starting your last session of the day). |
| `health.privacy.when` | Quand : quand tu termines, modifies ou supprimes une séance, quand tu ajoutes ou supprimes une pesée, et quand tu appuies sur « Envoyer l'historique ». | When: when you finish, edit or delete a session, when you add or delete a weigh-in, and when you tap “Send history”. |
| `health.privacy.nothingElse` | Rien d'autre : MuscleNote ne lit aucune donnée de Health Connect et n'envoie rien sur internet ; tes données restent sur ton téléphone. | Nothing else: MuscleNote reads no data from Health Connect and sends nothing over the internet; your data stays on your phone. |
| `health.privacy.stop` | Arrêter : coupe l'interrupteur Health Connect dans Paramètres. Les données déjà écrites s'effacent depuis les réglages de Health Connect. | To stop: turn off the Health Connect switch in Settings. Data already written can be deleted from Health Connect's settings. |

### 15.5 Notification de séance (🔄 D31)

Pendant une séance en cours, une seule notification **« Séance en cours »** résume la séance et
permet d'avancer sans ouvrir l'app. Elle remplace la notification « Repos en cours » (§ 7.2) et
son canal (§ 7.1).

**Publication** : canal `workout-ongoing` (LOW, silencieux, `VISIBILITY_PUBLIC`),
`setOngoing(true)`, `setOnlyAlertOnce(true)`, `setSilent(true)`, `BigTextStyle`, toucher → écran
`workout`. Publiée dès qu'une séance est en cours (démarrage, chargement de l'app — après un
redémarrage du téléphone, par exemple —, changement de langue), republiée à chaque changement de
la séance, du repos ou des exercices perso (au plus 4 fois par seconde, le dernier état est
toujours publié), retirée à la fin, à l'abandon et à la finalisation après 24 h (§ 3.4). Pas de
service de premier plan : le chronomètre est celui du système (`setUsesChronometer`). Sans
notifications autorisées, rien n'est publié. Android 14+ permet de balayer une notification en
cours : elle revient au changement suivant de la séance ou du repos.

| moment | titre | chronomètre | texte | actions |
|---|---|---|---|---|
| hors repos | nom de la séance (§ 3.1) | durée de la séance (`setWhen(date)`) | « {exercice} · Série {n} · {détail} » | « Valider la série » |
| repos (§ 7.2) | « {séance} · Repos » | compte à rebours jusqu'à `endsAt` | « Prochaine : {exercice} · Série {n} · {détail} » | « −30 s », « +30 s », « Passer » |
| tout validé | nom de la séance | durée de la séance | « Toutes les séries sont validées » | aucune (hors repos) |

- **Prochaine série** : dans l'ordre de la séance, la première série non validée **après la
  dernière série validée** ; s'il n'y en a pas, la première non validée depuis le début (une série
  sautée plus haut). Un exercice absent de l'écran de séance (inconnu) est ignoré. {n} = rang de
  la série dans son exercice (à partir de 1).
- **Détail** : force « {reps} × {poids} kg » (poids à la locale, `82,5` en français), ou
  « {reps} reps » à 0 kg ; cardio : durée et distance renseignées, « 30 min · 5 km », rien sans
  l'une ni l'autre.
- **Actions** (récepteur `RestTimerReceiver`, intents explicites, `FLAG_IMMUTABLE`, utilisables
  écran verrouillé ; chacune attend le chargement des données si l'app était fermée, puis
  republie la notification) :
  - « Valider la série » : valide la prochaine série et lance le repos, exactement comme un appui
    dans l'écran de séance (§ 2.6, § 7.2) ;
  - « −30 s » / « +30 s » : § 7.2 ;
  - « Passer » : arrêt du repos (§ 7.2).
  Android ne montre que trois actions : pendant le repos, « Valider la série » n'y est pas.
- **Live Update** (Android 16 et plus) : la notification demande sa promotion
  (`setRequestPromotedOngoing(true)`, permission `POST_PROMOTED_NOTIFICATIONS`, § 1). La pastille
  de la barre d'état montre le compte à rebours pendant le repos, sinon « Série {n} »
  (`setShortCriticalText`). L'utilisateur (réglages de notification de l'app) ou le fabricant
  peuvent la refuser : c'est alors une notification en cours classique, avec le même contenu.
  Avant Android 16, notification en cours classique.
- **Montres** : Android ne relaie pas une notification **en cours** vers une montre connectée
  (règle de Wear OS ; Wear OS 7 relaie les Live Updates selon le fabricant). Ce qui arrive sur la
  montre et la fait vibrer, c'est l'**alerte « Repos terminé »** (§ 7.2, canal `rest-timer`, son et
  vibration, pas en cours), qui porte l'action « Valider la série » quand une série reste à
  valider : au poignet, la boucle est complète — vibration de fin de repos, la série, « Valider la
  série », nouveau repos. Les actions « −30 s », « +30 s » et « Passer » restent sur le téléphone.
  L'alerte n'est publiée que l'app en arrière-plan ou fermée (§ 7.2) : app ouverte au premier
  plan, seul le téléphone vibre.

**Textes** :

| clé | FR | EN |
|---|---|---|
| `channel.workoutOngoing` | Séance en cours | Workout in progress |
| `workoutNotif.restTitle` | {{workout}} · Repos | {{workout}} · Rest |
| `workoutNotif.set` | Série {{n}} · {{detail}} | Set {{n}} · {{detail}} |
| `workoutNotif.setOnly` | Série {{n}} | Set {{n}} |
| `workoutNotif.strength` | {{reps}} × {{weight}} kg | {{reps}} × {{weight}} kg |
| `workoutNotif.reps` | {{reps}} reps | {{reps}} reps |
| `workoutNotif.minutes` | {{value}} min | {{value}} min |
| `workoutNotif.km` | {{value}} km | {{value}} km |
| `workoutNotif.next` | Prochaine : {{set}} | Next: {{set}} |
| `workoutNotif.allDone` | Toutes les séries sont validées | All sets are done |
| `workoutNotif.completeSet` | Valider la série | Complete set |
| `workoutNotif.minus30` / `plus30` | −30 s / +30 s | −30 s / +30 s |

### 15.6 Pistes

- **App Wear OS** : une app sur la montre (Ongoing Activity, tuile du repos) donnerait au poignet
  ce qu'une notification de téléphone ne peut pas y montrer : le compte à rebours, « −30 s »,
  « +30 s » et « Passer » pendant le repos. Elle demanderait un module Wear, la synchronisation de
  la séance entre téléphone et montre (Data Layer) et une publication à part : hors du périmètre
  actuel (§ 15.5).
- **Lire le poids dans Health Connect** (🔄 D38) : une pesée faite par une balance connectée ou une
  autre app compterait alors comme « pesé ce jour-là » (§ 15.13.1) et ferait taire le rappel. Elle
  demanderait la permission `health.READ_WEIGHT` (D19, D29 : aujourd'hui aucune lecture) et de
  distinguer les poids écrits par MuscleNote : hors du périmètre actuel.
- **Objectif de séances réglable** (🔄 D41) : choisir le nombre de séances qui valide une semaine
  (aujourd'hui fixé à 3, § 3.6), pour l'**Accueil et le Journal ensemble** — série, record, carte
  « Cette semaine », barres des 8 semaines et compteurs « n/3 » du Journal suivraient le même nombre.
  Il demanderait un réglage sauvegardé et de décider si un changement d'objectif réécrit les séries
  passées (record compris).
- **Protéines dans Progrès et l'export CSV** (🔄 D42) : une courbe dans Progrès à côté des calories et
  de l'eau, une colonne « Protéines (g) » dans `musclenote_nutrition_*.csv` (§ 8). Un objectif de
  protéines daté serait alors à décider (aujourd'hui il ne vaut que pour le jour même, § 15.16.1).
- **Prévenir quand le stock est bas** (🔄 D42) : une notification quand un complément passe sous son
  seuil (§ 15.16.4) ; aujourd'hui, seul le rouge de sa ligne le signale.
- **Muscles dans Progrès** (🔄 D43) : séries ou volume par muscle sur la semaine, ou une carte des
  muscles travaillés récemment, à partir des muscles du § 15.17.2 (principal et secondaire à pondérer :
  à décider). Aujourd'hui les muscles ne servent qu'à la fiche, au filtre et à la sheet « Muscle ».
- **Muscles dans l'export CSV** (🔄 D43) : l'export ne décrit pas les exercices (§ 8) ; les muscles n'y
  entrent pas.

**Maquettes disponibles** (🔄 D40) — la maquette Claude Design « v2 listes groupées » (15/11/2026,
`design/claude-design/`) dessine des fonctions que l'app n'a pas ; elles ne sont pas codées et
attendent leur propre sujet (décision, spécification, données) :

- ~~Calendrier du mois~~ et ~~« 3/3 · validée »~~ (`T2-Journal`) : réalisés par 🔄 D41 (§ 15.15),
  avec la règle fixe de 3 séances ; restent la mention « S45 et S46 validées » du résumé du mois et
  l'objectif réglable (piste ci-dessus).
- **Filtre du Journal par type de séance** (`T2-Journal`) : bouton `tune` dans la barre de titre.
- ~~Protéines~~, ~~stock des compléments~~ et ~~moyenne sur 7 jours~~ (`T2-Nutrition`, `T2-Stock`) :
  réalisés par 🔄 D42 (§ 15.16), avec le groupe du héros de la maquette (lignes « Eau » et
  « Protéines », boutons +) et « 1 sur 3 pris » à droite de l'étiquette « Compléments » ; restent le
  lien « Gérer », l'heure de la prise en sous-titre (« prise à 07:40 »), la case ronde à gauche (D40),
  les puces de saisie rapide du stock (« +250 g ») et le lien « Tendances » (courbes de Nutrition,
  déplacées depuis Progrès).
- **Sélecteur plein écran trié** (`T2-Carte-corps`, 🔄 D43) : le contrôle segmenté « Par muscle » ·
  « Récents » · « A – Z », la recherche dans la barre du haut et le bouton + rond de chaque ligne ; le
  sélecteur garde sa sheet, ses lignes à cocher et son segmenté « Par groupe » / « Par muscle »
  (§ 15.17.6).
- Les planches plus anciennes dessinent aussi **Mes records** (`T2-Records`) et les **mensurations**
  (`T2-Mensurations`).

### 15.7 Refonte « Studio épuré » : séance en cours (🔄 D32)

Deuxième étape de la refonte décrite dans `DESIGN.md` : l'écran de séance en cours passe au
gabarit **« en direct »** (DESIGN.md § 10.3), fait uniquement des jetons et composants du design
system. Il **remplace** la disposition des § 2.4 – 2.6 et le bandeau de repos du § 7.2 ; l'éditeur
de séance terminée (§ 2.9) garde ses lignes et le clavier du système. Les règles du § 3, la
notification de séance (§ 15.5) et le **format des données ne changent pas** (§ 4, § 5) : aucune
migration.

#### 15.7.1 Écran

- **En-tête, fixe** : « Réduire » (⌄ : retour à l'Accueil, la séance reste en cours) ; nom affiché
  (§ 3.1) et « 78,4 kg · 2 / 16 séries » (poids omis s'il vaut 0 ; 🔄 D38 : le poids de la séance,
  sinon la dernière pesée connue, omis sans aucun poids, § 15.13.1) ; chronomètre `m:ss` en jaune
  (minutes non bornées). Dessous, la progression en segments, un par série (masquée sans série).
- **Échauffement** (§ 2.4, toujours local à l'écran, non enregistré) : une ligne « Échauffement »
  avec « 2/5 exercices » ; la toucher ouvre une bottom sheet avec les 5 exercices à cocher.
- **Un bloc par exercice** (ordre de la séance, exercices inconnus masqués — § 10.5), 24 dp entre
  deux blocs, sans carte :
  - titre — **appui long** pour réordonner (glisser-déposer, 🔄 D14) —, bouton « fiche »
    (→ historique de l'exercice, § 2.8), bouton ⋮ (bottom sheet : note de l'exercice, enregistrée
    à chaque frappe, et « Retirer de la séance » avec confirmation). La note s'affiche sous le titre ;
  - puces : **dernière fois** (date de la séance de référence, § 15.7.2) et **suggestion**
    (§ 15.7.3) ;
  - colonnes **# · Préc. · Kg · Reps** (cardio : **# · Préc. · Min · Km**) ;
  - **lignes de série** de 56 dp : valeurs d'une série à faire en gris (`textTertiary`), d'une
    série validée en `textPrimary` ; case de 48 dp (validée : inverse avec ✓). Toucher une valeur
    ouvre le pavé (§ 15.7.4) ; toucher la case bascule la validation (§ 2.6) ;
  - le **repos** (§ 7.2), en jaune, juste après la **dernière série validée** dans l'ordre de la
    séance (tout en haut s'il n'y en a pas) : « REPOS », temps restant, « sur 2:30 », barre du temps
    écoulé, « −30 s », « +30 s », « Passer » (mêmes effets que les actions de la notification,
    § 15.5). Il disparaît à la fin du repos, comme avant ;
  - la ligne **« + Série »**, toujours visible sous la dernière série du bloc : ajoute une série
    (règles du § 2.5).
- Fin de liste : « Ajouter un exercice » (→ sélecteur, § 2.7). Sans exercice : état vide « Aucun
  exercice » / « Ajoute des exercices pour commencer » avec la même action. Sans séance en cours :
  « Aucune séance active » et « Retour ».
- **En bas, fixe** : le pavé numérique quand une valeur est en saisie (§ 15.7.4), sinon
  « Terminer la séance », qui ouvre une confirmation en bottom sheet : « Terminer la séance ? »,
  ou, sans série validée, « Abandonner la séance ? » (elle ne sera pas enregistrée, 🔄 D15). Le
  bouton de confirmation reste en inverse, jamais rouge (DESIGN.md § 13). 🔄 D37 : « Terminer »
  ouvre le résumé de la séance (§ 15.12).
- **Supprimer une série** : bouton « Supprimer la série » de l'en-tête du pavé, puis confirmation
  en bottom sheet (🔄 jusqu'ici immédiate, § 10.8).
- **Valider une série** (case, pavé ou notification) : comme avant, non validée → validée lance le
  repos avec le nom de l'exercice (§ 2.6, § 7.2) ; la notification de séance et ses actions
  (§ 15.5) sont inchangées.

#### 15.7.2 Colonne « Préc. »

- **Séance de référence** d'un exercice : la séance **terminée** la plus récente où il a **au moins
  une série validée**, quelle que soit sa clé de séance (la séance en cours n'en fait pas partie).
- La ligne de rang n (n-ième série de l'exercice dans la séance en cours) montre la **n-ième série
  validée** de l'exercice dans la séance de référence (les séries non validées y sont sautées) :
  - force : « 80 × 8 » (charge × reps, charge à la locale : `82,5`) ; « 12 reps » à 0 kg ;
  - cardio : « 30 · 5,2 » (min · km) ; « 30 min » sans distance ;
  - aucune série de ce rang, ou pas de séance de référence : « — ».
- **Toucher** la valeur la recopie dans la ligne : reps et charge (cardio : durée, distance,
  vitesse et inclinaison). La validation de la ligne ne change pas ; « — » ne fait rien.
- Puce **dernière fois** (icône historique) : date de la séance de référence (« jeu. 5 nov. ») ;
  absente sans séance de référence.

#### 15.7.3 Suggestion de charge

Pour un exercice de force qui a une séance de référence (§ 15.7.2) ; jamais en cardio.

- **Reps tenues** : dans la séance de référence, **toutes** les séries de l'exercice sont validées
  (une série prévue mais non validée = objectif manqué) et **chacune a au moins autant de reps que
  la première**. L'app n'enregistre pas d'objectif de reps : celui de la première série en tient
  lieu (des séries à reps décroissantes, 12 / 10 / 8, donnent donc « même charge »).
- **Poids du corps** : toutes les séries validées de la séance de référence sont à 0 kg.
- **Proposition** : reps tenues → **+2,5 kg** (poids du corps : **+1 rep**) ; sinon **même
  charge** (poids du corps : **mêmes reps**).
- **Affichage** dans le bloc : puce verte « ↑ +2,5 kg » / « ↑ +1 rep » ; « Même charge » /
  « Mêmes reps » en puce neutre (le vert ne marque qu'une hausse, DESIGN.md § 1).
- **Jamais appliquée d'office.** Toucher la puce l'applique aux séries **non validées** de
  l'exercice qui ont une série « Préc. » de même rang : charge = charge de cette série + 2,5 (ou
  + 0), reps inchangées ; au poids du corps, reps = reps de cette série + 1 (ou + 0), charge
  inchangée. Les séries validées ne bougent pas ; toucher deux fois donne le même résultat.

#### 15.7.4 Pavé numérique

Remplace le clavier du système pour les séries de la séance en cours.

- **Ouverture** : toucher une valeur d'une ligne. Le champ en saisie est **en jaune** (fond
  `liveDim`, curseur `liveText`) et la liste défile pour qu'il reste visible au-dessus du pavé.
  Fermeture : bouton « Fermer le pavé », retour système, ou après la dernière série (ci-dessous).
- **En-tête** : contrôle segmenté des champs de la série, le premier rappelant le rang
  (« Kg · série 3 » / « Reps » ; cardio « Min · série 2 » / « Km » / « Km/h » / « % ») —
  toucher un segment change de champ ; « Supprimer la série » ; « Fermer le pavé ». Vitesse et
  inclinaison, absentes des colonnes, s'affichent pendant leur saisie dans un champ héros au-dessus
  des touches.
- **Touches** : 1 – 9, 0, séparateur décimal de la langue (« , » en français, « . » en anglais),
  effacer, pas « − » et « + », touche principale sur deux colonnes.
- **Saisie** (règles pures, testées en JVM) :
  - un champ qui vient d'être ouvert (toucher, « Suivant », segment, pas) est **neuf** : la
    première touche chiffre ou séparateur **remplace** sa valeur, « effacer » le **vide** ;
    ensuite, ajout et effacement caractère par caractère ;
  - séparateur : sans effet sur les reps ou s'il y en a déjà un ; sur un champ vide, il donne
    « 0, » ; **2 décimales** au plus ;
  - pas de zéro inutile en tête (« 0 » puis « 7 » → « 7 ») ;
  - longueur maximale, comme au § 2.6 : reps 3, charge 6, durée 4, distance 5, vitesse 4,
    inclinaison 4 caractères ; une touche qui la dépasserait est sans effet ;
  - chaque touche s'applique **tout de suite** à la série (🔄 D20 conservé), lue par
    `parseDecimal` / `parseInteger` (§ 10.1) ; un champ vide vaut 0 ;
  - **pas** : ±2,5 kg, ±1 rep, ±1 min, ±0,5 km, ±0,5 km/h, ±1 % ; jamais sous 0 ; résultat
    arrondi au centième (demi vers +∞, § 3.5) ; sans effet s'il dépasserait la longueur maximale ;
  - affichage des valeurs (lignes et pavé) : 2 décimales au plus, sans zéro final ni séparateur de
    milliers (`82,5`, `80`, `81,25`) ; une valeur 0 s'affiche `0`.
- **Touche principale** : « **Suivant** » passe au champ suivant de la série (Kg → Reps ; Min → Km
  → Km/h → %) ; sur le dernier, « **✓ Valider la série** » valide la série (le repos démarre) puis
  ouvre la **série non validée suivante du même exercice** (premier champ, neuf), ou ferme le pavé
  s'il n'y en a plus. Sur une série déjà validée, la touche du dernier champ est « OK » et ferme le
  pavé.

#### 15.7.5 Textes (FR et EN)

Clés Android (`strings.xml`) ; les libellés existants sont repris (« Terminer la séance », « Ajouter
un exercice », « Repos », « Passer », « −30 s », « +30 s », « Valider la série », échauffement,
note, retrait).

| clé | FR | EN |
|---|---|---|
| `workout_subtitle` (pluriel sur le total) | {{poids}} kg · {{faites}} / {{total}} séries | {{weight}} kg · {{done}} / {{total}} sets |
| `workout_subtitle_no_weight` (pluriel) | {{faites}} / {{total}} séries | {{done}} / {{total}} sets |
| `workout_col_index` / `_previous` | # / Préc. | # / Prev. |
| `workout_col_kg` / `_reps` / `_min` / `_km` | Kg / Reps / Min / Km | Kg / Reps / Min / Km |
| `workout_previous_strength` | {{charge}} × {{reps}} | {{weight}} × {{reps}} |
| `workout_previous_reps` | {{reps}} reps | {{reps}} reps |
| `workout_previous_cardio` / `_minutes` | {{min}} · {{km}} / {{min}} min | {{min}} · {{km}} / {{min}} min |
| `workout_previous_copy` | Recopier la série précédente {{valeur}} | Copy previous set {{value}} |
| `workout_last_time` | Dernière fois le {{date}} | Last time on {{date}} |
| `workout_suggest_plus_kg` / `_plus_rep` | +{{valeur}} kg / +1 rep | +{{value}} kg / +1 rep |
| `workout_suggest_same_weight` / `_same_reps` | Même charge / Mêmes reps | Same weight / Same reps |
| `workout_suggest_apply` | Appliquer la suggestion : {{valeur}} | Apply suggestion: {{value}} |
| `workout_add_set_row` | Série | Set |
| `workout_set_check` / `_checked` | Valider la série {{n}} / Série {{n}} validée | Complete set {{n}} / Set {{n}} completed |
| `workout_rest_total` | sur {{total}} | of {{total}} |
| `workout_keypad_first` | {{champ}} · série {{n}} | {{field}} · set {{n}} |
| `workout_keypad_speed` / `_incline` | Km/h / % | Km/h / % |
| `workout_keypad_next` / `_ok` | Suivant / OK | Next / OK |
| `workout_delete_set` | Supprimer la série | Delete set |
| `workout_delete_set_title` | Supprimer la série {{n}} ? | Delete set {{n}}? |
| `workout_delete_set_message` | Elle sera retirée de cette séance. | It will be removed from this session. |
| `workout_exercise_remove` | Retirer de la séance | Remove from session |
| `workout_exercise_remove_message` | Ses séries de cette séance seront perdues. | Its sets in this session will be lost. |
| `workout_finish_message` (pluriel) | {{n}} séries validées en {{durée}}. Tu pourras corriger la séance ensuite. | {{n}} sets completed in {{duration}}. You can edit the session afterwards. |
| `workout_abandon_title` | Abandonner la séance ? | Abandon the session? |
| `workout_continue` | Continuer la séance | Keep going |
| `workout_empty_title` | Aucun exercice | No exercises |
| `workout_no_active_message` | Démarre une séance depuis l'Accueil. | Start a session from Home. |

### 15.8 Refonte « Studio épuré » : navigation, Accueil, Journal et logo (🔄 D33)

Troisième étape de la refonte décrite dans `DESIGN.md` : la navigation à quatre onglets, l'Accueil
et le **Journal** (la liste des séances, qui quitte l'Accueil) passent aux gabarits et composants du
design system, et l'app reçoit le logo de la maquette. Progrès, Nutrition et Paramètres gardent leurs
écrans actuels, comme « Démarrer une séance », le sélecteur d'exercices, l'historique d'un exercice
et l'éditeur de séance. Les règles du § 3, les notifications (§ 7, § 15.5) et le **format des
données ne changent pas** (§ 4, § 5) : aucune migration.

#### 15.8.1 Navigation

Remplace le § 2.1 :

```
Racine (Navigation Compose, aucun en-tête système)
├── Onglets (StudioNavigationBar, 4 destinations)
│   ├── home        « Accueil »     icône `home`
│   ├── journal     « Journal »     icône `calendar_month`
│   ├── progress    « Progrès »     icône `monitoring`      écran actuel (§ 2.10)
│   └── nutrition   « Nutrition »   icône `restaurant`      écran actuel (§ 2.11)
├── settings                        Paramètres, écran actuel (§ 2.12), par l'engrenage de l'Accueil
├── start-workout                   plein écran, inchangés : démarrer une séance,
├── workout                         séance en cours,
├── exercise-picker                 sélecteur d'exercices,
├── exercise-history/{exerciseId}   historique d'un exercice,
└── edit-workout/{workoutId}        éditeur de séance terminée
```

- **Barre d'onglets** (DESIGN.md § 9.2) : filet en haut, icône pleine et libellé en gras pour
  l'onglet sélectionné, sans indicateur. Affichée sur les quatre onglets et sur **Paramètres**, page
  de second niveau de l'Accueil : l'onglet Accueil y reste sélectionné et le toucher y ramène.
  Masquée sur les écrans plein écran. Chaque onglet garde son état (défilement) d'une visite à
  l'autre.
- **Mini-barre** de la séance en cours, dès qu'une séance est en cours, 8 dp au-dessus de la barre
  d'onglets, dans les marges de l'écran : sur **Journal, Progrès, Nutrition et Paramètres**. Point
  jaune, nom affiché de la séance (§ 3.1), puis pendant un repos « repos **1:48** » (temps restant),
  sinon le chrono de la séance « **17:48** » (`m:ss`, minutes non bornées) en jaune ; bouton texte
  « Reprendre ». Toucher la barre ou « Reprendre » ouvre la séance. Sur l'Accueil, la surface « En
  cours » (§ 15.8.2) tient ce rôle : pas de mini-barre (maquette).
- **Ouvrir la séance** (surface de l'Accueil, mini-barre, toucher de la notification « Séance en
  cours » ou de l'alerte « Repos terminé ») : si la séance est déjà dans la pile (sous l'historique
  d'un exercice ou le sélecteur, par exemple), on y revient ; sinon elle s'ouvre par-dessus l'écran
  affiché. Elle n'est **jamais deux fois** dans la pile. Sans séance en cours (notification restée
  affichée), l'écran « Aucune séance active » s'affiche comme avant.
- **Quitter la séance** : « Réduire » et le retour système reviennent à l'écran d'où on l'a ouverte
  (un onglet, Paramètres) ; juste après « Commencer », c'est l'écran d'où « Démarrer une séance »
  a été ouvert (l'Accueil, ou le Journal vide), puisque « Démarrer » a été remplacé (§ 2.3).
  Abandonner la séance revient à l'Accueil ; la terminer ouvre son résumé au-dessus de l'Accueil
  (🔄 D37, § 15.12), dont « Terminer » et le retour reviennent à l'Accueil.
- **Autres entrées externes** : rappel de complément → onglet Nutrition ; fichier JSON partagé ou
  ouvert → Accueil puis Paramètres (confirmation d'import, § 5.4). Les actions des notifications
  (« Valider la série », « −30 s », « +30 s », « Passer », § 15.5) n'ouvrent pas l'app et ne changent
  pas.
- **Retour, geste prédictif compris** (`android:enableOnBackInvokedCallback="true"`, Android 13+ ;
  d'office à partir d'Android 16) :
  - une bottom sheet ouverte, puis le pavé numérique, se ferment d'abord ;
  - Journal, Progrès, Nutrition et Paramètres → Accueil ; écran plein écran → écran précédent
    (étape 2 de « Démarrer » → étape 1, comme avant) ; Accueil → quitte l'app, avec l'aperçu du
    système (retour à l'écran d'accueil du téléphone) ;
  - transitions en **fondu** au rythme `standard` (DESIGN.md § 7) entre deux écrans : pendant le
    geste, l'écran précédent apparaît sous le doigt ; geste annulé, rien ne change.

#### 15.8.2 Accueil

Remplace le § 2.2. Gabarit **hub** (DESIGN.md § 10.1), de haut en bas :

1. **Barre** : date du jour, jour de la semaine en toutes lettres (« Samedi 14 novembre »,
   « Saturday, November 14 »), et l'engrenage → Paramètres.
2. **Une seule surface mise en avant** :
   - **séance en cours** — « En cours » : « EN COURS » à gauche, nom affiché à droite ; chrono de la
     séance (`m:ss`, minutes non bornées) en grand, en jaune ; pendant un repos, « Repos **1:48** ·
     Développé couché, série 3 » (temps restant en jaune, puis la prochaine série du § 15.5 ; sans
     prochaine série, « Repos 1:48 » seul) ; les segments des séries, « 2 / 16 séries » à gauche et
     le poids de corps « 78,4 kg » à droite (omis s'il vaut 0 ; 🔄 D38 : poids de la séance, sinon
     dernière pesée connue, § 15.13.1) ; sans série, ni segments ni
     compteur ; bouton principal « Reprendre » → séance.
   - **sinon** — « Prochaine séance » : légende « Prochaine séance » ; titre = le type sélectionné
     (au départ le type suggéré, § 15.8.3), ou « Nouvelle séance » sans suggestion ; dessous
     « Dernière fois il y a 7 jours · 5 exercices » (« aujourd'hui », « hier » ; la dernière séance
     de ce type), ou sans suggestion « Choisis ta séance à l'étape suivante » ; puces des **types
     réguliers** (3 au plus, le suggéré en premier et sélectionné ; une seule sélection), puis
     « Autre… » ; bouton principal « Démarrer ».
     - « Démarrer » avec un type → écran « Démarrer une séance » **directement à l'étape du poids de
       corps** (§ 2.3, étape 2), avec ce type : son préréglage, ou le nom perso de sa dernière
       séance ; ← revient à l'étape 1, ce type déjà choisi.
     - « Autre… », ou « Démarrer » sans type → « Démarrer une séance » à l'étape 1, comme avant.
3. **Sections**, espacées de 40 dp, chacune seulement si elle a un contenu :
   1. **« Mise à jour disponible »** (🔄 D25, mêmes conditions qu'au § 2.2) : une ligne « MuscleNote
      {version} » / « Version installée : {installée} » avec chevron → téléchargement (APK dans le
      navigateur, sinon page de la Release) ; bouton texte « Plus tard », qui masque l'annonce
      jusqu'à la version suivante. 🔄 D39 : « Plus tard » et « Ignorer cette version » de la
      notification masquent la même version (l'un fait aussi l'autre, § 15.14.4) ; la version
      connue vient de la vérification quotidienne, qui ne tourne que si la notification « Nouvelle
      version disponible » est activée, ou de « Vérifier maintenant » (§ 15.14.2) : réglage coupé,
      l'annonce n'apparaît qu'après une vérification manuelle.
   2. **« Depuis l'ancienne app »** (🔄 D4/D5, mêmes conditions : aucune séance terminée ni en cours,
      carte non masquée) : « Tu viens de l'ancienne MuscleNote ? » et le mode d'emploi (§ 5.5) en
      paragraphe ; bouton texte « Masquer » (préférence locale, comme avant).
   3. **« Dernière séance »**, lien « Tout voir » → onglet Journal : la ligne de la séance terminée la
      plus récente (même ligne et mêmes actions qu'au Journal, § 15.8.4).
   4. **« Cette semaine »** : rangée de stats « **2/3** séances » (séances terminées de la semaine
      en cours, sur les 3 qui la valident), « **4 sem.** série en cours » (§ 3.6 : `current` si
      `isAlive`, sinon 0), « **9 sem.** record » (`best`) ; barres des 8 dernières semaines (cette
      semaine à droite ; hauteur selon le nombre de séances, semaine validée en `textPrimary`, les
      autres en `textTertiary`) et la légende « 8 dernières semaines · 3 séances = semaine
      validée ».
   Sans aucune séance terminée, les sections 3 et 4 n'apparaissent pas.

Quittent l'Accueil : l'image de fond floutée, la ligne « Séances / Volume total / Dernier poids »
et la carte « temps en salle » (totaux → Journal ; le dernier poids reste dans Progrès, « Actuel »,
§ 2.10), la légende Validée / Partielle / Aucune et les phrases d'aide de la série, la liste de
toutes les séances (→ Journal) et l'état vide « Prêt à s'entraîner ? » (la surface « Nouvelle
séance » le remplace).

#### 15.8.3 Surface de l'Accueil et type suggéré (règles pures, testées en JVM)

- **Choix de la surface** : une séance en cours → « En cours » ; sinon « Prochaine séance ».
- **Fenêtre** : les séances terminées des **42 derniers jours** (jour local de la séance ≥
  aujourd'hui − 41 jours), groupées par **clé de séance** (§ 3.1 : préréglage, ou nom normalisé).
- **Types réguliers** : les clés qui ont **au moins 2 séances** dans la fenêtre **et au moins la
  moitié** des séances de la clé la plus fréquente (un « Cardio » occasionnel au milieu d'un Push /
  Pull / Legs n'en est pas un).
- **Ordre** : de la clé dont la dernière séance est la plus ancienne à la plus récente (à égalité :
  la plus fréquente, puis l'ordre alphabétique de la clé). La première est le **type suggéré** —
  dans une rotation, celui qui revient ; les puces montrent les trois premières.
- Aucun type régulier → pas de suggestion (« Nouvelle séance », pas de puce de type).
- Pour chaque type : nom affiché et démarrage selon sa séance la plus récente (préréglage, sinon son
  `name`) ; « il y a N jours » = jours calendaires locaux entre cette séance et aujourd'hui ;
  « N exercices » = ses exercices, ceux que le pré-remplissage reprendra (§ 3.1).

#### 15.8.4 Journal

> 🔄 D40 — **listes groupées** (DESIGN.md § 10.1) : « Depuis le début » est un groupe de stats sous
> son étiquette, chaque semaine un groupe titré (compteur à droite) ; contenu et règles inchangés.
>
> 🔄 D41 — **calendrier du mois** en tête (§ 15.15.1), sheet « Journée » (§ 15.15.2) ; le compteur de
> chaque semaine devient « 2/3 » ou « 3/3 · validée », la règle de la série de l'Accueil (§ 15.15.3).

Nouvel onglet, gabarit **hub** en liste paresseuse (l'historique peut compter des centaines de
séances) :

1. Barre de titre « Journal ».
2. **« Depuis le début »** (s'il y a une séance terminée) : rangée de stats « **48** séances »,
   « **312 t** volume » (§ 3.8, tonnes arrondies à l'entier), « **52 h** en salle » (total du § 3.7,
   arrondi à l'heure ; « 45 min » en dessous d'une heure) et « **1 h 05** par séance » (moyenne du
   § 3.7) ; ces deux dernières absentes si aucune séance n'est retenue par le § 3.7.
3. **Une section par semaine** ayant au moins une séance terminée, de la plus récente à la plus
   ancienne. Semaine = semaine ISO, **du lundi au dimanche**, au jour local de la séance (comme le
   § 3.6) : une semaine à cheval sur deux années reste une seule section. Titre : « Cette semaine »,
   « Semaine dernière », sinon « Semaine du 26 oct. » (son lundi), avec l'année quand ce lundi n'est
   pas dans l'année en cours (« Semaine du 29 déc. 2025 »). À droite : « 3 séances ». Une séance à
   la date illisible (§ 10.3) va dans une dernière section « Date inconnue ».
4. **Une ligne par séance**, de la plus récente à la plus ancienne :
   - titre : nom affiché (§ 3.1) ;
   - sous-titre : date courte locale, puis la durée si `durationMinutes` > 0 : « mer. 11 nov. ·
     1 h 12 » (« 45 min », « 1 h », « 1 h 05 » ; en anglais « 45 min », « 1 h », « 1 h 05 min ») ;
   - à droite : le volume (§ 3.8, séries validées, arrondi au kg, séparateur de milliers de la
     langue : « 6 840 kg ») ; volume nul (cardio, poids du corps) → le nombre de séries validées
     (« 12 séries ») ;
   - **toucher** → 🔄 D37 : **résumé** de la séance (§ 15.12) ; c'était l'éditeur (§ 2.9), qui
     reste dans l'appui long et dans le ⋮ du résumé ;
   - **appui long** → bottom sheet : titre = nom affiché, texte = « mer. 11 nov. · 18:30 » ;
     boutons secondaires « Modifier la séance » (→ éditeur), « Modifier la date », « Supprimer la
     séance » ; bouton texte « Fermer ». « Supprimer la séance » ouvre une confirmation en bottom
     sheet : « Supprimer la séance ? » / « {séance} du {date} sera retirée de ton historique. » /
     [Supprimer] (principal, en inverse, jamais rouge — DESIGN.md § 13) / [Annuler].
5. **Sans séance terminée** : état vide « Aucune séance » / « Tes séances terminées s'afficheront
   ici, semaine par semaine. » ; action « Démarrer une séance » (→ « Démarrer une séance », étape 1),
   ou « Reprendre la séance » si une séance est en cours.

**Modifier la date** : formulaire en bottom sheet (gabarit formulaire, DESIGN.md § 10.4) qui reprend
la modale du § 2.2 : titre « Modifier la date », nom affiché dessous ; champs Jour / Mois / Année puis
Heure : Min au clavier numérique du système (le champ en saisie en jaune), préremplis avec la date
locale de la séance ; mêmes règles (heure ou minute vide → 12 h / 00 ; date impossible, § 10.3 →
« Date ou heure invalide » et vibration d'erreur, la sheet reste ouverte) ; « Enregistrer »
(principal) et « Annuler ». L'historique est retrié (§ 2.2).

**Règles pures, testées en JVM** : regroupement par semaine (lundi, changement d'année, date
illisible), libellé de semaine, résumé d'une séance (durée affichée seulement si > 0, volume des
seules séries validées arrondi au kg, repli sur le nombre de séries), totaux.

#### 15.8.5 Logo

- **Dessin** : la variante « Deux disques » de la maquette `design/claude-design/T2-Logo.dc.html` (un grand et
  un petit disque de chaque côté, la barre qui dépasse),
  haltère **jaune `#FFC93D` sur graphite `#17181B`**, barres arrondies, dans le calque de 108 dp et
  sa zone sûre de 66 dp.
- **Icône adaptative** (`mipmap-anydpi/ic_launcher.xml`, aussi `roundIcon`) : fond = couleur
  graphite ; avant-plan = l'haltère en vecteur ; **couche monochrome** = le même haltère, d'une seule
  couleur, pour les icônes à thème (Android 13+). Les anciennes images de l'app Expo sont retirées.
- **Écran de démarrage** (Android 12+) : fond au jeton `background` du thème choisi (`#0F1012` /
  `#FFFFFF`, § 9.1) ; icône = l'avant-plan de l'icône posé sur un disque graphite
  (`windowSplashScreenIconBackgroundColor`), pour que le jaune reste lisible sur le blanc. Avant
  Android 12, pas d'icône (le fond seul, comme aujourd'hui) : pas de dépendance ajoutée.
- **Petite icône des notifications** : l'haltère en silhouette blanche ; couleur d'accent graphite
  `#17181B` (§ 7.1).
- **Variante preview** (`com.musclenote.android.preview`, ressources du source set `preview`) : le
  même logo avec une **marque jaune** — un bandeau jaune au bas de l'icône, sous l'haltère, dans
  l'avant-plan et dans la couche monochrome, visible quel que soit le masque du lanceur (et donc sur
  son écran de démarrage). La petite icône des notifications ne change pas.

#### 15.8.6 Textes (FR et EN)

Clés Android (`strings.xml`) ; les libellés existants sont repris (« Modifier la date », Jour / Mois
/ Année / Heure / Min, « Date ou heure invalide », « Mise à jour disponible », mode d'emploi du
transfert, « {n} sem. », pluriels séances / séries / exercices).

| clé | FR | EN |
|---|---|---|
| `tabs_journal` / `journal_title` | Journal | Journal |
| `common_see_all` | Tout voir | See all |
| `common_kg_value` | {{valeur}} kg | {{value}} kg |
| `mini_bar_rest` | repos | rest |
| `home_resume` | Reprendre | Resume |
| `home_live_label` | En cours | In progress |
| `home_rest_detail` | {{exercice}}, série {{n}} | {{exercise}}, set {{n}} |
| `home_next_caption` | Prochaine séance | Next workout |
| `home_next_new` | Nouvelle séance | New workout |
| `home_next_new_detail` | Choisis ta séance à l'étape suivante | Pick your workout in the next step |
| `home_next_last_today` / `_yesterday` | Dernière fois aujourd'hui / hier | Last time today / yesterday |
| `home_next_last_days` (pluriel) | Dernière fois il y a {{n}} jours | Last time {{n}} days ago |
| `home_next_detail` | {{dernière fois}} · {{exercices}} | {{last time}} · {{exercises}} |
| `home_next_other` | Autre… | Other… |
| `home_start` | Démarrer | Start |
| `home_update_row` | MuscleNote {{version}} | MuscleNote {{version}} |
| `home_update_installed` | Version installée : {{version}} | Installed version: {{version}} |
| `home_update_later` | Plus tard | Later |
| `home_migrate_section` | Depuis l'ancienne app | From the old app |
| `home_hide` | Masquer | Hide |
| `home_last_session` | Dernière séance | Last workout |
| `home_this_week` | Cette semaine | This week |
| `home_week_sessions_value` / `_label` | {{n}}/{{3}} / séances | {{n}}/{{3}} / workouts |
| `home_weeks_value` (pluriel) | {{n}} sem. | {{n}} wk / {{n}} wks |
| `home_streak_label` / `home_record_label` | série en cours / record | current streak / best |
| `home_week_bars_caption` | 8 dernières semaines · 3 séances = semaine validée | Last 8 weeks · 3 workouts = week done |
| `home_week_bars_description` | 8 dernières semaines : {{liste}}, puis {{n}} cette semaine | Last 8 weeks: {{list}}, then {{n}} this week |
| `journal_all_time` | Depuis le début | All time |
| `journal_stat_sessions` / `_volume` / `_gym_time` / `_average` | séances / volume / en salle / par séance | workouts / volume / at the gym / per workout |
| `journal_tonnes` | {{valeur}} t | {{value}} t |
| `journal_week_current` / `_previous` | Cette semaine / Semaine dernière | This week / Last week |
| `journal_week_of` | Semaine du {{date}} | Week of {{date}} |
| `journal_unknown_date` | Date inconnue | Unknown date |
| `journal_empty_title` | Aucune séance | No workouts yet |
| `journal_empty_message` | Tes séances terminées s'afficheront ici, semaine par semaine. | Your finished workouts will show up here, week by week. |
| `journal_empty_start` / `_resume` | Démarrer une séance / Reprendre la séance | Start a workout / Resume the workout |
| `duration_minutes` / `_hours` / `_hours_minutes` | {{m}} min / {{h}} h / {{h}} h {{mm}} | {{m}} min / {{h}} h / {{h}} h {{mm}} min |
| `duration_compact` (rangée de stats) | {{h}} h {{mm}} | {{h}}h {{mm}}m |
| `session_subtitle` | {{date}} · {{durée ou heure}} | {{date}} · {{duration or time}} |
| `session_volume_kg` | {{volume}} kg | {{volume}} kg |
| `session_long_press` (accessibilité) | Options de la séance | Workout options |
| `session_actions_edit` / `_delete` | Modifier la séance / Supprimer la séance | Edit workout / Delete workout |
| `session_delete_title` | Supprimer la séance ? | Delete this workout? |
| `session_delete_message` | {{séance}} du {{date}} sera retirée de ton historique. | {{workout}} on {{date}} will be removed from your history. |

Les clés de l'ancien Accueil devenues inutiles (`home_new_session`, `home_streak_*`, `home_stat_*`,
`home_gym_time`, `home_recent_sessions`, `home_empty_*`…) sont retirées.

### 15.9 Refonte « Studio épuré » : fiche exercice et Progrès (🔄 D34)

Quatrième étape de la refonte décrite dans `DESIGN.md` : l'historique d'un exercice (§ 2.8) devient
la **fiche exercice** (gabarit fiche, DESIGN.md § 10.2) et l'onglet **Progrès** (§ 2.10) passe au
gabarit hub (§ 10.1), avec tout ce qu'ils affichent aujourd'hui, refait avec les composants. La
courbe est le composant `TrendCurve` du design system (Canvas Compose, aucune bibliothèque), qui
remplace le graphique linéaire du § 2.13. **Hors périmètre** : la nouvelle fonctionnalité Records
(page Mes records, records battus) ; le record personnel et le 1RM estimé existants restent.
Nutrition, Paramètres, « Démarrer une séance », le sélecteur d'exercices et l'éditeur de séance
gardent leurs écrans actuels. Les règles du § 3 (sauf le § 3.10, § 15.9.3) et le **format des
données ne changent pas** (§ 4, § 5) : aucune migration.

#### 15.9.1 Fiche exercice

Remplace le § 2.8, même route (`exercise-history/{exerciseId}`), ouverte depuis le bouton « fiche » du
bloc exercice de la séance en cours (§ 15.7.1) et depuis Progrès (🔄 D43 : et depuis la sheet « Muscle »,
§ 15.17.7 ; dernière section « Muscles travaillés », § 15.17.5). Gabarit **fiche**, en liste
paresseuse (la liste des séances peut compter des centaines de lignes). Comme avant : séances
**terminées** où l'exercice a **au moins une série validée**, seules les séries validées comptent
(🔄 D15), la séance en cours n'en fait pas partie, exercices archivés compris (§ 3.9). La
**métrique** d'une séance (charge max, reps max au poids du corps, durée en cardio) et son unité
(« kg », « reps », « min ») suivent le § 15.9.3.

1. **Barre** : ← (retour). Exercice introuvable → « Exercice introuvable » et Retour, comme avant.
2. **Titre** : nom affiché ; dessous « Pecs · 19 séances depuis févr. 2026 » (groupe, nombre de
   séances, mois de la première).
3. **Chiffre héros** (pas de contrôle segmenté : une seule métrique) :
   - **au repos** : légende « Dernière séance · jeu. 5 nov. » ; valeur = métrique de la **dernière
     séance de la période** (2 décimales au plus, `82,5`) et son unité ; **variation** = dernière
     séance − **premier point de la période**, au dixième, toujours signée (« +7,5 kg », « −2,5 kg »,
     « +3 reps ») ou « identique » si nulle, suivie de « depuis le 14 août » (date du premier point).
     **Vert** si hausse, **rouge** si baisse, **gris** si égale (DESIGN.md § 1). Une seule séance dans
     la période : pas de variation ;
   - **au toucher de la courbe** : légende = date du point (« jeu. 22 oct. ») ; valeur = métrique de
     cette séance ; variation = point − premier point de la période (même contexte). Lâcher revient
     au repos ;
   - **période sans séance** : valeur « — », légende « Aucune séance sur cette période », ni unité
     ni variation ;
   - le chiffre **défile en 250 ms** (`numberChange`, DESIGN.md § 7) quand la période change ; au
     toucher, il suit le doigt sans défiler.
4. **Courbe** (`TrendCurve`, 172 dp) : la métrique de chaque séance de la période, de la plus ancienne
   à gauche à la plus récente, l'abscisse proportionnelle à la date ; réduite au-delà de 120 points
   (§ 15.9.3) ; dates du premier et du dernier point sous l'axe (« 14 août », « 5 nov. » ; l'année
   quand elle n'est pas l'année en cours : « 12 févr. 2025 »). Un seul point : le point seul, à droite.
   Période vide : « Choisis une période plus longue. » au centre.
   - **Glisser le doigt** sur la courbe sélectionne le point le plus proche (§ 15.9.3) : trait
     jusqu'au point, la suite en gris, guide vertical, date du point sous l'axe, et le héros affiche
     sa valeur et sa variation (DESIGN.md § 9.1). La courbe garde le geste : elle ne fait pas
     défiler la fiche.
   - **TalkBack** lit la période, la valeur de départ, celle d'arrivée et la variation : « Charge max
     par séance, 3 mois : de 92,5 kg le 14 août à 100 kg le 5 nov., +7,5 kg » ; « …, 1 mois : 95 kg
     le 5 nov. » ; « … : aucune séance ».
5. **Sélecteur de période** : 1M · 3M · 6M · 1A · Tout, **Tout** par défaut (maquette), lus « 1 mois »,
   « 3 mois », « 6 mois », « 1 an », « Tout l'historique » par TalkBack. Le choix est gardé à la
   rotation, pas d'une ouverture à l'autre. Période = séances dont le **jour local** est postérieur
   ou égal à aujourd'hui moins 1, 3, 6 ou 12 mois calendaires (le 14 nov., « 1M » part du 14 oct.) ;
   « Tout » = toutes.
6. **Rangée de stats** (sur tout l'historique, comme les cartes de record du § 2.8) : « 100 kg »
   record personnel (meilleure métrique : charge max, « 15 reps » au poids du corps ; en cardio
   « 60 min » séance la plus longue) et « 112 kg » 1RM estimé (meilleur 1RM Epley des séries
   validées, § 3.5, seulement s'il est > 0 ; jamais en cardio).
7. **Section « Séances »**, compteur à droite : **toutes** les séances de l'exercice (pas seulement
   celles de la période), la plus récente en haut (🔄 D15) :
   - titre : date « jeu. 5 nov. » (avec l'année si ce n'est pas l'année en cours) ;
   - sous-titre : « Meilleure série 80 × 8 · 1RM ~101 kg » — la série validée la plus lourde, à
     égalité celle qui a le plus de reps puis la première ; « 12 reps » à 0 kg ; « 1RM ~ » = meilleur
     1RM de la séance, omis s'il vaut 0 ; cardio : la série la plus longue, à égalité la plus longue
     distance (« 30 min · 5,2 km », « 30 min » sans distance), sans 1RM ;
   - à droite : le **volume** (§ 3.8) « 1 920 kg » ; volume nul (poids du corps) → « 3 séries » ;
     cardio → la durée totale « 45 min » ; dessous, **« Record »** en vert sur la séance qui détient
     le **record de la période** choisie (meilleure métrique de la période, la plus ancienne à
     égalité) : la marque se déplace avec la période ;
   - toucher → éditeur de la séance (§ 2.9).
8. **Aucune séance** : sous le titre, état vide « Aucune séance » / « Valide une série de cet exercice
   pour suivre sa progression. », action « Retour ».

Le poids de corps et les puces de chaque série d'une séance (§ 2.8) restent dans l'éditeur, ouvert
en touchant la ligne ; « Max X kg » est la charge de la meilleure série ; le message « Fais encore
des séances… » est remplacé par le point seul.

#### 15.9.2 Progrès

Remplace la disposition du § 2.10 (onglets en puces colorées, cartes). Gabarit **hub** en liste
paresseuse, de haut en bas :

1. **Barre** : « Progrès » (le nom de l'onglet ; « Progression » est retiré).
2. **Surface « Poids de corps »** (surface « Mesure », la seule de l'écran) :
   - légende « Poids de corps · 12 nov. » (date du dernier point, « aujourd'hui », « hier ») et le
     bouton + « Ajouter une pesée » → formulaire « Nouvelle pesée » (ci-dessous) ; 🔄 D38 : avant lui,
     le bouton `notifications` « Rappel de pesée » → sheet du rappel (§ 15.13.3) ;
   - chiffre héros = **Actuel** (dernier point, § 3.10) et « kg » ; variation = **Évolution**
     (dernier − premier, au dixième) « +10,4 kg depuis le 12 févr. », **en gris** : sans objectif de
     poids déclaré, ni une hausse ni une baisse n'est un progrès (DESIGN.md § 1 ; 🔄 l'app colorait la
     hausse en rouge et la baisse en vert) ; pas de variation avec un seul point ;
   - courbe compacte (48 dp) de tous les points, réduite au-delà de 120 ; dessous, « 42 jours » à
     gauche et **Min–Max** « 68 – 78,4 kg » à droite ;
   - sans aucun point (ni pesée ni poids de séance) : légende « Poids de corps », bouton +, et la
     phrase « Aucun poids enregistré. Ajoute une pesée, ou ton poids en démarrant une séance. »
3. **Section « Exercices »** :
   - **puces de filtre**, défilantes, une par **groupe musculaire ayant au moins une séance** (ordre
     du § 6.2 ; 🔄 les groupes sans séance ne sont plus affichés, ils l'étaient à 50 %) ; par défaut
     le groupe qui a le plus de séances (égalité : ordre du § 6.2) ; le choix est gardé d'une visite
     à l'autre de l'onglet ;
   - **rangée de stats** du groupe (§ 3.10) : « 12 » séances, « 102,5 kg » charge max (cardio
     « 45 min » durée max ; « — » si aucun exercice du groupe n'a de charge), « 4,2 t » vol. max
     (cardio « 12,4 km » distance max) ;
   - **une ligne par exercice** du groupe utilisé au moins une fois, **la dernière séance la plus
     récente en haut** (égalité : le plus de séances, puis l'ordre du catalogue et des perso) :
     « Exercices les plus utilisés » et « Tous les exercices » ne font plus qu'une liste, sans rang ni
     couleur (DESIGN.md § 1). Titre : nom ; sous-titre : « 19 séances · record 100 kg » ; à droite :
     la **mini-courbe** de la métrique sur tout l'historique (réduite à 24 points ; absente avec une
     seule séance), puis la métrique de la **dernière séance** « 80 kg » (« 12 reps », « 45 min ») et
     dessous la **variation** dernière − première séance (« +17,5 kg », « −5 kg », « identique » ;
     verte, rouge, grise ; absente avec une seule séance) : la règle de la fiche en période « Tout ».
     Toucher → **fiche exercice** ;
   - aucune séance terminée : état vide « Aucune séance encore » / « Lance ta première séance pour
     voir tes progrès ici », action « Démarrer une séance » (ou « Reprendre la séance » si une séance
     est en cours).
4. **Section « Calories et eau »** (le contenu des anciens onglets Calories et Eau, § 2.10 et § 3.10) :
   - puces de filtre « Calories » / « Eau » (Calories par défaut, choix gardé) ;
   - rangée de stats : « 1 850 » aujourd'hui, « 2 120 » moy. 7 jours (« — » sans jour saisi dans la
     fenêtre), « 12 » jours objectif (objectif du jour concerné) ; l'eau en litres au dixième ;
   - **courbe** (150 dp) des jours saisis s'il y en a au moins 2, réduite au-delà de 120 points,
     dates du premier et du dernier jour, et une **ligne d'objectif** pointillée au niveau de
     l'objectif actuel, marquée « Objectif » ; elle ne se touche pas (pas de chiffre héros dans un
     hub) ; TalkBack : « Calories, 42 jours : de 2 100 kcal le 3 oct. à 1 850 kcal le 14 nov.,
     −250 kcal » ;
   - lignes **Objectif quotidien** (actuel), **Meilleure journée**, **Jours suivis** ;
   - aucun jour saisi : état vide « Aucune donnée » et l'indication actuelle (« Commence à saisir tes
     calories dans l'onglet Nutrition… »), action « Aller à Nutrition » (→ onglet Nutrition).
5. **Section « Pesées »** (l'ancien « Journal de poids »), compteur à droite : les pesées manuelles,
   la plus récente en haut — date « 12 nov. 2026 », poids « 78,4 kg » à droite. **Toucher** une pesée
   ouvre la confirmation en bottom sheet « Supprimer la pesée ? » / « La pesée du 12 nov. 2026
   (78,4 kg) sera retirée. » / [Supprimer] (principal, en inverse) / [Annuler] (🔄 la poubelle
   supprimait sans confirmation, DESIGN.md § 13). Sans pesée : la phrase actuelle (« Aucune pesée
   manuelle enregistrée… »).

**Nouvelle pesée** : formulaire en bottom sheet au **gabarit formulaire** (DESIGN.md § 10.4), avec le
pavé numérique :
- titre « Nouvelle pesée » ; phrase d'aide « Dernier poids : 78,4 kg, le 12 nov. » (dernier point du
  poids de corps), sinon « Ton poids, en kg. » ;
- **champ héros** « 78,4 kg », prérempli avec le dernier poids (vide sans poids) et **neuf** : la
  première touche le remplace ;
- **puces de saisie rapide** « Aujourd'hui » / « Hier » (celle qui correspond à la date choisie est
  sélectionnée ; aujourd'hui au départ) ;
- **ligne d'information** « Date » « sam. 14 nov. » avec chevron : ouvre le sélecteur de date du
  système (Material 3, aux couleurs du thème), pour une autre date ;
- **pavé numérique** sans en-tête : règles de saisie du § 15.7.4 pour une charge (6 caractères,
  2 décimales, séparateur de la langue), pas **−0,1** / **+0,1**, touche principale « ✓ Ajouter ».
- « Ajouter » : `0 < poids ≤ 500` (§ 3.11) → enregistrée à **midi local** du jour choisi, elle
  remplace la pesée du même jour, la sheet se ferme ; sinon la phrase d'aide devient « Vérifie le
  poids et la date » avec une vibration d'erreur, et la sheet reste ouverte. Retour ou voile :
  fermée sans rien enregistrer.

#### 15.9.3 Règles (pures, testées en JVM)

- **Métrique d'une séance** d'un exercice (🔄 D34, remplace celle du § 3.10) :
  - **cardio** : durée totale (min) ;
  - **force, avec lest** — au moins une série validée de l'historique de l'exercice a une charge
    > 0 : **charge max** des séries validées de la séance (kg, la charge saisie, donc le lest ajouté
    pour un exercice au poids du corps ; une séance sans lest compte 0 kg) ;
  - **poids du corps** — toutes les séries validées de l'historique sont à 0 kg : **reps max** d'une
    série de la séance (« reps »).
- **Série d'une période** : les points (séances, ou jours) de la période (§ 15.9.1, point 5), dans
  l'ordre chronologique ; vide si aucun.
- **Résumé d'une série** : aucun point → rien (héros « — ») ; sinon **dernier** point (valeur au
  repos), **départ** (premier point) et **variation** = dernier − départ, au dixième (demi vers +∞,
  § 3.5) ; un seul point → pas de variation ; **record** = point de valeur max (le plus ancien à
  égalité). Variation d'un point touché = point − départ. Sens : > 0 hausse, < 0 baisse, 0 (au
  dixième) égale — jamais « +0,0 » ni « −0,0 ».
- **Point le plus proche du doigt** : chaque point est placé à une abscisse proportionnelle à sa date
  entre les marges de la courbe ; le point choisi est celui dont l'abscisse est la plus proche de
  celle du doigt (à égalité, le plus ancien) ; doigt avant le premier point → le premier, après le
  dernier → le dernier ; courbe vide → aucun.
- **Réduction** au-delà de `max` points (120 pour une courbe de fiche ou de hub, 24 pour une
  mini-courbe) : on garde le premier et le dernier point ; l'intervalle de dates est coupé en
  `(max − 2) / 2` tranches égales, et chaque tranche non vide garde son point le plus bas et son
  point le plus haut (le plus ancien à égalité ; un seul s'ils sont confondus), dans l'ordre
  chronologique. Résultat : au plus `max` points, toujours le record, la forme de la courbe
  conservée (creux compris). Le résumé et le chiffre héros se calculent sur la série complète ; le
  toucher choisit parmi les points dessinés.
- **Progrès, groupes** (§ 3.10 modifié) : « Charge max » = max des records **en kg** de tous les
  exercices du groupe (🔄 : du top 3, qui disparaît ; les exercices au poids du corps, comptés en
  reps, n'y entrent pas) ; « Durée max » en cardio = max des records ; exercices triés par date de
  dernière séance décroissante (égalité : nombre de séances décroissant, puis ordre du catalogue et
  des perso). Séances et vol. max ne changent pas.
- **Saisie de la pesée** : celle du § 15.7.4 (champ neuf, séparateur, 2 décimales, 6 caractères,
  pas arrondi au centième, jamais sous 0), avec un pas de 0,1 kg.

#### 15.9.4 Textes (FR et EN)

Clés Android (`strings.xml`) ; les libellés existants sont repris (« Exercice introuvable », « Retour »,
« Nouvelle pesée », « Ajouter une pesée », « Vérifie le poids et la date », « Aucune pesée manuelle enregistrée… », « Aucune séance
encore », « Lance ta première séance… », « Aucune donnée » et ses indications, « Objectif
quotidien », « Meilleure journée », « Jours suivis », « Calories », « Eau », « Date », les noms de
groupes, les pluriels séances / séries / jours, `common_kg_value`, `session_volume_kg`,
`journal_tonnes`, `workout_previous_strength` / `_reps`).

| clé | FR | EN |
|---|---|---|
| `exercise_fiche_subtitle` | {{groupe}} · {{séances}} depuis {{mois}} | {{group}} · {{sessions}} since {{month}} |
| `exercise_fiche_last_session` | Dernière séance · {{date}} | Last session · {{date}} |
| `exercise_fiche_sessions` | Séances | Sessions |
| `exercise_fiche_period_empty` | Aucune séance sur cette période | No sessions in this period |
| `exercise_fiche_period_empty_hint` | Choisis une période plus longue. | Pick a longer period. |
| `exercise_fiche_stat_record` / `_stat_longest` / `_stat_one_rm` | record personnel / séance la plus longue / 1RM estimé | personal best / longest session / estimated 1RM |
| `exercise_fiche_best_set` | Meilleure série {{série}} | Best set {{set}} |
| `exercise_fiche_one_rm` | 1RM ~{{valeur}} kg | 1RM ~{{value}} kg |
| `exercise_fiche_cardio_set` / `_minutes` | {{min}} min · {{km}} km / {{min}} min | {{min}} min · {{km}} km / {{min}} min |
| `exercise_fiche_record` | Record | Record |
| `exercise_fiche_empty_title` / `_message` | Aucune séance / Valide une série de cet exercice pour suivre sa progression. | No sessions yet / Complete a set of this exercise to track its progress. |
| `delta_since` | depuis le {{date}} | since {{date}} |
| `delta_same` | identique | unchanged |
| `period_1m` … `period_all` | 1M / 3M / 6M / 1A / Tout | 1M / 3M / 6M / 1Y / All |
| `period_1m_description` … `_all_description` | 1 mois / 3 mois / 6 mois / 1 an / Tout l'historique | 1 month / 3 months / 6 months / 1 year / All time |
| `curve_metric_load` / `_reps` / `_duration` | Charge max par séance / Reps max par séance / Durée par séance | Top weight per session / Top reps per session / Duration per session |
| `curve_description_range` | {{courbe}}, {{période}} : de {{départ}} le {{date}} à {{arrivée}} le {{date}}, {{variation}} | {{curve}}, {{period}}: from {{start}} on {{date}} to {{end}} on {{date}}, {{change}} |
| `curve_description_single` / `_empty` | {{courbe}}, {{période}} : {{valeur}} le {{date}} / {{courbe}}, {{période}} : aucune séance | {{curve}}, {{period}}: {{value}} on {{date}} / {{curve}}, {{period}}: no sessions |
| `curve_goal` | Objectif | Goal |
| `common_min_value` / `_reps_value` / `_km_value` / `_liters_value` / `_kcal_value` | {{valeur}} min / reps / km / L / kcal | {{value}} min / reps / km / L / kcal |
| `progress_weight_title` | Poids de corps | Body weight |
| `progress_weight_caption` | Poids de corps · {{date}} | Body weight · {{date}} |
| `common_today_lower` / `common_yesterday_lower` | aujourd'hui / hier | today / yesterday |
| `progress_weight_range` | {{min}} – {{max}} kg | {{min}} – {{max}} kg |
| `progress_weight_none` | Aucun poids enregistré. Ajoute une pesée, ou ton poids en démarrant une séance. | No weight yet. Add a weigh-in, or enter your weight when you start a workout. |
| `progress_exercises` | Exercices | Exercises |
| `progress_exercise_subtitle` | {{séances}} · record {{valeur}} | {{sessions}} · best {{value}} |
| `progress_group_sessions` / `_max_load` / `_max_duration` / `_max_volume` / `_max_distance` | séances / charge max / durée max / vol. max / distance max | workouts / top weight / longest / top volume / longest distance |
| `progress_nutrition` | Calories et eau | Calories and water |
| `progress_daily_today` / `_today_liters` / `_avg7` / `_goal_days` | aujourd'hui / aujourd'hui, L / moy. 7 jours / jours objectif | today / today, L / 7-day avg. / goal days |
| `progress_daily_open_nutrition` | Aller à Nutrition | Go to Nutrition |
| `progress_weigh_ins` | Pesées | Weigh-ins |
| `progress_weigh_in_delete` (accessibilité) | Supprimer la pesée | Delete weigh-in |
| `progress_weigh_in_delete_title` | Supprimer la pesée ? | Delete this weigh-in? |
| `progress_weigh_in_delete_message` | La pesée du {{date}} ({{poids}} kg) sera retirée. | The {{date}} weigh-in ({{weight}} kg) will be removed. |
| `weigh_in_help_last` / `_help_none` | Dernier poids : {{poids}} kg, le {{date}} / Ton poids, en kg. | Last weight: {{weight}} kg, on {{date}} / Your weight, in kg. |
| `weigh_in_today` / `_yesterday` | Aujourd'hui / Hier | Today / Yesterday |
| `weigh_in_add` | Ajouter | Add |

Les clés devenues inutiles (`progress_title`, `progress_tab_weight`, `progress_weight_current` /
`_change` / `_min_max` / `_chart_title`, `progress_stat_*`, `progress_cal_chart_*`,
`progress_water_chart_*`, `progress_recap_title`, `progress_muscle_*`, `body_weight_log_title`,
`body_weight_weight_label` / `_placeholder`, `exercise_history_*` sauf `exercise_history_not_found`…)
sont retirées.

### 15.10 Refonte « Studio épuré » : Nutrition et Paramètres (🔄 D35)

Étape 5a de la refonte décrite dans `DESIGN.md` : l'onglet **Nutrition** (§ 2.11) passe au gabarit
hub (DESIGN.md § 10.1) avec **toutes ses fonctions actuelles**, rappels compris, et ses saisies au
gabarit formulaire (§ 10.4) ; **Paramètres** (§ 2.12) devient une liste groupée (hub de second
niveau) dont chaque réglage s'ouvre en formulaire, avec les mêmes réglages qu'aujourd'hui. Seul
changement de réglage : le **thème** propose Sombre, Clair, Système, **Sombre par défaut**
(§ 15.10.4). **Hors périmètre** : objectif de protéines et stock des compléments (fonctionnalités
prévues après ; 🔄 D42, § 15.16), section « Tendances » de Nutrition (les courbes de calories et d'eau restent dans
Progrès, § 15.9.2). Export, sauvegarde automatique, Health Connect et envoi d'avis gardent leurs
règles (§ 5, § 8, § 14, § 15.2, § 15.3) ; les notifications (§ 7) ne changent pas. Le **format des
données ne change pas** (§ 4, § 5), sauf la valeur par défaut du thème : aucune migration.

#### 15.10.1 Nutrition

> 🔄 D40 — **listes groupées** (DESIGN.md § 10.1) : « Aujourd'hui » est le groupe du héros,
> « Compléments » un groupe titré (« Nouveau complément » et l'info dessous) ; contenu inchangé.
>
> 🔄 D42 — le groupe du héros prend la forme de la maquette (§ 15.16.2) : bouton + des calories à côté
> de la légende, puis les lignes **« Eau »** et **« Protéines »**, chacune avec son bouton + ; plus de
> rangée de stats ni de boutons « Calories » / « Eau » ; « 1 sur 3 pris » passe à droite de
> l'étiquette « Compléments ». Chaque complément montre ses **jours de stock** avant sa case
> (§ 15.16.4) ; le groupe **« Moyenne sur 7 jours »** suit les compléments (§ 15.16.6).

Remplace la disposition du § 2.11. Gabarit **hub**, de haut en bas :

1. **Barre** : « Nutrition ».
2. **Surface « Aujourd'hui »** (la seule de l'écran) — ce qui compte aujourd'hui : les calories en
   grand, l'eau et les compléments en résumé, les deux saisies du jour sous le pouce.
   - légende « Aujourd'hui » ;
   - **chiffre héros** = kcal du jour et « kcal » (défile quand il change, DESIGN.md § 7) ;
   - dessous, l'**état par rapport à l'objectif du jour** (`goalFor(aujourd'hui)`, § 3.12), selon le
     ratio non plafonné du § 2.11 (🔄 D15), toujours en mots :
     - ratio < 0,9 : « encore 950 kcal » (gris) suivi de « sur 2 800 » ; à 0 kcal, « objectif
       2 800 kcal » seul ;
     - 0,9 ≤ ratio ≤ 1,05 : « dans l'objectif » (**vert**) suivi de « · 2 800 kcal » ;
     - ratio > 1,05 : « +320 kcal » (**rouge**) suivi de « au-dessus de 2 800 » ;
   - **piste de progression** (3 dp, `surfacePressed`, remplie en `textPrimary`) : kcal / objectif,
     plafonnée à 100 % ;
   - **rangée de stats** : l'eau du jour « 750 ml » (dès 1 000 ml « 1,5 L », au dixième) avec
     « eau · sur 2,0 L », ou « eau · objectif atteint » une fois l'objectif d'eau atteint ; et, s'il
     y a au moins un complément, « 1/3 » avec « compléments pris » ;
   - deux **boutons secondaires** moyens, largeurs égales, icône `add` : « Calories » et « Eau » →
     formulaires du § 15.10.2.
3. **Section « Compléments »** (sans compteur : les compléments pris sont sur la surface) :
   - **une ligne par complément**, dans l'ordre de création : titre = nom ; sous-titre = « 5 g par
     prise » (omis si le dosage est vide, 🔄 D15) puis le résumé des rappels (« Pas de rappel »,
     « Rappels activés », « Rappel à 08:00 », « 3 rappels », § 2.11), séparés par « · » ; à droite
     la **case « pris aujourd'hui »** (§ 3.12) : la toucher la coche ou la décoche (vibration) ;
     toucher **le reste de la ligne** ouvre la sheet du complément (§ 15.10.2) ;
   - sous les lignes, bouton secondaire pleine largeur « Nouveau complément » (icône `add`) →
     formulaire du § 15.10.2 ;
   - sans complément : état vide (pastille `pill`) « Aucun complément » / « Ajoute tes compléments
     alimentaires et configure des rappels pour ne jamais en oublier », action « Ajouter un
     complément » ;
   - si au moins un complément a ses rappels activés : la phrase `nutrition.infoMobile` en légende
     sous la section.
4. Mini-barre de la séance en cours et barre d'onglets (§ 15.8.1), sans changement ; le toucher d'un
   rappel ouvre toujours cet onglet.

#### 15.10.2 Formulaires de Nutrition

Tous en bottom sheet (gabarit formulaire, DESIGN.md § 10.4) ; retour ou voile : fermée sans rien
enregistrer, sauf la sheet d'un complément dont chaque changement s'applique tout de suite (comme
la carte du § 2.11). Pavé numérique : règles du § 15.10.5.

**Calories** (bouton « Calories ») :
- titre « Calories » ; phrase d'aide « Aujourd'hui : 1 850 kcal sur 2 800. » ;
- puces « Ajouter » (choisie à l'ouverture) / « Total du jour » (= « Définir » du § 2.11) ;
- **champ héros** en kcal : en « Ajouter », vide ; en « Total du jour », les kcal du jour
  (champ neuf : la première touche les remplace) ;
- lignes d'information : « Nouveau total » « 2 200 kcal » (en « Ajouter » seulement, mis à jour à
  chaque touche) ; « Objectif quotidien » « 2 800 kcal » avec chevron → formulaire d'objectif ;
- pavé : pas **−50** / **+50**, touche principale « ✓ Ajouter » ou « ✓ Enregistrer » ;
- « Ajouter » : valeur > 0 → `kcal_du_jour += v` ; « Total du jour » : champ non vide → `kcal_du_jour
  = v` (0 remet le jour à zéro, l'ancien ⟲) ; résultat borné à ≥ 0 (§ 3.12). Puis la sheet se ferme
  (vibration de succès). Champ vide, ou 0 en « Ajouter » : la phrase d'aide devient « Saisis une
  quantité » avec une vibration d'erreur, la sheet reste ouverte.

**Eau** (bouton « Eau ») : même formulaire en **ml** — titre « Eau », aide « Aujourd'hui : 750 ml
sur 2,0 L. », puces « Ajouter » / « Total du jour », « Nouveau total » « 1,0 L », « Objectif
quotidien » « 2,0 L » → objectif d'eau ; pas **−250** / **+250** (un verre). « Total du jour »
remplace l'ancien ⟲ de l'eau (0 = remise à zéro) et permet de corriger le total (🔄 nouvelle règle
`setWater`, § 15.10.5).

**Objectif de calories / d'eau** (ligne « Objectif quotidien ») : la même sheet passe à ce
formulaire — titre « Objectif de calories » ou « Objectif d'eau », aide « À partir d'aujourd'hui ;
les jours passés gardent le leur. » ; champ héros prérempli avec l'objectif du jour (neuf), en kcal
ou en **litres** ; pas ±100 kcal ou ±0,25 L ; « ✓ Enregistrer » : valeur > 0 → objectif à partir
d'aujourd'hui (§ 3.12, litres stockés en ml `round(v × 1000)`), retour au formulaire de saisie ;
sinon « Saisis un objectif supérieur à 0 » et vibration d'erreur. Retour : le formulaire de saisie.

**Nouveau complément** (le formulaire long, DESIGN.md § 13) — une sheet défilante, au clavier du
système puisque le nom est du texte :
- titre « Nouveau complément » ;
- « Nom » : `StudioTextField` d'une ligne, focus automatique, majuscule en début de phrase,
  invite « Ex : Créatine, Whey, Oméga-3... », 40 caractères ;
- « Dosage par prise » : `StudioTextField` numérique d'une ligne (clavier décimal), **texte libre
  gardé tel que saisi** (§ 2.11), 10 caractères ; dessous, les **unités** en puces de filtre `g`
  (par défaut), `mg`, `ml`, `caps`, `cp`, `dose`, `ui` ;
- ligne à cocher « Rappels » (cochée au départ) ; cochée : une ligne d'information par rappel
  « Rappel » « 08:00 » avec chevron → choix de l'heure, et le bouton texte « Ajouter un rappel »
  (icône `add` ; heure du dernier + 2 h, § 2.11) ; recochée sans rappel → 08:00 ;
- bouton principal « Enregistrer », actif dès que le nom (trim) n'est pas vide : crée le complément
  (§ 3.12, sans rappels si la case est décochée ; demande `POST_NOTIFICATIONS` si des rappels sont
  actifs) et ferme la sheet.

**Sheet d'un complément** (toucher sa ligne) :
- titre = le nom ; phrase d'aide « 5 g par prise » (absente sans dosage) ;
- ligne à cocher « Rappels » (`reminderEnabled`) ; l'activer demande la permission si nécessaire ;
- une ligne d'information par rappel (« Rappel » « 08:00 », chevron → choix de l'heure) et « Ajouter
  un rappel », comme dans le formulaire ; affichées même rappels désactivés (ils sont conservés,
  § 4.1), comme la liste dépliée du § 2.11 ;
- bouton secondaire « Supprimer le complément » → confirmation en bottom sheet « Supprimer
  « Créatine » ? » / « Le complément et ses rappels seront retirés. » / [Supprimer] (principal, en
  inverse) / [Annuler] (🔄 remplace la confirmation en ligne) ;
- bouton texte « Fermer ».

**Choix de l'heure** (🔄 D7 inchangé) : le `TimePicker` Material 3 en dialogue, aux couleurs de
`StudioTheme`, « Heure du rappel », 24 h ou 12 h selon le système, toute minute ; boutons texte
« Annuler » et « OK », et « Supprimer » (à gauche) s'il y a plus d'un rappel (🔄 remplace le ✕ de la
ligne).

#### 15.10.3 Paramètres

> 🔄 D40 — **listes groupées** (DESIGN.md § 10.1) : « Mes données » en groupe de stats, 32 sous le
> titre, puis chaque section en groupe titré ; l'info de « Données » sous son groupe ; réglages,
> lignes et sheets inchangés.

Remplace la disposition du § 2.12. **Hub de second niveau** (DESIGN.md § 10.1), ouvert par
l'engrenage de l'Accueil, barre d'onglets gardée (Accueil sélectionné, § 15.8.1). Une liste groupée
de lignes (`ListRow`) : chacune montre la valeur ou l'état du réglage et ouvre un **formulaire** en
bottom sheet, ou lance l'action. De haut en bas :

1. **Barre** : ← (retour à l'Accueil) puis le titre « Paramètres ».
2. **Mes données** (rangée de stats, sans surface) : « 124 » séances terminées, « 6 » exercices
   perso **non archivés** (§ 2.12, point 1).
3. **Section « Préférences »** :
   - **Langue** — valeur « Langue du système », « Français » ou « English » (sans drapeaux) → sheet
     « Langue », aide « Langue du système : celle du téléphone. », contrôle segmenté « Système ·
     Français · English » ; le toucher applique le choix (🔄 D9, `setApplicationLocales`) et ferme
     la sheet ;
   - **Thème** — valeur « Sombre », « Clair » ou « Système » → sheet « Thème », aide « Sombre par
     défaut. Système suit le réglage du téléphone. », contrôle segmenté « Sombre · Clair ·
     Système » ; appliqué tout de suite (§ 15.10.4), sheet fermée ;
   - **Minuteur de repos** — valeur « 3 min » → sheet « Minuteur de repos », aide « Durée par défaut
     du repos entre les séries », puces de filtre 1 min … 5 min (§ 2.12) ; appliqué, sheet fermée ;
   - **Notifications désactivées** (seulement si `POST_NOTIFICATIONS` est refusée ou les
     notifications de l'app coupées, revérifié au retour sur l'écran, 🔄 D6) — sous-titre
     « Minuteur et rappels silencieux », chevron → réglages de notification de l'app. 🔄 D39 :
     la ligne passe en tête de la section « Notifications » (§ 15.14.5).
4. **Section « Rappels »** (🔄 D38, § 15.13.3) : « Rappel de pesée » → sheet du rappel, puis une ligne
   par complément → sa sheet de Nutrition (§ 15.10.2). 🔄 D39 : renommée **« Notifications »**, elle
   gagne la ligne « Nouvelle version disponible » (§ 15.14.5).
5. **Section « Données »** :
   - **Sauvegarde automatique** (🔄 D28, § 15.2) — valeur « Activée » / « Désactivée » ; sous-titre :
     « Dernière le 14 nov. 2026, 08:12 », « Aucune sauvegarde pour l'instant », « Dossier
     inaccessible » ou « Échec le … », désactivée « Chaque jour, dans le dossier de ton choix » →
     sheet « Sauvegarde automatique » : aide (`settings_auto_backup_desc`) ; ligne à cocher
     « Sauvegarde quotidienne » (cocher sans dossier ouvre le sélecteur, § 15.2) ; activée, lignes
     d'information « Dossier » (nom, chevron → sélecteur) et « Dernière sauvegarde » ; l'échec en
     phrase (dossier inaccessible, échec d'écriture daté) ; bouton principal « Sauvegarder
     maintenant » (« Sauvegarde… » et désactivé pendant l'écriture) ;
   - **Exporter mes données** — sous-titre « Fichier JSON de toutes tes données » → JSON v2 et
     feuille de partage (§ 5.6) ;
   - **Exporter en CSV** (🔄 D1) — « Tableaux pour Excel ou Sheets » → § 8 ;
   - **Importer des données** — « Depuis un fichier exporté » → sélecteur de fichiers puis
     confirmation en bottom sheet « Écraser les données actuelles ? » / « Tes N séances actuelles
     seront remplacées… » / [Confirmer] / [Annuler] (§ 5.4) ; un fichier partagé vers l'app ouvre
     Paramètres sur cette confirmation (§ 15.8.1) ;
   - pendant une opération, le sous-titre de la ligne devient « En cours… » et les trois lignes
     sont inactives ; le message de succès ou d'erreur (`settings.export.*` / `settings.import.*`)
     s'affiche en phrase sous les lignes ; dessous, l'info `settings.info` en légende ;
   - **Health Connect** (🔄 D29, seulement si disponible, § 15.3) — valeur « Activé » / « Désactivé » ;
     sous-titre « Dernier envoi le … », « Autorisation manquante », ou désactivé « Séances et poids
     vers tes apps de santé » → sheet « Health Connect » : aide (`health_desc`) ; ligne à cocher
     « Envoyer à Health Connect » (cocher demande l'autorisation, refus → « Autorisation refusée… ») ;
     activé : autorisations manquantes en phrase avec le bouton secondaire « Autoriser », lignes
     d'information « Dernier envoi » et « Écrits » (« 12 séances · 30 poids »), dernière erreur
     datée ; bouton principal « Envoyer l'historique » (« Envoi… » pendant l'envoi) et son résultat
     en phrase. La section « SANTÉ » disparaît : la ligne rejoint « Données ».
6. **Section « À propos »** (remplace « APPLICATION » et « TON AVIS ») :
   - **Mises à jour** (🔄 D25, § 3.17 ; 🔄 D39 : **remplacée** par les lignes « Version … »,
     « Vérifier maintenant » et « Télécharger la … » / « Ouvrir Obtainium » du § 15.14.5) — valeur =
     version installée ; sous-titre « Gérées par
     Obtainium » (ligne inactive), « Pas encore vérifié », « À jour · vérifié le 14 nov., 08:12 »,
     « La 1.0.43 est disponible », « Vérification impossible : pas de connexion ? » après un échec,
     « Vérification… » pendant la requête ; toucher → [Vérifier] ; une mise à jour disponible ajoute
     la ligne **« Télécharger la 1.0.43 »** (chevron) → APK dans le navigateur ;
   - **Rapport de plantage** (🔄 D17, seulement si un rapport existe, § 10.9) — « Dernier plantage le
     … » → sheet : aide, bouton principal « Partager », bouton secondaire « Supprimer » (tous les
     rapports), bouton texte « Fermer » ;
   - **Envoyer un avis** (🔄 D26, seulement si l'URL du webhook a été fournie au build, § 14.5) —
     « Une idée, un bug ? » → sheet « Ton avis » : le formulaire du § 14.1 refait avec les
     composants — puces de filtre Idée / Bug / Autre, message en `StudioTextField` multiligne
     (4 à 8 lignes) avec le compteur « n / 1 000 » en légende, pseudo en `StudioTextField` d'une
     ligne, bouton principal « Envoyer » (« Envoi… » pendant l'envoi ; désactivé pendant un délai,
     « Prochain envoi possible dans … » dessous), informations jointes en légende, résultat (§ 14.4)
     en phrase. Brouillon, limites, délais et requête inchangés (§ 14.1 – 14.3).

Les messages de succès et d'erreur ne prennent pas de couleur (DESIGN.md § 1 : vert = progression,
rouge = baisse) : phrase en `textPrimary` 600, comme l'erreur de la pesée (§ 15.9.2).

#### 15.10.4 Thème

- Choix **Sombre** (par défaut), **Clair**, **Système**, dans cet ordre (🔄 D35, remplace « Système »
  par défaut du § 3.14 et l'ordre du § 2.12) ; effet immédiat (§ 13.4).
- Format inchangé : `preferences.theme` vaut toujours `"system"`, `"light"` ou `"dark"`.
  **Défaut `"dark"`** : app installée sans `preferences.json`, sauvegarde importée dont le thème est
  absent ou invalide (§ 5.3 : `theme` invalide → `"dark"`), sauvegarde v1 de l'app Expo (qui
  n'avait que le thème sombre : migration `theme = "dark"`).
- Un choix déjà enregistré (`"system"` compris) est **gardé** : rien ne le réécrit.
- Le choix est transmis au système (Android 12+, § 9.1) une fois les préférences lues, comme avant ;
  avant la lecture, l'interface part du thème sombre.

#### 15.10.5 Règles (pures, testées en JVM)

- **État des calories du jour** : `ratio = kcal / objectif` (0 si objectif ≤ 0), ton du § 2.11
  (🔄 D15) ; *encore* = `objectif − kcal` (ratio < 0,9), *au-dessus* = `kcal − objectif`
  (ratio > 1,05) ; piste = `min(1, ratio)`.
- **Eau** : objectif atteint ssi `ml ≥ objectif` ; `setWater(jour, v)` : `waterLog[jour] = max(0, v)`
  (🔄 nouvelle règle, pendant de `setCaloriesToday`).
- **Compléments pris** : nombre de compléments existants dont `taken[id] == aujourd'hui`.
- **Résumé des rappels** d'un complément : désactivés → aucun ; activés et 0 → activés ; 1 → l'heure ;
  n → n (§ 2.11).
- **Saisie au pavé** des quantités, sur le modèle du § 15.7.4 (champ neuf, pas de zéro en tête,
  touche au-delà de la longueur sans effet, pas jamais sous 0) :
  - kcal et ml : **entiers**, 5 chiffres au plus, le séparateur est sans effet ;
  - objectif d'eau en litres : séparateur de la langue, 2 décimales et 5 caractères au plus ;
  - pas : ±50 kcal, ±250 ml, ±100 kcal, ±0,25 L ; un champ vide vaut 0 pour le pas ;
  - validation : « Ajouter » > 0, « Total du jour » champ non vide (≥ 0), objectif > 0.
- **Thème** : défaut `Dark` (`Preferences.DEFAULT`), lecture d'un `theme` invalide ou absent →
  `Dark`, migration v1 → `"dark"`.

#### 15.10.6 Design system

Composants existants, sauf ces ajouts (décrits dans DESIGN.md § 9 avec leurs aperçus) :
- **`ListRow`** : case à cocher **séparée** de la ligne (`ListRowTrailing.Check` avec sa propre
  action) — la case bascule, la ligne ouvre autre chose (complément) ; sans action propre, toute
  la ligne bascule comme avant (échauffement, lignes à cocher des sheets).
- **Surface « Aujourd'hui »** (`DailySurface`, variante de `HighlightSurface`) : légende, chiffre
  héros et état, piste, rangée de stats, deux boutons.
- **Piste de progression** `ProgressTrack` : la piste de `LiveTrack` remplie en `textPrimary`.
- **Icône** `pill` (Material Symbols Rounded), état vide des compléments.
- **Contrôle segmenté** posé dans une sheet (langue, thème) et **ligne à cocher sur une sheet**
  (`ListRow` sans filet, les `InfoRow` qui suivent portant le filet `surfaceControl`).
- **`StudioTextField`** : `minLines` (message d'un avis, 4 à 8 lignes) et `enabled` (envoi en cours).

#### 15.10.7 Textes (FR et EN)

Clés Android (`strings.xml`) ; les libellés existants sont repris (« Nutrition », « Calories »,
« Eau », « Nouveau complément », « Nom », « Dosage par prise », « Rappels », « Ajouter un rappel »,
« Heure du rappel », « Aucun complément » et son texte, « {dosage} {unité} par prise », résumés des
rappels, « Supprimer « … » ? », `nutrition.infoMobile`, « Objectif quotidien », `common_kcal_value`,
`common_liters_value`, et tous les textes de Paramètres, de la sauvegarde automatique, de Health
Connect, des mises à jour, du rapport de plantage et de l'avis).

| clé | FR | EN |
|---|---|---|
| `nutrition_today` | Aujourd'hui | Today |
| `nutrition_calories_left` | encore {{n}} kcal | {{n}} kcal left |
| `nutrition_calories_on_target` | dans l'objectif | on target |
| `nutrition_calories_over` | +{{n}} kcal | +{{n}} kcal |
| `nutrition_goal_of` / `_goal_dot` / `_goal_over` / `_goal_only` | sur {{objectif}} / · {{objectif}} kcal / au-dessus de {{objectif}} / objectif {{objectif}} kcal | of {{goal}} / · {{goal}} kcal / over {{goal}} / goal {{goal}} kcal |
| `nutrition_water_of` / `_water_reached` | eau · sur {{objectif}} / eau · objectif atteint | water · of {{goal}} / water · goal reached |
| `nutrition_supplements_taken_label` | compléments pris | supplements taken |
| `nutrition_ml_value` | {{valeur}} ml | {{value}} ml |
| `nutrition_supplements` | Compléments | Supplements |
| `nutrition_taken_toggle` (accessibilité) | Pris aujourd'hui | Taken today |
| `nutrition_supplement_options` (accessibilité) | Rappels et options | Reminders and options |
| `nutrition_empty_action` | Ajouter un complément | Add a supplement |
| `nutrition_entry_help` | Aujourd'hui : {{valeur}} sur {{objectif}}. | Today: {{value}} of {{goal}}. |
| `nutrition_mode_add` / `_mode_total` | Ajouter / Total du jour | Add / Day total |
| `nutrition_new_total` | Nouveau total | New total |
| `nutrition_entry_invalid` | Saisis une quantité | Enter an amount |
| `nutrition_calorie_goal_title` / `_water_goal_title` | Objectif de calories / Objectif d'eau | Calorie goal / Water goal |
| `nutrition_goal_help` | À partir d'aujourd'hui ; les jours passés gardent le leur. | From today on; past days keep theirs. |
| `nutrition_goal_invalid` | Saisis un objectif supérieur à 0 | Enter a goal above 0 |
| `nutrition_reminder` | Rappel | Reminder |
| `nutrition_delete_supplement` | Supprimer le complément | Delete supplement |
| `nutrition_delete_message` | Le complément et ses rappels seront retirés. | The supplement and its reminders will be removed. |
| `common_close` / `common_ok` | Fermer / OK | Close / OK |
| `settings_section_preferences` / `_data` / `_about` | Préférences / Données / À propos | Preferences / Data / About |
| `settings_language_help` | Langue du système : celle du téléphone. | System language: your phone's. |
| `settings_language_system_short` (segment) | Système | System |
| `settings_theme_help` | Sombre par défaut. Système suit le réglage du téléphone. | Dark by default. System follows your phone's setting. |
| `settings_notifications_off_title` / `_subtitle` | Notifications désactivées / Minuteur et rappels silencieux | Notifications off / Rest timer and reminders are silent |
| `settings_on_f` / `settings_off_f` | Activée / Désactivée | On / Off |
| `settings_on_m` / `settings_off_m` | Activé / Désactivé | On / Off |
| `settings_auto_backup_off_subtitle` | Chaque jour, dans le dossier de ton choix | Every day, in the folder you choose |
| `settings_auto_backup_last_short` / `_failed_short` / `_folder_lost_short` | Dernière le {{date}} / Échec le {{date}} / Dossier inaccessible | Last on {{date}} / Failed on {{date}} / Folder unavailable |
| `settings_auto_backup_toggle` / `_folder_label` / `_last_label` | Sauvegarde quotidienne / Dossier / Dernière sauvegarde | Daily backup / Folder / Last backup |
| `settings_auto_backup_running` | Sauvegarde… | Backing up… |
| `settings_export_short` / `_export_csv_short` / `_import_short` | Fichier JSON de toutes tes données / Tableaux pour Excel ou Sheets / Depuis un fichier exporté | JSON file with all your data / Tables for Excel or Sheets / From an exported file |
| `settings_in_progress` | En cours… | In progress… |
| `health_subtitle_off` / `_subtitle_last` / `_subtitle_missing` | Séances et poids vers tes apps de santé / Dernier envoi le {{date}} / Autorisation manquante | Workouts and weight to your health apps / Last sent {{date}} / Permission missing |
| `health_toggle` / `_last_label` / `_written_label` / `_sending` | Envoyer à Health Connect / Dernier envoi / Écrits / Envoi… | Send to Health Connect / Last sent / Written / Sending… |
| `settings_update_subtitle_managed` / `_never` / `_up_to_date` / `_available` / `_checking` | Gérées par Obtainium / Pas encore vérifié / À jour · vérifié le {{date}} / La {{version}} est disponible / Vérification… | Managed by Obtainium / Not checked yet / Up to date · checked {{date}} / {{version}} is available / Checking… |
| `settings_update_download` | Télécharger la {{version}} | Download {{version}} |
| `feedback_row_title` / `_row_subtitle` | Envoyer un avis / Une idée, un bug ? | Send feedback / An idea, a bug? |
| `feedback_sending` | Envoi… | Sending… |

`settings_section_preferences` devient « Préférences » / « Preferences » (titre de section, plus en
capitales). Les clés devenues inutiles (`nutrition_section_today`, `nutrition_cal_add` / `_cal_set` /
`_cal_placeholder`, `nutrition_water_placeholder`, `nutrition_cal_goal_placeholder`,
`nutrition_water_goal_placeholder`, `nutrition_supplement`, `nutrition_supplements_section`,
`nutrition_taken_yes` / `_taken_no`, `chart_goal`, `settings_section_backup` / `_app` / `_health` /
`_feedback`, `settings_my_data`, `settings_language_desc`, `settings_theme_desc`,
`settings_notifications_denied` / `_open`, `settings_export_desc`, `settings_export_csv_desc`,
`settings_import_desc`, `settings_auto_backup_folder` / `_change` / `_last`, `settings_update_managed`
/ `_never` / `_up_to_date` / `_available` / `_check`, `update_download`, `health_last`,
`health_written`) sont retirées.

### 15.11 Refonte « Studio épuré » : démarrer, sélecteur, éditeur et finitions (🔄 D36)

Étape 5b, dernière de la refonte (`DESIGN.md`) : les trois écrans encore sur l'ancien thème —
« Démarrer une séance » (§ 2.3), le sélecteur d'exercices (§ 2.7) et l'éditeur d'une séance
terminée (§ 2.9) — passent au design system selon la correspondance de DESIGN.md § 11 ; l'ancien
thème (`ui/theme/`), ses composants et ses icônes sont retirés ; l'interface est vérifiée pour
l'accessibilité (contrastes, texte à 200 %, TalkBack). Les règles du § 3 et le **format des
données ne changent pas** (§ 4, § 5) : aucune migration.

#### 15.11.1 Démarrer une séance

Remplace l'écran plein du § 2.3 par un **formulaire en bottom sheet** (DESIGN.md § 10.4), posé
sur l'écran qui l'ouvre : l'Accueil (« Démarrer » de la surface « Prochaine séance », à l'étape du
poids avec le type choisi, § 15.8.2 ; « Autre… », à l'étape du type), le Journal et Progrès (état
vide, étape du type). Plus de route `start-workout`.

- **Étape « Type de séance »** — titre et aide du § 2.3 ; les préréglages en puces de filtre sous
  le libellé de leur groupe (PPL, Split, Autre) ; « Personnalisé » : un `StudioTextField` d'une
  ligne (placeholder « Ex: Épaules / Mollets », 30 caractères). Toucher une puce vide le nom ;
  écrire un nom désélectionne la puce. « Suivant » (principal) actif si un préréglage est choisi
  ou si le nom (`trim()`) n'est pas vide.
- **Étape « Poids de corps »** — aide « Ton poids aujourd'hui ? » ; champ héros en kg, prérempli
  avec le poids de la séance terminée la plus récente (champ neuf : la première touche le
  remplace), vide sinon ; puces de saisie rapide 55 … 100 sur une rangée défilante (celle qui
  vaut la saisie est sélectionnée) ; ligne d'information « Type de séance » (le type choisi,
  chevron → étape du type) ; pavé numérique, pas ±0,1 (comme la pesée), touche principale
  « ✓ Commencer ». Poids ≤ 0 : l'aide devient « Saisis ton poids de corps » avec une vibration
  d'erreur.
- 🔄 D38 (§ 15.13.2) : champ vide, dernière pesée en aide, bouton texte « Passer » (séance sans
  poids) ; déjà pesé aujourd'hui → pas de champ, « Suivant » devient « ✓ Commencer ».
- « Commencer » : permission des notifications puis `startWorkout` comme au § 2.3, la sheet se
  ferme et la séance en cours s'ouvre. Retour à l'étape du poids : l'étape du type ; voile ou
  glisser : la sheet se ferme sans rien démarrer.

#### 15.11.2 Sélecteur d'exercices

Remplace l'écran plein du § 2.7 (route `exercise-picker` retirée) par une **bottom sheet haute**
(92 % de l'écran) ouverte par « Ajouter un exercice » de la séance en cours (bouton de fin de liste
ou état vide). Mêmes règles : exercices archivés absents, filtre groupe **et** texte sur le nom
affiché, ajouts multiples. 🔄 D43 : filtre « Par muscle » et sheet « Muscle » (§ 15.17.6, § 15.17.7),
muscles d'un exercice perso dans son formulaire (§ 15.17.3).

- **Haut** : titre « Ajouter un exercice » et bouton icône **+** « Nouvel exercice » ; recherche
  (`StudioTextField`, « Chercher un exercice... ») ; puces de filtre « Tous » puis les 9 groupes,
  sur une rangée défilante (toucher la puce active la désélectionne).
- **Liste** : une `ListRow` par exercice, titre = nom, sous-titre = groupe, et la **case** à
  droite : cochée et inactive s'il est déjà dans la séance (TalkBack : « coché, désactivé ») ;
  sinon toucher la ligne l'ajoute (`addExerciseToWorkout`, vibration légère) et la coche. Liste
  vide : « Aucun exercice trouvé ».
- **Exercice perso** non ajouté : **appui long** (« Modifier ou supprimer ») → la sheet passe à ses
  actions : titre = nom, aide = groupe ; « Modifier l'exercice » et « Supprimer l'exercice »
  (secondaires), « Fermer ». Modifier : formulaire nom + groupe, « Enregistrer ». Supprimer :
  confirmation « Supprimer « Nom » ? » / `exercisePicker.deleteHint` / [Supprimer] / [Annuler]
  (§ 3.9 : archivé s'il sert encore).
- **Nouvel exercice** (+) : la sheet passe au formulaire — « Nom » (focus automatique, majuscule
  en début de phrase) et « Groupe musculaire » en puces de filtre (le groupe filtré, sinon `dos`),
  [Ajouter] actif si le nom n'est pas vide, [Annuler]. Le retour ramène à la liste.

#### 15.11.3 Éditer une séance terminée

Remplace la disposition du § 2.9 : gabarit **fiche** en liste paresseuse avec une action fixe en
bas (DESIGN.md § 10.2). Toujours une **copie de travail** enregistrée par « Enregistrer » seulement,
avec « Abandonner les modifications ? » à la sortie (🔄 D15).

- **Barre** : ✕ (« Fermer ») ; bloc titre : surtitre « Modifier la séance », titre = nom affiché,
  sous-titre = date de la séance (« lun. 9 nov. 2026 »).
- **Séance** : deux lignes, « Type de séance » (le nom, toucher → **« Renommer la séance »** :
  sheet aux mêmes puces et nom perso qu'au § 15.11.1, « Enregistrer ») et « Poids de corps »
  (« 78,4 kg » ou « — », toucher → formulaire au pavé, pas ±0,1, « Enregistrer » si le poids est
  supérieur à 0, sinon « Saisis ton poids de corps »).
- **Un bloc par exercice** (section, 40 entre eux) : en-tête `ExerciseBlockHeader` sans bouton
  de fiche, ⋮ → sheet de l'exercice (titre = nom, aide = groupe, « Retirer de la séance » puis
  confirmation, « Fermer ») ; puce neutre du groupe ; colonnes # · KG · REPS (MIN · KM en cardio)
  **sans « Préc. »** ; lignes de série avec leur case de validation ; « + Série » (copie de la
  dernière série ou 10 × 20 kg / 30 min, validée, comme au § 2.9).
- **Saisie au pavé** comme dans la séance en cours (§ 15.7.4) : toucher une valeur ouvre le pavé
  en bas à la place de « Enregistrer » (segmenté des champs, vitesse et inclinaison en champ héros,
  « Supprimer la série » confirmé, « Suivant » / « Valider la série » / « OK ») ; valider une
  série la coche dans la copie, **sans repos**. Le retour ferme d'abord le pavé.
- **Ajouter un exercice** (bouton secondaire en fin de liste, ou état vide « Aucun exercice » /
  « Ajoute les exercices de cette séance. ») → le sélecteur du § 15.11.2, sans exercice perso à
  gérer : les exercices déjà dans la séance y sont cochés ; toucher en ajoute un (série initiale
  du § 2.9) et ferme la sheet.
- En bas : « ✓ Enregistrer » (`patchWorkout`, puis retour) ; « Abandonner les modifications ? » en
  bottom sheet (« Tes changements sur cette séance ne seront pas enregistrés. », [Abandonner],
  [Continuer l'édition]).

#### 15.11.4 Accessibilité

- **Contrastes** (WCAG, calculés sur les jetons) : tout texte ≥ 4,5:1 sur les fonds où il est
  posé, dans les deux thèmes ; `textTertiary` seulement pour les chiffres de 24 sp et plus (≥ 3:1)
  et les boutons désactivés (exemptés). En clair, l'aplat `live` (point « en cours », barre de
  repos) passe de `#FFC93D` (1,1 à 1,5:1 sur les fonds clairs) à **`#9E7100`** (≥ 3:1 partout) ;
  le texte en direct reste `#7A5900`, le sombre et le logo ne changent pas.
- **Texte à 200 %** : rien n'est coupé. Les hauteurs des composants deviennent des minimums
  (boutons, puces, lignes, en-têtes, mini-barre, touches, champ héros, lignes de série) ; titres,
  sous-titres, légendes et messages passent à la ligne ; un chiffre ne passe jamais à la ligne : il
  rétrécit jusqu'à 60 % pour tenir dans sa colonne (stats, séries) ; les libellés d'onglets et
  les en-têtes de colonnes rétrécissent de même ; la colonne « Préc. » s'élargit jusqu'à 1,5 fois.
- **TalkBack** : chaque valeur de série se lit avec sa série et sa colonne (« Série 3, Kg :
  82,5 ») ; « + Série » se lit « Ajouter une série : Développé couché » ; les boutons fiche et
  options d'un bloc nomment l'exercice ; la touche ⌫ du pavé se lit « Effacer » ; une case sans
  action (exercice déjà ajouté) est lue cochée et désactivée.
- **Cibles tactiles** : 48 dp au moins partout ; les boutons icône de 44 (bloc exercice, en-tête du
  pavé) sont posés dans une zone de 48 ; les options du contrôle segmenté se touchent sur toute la
  hauteur du conteneur (48) ; dans l'en-tête du pavé, le segmenté défile plutôt que de serrer
  les boutons (quatre champs du cardio).
- Vérifié par les règles d'Accessibility Test Framework sur tous les aperçus (`compose-preview
  a11y`) : aucune erreur sur les écrans ; restent des avertissements « texte identique » attendus
  (« Valider la série 1 » dans chaque bloc).

#### 15.11.5 Retiré

- **Ancien thème** `ui/theme/` (`Color.kt`, `Shape.kt`, `Theme.kt`, `Type.kt` : palettes du § 9.1,
  Inter du § 9.2, couleurs des groupes et du top 3) ; `SystemNightMode` (§ 9.1) passe dans
  `ui/designsystem/theme/`. L'app entière est posée dans `StudioTheme` par `MainActivity`, comme
  l'écran de politique de confidentialité de Health Connect.
- **Police Inter** et les polices téléchargeables : dépendance `ui-text-google-fonts`,
  `res/values/font_certs.xml`. L'app n'utilise plus Google Play Services pour sa police (🔄 D12
  remplacée : Archivo embarquée, DESIGN.md § 3).
- **Composants** de `ui/components/` : `InlineConfirm`, `MuscleGroupBadge` (et les couleurs de
  groupe, § 6.2), `NotFoundScreen`, `ScreenHeader`, `DashedBorder`, la barre de recherche et les
  puces de groupe (`ExerciseSearch.kt` ne garde que le filtre) ; l'ancienne ligne de série de
  l'éditeur (`ui/workout/SetRow.kt`), `StartWorkoutScreen`, `ExercisePickerScreen`.
- **Icônes** Material Symbols Outlined de l'ancien thème : 52 fichiers `res/drawable/ic_*.xml`.
  Restent `ic_check`, `ic_replay_30`, `ic_forward_30`, `ic_skip_next` (actions de la notification
  de séance), `ic_notification` et les couches du logo.
- **Textes** devenus inutiles : `start_workout_custom_empty`, `exercise_picker_title`,
  `exercise_card_add_set`, `edit_workout_empty_exercises`, `workout_in_progress`,
  `workout_sets_progress`.

#### 15.11.6 Design system

Composants existants, sauf ces ajouts (DESIGN.md § 3 et § 9) :
- jetons `bodyStrong` (14 · 600, message d'état sans couleur) et `captionStrong` (13 · 600) ;
- `StatusMessage` et `statusHelp` (message de succès ou d'erreur, aussi en aide de formulaire) et
  `LabeledField` (champ sous son libellé), sortis des écrans ;
- `SetRow` et `SetTableHeader` sans colonne « Préc. » (`previous = null`), descriptions TalkBack
  des valeurs ; `AddSetRow` avec description ; `ExerciseBlockHeader` sans bouton fiche ;
- `ListRow` : case sans action lue désactivée, case avec appui long ;
- `FicheLazyTemplate` avec une action fixe en bas (`bottomAction`) ;
- les adaptations au texte agrandi et aux cibles tactiles du § 15.11.4.

#### 15.11.7 Textes (FR et EN)

| clé | FR | EN |
|---|---|---|
| `start_workout_weight_invalid` | Saisis ton poids de corps | Enter your body weight |
| `exercise_picker_delete_exercise` | Supprimer l'exercice | Delete exercise |
| `exercise_picker_actions` (accessibilité) | Modifier ou supprimer | Edit or delete |
| `exercise_picker_name_label` / `_group_label` | Nom / Groupe musculaire | Name / Muscle group |
| `edit_workout_empty_message` | Ajoute les exercices de cette séance. | Add this session's exercises. |
| `edit_workout_discard_message` | Tes changements sur cette séance ne seront pas enregistrés. | Your changes to this session won't be saved. |
| `workout_set_value_description` (accessibilité) | Série {{n}}, {{colonne}} : {{valeur}} | Set {{n}}, {{column}}: {{value}} |
| `workout_add_set_to` (accessibilité) | Ajouter une série : {{exercice}} | Add a set: {{exercise}} |
| `workout_exercise_details_for` / `_options_for` (accessibilité) | Fiche : {{exercice}} / Options : {{exercice}} | Details: {{exercise}} / Options: {{exercise}} |

#### 15.11.8 Mise à jour depuis la v1.0

Même app (`com.musclenote.android`), même clé de signature, `versionCode` supérieur : la Release
de la refonte s'installe par-dessus une v1.0.x et garde tout. **Rien à migrer** : les fichiers de
`filesDir/data/` (§ 4.2), données comme états propres à l'appareil (séance en cours, minuteur,
sauvegarde automatique, Health Connect, mises à jour), sont relus tels quels. Seul
comportement nouveau, le thème par défaut (§ 15.10.4) : sans `preferences.json` (ni la durée du
repos ni le thème changés, rien importé), l'app s'ouvre en sombre ; sinon le thème enregistré,
« Système » compris, est gardé.

Vérifié le 15 novembre 2026, en release, sur un émulateur Android 16 (API 36) :
- **Builds** : la v1.0.8 (tag `v1.0.8`, = `main`) puis la build de `redesign`, signées avec la même
  clé de test, `versionCode` 1008 puis 1009 ; installation de la seconde par `adb install -r`
  (date de première installation conservée).
- **Données de la v1.0.8** : import d'une sauvegarde de test (18 séances Push / Pull / Legs sur six
  semaines avec du cardio et une note, un exercice perso et un archivé, trois pesées, deux
  compléments dont un à deux rappels, objectifs datés de calories et d'eau, thème clair, repos
  2 min 30) ; puis, dans l'app, calories et eau du jour, complément pris, sauvegarde automatique
  dans un dossier, langue de l'app en français, séance « Push » démarrée avec une série validée et
  le repos en cours.
- **Résultat** : la sauvegarde automatique écrite par la v1.0.8 juste avant la mise à jour et
  celle écrite par la nouvelle version juste après sont **identiques hors `exportedAt`** (séances,
  exercices, pesées, compléments, prises, suivi du jour, préférences). La séance en cours reprend où
  elle était (1 / 9 séries, repos qui continue, notification de séance republiée au nouveau style) ;
  langue, thème clair, durée du repos, dossier et planification de la sauvegarde automatique,
  alarmes des rappels : gardés ; aucun plantage. Accueil, Journal, Progrès et Nutrition affichent
  les mêmes chiffres qu'avant (le « 19h 30m » de l'ancien Accueil devient « 20 h » dans le
  Journal : arrondi à l'heure du § 15.8).

### 15.12 Résumé de fin de séance (🔄 D37)

Après « Terminer la séance », la séance terminée s'affiche en **résumé** : un écran au gabarit
**fiche** (DESIGN.md § 10.2), fait des composants existants. Le même résumé est ensuite la fiche
d'une séance du Journal. Tout est **calculé** à partir des séances terminées : le **format des
données ne change pas** (§ 4, § 5), aucune migration ; les règles du § 3 non plus.

#### 15.12.1 Ouverture et navigation

- **Fin de séance** : « Terminer » dans la confirmation (§ 15.7.1), avec au moins une série
  validée → la séance est terminée comme avant (§ 3.3 : historique, repos arrêté, notification
  « Séance en cours » retirée, § 15.5) et son résumé **prend la place de la séance en cours** dans
  la pile, au-dessus de l'Accueil. Aucun bouton en haut ; **un seul bouton**, « Terminer »
  (principal, barre d'action en bas), qui ramène à l'Accueil ; le retour système aussi. Sans série
  validée (« Abandonner »), rien n'est enregistré et l'on revient à l'Accueil, sans résumé.
- **Consultation** : toucher une séance du **Journal** ou la ligne « Dernière séance » de l'Accueil
  (§ 15.8.4) ouvre son résumé (🔄 c'était l'éditeur ; DESIGN.md § 11 l'annonçait « en attendant la
  fiche résumé »). Barre : ← (retour à l'écran d'origine) et ⋮ « Options de la séance », qui ouvre
  la sheet de l'appui long (« Modifier la séance » → éditeur, « Modifier la date », « Supprimer la
  séance ») ; pas de barre d'action. Une séance supprimée ferme son résumé. L'appui long sur une
  ligne ne change pas ; les séances de la fiche exercice (§ 15.9.1) ouvrent toujours l'éditeur.
- Route `workout-summary/{workoutId}`, avec `?finished=true` en fin de séance. Séance introuvable :
  « Séance introuvable » et « Retour », comme l'éditeur (§ 2.9).

#### 15.12.2 Écran

De haut en bas :

1. **Barre** (§ 15.12.1).
2. **Bloc titre** : surtitre « Séance terminée · jeu. 12 nov. · 18:04 » (date et heure locales du
   début ; date illisible, § 10.3 : « Séance terminée ») ; titre = nom affiché (§ 3.1).
3. **Chiffre héros** : légende « Volume total » ; valeur = le **volume** de la séance (§ 3.8, séries
   validées, arrondi au kg, séparateur de milliers de la langue : « 8 240 ») et l'unité « kg » ;
   **variation** par rapport à la séance précédente du même programme (§ 15.12.3), toujours signée
   — « +280 kg » en vert, « −150 kg » en rouge, « identique » en gris — suivie de « vs Push du
   jeu. 5 nov. » (nom affiché et date de cette séance). **Première séance du programme : ni
   variation ni contexte.** Une séance sans charge (cardio, poids du corps) affiche « 0 kg ».
4. **Rangée de stats** : la **durée** « 1 h 14 » (format compact du Journal, § 15.8.4), absente sans
   `durationMinutes` (séance finalisée après 24 h, § 3.4) ; le nombre de **séries** validées « 16 ».
5. **Section « Exercices »** ; à droite « Records battus · 2 » en vert (nombre d'exercices qui ont
   battu un record, DESIGN.md § 9.1), sinon le nombre d'exercices. Une ligne par exercice ayant au
   moins une série validée, dans l'ordre de la séance (exercice inconnu masqué, § 10.5) :
   - titre : nom de l'exercice (§ 6) ;
   - sous-titre : « Meilleure série 82,5 × 8 · 1RM ~104 kg », mêmes règles et textes que les séances
     de la fiche exercice (§ 15.9.1, point 7 : la série validée la plus lourde, à égalité le plus de
     reps puis la première ; « 12 reps » à 0 kg ; 1RM omis s'il vaut 0 ; cardio « 30 min · 5,2 km »,
     sans 1RM) ;
   - à droite, **seulement si un record est battu** (§ 15.12.3) : « Record » en vert (variation
     seule). Rien sinon.
   Les lignes ne s'ouvrent pas (la fiche exercice reste accessible depuis Progrès et la séance).
6. **En fin de séance** seulement : barre d'action « Terminer ».

#### 15.12.3 Règles (pures, testées en JVM)

- **Séances plus anciennes** qu'une séance S : les séances terminées placées après S dans
  l'historique, trié par date décroissante (§ 4.2). La séance en cours n'en fait jamais partie, ni
  les séances plus récentes que S : le résumé d'une ancienne séance se lit comme le jour où elle a
  été faite (une date modifiée déplace la séance, § 15.8.4).
- **Séance précédente du même programme** : la plus récente des séances plus anciennes qui a la
  même **clé de séance** que S (§ 3.1 : préréglage, sinon nom normalisé). Aucune → pas de variation.
  Une séance d'un autre programme ne compte jamais, même si elle est plus récente.
- **Variation du volume** = volume de S arrondi au kg − volume de la précédente arrondi au kg :
  > 0 hausse (vert), < 0 baisse (rouge), 0 « identique » (gris) ; jamais « +0 kg ».
- **Records battus** par un exercice de S (au moins une série validée dans S), comparés aux séances
  plus anciennes où il a au moins une série validée — les deux records de la fiche exercice
  (§ 15.9.1, point 6) :
  - **record personnel** : la métrique de S (§ 15.9.3 : charge max ; reps max au poids du corps ;
    durée totale en cardio) est **strictement supérieure** au maximum de cette métrique sur les
    séances plus anciennes. Le choix charge / reps se fait sur les séances plus anciennes et S :
    un premier lest sur un exercice jusque-là au poids du corps est un record de charge ;
  - **1RM estimé** (force seulement) : le meilleur 1RM Epley de S (§ 3.5) est > 0 et
    **strictement supérieur** au meilleur des séances plus anciennes ;
  - **égalité : pas de record** ; **première fois** que l'exercice a une série validée (aucune
    séance plus ancienne) : **pas de record**, il n'y a rien à battre ;
  - seules les séries validées comptent (§ 3.16) ; l'exercice a battu un record si l'un des deux
    l'est (un seul « Record » sur sa ligne).

#### 15.12.4 Textes (FR et EN)

Clés Android (`strings.xml`) ; les libellés existants sont repris (« Séance introuvable », « Retour »,
« Exercices », « Options de la séance », « Meilleure série … », « 1RM ~… kg », « Record »,
« identique », les séries de la fiche exercice, les durées du Journal, `common_sets_count`).

| clé | FR | EN |
|---|---|---|
| `summary_finished` | Séance terminée | Workout finished |
| `summary_finished_on` | Séance terminée · {{date}} · {{heure}} | Workout finished · {{date}} · {{time}} |
| `summary_volume` | Volume total | Total volume |
| `summary_delta_context` | vs {{séance}} du {{date}} | vs {{workout}} on {{date}} |
| `summary_stat_duration` | durée | duration |
| `summary_stat_sets` (pluriel) | série / séries | set / sets |
| `summary_records` (pluriel) | Record battu · {{n}} / Records battus · {{n}} | Record broken · {{n}} / Records broken · {{n}} |
| `summary_done` | Terminer | Done |

### 15.13 Pesée facultative et rappel de pesée (🔄 D38)

Le poids de corps n'est plus exigé à chaque séance : au démarrage, la pesée devient **facultative**
(« Passer »), et elle n'est plus demandée du tout quand le poids du jour est déjà connu. En échange,
un **rappel de pesée**, désactivé par défaut et réglé dans une nouvelle section « Rappels » de
Paramètres, propose de se peser certains jours à heure fixe. Aucune mesure n'est inventée : un poids
n'est jamais recopié d'un jour sur l'autre ; à l'affichage, une séance sans poids montre la dernière
pesée connue. Le **format des données ne change pas de version** (§ 15.13.5) : `bodyWeight` garde sa
forme et le réglage du rappel s'ajoute aux préférences comme champ facultatif ; aucune migration.

#### 15.13.1 Poids du jour et dernière pesée connue

- **Poids d'un jour** : la règle de Progrès et de Health Connect (§ 3.10, § 15.3) — la pesée manuelle
  du jour (§ 3.11) si elle existe, sinon le `bodyWeight > 0` de la séance la plus récente du jour
  (jour local de son début). Ici, la **séance en cours** compte comme une séance de son jour.
- **Pesé ce jour-là** = ce jour a un poids. MuscleNote ne lit pas Health Connect (🔄 D29 : aucune
  permission de lecture) : une pesée faite dans une autre app (balance connectée…) n'existe pas pour
  MuscleNote (piste au § 15.6).
- **Dernière pesée connue** au jour J : le poids du jour le plus récent ≤ J, avec son jour. Aucune →
  rien.
- **Poids d'une séance**, pour l'affichage : son `bodyWeight` s'il est > 0 ; sinon la dernière pesée
  connue au jour local de son début (pesées et autres séances, ce jour compris) ; sinon rien, et la
  valeur n'est pas affichée. Il sert à l'en-tête de la séance en cours « 78,4 kg · 2 / 16 séries »
  (§ 15.7.1) et à la surface « En cours » de l'Accueil (§ 15.8.2).
- Partout ailleurs, seul le poids **saisi** compte, jamais recopié : courbe et pesées de Progrès
  (§ 15.9.2), Health Connect (une séance sans poids n'écrit pas de poids, § 15.3), export CSV (colonne
  « Poids de corps (kg) » vide, § 8), éditeur (« Poids de corps » « — », § 15.11.3), sauvegarde JSON.
- **Le 0 n'est jamais une valeur** : un `bodyWeight` ≤ 0 ne s'affiche nulle part (« — » ou rien,
  selon l'écran), ne part pas vers Health Connect et laisse sa cellule CSV vide. Aucun calcul n'utilise
  le poids d'une séance : le volume (§ 3.8) et le 1RM estimé (§ 3.5) se calculent sur la charge des
  séries, et un exercice « au poids du corps » se reconnaît à ses séries à 0 kg, sa métrique étant les
  reps (§ 15.9.3).

#### 15.13.2 Démarrer une séance : étape « Poids de corps »

Remplace le préremplissage du poids (§ 2.3, § 15.11.1) ; le reste de la sheet ne change pas.

- **Pas encore pesé aujourd'hui** (§ 15.13.1) :
  - champ héros **vide** (il n'est plus prérempli avec le poids de la dernière séance) ;
  - aide = la dernière pesée connue, valeur et ancienneté : « Dernière pesée : 78,4 kg, hier. »,
    « Dernière pesée : 78,4 kg, il y a 3 jours. » (jours calendaires locaux) ; sans aucune pesée,
    « Ton poids aujourd'hui ? Facultatif. » ;
  - puces 55 … 100, ligne « Type de séance » et pavé (pas ±0,1) inchangés ; champ vide, les pas − / +
    partent de la dernière pesée connue (« +0,1 » donne 78,5) ;
  - bouton texte **« Passer »**, pleine largeur, sous la ligne « Type de séance » : démarre la séance
    **sans poids** (`bodyWeight = 0`) et **n'enregistre aucune pesée** ; sinon comme « Commencer »
    (permission des notifications, `startWorkout`, la séance s'ouvre) ;
  - « ✓ Commencer » exige toujours un poids > 0 : champ vide ou 0 → l'aide devient « Saisis ton
    poids, ou touche « Passer ». » avec une vibration d'erreur.
- **Déjà pesé aujourd'hui** : **pas de champ**. L'étape « Poids de corps » n'a plus que l'aide « Déjà
  pesé aujourd'hui : 78,4 kg. », la ligne « Type de séance » et le bouton principal « ✓ Commencer » ;
  depuis l'étape du type, « Suivant » devient « ✓ Commencer » et démarre directement (« Démarrer » de
  l'Accueil avec un type ouvre l'étape du poids, donc cette confirmation). La séance démarre sans
  poids : celui du jour s'affiche (§ 15.13.1) sans être recopié.
- Un poids saisi est enregistré dans la séance comme avant (`bodyWeight`) et devient le poids du jour
  s'il n'y a pas de pesée (§ 3.10) ; il ne crée pas de pesée manuelle (section « Pesées » de Progrès
  inchangée).

#### 15.13.3 Réglage du rappel de pesée

- **Réglage** : activé ou non (**désactivé par défaut**), heure (**07:30** par défaut, toute minute),
  jours de la semaine (**tous** par défaut). Gardé dans les préférences (§ 15.13.5), donc sauvegardé
  et importé comme la durée du repos.
- **Paramètres, section « Rappels »** (nouvelle, entre « Préférences » et « Données », § 15.10.3 ;
  🔄 D39 : renommée « Notifications », § 15.14.5) :
  - **Rappel de pesée** — valeur « 07:30 » ou « Désactivé » ; sous-titre : les jours (« Tous les
    jours », « lun., mer., ven. », « Aucun jour ») ou, désactivé, « Pour penser à te peser » → sheet
    « Rappel de pesée » ;
  - puis **une ligne par complément** (les rappels de Nutrition, § 15.10.1, dans l'ordre de création) :
    titre = nom, sous-titre = résumé de ses rappels (« Rappel à 08:00 », « 3 rappels », « Pas de
    rappel »…), chevron → **la sheet du complément** de Nutrition (§ 15.10.2 : rappels, suppression
    confirmée, « Fermer »), la même. Sans complément, pas de ligne : ils se créent dans Nutrition.
- **Sheet « Rappel de pesée »** (réglage composé, comme la sauvegarde automatique ; chaque changement
  s'applique tout de suite, retour ou voile ferment) :
  - titre « Rappel de pesée », aide « Il ne sonne pas si tu t'es déjà pesé ce jour-là. » ;
  - ligne à cocher « Me rappeler de me peser » ; cocher demande `POST_NOTIFICATIONS` si nécessaire
    (§ 7.1) ;
  - ligne d'information « Heure » « 07:30 », chevron → choix de l'heure (§ 15.10.2 : `TimePicker`,
    « Heure du rappel », sans « Supprimer ») ;
  - « Jours » (champ sous son libellé) : 7 puces de filtre à **choix multiple**, du lundi au dimanche,
    libellés courts de la langue (« lun. » … « dim. », « Mon » … « Sun »), lus en entier par TalkBack
    (« lundi ») ; toucher une puce ajoute ou retire le jour ; aucun jour → aucun rappel ;
  - heure et jours se règlent même décoché ; bouton texte « Fermer ».
- **Raccourci depuis Progrès** : la surface « Poids de corps » (§ 15.9.2) gagne, avant le bouton +, un
  bouton icône `notifications` « Rappel de pesée » qui ouvre la même sheet au-dessus de Progrès.

#### 15.13.4 Alarme et notification

Même mécanisme que les rappels de compléments (§ 7.3, 🔄 D7) :

- **Une alarme exacte** (`setExactAndAllowWhileIdle`, `RTC_WAKEUP`) à la
  **prochaine occurrence** : le premier jour, aujourd'hui compris, dont le jour de la semaine est coché
  et où l'heure locale `heure:minute` est strictement après maintenant. Heure **locale murale**,
  résolue par `ZonedDateTime.of` : heure inexistante (passage à l'heure d'été) → décalée après le
  saut ; heure ambiguë (passage à l'heure d'hiver) → première occurrence. Rappel désactivé ou aucun
  jour → pas d'alarme (celle qui existait est annulée).
- **Alarmes exactes refusées** sur le téléphone (`canScheduleExactAlarms()` faux, Android 12+, ou
  `SecurityException`) : le même repli que les rappels de compléments (§ 7.1), une alarme inexacte
  `setAndAllowWhileIdle` (`RTC_WAKEUP`), qui peut sonner un peu plus tard mais jamais plus tôt.
- Identité : `PendingIntent` d'URI `musclenote://weigh-in-reminder` (une seule alarme).
- **Programmée** à chaque changement du réglage, au démarrage de l'app, après un import et par les
  récepteurs système du § 7.3 (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`,
  `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`) ; file sérielle, chaque
  opération relisant le réglage le plus récent.
- **À la sonnerie**, le récepteur relit les données (processus relancé par l'alarme compris) :
  - rappel désactivé → rien, sans reprogrammation ;
  - jour local de la sonnerie non coché (réglage changé entre-temps) → pas de notification ;
  - **déjà pesé ce jour-là** (§ 15.13.1 : pesée manuelle, séance terminée ou en cours avec un poids) →
    pas de notification ;
  - sinon la notification ;
  - puis, rappel activé, l'occurrence suivante.
- **Notification** sur un nouveau canal `weigh-in` (§ 7.1) : titre « ⚖️ Pesée du jour », texte « Monte
  sur la balance, puis note ton poids. », dans la langue de l'app au moment de l'affichage ;
  `CATEGORY_REMINDER`, auto-annulable, une seule à la fois (la suivante la remplace). **Toucher** →
  onglet Progrès avec le formulaire « Nouvelle pesée » ouvert (§ 15.9.2 : pavé numérique,
  aujourd'hui).
- **Aucune permission nouvelle** : `POST_NOTIFICATIONS`, alarmes exactes et `RECEIVE_BOOT_COMPLETED`
  servent déjà (§ 1). Notifications refusées : rien ne s'affiche, comme pour les compléments, et
  Paramètres montre « Notifications désactivées » (§ 15.10.3).

#### 15.13.5 Format des données

- **`Workout.bodyWeight`** (§ 4.1) : même champ, même sens — `0` = **non saisi** (pesée passée, ou
  séance démarrée un jour déjà pesé), comme l'« inconnu » d'avant. Il est **toujours écrit**
  (`"bodyWeight": 0` dans les fichiers et les sauvegardes), pour que les versions précédentes de l'app
  relisent tout ; il est désormais **lu absent comme 0** dans `workouts.json` et
  `current_workout.json` (l'import l'acceptait déjà absent ou `null`, § 5.3). Pas de nouvelle version,
  pas de migration : les séances existantes gardent leur poids et se relisent telles quelles.
- **`Preferences.weighInReminder`** (nouveau, facultatif) : `{ "enabled": false, "hour": 7,
  "minute": 30, "days": [1, 2, 3, 4, 5, 6, 7] }`, jours ISO (1 = lundi … 7 = dimanche), **omis** quand
  il vaut le réglage par défaut. Dans `preferences.json` et dans `@gymtracker_preferences` de la
  sauvegarde v2 (un ajout de champ, § 5.1 : les versions précédentes l'ignorent). Lecture (fichier ou
  import) : absent ou pas un objet → le défaut ; `enabled` non booléen → `false` ; `hour` hors 0–23 ou
  `minute` hors 0–59 → 07:30 ; `days` : entiers de 1 à 7 gardés, sans doublon, triés (`[]` = aucun
  jour) ; absent ou pas un tableau → tous les jours.

#### 15.13.6 Règles (pures, testées en JVM)

- **Poids** : poids du jour, « pesé ce jour-là » (pesée, séance terminée, séance en cours, séance
  sans poids, autre jour), dernière pesée connue et son ancienneté, poids d'une séance (le sien, la
  dernière pesée connue — pesée ou autre séance, le jour même compris —, rien).
- **Étape du poids** : champ affiché ou non (pesée du jour, séance du jour, rien aujourd'hui), aide
  (dernière pesée ou aucune), point de départ des pas.
- **Décision à la sonnerie** : désactivé, jour non coché, déjà pesé (pesée, séance terminée, séance en
  cours), pesée d'un autre jour, Health Connect (jamais lu : seules les données de l'app comptent),
  notifier.
- **Prochaine occurrence** : aujourd'hui ou un jour suivant, jours cochés seulement, aucun jour, heure
  passée ou exactement maintenant (→ occurrence suivante), passage à l'heure d'été (heure inexistante,
  23 h réelles entre deux sonneries) et à l'heure d'hiver (heure ambiguë, 25 h réelles).
- **Réglage** : résumé des jours ; lecture (absent, invalide, jours en double ou hors 1–7) ;
  `bodyWeight` absent relu à 0 et toujours écrit.
- **Le 0 jamais utilisé** : séance à `bodyWeight` 0 (ou négatif) → cellule CSV vide, aucun poids pour
  Health Connect (la séance, elle, part), aucun poids affiché (poids de la séance absent).

#### 15.13.7 Design system

Composants existants, sauf (DESIGN.md § 8 et § 9) :
- icône `notifications` (Material Symbols Rounded), bouton du rappel de pesée ;
- `MetricSurface` : un second bouton icône facultatif dans l'en-tête, avant `add` ;
- `FilterChip` : choix multiple (jours du rappel), lu comme une case à cocher, avec une description
  TalkBack en toutes lettres.

#### 15.13.8 Textes (FR et EN)

Clés Android (`strings.xml`) ; les libellés existants sont repris (« Poids de corps », « Commencer »,
« Saisis ton poids de corps », « Heure du rappel », « Fermer », « Désactivé », « aujourd'hui »,
« hier », « Nouvelle pesée », résumés des rappels de compléments).

| clé | FR | EN |
|---|---|---|
| `start_workout_weight_optional` | Ton poids aujourd'hui ? Facultatif. | Your weight today? Optional. |
| `start_workout_weight_last` | Dernière pesée : {{poids}} kg, {{ancienneté}}. | Last weigh-in: {{weight}} kg, {{age}}. |
| `common_days_ago` (pluriel) | il y a {{n}} jour / il y a {{n}} jours | {{n}} day ago / {{n}} days ago |
| `start_workout_weight_or_skip` | Saisis ton poids, ou touche « Passer ». | Enter your weight, or tap “Skip”. |
| `start_workout_skip` | Passer | Skip |
| `start_workout_weighed_today` | Déjà pesé aujourd'hui : {{poids}} kg. | Already weighed today: {{weight}} kg. |
| `settings_section_reminders` | Rappels | Reminders |
| `weigh_in_reminder_title` | Rappel de pesée | Weigh-in reminder |
| `weigh_in_reminder_off_subtitle` | Pour penser à te peser | So you remember to weigh in |
| `weigh_in_reminder_help` | Il ne sonne pas si tu t'es déjà pesé ce jour-là. | It stays silent if you've already weighed in that day. |
| `weigh_in_reminder_toggle` | Me rappeler de me peser | Remind me to weigh in |
| `weigh_in_reminder_time` / `_days` | Heure / Jours | Time / Days |
| `weigh_in_reminder_every_day` / `_no_day` | Tous les jours / Aucun jour | Every day / No day |
| `channel_weigh_in` | Rappel de pesée | Weigh-in reminder |
| `weigh_in_notif_title` | ⚖️ Pesée du jour | ⚖️ Daily weigh-in |
| `weigh_in_notif_body` | Monte sur la balance, puis note ton poids. | Step on the scale, then log your weight. |

### 15.14 Notification de mise à jour (🔄 D39)

La vérification des mises à jour (§ 3.17, 🔄 D25) ne se fait plus à l'ouverture de l'app mais
**une fois par jour, vers 18 h, en arrière-plan**, et une nouvelle version s'annonce par une
**notification** « Nouvelle version disponible », activée par défaut sauf quand Obtainium a installé
l'app (il prévient déjà). Ce réglage commande aussi la vérification automatique : coupé, l'app ne
contacte GitHub que sur « Vérifier maintenant ». Paramètres dit toujours où en est l'app
(« Version 1.0.42 · à jour ») et vérifie sur demande, notification coupée ou non. La section « Rappels » de Paramètres devient
« Notifications ». Aucune donnée de l'app n'est envoyée ; rien n'entre dans la sauvegarde.

#### 15.14.1 Réglage « Nouvelle version disponible »

- Activé ou non. **Par défaut : désactivé si l'app a été installée (ou sa dernière mise à jour
  faite) par Obtainium, activé sinon.** « Installée par Obtainium » = la détection du § 3.17
  (installateur ou initiateur `dev.imranr.obtainium`, `dev.imranr.obtainium.fdroid`, ou
  `com.android.vending`, nom qu'Obtainium emprunte quand il installe par Shizuku), relue à chaque
  démarrage.
- **Modifiable dans les deux cas.** Le choix n'est enregistré que lorsque l'utilisateur touche la
  ligne (`notifyEnabled`, § 15.14.6) ; tant qu'il n'a rien choisi, le défaut suit la source
  d'installation (une app installée à la main puis mise à jour par Obtainium passe à « désactivé »).
- Propre à l'appareil, **hors sauvegarde** : la source d'installation dépend du téléphone, un import
  ne change pas le réglage.
- Activer demande `POST_NOTIFICATIONS` si elle peut encore l'être (§ 7.1). Refusée : la vérification
  a lieu, rien ne s'affiche, et Paramètres montre « Notifications désactivées ».
- **Variante preview** (`com.musclenote.android.preview`, `BuildConfig.UPDATE_NOTIFICATIONS` faux) :
  ni tâche quotidienne ni notification, ligne masquée. « Vérifier maintenant » y marche (une
  vérification à la demande), et l'annonce de l'Accueil comme pour une app installée sans Obtainium.

#### 15.14.2 Vérification quotidienne

- **Quand elle existe** : **seulement si la notification est activée** (réglage effectif,
  § 15.14.1), quelle que soit la source d'installation. Réglage coupé : **aucune requête
  automatique** ; seul « Vérifier maintenant » contacte GitHub, et l'annonce de l'Accueil (§ 15.8.2,
  app installée sans Obtainium) n'apparaît qu'après une vérification manuelle. Preview : jamais.
- Elle **remplace la vérification au retour au premier plan** de D25 : ouvrir l'app ne déclenche
  plus de requête.
- **Tâche WorkManager unique `update-check`** : une tâche ponctuelle dont le délai mène à la
  **prochaine 18:00 locale** (strictement après maintenant ; heure murale, comme les rappels du
  § 7.3 : après un changement d'heure, toujours 18 h à l'horloge). À chaque passage, réussi ou non,
  elle se reprogramme pour la 18:00 suivante. Programmée au démarrage de l'app (gardée si elle
  existe déjà) et à chaque changement du réglage ; annulée quand rien ne la justifie plus.
  WorkManager la garde après un redémarrage du téléphone et une mise à jour de l'app.
- **Réseau requis** : contrainte `NetworkType.CONNECTED` (d'où `ACCESS_NETWORK_STATE`, § 1). Sans
  réseau à 18 h, elle part dès que le réseau revient, puis se reprogramme pour la 18:00 qui suit
  ce moment. Le système peut la décaler (économie de batterie) : « vers 18 h ».
- **Requête** : celle du § 3.17 (`GET https://api.github.com/repos/Bouss45/MuscleNote/releases/latest`,
  HTTPS, délai 10 s, `User-Agent: MuscleNote-Android/{version}`), aucune donnée de l'app. **Une
  requête automatique par jour au plus** ; « Vérifier maintenant » en fait une à chaque appui.
- **Échec** (pas de réseau, délai dépassé, réponse invalide, § 15.14.3) : rien d'enregistré ne
  change, aucune notification, **pas de nouvel essai avant la 18:00 suivante**.
- Une vérification à la fois (tâche ou « Vérifier maintenant ») : celle qui arrive pendant une autre
  attend sa fin, puis fait la sienne.

#### 15.14.3 Réponse et comparaison des versions

- **Réponse valide** : HTTP 200, corps JSON d'au plus 1 Mo avec `tag_name` et `html_url`, `draft` et
  `prerelease` faux (l'API ne renvoie déjà que la dernière Release ni brouillon ni préversion ; relu
  par sécurité), tag contenant au moins un nombre. Le texte de la Release (`body`, facultatif) donne
  la première ligne des notes ; l'APK est le premier fichier `.apk` (§ 3.17). **Tout le reste est un
  échec** : réponse absente (pas de réseau, délai), 404 (aucune Release), 403 / 429 (limite de
  l'API), JSON illisible, champ manquant, brouillon, préversion, tag sans nombre.
- **Comparaison avec l'app installée** : un tag **`v1.0.n`** (version `1.0.n` une fois le `v` retiré,
  `n` entier) donne le `versionCode` de la Release, **`1000 + n`** (règle de `release.yml`, § 15.1) ;
  la Release est plus récente si ce nombre dépasse le `versionCode` installé. Autre forme de tag
  (`v1.1.0`, `v2.0.3`, `v1.0.12-beta`) :
  comparaison des nombres de la version contre le `versionName` installé (§ 3.17), pour ne rien
  rater si le schéma change. Build local (`versionCode` 1) : toute Release est plus récente.
- **Entre deux Releases** (dernière notifiée, version ignorée) : nombres de la version (`1.0.13` >
  `1.0.12`).
- **Première ligne des notes** : le premier élément de liste (`- …` ou `* …`) du texte de la Release,
  sans le tiret ni les espaces autour, Markdown laissé tel quel (« Pesée facultative au début de
  séance et rappel de pesée (#10) ») ; 200 caractères au plus (points de code), « … » au-delà. Aucun
  élément de liste (« Aucun changement dans l'app : nouvelle compilation. », texte vide ou absent)
  → le texte par défaut `update_notif_body_default`.

#### 15.14.4 Notification

- **Après une vérification quotidienne réussie** (pas après « Vérifier maintenant » : la réponse
  est alors sous les yeux de l'utilisateur), si :
  1. le réglage effectif est activé (et ce n'est pas la preview) ;
  2. la dernière Release est plus récente que l'app installée (§ 15.14.3) ;
  3. ce n'est pas la version ignorée (`dismissedVersion` : « Ignorer cette version », ou « Plus
     tard » de l'Accueil) ;
  4. elle est plus récente que la dernière version notifiée (`notifiedVersion`), ou aucune ne l'a été.
- **Une seule fois par version** : `notifiedVersion` reçoit la version **seulement si la
  notification a pu s'afficher** (notifications de l'app autorisées, canal `updates` non coupé) ;
  sinon elle est retentée à la vérification suivante.
- **Seule la dernière version** : l'API ne donne que la dernière Release, donc les versions sorties
  entre deux vérifications sont sautées (1.0.10 installée, 1.0.11 à 1.0.13 publiées → une seule
  notification, la 1.0.13) ; une Release plus ancienne que la dernière notifiée (Release retirée)
  ne l'est pas non plus.
- **Elle remplace celle qui est affichée** : un seul identifiant de notification ; la 1.0.13 prend la
  place de la 1.0.12 encore affichée au lieu de s'empiler, et sonne (c'est une nouvelle version).
- **Contenu**, dans la langue de l'app au moment de l'affichage : canal `updates` (§ 7.1), petite
  icône `ic_notification` ; titre « MuscleNote {version} disponible » ; texte = la première ligne des
  notes (§ 15.14.3), en entier une fois dépliée ; auto-annulable ; visibilité publique (rien de
  personnel).
- **Toucher** = « Voir », et la notification disparaît.
- **« Voir »** : app installée par Obtainium et Obtainium présent → **ouvre Obtainium** (le paquet
  installateur, sinon l'autre variante ; un installateur au nom du Play Store → Obtainium s'il est
  présent ; paquets déclarés dans `<queries>`) ; sinon → **la page de la Release** (`html_url`) dans le
  navigateur. Destination choisie au moment de l'affichage et ouverte directement par la
  notification (`PendingIntent` d'activité : Android 12+ interdit le relais par un récepteur). Après
  l'action « Voir », la notification reste : elle part avec « Ignorer », un balayage ou
  l'installation.
- **« Ignorer cette version »** : `dismissedVersion` reçoit la version et la notification est
  retirée ; ni notification ni annonce de l'Accueil pour elle ; la version suivante sera notifiée.
- **Retirée** aussi au démarrage de l'app (dont le démarrage qui suit sa mise à jour,
  `MY_PACKAGE_REPLACED`, § 7.3) dès que la version notifiée n'est plus plus récente que l'app
  installée, et quand le réglage est désactivé.

#### 15.14.5 Paramètres

- **Section « Notifications »** (ex-« Rappels », même place, entre « Préférences » et « Données ») :
  1. **Notifications désactivées** — déplacée depuis « Préférences », mêmes conditions et même
     action (§ 15.10.3) ;
  2. **Rappel de pesée** (§ 15.13.3) ;
  3. une ligne par **complément** (§ 15.13.3) ;
  4. **Nouvelle version disponible** — ligne à cocher (`ListRowTrailing.Check`, toute la ligne la
     bascule) ; sous-titre « Chaque jour vers 18 h », ou « Obtainium te prévient déjà » si l'app est
     installée par Obtainium ; absente de la preview.
- **Section « À propos »** — la ligne « Mises à jour » est remplacée par :
  1. **« Version 1.0.42 · à jour »** (dernière vérification réussie, rien de plus récent),
     **« Version 1.0.42 · 1.0.43 disponible »**, ou « Version 1.0.42 » (jamais vérifiée) ; sous-titre
     « Vérifiée le 14 nov., 08:12 », « Pas encore vérifiée », ou « Vérification impossible : pas de
     connexion ? » après un échec depuis le lancement de l'app ; ligne d'information, sans action ;
  2. **« Vérifier maintenant »** — vérifie tout de suite, Obtainium ou non, notification coupée ou
     non ; sous-titre « Vérification… » et ligne inactive pendant la requête ; ne notifie pas ;
  3. si une version plus récente est connue, même ignorée (Paramètres dit toujours où en est l'app) :
     **« Télécharger la 1.0.43 »** (chevron → l'APK dans le navigateur, sinon la page de la Release,
     comme avant) pour une app installée sans Obtainium, **« Ouvrir Obtainium »** (chevron → la
     destination de « Voir ») sinon.
- « Gérées par Obtainium » disparaît : la vérification à la demande vaut aussi avec Obtainium.

#### 15.14.6 Format des données

`update_check.json` (§ 4.2 ; propre à l'appareil, hors sauvegarde) gagne deux champs facultatifs :
`notifyEnabled` (booléen ; absent = le défaut du § 15.14.1) et `notifiedVersion` (chaîne ; absente =
aucune version notifiée). Les champs existants (`checkedAt`, `latestVersion`, `pageUrl`, `apkUrl`,
`dismissedVersion`) ne changent pas. Un ancien fichier se relit tel quel : ni migration ni version de
format.

#### 15.14.7 Règles (pures, testées en JVM)

- **Versions** : tag `v1.0.n` → `versionCode` `1000 + n` ; plus récente, identique ou plus ancienne
  que le `versionCode` installé ; autre forme de tag → nombres de la version ; tag sans nombre jamais
  plus récent.
- **Réponse** : valide (avec ou sans notes, sans APK) ; invalide (JSON cassé, sans `tag_name`,
  brouillon, préversion, tag sans nombre) ; absente (la source échoue) → échec, rien d'enregistré,
  aucune notification.
- **Notifier ou non** : réglage coupé, preview, pas plus récente, version ignorée, déjà notifiée
  (une seule notification par version), Release plus ancienne que la dernière notifiée (version
  intermédiaire sautée), plus récente que la notifiée (elle la remplace), affichage impossible (non
  enregistrée, retentée), « Vérifier maintenant » (jamais).
- **Réglage par défaut** selon la source d'installation (Obtainium, Obtainium F-Droid, nom du Play
  Store, installateur inconnu, autre installateur) ; un choix enregistré l'emporte.
- **Tâche programmée** seulement si le réglage effectif est activé (Obtainium ou non, choix
  enregistré ou défaut), jamais dans la preview ; annulée quand il est coupé.
- **Prochaine vérification** : avant, après et à 18:00 pile ; passage à l'heure d'été et d'hiver.
- **Première ligne des notes** : premier élément `-` ou `*`, aucun élément, texte absent, troncature.
- **Retrait** : notification retirée au démarrage quand la version notifiée est installée.

#### 15.14.8 Design system

Aucun composant nouveau : des `ListRow` dans des sections existantes ; la ligne à cocher
(`ListRowTrailing.Check`, déjà dans les sheets) entre dans la liste de Paramètres.

#### 15.14.9 Textes (FR et EN)

Clés Android (`strings.xml`). Repris : « Vérification… », « Vérification impossible : pas de
connexion ? », « Télécharger la {version} », « Notifications désactivées ». Retirés :
`settings_update_title`, `settings_update_subtitle_managed`, `settings_update_subtitle_never`,
`settings_update_subtitle_up_to_date`, `settings_update_subtitle_available` ;
`settings_section_reminders` devient `settings_section_notifications`.

| clé | FR | EN |
|---|---|---|
| `settings_section_notifications` | Notifications | Notifications |
| `update_notify_title` | Nouvelle version disponible | New version available |
| `update_notify_subtitle` | Chaque jour vers 18 h | Every day around 6 pm |
| `update_notify_subtitle_obtainium` | Obtainium te prévient déjà | Obtainium already lets you know |
| `settings_version` | Version {{version}} | Version {{version}} |
| `settings_version_up_to_date` | Version {{version}} · à jour | Version {{version}} · up to date |
| `settings_version_available` | Version {{version}} · {{latest}} disponible | Version {{version}} · {{latest}} available |
| `settings_version_checked` | Vérifiée le {{date}} | Checked {{date}} |
| `settings_version_never` | Pas encore vérifiée | Not checked yet |
| `settings_update_check_now` | Vérifier maintenant | Check now |
| `settings_update_open_obtainium` | Ouvrir Obtainium | Open Obtainium |
| `channel_updates` | Mises à jour | Updates |
| `update_notif_title` | MuscleNote {{version}} disponible | MuscleNote {{version}} available |
| `update_notif_body_default` | Une nouvelle version de MuscleNote est prête. | A new version of MuscleNote is ready. |
| `update_notif_view` | Voir | View |
| `update_notif_ignore` | Ignorer cette version | Skip this version |

### 15.15 Calendrier du Journal (🔄 D41)

En tête du Journal, le **mois en calendrier**, comme sur la maquette `T2-Journal` (planche « Listes
groupées », DESIGN.md) : on voit d'un coup d'œil les jours d'entraînement, et toucher un jour ouvre
une sheet **« Journée »** qui rassemble ce qui a été noté ce jour-là. Les semaines de la liste
affichent « 3/3 · validée » selon la règle de la série de l'Accueil, pour que les deux écrans disent
toujours la même chose. Le calendrier ne montre que des données existantes ; seul ajout au format :
l'**historique des prises de compléments**, champ facultatif sans migration (§ 15.15.4). La liste par
semaine (§ 15.8.4) reste sous le calendrier.

#### 15.15.1 Calendrier du mois

- **Place** : groupe du héros du Journal (gabarit hub groupé, DESIGN.md § 10.1), 8 sous la barre de
  titre ; dessous, « Depuis le début » puis les semaines, inchangés. Sans séance terminée, le Journal
  garde son état vide seul (pas de calendrier).
- **En-tête** : le mois et l'année, « Novembre 2026 » (`November 2026`), puis les flèches « Mois
  précédent » / « Mois suivant » (boutons icône, cibles 48), inactives au premier et au dernier mois.
- **Changer de mois** : les flèches, ou un **glissement horizontal** sur la grille (mois suivant vers
  la gauche, précédent vers la droite).
- **Mois accessibles** : du mois de la **plus ancienne donnée** (séance terminée, pesée, calories ou
  eau notées, prise de complément) au **mois courant** ; jamais un mois à venir. Le Journal s'ouvre
  sur le mois courant ; le mois choisi est gardé tant que l'onglet reste ouvert.
- **Jours de la semaine** : semaine **du lundi** au dimanche (ISO, comme le § 3.6), initiales de la
  langue : « L M M J V S D » (`M T W T F S S`).
- **Grille** : cases vides avant le 1er du mois (rien du mois voisin), 4 à 6 rangées selon le mois ;
  la hauteur de 6 rangées est toujours réservée, pour que changer de mois ne fasse pas sauter la liste.
- **Un jour** :
  - au moins une **séance terminée** ce jour-là (jour local de son **début**, comme le § 15.8.4) →
    **rond plein** (`inverse`), chiffre en `onInverse` 700 ;
  - **aujourd'hui** → **rond vide** (anneau `textSecondary`), chiffre 700 ; aujourd'hui avec une
    séance → rond plein entouré de l'anneau ;
  - jours **à venir** → chiffre en `textSecondary`, inactifs ;
  - autres jours → chiffre en `textPrimary`.
  La séance en cours ne compte pas (elle n'est pas terminée).
- **Résumé** sous la grille (filet `separator` au-dessus, légende en texte secondaire) : « 6 séances
  en novembre », « 1 séance en novembre », « Aucune séance en novembre » — séances terminées du mois.
- **Toucher** un jour passé ou aujourd'hui → sheet « Journée » (§ 15.15.2), même sans donnée.
- **Accessibilité** : chaque jour est un bouton lu par TalkBack en toutes lettres, avec ses séances :
  « jeudi 12 novembre, 2 séances », « samedi 14 novembre, aucune séance », « dimanche 15 novembre,
  aujourd'hui, aucune séance » ; action « Voir la journée » ; jours à venir lus « à venir », inactifs.
  Le mois est lu comme un titre ; les flèches font tout ce que fait le glissement. Texte agrandi :
  le chiffre rétrécit pour tenir dans son rond, rien n'est coupé.

#### 15.15.2 Sheet « Journée »

- **Titre** : la date en toutes lettres, « Jeudi 12 novembre » (avec l'année si elle n'est pas
  l'année en cours). Bottom sheet **en listes groupées** : fond `background`, groupes
  `surfaceRaised`, étiquettes en capitales (DESIGN.md § 9.1). Lecture seule.
- **Sections**, dans cet ordre, chacune **masquée si elle est vide** :
  1. **« Séances »** — une ligne par séance terminée commencée ce jour-là (jour local), de la plus
     ancienne à la plus récente : nom affiché (§ 3.1) ; sous-titre heure de début et durée, « 18:30 ·
     1 h 12 » (durée omise si nulle) ; à droite le volume, comme dans le Journal (§ 15.8.4).
     **Toucher → résumé** de la séance (§ 15.12) ; la sheet se ferme.
  2. **« Poids »** — le poids de ce jour selon la règle de Progrès (§ 3.10, § 15.13.1), à droite de
     sa ligne : la pesée du jour, ligne « Pesée » ; sinon le poids saisi au début d'une séance de ce
     jour (la plus récente avec un poids), ligne « Saisi au début de Push ».
  3. **« Compléments pris »** — une ligne par complément pris ce jour-là d'après son **historique des
     prises** (§ 15.15.4), dans l'ordre des compléments : nom ; sous-titre le dosage s'il est renseigné
     et, à partir de deux doses, « 2 doses ». Les prises antérieures à cette version n'ont pas
     d'historique : la section est alors masquée. Seule exception, **aujourd'hui** : un complément
     coché « pris aujourd'hui » (`taken`, § 3.12) compte au moins pour une dose, même coché avant la
     mise à jour. Un complément supprimé emporte son historique.
  4. **« Calories et eau »** (🔄 D42 : **« Nutrition »**, avec les protéines du jour, § 15.16.7) —
     « Calories » « 1 850 kcal », sous-titre « objectif 2 600 kcal » ;
     « Eau » « 1,5 L », sous-titre « objectif 2,5 L » ; objectifs en vigueur **ce jour-là** (objectifs
     datés, § 3.12). Une ligne seulement si sa valeur est supérieure à 0.
- **Jour sans rien** : la phrase « Rien de noté ce jour-là. » à la place des sections.
- En bas, bouton texte « Fermer » ; retour ou voile ferment aussi.

#### 15.15.3 Semaines validées dans le Journal

- **Pas de réglage** : le Journal reprend la règle de la série de l'Accueil (§ 3.6) — une semaine est
  **validée à 3 séances** terminées (`WorkoutStats.VALIDATED_WEEK_SESSIONS`, partagé), pour que
  l'Accueil et le Journal affichent toujours la même chose.
- À droite de l'étiquette de chaque semaine, à la place de « 3 séances » :
  - moins de 3 → « 2/3 », en **texte secondaire (gris)**, semaine en cours comprise ;
  - 3 ou plus → « 3/3 · validée » (« 4/3 · validée »), en **`up` (vert)** ;
  - **jamais rouge** : une semaine passée non validée reste grise ;
  - la section « Date inconnue » garde son compteur « 2 séances ».
  TalkBack : « 2 séances sur 3 », « 3 séances sur 3, semaine validée ».
- Un objectif réglable (Accueil et Journal ensemble) est une piste (§ 15.6).

#### 15.15.4 Format des données : historique des prises

- **`Supplement.intakes`** (nouveau, facultatif) : objet `{ "AAAA-MM-JJ": doses }` — jour local de la
  prise → nombre de doses (entier ≥ 1). **Un seul enregistrement par complément et par jour** : la
  taille reste bornée (un complément pris chaque jour ajoute une quinzaine d'octets par jour, environ
  5 ko par an). **Omis** quand il est vide.
- **Écriture** : cocher « pris aujourd'hui » écrit `intakes[aujourd'hui] = 1` (l'app ne compte pas
  encore plusieurs doses ; le nombre est prêt pour la piste du § 15.6 ; 🔄 D42 : la coche écrit les
  **doses par jour** du complément, § 15.16.4) ; décocher retire
  `intakes[aujourd'hui]`. `taken` (`supplements_taken.json`, § 3.12) est écrit comme avant et reste la
  source de « pris aujourd'hui ».
- **Lecture** (fichier `supplements.json` ou import, `@gymtracker_supplements` de la sauvegarde v2) :
  absent ou pas un objet → historique vide ; entrées dont la clé n'est pas un jour valide (§ 10.3) ou
  dont la valeur n'est pas un entier ≥ 1 → écartées. **Ancien fichier** sans `intakes` → relu tel
  quel, historique vide. Ni nouvelle version, ni migration ; les versions précédentes ignorent le
  champ (et le perdent si elles réécrivent le fichier).
- Sauvegarde et export JSON v2 le portent avec le complément ; l'export CSV ne change pas.

#### 15.15.5 Règles (pures, testées en JVM)

- **Grille du mois** : mois commençant un lundi (aucune case vide), un dimanche (6 cases vides),
  février de 28 jours commençant un lundi (4 rangées), février bissextile (29 jours), mois sur
  6 rangées ; ordre des jours du lundi au dimanche.
- **Mois accessibles** : de la plus ancienne donnée (séance, pesée, calories, eau, prise) au mois
  courant ; sans donnée, le mois courant seul ; mois précédent / suivant aux bornes.
- **Jours à séance et résumé du mois** : séances terminées seulement, jour local de leur début, 0, 1
  ou n séances.
- **Journée** : séances du jour local de début (séance commencée à 23:30 et finie après minuit → la
  veille ; séance commencée à 00:15 heure locale, enregistrée la veille en UTC → ce jour-là), ordre ;
  poids (pesée, sinon poids d'une séance, sinon rien) ; compléments pris d'après l'historique, jour
  **sans prise**, coche d'aujourd'hui sans historique ; calories et eau avec l'objectif du jour ;
  sections vides ; jour vide.
- **Historique des prises** : cocher écrit une dose aujourd'hui, décocher la retire, recocher ne crée
  pas de second enregistrement ; jours précédents conservés ; complément supprimé ; **ancien fichier
  sans historique** relu tel quel ; lecture d'entrées invalides ; champ omis quand il est vide.
- **Semaines** : 0, 2, 3 et 4 séances (« n/3 », validée à 3), semaine en cours, même règle que la série
  de l'Accueil.

#### 15.15.6 Design system

- **Composant nouveau** `MonthCalendar` (DESIGN.md § 9.1) : en-tête du mois et ses flèches, initiales
  des jours, grille de jours (rond plein, rond vide, à venir), résumé sous un filet ; glissement entre
  les mois. Rangées de 48 (cibles tactiles ; 38 sur la maquette).
- **Sheet groupée** : `SheetSurface` sur `background`, pour y poser des groupes `surfaceRaised`.
- Le reste vient de l'existant : `ListGroup`, `ListRow`, `SectionHeader` groupé.

#### 15.15.7 Textes (FR et EN)

Clés Android (`strings.xml`) ; repris : « Fermer », « Calories », « Eau », les formats de durée, de
volume, de poids et de litres, les noms de séance, le dosage des compléments.

| clé | FR | EN |
|---|---|---|
| `journal_calendar_previous` / `_next` | Mois précédent / Mois suivant | Previous month / Next month |
| `journal_calendar_month_sessions` (pluriel) | {{n}} séance en {{mois}} / {{n}} séances en {{mois}} | {{n}} workout in {{month}} / {{n}} workouts in {{month}} |
| `journal_calendar_month_none` | Aucune séance en {{mois}} | No workouts in {{month}} |
| `journal_calendar_sessions` (pluriel) | {{n}} séance / {{n}} séances | {{n}} workout / {{n}} workouts |
| `journal_calendar_no_session` | aucune séance | no workout |
| `journal_calendar_today` | aujourd'hui | today |
| `journal_calendar_upcoming` | à venir | upcoming |
| `journal_calendar_open_day` | Voir la journée | Open the day |
| `journal_day_sessions` | Séances | Workouts |
| `journal_day_weight` | Poids | Weight |
| `journal_day_weigh_in` | Pesée | Weigh-in |
| `journal_day_weight_from_session` | Saisi au début de {{séance}} | Entered when starting {{workout}} |
| `journal_day_supplements` | Compléments pris | Supplements taken |
| `journal_day_doses` (pluriel) | {{n}} dose / {{n}} doses | {{n}} dose / {{n}} doses |
| `journal_day_nutrition` | Calories et eau | Calories and water |
| `journal_day_goal` | objectif {{valeur}} | goal {{value}} |
| `journal_day_empty` | Rien de noté ce jour-là. | Nothing logged that day. |
| `journal_week_count` | {{n}}/{{objectif}} | {{n}}/{{goal}} |
| `journal_week_validated` | {{n}}/{{objectif}} · validée | {{n}}/{{goal}} · done |
| `journal_week_count_description` (pluriel) | {{n}} séances sur {{objectif}} | {{n}} workouts out of {{goal}} |
| `journal_week_validated_description` (pluriel) | {{n}} séances sur {{objectif}}, semaine validée | {{n}} workouts out of {{goal}}, week done |

Lecture d'un jour par TalkBack : la date en toutes lettres (« jeudi 12 novembre »), puis
`journal_calendar_today` pour aujourd'hui, puis `journal_calendar_upcoming` (jour à venir),
`journal_calendar_sessions` ou `journal_calendar_no_session`, séparés par des virgules.

### 15.16 Protéines, stock des compléments et moyenne sur 7 jours (🔄 D42)

Trois fonctions que la maquette `T2-Nutrition` (planche « Listes groupées », DESIGN.md) dessine et que
l'app n'avait pas (§ 15.6) : un **objectif de protéines** qui suit le poids de corps, le **stock** de
chaque complément décompté à chaque prise, et la **moyenne sur 7 jours** des calories, de l'eau et des
protéines. Le groupe « Aujourd'hui » prend la forme de la maquette : l'**eau** et les **protéines** y
sont deux lignes identiques, chacune avec son bouton +. Les protéines du jour rejoignent la sheet
« Journée » du Journal. Tout est fait des gabarits (hub groupé, formulaires au pavé, DESIGN.md § 10.1,
§ 10.4) et des composants existants. Le format gagne des **champs facultatifs** (§ 15.16.8) : ni
nouvelle version, ni migration ; une sauvegarde ou un fichier d'avant cette version se relit tel quel
(aucune protéine, objectif de 1,6 g par kg, une dose par jour, stock non suivi).

Ordre du hub Nutrition (§ 15.10.1) : barre ; groupe du héros « Aujourd'hui » (§ 15.16.2) ;
« Compléments » ; « Moyenne sur 7 jours » (§ 15.16.6).

#### 15.16.1 Protéines

- **Donnée** : grammes de protéines par jour local (`proteinLog`, § 15.16.8), entiers ≥ 0, saisis
  comme les calories (§ 15.16.3).
- **Objectif** (réglage `proteinGoal`), deux modes :
  - **par kg de poids** (par défaut) : facteur de **1,2 à 2,2 g par kg**, au dixième, **1,6** par
    défaut ; objectif = poids × facteur, **arrondi au gramme** (demi vers le haut : 82,5 kg × 1,8 =
    148,5 → 149 g ; 78,4 kg × 1,6 = 125,44 → 125 g), calculé exactement (poids au centième × facteur
    au dixième, en millièmes de gramme entiers), sans erreur d'arrondi ;
  - **valeur fixe** : grammes entiers > 0, saisis au pavé.
- **Poids utilisé** : la **dernière pesée connue** aujourd'hui (§ 15.13.1) — la pesée manuelle, sinon
  le poids saisi au début d'une séance (séance en cours comprise), le jour le plus récent l'emportant.
  Une nouvelle pesée change l'objectif tout de suite.
- **Sans aucune pesée** : valeur fixe uniquement. Le mode « par kg » n'est pas proposé dans le
  formulaire ; s'il était choisi (pesées supprimées depuis), l'objectif est la valeur fixe si elle a
  été saisie, sinon **pas d'objectif**.
- **Non daté** : contrairement aux calories et à l'eau (🔄 D15), l'objectif de protéines n'a pas
  d'historique ; il ne vaut que pour le jour même. Changer de mode ou de valeur vaut aussitôt.
- **Atteint** ssi il y a un objectif et grammes du jour ≥ objectif.

#### 15.16.2 Groupe « Aujourd'hui »

Remplace le point 2 du § 15.10.1 (surface « Aujourd'hui »), comme sur la maquette :

- **En-tête** : légende « Aujourd'hui · calories » ; à droite le **bouton +** (bouton icône plein, rond
  de 40 `surfaceControl` dans une cible de 48) « Ajouter des calories » → formulaire Calories en
  « Ajouter » (§ 15.10.2, inchangé).
- **Chiffre héros** (kcal du jour), **état** par rapport à l'objectif et **piste** : inchangés (D35).
- La rangée de stats et les deux boutons « Calories » / « Eau » disparaissent : l'eau devient une
  ligne ; le compte des compléments pris passe à droite de l'étiquette « Compléments », « 1 sur 3
  pris » (s'il y a au moins un complément ; TalkBack : « 1 complément sur 3 pris aujourd'hui »).
- Sous la piste, un filet en retrait puis **deux lignes identiques**, « Eau » puis « Protéines »
  (`ListRow` dans le groupe) :
  - titre « Eau » / « Protéines » ; sous-titre « objectif 2,5 L » / « objectif 150 g », une fois
    l'objectif atteint « objectif 2,5 L atteint » / « objectif 150 g atteint » (le mot accompagne la
    couleur, DESIGN.md § 1), protéines sans objectif « Pas d'objectif » ;
  - à droite la valeur du jour, « 1,5 L » (« 750 ml » sous 1 000 ml, au dixième de litre au-delà,
    § 2.11) / « 112 g », en **gris** (`textSecondary`) tant que l'objectif n'est pas atteint (ou sans
    objectif), en **vert** (`up`) une fois atteint (eau : ml ≥ objectif du jour, § 15.10.5 ;
    protéines : § 15.16.1) ;
  - puis le **bouton +** (le même que celui des calories) « Ajouter de l'eau » / « Ajouter des
    protéines » → formulaire de saisie en « Ajouter » (§ 15.16.3) ;
  - **toucher le reste de la ligne** → son **détail** (§ 15.16.3).

#### 15.16.3 Formulaires et détails

Bottom sheets (gabarit formulaire, DESIGN.md § 10.4) ; retour ou voile : fermée sans rien enregistrer.

**Protéines** (bouton + de la ligne) — le formulaire des calories (§ 15.10.2) en grammes :
- titre « Protéines » ; aide « Aujourd'hui : 112 g sur 150 g. », sans objectif « Aujourd'hui : 112 g. » ;
- puces « Ajouter » (choisie à l'ouverture) / « Total du jour » ; champ héros en **g** (« Ajouter » :
  vide ; « Total du jour » : les grammes du jour, champ neuf) ;
- lignes d'information « Nouveau total » « 142 g » (en « Ajouter ») et « Objectif quotidien » « 150 g »
  (« À définir » sans objectif), chevron → formulaire d'objectif ;
- pavé : grammes **entiers**, 4 chiffres au plus, pas **−5** / **+5** ; « ✓ Ajouter » ou
  « ✓ Enregistrer » ; mêmes validations que les calories (« Ajouter » > 0, « Total du jour » champ non
  vide, 0 remet le jour à zéro ; sinon « Saisis une quantité » et vibration d'erreur), résultat borné
  à ≥ 0 ; la sheet se ferme (vibration de succès).

**Eau** (bouton + de la ligne) : le formulaire Eau du § 15.10.2, inchangé, ouvert en « Ajouter ».

**Objectif de protéines** (« Objectif quotidien ») : la même sheet passe à ce formulaire ; retour → la
saisie.
- titre « Objectif de protéines » ;
- **au moins un poids connu** : puces « Par kg » / « Valeur fixe », la puce du mode enregistré choisie
  à l'ouverture ;
  - « Par kg » : aide « Avec ta dernière pesée : 78,4 kg. » ; champ héros prérempli avec le facteur
    (« 1,6 », unité « g/kg », neuf), séparateur décimal de la langue, une décimale (3 caractères au
    plus) ; pas **−0,1** / **+0,1**, bornés à 1,2 et 2,2 ; ligne d'information « Objectif » « 125 g »
    recalculée à chaque touche (« — » si le facteur est hors bornes) ; « ✓ Enregistrer » : facteur
    entre 1,2 et 2,2 → mode « par kg » avec ce facteur, sinon « Choisis entre 1,2 et 2,2 g par kg » et
    vibration d'erreur ;
  - « Valeur fixe » : aide « Le même objectif chaque jour. » ; champ héros en g, prérempli avec la
    valeur fixe enregistrée, sinon l'objectif par kg du jour (neuf) ; entiers, 4 chiffres, pas
    **−10** / **+10** ; « ✓ Enregistrer » : valeur > 0 → mode « valeur fixe », sinon « Saisis un
    objectif supérieur à 0 » ;
- **aucun poids connu** : pas de puces ; aide « Sans pesée, l'objectif est une valeur fixe. » ; le
  formulaire « Valeur fixe » seul.
- Changer de puce garde le facteur et la valeur fixe enregistrés ; seul « ✓ Enregistrer » change le
  mode. Enregistré → retour à l'étape d'où l'objectif a été ouvert.

**Détail de l'eau / des protéines** (toucher la ligne) — sheet de lignes d'information, comme celle d'un
complément :
- titre « Eau » / « Protéines » ; aide « Aujourd'hui : 1,5 L sur 2,5 L. » (« Aujourd'hui : 112 g. »
  sans objectif) ;
- ligne d'information « Aujourd'hui » « 1,5 L » / « 112 g », chevron → formulaire de saisie en
  « Total du jour » (pour corriger le total) ;
- ligne d'information « Objectif quotidien » : « 2,5 L » ; protéines « 125 g · 1,6 g/kg » en mode par
  kg, « 150 g » en valeur fixe, « À définir » sans objectif ; chevron → formulaire d'objectif ;
- ligne d'information « Moyenne sur 7 jours » « 2,1 L » / « 138 g » (§ 15.16.6 ; « — » sans jour),
  sans chevron ;
- bouton principal « Ajouter » (icône `add`) → formulaire de saisie en « Ajouter » ;
- bouton texte « Fermer ».
- Un formulaire ouvert depuis le détail y revient au retour ; une saisie enregistrée ferme la sheet,
  un objectif enregistré revient au détail.

#### 15.16.4 Stock des compléments

- **Doses par jour** (réglage nouveau du complément, `dosesPerDay`) : entier de **1 à 20**, **1 par
  défaut**. Cocher « pris aujourd'hui » enregistre ce nombre de doses ce jour-là dans l'historique des
  prises (§ 15.15.4, qui en enregistrait une). Changer les doses par jour alors que le complément est
  coché aujourd'hui met à jour la prise du jour ; les jours passés gardent la leur.
- **Stock** (facultatif, `stock`), en **doses** : saisi au pavé (§ 15.16.5). Le stock restant
  aujourd'hui = les doses saisies, moins les doses prises **depuis** la saisie, d'après l'historique
  des prises : celles des jours suivant le jour de la saisie, et celles de ce jour-là ajoutées après la
  saisie (une dose déjà prise au moment de la saisie n'est pas retirée une seconde fois ; la décocher
  ensuite la remet dans le stock). **Jamais sous 0** : une prise sur un stock vide le laisse à 0.
- **« Racheté »** : ajoute des doses au stock restant (0 compris) ; le nouveau stock repart de ce
  jour-là (§ 15.16.8).
- **Jours restants** = partie entière de `stock restant / doses par jour` (25 doses à 2 par jour →
  « 12 jours » ; 1 dose à 2 par jour → « 0 jour ») ; doses par jour ≤ 0 (fichier retouché) → pas de
  jours, le stock s'affiche en doses (« 24 doses »), jamais en rouge.
- **Stock bas** : jours restants **≤ au seuil** du complément (`lowStockDays`, **7 jours** par défaut,
  0 à 99) → en **rouge** (`down`) ; « à 7 jours » = rouge dès qu'il en reste 7. Pas de notification
  (piste, § 15.6).
- **Ligne du complément** (§ 15.10.1) : avec un stock suivi, les jours restants à droite, **avant la
  case** « pris aujourd'hui », comme sur la maquette (« 12 jours », « 4 jours » en rouge) ; TalkBack
  lit « Stock : 12 jours » ou « Stock bas : 4 jours ». **Sans stock saisi, rien ne s'affiche** (la
  ligne est celle d'avant). Le sous-titre gagne « 2 doses par jour » à partir de deux doses :
  « 5 g par prise · 2 doses par jour · Rappel à 08:00 ».
- **Sheet du complément** (§ 15.10.2), entre les rappels et « Supprimer le complément » :
  - ligne d'information « Doses par jour » « 1 », chevron → formulaire ;
  - ligne d'information « Stock » : « Non suivi », ou « 24 doses · 12 jours », chevron → formulaire ;
  - stock suivi seulement : ligne d'information « Stock bas » « à 7 jours », chevron → formulaire ; puis
    le bouton texte **« Racheté »** (icône `add`) → formulaire « Ajouter du stock ».
- Supprimer un complément emporte son stock ; l'export CSV ne change pas.

#### 15.16.5 Formulaires du stock

La sheet du complément passe au formulaire touché (comme l'objectif des calories) ; retour → l'étape
d'où il a été ouvert (la sheet du complément, ou « Ajouter du stock » pour le seuil ouvert depuis
lui) ; « ✓ Enregistrer » ou « ✓ Ajouter » enregistre, vibre et revient à cette étape. Saisies au
pavé, entiers, champ prérempli neuf.

- **Stock** : titre « Stock » ; aide « Doses qu'il te reste aujourd'hui. » ; champ héros en « doses »,
  prérempli avec le stock restant (vide s'il n'est pas suivi) ; 4 chiffres, pas **−10** / **+10** ;
  ligne d'information « Jours restants » « 12 jours » (« — » champ vide), recalculée à chaque touche ;
  stock suivi : bouton texte discret « Ne plus suivre le stock » (retire le stock et revient à la
  sheet) ; « ✓ Enregistrer » : champ non vide (0 compris) → stock = cette valeur aujourd'hui, sinon
  « Saisis une quantité ».
- **Ajouter du stock** (« Racheté », d'après `T2-Stock`) : titre « Ajouter du stock » ; aide
  « Créatine · il reste 8 doses, soit 4 jours. », les jours en `down` quand le stock est bas
  (DESIGN.md § 10.4) ; champ héros vide en « doses ajoutées » ; 4 chiffres, pas **−10** / **+10** ;
  lignes d'information « Nouveau stock » « 38 doses · 19 jours » (recalculée) et « Stock bas » « à 7
  jours » (chevron → formulaire du seuil) ; « ✓ Ajouter » : valeur > 0, sinon « Saisis une quantité ».
  Les puces de saisie rapide de la maquette (« +250 g ») ne sont pas reprises.
- **Stock bas** : titre « Stock bas » ; aide « En rouge quand il reste ce nombre de jours ou moins. » ;
  champ héros en « jours », prérempli (7) ; 2 chiffres, pas **−1** / **+1** ; « ✓ Enregistrer » : champ
  non vide, 0 à 99, sinon « Saisis un nombre de jours entre 0 et 99 ».
- **Doses par jour** : titre « Doses par jour » ; aide « Cocher « pris aujourd'hui » compte ces
  doses. » ; champ héros en « doses », prérempli ; 2 chiffres, pas **−1** / **+1** ; « ✓ Enregistrer » :
  1 à 20, sinon « Saisis entre 1 et 20 doses ».

#### 15.16.6 Moyenne sur 7 jours

- **Groupe titré** « Moyenne sur 7 jours », après « Compléments » : un `ListGroup` contenant une
  rangée de stats (padding 16), comme « Depuis le début » du Journal :
  - « 2 410 » / « kcal par jour » ;
  - « 2,1 L » / « d'eau » (« 750 ml » sous 1 000 ml, au dixième de litre au-delà, comme la ligne
    « Eau ») ;
  - « 138 g » / « de protéines ».
- **Fenêtre** : les **7 jours avant aujourd'hui** (de J − 7 à J − 1, jours locaux) ; la journée en
  cours n'y entre pas, elle tirerait la moyenne vers le bas jusqu'au soir.
- **Seulement les jours avec des données**, mesure par mesure : la moyenne des calories porte sur les
  jours de la fenêtre où les calories sont > 0, de même pour l'eau et les protéines ; un jour à 0
  (remis à zéro) ne compte pas. Arrondi à l'unité (demi vers le haut) : kcal, ml, g.
- Une mesure **sans aucun jour** dans la fenêtre → « — ». **Aucune** des trois → le groupe est masqué.
- La même moyenne figure dans le détail de l'eau et des protéines (§ 15.16.3).
- Pas de lien « Tendances » (pas d'écran de courbes dans Nutrition, § 15.6).

#### 15.16.7 Sheet « Journée »

- La section « Calories et eau » de la sheet « Journée » (§ 15.15.2, point 4) devient **« Nutrition »**
  et gagne, après l'eau, la ligne **« Protéines »** « 112 g » quand les protéines de ce jour-là sont
  > 0. **Sans sous-titre** : l'objectif de protéines n'est pas daté (§ 15.16.1), celui d'un jour passé
  n'est pas connu. Les calories et l'eau gardent leur objectif du jour.
- La section est masquée quand les trois valeurs sont nulles ; un jour avec des protéines seulement
  n'est plus un « jour sans rien ».
- **Mois accessibles** du calendrier (§ 15.15.1) : des protéines notées comptent comme une donnée.

#### 15.16.8 Format des données

Champs facultatifs, **omis quand ils valent leur défaut** ; les versions précédentes de l'app les
ignorent (et les perdent si elles réécrivent le fichier), comme `intakes` (§ 15.15.4). Lus dans le
fichier (`daily_tracker.json`, `supplements.json`) comme à l'import (`@gymtracker_daily_tracker`,
`@gymtracker_supplements` de la sauvegarde v2) ; une valeur invalide ne fait jamais refuser l'import.

- **`DailyTracker.proteinLog`** : `{ "AAAA-MM-JJ": grammes }`, comme `caloriesLog` (entiers ≥ 0 par jour
  valide ; le reste écarté) ; absent → vide.
- **`DailyTracker.proteinGoal`** : `{ "mode": "perKg", "perKg": 1.6 }` (défaut, omis) ou
  `{ "mode": "fixed", "perKg": 1.6, "grams": 150 }`. Lecture : absent ou pas un objet → le défaut ;
  `mode` inconnu → `perKg` ; `perKg` hors de 1,2 – 2,2 ou pas un nombre → 1,6, sinon arrondi au
  dixième ; `grams` pas un entier > 0 → absent ; `mode` `fixed` sans `grams` → `perKg`.
- **`Supplement.dosesPerDay`** : entier ; absent, pas un entier ou hors de 1 – 20 → 1.
- **`Supplement.stock`** : `{ "doses": 24, "day": "2026-11-12", "dayIntake": 1 }` — `doses` comptées le
  jour local `day`, après `dayIntake` doses prises ce jour-là (omis à 0). Stock restant aujourd'hui =
  `max(0, doses − (intakes[day] − dayIntake) − Σ intakes[d > day])`. Saisir le stock ou « Racheté »
  le réécrit : `{ doses: v (ou restant + ajout), day: aujourd'hui, dayIntake: intakes[aujourd'hui] }`.
  Lecture : pas un objet, `doses` pas un entier ≥ 0 ou `day` pas un jour valide → stock non suivi ;
  `dayIntake` pas un entier ≥ 0 → 0.
- **`Supplement.lowStockDays`** : entier ; absent, pas un entier ou hors de 0 – 99 → 7.

#### 15.16.9 Règles (pures, testées en JVM)

- **Objectif de protéines** : par kg avec la dernière pesée connue (pesée, sinon poids d'une séance,
  séance en cours comprise ; le jour le plus récent), **arrondi** (125,44 → 125, 148,5 → 149, valeurs
  exactes sans erreur de virgule flottante), facteur aux bornes ; **pas de pesée** (valeur fixe, ou pas
  d'objectif ; mode « par kg » non proposé) ; valeur fixe ; formulaire (facteur hors bornes refusé, pas
  bornés à 1,2 et 2,2).
- **Objectif atteint ou non** : sous l'objectif, exactement l'objectif, au-dessus, pas d'objectif ;
  même règle pour l'eau.
- **Protéines du jour** : ajouter, total du jour, jamais sous 0.
- **Stock** : stock restant après des prises (jours suivants, prise du jour faite avant et après la
  saisie, prise décochée) et après « Racheté » ; **jamais négatif** (prises au-delà du stock, rachat
  depuis 0) ; doses par jour enregistrées par la coche et mises à jour le jour même.
- **Jours restants** : division entière, **0 dose par jour** (pas de jours), seuil (en dessous, égal,
  au-dessus, seuil 0), stock non suivi.
- **Moyenne sur 7 jours** : fenêtre de J − 7 à J − 1 (aujourd'hui et le 8e jour exclus), **jours vides
  ou à 0 exclus**, mesure sans jour (« — »), aucune donnée (groupe masqué), arrondi.
- **Journée** : protéines du jour (affichées si > 0), jour avec des protéines seulement, mois
  accessibles.
- **Lecture** : anciens fichiers sans les nouveaux champs relus tels quels, valeurs invalides, champs
  omis au défaut.

#### 15.16.10 Design system

Aucun composant nouveau ; des variantes de composants existants, décrites dans DESIGN.md avec leurs
aperçus :
- **`StudioIconButton`** plein (`filled`) : rond de 40 `surfaceControl` (pressé `surfacePressed`), icône
  22 `textPrimary`, dans une cible de 48 — les boutons + du groupe « Aujourd'hui » ;
- **`ListRowTrailing.ValueAction`** : la valeur (colorée par sa tendance) puis ce bouton plein, qui a
  sa propre action et son libellé TalkBack ; le reste de la ligne garde son toucher (eau, protéines) ;
- **`ListRowTrailing.Check`** : une **valeur** facultative avant la case (`bodyLineStrong`, `down` pour
  un stock bas), avec sa description TalkBack ;
- **`DailySurface`** redessinée comme la maquette : légende et bouton + plein, chiffre héros, état,
  piste, puis des lignes (`ListRow` en disposition de groupe) après un filet en retrait ; plus de
  rangée de stats ni de paire de boutons ;
- **`highlightedHelp`** : la phrase d'aide d'un formulaire dont la donnée clé prend sa couleur
  (« soit **4 jours** » en `down`, DESIGN.md § 10.4), à côté de `statusHelp`.
- Le reste : `ListRow` (valeur colorée `valueTrend`), `ListGroup` + `StatRow`, `SectionHeader` groupé
  (mention à droite), `FormSheetTemplate`, `FormHeroField`, `InfoRow`, `FilterChip`, `NumericKeypad`,
  `SheetSurface`, `StudioButton`.

#### 15.16.11 Textes (FR et EN)

Clés Android (`strings.xml`) ; repris : « Aujourd'hui », « Eau », « Ajouter », « Total du jour »,
« Nouveau total », « Objectif quotidien », « Saisis une quantité », « Saisis un objectif supérieur à
0 », « Fermer », `journal_day_goal` (« objectif {{valeur}} »), `journal_day_doses` (« {{n}} dose(s) »),
les formats de kcal, ml et litres. Devenues inutiles et retirées : `nutrition_water_of`,
`nutrition_water_reached`, `nutrition_supplements_taken_label`.

| clé | FR | EN |
|---|---|---|
| `nutrition_today_calories` | Aujourd'hui · calories | Today · calories |
| `nutrition_calories_add` / `_water_add` / `_protein_add` (accessibilité) | Ajouter des calories / Ajouter de l'eau / Ajouter des protéines | Add calories / Add water / Add protein |
| `nutrition_detail_open` (accessibilité) | Voir le détail | Open details |
| `nutrition_goal_reached` | objectif {{valeur}} atteint | {{value}} goal reached |
| `nutrition_supplements_taken_count` | {{n}} sur {{total}} pris | {{n}} of {{total}} taken |
| `nutrition_supplements_taken_description` (pluriel, accessibilité) | {{n}} complément sur {{total}} pris aujourd'hui / {{n}} compléments sur {{total}} pris aujourd'hui | {{n}} of {{total}} supplement taken today / {{n}} of {{total}} supplements taken today |
| `nutrition_protein` | Protéines | Protein |
| `nutrition_grams_value` | {{valeur}} g | {{value}} g |
| `nutrition_protein_no_goal` | Pas d'objectif | No goal |
| `nutrition_entry_help_no_goal` | Aujourd'hui : {{valeur}}. | Today: {{value}}. |
| `nutrition_goal_undefined` | À définir | Not set |
| `nutrition_protein_goal_title` | Objectif de protéines | Protein goal |
| `nutrition_protein_mode_per_kg` / `_mode_fixed` | Par kg / Valeur fixe | Per kg / Fixed amount |
| `nutrition_protein_per_kg_help` | Avec ta dernière pesée : {{poids}} kg. | With your last weigh-in: {{weight}} kg. |
| `nutrition_protein_fixed_help` | Le même objectif chaque jour. | The same goal every day. |
| `nutrition_protein_no_weight_help` | Sans pesée, l'objectif est une valeur fixe. | Without a weigh-in, the goal is a fixed amount. |
| `nutrition_protein_goal_label` | Objectif | Goal |
| `nutrition_protein_goal_per_kg_value` | {{objectif}} · {{facteur}} g/kg | {{goal}} · {{factor}} g/kg |
| `nutrition_protein_per_kg_invalid` | Choisis entre 1,2 et 2,2 g par kg | Choose between 1.2 and 2.2 g per kg |
| `nutrition_average_title` | Moyenne sur 7 jours | 7-day average |
| `nutrition_average_kcal` / `_water` / `_protein` | kcal par jour / d'eau / de protéines | kcal a day / water / protein |
| `nutrition_stock` | Stock | Stock |
| `nutrition_stock_untracked` | Non suivi | Not tracked |
| `nutrition_stock_days` (pluriel) | {{n}} jour / {{n}} jours | {{n}} day / {{n}} days |
| `nutrition_stock_value` | {{doses}} · {{jours}} | {{doses}} · {{days}} |
| `nutrition_stock_description` / `_low_description` (accessibilité) | Stock : {{jours}} / Stock bas : {{jours}} | Stock: {{days}} / Low stock: {{days}} |
| `nutrition_doses_per_day` | Doses par jour | Doses per day |
| `nutrition_doses_per_day_subtitle` (pluriel) | {{n}} dose par jour / {{n}} doses par jour | {{n}} dose a day / {{n}} doses a day |
| `nutrition_doses_per_day_help` | Cocher « pris aujourd'hui » compte ces doses. | Ticking “taken today” counts these doses. |
| `nutrition_doses_per_day_invalid` | Saisis entre 1 et 20 doses | Enter between 1 and 20 doses |
| `nutrition_doses_unit` | doses | doses |
| `nutrition_stock_help` | Doses qu'il te reste aujourd'hui. | Doses you have left today. |
| `nutrition_stock_days_left` | Jours restants | Days left |
| `nutrition_stock_stop` | Ne plus suivre le stock | Stop tracking stock |
| `nutrition_stock_low` | Stock bas | Low stock |
| `nutrition_stock_low_value` | à {{jours}} | at {{days}} |
| `nutrition_stock_low_help` | En rouge quand il reste ce nombre de jours ou moins. | Shown in red with this many days left or fewer. |
| `nutrition_stock_low_invalid` | Saisis un nombre de jours entre 0 et 99 | Enter a number of days between 0 and 99 |
| `nutrition_days_unit` | jours | days |
| `nutrition_restocked` | Racheté | Restocked |
| `nutrition_restock_title` | Ajouter du stock | Add stock |
| `nutrition_restock_help` | {{nom}} · il reste {{doses}}, soit {{jours}}. | {{name}} · {{doses}} left, that's {{days}}. |
| `nutrition_restock_help_doses` (sans doses par jour valables) | {{nom}} · il reste {{doses}}. | {{name}} · {{doses}} left. |
| `nutrition_restock_unit` | doses ajoutées | doses added |
| `nutrition_new_stock` | Nouveau stock | New stock |
| `journal_day_nutrition` (modifiée) | Nutrition | Nutrition |

### 15.17 Carte du corps et muscles travaillés (🔄 D43)

La maquette « Carte du corps » (planche `T2-Carte-corps`, composant `T2-Corps`, export
`design/claude-design/corps/carte-du-corps.svg`) dessine **17 muscles**, de face et de dos. L'app gagne
une **liste fixe de muscles** (§ 15.17.1), les **muscles principaux et secondaires** de chaque exercice
intégré (§ 15.17.2, donnée embarquée, jamais dans les fichiers de l'utilisateur), un **choix facultatif**
des muscles d'un exercice perso (§ 15.17.3), la section **« Muscles travaillés »** de la fiche exercice
(§ 15.17.5), un **filtre « Par muscle »** dans le sélecteur d'exercices (§ 15.17.6) et une **sheet
« Muscle »** (§ 15.17.7). L'écran de la séance en cours ne change pas (seul le sélecteur qu'il ouvre gagne
le filtre). Le **groupe musculaire** (§ 6.2) reste tel quel : il classe les exercices, décide du cardio
(§ 4.1) et filtre « Par groupe ». Un composant nouveau, la **carte du corps** (`BodyMap`, § 15.17.4). Le
format gagne deux **champs facultatifs** sur les exercices perso (§ 15.17.8) : ni nouvelle version, ni
migration.

#### 15.17.1 Muscles

Liste fixe ; l'**ordre ci-dessous est l'ordre d'affichage** (noms d'un exercice, puces, éléments TalkBack
de la carte). Les ids sont ceux des formes du SVG ; **figés** : ils sont stockés dans les exercices perso.
« Vue » = la moitié de la carte montrée par la sheet « Muscle » (celle où le muscle se voit le mieux) ; un
muscle visible sur les deux vues est une seule forme : il se colore des deux côtés.

| # | id | nom FR | nom EN | nom savant FR | nom savant EN | vue |
|---|---|---|---|---|---|---|
| 1 | `trapezes` | Trapèzes | Traps | trapèze | trapezius | dos |
| 2 | `epaules-avant` | Épaules avant | Front delts | deltoïde antérieur | anterior deltoid | face |
| 3 | `epaules-cote` | Épaules côté | Side delts | deltoïde moyen | lateral deltoid | face |
| 4 | `epaules-arriere` | Épaules arrière | Rear delts | deltoïde postérieur | posterior deltoid | dos |
| 5 | `pectoraux` | Pecs | Chest | grand pectoral | pectoralis major | face |
| 6 | `biceps` | Biceps | Biceps | biceps brachial | biceps brachii | face |
| 7 | `triceps` | Triceps | Triceps | triceps brachial | triceps brachii | dos |
| 8 | `avant-bras` | Avant-bras | Forearms | brachio-radial, fléchisseurs et extenseurs du poignet | brachioradialis, wrist flexors and extensors | face |
| 9 | `abdominaux` | Abdos | Abs | grand droit de l'abdomen | rectus abdominis | face |
| 10 | `obliques` | Obliques | Obliques | obliques externe et interne | external and internal obliques | face |
| 11 | `dorsaux` | Dorsaux | Lats | grand dorsal | latissimus dorsi | dos |
| 12 | `lombaires` | Lombaires | Lower back | érecteurs du rachis | erector spinae | dos |
| 13 | `fessiers` | Fessiers | Glutes | grand et moyen fessiers | gluteus maximus and medius | dos |
| 14 | `quadriceps` | Quadriceps | Quads | quadriceps fémoral | quadriceps femoris | face |
| 15 | `ischio-jambiers` | Ischio-jambiers | Hamstrings | biceps fémoral, semi-tendineux et semi-membraneux | biceps femoris, semitendinosus and semimembranosus | dos |
| 16 | `adducteurs` | Adducteurs | Adductors | grand, long et court adducteurs | adductor magnus, longus and brevis | face |
| 17 | `mollets` | Mollets | Calves | triceps sural (gastrocnémien et soléaire) | triceps surae (gastrocnemius and soleus) | dos |

**Rôle** (une phrase, sheet « Muscle » ; anglais au § 15.17.11) :

| id | rôle FR |
|---|---|
| `trapezes` | Hausse, rapproche et abaisse les omoplates : il porte les charges lourdes et tient le haut du dos dans les tirages. |
| `epaules-avant` | Lève le bras devant soi : il pousse dans tous les développés, surtout au-dessus de la tête. |
| `epaules-cote` | Écarte le bras sur le côté : c'est lui qui élargit la carrure, travaillé par les élévations latérales. |
| `epaules-arriere` | Tire le bras vers l'arrière : il aide dans les tirages et le rowing, et équilibre l'épaule face aux pecs. |
| `pectoraux` | Rapproche le bras du buste et le pousse vers l'avant : c'est le moteur des développés, des dips et des pompes. |
| `biceps` | Plie le coude et tourne la paume vers le haut : le moteur des curls, qui aide dans les tractions et les tirages. |
| `triceps` | Tend le coude : il finit chaque développé et chaque dips, et fait les deux tiers du volume du bras. |
| `avant-bras` | Ferment la main et tiennent le poignet : c'est la prise, qui travaille dès qu'on tient lourd. |
| `abdominaux` | Enroule le buste en rapprochant les côtes du bassin, et gaine le tronc sous les charges lourdes. |
| `obliques` | Tournent et inclinent le buste, et le gardent droit quand la charge est d'un seul côté. |
| `dorsaux` | Ramène le bras vers le bas et vers l'arrière : le moteur des tractions, des tirages et du rowing. |
| `lombaires` | Redressent la colonne et la tiennent droite : ils travaillent dans le soulevé de terre, le squat et le rowing. |
| `fessiers` | Tendent la hanche et écartent la jambe : le moteur des squats, des fentes et du soulevé de terre. |
| `quadriceps` | Tend le genou : le moteur des squats, de la presse et du leg extension. |
| `ischio-jambiers` | Plient le genou et tendent la hanche : leg curl et soulevé de terre. |
| `adducteurs` | Ramènent la jambe vers l'intérieur : ils tiennent les genoux dans le squat et les fentes. |
| `mollets` | Tendent la cheville, talon levé : debout surtout les jumeaux, assis surtout le soléaire. |

#### 15.17.2 Muscles des exercices intégrés

**Principal** = le muscle qui fait le travail ; **secondaire** = il aide. Chaque exercice du catalogue
(§ 6.1) a **au moins un principal** et zéro ou plusieurs secondaires, **jamais le même muscle dans les
deux**. Donnée embarquée dans le code (catalogue), comme les noms : jamais stockée, sauvegardée ni
importée — une version qui la corrige s'applique à tout l'historique. Listes dans l'ordre du § 15.17.1.

| # | id | principaux | secondaires |
|---|---|---|---|
| 1 | `deadlift` | `lombaires`, `fessiers`, `ischio-jambiers` | `trapezes`, `avant-bras`, `dorsaux`, `quadriceps`, `adducteurs` |
| 2 | `pullup` | `dorsaux` | `trapezes`, `epaules-arriere`, `biceps`, `avant-bras` |
| 3 | `row` | `trapezes`, `dorsaux` | `epaules-arriere`, `biceps`, `lombaires` |
| 4 | `latpull` | `dorsaux` | `trapezes`, `epaules-arriere`, `biceps` |
| 5 | `tiragebucheron` | `dorsaux` | `trapezes`, `epaules-arriere`, `biceps` |
| 6 | `tirageh` | `trapezes`, `dorsaux` | `epaules-arriere`, `biceps` |
| 7 | `bench` | `pectoraux` | `epaules-avant`, `triceps` |
| 8 | `incline` | `pectoraux` | `epaules-avant`, `triceps` |
| 9 | `fly` | `pectoraux` | `epaules-avant` |
| 10 | `dips` | `pectoraux`, `triceps` | `epaules-avant` |
| 11 | `dbench` | `pectoraux` | `epaules-avant`, `triceps` |
| 12 | `dincline` | `pectoraux` | `epaules-avant`, `triceps` |
| 13 | `pecfly` | `pectoraux` | `epaules-avant` |
| 14 | `curl` | `biceps` | `avant-bras` |
| 15 | `hammercurl` | `biceps`, `avant-bras` | — |
| 16 | `concentcurl` | `biceps` | `avant-bras` |
| 17 | `pushdown` | `triceps` | — |
| 18 | `overheadtri` | `triceps` | — |
| 19 | `skullcrusher` | `triceps` | — |
| 20 | `barfront` | `triceps` | — |
| 21 | `squat` | `fessiers`, `quadriceps` | `lombaires`, `ischio-jambiers`, `adducteurs` |
| 22 | `legpress` | `quadriceps` | `fessiers`, `ischio-jambiers`, `adducteurs` |
| 23 | `legcurl` | `ischio-jambiers` | `mollets` |
| 24 | `legext` | `quadriceps` | — |
| 25 | `lunges` | `fessiers`, `quadriceps` | `ischio-jambiers`, `adducteurs` |
| 26 | `calfraize` | `mollets` | — |
| 27 | `adductorpress` | `adducteurs` | — |
| 28 | `abductorpress` | `fessiers` | — |
| 29 | `calfsit` | `mollets` | — |
| 30 | `ohp` | `epaules-avant` | `trapezes`, `epaules-cote`, `pectoraux`, `triceps` |
| 31 | `lateralraise` | `epaules-cote` | `trapezes`, `epaules-avant` |
| 32 | `frontraise` | `epaules-avant` | `epaules-cote`, `pectoraux` |
| 33 | `crunch` | `abdominaux` | `obliques` |
| 34 | `plank` | `abdominaux` | `obliques` |
| 35 | `legrise` | `abdominaux` | `obliques`, `quadriceps` |
| 36 | `treadmill` | `quadriceps`, `mollets` | `fessiers`, `ischio-jambiers` |
| 37 | `bike` | `quadriceps` | `fessiers`, `ischio-jambiers`, `mollets` |
| 38 | `wristcurl` | `avant-bras` | — |
| 39 | `wristext` | `avant-bras` | — |
| 40 | `reversecurl` | `avant-bras` | `biceps` |
| 41 | `pinchgrip` | `avant-bras` | — |
| 42 | `farmerwalk` | `trapezes`, `avant-bras` | `abdominaux`, `obliques`, `lombaires` |

Choix notables : le brachial (sous le biceps) compte comme `biceps` et le brachio-radial comme
`avant-bras` (curl marteau, curl inversé) ; le droit fémoral, fléchisseur de la hanche, fait du
`quadriceps` un secondaire du relevé de jambes ; le moyen fessier fait des `fessiers` le principal de
l'abducteur ; le chef claviculaire du grand pectoral fait des `pectoraux` un secondaire du développé
militaire et des élévations frontales (comme la maquette) ; au tapis, `quadriceps` et `mollets` portent
la marche et la course quelle que soit la pente.

#### 15.17.3 Exercices perso

- Deux champs **facultatifs** (§ 15.17.8). Un exercice perso créé avant cette version, ou sans choix,
  n'a **aucun muscle** : pas de section « Muscles travaillés » dans sa fiche (§ 15.17.5), jamais retenu
  par un muscle du filtre (§ 15.17.6), absent des sheets « Muscle » ; sa ligne garde son groupe en
  sous-titre.
- **Formulaire** « Nouvel exercice » / « Modifier l'exercice » (§ 15.11.2) : après « Groupe
  musculaire », « Muscles principaux » puis « Muscles secondaires », chacun sous son libellé en **puces de
  filtre à choix multiple** (les 17, dans l'ordre du § 15.17.1, sur plusieurs lignes, lues comme des cases
  à cocher). Choisir dans une liste un muscle déjà choisi dans l'autre l'en retire. Rien d'obligatoire :
  [Ajouter] / [Enregistrer] ne dépendent que du nom ; des secondaires sans principal sont acceptés. Le
  formulaire défile ; « Modifier » part des muscles enregistrés.
- Changer les muscles ne touche pas aux séances (comme le nom et le groupe, § 3.9).

#### 15.17.4 Carte du corps

- **Dessin** : les formes du SVG, converties pour Compose (tracés absolus `M`, `C`, `L`, `Z`) ; repère de
  420 × 460, vue de face à gauche (x 0 – 200), vue de dos à droite (x 220 – 420) ; une vue seule recadre
  sur 200 × 460. La carte prend la **hauteur** demandée, la largeur suit (420 / 460, ou 200 / 460 pour une
  vue). Silhouette `bodyBase` cernée d'un contour `bodyOutline` de 1 dp ; chaque muscle rempli selon son
  état et séparé de ses voisins par un **écart** de 1,5 dp de la couleur de la surface sous la carte (fond
  ou groupe / sheet). Les traits ne grandissent pas avec la carte (nette de 60 à 900 dp).
- **États d'un muscle** (aucune couleur de sens : jaune, vert et rouge gardent le leur, DESIGN.md § 1) :
  **principal** plein `textPrimary` ; **secondaire** `bodySecondary` ; **non travaillé** `bodyRest` ;
  **sélectionné** (filtre) plein `textPrimary` entouré d'un anneau de 1,75 dp séparé du muscle par 2,5 dp
  d'écart.
- **Tailles** : grande, 360 dp de haut (filtre du sélecteur, réduite si la largeur manque) ; petite,
  150 dp (fiche) ; une vue, 140 dp (sheet « Muscle »).
- **Toucher** (fiche et filtre seulement) : le muscle sous le doigt ; hors de toute forme, le muscle le
  plus proche à **12 dp** au plus ; plus loin (silhouette, tête, vide), rien. Les formes ne se chevauchent
  pas ; au même point, la dernière dessinée l'emporte.
- **Accessibilité** : une carte touchable donne à TalkBack **un élément par muscle** (17, dans l'ordre du
  § 15.17.1), centré sur sa forme dans sa vue (§ 15.17.1), lu par son nom et son état : « Pecs,
  principal », « Triceps, secondaire », « Lombaires, non travaillé » (fiche, action « Voir le muscle ») ;
  dans le filtre, une case à cocher « Pecs », cochée ou non (action « Filtrer »). La carte de la sheet
  « Muscle », non touchable, est décorative (le titre nomme le muscle).

#### 15.17.5 Fiche exercice : « Muscles travaillés »

- **Dernière section** de la fiche (§ 15.9.1) : après « Séances », ou après l'état vide « Aucune séance ».
  En-tête de section de la fiche (`SectionHeader` de 20, la fiche n'est pas en listes groupées).
- Une rangée : à gauche la **petite carte** (150 dp, les deux vues, principaux pleins, secondaires gris,
  écart couleur du fond) ; à 20 dp, la colonne des **noms** : étiquette « Principal » / « Principaux »
  (selon le nombre), un nom par ligne (pastille de légende pleine, nom en 600), puis « Secondaire » /
  « Secondaires » et leurs noms (pastille grise, nom en 500) ; sans secondaire, pas de seconde étiquette.
  Chaque nom est une ligne d'au moins 48 dp qui ouvre la **sheet « Muscle »** (§ 15.17.7) ; toucher la
  carte aussi, sur n'importe quel muscle, travaillé ou non. La carte reste en haut quand la colonne est plus
  haute qu'elle ; en texte agrandi les noms passent à la ligne.
- Exercice perso sans muscle : **pas de section**. Exercice archivé : la section comme les autres.

#### 15.17.6 Sélecteur d'exercices : filtre « Par muscle »

- Sous la recherche, un **contrôle segmenté** « Par groupe » / « Par muscle » ; « Par groupe » par
  défaut, le choix est gardé tant que la sheet reste ouverte (rotation comprise), pas d'une ouverture à
  l'autre.
- **« Par groupe »** : les puces de groupe du § 15.11.2, inchangées.
- **« Par muscle »** : en tête de la liste (elle défile avec), la **grande carte**, centrée, « Face » et
  « Dos » en légende sous chaque moitié ; tous les muscles au repos sauf ceux du filtre. **Toucher un
  muscle l'ajoute au filtre ou l'en retire** (sélectionné = plein + anneau ; plusieurs muscles possibles ;
  vibration légère). Sous la carte, une **puce sélectionnée** par muscle choisi (ordre du § 15.17.1) puis
  « Effacer » (bouton texte, vide le filtre) ; sans muscle choisi, « Touche un muscle pour filtrer la
  liste. » (`caption`). Toucher la puce d'un muscle ouvre sa **sheet « Muscle »** (§ 15.17.7), étape du
  sélecteur, d'où « Retirer du filtre ».
- **Filtre** : exercices non archivés dont **au moins un** des muscles choisis est principal **ou**
  secondaire, **et** dont le nom affiché contient le texte de la recherche (§ 10.6) ; aucun muscle
  choisi → tous (texte seul). Le groupe n'est pas appliqué dans ce mode. **Ordre** : d'abord les
  exercices où un muscle choisi est principal, puis ceux où il n'est que secondaire, chacun dans l'ordre
  du sélecteur (catalogue, puis perso par création).
- **Sous-titre** des lignes en « Par muscle » : les principaux puis, après « · », les secondaires, en
  minuscules après le premier nom (« Pecs · épaules avant, triceps », « Lombaires, fessiers,
  ischio-jambiers · trapèzes, … ») ; sans muscle, le groupe. En « Par groupe », le groupe, comme avant.
- Lignes, cases, ajouts, appui long et « Aucun exercice trouvé » inchangés.

#### 15.17.7 Sheet « Muscle »

- Ouverte depuis la **fiche** (bottom sheet sur la fiche) et depuis le **filtre** (étape de la sheet du
  sélecteur, comme ses formulaires : retour → la liste, § 15.11.2).
- **Haut** : la carte d'une vue (140 dp, la vue du muscle, § 15.17.1 ; ce muscle plein, le reste au repos,
  décorative) ; à 16 dp, l'étiquette « Muscle » (`label`, `textSecondary`), le **nom** (`titleScreen`, lu
  comme un titre) et le **nom savant** (`bodyLine` en `textSecondary`).
- Le **rôle** (`paragraph`).
- **« Exercices »** (étiquette, nombre à droite) puis une ligne par exercice non archivé qui le
  travaille (catalogue et perso) : **principaux d'abord**, puis secondaires, chacun dans l'ordre du
  sélecteur ; sous-titre « Principal » / « Secondaire ».
  - Depuis la fiche : toucher → la **fiche** de cet exercice (la sheet se ferme ; l'exercice de la fiche
    ouverte ferme seulement la sheet), chevron à droite.
  - Depuis le filtre : la ligne à cocher du sélecteur (cochée et inactive si l'exercice est déjà dans la
    séance, toucher l'ajoute).
- **Bas** : depuis le filtre, « Retirer du filtre » (bouton secondaire) ; « Fermer » (bouton texte).
- Le contenu défile s'il dépasse (texte agrandi, muscle très sollicité).

#### 15.17.8 Format des données

- **`Exercise.primaryMuscles`**, **`Exercise.secondaryMuscles`** : tableaux d'ids du § 15.17.1,
  **facultatifs, omis vides** ; les versions précédentes de l'app les ignorent (et les perdent si elles
  réécrivent le fichier), comme `intakes` (§ 15.15.4). Lus dans `exercises.json` comme à l'import
  (`@gymtracker_exercises`) : pas un tableau → vide ; éléments qui ne sont pas un id connu écartés,
  doublons retirés, ordre ramené à celui du § 15.17.1 ; un muscle présent dans les deux → principal
  seulement. Une valeur invalide ne fait jamais refuser l'import (§ 5.3). Un ancien fichier se relit sans
  muscle.
- Les muscles des exercices intégrés ne sont pas dans le format (§ 15.17.2). Export CSV inchangé (§ 8).

#### 15.17.9 Règles (pures, testées en JVM)

- **Catalogue** : chaque exercice intégré a au moins un principal ; chaque id de muscle existe dans la
  liste ; aucun muscle à la fois principal et secondaire ; les 42 exercices sont couverts ; les ids de la
  liste sont exactement les formes de la carte.
- **Filtre par muscle** : retenu par un principal, par un secondaire ; plusieurs muscles (union) ;
  principaux avant secondaires ; avec la recherche ; aucun muscle choisi (tous) ; exercice perso sans
  muscle (jamais retenu par un muscle, présent sans choix) ; exercice perso avec muscles ; archivés
  absents.
- **Exercices d'un muscle** (sheet) : principaux puis secondaires, perso compris, archivés absents.
- **Formulaire** : un muscle choisi dans une liste quitte l'autre.
- **Lecture** : ancien fichier sans les champs relu sans muscle ; ids inconnus, doublons, muscle dans
  les deux listes, pas un tableau ; champs omis vides à l'écriture.
- **Carte** : toucher dans une forme, juste à côté (la plus proche à 12 dp), loin (rien) ; vue seule.

#### 15.17.10 Design system

- **Composant nouveau** `BodyMap` (DESIGN.md § 9.1) : la carte du § 15.17.4, ses tailles, états et
  éléments TalkBack ; sa géométrie pure (tracés, mise à l'échelle, toucher) testée en JVM, comme
  `CurveGeometry` ; la ligne de légende `MuscleLegendRow` (pastille + nom, graisse selon le rôle) de la
  fiche.
- **Jetons nouveaux** (DESIGN.md § 2), valeurs de la maquette : `bodyBase` (#1F2125 / #E4E4E1),
  `bodyOutline` (#45484E / #B4B6BA), `bodyRest` (#2E3035 / #D3D3CF), `bodySecondary` (#7E8187 /
  #6F7277) ; le principal est `textPrimary`, l'écart la couleur de la surface.
- Le reste : `SectionHeader`, `ListRow`, `SegmentedControl`, `FilterChip` (choix multiple),
  `LabeledField`, `SheetSurface`, `StudioBottomSheet`, `StudioButton`, `SheetTitle`.

#### 15.17.11 Textes (FR et EN)

Clés Android (`strings.xml`) ; noms, noms savants et rôles FR au § 15.17.1. Repris : les
`exercise_picker_*` existants et « Fermer ».

| clé | FR | EN |
|---|---|---|
| `muscle_name_<id>` (17) | Trapèzes … Mollets | Traps … Calves |
| `muscle_scientific_<id>` (17) | trapèze … | trapezius … |
| `muscle_role_<id>` (17) | rôles du § 15.17.1 | ci-dessous |
| `exercise_muscles_title` | Muscles travaillés | Muscles worked |
| `exercise_muscles_primary` (pluriel) | Principal / Principaux | Primary / Primary |
| `exercise_muscles_secondary` (pluriel) | Secondaire / Secondaires | Secondary / Secondary |
| `muscle_sheet_label` | Muscle | Muscle |
| `muscle_sheet_exercises` | Exercices | Exercises |
| `muscle_role_primary` / `_secondary` | Principal / Secondaire | Primary / Secondary |
| `muscle_state_primary` / `_secondary` / `_rest` (accessibilité) | {{nom}}, principal / {{nom}}, secondaire / {{nom}}, non travaillé | {{name}}, primary / {{name}}, secondary / {{name}}, not worked |
| `muscle_open` (accessibilité) | Voir le muscle | Show muscle |
| `muscle_filter_action` (accessibilité) | Filtrer | Filter |
| `body_map_front` / `_back` | Face / Dos | Front / Back |
| `exercise_picker_by_group` / `_by_muscle` | Par groupe / Par muscle | By group / By muscle |
| `exercise_picker_muscle_hint` | Touche un muscle pour filtrer la liste. | Tap a muscle to filter the list. |
| `exercise_picker_clear` | Effacer | Clear |
| `muscle_filter_remove` | Retirer du filtre | Remove from filter |
| `exercise_picker_primary_muscles` / `_secondary_muscles` | Muscles principaux / Muscles secondaires | Primary muscles / Secondary muscles |

Rôles en anglais (`muscle_role_<id>`) :

| id | EN |
|---|---|
| `trapezes` | Raises, squeezes and lowers the shoulder blades: it carries heavy loads and holds the upper back in rows. |
| `epaules-avant` | Raises the arm in front of you: it pushes in every press, especially overhead. |
| `epaules-cote` | Lifts the arm out to the side: it gives the shoulders their width, trained by lateral raises. |
| `epaules-arriere` | Pulls the arm back: it helps in rows and pulldowns and balances the shoulder against the chest. |
| `pectoraux` | Brings the arm across the body and pushes it forward: it drives presses, dips and push-ups. |
| `biceps` | Bends the elbow and turns the palm up: it drives curls and helps in pull-ups and rows. |
| `triceps` | Straightens the elbow: it finishes every press and dip and makes up two thirds of the upper arm. |
| `avant-bras` | Close the hand and hold the wrist: they are your grip, working whenever you hold something heavy. |
| `abdominaux` | Curls the trunk by bringing the ribs toward the pelvis, and braces the core under heavy loads. |
| `obliques` | Rotate and bend the trunk, and keep it upright when the load is on one side. |
| `dorsaux` | Pulls the arm down and back: it drives pull-ups, pulldowns and rows. |
| `lombaires` | Straighten the spine and keep it upright: they work in deadlifts, squats and rows. |
| `fessiers` | Extend the hip and move the leg outward: they drive squats, lunges and deadlifts. |
| `quadriceps` | Straightens the knee: it drives squats, the leg press and leg extensions. |
| `ischio-jambiers` | Bend the knee and extend the hip: leg curls and deadlifts. |
| `adducteurs` | Bring the leg inward: they steady the knees in squats and lunges. |
| `mollets` | Point the foot, heel up: standing works mostly the gastrocnemius, seated mostly the soleus. |
