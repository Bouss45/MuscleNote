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

---

## 1. Vue d'ensemble

- **MuscleNote** : carnet de musculation personnel, en français et en anglais, qui suit **la
  langue du système** (🔄 D9). Installé par APK (Releases GitHub, suivies par Obtainium),
  **pas de publication Play Store**.
- **Hors ligne** : aucun compte, aucune donnée d'entraînement envoyée. **Deux accès réseau
  seulement** :
  - la vérification des mises à jour (🔄 D25, § 3.17), au plus une fois par jour et seulement
    si l'app n'est pas installée par Obtainium ; aucune donnée de l'app envoyée ;
  - l'envoi d'un avis (🔄 D26, § 14), **uniquement quand l'utilisateur appuie sur
    « Envoyer »** : le type, le message, le pseudo facultatif et les quatre informations
    techniques affichées sous le formulaire. C'est le seul envoi de données de l'app.

  Exception indirecte : la police Inter est obtenue via les polices téléchargeables de Google
  Play Services (🔄 D12).
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
  - `androidx.compose.ui:ui-text-google-fonts` pour Inter (🔄 D12) ;
  - `sh.calvin.reorderable` pour le glisser-déposer (🔄 D14) ;
  - graphiques dessinés au `Canvas` Compose (🔄 D13).
- **SDK** (D8) : `minSdk 26` (Android 8.0), `compileSdk` / `targetSdk` = dernière API stable,
  soit **37** (Android 17) au démarrage du projet.
- **Permissions** (🔄 D19 — uniquement celles qui servent) :
  | permission | usage |
  |---|---|
  | `POST_NOTIFICATIONS` | minuteur de repos et rappels (demandée à l'exécution, Android 13+) |
  | `USE_EXACT_ALARM` | alarmes exactes, Android 13+ (accordée d'office ; acceptable hors Play Store) |
  | `SCHEDULE_EXACT_ALARM` (`maxSdkVersion="32"`) | alarmes exactes, Android 12–12L |
  | `RECEIVE_BOOT_COMPLETED` | reprogrammer les rappels après redémarrage |
  | `VIBRATE` | vibration de fin de repos depuis le récepteur d'alarme |
  | `INTERNET` (🔄 D25, D26) | vérification des mises à jour (§ 3.17) et envoi d'un avis à la demande de l'utilisateur (§ 14), rien d'autre |
  Aucune permission de stockage (fichiers via le sélecteur système et `FileProvider`), de
  localisation ni de caméra.
- Quatre domaines fonctionnels :
  1. **Séances** (démarrer, saisir et valider les séries, terminer, éditer l'historique) ;
  2. **Progrès** (graphiques poids de corps, calories, eau, par groupe musculaire) ;
  3. **Nutrition** (calories et eau du jour, compléments + rappels) ;
  4. **Paramètres** (langue, thème, minuteur, sauvegarde/restauration JSON, export CSV,
     mises à jour, rapport de plantage, envoi d'un avis).

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
6. Section **« APPLICATION »** :
   - **Mises à jour** (🔄 D25, icône `download`, § 3.17) : « Version {installée} · … » —
     installée par Obtainium : « mises à jour gérées par Obtainium », rien d'autre ; sinon
     « pas encore de vérification », « à jour (vérification du {date heure}) » ou « la {version}
     est disponible » (en `primary`), « Vérification impossible : pas de connexion ? » en rouge
     après un échec ; boutons [Vérifier] (indicateur pendant la requête) et, si une mise à
     jour est disponible, [Télécharger] ;
   - **Rapport de plantage** (🔄 D17, affiché seulement si un rapport existe, § 10.9) :
     « Dernier plantage le {date} » + [Partager] (feuille de partage, `text/plain`)
     [Supprimer] (efface tous les rapports).
7. Section **« TON AVIS »** (🔄 D26, seulement si l'URL du webhook a été fournie au build) :
   formulaire d'envoi d'un avis, décrit au § 14.1.

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
- Locale de formatage : la locale effective de l'app (`fr-*` ou `en-*`), c'est-à-dire celle
  des textes affichés : la première locale de la liste (langues du système, ou choix FR / EN
  de l'app) dont la langue a des textes, région comprise (`en-GB`, `fr-CA`) ; aucune →
  français, comme les textes (§ 9.4).
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
| `local_preferences.json` | `{ migrateCardDismissed, appLanguage? }` — préférences locales (§ 3.14), hors sauvegarde |
| `rest_timer.json` | `RestTimerState` (absent hors repos) |
| `update_check.json` | `{ checkedAt, latestVersion?, pageUrl?, apkUrl?, dismissedVersion? }` — vérification des mises à jour (§ 3.17), hors sauvegarde |
| `feedback.json` | `{ lastSentAt, blockedUntil }` — délais entre deux avis (§ 14.3), hors sauvegarde |
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
🔄 D23 — en thème clair, le texte et les icônes dans ces couleurs prennent une nuance foncée :
ambre du minuteur `#B45309`, top 3 `#166534`, `#1D4ED8`, `#7E22CE`, groupes au § 6.2 (fonds,
bordures et courbes inchangés).

Le splash et le fond de fenêtre suivent le thème **choisi dans l'app** : sur Android 12+, le
choix est transmis au système (`UiModeManager.setApplicationNightMode`, « Système » = pas de
surcharge), ce qui recrée l'activité quand le thème effectif change ; avant Android 12, le
splash suit le thème du système.

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
| `restTimer.ongoingTitle` | Repos en cours | Resting |
| `restTimer.ongoingText` | Prochaine série : {{exercise}} | Next set: {{exercise}} |
| `channel.restTimerOngoing` | Repos en cours | Rest in progress |
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
vérification finale (§ 13), D25 et D26 après la bascule.

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
| D19 | Permissions | ne demander que les permissions utiles | liste fermée (§ 1) ; `INTERNET` y est entrée avec D25 (mises à jour) et sert aussi à D26 (avis), seul envoi de données, toujours déclenché par l'utilisateur |
| D20 | Ergonomie de saisie | au plus utile | enregistrement à chaque frappe gardé, 0 affiché vide partout, IME « Suivant » (§ 2.6) |
| D21 | Anciennes séries non cochées | toutes marquées validées à la migration | migration v1 → v2 : `completed = true` pour toutes les séries des séances terminées ; les statistiques passées ne changent pas (§ 3.16, 5.3) |
| D22 | Nom affiché pendant la transition | « MuscleNote » suivi d'un « 2 » | nom de transition distinct de l'app Expo, cible du partage au § 5.5 ; retiré à la bascule : `app_name` = « MuscleNote » (FR et EN) (§ 1, 9.5) |
| D23 | Contraste des couleurs de groupe en thème clair | texte foncé en thème clair | texte et icônes en nuance foncée (contraste ≥ 4,5:1) en thème clair : groupes, ambre du minuteur, top 3 ; fonds, bordures, courbes et thème sombre inchangés (§ 6.2, 9.1) |
| D24 | Libellé EN de `legsSplit` (« Legs » en double) | « Legs (split) » | libellé anglais « Legs (split) » ; anciennes séances « Legs » toujours rattachées à `legs` (§ 5.3, 6.3) |
| D25 | Mises à jour d'une app installée sans Obtainium | vérification intégrée | requête quotidienne à l'API GitHub, permission `INTERNET`, annonce sur l'Accueil et carte dans Paramètres ; désactivée quand Obtainium installe l'app (§ 1, 2.2, 2.12, 3.17) |
| D26 | Retours des utilisateurs | envoyer un avis depuis l'app vers le serveur Discord du développeur | section « Ton avis » dans Paramètres ; webhook Discord dont l'URL vient d'un secret GitHub injecté au build (section masquée sans elle) ; envoi seulement sur appui, aucune donnée d'entraînement, aucune mention possible, un envoi par minute (§ 1, 2.12, 14) |

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
section. Les chemins ci-dessous sont relatifs à
`android-app/app/src/main/java/com/musclenote/android/`.

### 13.1 Fait

Tout ce que décrivent les § 1 à 10 est implémenté. Les règles métier pures sont couvertes par
245 tests JVM (23 classes, `app/src/test/`), lancés par la CI à chaque push avant la
construction de l'APK.

| § | sujet | où |
|---|---|---|
| 1 | identité (`com.musclenote.android`, « MuscleNote », portrait), SDK 26 / 37, socle technique ; permissions = la liste fermée de D19, vérifiée dans le manifeste fusionné de l'APK (`INTERNET` ajoutée ensuite par D25, § 13.6) | `app/build.gradle.kts`, `AndroidManifest.xml` |
| 2.1 | navigation, 4 onglets, entrées externes (notifications, fichier partagé) | `ui/MuscleNoteApp.kt`, `MainActivity.kt` |
| 2.2 | Accueil : fond flouté, séance en cours, carte de transfert, série hebdo, stats, temps en salle, séances avec édition / date / suppression | `ui/home/` |
| 2.3 – 2.6 | démarrer une séance, séance en cours (chronomètre, échauffement, glisser-déposer, fin ou abandon), carte d'exercice, ligne de série | `ui/workout/` |
| 2.7 – 2.9 | sélecteur d'exercices, historique d'un exercice, éditeur de séance (copie de travail, renommage, dialogue de sortie) | `ui/workout/`, `ui/history/` |
| 2.10 | Progrès : poids corps et pesées, calories, eau, groupes musculaires | `ui/progress/`, `data/progress/ProgressStats.kt` |
| 2.11 | Nutrition : suivi du jour, compléments, rappels au `TimePicker` | `ui/nutrition/` |
| 2.12 | Paramètres : mes données, langue, thème, minuteur, notifications, sauvegarde, export CSV, import, rapport de plantage | `ui/settings/` |
| 2.13 | graphique linéaire au `Canvas` | `ui/components/LineChart.kt` |
| 3 | règles métier (séance, 1RM, série hebdo, temps en salle, volume, archivage, Progrès, pesées, nutrition, préférences, séries validées) | `data/workout/`, `data/progress/`, `data/nutrition/`, `data/repository/` |
| 4 | modèle, un fichier JSON par document, écritures atomiques et différées, fichiers illisibles mis de côté | `data/model/`, `data/storage/` |
| 5 | sauvegarde v2, import v1 / v2 (validation, migration, normalisation), transfert depuis l'app Expo | `data/backup/`, `ui/settings/BackupViewModel.kt` |
| 6 | 42 exercices intégrés, 9 groupes, 11 préréglages | `data/catalog/`, `data/model/` |
| 7 | canaux, minuteur de repos (notification en cours, alarme exacte), rappels de compléments (alarmes exactes, resynchronisations) | `notifications/`, `data/repository/RestTimerRepository.kt` |
| 8 | export CSV | `data/export/CsvExport.kt`, `ui/settings/CsvExportTexts.kt` |
| 9 | palettes sombre et claire, Inter, icônes Material Symbols, textes FR / EN | `ui/theme/`, `res/` |
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
| Splash et fond de fenêtre au thème du système, pas au choix Clair / Sombre de l'app (flash de l'autre thème au lancement) | Android 12+ : `setApplicationNightMode` (§ 9.1) ; `ui/theme/SystemNightMode.kt` |
| Texte en couleur de groupe peu lisible en thème clair (1,9 – 3,6:1) | décision D23 : nuance foncée en thème clair (§ 6.2, 9.1) |
| Deux préréglages « Legs » en anglais | décision D24 : « Legs (split) » (§ 6.3) |

### 13.3 Manque

Rien de fonctionnel. Restent à valider **à l'œil, sur l'appareil**, ce que la spec présente
comme des propositions :
- la palette claire (§ 9.1) ;
- l'opacité de l'image de fond de l'Accueil, 0,35 en sombre et 0,12 en clair (§ 2.2).

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
- **Notification « Repos en cours »** : `setTimeoutAfter` reçoit le temps restant (égal à
  `restSeconds × 1000` au démarrage), pour rester juste quand la notification est republiée
  dans une autre langue en cours de repos.
- **Partage vers l'app** (§ 5.4) : `ACTION_SEND` est déclaré pour `application/json` seulement
  (type utilisé par l'app Expo), pour ne pas apparaître dans tous les partages de texte ;
  `ACTION_VIEW` accepte `application/json` et `text/plain`.
- **Valeurs ≥ 1000 du graphique des calories** : `2,5k` en français, `2.5k` en anglais
  (séparateur décimal de la locale, § 9.4).
- **`DUMP`** apparaît dans le manifeste fusionné : c'est la permission qui *protège* le
  récepteur d'`androidx.profileinstaller`, pas une permission demandée par l'app.

### 13.5 Bascule

L'app native a remplacé l'app Expo sur `main` : nom affiché « MuscleNote », app Expo retirée
du dépôt (conservée au tag `expo-final`), Releases GitHub `v1.0.<n>` publiées par
`.github/workflows/release.yml` à chaque push sur `main` touchant `android-app/`
(`versionCode` = 1000 + n, au-dessus des pré-releases `v0.1.*` de la transition, supprimées).

### 13.6 Après la bascule

- Vérification des mises à jour intégrée pour une app installée sans Obtainium (🔄 D25,
  § 3.17) ; c'est la seule requête réseau de l'app et la seule raison de la permission
  `INTERNET`.

---

## 14. Envoyer un avis

🔄 D26. Un formulaire de Paramètres envoie une idée, un bug ou une remarque sur le serveur
Discord du développeur, par un webhook. C'est le **seul envoi de données** de l'app, et il n'a
lieu que lorsque l'utilisateur appuie sur « Envoyer » : pas d'envoi automatique, pas de file
d'attente, pas de nouvel essai en arrière-plan.

### 14.1 Formulaire (Paramètres, section « TON AVIS »)

Dernière section de Paramètres (après « APPLICATION », § 2.12), **affichée seulement si l'URL
du webhook a été fournie au build** (§ 14.5) ; sinon elle est absente, titre compris. Une carte
au style des cartes de Paramètres :

- **En-tête** : icône ronde `forum` (`primary`), titre « Ton avis », description « Une idée, un
  bug ? Envoie-le directement au développeur. ».
- **Type** : puces « Idée » (choisie par défaut), « Bug », « Autre » ; choix unique, style des
  puces du minuteur.
- **Message** : champ multiligne (4 lignes visibles, grandit jusqu'à 8 puis défile),
  indication « Décris ton idée ou le problème… », majuscule en début de phrase. **1 000
  caractères au plus** : la saisie s'arrête à la limite et un collage trop long est tronqué.
  Caractère = point de code Unicode (un emoji simple compte pour 1 et n'est jamais coupé en
  deux). Compteur « {n} / 1 000 » aligné à droite sous le champ (`mutedForeground`, `destructive`
  à 1 000), nombres formatés dans la locale de l'app (§ 3.14).
- **Pseudo** (facultatif) : champ d'une ligne, indication « Pseudo (facultatif) », 32 caractères
  au plus, touche « Terminé ».
- **Bouton plein « Envoyer »** (`primary`, icône `send`), actif seulement si le message contient
  autre chose que des espaces, qu'aucun envoi n'est en cours et qu'aucun délai ne court
  (§ 14.3). Pendant l'envoi : indicateur dans le bouton, puces et champs désactivés. Pendant un
  délai : bouton désactivé et, dessous, « Prochain envoi possible dans {délai} », décompté à la
  seconde (« 42 s » ; à partir d'une minute, en minutes arrondies au-dessus : « 3 min »).
- **Informations jointes**, en petit (`mutedForeground`, 11 sp, icône `info`) sous le bouton :
  « Joint automatiquement : MuscleNote {version} · Android {version} · {modèle} · langue
  {langue}. Rien d'autre : aucune donnée d'entraînement. », avec exactement les valeurs envoyées
  (§ 14.2).
- **Encart de résultat** sous le formulaire, au style de l'encart de statut (§ 2.12, point 4) :
  succès ou erreur (§ 14.4) ; il disparaît dès que le formulaire est modifié.
- **Brouillon** gardé en mémoire tant que l'app tourne : changer d'onglet, de langue ou de thème
  ne le perd pas. Il n'est pas écrit sur disque.

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
  l'attente ne dépasse jamais 60 s (après un envoi) ni le délai demandé (après un 429).
- Un échec réseau ou une erreur autre que 429 n'impose aucun délai : on peut réessayer tout de
  suite.
- Ces limites évitent les doubles envois et l'emballement ; elles ne protègent pas le webhook
  lui-même (§ 14.5).

### 14.4 Résultats

| issue | encart | formulaire |
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
  différente de `https://discord.com/api/webhooks/<id>/<jeton>` (ou `discordapp.com`, espaces
  autour ignorés) → le build échoue, sans afficher l'URL.
- **Limite assumée** : l'URL est incluse dans l'APK, qui est public. Quiconque le décompile peut
  la lire et poster dans le salon sans passer par les limites de l'app (§ 14.3). Parades : un
  webhook dans un **salon privé** que seul le développeur voit ; en cas d'abus, supprimer le
  webhook sur Discord, en créer un autre, mettre à jour le secret et publier une Release (les
  versions précédentes obtiennent alors une erreur 401 / 404, donc le message d'échec).

### 14.6 Textes (FR et EN)

| clé | FR | EN |
|---|---|---|
| `settings.section.feedback` | TON AVIS | YOUR FEEDBACK |
| `feedback.title` | Ton avis | Your feedback |
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
`settings.restTimer.minutes` : « 42 s », « 3 min »).
