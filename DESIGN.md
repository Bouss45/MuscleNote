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

`Studio.colors`, deux palettes ; le choix Sombre (par défaut), Clair, Système de Paramètres (SPEC
§ 15.10.4). Pas de couleurs dynamiques (Material You).

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
| `live` | direct | `#FFC93D` | `#9E7100` | aplat : point « en cours », barre de repos (or foncé en clair, cf. contrastes) |
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

**Vérifiés sur toutes les paires utilisées** (SPEC § 15.11.4), sombre / clair : tout texte ≥ 4,5:1
sur son fond (au plus bas : secondaire sur `surfacePressed` 4,75 / 4,52, `up` sur `upDim` 7,6 /
4,8, `down` sur `downDim` 5,7 / 4,6) ; tertiaire ≥ 3:1 sur le fond et les surfaces (bord de case,
chiffres pré-remplis) ; seul le texte d'un bouton **désactivé** (tertiaire sur `surfaceControl`,
2,9:1) est en dessous, comme l'admet WCAG pour un contrôle inactif. En clair, l'aplat `live` de la
maquette (`#FFC93D`) ne faisait que 1,1 à 1,5:1 sur les fonds clairs : il passe à `#9E7100`
(≥ 3:1 partout, élément non textuel) ; le logo garde le jaune de la marque.

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
| `bodyStrong` | — | 14 · 600 | message de succès ou d'erreur, sans couleur (`StatusMessage`, `statusHelp`) |
| `paragraph` | — | 14 · 400 · 1,45 | texte d'une sheet de confirmation, d'un état vide |
| `caption` | texte.legende | 13 · 400 | légendes, sous-titres de ligne, mini-puce |
| `captionStrong` | — | 13 · 600 | variation d'une ligne, compteur arrivé à sa limite |
| `label` | etiquette | 12 · 700 · capitales · +12 % | « En cours », « Repos » |
| `small` | — | 12 · 400 | libellés d'onglets (500 / 700), axes de courbe (400 / 700), légende des semaines |
| `columnHeader` | — | 11 · 700 · capitales · +12 % | en-têtes de colonnes des séries (#, Préc., Kg, Reps) |

Les graisses qui varient selon l'état (onglet, segmenté, puce, valeur pré-remplie ou validée)
sont appliquées par le composant, jamais par l'écran.

**Interligne inférieur à la police** (0,85 des grands chiffres) : appliqué comme en CSS, boîte de
la hauteur de l'interligne et glyphes centrés qui débordent (`RollingText`) ; Compose seul ne
descend pas sous la hauteur de la police.

**Texte agrandi** (taille de police du système jusqu'à 200 %) : rien n'est coupé. Les hauteurs
des composants sont des **minimums** (ils grandissent avec leur texte) ; titres, sous-titres,
légendes, libellés de boutons et messages **passent à la ligne** ; un **chiffre ne passe jamais à la
ligne** : `RollingText` et les valeurs de série rétrécissent jusqu'à 60 % pour tenir dans leur
colonne. Les mots seuls d'une colonne étroite (libellés d'onglets, en-têtes de colonnes) rétrécissent
de même (`FitText`). Android agrandit moins les grands textes que les petits (mise à l'échelle non
linéaire, Android 14+) : les chiffres héros changent peu.

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
pour la version pleine). Les icônes Outlined de l'ancien thème sont retirées ; hors Compose
restent `ic_check`, `ic_replay_30`, `ic_forward_30`, `ic_skip_next` (actions de la notification
de séance), `ic_notification` et les couches du logo.

Tailles : 24 (défaut), 22 (pastille de ligne, coche du pavé, chevron d'une ligne d'information),
20 (chevron d'un lien « Tout voir »), 18 (dans une puce), 28 (état vide).

Jeu de la maquette : `home`, `calendar_month`, `monitoring`, `restaurant` (+ pleines),
`settings`, `arrow_back`, `close`, `more_vert`, `keyboard_arrow_down`, `chevron_right`, `add`,
`check`, `backspace`, `keyboard_hide`, `history`, `arrow_upward`, `arrow_downward`,
`emoji_events`, `edit_note`, `straighten`, `photo_camera` ; ajoutées avec la séance en cours :
`delete` (supprimer la série, en-tête du pavé), `fitness_center` (état vide de la séance) ; avec
Nutrition : `pill` (état vide des compléments) ; avec le rappel de pesée (SPEC § 15.13) :
`notifications` (bouton de la surface « Poids de corps » de Progrès).

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
- La valeur défile (`numberChange`) quand elle change, par ex. au changement de période ; au toucher
  de la courbe elle suit le doigt **sans défiler** (`animate = false`). Sans valeur (période vide) :
  « — », sans unité ni variation.
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
- Centre : titre `bodyLine` + 3 + sous-titre `caption` `textSecondary` (optionnel ; l'un et
  l'autre passent à la ligne plutôt que d'être coupés) ; ou titre + 8
  + rangée de **mini-puces** (`caption` sur `surfaceControl`, rayon 8, padding 3 × 8, espacées de 4).
- Fin, au choix : valeur `bodyLineStrong` ; valeur + 3 + variation (`caption` 600, `up` / `down`,
  ou « Record » en `up`) ; **mini-courbe + valeur et variation** (`ListRowTrailing.CurveValue`,
  mini-courbe 56 × 24 puis 12 : un exercice de Progrès) ; variation seule (`emphasis`, colorée, ou
  « identique » en `textSecondary`) ; chevron 24 `textSecondary` ; **case à cocher** (`ListRowTrailing.Check`, la
  case 48 de la ligne de série) — toute la ligne la bascule (rôle case à cocher) : échauffement de
  la séance en cours, lignes à cocher d'une sheet (rappels, sauvegarde quotidienne, Health Connect ;
  sans filet, le filet d'une sheet étant celui des `InfoRow`). Avec sa **propre action**
  (`onToggle` + libellé TalkBack), la case seule (cible 48) bascule et le reste de la ligne garde
  son toucher : complément de Nutrition (case = « pris aujourd'hui », ligne → ses rappels). **Sans
  action** (ni `onClick` ni `onToggle`), la case est lue cochée ou non **et désactivée** (exercice
  déjà dans la séance, sélecteur) ; avec un **appui long**, la ligne à cocher le garde (exercice
  perso du sélecteur : modifier, supprimer).
- États : normal ; **pressé** (fond `surfaceRaised`, rayon 12, déborde de 12 de chaque côté).
- **Appui long** optionnel (`onLongClick` + libellé d'accessibilité) : les actions secondaires
  d'une ligne, dans une bottom sheet (séance du Journal et « Dernière séance » de l'Accueil :
  modifier, changer la date, supprimer). Le toucher garde l'action principale ; `onClickLabel` la
  nomme pour TalkBack quand elle ne va pas de soi (pesée : « Supprimer la pesée »).

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

Choix unique par défaut (lue comme bouton radio) ; **choix multiple** (`multiSelect`, jours du rappel
de pesée, SPEC § 15.13.3) : même dessin, chaque puce se coche ou se décoche et se lit comme une case à
cocher, avec une `description` TalkBack en toutes lettres (« lundi » pour « lun. »).

Espace icône–texte : 6 (4 pour les flèches). Les puces de filtre et de saisie rapide sont
cliquables (cible 48 : Compose agrandit la zone de toucher d'une puce de 32 jusqu'à 48, ou jusqu'à
la puce voisine — 40 entre deux rangées espacées de 8) ; les autres sont informatives, sauf la
**puce de suggestion de charge** du
bloc exercice : elle applique la suggestion au toucher (`StudioChip(onClick)`, hausse « +2,5 kg »
ou neutre « Même charge »).

**Courbe** — `TrendCurve`. Trait 1,5 `textPrimary`, jointures et bouts arrondis, **ni grille ni
remplissage**. Hauteur 172 (150 si l'écran est chargé : `CurveHeightDense`, section d'un hub).
Dessinée au `Canvas` Compose, sans bibliothèque ; abscisse proportionnelle à la date.
- **Au repos** : point final plein r 4 ; seules la première et la dernière date sous l'axe
  (`small`, `textSecondary`). Un seul point : le point seul, à droite.
- **Au toucher** (glisser) : le point le plus proche du doigt (`CurveGeometry`, testé en JVM) ;
  trait jusqu'au point en `textPrimary`, après en `textTertiary` ; guide vertical 1 `textSecondary` ;
  point r 5 ; sa date sous l'axe (`small` 700, `textPrimary`) ; le chiffre héros de l'écran affiche
  la valeur du point. La courbe garde le geste (l'écran ne défile pas sous le doigt).
- **Vide** : une phrase centrée (`body`, `textSecondary`), ex. « Choisis une période plus longue. ».
- **Ligne d'objectif** (optionnelle) : pointillés 4 / 4, 1, `textTertiary`, comprise dans l'échelle,
  marquée à droite au-dessus (« Objectif », `small` `textSecondary`) : calories et eau de Progrès.
- **Accessibilité** : une description lue par TalkBack — période, valeur de départ, d'arrivée,
  variation (« Charge max par séance, 3 mois : de 92,5 kg le 14 août à 100 kg le 5 nov., +7,5 kg »).
- Au-delà de 120 points, l'écran passe une série réduite (SPEC § 15.9.3 : creux et pics gardés).
- Variante **compacte** (dans une surface) : hauteur 48, sans dates, point final r 3,5.
- Variante **mini** (`MiniTrendCurve`, fin d'une ligne de liste) : 56 × 24, point final r 2,5, ni
  dates ni toucher, décorative (la ligne dit la valeur et la variation) ; 24 points au plus.

**Sélecteur de période** — `PeriodSelector`. Texte seul, options réparties sur la largeur
(1M · 3M · 6M · 1A · Tout), chacune 44 de haut et 48 de large au moins. `emphasis` 600
`textSecondary` ; sélection : 800, `textPrimary`, soulignée (2 d'épaisseur, 6 sous le texte).
TalkBack lit une description par option (« 3 mois », « Tout l'historique »), pas le libellé court.

**Contrôle segmenté** — `SegmentedControl`. 2 à 4 options. Conteneur `surfaceControl`, rayon 12,
retrait 4, options espacées de 4. Option : hauteur 40, rayon 9, largeurs égales ; sélectionnée
`surfacePressed` + `body` 700 `textPrimary`, sinon transparente + `body` 600 `textSecondary`.
Chaque option se touche sur toute la hauteur du conteneur (**48**), l'option choisie dessinée en
retrait. Variante **compacte** (en-tête du pavé) : option de 36 (conteneur 48), largeur au
contenu, padding 14.

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
  **petite** (44) pour la mini-barre et les icônes en ligne (bloc exercice, pavé). Un bouton icône
  petit garde une cible de 48 : son cercle de 44 est posé dans une zone de toucher de 48.
- Les hauteurs sont des minimums : un libellé trop long pour sa largeur (texte à 200 %) passe à la
  ligne, centré.
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
  (`titleScreen`) ; légende (« Dernière fois il y a 7 jours · 5 exercices ») ; puces de filtre
  (types, « Autre… »), sur plusieurs lignes si les noms sont longs, aucune rangée sans type ;
  bouton principal « Démarrer ».
- **Aujourd'hui** (`DailySurface`, hub Nutrition) : légende « Aujourd'hui » ; chiffre héros (kcal du
  jour) + unité ; état par rapport à l'objectif en variation (« encore 950 kcal » gris, « dans
  l'objectif » `up`, « +320 kcal » `down`) suivi de son contexte ; piste de progression
  (`ProgressTrack`) ; rangée de stats (eau, compléments pris) ; deux boutons secondaires moyens de
  largeurs égales, icône `add` (« Calories », « Eau »).
- **Mesure** (`MetricSurface`, hub Progrès) : légende + bouton icône `add`, précédé au besoin d'un
  second bouton icône (`secondaryAction`, `SurfaceAction` : `notifications` « Rappel de pesée »,
  SPEC § 15.13.3) ; chiffre héros + unité ;
  variation ; courbe compacte ; légende optionnelle sous la courbe, à gauche et à droite (`caption`
  `textSecondary` : « 42 jours » · « 68 – 78,4 kg »). Sans valeur : une phrase (`paragraph`
  `textSecondary`) à la place du chiffre et de la courbe.

**Ligne de série** — `SetRow`. Hauteur 56, filet en bas. Colonnes : n° 22 · « Préc. » 60 · kg
(reste) · reps (reste) · case 48, espacées de 8. En-tête : `columnHeader` `textSecondary`
(# · PRÉC. · KG · REPS).
- n° : `emphasis` en 400 `textSecondary` (700 `textPrimary` sur la ligne en saisie) ;
  « Préc. » : `caption` `textSecondary` (« 80 × 8 »), **se touche** pour recopier la série
  précédente (`onPreviousClick`, toute la hauteur de la ligne) ; valeurs : `numberMedium`, chaque
  valeur se touche pour ouvrir le pavé sur ce champ (toute la hauteur de la ligne).
- Une série **validée** peut aussi être en saisie (`editingField`) : case validée, champ actif en
  `liveDim`, l'autre valeur en 700 `textPrimary`.
- **Sans « Préc. »** (`previous = null`, éditeur d'une séance terminée) : la colonne disparaît, de
  la ligne comme de l'en-tête.
- **Texte agrandi** : la colonne « Préc. » s'élargit avec la police (jusqu'à 1,5 fois), les valeurs
  rétrécissent plutôt que de passer à la ligne. **TalkBack** : chaque valeur se lit avec sa série et
  sa colonne (`weightDescription` / `repsDescription`, « Série 3, Kg : 82,5 »).

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
`textPrimary` (« Série »). Pressée : fond `surfaceRaised` qui déborde de 12, rayon 12. Lue par
TalkBack avec son exercice (`description` : « Ajouter une série : Squat »).

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
plus) ; 4 ; une action (bouton secondaire moyen, padding 20). Une phrase, une action ; la phrase
peut manquer quand le titre suffit (« Exercice introuvable »).

**Bottom sheet** — `StudioBottomSheet`. `surfaceRaised`, coins du haut 20, sur `scrim` ; padding
10 / 20 / 20 ; poignée 36 × 4 `surfacePressed` centrée ; titre `titleSection` + 6 + texte `body`
`textSecondary` ; puis les actions (principal 56, texte 48), espacées de 14. Sert aux
confirmations (« Terminer la séance ? ») et au gabarit formulaire.

**Pavé numérique** — `NumericKeypad`. Dans une bottom sheet : `surfaceRaised`, padding 12,
éléments espacés de 10.
- En-tête (optionnel) : segmenté compact (« Kg · série 3 » / « Reps ») et, à droite, boutons icône
  44 en `textSecondary` : `delete` « Supprimer la série » (optionnel, séance en cours) puis
  `keyboard_hide` « Fermer le pavé ». Le segmenté prend la place restante et défile s'il est trop
  large (quatre champs du cardio, texte à 200 %) : les boutons gardent leur cible de 48.
- Grille de 4 colonnes espacées de 6, touches de 52, rayon 12 : chiffres 1–9, « , », 0 sur
  `surfaceControl` (`numberKey`) ; colonne de droite : pas − et + (`surfacePressed`, `action` en
  17 → 16, ex. « −2,5 » / « +2,5 », « −50 » / « +50 ») et effacer (`surfacePressed`, `backspace`, lue « Effacer ») ;
  touche principale sur deux colonnes (`inverse` / `onInverse`, `action`, icône `check` 22
  optionnelle) qui reprend l'action de l'écran (« Valider la série », « Ajouter »).
- Pas configurables : ±2,5 kg, ±1 rep, ±50 g…

**Champ texte** — `StudioTextField` (hors maquette, ajouté avec la séance en cours) : texte libre
au clavier du système, la note d'un exercice. Hauteur 48 au moins, rayon 12, padding 16 / 14,
`body` `textPrimary`, plusieurs lignes (`minLines` / `maxLines` : 4 à 8 pour un avis) ; au repos
`surfaceControl`, **en saisie `liveDim` et curseur `liveText`** (comme tout champ en saisie) ; invite
en `textSecondary` ; désactivé (envoi en cours) : texte en `textTertiary`. Posé dans une bottom sheet. Variante **numérique sur une ligne**
(`singleLine`, clavier numérique) : champs Jour / Mois / Année / Heure / Min de « Modifier la date »,
chacun sous son libellé (`caption` `textSecondary`).

**Mini-barre et onglets** — `MiniSessionBar` + `StudioNavigationBar`.
- Mini-barre (séance réduite) : hauteur 56, `surfaceRaised`, rayon 12, padding 16 / 8, éléments
  espacés de 12 : point `live` 8 ; « **Push** · repos **1:48** » (`actionSmall`, « · repos » en
  500 `textSecondary`, temps en `liveText`) ; bouton texte petit « Reprendre ». Hors repos :
  « **Push** · 17:48 », le chrono de la séance. Posée 8 au-dessus de la barre d'onglets, dans les
  marges d'écran, sur tous les hubs sauf l'Accueil (sa surface « En cours » la remplace).
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
- **Piste de progression** — `ProgressTrack` : la piste de repos (3, rayon 2, `surfacePressed`)
  remplie en `textPrimary` ; décorative, le texte voisin dit l'état (calories du jour sur l'objectif,
  surface « Aujourd'hui »). La piste de repos (`LiveTrack`) est la même, remplie en `live`.
- **Progression des séries** — `SetProgress` : un segment par série prévue, hauteur 3, rayon 2,
  espacés de 3 ; faites `textPrimary`, à faire `surfacePressed`.
- **Barres des semaines** — `WeekBars` : 8 barres espacées de 6, hauteur 28 pour le maximum,
  rayon 3 ; semaine validée `textPrimary`, sinon `textTertiary` ; légende `small` `textSecondary`
  8 dessous (« 8 dernières semaines · 3 séances = semaine validée »).
- **Bloc exercice** — `ExerciseBlockHeader` : titre `titleBlock` + boutons icône petits
  (`monitoring` → fiche, `more_vert`) ; 8 ; puces (précédent : neutre `history` « 3 × 8 ·
  80 kg » ; suggestion : hausse « +2,5 kg ») ; 16 ; en-tête des séries. Sans bouton fiche dans
  l'éditeur d'une séance terminée (puce neutre du groupe à la place des puces) ; les deux boutons
  nomment l'exercice pour TalkBack (« Fiche : Squat », « Options : Squat »).
- **Barre d'action** — `BottomActionBar` : sur le fond, padding 12 / 20, inset de navigation en
  bas ; un bouton principal pleine largeur (icône de tête possible).
- **Champ héros de formulaire** — `FormHeroField` : hauteur 76, padding 16, rayon 12, fond
  `liveDim` en saisie ; valeur `numberLarge` + curseur 2 × 40 `liveText` + unité (`unit`,
  `textSecondary`), espacés de 10.
- **Ligne d'information** — `InfoRow` : hauteur 52, libellé `bodyLine`, valeur `bodyLineStrong`
  (+ chevron 22 `textSecondary` si elle s'ouvre) ; filet `surfaceControl` (posée sur une sheet).
  La valeur prend au plus la moitié de la ligne et passe à la ligne au besoin.
- **Champ sous son libellé** — `LabeledField` : libellé `caption` `textSecondary`, 8, puis le
  contenu (`StudioTextField`, puces de filtre) ; les formulaires longs en sont faits (nouveau
  complément, nouvel exercice, type de séance).
- **Message d'état** — `StatusMessage` : une phrase de succès ou d'erreur en `bodyStrong`
  `textPrimary`, sans couleur ; `statusHelp` la donne en aide d'un formulaire (saisie refusée).
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
4. En bas : mini-barre si une séance est réduite (pas sur l'Accueil), puis la barre d'onglets (le
   hub de premier niveau sélectionné ; une page de second niveau, comme Paramètres, garde l'onglet
   de son hub).

Chaque ligne ouvre un autre gabarit. Tout défile sous la barre d'onglets, fixe. Dans l'app, la
mini-barre et la barre d'onglets sont posées une fois pour tous les hubs par la navigation ; l'écran
ne reçoit que l'inset du haut (`tabScreenInsets()`).

Variante **paresseuse** (`HubLazyTemplate`) : même disposition, pour un hub dont les lignes se
comptent par centaines (Journal, Progrès) ; chaque section (`section` : en-tête, 4, lignes) ou bloc sans
en-tête (`block` : rangée de stats, état vide) est précédé de 40, sauf le premier.

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

Variante **paresseuse** (`FicheLazyTemplate`) : même disposition, le haut (barre → stats) en un
élément puis les sections en éléments paresseux (`HubSections`), pour une fiche dont les lignes se
comptent par centaines (fiche exercice). Sans chiffre héros (aucune donnée), les sections suivent le
titre à `hero` (32). Une action fixe en bas est possible (`bottomAction`) : « Enregistrer » de
l'éditeur d'une séance terminée, remplacé par le pavé numérique pendant une saisie.

### 10.3 En direct

Ce qui se passe maintenant : la séance (demain, un cardio guidé). **Pas d'onglets**, le jaune pour
le temps qui passe, les actions sous le pouce.

1. **Fixes en haut** : `LiveHeader` (réduire · titre · chrono) ; 8 ; `SetProgress` (le chrono et
   « Réduire » restent visibles quand la liste défile).
2. Liste paresseuse des blocs exercice (24 entre eux, glisser-déposer par appui long sur le titre) :
   `ExerciseBlockHeader`, en-tête de colonnes, `SetRow`, le `RestBanner` juste après la dernière
   série validée, et la ligne « + Série » (`AddSetRow`) sous la dernière série. La liste défile
   pour garder le champ en saisie au-dessus du pavé.
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
| Accueil | **Séance** (surface) : reprendre → en direct, ou démarrer (type suggéré) → « Démarrer une séance » · **Dernière séance** : 1 ligne → résumé (fiche, SPEC § 15.12), « Tout voir » → Journal · **Cette semaine** : séances / 3, série, record, 8 semaines (SPEC § 15.8.2) |
| Journal | **Depuis le début** : totaux · **une section par semaine** : séances → résumé (fiche, SPEC § 15.12), appui long → actions (SPEC § 15.8.4) · plus tard : **Calendrier** : mois (*Nouveau*) → hub |
| Progrès | **Poids de corps** (surface) : pesée → formulaire, rappel de pesée → formulaire (SPEC § 15.13.3) · **Exercices** : puces des groupes, stats, une ligne par exercice (mini-courbe) → fiche exercice · **Calories et eau** · **Pesées** (SPEC § 15.9.2) · plus tard : **Records** : Mes records (*Nouveau*) → hub ; **Corps** : poids → fiche ; mensurations (*Nouveau*) → fiche ; photos (*Nouveau*) → hub |
| Nutrition | **Aujourd'hui** (surface) : calories, eau → formulaire · **Compléments** : pris aujourd'hui (case), rappels et suppression → formulaire, nouveau complément → formulaire (SPEC § 15.10.1) · plus tard : protéines (*Nouveau*) → formulaire ; stock (*Nouveau*) → formulaire ; **Tendances** : courbes sur 30 jours (déplacées depuis Progrès) → fiche |
| Paramètres | **Mes données** (stats) · **Préférences** : langue, thème, repos → formulaire ; notifications coupées → réglages du système · **Rappels** : rappel de pesée, un complément → formulaire (SPEC § 15.13.3) · **Données** : sauvegarde auto, Health Connect → formulaire ; export JSON, CSV, import · **À propos** : mises à jour ; rapport de plantage, envoyer un avis → formulaire (SPEC § 15.10.3) |

Correspondance avec les écrans d'avant la refonte (SPEC § 2) — **toute migrée** (étape 5b, SPEC
§ 15.11) :

| écran d'avant | gabarit |
|---|---|
| Accueil (§ 2.2) | hub Accueil |
| Démarrer une séance (§ 2.3) | surface « Prochaine séance » de l'Accueil ; formulaire en bottom sheet (type, puis poids au pavé) |
| Séance en cours (§ 2.4 – 2.6) | en direct |
| Sélecteur d'exercices (§ 2.7) | formulaire : bottom sheet haute, liste d'options à cocher avec recherche |
| Historique d'un exercice (§ 2.8) | fiche exercice |
| Éditer une séance terminée (§ 2.9) | fiche (paresseuse, « Enregistrer » en bas) + formulaires (type, poids, sélecteur) |
| Progrès (§ 2.10) | hub Progrès ; pesée → formulaire |
| Nutrition (§ 2.11) | hub Nutrition ; saisies → formulaires |
| Paramètres (§ 2.12) | hub Paramètres, ouvert depuis l'Accueil |
| Politique de confidentialité Health Connect (§ 15.3) | fiche (titre et paragraphes) |

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
- **Séance en cours** (SPEC § 15.7, à la migration de l'écran) :
  - **note et retrait d'un exercice** : menu ⋮ du bloc → bottom sheet (titre = l'exercice, note
    dans un `StudioTextField`, « Retirer de la séance » en bouton secondaire puis confirmation,
    « Fermer ») ;
  - **supprimer une série** : bouton `delete` de l'en-tête du pavé, puis confirmation ;
  - **échauffement** : une `ListRow` « Échauffement · 2/5 exercices » en tête de liste, qui ouvre
    une bottom sheet de cinq lignes à cocher ;
  - **puce « précédent »** du bloc : la date de la séance d'où vient la colonne « Préc. » (icône
    `history`) — les séries elles-mêmes sont déjà dans la colonne ;
  - **ajouter un exercice** : bouton secondaire pleine largeur (icône `add`) en fin de liste ; sans
    exercice, l'état vide porte cette action ;
  - **barre du bas** hors saisie : « Terminer la séance » seul (`BottomActionBar`).

Décidé (14/11/2026, SPEC § 15.8) :

- **Navigation** : quatre onglets ; Paramètres poussé au-dessus de l'Accueil, barre d'onglets
  gardée (Accueil sélectionné) ; mini-barre sur tous les hubs sauf l'Accueil ; transitions en fondu
  (`standard`), suivies par le geste de retour prédictif.
- **Accueil** : une seule surface (« En cours » ou « Prochaine séance »), puis « Dernière séance »
  et « Cette semaine » ; l'annonce de mise à jour et le transfert depuis l'ancienne app deviennent
  des sections (ligne + bouton texte discret « Plus tard » / « Masquer »).
- **Journal** : totaux, puis une section par semaine (titre « Cette semaine », « Semaine
  dernière », « Semaine du 26 oct. », compteur à droite) ; appui long sur une séance → sheet
  d'actions ; « Modifier la date » en formulaire (bottom sheet, champs numériques).

Décidé (14/11/2026, SPEC § 15.9) :

- **Fiche exercice** : chiffre héros = la dernière séance de la période, variation depuis le premier
  point (verte, rouge ou grise), courbe touchable, période « Tout » par défaut ; record personnel et
  1RM estimé en rangée de stats ; une ligne par séance (meilleure série, 1RM, volume), « Record » sur
  la séance qui détient le record de la période. Pas de contrôle segmenté tant qu'il n'y a qu'une
  métrique.
- **Progrès** : la surface « Mesure » porte le poids de corps (variation en gris, sans objectif
  déclaré) ; « Exercices » se filtre par des puces de groupe ; « Calories et eau » reste dans Progrès
  avec une courbe à ligne d'objectif, jusqu'à la migration de Nutrition ; supprimer une pesée passe
  par une confirmation.
- **Date dans un formulaire** au pavé (pesée) : puces « Aujourd'hui » / « Hier » et une ligne
  d'information « Date » qui ouvre le sélecteur de date du système (Material 3, aux couleurs de
  `StudioTheme`).

- **Logo** : variante **« Deux disques »** (`T2-Logo.dc.html`), haltère `#FFC93D` sur graphite
  `#17181B`. Icône adaptative (fond graphite, avant-plan vectoriel, couche monochrome pour les
  icônes à thème), icône de l'écran de démarrage sur un disque graphite, petite icône des
  notifications en silhouette blanche (accent graphite). La variante preview ajoute un bandeau jaune
  au bas de l'icône. Ressources : `res/drawable/ic_launcher_foreground.xml`,
  `ic_launcher_monochrome.xml`, `ic_notification.xml` ; `app/src/preview/res/drawable/` pour la
  preview.

Décidé (14/11/2026, SPEC § 15.10) :

- **Nutrition** : une seule surface, « Aujourd'hui » (`DailySurface`) : les calories du jour en
  chiffre héros et l'état en mots, vert dans l'objectif, rouge au-dessus (la couleur suit l'objectif),
  l'eau et les compléments pris en stats, « Calories » et « Eau » au pavé numérique (formulaire :
  « Ajouter » / « Total du jour », « Nouveau total », « Objectif quotidien » qui fait passer la sheet
  à l'objectif). Compléments en `ListRow` à case séparée ; leur sheet : ligne à cocher « Rappels »,
  une `InfoRow` par rappel, « Ajouter un rappel » (bouton texte), suppression confirmée.
- **Formulaire long** (nouveau complément) : une sheet défilante au clavier du système, champs
  `StudioTextField` sous leur libellé (`caption`), unités en puces de filtre, rappels comme
  ci-dessus, bouton principal « Enregistrer ».
- **Choix de l'heure** : le `TimePicker` de Material 3 en dialogue `surfaceRaised` rayon 20, heure ou
  minute en saisie sur `liveDim`, boutons texte (« Supprimer » discret à gauche, « Annuler », « OK »).
- **Paramètres** : hub de second niveau (`FicheTopBar` puis `titleScreen`), « Mes données » en
  rangée de stats, lignes `ListRow` avec la valeur à droite ; un réglage à options s'ouvre en sheet
  (contrôle segmenté pour la langue et le thème, puces pour le minuteur) et se ferme au choix ; les
  réglages composés (sauvegarde automatique, Health Connect, rapport de plantage, avis) en sheet.
- **Messages de succès et d'erreur** : une phrase en `textPrimary` 600, sans couleur (pas d'encart
  coloré) ; le rouge reste à la baisse.

Décidé (15/11/2026, SPEC § 15.11) :

- **Démarrer une séance** : un formulaire en bottom sheet à deux étapes, « Type de séance »
  (préréglages en puces de filtre sous le libellé de leur groupe, nom perso en `StudioTextField`,
  « Suivant ») puis « Poids de corps » (champ héros, puces 55 … 100 sur une rangée défilante, ligne
  d'information « Type de séance » qui revient à l'étape 1, pavé, « ✓ Commencer »). Posé sur
  l'écran qui l'ouvre ; plus d'écran plein.
- **Recherche d'exercice** (question ouverte, tranchée) : le sélecteur est une bottom sheet haute
  (92 %) : titre et bouton **+** « Nouvel exercice », recherche, puces de groupe défilantes, une
  `ListRow` à cocher par exercice (sous-titre = groupe ; cochée et inactive s'il est déjà dans la
  séance). Les actions d'un exercice perso (modifier, supprimer) passent par l'appui long ; la sheet
  change d'étape (formulaire, actions, confirmation) plutôt que d'empiler les sheets.
- **Éditeur d'une séance terminée** : fiche paresseuse ; « Type de séance » et « Poids de corps »
  en lignes qui ouvrent un formulaire ; un bloc par exercice comme en séance, sans « Préc. » ni
  repos ; « Enregistrer » en barre d'action, le pavé à sa place pendant une saisie ; abandon des
  modifications confirmé en bottom sheet.
- **Accessibilité** : les règles du § 3 (texte agrandi), du § 9 (cibles de 48, libellés TalkBack)
  et l'aplat `live` foncé en clair (§ 2).

Décidé (15/11/2026, SPEC § 15.12) :

- **Résumé de fin de séance** : gabarit fiche paresseux, sans composant nouveau. Bloc titre
  « Séance terminée · date · heure » puis le nom ; chiffre héros = volume total, variation par
  rapport à la dernière séance du même programme (« vs Push du jeu. 5 nov. ») ; rangée de stats
  (durée, séries) ; section « Exercices » avec « Records battus · n » en `up` à droite, une
  `ListRow` par exercice (meilleure série en sous-titre, « Record » en variation seule). En fin de
  séance, **aucun bouton en haut** (une barre vide de 48) et « Terminer » seul en `BottomActionBar`
  (la maquette avait ✕, note et « Enregistrer la séance ») ; en consultation depuis le Journal, ←
  et ⋮ (actions de la séance), sans barre d'action.

Décidé (15/11/2026, SPEC § 15.13) :

- **Pesée facultative et rappel de pesée** : aucun composant nouveau. Au démarrage d'une séance,
  l'étape « Poids de corps » garde son formulaire (champ vide, la dernière pesée en aide) et gagne
  un bouton texte « Passer » sous la ligne du type ; déjà pesé aujourd'hui, elle n'a plus de champ ni
  de pavé (aide, ligne du type, « ✓ Commencer »). Paramètres gagne une section « Rappels » (rappel de
  pesée, puis une ligne par complément vers sa sheet de Nutrition) ; la sheet du rappel est un
  réglage composé : ligne à cocher, ligne d'information « Heure » (`TimePicker`), « Jours » en puces
  de filtre à choix multiple, « Fermer ». Sur la surface « Poids de corps », la cloche
  `notifications` ouvre la même sheet, avant le +.

Hors périmètre pour l'instant :

- **Données nouvelles** : calendrier du Journal, Mes records, mensurations, photos, protéines, stock des compléments, objectif de poids
  (prise de masse / sèche). La maquette les dessine pour prouver que le système tient (« 3
  fonctionnalités, zéro nouveau composant ») ; les ajouter est une décision produit à part.

Ouvert : rien (la recherche d'exercice est tranchée ci-dessus).

## 14. Code

- `ui/designsystem/theme/` : `StudioTheme` (palettes, `CompositionLocal`s, correspondance avec
  `MaterialTheme` pour que les composants Material encore utilisés prennent la palette),
  `StudioColors`, `StudioTypography` (familles Archivo et Archivo condensée), `StudioSpacing`,
  `StudioShapes`, `StudioMotion`, et `SystemNightMode` (le thème choisi transmis au système pour le
  splash). L'app entière est posée une fois dans `StudioTheme(darkTheme = …)` au thème choisi, par
  `MainActivity` (et l'écran de politique de Health Connect par son activité). Accès :
  `Studio.colors.live`, `Studio.typography.numberHero`, `Studio.spacing.screen`,
  `Studio.shapes.control`, `Studio.motion.fast`.
- `ui/designsystem/components/` : les composants du § 9 (`Foundation.kt` : icône, variation,
  point et étiquette en direct, chiffre qui défile ; puis un fichier par composant ou famille ;
  `TextField.kt` pour le champ texte ; `TrendCurve.kt` pour les trois courbes et
  `CurveGeometry.kt`, leur géométrie pure, testée en JVM ; `FitText`, dans `Foundation.kt`, pour un
  mot qui doit tenir sur une ligne).
- `ui/designsystem/templates/Templates.kt` : les quatre gabarits du § 10 (`HubTemplate` et sa
  variante paresseuse `HubLazyTemplate`, `FicheTemplate` et `FicheLazyTemplate`, `LiveTemplate`,
  `FormSheetTemplate`), en
  composables à emplacements ; ils reçoivent les insets du système (`ScreenInsets` ;
  `tabScreenInsets()` pour un onglet, dont la barre absorbe l'inset du bas).
- Icônes `res/drawable/ds_ic_*.xml` ; police `res/font/archivo.ttf` (+ licence
  `assets/licenses/archivo-OFL.txt`) ; libellés d'accessibilité `ds_*` dans `strings.xml`.
- Aperçus : chaque composant et gabarit a ses `@Preview` en sombre et en clair, dans le source set
  `debug` (`app/src/debug/java/com/musclenote/android/ui/designsystem/`), comme les aperçus des
  écrans : un aperçu par composant (`DataComponentPreviews`, `ActionComponentPreviews`,
  `TemplatePiecePreviews`), les deux planches de composants recomposées à la taille de la
  maquette (`BoardPreviews`, 1400 × 1320) et les gabarits remplis comme les écrans de la maquette
  (`TemplatePreviews`, 412 × 915, insets de la maquette).
- Textes des composants (libellés d'accessibilité, « Tout voir »…) dans `strings.xml` FR / EN.
- L'ancien thème (`ui/theme/`, Inter, couleurs de groupe, icônes Outlined) est **retiré** depuis
  l'étape 5b (SPEC § 15.11) : tout écran n'utilise que `Studio`, sans couleur, taille, police ni
  graisse posée à la main.
