# DESIGN.md — design system « Studio épuré »

Référence unique de l'interface de MuscleNote : jetons, typographie, composants, gabarits d'écran
et navigation. Source : la maquette Claude Design « MuscleNote — refonte », page **Tour 2 · Studio
épuré** (12/11/2026), exportée dans [`design/claude-design/`](design/claude-design/) (fichiers
`T2-*.dc.html`). Le comportement et les règles métier restent dans `SPEC.md` ; ce document dit à
quoi ressemble l'interface et avec quoi on la construit.

**Règle** : toute interface est faite des jetons et des composants décrits ici, jamais d'une couleur
ou d'une taille en dur. Une nouvelle fonctionnalité = un des quatre gabarits (§ 10) + des
composants existants (§ 9) ; un composant nouveau seulement s'il le faut, et il est alors ajouté ici.

Code : `ui/designsystem/` (§ 14).

## Sommaire

1. Principes
2. Couleurs
3. Typographie
4. Espacements
5. Rayons
6. Élévation
7. Mouvements
8. Icônes
9. Composants
10. Gabarits d'écran
11. Navigation
12. Écarts de la maquette, tranchés
13. Hors périmètre, questions ouvertes
14. Code

---

## 1. Principes

- **Noir, blanc et gris pour tout.** Trois couleurs seulement, et chacune veut dire quelque chose :
  **jaune = en direct**, **vert = progression**, **rouge = baisse**. Aucune autre couleur, ni
  décorative ni par groupe musculaire.
- **Jamais la couleur seule** : une variation porte toujours son signe (+ / −) ou un mot
  (« Record », « identique »).
- **La couleur suit l'objectif, pas le signe** : une valeur dont la baisse est souhaitée (tour de
  taille en sèche) s'affiche en vert quand elle baisse. Une mesure sans objectif déclaré reste
  neutre (gris).
- **Le jaune n'est jamais une action.** Le bouton principal est en inverse (blanc sur sombre, noir
  sur clair) ; le jaune marque ce qui se passe maintenant : chrono, repos, champ en saisie.
- **Un chiffre héros par écran**, **une surface mise en avant par écran au plus**. Le reste repose
  sur le fond, en sections séparées par des filets.
- **Plat** : ni ombre ni bordure de carte ; la hiérarchie vient des surfaces (§ 6).
- **Rien ne boucle** : pas de pulsation du chrono ni d'animation permanente.
- **Cibles tactiles de 48 dp minimum**, même quand le visuel est plus petit.

## 2. Couleurs

`Studio.colors`, deux palettes ; le choix Système / Clair / Sombre de Paramètres (SPEC § 2.12)
reste. Pas de couleurs dynamiques (Material You).

| jeton (Kotlin) | maquette | sombre | clair | rôle |
|---|---|---|---|---|
| `background` | fond | `#0F1012` | `#FFFFFF` | fond de tous les écrans, de la fenêtre et du splash |
| `surfaceRaised` | surface.avant | `#1C1D21` | `#F2F2F0` | la seule surface mise en avant d'un écran, bottom sheet, mini-barre, état pressé d'un bouton texte ou d'une ligne |
| `surfaceControl` | surface.controle | `#2A2C31` | `#E7E7E4` | touches du pavé, contrôle segmenté, puces, bouton secondaire, pastille d'icône, filets posés sur une surface |
| `surfacePressed` | surface.pressee | `#36383E` | `#D9D9D5` | état pressé, option sélectionnée du segmenté, touches d'action du pavé, pistes de progression, poignée de sheet |
| `separator` | separateur | `#222428` | `#EBEBE8` | filets de liste, 1 dp, posés sur le fond |
| `textPrimary` | texte.primaire | `#F3F3F1` | `#121315` | titres, chiffres, valeurs saisies, courbes |
| `textSecondary` | texte.secondaire | `#A2A5AB` | `#5C5F65` | légendes, colonne « Préc. », onglets inactifs, unités |
| `textTertiary` | texte.tertiaire | `#6F737A` | `#85888E` | valeurs pré-remplies, désactivé, semaine non validée — **chiffres de 24 sp et plus seulement** |
| `inverse` | inverse | `#F3F3F1` | `#121315` | bouton principal, case de série validée, puce de filtre sélectionnée |
| `onInverse` | — | `#0F1012` | `#FFFFFF` | texte et icône sur `inverse` |
| `live` | direct | `#FFC93D` | `#FFC93D` | aplat : point « en cours », barre de repos |
| `liveText` | direct.texte | `#FFC93D` | `#7A5900` | chrono, repos, curseur de saisie, étiquette « En cours » |
| `liveDim` | direct.attenue | `#332C14` | `#FFF0C2` | fond du champ en saisie, fond de la puce « en cours » |
| `onLive` | — | `#17140A` | `#17140A` | texte posé sur un aplat `live` (aucun usage dans la maquette, réservé) |
| `up` | hausse | `#3DD68C` | `#0B7A43` | progression, record, suggestion de charge |
| `upDim` | hausse.attenue | `#12301F` | `#E3F5EA` | fond de puce hausse / record |
| `down` | baisse | `#FF6B5E` | `#C62D22` | régression, stock bas |
| `downDim` | baisse.attenue | `#3A1714` | `#FCE6E3` | fond de puce baisse |
| `scrim` | voile | noir 60 % | `#121315` 40 % | voile sous une bottom sheet |

**Quelle couleur pour quoi :**

- *Jaune (en direct)* : le chrono de la séance en cours, le repos (chiffre, étiquette, barre), le
  point « en cours » (surface de l'Accueil, mini-barre, puce), le champ en saisie (fond `liveDim`
  + curseur `liveText`). Rien d'autre, même pour attirer l'attention.
- *Vert (progression)* : variation favorable, « Record », nombre de records battus, puce de
  suggestion de charge (+2,5 kg).
- *Rouge (baisse)* : variation défavorable, stock bas (« 4 jours »).
- *Gris* : variation nulle (« identique ») ou sans objectif.
- *Courbes* : toujours `textPrimary` ; la partie après le point touché en `textTertiary`.

**Contrastes sur fond** (maquette) : texte 17,1:1 · secondaire 7,7:1 · direct 12,4:1 (en clair,
`liveText` 6,5:1) · hausse 10,2:1 / 5,4:1 · baisse 6,8:1 / 5,5:1. Tertiaire 4,0:1 / 3,6:1, d'où
sa limite aux chiffres de 24 sp et plus.

Les couleurs de groupes musculaires de l'app actuelle (SPEC D23) disparaissent : les groupes
s'affichent en texte (puces de filtre neutres).

## 3. Typographie

**Archivo**, police variable (graisse 100–900, largeur 62–125 %), **embarquée** dans
`res/font/archivo.ttf` (licence SIL OFL 1.1, texte dans `assets/licenses/archivo-OFL.txt`). Les
polices téléchargeables d'Android (fournisseur Google Fonts) ne servent que des instances par
graisse, sans l'axe de largeur : le condensé y est impossible. Deux familles en sont tirées :
Archivo (largeur 100) et **Archivo condensée (largeur 75)**.

- **Chiffres de 24 sp et plus : condensé 75.** En dessous : largeur normale.
- **Chiffres toujours tabulaires et alignés** (`tnum`, `lnum`), pour que rien ne bouge quand une
  valeur change.
- Tailles en `sp` (elles suivent la taille de police du système).

| jeton (Kotlin) | maquette | taille · graisse · largeur · interligne · approche | usage |
|---|---|---|---|
| `numberHero` | chiffre.heros | 96 · 700 · condensé · 0,85 · −1 % | chiffre héros, un par écran |
| `numberLarge` | chiffre.grand | 56 · 700 · condensé · 0,85 | repos, champ héros de formulaire |
| `numberMedium` | chiffre.moyen | 32 · 700 · condensé · 1,0 | stats, valeurs de série, chrono d'en-tête |
| `numberKey` | (pavé) | 26 · 600 · condensé | touches chiffrées du pavé |
| `titleScreen` | titre.ecran | 32 · 700 · −1 % | titre de hub et de résumé, titre de la surface « Prochaine séance » |
| `titleFiche` | (fiche) | 28 · 700 · 1,1 · −1 % | titre d'une fiche |
| `titleBlock` | (séance) | 22 · 700 | titre d'un bloc exercice |
| `titleSection` | titre.section | 20 · 700 | en-tête de section, titre de bottom sheet |
| `unit` | (héros) | 20 · 600 | unité après un chiffre héros ou grand (« kg », « g ajoutés ») |
| `titleItem` | — | 18 · 700 | titre d'en-tête en direct, titre d'état vide |
| `action` | — | 16 · 700 | bouton 56 dp et bouton texte, « Repos terminé » |
| `bodyLine` | texte.ligne | 16 · 500 | libellé de ligne de liste |
| `bodyLineStrong` | — | 16 · 600 | valeur à droite d'une ligne |
| `actionSmall` | — | 15 · 700 | bouton 48 dp, mini-barre |
| `emphasis` | — | 15 · 600 | variation sous un héros, date de l'Accueil, sélecteur de période (sélection 800) |
| `body` | — | 14 · 400 | puces (700 si colorée), segmenté (600 / 700), lien « Tout voir » (600), phrase d'aide d'un formulaire |
| `paragraph` | — | 14 · 400 · 1,45 | texte d'une sheet de confirmation, d'un état vide |
| `caption` | texte.legende | 13 · 400 | légendes, sous-titres de ligne, variation de ligne (600), mini-puce |
| `label` | etiquette | 12 · 700 · capitales · +12 % | « En cours », « Repos » |
| `small` | — | 12 · 400 | libellés d'onglets (500 / 700), axes de courbe (400 / 700), légende des semaines |
| `columnHeader` | — | 11 · 700 · capitales · +12 % | en-têtes de colonnes des séries (#, Préc., Kg, Reps) |

Les graisses qui varient selon l'état (onglet, segmenté, puce, valeur pré-remplie ou validée)
sont appliquées par le composant, jamais par l'écran.

**Interligne inférieur à la police** (0,85 des grands chiffres) : appliqué comme en CSS, boîte de
la hauteur de l'interligne et glyphes centrés qui débordent (`RollingText`) ; Compose seul ne
descend pas sous la hauteur de la police.

## 4. Espacements

Base 4. `Studio.spacing` :

| jeton | maquette | dp | usage |
|---|---|---|---|
| `xxs` | esp.1 | 4 | titre de section → première ligne, période sous la courbe |
| `xs` | esp.2 | 8 | dans un groupe (puces, boutons d'une rangée) |
| `s` | esp.3 | 12 | titre de section → contenu, barre de titre → surface |
| `m` | esp.4 | 16 | padding vertical de ligne, colonnes d'une rangée de stats |
| `screen` | esp.5 | 20 | **marge d'écran**, padding de la surface mise en avant et des sheets |
| `l` | esp.6 | 24 | bloc exercice suivant (séance), rangée de stats sous la période |
| `hero` | esp.8 | 32 | autour du chiffre héros |
| `section` | esp.10 | 40 | **entre sections** |
| `touch` | esp.12 | 48 | cible tactile minimale |
| `row` | esp.16 | 64 | hauteur de ligne de liste |

Les dimensions propres à un composant (hauteur d'une touche, colonne d'une ligne de série,
écart entre libellé et chiffre héros…) sont celles de la maquette, définies une fois dans le
composant et décrites au § 9.

**Bords de l'écran** : en haut, l'inset de la barre d'état (la maquette prévoit 40) ; en bas,
l'inset de la barre de navigation, absorbé par la barre d'onglets ou la barre d'action.

## 5. Rayons

| jeton | dp | usage |
|---|---|---|
| `control` | 12 | boutons, puces, touches, segmenté, champ en saisie, case de série, mini-barre, ligne pressée |
| `surface` | 20 | surface mise en avant ; bottom sheet (coins du haut seulement) |
| `full` | cercle | points, pastilles d'icône, boutons icône |

Rayons internes, fixés par leur composant : 9 (option du segmenté, 12 − 3 de retrait), 8
(mini-puce), 3 (barres des semaines), 2 (pistes et segments de progression, poignée de sheet).

## 6. Élévation

Aucune ombre. Quatre niveaux de surface, du plus bas au plus haut : `background` →
`surfaceRaised` → `surfaceControl` → `surfacePressed`. Une bottom sheet est `surfaceRaised` sur
un `scrim` ; posés dessus, filets et contrôles passent d'un niveau (`surfaceControl`).

## 7. Mouvements

`Studio.motion` :

| jeton | ressort Compose | usage |
|---|---|---|
| `fast` | amortissement 1 (sans rebond), raideur 1500 (`StiffnessMedium`) | pressions, changements d'état |
| `standard` | amortissement 1, raideur 400 (`StiffnessMediumLow`) | bottom sheet, transitions d'écran |
| `expressive` | amortissement 0,75 (`DampingRatioLowBouncy`), raideur 1500 | série validée (case), record |
| `numberChange` | 250 ms | un chiffre qui change défile verticalement (ancien vers le haut, nouveau par le bas) |

En direct : **aucune animation en boucle** (le chrono change de valeur, il ne pulse pas).

## 8. Icônes

**Material Symbols Rounded**, 24 dp, graisse 400, contour ; **plein** (FILL 1) seulement pour
l'onglet sélectionné. Vector drawables `res/drawable/ds_ic_<nom>.xml` (`ds_ic_<nom>_filled.xml`
pour la version pleine), à côté des icônes Outlined actuelles le temps de la migration.

Tailles : 24 (défaut), 22 (pastille de ligne, coche du pavé, chevron d'une ligne d'information),
20 (chevron d'un lien « Tout voir »), 18 (dans une puce), 28 (état vide).

Jeu de la maquette : `home`, `calendar_month`, `monitoring`, `restaurant` (+ pleines),
`settings`, `arrow_back`, `close`, `more_vert`, `keyboard_arrow_down`, `chevron_right`, `add`,
`check`, `backspace`, `keyboard_hide`, `history`, `arrow_upward`, `arrow_downward`,
`emoji_events`, `edit_note`, `straighten`, `photo_camera`.

## 9. Composants

Chaque composant n'utilise que des jetons ; il reçoit ses textes déjà formatés (`formatNumber`,
`appLocale()`) et ses libellés d'accessibilité depuis les ressources. Dimensions en dp.

### 9.1 Données et listes (planche « Composants 1 »)

**Chiffre héros** — `HeroNumber`. Un seul par écran.
- Anatomie : légende (`caption`, `textSecondary`) · 6 · valeur (`numberHero`) + unité (`unit`,
  `textSecondary`, 6 après, sur la ligne de base) · 6 · variation (`emphasis`, colorée) suivie de
  son contexte (`emphasis` en 400, `textSecondary`, ex. « depuis le 12 févr. »).
- États : **normal** ; **en direct** (la légende devient l'étiquette en direct : point `live` 8 +
  `label` `liveText`, ex. « EN COURS · PUSH » ; valeur en `liveText`, sans unité ni variation) ;
  **au toucher de la courbe** (légende = date du point, valeur et variation du point).
- Variation : hausse `up`, baisse `down`, neutre `textSecondary` ; toujours signée.

**En-tête de section** — `SectionHeader`. Hauteur 40, titre `titleSection`.
- À droite, au choix : lien (« Tout voir », `body` 600 `textSecondary` + `chevron_right` 20) ;
  compteur ou mention (`body` 600, `textSecondary`, ou `up` pour « Records battus · 2 ») ; rien.
- Variante avec sous-titre : titre + 2 + `caption` `textSecondary` (« Encore 1 séance pour
  valider »), hauteur libre.
- Suivi de 4 avant la première ligne.

**Rangée de stats** — `StatRow`. 2 à 4 valeurs sans fond, colonnes égales séparées de 16 :
valeur `numberMedium` + 2 + légende `caption` `textSecondary`.

**Ligne de liste** — `ListRow`. Hauteur 64 (72 avec mini-puces), éléments espacés de 14, filet
`separator` 1 en bas (aucun sous la dernière ligne d'une section).
- Début (optionnel) : pastille 40 ronde `surfaceControl` avec une icône 22 `textPrimary`.
- Centre : titre `bodyLine` + 3 + sous-titre `caption` `textSecondary` (optionnel) ; ou titre + 8
  + rangée de **mini-puces** (`caption` sur `surfaceControl`, rayon 8, padding 3 × 8, espacées de 4).
- Fin, au choix : valeur `bodyLineStrong` ; valeur + 3 + variation (`caption` 600, `up` / `down`,
  ou « Record » en `up`) ; variation seule (`emphasis`, colorée, ou « identique » en
  `textSecondary`) ; chevron 24 `textSecondary`.
- États : normal ; **pressé** (fond `surfaceRaised`, rayon 12, déborde de 12 de chaque côté).

**Puce** — `Chip`. Hauteur 32, rayon 12, padding 10 (12 sans icône), icône 18, `body`.

| variante | fond | texte | icône / signe | graisse |
|---|---|---|---|---|
| neutre | `surfaceControl` | `textPrimary` | icône optionnelle en `textSecondary` (ex. `history`) | 400 |
| hausse | `upDim` | `up` | `arrow_upward` | 700 |
| baisse | `downDim` | `down` | `arrow_downward` | 700 |
| en direct | `liveDim` | `liveText` | point `live` 8 | 700 |
| record | `upDim` | `up` | `emoji_events` | 700 |
| filtre sélectionné | `inverse` | `onInverse` | — | 700 |
| filtre non sélectionné | `surfaceControl` | `textPrimary` | — | 400 |

Espace icône–texte : 6 (4 pour les flèches). Les puces de filtre et de saisie rapide sont
cliquables (cible 48) ; les autres sont informatives.

**Courbe** — `TrendCurve`. Trait 1,5 `textPrimary`, jointures et bouts arrondis, **ni grille ni
remplissage**. Hauteur 172 (150 si l'écran est chargé).
- **Au repos** : point final plein r 4 ; seules la première et la dernière date sous l'axe
  (`small`, `textSecondary`).
- **Au toucher** (glisser) : trait jusqu'au point en `textPrimary`, après en `textTertiary` ;
  guide vertical 1 `textSecondary` ; point r 5 ; sa date sous l'axe (`small` 700, `textPrimary`) ;
  le chiffre héros de l'écran affiche la valeur du point.
- Variante **compacte** (dans une surface) : hauteur 48, sans dates, point final r 3,5.

**Sélecteur de période** — `PeriodSelector`. Texte seul, options réparties sur la largeur
(1M · 3M · 6M · 1A · Tout), chacune 44 de haut et 48 de large au moins. `emphasis` 600
`textSecondary` ; sélection : 800, `textPrimary`, soulignée (2 d'épaisseur, 6 sous le texte).

**Contrôle segmenté** — `SegmentedControl`. 2 à 4 options. Conteneur `surfaceControl`, rayon 12,
retrait 4, options espacées de 4. Option : hauteur 40, rayon 9, largeurs égales ; sélectionnée
`surfacePressed` + `body` 700 `textPrimary`, sinon transparente + `body` 600 `textSecondary`.
Variante **compacte** (en-tête du pavé) : hauteur 36, largeur au contenu, padding 14.

**Section** (assemblage) — en-tête de section + lignes, posés sur le fond. Pas un composant :
c'est la brique des gabarits hub et fiche.

### 9.2 Actions et saisie (planche « Composants 2 »)

**Boutons** — `StudioButton`. Rayon 12.

| variante | hauteur | normal | pressé | désactivé |
|---|---|---|---|---|
| principal | 56 | `inverse` / `onInverse`, `action` | opacité 0,8 | `surfaceControl` / `textTertiary` |
| secondaire | 56 | `surfaceControl` / `textPrimary`, `action` | `surfacePressed` | `surfaceControl` / `textTertiary` |
| texte | 48 | transparent / `textPrimary`, `action` | `surfaceRaised` | `textTertiary` |
| icône | 48, rond | transparent, icône 24 `textPrimary` | `surfaceControl` | `textTertiary` |

- Taille **moyenne** (48, `actionSmall`) pour les rangées de boutons (repos) et l'état vide ;
  **petite** (44) pour la mini-barre et les icônes en ligne (bloc exercice, pavé).
- Marge latérale : 4 pour un bouton plein (texte centré, souvent dans une colonne étroite), 12
  pour un bouton texte ou petit, 20 pour un bouton à la largeur de son texte (état vide).
- Icône de tête optionnelle (22, 8 avant le texte), ex. « + Nouvelle mesure ».
- Ton `textSecondary` possible pour un bouton texte ou icône discret (« Relancer », fermer le pavé).
- Un bouton icône aligné sur un bord déborde de 12 pour que son icône tombe sur la marge.

**Surface mise en avant** — `HighlightSurface`. Une seule par écran : `surfaceRaised`, rayon 20,
padding 20, éléments espacés de 14. Variantes de la maquette :
- **En direct** (`LiveSessionCard`) : étiquette en direct « EN COURS » à gauche, nom de la séance
  (`emphasis` 700) à droite ; chrono `numberHero` `liveText` ; 8 ; « Repos **1:48** ·
  développé couché, série 3 » (`body`, `textSecondary`, temps en `liveText` 700) ; barre de
  progression des séries + légende (« 2 / 16 séries » · poids de corps) ; bouton principal
  « Reprendre ».
- **Prochaine séance** (`NextSessionCard`) : légende « Prochaine séance » ; type suggéré
  (`titleScreen`) ; légende (« Dernier Push il y a 7 jours · 5 exercices ») ; puces de filtre
  (types, « Autre… ») ; bouton principal « Démarrer ».
- **Mesure** (hub Progrès) : légende + bouton icône `add` ; chiffre héros + unité ; variation ;
  courbe compacte.

**Ligne de série** — `SetRow`. Hauteur 56, filet en bas. Colonnes : n° 22 · « Préc. » 60 · kg
(reste) · reps (reste) · case 48, espacées de 8. En-tête : `columnHeader` `textSecondary`
(# · PRÉC. · KG · REPS).
- n° : `emphasis` en 400 `textSecondary` (700 `textPrimary` sur la ligne en saisie) ;
  « Préc. » : `caption` `textSecondary` (« 80 × 8 ») ; valeurs : `numberMedium`.

| état | valeurs | case |
|---|---|---|
| à faire, pré-remplie | 600, `textTertiary` | contour 2 `textTertiary`, rayon 12 |
| en saisie | champ actif : fond `liveDim`, rayon 12, hauteur 44, déborde de 8 à gauche, valeur 700 `textPrimary` + curseur 2 × 26 `liveText` ; l'autre valeur reste pré-remplie | contour 2 `textTertiary` |
| validée | 700, `textPrimary` | `inverse` + `check` 24 `onInverse` |
| validée avec record | idem + « Record » (`small` 700, `up`) après les reps | idem |

La validation anime la case avec `expressive`.

**Ligne « + Série »** — `AddSetRow`. Sous la dernière série de chaque bloc exercice, **toujours
visible** : ajouter une série est fréquent en séance, ce n'est pas une option de menu. Hauteur 48,
alignée sur la colonne des numéros : icône `add` 22 `textSecondary`, 6, libellé `actionSmall`
`textPrimary` (« Série »). Pressée : fond `surfaceRaised` qui déborde de 12, rayon 12.

**Bandeau de repos** — `RestBanner`. Entre deux séries, **sans fond** : padding 12 en haut, 14 en
bas, filet en bas, éléments espacés de 10.
- **En cours** : étiquette « REPOS » (`label` `liveText`) + 4 + temps restant `numberLarge`
  `liveText` ; à droite, « sur 2:30 » (`body` `textSecondary`) ; piste 3 `surfacePressed`
  remplie en `live` selon le temps écoulé ; rangée « −30 s » · « +30 s » (secondaires moyens) ·
  « Passer » (texte moyen), largeurs égales.
- **Terminé** : « Repos terminé » (`action`) + 2 + « 2:30 · à toi, série 3 » (`caption`
  `textSecondary`) ; à droite « Relancer » (texte moyen, `textSecondary`).

**État vide** — `EmptyState`. Centré, éléments espacés de 10 : pastille 64 `surfaceControl` +
icône 28 `textSecondary` ; titre `titleItem` ; une phrase `body` `textSecondary` (300 de large au
plus) ; 4 ; une action (bouton secondaire moyen, padding 20). Une phrase, une action.

**Bottom sheet** — `StudioBottomSheet`. `surfaceRaised`, coins du haut 20, sur `scrim` ; padding
10 / 20 / 20 ; poignée 36 × 4 `surfacePressed` centrée ; titre `titleSection` + 6 + texte `body`
`textSecondary` ; puis les actions (principal 56, texte 48), espacées de 14. Sert aux
confirmations (« Terminer la séance ? ») et au gabarit formulaire.

**Pavé numérique** — `NumericKeypad`. Dans une bottom sheet : `surfaceRaised`, padding 12,
éléments espacés de 10.
- En-tête (optionnel) : segmenté compact (« Kg · série 3 » / « Reps ») et, à droite, bouton icône
  44 `keyboard_hide` `textSecondary`.
- Grille de 4 colonnes espacées de 6, touches de 52, rayon 12 : chiffres 1–9, « , », 0 sur
  `surfaceControl` (`numberKey`) ; colonne de droite : pas − et + (`surfacePressed`, `action` en
  17 → 16, ex. « −2,5 » / « +2,5 », « −50 » / « +50 ») et effacer (`surfacePressed`, `backspace`) ;
  touche principale sur deux colonnes (`inverse` / `onInverse`, `action`, icône `check` 22
  optionnelle) qui reprend l'action de l'écran (« Valider la série », « Ajouter »).
- Pas configurables : ±2,5 kg, ±1 rep, ±50 g…

**Mini-barre et onglets** — `MiniSessionBar` + `StudioNavigationBar`.
- Mini-barre (séance réduite) : hauteur 56, `surfaceRaised`, rayon 12, padding 16 / 8, éléments
  espacés de 12 : point `live` 8 ; « **Push** · repos **1:48** » (`actionSmall`, « · repos » en
  500 `textSecondary`, temps en `liveText`) ; bouton texte petit « Reprendre ». Hors repos, le
  temps est le chrono de la séance. Posée 8 au-dessus de la barre d'onglets, dans les marges
  d'écran.
- Barre d'onglets : filet `separator` en haut, 4 colonnes égales, padding 10 en haut, inset de
  navigation en bas ; icône 24 + 4 + libellé `small`. Sélectionné : icône pleine, `textPrimary`,
  700 ; sinon icône contour, `textSecondary`, 500. **Pas d'indicateur** (pastille Material).

### 9.3 Pièces des gabarits (tirées des écrans et des gabarits)

Ces pièces ne sont pas sur les planches de composants mais reviennent dans les écrans ; elles
font partie du design system au même titre.

- **Barres de titre** — `ScreenTitleBar` (hub : 56, `titleScreen`, bouton icône optionnel à
  droite) ; `DateTitleBar` (Accueil : 48, date `emphasis` `textSecondary`, bouton icône
  `settings` à droite) ; `FicheTopBar` (48 : bouton icône à gauche — `arrow_back` ou `close` — et
  options à droite — `more_vert`, `edit_note`) suivie du bloc titre (`titleFiche` ou
  `titleScreen` + 4 + `caption` `textSecondary`) ; `LiveHeader` (48 : `keyboard_arrow_down`
  « Réduire » ; nom `titleItem` + 2 + `caption` « 78,4 kg · 2 / 16 séries » ; chrono
  `numberMedium` `liveText`).
- **Progression des séries** — `SetProgress` : un segment par série prévue, hauteur 3, rayon 2,
  espacés de 3 ; faites `textPrimary`, à faire `surfacePressed`.
- **Barres des semaines** — `WeekBars` : 8 barres espacées de 6, hauteur 28 pour le maximum,
  rayon 3 ; semaine validée `textPrimary`, sinon `textTertiary` ; légende `small` `textSecondary`
  8 dessous (« 8 dernières semaines · 3 séances = semaine validée »).
- **Bloc exercice** — `ExerciseBlockHeader` : titre `titleBlock` + boutons icône petits
  (`monitoring` → fiche, `more_vert`) ; 8 ; puces (précédent : neutre `history` « 3 × 8 ·
  80 kg » ; suggestion : hausse « +2,5 kg ») ; 16 ; en-tête des séries.
- **Barre d'action** — `BottomActionBar` : sur le fond, padding 12 / 20, inset de navigation en
  bas ; un bouton principal pleine largeur (icône de tête possible).
- **Champ héros de formulaire** — `FormHeroField` : hauteur 76, padding 16, rayon 12, fond
  `liveDim` en saisie ; valeur `numberLarge` + curseur 2 × 40 `liveText` + unité (`unit`,
  `textSecondary`), espacés de 10.
- **Ligne d'information** — `InfoRow` : hauteur 52, libellé `bodyLine`, valeur `bodyLineStrong`
  (+ chevron 22 `textSecondary` si elle s'ouvre) ; filet `surfaceControl` (posée sur une sheet).
- **Point et étiquette en direct** — `LiveDot` (8, `live`) et `LiveLabel` (point + 8 + `label`
  `liveText`).
- **Variation** — `Delta` : texte signé coloré selon le sens (hausse / baisse / neutre), avec son
  contexte optionnel en `textSecondary`.

## 10. Gabarits d'écran

Tout écran, présent ou futur, est l'un de ces quatre (planche « 4 gabarits d'écran »). Marges
latérales `screen` (20) ; haut = inset de la barre d'état.

### 10.1 Hub

Une liste de sections : Accueil, Journal, Progrès, Nutrition, Paramètres, Mes records.

1. Barre de titre (`ScreenTitleBar`, ou `DateTitleBar` pour l'Accueil ; une page de second
   niveau comme Mes records a `FicheTopBar` sans options puis `titleScreen`).
2. `s` (12) puis **une surface mise en avant au plus** (optionnelle ; peut porter le chiffre héros).
3. Sections (`SectionHeader` + `ListRow`, ou rangée de stats, puces de filtre…), séparées de
   `section` (40). Ordre : par fréquence d'usage, de haut en bas.
4. En bas : mini-barre si une séance est réduite, puis la barre d'onglets (le hub de premier
   niveau sélectionné ; une page de second niveau garde l'onglet de son hub).

Chaque ligne ouvre un autre gabarit. Tout défile sous la barre d'onglets, fixe.

### 10.2 Fiche

Un sujet, un chiffre héros : fiche exercice, résumé de séance, poids, mensurations. Pas d'onglets.

1. `FicheTopBar` (retour / options, ou fermer / note).
2. 4, bloc titre.
3. 20, contrôle segmenté (optionnel : la métrique affichée).
4. `hero` (32), chiffre héros + variation.
5. 20, courbe ; 4, sélecteur de période. **Au toucher, la courbe pilote le héros.**
6. 24, rangée de stats (optionnelle).
7. `section` (40), sections : lignes avec mini-puces ou variation.
8. Action principale (optionnelle) dans une `BottomActionBar` fixe.

### 10.3 En direct

Ce qui se passe maintenant : la séance (demain, un cardio guidé). **Pas d'onglets**, le jaune pour
le temps qui passe, les actions sous le pouce.

1. `LiveHeader` (réduire · titre · chrono) ; 8 ; `SetProgress`.
2. Blocs exercice (24 entre eux) : `ExerciseBlockHeader`, en-tête de colonnes, `SetRow`, le
   `RestBanner` juste après la dernière série validée, et la ligne « + Série » (`AddSetRow`) sous
   la dernière série.
3. En bas, fixe : la bottom sheet du pavé numérique quand une valeur est en saisie, sinon une barre
   d'actions (valider, terminer).

« Réduire » rend la main aux hubs et fait apparaître la mini-barre.

### 10.4 Formulaire

Saisir une valeur : stock, pesée, mesure, calories, rappel. **Toujours une bottom sheet**, un champ
héros, une action principale. L'écran d'origine reste dessous, sous le voile.

1. Poignée ; titre `titleSection` + phrase d'aide `body` `textSecondary` (la donnée clé peut y
   prendre sa couleur : « soit **4 jours** » en `down`).
2. `FormHeroField` en saisie.
3. Puces de saisie rapide (filtres : celle qui correspond à la valeur est sélectionnée).
4. Lignes d'information (`InfoRow`) : conséquence de la saisie, réglage lié.
5. Pavé numérique (sans en-tête) dont la touche principale porte l'action, ou une liste d'options.

Espacement interne 12, padding 10 / 20, inset de navigation en bas.

## 11. Navigation

- **Quatre onglets fixes**, chacun un hub : **Accueil** (`home`), **Journal**
  (`calendar_month`), **Progrès** (`monitoring`), **Nutrition** (`restaurant`). Aucun cinquième
  onglet, jamais : une nouveauté devient une ligne dans une section, ou une nouvelle section.
- **Paramètres** sort de la barre : icône `settings` en haut de l'Accueil ; c'est un hub.
- **Séance en cours** : plein écran (gabarit en direct), sans onglets. Réduite, elle reste en
  mini-barre au-dessus des onglets sur tous les hubs. Terminée : le résumé (fiche), puis elle
  vit dans le Journal.
- Une ligne ouvre toujours l'un des quatre gabarits ; une saisie ouvre toujours un formulaire
  (bottom sheet).

Sections des hubs (planche « Navigation qui grandit » ; *Nouveau* = demande des données que l'app
n'a pas encore, cf. § 13) :

| hub | sections → gabarit ouvert |
|---|---|
| Accueil | **Séance** : reprendre / démarrer → en direct · **Cette semaine** : série sur 8 semaines → Journal · **Récentes** : 3 dernières séances → fiche (résumé) |
| Journal | **Calendrier** : mois (*Nouveau*) → hub · **Séances** : toutes, par type → fiche ; résumés (*Nouveau* : le résumé de fin de séance) → fiche |
| Progrès | **Records** : Mes records (*Nouveau*) → hub · **Exercices** : top, historique, 1RM → fiche · **Corps** : poids → fiche ; mensurations (*Nouveau*) → fiche ; photos (*Nouveau*) → hub |
| Nutrition | **Aujourd'hui** : calories, eau → formulaire ; protéines (*Nouveau*) → formulaire · **Compléments** : rappels → formulaire ; stock (*Nouveau*) → formulaire · **Tendances** : courbes sur 30 jours (déplacées depuis Progrès) → fiche |
| Paramètres | **Préférences** : langue, thème, repos → formulaire · **Données** : sauvegarde auto, import / export, Health Connect · **À propos** : envoyer un avis |

Correspondance avec les écrans actuels (SPEC § 2), pour la migration :

| écran actuel | gabarit |
|---|---|
| Accueil (§ 2.2) | hub Accueil |
| Démarrer une séance (§ 2.3) | surface « Prochaine séance » de l'Accueil ; « Autre… » → formulaire |
| Séance en cours (§ 2.4 – 2.6) | en direct |
| Sélecteur d'exercices (§ 2.7) | formulaire (liste d'options avec recherche) |
| Historique d'un exercice (§ 2.8) | fiche exercice |
| Éditer une séance terminée (§ 2.9) | fiche (résumé) + formulaires |
| Progrès (§ 2.10) | hub Progrès ; poids → fiche ; pesée → formulaire |
| Nutrition (§ 2.11) | hub Nutrition ; saisies → formulaires ; Calories / Eau → Tendances (fiche) |
| Paramètres (§ 2.12) | hub Paramètres, ouvert depuis l'Accueil |

## 12. Écarts de la maquette, tranchés

La maquette n'est pas toujours d'accord avec elle-même ; voici ce qui fait foi.

| écart | maquette | retenu |
|---|---|---|
| chiffre héros dans une surface | 64 (planche Composants 2), 88 (Progrès, Mes records), 96 (Accueil) | 96 partout (`numberHero`) |
| libellé du bouton principal | 16 (Composants 2), 17 (écrans) | 16 (`action`) |
| chrono de l'en-tête en direct | 26 (gabarits), 30 (séance) | 32 (`numberMedium`) |
| touches de pas du pavé | 17 · 700 | 16 · 700 (`action`) |
| écarts verticaux des fiches et du résumé | 28, 36 | 32 (`hero`), 40 (`section`) |
| padding haut des surfaces du hub Progrès | 18 | 20 (`screen`) |
| puces de saisie rapide (Stock) | 14 · 600 | puces de filtre (400 / 700) |
| en-tête de colonne « Préc. » | 11 px | `columnHeader` 11 sp |
| bloc titre du résumé sous sa barre | 8 | 4, comme toutes les fiches |
| barre de titre du hub Progrès | tassée par le navigateur (contenu plus haut que la planche) | 56, comme tous les hubs |
| marge latérale des boutons | aucune (bouton HTML par défaut) | 4 / 12 / 20 (§ 9.2) |

Les mesures internes des composants (14 entre éléments d'une ligne, 6 sous une légende de héros,
3 entre titre et sous-titre…) sont gardées telles quelles.

## 13. Décisions hors maquette, hors périmètre, questions ouvertes

Décidé (13/11/2026) :

- **Suppression** (séance, série, exercice) : une bottom sheet de confirmation
  (`ConfirmSheetContent`) dont le texte dit ce qui est supprimé et dont le bouton principal reste
  `inverse` (noir / blanc) ; le rouge reste réservé à la baisse.
- **Ajouter une série** : la ligne « + Série » (`AddSetRow`) sous la dernière série du bloc, pas
  le menu ⋮ — c'est une action fréquente en séance.

Hors périmètre pour l'instant :

- **Données nouvelles** : calendrier du Journal, résumé de fin de séance avec records,
  Mes records, mensurations, photos, protéines, stock des compléments, objectif de poids
  (prise de masse / sèche). La maquette les dessine pour prouver que le système tient (« 3
  fonctionnalités, zéro nouveau composant ») ; les ajouter est une décision produit à part.
- **Logo** : trois variantes d'haltère (« Deux disques », « Pyramide », « Compacte »), jaune sur
  graphite, non tranchées.

Ouvert :

- **Notes et retrait d'un exercice** en séance : non dessinés ; proposition, le menu ⋮ du bloc
  exercice — à confirmer à la migration de la séance en cours.
- **Échauffement, recherche d'exercice, formulaires longs** (rappels de compléments) : à composer
  avec les gabarits et composants existants au moment de leur migration.

## 14. Code

- `ui/designsystem/theme/` : `StudioTheme` (palettes, `CompositionLocal`s, correspondance avec
  `MaterialTheme` pour que les composants Material encore utilisés prennent la palette),
  `StudioColors`, `StudioTypography` (familles Archivo et Archivo condensée), `StudioSpacing`,
  `StudioShapes`, `StudioMotion`. Accès : `Studio.colors.live`, `Studio.typography.numberHero`,
  `Studio.spacing.screen`, `Studio.shapes.control`, `Studio.motion.fast`.
- `ui/designsystem/components/` : les composants du § 9 (`Foundation.kt` : icône, variation,
  point et étiquette en direct, chiffre qui défile ; puis un fichier par composant ou famille).
- `ui/designsystem/templates/Templates.kt` : les quatre gabarits du § 10 (`HubTemplate`,
  `FicheTemplate`, `LiveTemplate`, `FormSheetTemplate`), en composables à emplacements ; ils
  reçoivent les insets du système (`ScreenInsets`).
- Icônes `res/drawable/ds_ic_*.xml` ; police `res/font/archivo.ttf` (+ licence
  `assets/licenses/archivo-OFL.txt`) ; libellés d'accessibilité `ds_*` dans `strings.xml`.
- Aperçus : chaque composant et gabarit a ses `@Preview` en sombre et en clair, dans le source set
  `debug` (`app/src/debug/java/com/musclenote/android/ui/designsystem/`), comme les aperçus des
  écrans actuels : un aperçu par composant (`DataComponentPreviews`, `ActionComponentPreviews`,
  `TemplatePiecePreviews`), les deux planches de composants recomposées à la taille de la
  maquette (`BoardPreviews`, 1400 × 1320) et les gabarits remplis comme les écrans de la maquette
  (`TemplatePreviews`, 412 × 915, insets de la maquette).
- Textes des composants (libellés d'accessibilité, « Tout voir »…) dans `strings.xml` FR / EN.
- L'ancien thème (`ui/theme/`) reste tant que les écrans n'ont pas migré ; un écran migré
  n'utilise plus que `Studio`.
