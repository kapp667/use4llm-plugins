# Prototype USE vers TLA+ : rapport

Jetable. Local uniquement, rien n'a été poussé. Date : 2026-11-26.

## Question

Un traducteur déterministe d'un modèle USE vers TLA+ est-il faisable et fidèle ? Faut-il l'écrire en Java ?

## Réponse

Oui sur le sous-ensemble testé, et en Java. Toutes les mesures ci-dessous sont reproductibles.

## 1. Invariants (traducteur Java, `translator/`)

Un plugin USE (`tla <dossier>`) traduit le modèle et l'état courant de la session. Pour chaque état (l'état de départ et 20 mutations), USE et TLC donnent la valeur de chaque invariant, globalement et par objet. Un script compare.

Couverture : 701 invariants traduits sur 831, dont 48 sur 50 dans la sonde P. Sans la sonde, 653 sur 781. Les 130 autres sont refusés avec le nom de la construction (`results/translation-coverage.tsv`).

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

## 7. Série B : unités B1 à B3 (2026-11-26)

Les sorties des runs restent hors du dépôt. Seuls les TSV de synthèse sont ici. La section 1 et le README annonçaient 653 invariants traduits sur 781 : c'est le corpus sans la sonde P. Avec elle, `translation-coverage.tsv` dit 701 sur 831, et la référence de B1 redonne ce chiffre.

### B1. Les objets deviennent des model values

- Changement (`TlaCommand`) : chaque objet est une model value TLC `O_<nom>`, déclarée en `CONSTANT`. `ObjName` rend le nom USE pour l'affichage. Une fonction s'écrit en arbre équilibré de `@@`.
- Référence, sur le code d'avant : 0 désaccord, 10 vérifications d'erreur, 11 bugs volontaires détectés sur 11, en 28 min 48 s. `evidence.tsv`, `agreement.tsv` et `mutants.tsv` sont identiques aux fichiers versionnés.
- Premier essai après le changement : 105 désaccords, tous sur t101 (5 967 objets), valeur « missing ». Cause : SANY manque de mémoire au contrôle des niveaux. Avec des constantes déclarées, chaque nœud d'une chaîne de n `@@` garde l'ensemble des constantes qu'il contient, soit environ n²/2 entrées. Corrigé par l'arbre équilibré (D5 dans `disagreement-log.tsv`). L'évaluation de t101 passe de 25 s à 10 s.
- Après : 0 désaccord. Les fichiers `evidence`, `agreement`, `disagreements`, `errors`, `refusals`, `constructs`, `translation-coverage` et `mutants` sont identiques à la référence, octet pour octet, ainsi que les verdicts de `mc.tsv`.
- Symétrie : `UseMCSym` reprend `UseMC` avec `SYMMETRY Permutations(...)` sur les objets d'une classe de S0. TLC l'accepte et rend le même verdict que sans symétrie sur 21 modèles. Avec des chaînes à la place des model values, TLC refuse (« Symmetry function must have model values as domain and range »). Trois cas ont échoué parce que la classe choisie était trop grande : TLC construit les n! permutations, et 31 objets épuisent la mémoire. Le générateur prend maintenant une classe de 2 à 6 objets ; relancés, ces trois cas passent ou n'ont plus de module de symétrie.
- Après B3, le code final régénère les modules d'invariants des 202 cas du corpus : ils sont identiques octet pour octet à ceux du run B1.

### B2. Le fichier de réglages

Forme (une directive par ligne, documentée en tête de `Settings.java`) :

- `objects C n` : un pool de n model values pour la classe C ;
- `domain T v...` : les arguments de type T (`'texte'`, entiers, `a..b`, `true`, `#littéral`, `undefined`, et `objects` pour une classe) ;
- `param C::op p v...` : le domaine d'un paramètre ;
- `step new C`, `step call C::op`, `step new C call op` (le regroupement en un seul pas) ;
- `invariants every-state`, `after-calls` ou `after C::op...` ;
- `skip <nom>` : un invariant, une postcondition ou une multiplicité qu'on ne vérifie pas ;
- `bound T v...` : TLC n'explore pas les états où un attribut Integer ou String sort de ces valeurs ;
- `symmetry`.

`tlaops default` prend 2 objets par classe concrète, Integer 0..2, String 'a' et 'b', les objets existants ou l'indéfini, un pas `new C` par classe et un pas `call C::op` par opération qui n'est pas une requête, les bornes égales aux domaines, et les invariants partout.

Vérification : `ops-probe/settings/civstat.settings` décrit l'environnement de `Civstat.tla` (AtomicBirth). TLC vérifie le sens dans les deux directions, avec une correspondance d'états écrite à la main (`ops-probe/match/`) : la spec générée raffine la spec à la main, et l'inverse, avec 11 161 états de chaque côté. Un témoin négatif (un champ de la correspondance faussé) est détecté dès le premier pas. Une seule différence est voulue : dans le pas regroupé, `@pre` lit l'état après `new`, comme USE ; la spec à la main lisait l'état d'avant. Cela ne change rien pour civstat.

Ordre de travail : ce contrôle a besoin des actions générées, donc le code de B3 a été écrit avant de vérifier B2. Aucune vérification n'a été sautée.

### B3. Les corps SOIL deviennent des actions

Règles (`Ops.java`, commande `tlaops`) :

- L'état est un seul enregistrement `st`, de la forme que lit le traducteur d'invariants. `last` garde le pas, ses arguments et l'état d'avant l'appel (`pre`), que lit `@pre`.
- Un corps est une fonction d'une configuration (l'état et les variables SOIL) vers la suivante, ou `Fail` quand USE annulerait l'appel : un objet indéfini à affecter ou à lier, un lien déjà présent ou absent, une précondition qui n'est pas vraie, un `result` jamais affecté. Ce qu'une instruction n'affecte pas garde sa valeur.
- Un `if` dont la condition est indéfinie prend la branche `else`, comme SOIL. `declare` remet la variable à l'indéfini. Un `new` dans un corps prend un objet libre du pool et applique les `init:` dans l'ordre du modèle.
- Une postcondition est un invariant sur (`last.pre`, `st`). Une postcondition non traduisible fait refuser l'opération, car USE annulerait l'appel si elle était fausse.
- L'appel suit la classe de l'objet (redéfinitions), comme `MObjectOperationCallStatement`.

Vérifications sur civstat (`ops-probe/check-generated.sh`, résultats dans `ops-probe/civstat-generated.tsv`) :

| Run | Résultat de TLC | USE sur le chemin rejoué |
| --- | --- | --- |
| invariants partout, `new` et `birth()` séparés | `attributesDefined` violé au pas 2 | `attributesDefined` FAILED |
| `new` + `birth()` en un pas | `nameIsUnique` violé : deux `birth('Ada', #female)` | `nameIsUnique` FAILED |
| `marry()` cassé (l'époux n'est plus marié) | `maleHasMarriedWife` violée au pas 4, `O_Person2.marry(O_Person1)` | la même postcondition ne tient pas |
| 3 personnes, sans `nameIsUnique` | aucune erreur, 11 161 états | |
| 4 personnes | aucune erreur, 524 545 états | |
| 5 personnes, symétrie | aucune erreur, 215 729 états | |

Les trois nombres d'états sont ceux de la spec à la main. Temps, à conditions égales (1 worker, même machine) : 69 s contre 18 s pour 4 personnes, 111 s contre 29 s pour 5 avec symétrie. Les deux specs génèrent le même nombre d'états (1 918 625 et 1 009 289). Sans invariants, c'est 58 s contre 18 s : l'écart vient surtout du calcul des successeurs. Inférence, sans profilage : la copie de la configuration à chaque instruction et les gardes `Undef` du traducteur. Les 9 s et 10 s de la section 2 ont été mesurés dans d'autres conditions, sans doute avec plusieurs workers (inférence).

Corpus (`translator/scripts/ops-corpus.sh`, `results/ops-corpus.tsv`, `results/ops-refusals.tsv`), réglages par défaut : 20 modèles contiennent `begin`. 3 ne se chargent pas dans USE (tests négatifs du parseur). Les 17 autres ont 47 opérations qui ne sont pas des requêtes ; 19 sont traduites, dans 11 modèles, dont 7 entièrement. Refus :

| Construction | Opérations | Modèles |
| --- | --- | --- |
| classe avec une machine à états | 17 | 4 |
| appel d'une opération à corps SOIL dans un corps | 4 | 3 |
| `for` sur une collection non ordonnée (USE parcourt dans l'ordre de hachage) | 4 | 1 |
| opération sans corps (pre/post seulement) | 2 | 1 |
| `Real` | 1 | 1 |

Chaque run de TLC prend 1 à 2 s, surtout le démarrage. Le plus grand espace borné compte 15 681 états (projectworld, 4 s). TLC signale 4 violations : `attributesDefined` (civstat) et une multiplicité de `Employs` (projectworld), toutes deux juste après un `new`, et deux postconditions fausses par construction des tests (t084 `notOne`, t121 `inc2`). USE confirme les quatre sur le chemin rejoué. Les domaines par défaut ne satisfont pas certaines préconditions : `accept(i)` de CoffeeDispenser demande une pièce de 10 à 200, et les mariages de t094 et t095 demandent un genre que rien n'affecte.

Désaccords : aucun entre la spec générée et la spec à la main, ni entre TLC et USE sur les chemins rejoués.

Risques :

- Une erreur d'évaluation dans une action (une navigation qui trouve plusieurs objets là où la multiplicité en permet un) arrête TLC par un `Assert`, alors que USE annulerait l'appel. L'erreur est bruyante, pas silencieuse.
- `skip` sur une postcondition garde des pas que USE annulerait si elle était fausse.
- Le `CHOOSE` d'un `new` dans un corps est sûr sous symétrie par un argument, pas par une preuve. Un pool épuisé désactive le pas : c'est une borne, comme celles du ModelValidator.
- La spec générée est environ 4 fois plus lente que la spec à la main.

Ce dont B4 aura besoin :

- Rejouer une trace : `scripts/trace2soil.py` lit la trace JSON de TLC (`-dumpTrace json`), mais il devine les types. Une chaîne « Undef » ou « E::x » serait mal lue. Il faut donner les types des paramètres, par exemple dans `ops.tsv`.
- Dans le shell, une postcondition fausse met USE en pause (« Evaluation is paused ») et USE attend `c`. Il faut fermer l'entrée standard et rejouer pas à pas.
- Comparer l'état de USE et celui de TLC à chaque pas, avec la correspondance des noms : `!create O_Person1 : Person` garde le nom, mais un `new` dans un corps donne `C1` dans USE et le premier objet libre dans TLC.
- Pour les scénarios USE comme exécutions permises : les scripts utilisent `!create`, `!set` et `!insert` hors de toute opération. Il faut soit des pas d'environnement pour ces commandes, soit un état initial pris à USE (l'encodeur d'états de `tla` sait le faire). Il faut aussi des pools et des domaines dérivés du scénario.
