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

## 8. Série B : unités B4 et B5 (2026-11-26)

Les sorties des runs restent hors du dépôt. Les TSV de synthèse sont dans `translator/results/ops-*.tsv`. Chaque nombre ci-dessous est mesuré sur un run, sauf mention contraire.

### Outils ajoutés

- **`infotla <fichier> <étiquette> [expr ;; expr ...]`**, une commande du plugin. Elle écrit l'état courant de USE dans la forme de l'état de `tlaops` (`objs`, `cls`, `att`, `lnk`), avec l'encodeur de valeurs de `tla`, celui que l'oracle des invariants a validé, et la valeur de chaque expression OCL. Son nom commence par `info`, donc USE la lance aussi pendant la pause qui suit une postcondition fausse (`Shell.ppcShell`). L'état est alors celui que l'appel a laissé.
- **`types.tsv`**, écrit par `tlaops` : les pools, les classes et leurs parents, les énumérations, le type de chaque attribut de l'état, des paramètres et des résultats, chaque opération déclarée (corps, requête ou rien), et la précondition que vérifie chaque opérateur `PreC_`. Les outils de trace ne devinent plus aucun type.
- **Le réglage `contract C::op`** traduit la garde `Pre_` et les postconditions d'une opération, avec ou sans corps, pour un pas `openter` ... `opexit`. Aucun pas de `Next` ne l'exécute : TLC ne sait pas énumérer les états qu'une postcondition permet. Chaque précondition a son opérateur `PreC_`, et `Pre_` en est la conjonction.
- **`scripts/tlaval.py`** lit les valeurs TLA+, dans la sortie de TLC comme dans celle de `infotla`. Une chaîne et une model value ne se confondent plus. Un tuple, un enregistrement et une fonction égaux en TLA+ donnent la même valeur.
- **`scripts/replay.py`** remplace `trace2soil.py`, que j'ai supprimé. `check-generated.sh` l'utilise ; ses trois rejeux comparent maintenant les états pas à pas.
- **`scripts/scenario.py`** et **`scripts/witness.py`**, et les pilotes `ops-replays.sh`, `ops-scenarios.sh`, `ops-witnesses.sh`, puis `ops-oracle.sh` qui lance tout l'oracle de comportement.

`TlaCommand.value` est devenue statique pour servir à `infotla`. Les 17 specs du corpus sont identiques octet pour octet avant et après l'ajout des bugs volontaires, quand aucun n'est activé.

### B4. De TLC vers USE : rejouer chaque comportement

`replay.py` écrit les commandes USE d'une trace de TLC, pas par pas. Chaque argument est écrit selon le type de son paramètre (`types.tsv`). Après chaque pas, `infotla` lit l'état de USE et `check` le juge. Un objet qu'un corps crée reçoit une variable SOIL au nom de son objet de pool, et l'état de USE est lu avec cette correspondance. Au dernier pas, faute de variable, la correspondance est la bijection, classe par classe, qui rend les états égaux. Une trace qui finit sur une postcondition fausse finit par `c`, car USE est alors en pause.

La comparaison vérifie à chaque pas : USE accepte le pas ; son état égale celui de TLC (objets vivants, classes, attributs et associations de l'état de la spec) ; le résultat d'un appel égale celui de TLC ; les invariants et multiplicités que TLC a vérifiés tiennent dans USE. Au dernier pas, le check que TLC trouve violé l'est aussi dans USE.

Les traces rejouées sont chaque contre-exemple, et pour chaque opération traduite une trace de couverture : le plus court comportement qui finit par un appel de l'opération dont les postconditions tiennent. TLC n'explore que les états où les postconditions ont tenu, car USE annule l'appel sinon.

| Traces | Rejouées | D'accord | États comparés, égaux | Non atteintes |
| --- | --- | --- | --- | --- |
| civstat (`check-generated.sh`), 3 contre-exemples | 3 | 3 | 13, 13 | |
| corpus, 4 contre-exemples et 13 couvertures | 17 | 17 | 63, 63 | 6 |
| sondes B5, 2 contre-exemples et 15 couvertures | 17 | 17 | 66, 66 | 2 |

Les opérations non atteintes le sont par construction ou par les domaines par défaut. `C::i` (t084), `C::inc2` (t121), `failEnter` et `failExit` (sonde) ont une précondition ou une postcondition toujours fausse. `marry` de t094 et t095 demande un genre que rien n'affecte. `A::op1` (t22b) et `Company::start` (projectworld) ne sont pas atteintes avec les domaines par défaut.

Contrôle négatif : la trace du `marry()` cassé, rejouée sur le modèle correct, donne un état différent au pas 3 et une postcondition qui tient dans USE. La comparaison le signale.

### B4. De USE vers TLC : chaque scénario est un comportement de la spec

`scenario.py` lance le scénario dans USE tel qu'il est écrit, avec un `infotla` après chaque commande, et avant chaque appel pour le receveur et les arguments. Les réglages viennent de ce run : un pool par classe aussi grand que le plus d'objets vivants à la fois, un pas `new` par classe créée, un pas `call` par opération appelée, un `contract` par opération entrée. TLC suit ensuite les états de USE avec les actions de la spec :

- `new` : `!create`, `!x := new C` ou `!new C` hors opération, par `Step_new_C` ;
- `call` : `!x.op(...)` ou `!v := x.op(...)` hors opération, pour une opération à corps, par `Step_call_C_op`. `C` est la classe qui déclare l'opération, donc le pas passe par le choix de la redéfinition selon la classe de l'objet. Le résultat de la spec doit égaler la valeur que USE donne à `v`. Un appel que USE refuse (une précondition, une instruction qui échoue) doit être un appel dont `Call_` vaut `Fail`, et l'état doit rester le même. Le retour en arrière après `c` est un pas vers l'état d'avant ;
- `enter` : `!openter` ne change rien. La garde `Pre_` doit être vraie si USE entre, fausse s'il refuse ;
- `exit` : `!opexit [r]` vérifie le contrat, la garde dans l'état de l'`openter` et les postconditions entre cet état et celui de l'`opexit` ;
- `env` : toute autre commande (`!set`, `!insert`, `!delete`, `!destroy`, les commandes entre `openter` et `opexit`). L'état suivant est celui de USE, comme un état initial pris à USE.

À chaque état, TLC écrit la valeur de chaque check de la spec (invariants, multiplicités, postconditions), et avant chaque appel celle de chaque précondition. La comparaison les confronte aux verdicts de USE : `check`, les lignes `precondition ... is true|false` et `postcondition ...`, et les blocs `[Error]` d'un appel SOIL. L'objet qu'un `new` prend dans un corps est prévu : TLC range les model values dans l'ordre où le cfg les déclare (mesuré sur un module de test), donc `CHOOSE` prend l'objet libre de plus petit indice.

Compte des scénarios, mesuré : 18 scripts d'exemples appellent une opération (16 par `openter`, 2 par un appel SOIL), comme le disait le plan. 9 tests shell attendent un verdict de pré- ou postcondition. Le plan en comptait 10 : t093 ne contient que la ligne de résumé du modèle « 0 pre-/postconditions ».

| Scénarios | Nombre | Permis, verdicts d'accord | Pas new, call, contrat, env, non vérifiés | Verdicts comparés, d'accord |
| --- | --- | --- | --- | --- |
| scripts d'exemples | 18 | 18 | 55, 3, 61, 193, 31 | 2000, 2000 |
| tests shell | 9 | 8, et 1 erreur de TLC | 28, 8, 36, 89, 7 | 1046, 1046 |
| sondes B5 | 3 | 3 | 12, 22, 1, 8, 4 | 76, 76 |

L'erreur de TLC est t095. Après `cyd.marry(ada)`, `ada` a deux maris, et la navigation de la postcondition rencontre cette violation de multiplicité. USE juge la postcondition fausse, le traducteur s'arrête sur un `Assert`. C'est le risque noté en B3. Les 23 verdicts d'avant cet état sont d'accord.

Les pas non vérifiés sont refusés par nom : `Real` (Employee, EmployeeExtended, Polygon), `oclIsNew` dans une postcondition (Graph, t107), `Tuple` (t042), une requête à pré/post appelée dans une postcondition (NestedOperationCalls), un `for` sur un ensemble (projects2), un appel d'opération SOIL dans un corps (t084).

Les scripts ne tournent pas tous tels quels. `abcd_*.cmd` lisent `c:/use/civstat/...` : le fichier est cherché à côté du script. tollcoll01 lit `Point_southConnect.cmd`, le fichier s'appelle `Point_southconnect.cmd`. `EmployeeExtendedRaiseSalary.cmd` tourne après les fichiers numérotés de son dossier. `dotests.cmd` ouvre un `.testsuite` et n'est pas un scénario.

Contrôle négatif, à chaque run : `prepare.soil` avec `Ada` nommée `Adb` dans l'état d'après sa naissance. TLC s'arrête au pas 3.

### B4. Les états atteints servent de témoins au ModelValidator

`witness.py` fait explorer chaque spec par TLC, seulement à travers les états où chaque check tient. Il garde un état par jeu de bornes de témoin distinct (`ci/oracle/witness.sh` ne dépend que des tailles et des littéraux), au plus 5 par spec, les plus grands d'abord. USE construit l'état et le juge. S'il l'accepte, le validateur doit trouver une solution avec les bornes de `witness.sh`.

14 specs, 49 états : 36 solutions trouvées et acceptées par le `check` de USE, aucun UNSAT. 10 états sont sautés parce qu'ils n'ont aucun objet (l'état initial). 3 sont des erreurs du validateur sur civstat, qui refuse l'invariant `nameCapitalThenSmallLetters` (« OCL operation substring is not supported »). Les témoins sont petits : 4 objets au plus.

### Budget

L'oracle complet (`ops-oracle.sh`) prend 142 s sur 4 cœurs, un run : civstat 18 s, corpus 18 s, rejeux 12 s, sondes 22 s, scénarios 32 s, témoins 40 s (`results/ops-oracle.tsv`). Le budget était de l'ordre de 11 minutes. Un tour des 16 bugs volontaires a pris 42 minutes.

### B5. Bugs volontaires dans la traduction des actions

`Ops.java` contient 16 bugs volontaires, activés par la variable `TLA_OPS_MUTANT`. Chacun compte les endroits où il change la traduction, pour distinguer un bug qu'aucun modèle n'exerce d'un bug que les checks manquent. Tous sont exercés, de 2 à 234 fois par tour. `ops-mutants.sh` relance tout l'oracle par bug, et `mutant-row.py` dit quels checks le détectent : une ligne passe d'un résultat accepté à un autre, ou d'un échec à un autre. Une ligne qui disparaît (une opération qui n'est plus traduite) ne compte pas, car aucun oracle ne la juge.

Trois mesures, dans cet ordre : les checks existants seuls, aucune sonde écrite (`results/ops-mutants-before-probes.tsv`) ; avec les sondes dérivées de `StatementEffectTest` (`ops-mutants-derived-probes.tsv`) ; avec les sondes de fermeture et les corrections de l'oracle (`ops-mutants.tsv`).

| Bug volontaire | Appliqué (avant, final) | Avant les sondes | Sondes dérivées | Final | Détecté à la fin par |
| --- | --- | --- | --- | --- | --- |
| `frame-att` : une affectation d'attribut perd la valeur des autres objets (cadre oublié) | 91, 109 | oui | oui | oui | civstat, rejeux, scénarios |
| `frame-link` : une insertion de lien perd les autres liens (cadre oublié) | 34, 43 | oui | oui | oui | scénarios |
| `frame-objs` : `new` oublie les objets existants (cadre oublié) | 121, 136 | oui | oui | oui | civstat, rejeux, scénarios, sondes |
| `guard` : les préconditions sont ignorées (garde ignorée) | 91, 94 | oui | oui | oui | civstat, rejeux, scénarios, sondes |
| `atpre` : `@pre` lit l'état d'après l'appel | 231, 234 | oui | oui | oui | civstat, rejeux, scénarios |
| `atpre-new` : dans `new C call op`, `@pre` lit l'état d'avant `new` | 3, 3 | oui | oui | oui | civstat |
| `init` : `new` n'applique pas les `init` | 3, 3 | oui | oui | oui | rejeux, scénarios |
| `dispatch` : un appel exécute l'opération de la classe du pas, pas la redéfinition | 2, 4 | non | non | oui | scénarios |
| `result` : un résultat jamais affecté n'est pas un échec | 14, 29 | oui | oui | oui | rejeux |
| `nofail` : une instruction qui échoue est sautée, le corps continue | 100, 124 | non | non | oui | scénarios, sondes |
| `stale-read` : les instructions d'un corps lisent l'état d'entrée | 155, 197 | non | non | oui | scénarios, sondes |
| `ifundef` : un `if` à condition indéfinie prend la branche `then` | 38, 44 | non | non | oui | scénarios, sondes |
| `undef-target` : affecter un attribut d'un objet indéfini n'est pas un échec | 91, 109 | non | non | oui | scénarios, sondes |
| `dup-link` : insérer un lien existant, ou retirer un lien absent, n'est pas un échec | 64, 79 | non | oui | oui | scénarios, sondes |
| `new-fresh` : `new` dans un corps peut prendre un objet vivant | 9, 15 | oui | oui | oui | rejeux, scénarios, sondes |
| `mult` : une borne supérieure de multiplicité trop haute de 1 | 75, 87 | non | non | oui | scénarios |

- **Avant toute sonde : 9 sur 16.** Les checks existants détectent les trois cadres oubliés, la garde ignorée, les deux `@pre` lus dans le mauvais état, les `init` oubliés, le résultat jamais affecté et le `new` qui reprend un objet vivant. Les témoins du ModelValidator ne détectent aucun bug, à aucune mesure.
- **Avec les sondes dérivées : 10 sur 16.** La seule nouvelle détection est `dup-link`, par la trace de couverture de `unlink`.
- **Avec les sondes de fermeture et les corrections de l'oracle : 16 sur 16.**

Les trous et leur fermeture :

- `dispatch` était un trou de l'oracle. Un pas de scénario nommait la classe de l'objet, donc ne passait jamais par le choix de la redéfinition. Il nomme maintenant la classe qui déclare l'opération. Le test shell t105, qui appelle `f()` par une variable de la superclasse, sert de sonde (`action-probes/scenarios.txt`). t105 n'a pas de verdict de pré/post : il est hors des tests de contrat.
- `ifundef` était aussi un trou de l'oracle. La sonde dérivée `condUndef` (cas « condition = oclUndefined(Boolean) ») met la branche dans le résultat, et le résultat n'était comparé qu'à travers les postconditions. Les rejeux et les scénarios comparent maintenant le résultat de chaque appel. Aucune sonde n'a été ajoutée pour lui.
- `stale-read`, `undef-target`, `nofail` et `mult` : aucun cas de `StatementEffectTest` ne lit dans un corps ce qu'il vient d'écrire, n'affecte un objet indéfini, ne fait suivre une instruction qui échoue, ni ne viole une multiplicité. `action-probes/holes.use` et `holes.cmd` varient les cas les plus proches. Ils ont été écrits après le trou, et le disent en commentaire : ils prouvent que la sonde couvre le bug, rien de plus.
- `guard` : pendant B4, un `openter` refusé ne vérifiait que les préconditions une à une, pas la garde `Pre_`. Corrigé avant la première mesure, qui compte donc la garde.

Les sondes dérivées (`action-probes/statements.use`, `statements.cmd`) suivent les cas de `StatementEffectTest` dans l'ordre, sur le modèle de `TestSystem` sans sa classe d'association : chaque instruction du cas devient le corps d'une opération, et le script l'appelle. Le test a 11 méthodes et 32 cas hors annuler et refaire (compté sur les en-têtes de cas) ; le plan en comptait 23, chiffre que je ne retrouve pas. 15 cas ont une sonde de comportement. Les objets de lien (6 cas) sont hors du modèle de la sonde, car `tlaops` refuse les classes d'association. Le `new` nommé (2 cas), `destroy` (4), le `for` du premier cas d'itération et l'appel d'opération dans un corps (1) sont refusés par nom, et le scénario de la sonde montre chaque refus. Les trois cas restants portent sur la portée des variables du shell (« vanish », « shadows ») : dans un corps, USE refuse une variable non déclarée. Les tests shell de contrat sont déjà les 9 scénarios de B4.

### Bug de USE trouvé en route

Reproduit avec USE 7.5.0 seul, non signalé. Dans le shell, `!opexit` sans la valeur de résultat qu'exige l'opération fait quand même sortir de l'opération : `info opstack` dit ensuite « no active operations », et `!opexit 'x'` dit « No current operation ». `StatementEffectTest.testOpEnterOpExit` attend le contraire : l'opération reste sur la pile. Dans les sources, `MSystem.exitNonQueryOperation` dépile dans un `finally`, même quand la vérification du résultat échoue. L'API annule l'instruction échouée, ce qui rend l'opération à la pile, mais le shell lance `openter` et `opexit` sans annulation (`Shell.cmdExec`, `undoOnFailure = false`). La sonde rentre donc à nouveau dans l'opération avant `!opexit 'asdf'`.

### Vérifications finales

- `translator/all.sh`, relancé à la fin (27 min sur 4 cœurs) : 0 désaccord, 357 invariants distincts dans 37 modèles, 11 bugs volontaires sur 11 détectés. Les TSV des invariants sont identiques à ceux du dépôt, temps exclus, sauf trois lignes de `mc.tsv`, colonne `S0_symmetry_result` : Tree, actionsemantics et t101. Avec le jar construit depuis le commit 4fb76f9, les specs de ces trois cas sont identiques octet pour octet à celles du run (mesuré). Ces trois lignes du dépôt sont donc périmées. Le commit 4fb76f9 a ajouté ensemble la colonne et la borne de 2 à 6 objets, et ces lignes viennent d'un run sans la borne (inféré : la classe de Tree a 9 objets, arrêt à 300 s ; t101 a 5 967 objets, `Permutations` échoue). Avec la borne : Tree n'a pas de spec de symétrie, actionsemantics donne la même violation que sans symétrie, t101 aucune violation, à deux runs. Les trois cellules de `mc.tsv` (résultat et temps de la symétrie) ont été remplacées par celles du run final ; le reste du fichier est inchangé.
- Les sondes de comportement étaient d'abord dans `translator/probes/ops/`. `run.sh` prend tout `.use` sous `probes/` pour une sonde d'invariants, et `models.tsv` avait deux lignes de plus. Elles sont maintenant dans `translator/action-probes/`. `run.sh` relancé seul donne un `models.tsv` identique (214 lignes).
- `ops-probe/check-generated.sh` avec `FULL=1` : les 9 runs donnent les mêmes verdicts, nombres d'états et profondeurs qu'avant. La colonne du rejeu donne maintenant les états comparés (`ops-probe/civstat-generated.tsv`).
- L'oracle de comportement, relancé après le déplacement des sondes : les 9 TSV `ops-*` sont identiques, temps exclus (163 s pour ce run).

### Risques

- Les pas d'environnement des scénarios ne sont pas vérifiés : leur état est celui de USE. Comme la spec ne traduit pas `!set` ni `!insert`, rien ne se perd, mais rien n'est prouvé sur ces commandes.
- L'objet qu'un `new` prend dans un corps est prévu par l'ordre des model values de TLC. Si cet ordre changeait, les scénarios échoueraient bruyamment, sans faux accord.
- Deux objets de la même classe créés par le même corps : la correspondance est la bijection qui rend les états égaux. Aucun modèle ne le fait (inférence, d'après les rejeux).
- Le résultat d'une opération sans corps (`!opexit r`) n'est comparé qu'à travers les postconditions.
- Les témoins sont petits (4 objets au plus) et ne détectent aucun bug de traduction : ils testent le validateur, peu la spec.
- Un bug qui ne change que le nombre d'états, sans violation, n'est vu par aucun oracle : `corpus_changed` le note sans le juger.
