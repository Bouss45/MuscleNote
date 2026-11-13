# Export Claude Design — « MuscleNote — refonte »

Source du design system décrit dans [`DESIGN.md`](../../DESIGN.md) : le canvas Claude Design
« MuscleNote — refonte », page **Tour 2 · Studio épuré** (version du 12/11/2026), tel qu'exporté.
Ces fichiers sont la référence visuelle ; ils ne sont pas compilés dans l'app.

- `canvas.json` : index du canvas (planches, positions, pages ; la page « Tour 1 · Directions »
  y est listée mais ses planches ne sont pas exportées ici).
- `Main.dc.html` : organisation des onglets (tour 1, toujours valable).
- `T2-Tokens.dc.html` : couleurs, typographie, espacements, rayons, mouvements.
- `T2-Navigation.dc.html` : navigation qui grandit sans nouvel onglet.
- `T2-Composants-1.dc.html` / `T2-Composants-2.dc.html` : composants et leurs états (le thème se
  choisit par la propriété `theme` ; les fichiers `*-clair` importent la même planche en clair).
- `T2-Gabarits.dc.html` : les 4 gabarits d'écran.
- `T2-Accueil`, `T2-Seance`, `T2-Resume`, `T2-Fiche`, `T2-Progres` : écrans, sombre et clair.
- `T2-Records`, `T2-Mensurations`, `T2-Stock` : preuves « 3 fonctionnalités, zéro nouveau
  composant ».
- `T2-Logo.dc.html` : logo haltère, 3 variantes (non tranché).

Format : pages « Design Component » (`<x-dc>`), HTML statique dont les trous `{{c.…}}` sont les
couleurs de la palette déclarée en bas de chaque fichier ; `support.js` (le moteur de Claude
Design) n'est pas inclus.
