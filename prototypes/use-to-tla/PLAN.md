# Plan : de la preuve de concept aux opérations

Objectif : vérifier par TLC les comportements des modèles USE (suites d'opérations), ce que le validateur actuel ne fait pas. Chaque unité se termine par une vérification. On ne passe à la suivante que si la précédente est validée.

## Ordre et dépendances

```
P0 (sauvegarde du prototype)   branche de session de kapp667/use4llm-plugins, PR brouillon « do not merge »
A1 (issue 2)  ─┐  indépendantes, publiques, dans kapp667/use4llm
A2 (issue 1)  ─┘
B1 -> B2 -> B3 -> B4 -> B5     le code reste dans prototypes/use-to-tla/, jamais fusionné
                         └-> G (décision : en faire un vrai plugin ou non)
A2 avant B1 si possible : la correction de USE supprime un contournement du prototype.
```

Validation par l'auteur : A1 et A2 (PR publiques) et G (passage au vrai plugin).

## P0. Sauvegarder le prototype

- [x] Dossier `prototypes/use-to-tla/` sur la branche de session de use4llm-plugins. Un README dit qu'il est jetable, qu'il ne faut ni le fusionner ni le copier, et comment le relancer.
- [x] Les chemins du conteneur sont remplacés par `USE_HOME`, `USE_SRC`, `MV_JAR` et `TLA2TOOLS`. Les sorties générées et les jar ne sont pas versionnés.
- [x] Vérification : trois cas relancés depuis la copie du dépôt (témoin t048, sonde, Cars) donnent les mêmes valeurs que l'original, sans aucun désaccord.
- [x] PR en brouillon kapp667/use4llm-plugins#43 « do not merge ». La CI `build-and-test` tourne dessus et passe. CodeQL est rouge pour deux alertes dont la source est dans les vieilles copies SVN, dont le retrait est confié à la session USE4LLM.
- [ ] Après chaque étape de la série B, le dossier est mis à jour sur la même branche. Fait pour B1 à B3.

## A1. Corriger `assert valid invs` (kapp667/use4llm#2)

- [x] Fichiers : `MAssertGlobalInvariants.java`, plus un test JUnit 5 (`MTestSuiteTest`) au lieu d'un test shell. PR kapp667/use4llm#3.
- [x] Changement : passer une liste vide au lieu de `null`.
- [x] Le test échoue avant le changement et passe après, en local. `mvn verify` passe, dont 129 `ShellIT`. Pas de CI : Actions n'est pas activé sur le fork.
- [x] Le cas minimal de l'issue réussit avec la version construite : c'est le cas que reprend le test.

## A2. Corriger la copie d'état (kapp667/use4llm#1)

- [x] Fichier : `MSystemState.java:156-158`, plus un test unitaire qui reprend la variation de l'issue.
- [x] Changement : donner aux trois contrôleurs les tables de la copie.
- [x] Le test échoue avant et passe après, en local. Pas de CI, pour la même raison.
- [ ] La comparaison d'invariants du prototype tourne sans le contournement par réflexion, avec 0 désaccord et 11 bugs volontaires détectés sur 11. En attente : le prototype tourne sur la release 7.5.0, qui n'a pas la correction.

## B1. Identifiants d'objets en model values

- [x] Mesure de référence d'abord : `all.sh` donne 0 désaccord et 11 bugs volontaires détectés sur 11.
- [x] Changement : les objets deviennent des model values TLC, ce qui permet la symétrie.
- [x] Vérification : mêmes chiffres qu'avant, et la symétrie est acceptée par TLC sur un modèle.

## B2. Fichier de réglages

- [x] Forme des données d'abord : nombre d'objets par classe, domaines de valeurs, opérations que l'environnement peut appeler, moment où les invariants doivent tenir, regroupements comme `new` suivi de `birth()`.
- [x] Vérification : avec ce fichier, `civstat` donne la même spec que la traduction à la main (`ops-probe/`).

## B3. Traduire les corps SOIL en actions

- [x] Changement : corps SOIL -> action avec `UNCHANGED` pour le reste, `pre` -> garde, `post` vérifié avec `@pre`. Refus explicite de ce qui n'est pas traduit.
- [x] Vérification sur `civstat`. TLC trouve `nameIsUnique`, détecte le `marry()` cassé, et compte 11 161 états (3 personnes) et 524 545 états (4 personnes) sans symétrie, comme la version à la main.
- [x] Mesure sur les 18 modèles du corpus qui ont des corps SOIL : nombre traduits, refus par construction.

## B4. Oracle de comportement, dans les deux sens

- [ ] Chaque contre-exemple de TLC est rejoué dans USE, pas à pas. USE doit accepter chaque pas et constater la même violation.
- [ ] Chaque scénario existant (18 scripts d'exemples, 10 tests shell avec pre/post) doit être une exécution que la spec autorise.
- [ ] Les états atteints par TLC servent de témoins au ModelValidator, qui doit trouver une solution.
- [ ] Budget : rester dans l'ordre des 11 minutes de l'oracle actuel.

## B5. Sondes de comportement et bugs volontaires

- [ ] Sondes dérivées des 23 tests `StatementEffectTest` et des 10 tests shell de contrats, pas inventées.
- [ ] Bugs volontaires dans la traduction des actions : cadre oublié, garde ignorée, `@pre` lu dans le mauvais état, etc.
- [ ] Critère : chaque bug volontaire est détecté. Un bug non détecté est un trou de couverture à combler.

## G. Décision de publication (à toi)

- [ ] Si oui : un plugin dans use4llm-plugins, un fichier `ci/oracle/expected/tla/{examples,tests}.tsv` et une étape de CI séparée. Le format est convenu avec la session USE4LLM, qui reste responsable de l'oracle.

## Non prouvé à ce jour

- Les opérations sans corps SOIL, avec seulement pre/post : il faudrait choisir parmi tous les états qui respectent la postcondition. Le problème du cadre reste ouvert.
- Les machines à états : un seul exemple dans le corpus.
- Les performances de TLC sur de gros modèles.
- Lean et TLAPS : hors de ce plan.
