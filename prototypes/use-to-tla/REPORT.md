# Prototype USE vers TLA+ : rapport

Jetable. Local uniquement, rien n'a été poussé. Date : 2026-11-26.

## Question

Un traducteur déterministe d'un modèle USE vers TLA+ est-il faisable et fidèle ? Faut-il l'écrire en Java ?

## Réponse

Oui sur le sous-ensemble testé, et en Java. Toutes les mesures ci-dessous sont reproductibles.

## 1. Invariants (traducteur Java, `translator/`)

Un plugin USE (`tla <dossier>`) traduit le modèle et l'état courant de la session. Pour chaque état (l'état de départ et 20 mutations), USE et TLC donnent la valeur de chaque invariant, globalement et par objet. Un script compare.

Couverture : 653 invariants traduits sur 781. Les 128 autres sont refusés avec le nom de la construction (`results/translation-coverage.tsv`).

Accords, au format accords/désaccords (`results/evidence.tsv`) :

| Source des états | Modèles | Invariants distincts | inv vrai | inv faux | obj vrai | obj faux | obj indéfini |
| --- | --- | --- | --- | --- | --- | --- | --- |
| W, témoins acceptés par USE | 6 | 17 | 301/0 | 56/0 | 600/0 | 46/0 | 13/0 |
| W-, témoins rejetés par USE | 21 | 325 | 6119/0 | 696/0 | 29302/0 | 1381/0 | 342/0 |
| V, solutions du ModelValidator | 19 | 36 | 546/0 | 210/0 | 476/0 | 95/0 | 135/0 |
| P, sondes écrites pour le test | 1 | 48 | 382/0 | 626/0 | 2480/0 | 1197/0 | 499/0 |

Sans doublons sur W, W- et V, cela fait 357 invariants distincts dans 37 modèles.

La comparaison sait échouer. Les 11 bugs volontaires introduits dans le traducteur sont tous détectés, avec 72 à 5 351 désaccords chacun (`results/mutants.tsv`). Quatre d'entre eux (`div`, `select`, `collect`, `if`) ne sont détectés que par les sondes P.

Forme model checking : sur l'état de départ, TLC ne signale aucune violation exactement quand USE juge tous les invariants vrais, dans 47 cas sur 47. Sur un état violant, l'invariant signalé est faux pour USE dans 35 cas sur 35.

Désaccords rencontrés puis résolus (`results/disagreement-log.tsv`) :
- un bug du traducteur (`including` et `excluding` face à l'indéfini) ;
- un bug de lecture de la sortie TLC ;
- deux limites de TLC, la taille de pile et l'évaluation paresseuse.

Taille : environ 900 lignes pour le traducteur, plus le plugin et les scripts.

## 2. Opérations (sonde à la main, `ops-probe/`)

`civstat` a été traduit à la main avec des règles fixes. Un corps SOIL devient une action, tout ce qu'il n'affecte pas reste `UNCHANGED`, `pre` devient la garde et `post` est vérifié avec `@pre`.

- TLC trouve que `birth()` permet deux personnes de même nom. USE le confirme sur le chemin rejoué.
- Un corps d'opération cassé volontairement est détecté au pas 4.
- Avec 5 personnes, TLC explore 22,7 millions d'états en 6 min 27 s. Avec la symétrie, il en explore 215 729 en 10 s.

## 3. Ce que le modèle USE ne dit pas

- **Le moment où les invariants doivent tenir.** Juste après `new`, un objet a des attributs indéfinis.
- **L'environnement.** Combien d'objets, quelles valeurs, quelles opérations l'environnement peut appeler.

Il faut donc un fichier de réglages, comme les bornes du ModelValidator. Le traducteur reste déterministe : sa sortie ne dépend que du modèle et de ce fichier.

## 4. Pourquoi Java

L'export XMI de USE ne contient aucun invariant. Il écrit les corps d'opérations comme du simple texte. Un autre langage devrait réécrire :
- l'analyse de USE, environ 4 300 lignes de grammaire ;
- le typage et le désucrage : `collect` implicite, types Set, Bag et OrderedSet, noms de rôles par défaut, redéfinition d'opérations.

Il aurait de toute façon besoin de USE comme référence.

## 5. Limites

- **Le corpus de l'oracle teste peu les invariants.** La preuve vient surtout de W-, de V et de sondes écrites pour le test.
- **Non traduit :**
  - les opérations, pre/post et SOIL (hors sonde à la main), les machines à états, un vrai `Next` ;
  - les contraintes structurelles (multiplicités, composition) ;
  - les réels, les tuples, et les associations qualifiées, dérivées ou avec `subsets` ;
  - le dépassement d'entier.
- **L'ordre interne de USE.** USE parcourt les ensembles dans l'ordre de hachage de Java. Les constructions où cet ordre se voit, comme `any`, sont refusées.
- **Les performances de TLC** dépendent de la façon dont le TLA+ est écrit. Les identifiants d'objets doivent devenir des model values pour profiter de la symétrie.

## 6. Bugs de USE trouvés et signalés

- https://github.com/kapp667/use4llm/issues/1 : la copie d'un état écrit les valeurs dérivées dans l'état d'origine.
- https://github.com/kapp667/use4llm/issues/2 : `assert valid invs` plante toujours.

## Relancer

- `bash translator/all.sh` (environ 27 min sur 4 cœurs).
- `cd ops-probe && java -cp ../tools/tla2tools.jar tlc2.TLC -config sym-5.cfg CivstatSym`
