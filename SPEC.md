# SPEC.md — MuscleNote, spécification de référence pour la réécriture native

> **Rôle de ce document.** C'est la référence unique pour réécrire l'app Expo/React Native
> (`artifacts/mobile/`) en Android natif (Kotlin + Jetpack Compose). Il décrit le
> comportement **cible de l'app native** : le comportement de l'app Expo actuelle, auquel
> s'appliquent les décisions prises au § 11.
>
> - **🔄 Dn** marque un écart volontaire par rapport à l'app Expo ; `n` renvoie à la décision
>   n° n du § 11. Le comportement Expo d'origine est rappelé quand il compte (import des
>   anciennes sauvegardes, migration).
> - Ce qui est propre à iOS ou au web est omis.
> - Les points encore à trancher sont au § 12.
>
> Conventions : les chemins de fichiers sont relatifs à `artifacts/mobile/`. Les clés
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

---

## 1. Vue d'ensemble

- **MuscleNote** : carnet de musculation personnel, en français et en anglais, qui suit **la
  langue du système** (🔄 D9). Installé par APK, **pas de publication Play Store**.
- **100 % hors ligne** : aucun appel réseau, aucun compte, **pas de permission `INTERNET`**.
  Seule exception indirecte : la police Inter est obtenue via les polices téléchargeables
  de Google Play Services (🔄 D12) ; l'app elle-même ne fait aucune requête.
- **Identité Android** (🔄 D5) : nouvel `applicationId` (proposition :
  `com.musclenote.android`), nom affiché « MuscleNote », orientation **portrait** uniquement.
  L'app Expo (`com.musclenote.app`) peut rester installée pendant la transition ; le
  transfert des données est décrit au § 5.5.
- **Socle technique** :
  - Kotlin, Jetpack Compose, Material 3, Navigation Compose ;
  - coroutines + `StateFlow` ; kotlinx.serialization ;
  - stockage en fichiers JSON (§ 4.2, 🔄 D2), pas de Room ;
  - `AlarmManager` (alarmes exactes) pour le minuteur et les rappels (🔄 D6, D7) ;
  - AppCompat pour la langue par application (🔄 D9) ;
  - `androidx.compose.ui:ui-text-google-fonts` pour Inter (🔄 D12) ;
  - `sh.calvin.reorderable` pour le glisser-déposer (🔄 D14) ;
  - graphiques dessinés au `Canvas` Compose (🔄 D13).
- **SDK** : `minSdk 26` (Android 8.0), `compileSdk` / `targetSdk` = dernière API stable au
  démarrage du projet (valeur par défaut, D8 non tranchée — § 12).
- **Permissions** (🔄 D19 — uniquement celles qui servent) :
  | permission | usage |
  |---|---|
  | `POST_NOTIFICATIONS` | minuteur de repos et rappels (demandée à l'exécution, Android 13+) |
  | `USE_EXACT_ALARM` | alarmes exactes, Android 13+ (accordée d'office ; acceptable hors Play Store) |
  | `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) | alarmes exactes, Android 12–12L |
  | `RECEIVE_BOOT_COMPLETED` | reprogrammer les rappels après redémarrage |
  | `VIBRATE` | vibration de fin de repos depuis le récepteur d'alarme |
  Aucune permission de stockage (fichiers via le sélecteur système et `FileProvider`), de
  localisation ni de caméra.
- Quatre domaines fonctionnels :
  1. **Séances** (démarrer, saisir et valider les séries, terminer, éditer l'historique) ;
  2. **Progrès** (graphiques poids de corps, calories, eau, par groupe musculaire) ;
  3. **Nutrition** (calories et eau du jour, compléments + rappels) ;
  4. **Paramètres** (langue, thème, minuteur, sauvegarde/restauration JSON, export CSV,
     rapport de plantage).

---

## 2. Écrans et navigation

### 2.1 Arborescence

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
4. **Encart de statut** sous les actions : succès (fond `primary` à 8 %, texte `primary`,
   icône `check_circle`) ou erreur (fond `destructive` à 8 %, texte `destructive`, icône
   `error`), avec le message (`settings.export.*` / `settings.import.*`, erreur technique
   dans `{{error}}`).
5. **Encart info** permanent : `settings.info`.
6. **Rapport de plantage** (🔄 D17, affiché seulement si un rapport existe, § 10.9) :
   « Dernier plantage le {date} » + [Partager] (feuille de partage, `text/plain`)
   [Supprimer] (efface tous les rapports).

### 2.13 Graphique linéaire

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
- Le « jour courant » est un `StateFlow` recalculé à minuit (timer jusqu'au prochain minuit
  local) et à chaque retour au premier plan ; changement de fuseau
  (`ACTION_TIMEZONE_CHANGED`) → recalcul.

### 3.13 Minuteur de repos (règles)

Durée = préférence `restSeconds` (défaut **180 s** ; préréglages 60, 90, 120, 150, 180, 240,
300 s). Comportement et notifications au § 7.2.

### 3.14 Préférences

- `restSeconds` : entier > 0, sinon 180.
- `theme` (🔄 D10) : `"system"` (défaut) | `"light"` | `"dark"`.
- **Langue** (🔄 D9) : n'est plus une préférence de l'app ; elle est gérée par AppCompat
  (langue du système par défaut, surcharge possible FR/EN). Un changement de langue recrée
  l'activité, renomme les canaux de notification ; les textes des notifications sont
  calculés au moment où elles sont affichées, donc rien à reprogrammer.
- Locale de formatage : la locale effective de l'app (`fr-*` ou `en-*`).
- Préférences locales non sauvegardées : carte de transfert masquée (§ 2.2).

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
| `bodyWeight` | décimal | req. | kg | 0 = inconnu |
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

Les journaux peuvent contenir des jours à 0 (après ⟲).

**`Preferences`** : `{ restSeconds: entier, theme: "system" | "light" | "dark" }`.

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
| `rest_timer.json` | `RestTimerState` (absent hors repos) |
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
`applicationId` (bac à sable Android) ; ne sert que si l'option (a) du § 12 est retenue.

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
| workouts | tableau ; chaque élément objet avec `id` string, `date` string parseable en instant, `name` string/absent/null, `bodyWeight` nombre fini/absent/null, `preset` string/absent, `exercises` tableau d'objets `{ exerciseId: string, sets: tableau }`, chaque set objet avec `reps` et `weight` nombres finis et `duration`/`distance`/`speed`/`incline` nombres finis, absents ou null |
| exercises | tableau d'objets avec `id`, `name`, `muscleGroup` strings |
| body_weights | tableau d'objets `{ date: string, weight: nombre fini }` |
| supplements | tableau d'objets avec `id` et `name` strings |
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
    statistiques historiques — à confirmer, § 12) ; champs cardio `null` → absents ;
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
  validé (> 0 sinon 180) ; `theme = "system"`.

**Normalisation v2** (après migration) : `preset` inconnu → retiré ; `theme` invalide →
`"system"` ; si `calorieGoal` diffère de `goalFor(aujourd'hui)` (fichier v2 modifié par
l'app Expo), on ajoute `history[aujourd'hui] = calorieGoal` (idem eau) ; historique
d'objectif absent ou vide → `{ "0000-01-01": objectif courant }` ; entrées `taken` de
compléments inconnus retirées.

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

### 5.5 Transfert depuis l'app Expo (🔄 D4/D5 — provisoire, cf. § 12)

Avec un nouvel `applicationId`, l'app native ne peut pas lire le stockage de l'app Expo. Le
transfert retenu se fait **en un geste, sans modifier l'app Expo** :
1. dans l'app Expo : Paramètres → « Exporter mes données » ;
2. dans la feuille de partage : choisir **MuscleNote** (l'app native déclare un
   `intent-filter` `ACTION_SEND` pour `application/json`) ;
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

### 6.2 Groupes musculaires (ordre d'affichage, libellés, couleur)

| id | FR | EN | couleur |
|---|---|---|---|
| `dos` | Dos | Back | `#3b82f6` |
| `pecs` | Pecs | Chest | `#f97316` |
| `biceps` | Biceps | Biceps | `#a855f7` |
| `triceps` | Triceps | Triceps | `#8b5cf6` |
| `jambes` | Jambes | Legs | `#22c55e` |
| `épaules` | Épaules | Shoulders | `#f59e0b` |
| `abdos` | Abdos | Abs | `#ef4444` |
| `cardio` | Cardio | Cardio | `#ec4899` |
| `avant-bras` | Avant-bras | Forearms | `#14b8a6` |

Badge de groupe : fond couleur à 13 %, bordure couleur à 33 %, texte couleur, forme pilule ;
taille normale 12 sp (padding 10×4), petite 10 sp (padding 7×2), Inter 600. Mêmes couleurs
dans les deux thèmes (vérifier le contraste du texte en thème clair, § 9.1).

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
| Split | `legsSplit` | Jambes | Legs |
| Split | `arms` | Bras | Arms |
| Autre | `fullBody` | Full Body | Full Body |
| Autre | `isolation` | Isolation | Isolation |
| Autre | `cardio` | Cardio | Cardio |

⚠️ En anglais, `legs` et `legsSplit` ont le même libellé « Legs » mais restent deux
préréglages distincts (clés différentes). Option d'amélioration : libellé EN de `legsSplit`
« Legs (split) ».

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
| `rest-timer-ongoing` (🔄 D6) | Repos en cours | Rest in progress | LOW | non | non | public |
| `rest-timer` | Timer de repos | Rest timer | HIGH | défaut | `[0, 250, 250, 250]` ms | public |
| `supplements` | Rappels suppléments | Supplement reminders | HIGH | défaut | `[0, 250, 250, 250]` ms | privé |

- Petite icône : silhouette blanche sur transparent (vecteur dérivé de
  `assets/images/notification-icon.png`), couleur d'accent `#9B1C1C`. Pas de badge.
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
  1. notification **en cours** sur `rest-timer-ongoing`, visible sur l'écran de
     verrouillage : titre « Repos en cours », texte « Prochaine série : {exercice} »,
     compte à rebours **géré par le système** (`setUsesChronometer(true)`,
     `setChronometerCountDown(true)`, `setWhen(endsAt)`), `setOngoing(true)`,
     `setOnlyAlertOnce(true)`, `setSilent(true)`, `setTimeoutAfter(restSeconds × 1000)`,
     `VISIBILITY_PUBLIC`, catégorie `CATEGORY_STOPWATCH` ; action **« Passer »** ; toucher →
     écran `workout`. Pas de service de premier plan : aucune mise à jour périodique n'est
     nécessaire ;
  2. **alarme exacte** `setExactAndAllowWhileIdle(RTC_WAKEUP, endsAt)` vers le récepteur de
     fin de repos (une seule alarme active, remplacée à chaque démarrage).
- **Fin du repos** (récepteur d'alarme = source unique de l'événement, pas de double
  alerte) : supprime la notification en cours et `rest_timer.json`, puis :
  - app **au premier plan** (`ProcessLifecycleOwner` ≥ `STARTED`) : vibration « succès »
    seulement, pas de notification ;
  - app **en arrière-plan** ou processus tué : notification sur `rest-timer` : titre
    « Repos terminé 💪 », texte « C'est parti pour {exercice} ! », son + vibration,
    `VISIBILITY_PUBLIC`, auto-annulable ; toucher → `workout`.
- **Arrêt** (action « Passer » de la notification, bouton « Passer » du bandeau, fin ou
  abandon de séance) : annule l'alarme, la notification en cours et l'état persisté.
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
| `upload` / `download` | `upload` / `download` | export / import |
| `grid` | `table_view` | export CSV |
| `alert-circle` / `check-circle` | `error` / `check_circle` | statut |
| `wind`, `rotate-cw`, `move`, `arrow-down`, `activity` | `air`, `rotate_right`, `open_with`, `arrow_downward`, `accessibility_new` | échauffement |

### 9.4 Textes et traductions (🔄 D9)

- **Les sources de vérité des textes existants sont `artifacts/mobile/constants/i18n/fr.ts`
  (référence) et `artifacts/mobile/constants/i18n/en.ts`.** Ils contiennent toutes les
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
  (ressources par défaut). Les récepteurs (notifications) construisent leur texte avec la
  locale de l'app (`AppCompatDelegate.getApplicationLocales()`).
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
| `restTimer.ongoingTitle` | Repos en cours | Resting |
| `restTimer.ongoingText` | Prochaine série : {{exercise}} | Next set: {{exercise}} |
| `channel.restTimerOngoing` | Repos en cours | Rest in progress |
| `nutrition.reminderTimeTitle` | Heure du rappel | Reminder time |

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
traduction dans la spec.

| n° | Sujet | Réponse | Ce qui est spécifié |
|---|---|---|---|
| D1 | Export Excel ou CSV | « fait au plus simple » | CSV, 4 fichiers, mêmes tables (§ 8) |
| D2 | Persistance | « oui, fait au plus simple » | un fichier JSON par document, même forme que la sauvegarde ; le format § 5 reste le format d'échange (§ 4.2) |
| D3 | Langue des noms de séance | stocker l'identifiant de préréglage | `Workout.preset`, clé de séance, migration des anciens noms (§ 3.1, 5.3, 6.3) |
| D4 | Migration des données | automatique | ⚠️ incompatible avec D5 ; retenu provisoirement : transfert en un geste par partage (§ 5.5) — **à trancher, § 12** |
| D5 | `applicationId` | un nouvel applicationId | `com.musclenote.android` (proposition, § 1) |
| D6 | Minuteur de repos | visible sur l'écran (verrouillé) + demander les permissions | notification en cours avec compte à rebours système + alarme exacte de fin, demande de `POST_NOTIFICATIONS` (§ 7.1, 7.2) |
| D7 | Rappels de compléments | vrai sélecteur d'heure + alarme exacte | `TimePicker` M3, toute minute ; alarmes exactes reprogrammées chaque jour (§ 2.11, 7.3) |
| D8 | minSdk / targetSdk | — (pas de réponse) | défaut : minSdk 26, targetSdk = dernière API stable (§ 1, § 12) |
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
| D19 | Permissions | ne demander que les permissions utiles | liste fermée (§ 1) |
| D20 | Ergonomie de saisie | au plus utile | enregistrement à chaque frappe gardé, 0 affiché vide partout, IME « Suivant » (§ 2.6) |

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

1. **Migration automatique (D4) contre nouvel `applicationId` (D5).** Android isole les
   données de chaque app : avec un nouvel `applicationId`, l'app native **ne peut pas** lire
   le stockage de l'app Expo. Options :
   - **(a)** garder `com.musclenote.app` et signer avec la **même clé** que l'app Expo
     (gérée par EAS, récupérable via `eas credentials`) : l'app native s'installe par-dessus
     et lit `databases/RKStorage` au premier lancement (§ 4.3). Migration vraiment
     automatique, séance en cours comprise ; mais pas de cohabitation ni de retour arrière
     simple.
   - **(b)** *(retenue provisoirement, § 5.5)* nouvel `applicationId` + transfert en un geste
     par la feuille de partage, sans toucher à l'app Expo ; les deux apps cohabitent.
   - **(c)** nouvel `applicationId` + une dernière version de l'app Expo avec un bouton
     « Transférer vers la nouvelle app » qui envoie directement les données (séance en
     cours comprise) à l'app native. Plus confortable que (b), mais demande de retoucher
     et republier l'APK Expo.
   Recommandation : (b) si la cohabitation compte, (a) si « automatique » est prioritaire.
2. **minSdk / targetSdk (D8)** : défaut retenu minSdk 26 (Android 8.0) et targetSdk = dernière
   API stable. À confirmer selon les téléphones visés.
3. **Anciennes séries non cochées** (conséquence de D15) : à la migration v1, toutes les
   séries des séances terminées sont marquées validées pour que les statistiques passées ne
   changent pas. Alternative : garder les cases telles qu'enregistrées (plus exact si tu
   cochais toujours tes séries, mais les séries jamais cochées disparaîtraient des stats).
4. **Nom du nouvel `applicationId`** (si D5 est maintenue) : `com.musclenote.android`
   proposé ; pendant la cohabitation les deux apps s'appelleront « MuscleNote » sur le
   lanceur — garder ce nom ou distinguer temporairement (ex. « MuscleNote 2 ») ?
