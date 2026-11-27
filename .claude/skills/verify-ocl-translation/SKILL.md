---
name: verify-ocl-translation
description: Check that the ModelValidator's translation of OCL to Kodkod gives the value USE 7.5's evaluator gives, for every expression of a small grammar in every state of a small model. Use after any change to ModelValidator/trunk/src/org/tzi/use/kodkod/transform/ocl, src/org/tzi/kodkod/ocl, src/org/tzi/kodkod/model, src/org/tzi/use/kodkod/solution or KodkodQueryCmd, or when an OCL construct may be translated wrongly.
---

# Verify the OCL translation against USE

**Property.** For every expression of the grammar and every state within the bounds, the value
of the expression's Kodkod translation in that state equals USE's value, or the translation
refuses it (an exception, which `mv -validate` reports as an ERROR).

**Harm.** When the property breaks, `mv -validate` decides an invariant differently from USE
without saying so: it reports UNSATISFIABLE for a model that has a valid state, or finds
states USE rejects. A WARN before the verdict does not count as saying so.

## How it works

Generated inputs run against the real code; there is no Java verifier at this cost.

1. `ci/translation-oracle/expressions.py <depth>` prints every expression of a typed grammar up
   to the depth (default 2, about 12000 expressions). Over `model.use`: class `A` with
   `n : Integer`, derived `d = self.dst->size()`, subclass `B < A`, association `R`
   (`src [0..1]`, `dst [*]`). The grammar has Boolean, Integer, object, Set, Bag, Sequence and
   OrderedSet operations, iterators (`forAll`, `exists`, `select`, `reject`, `collect`,
   `closure`), `null`, `oclIsUndefined`, `oclIsKindOf`, `oclAsType`, `flatten` and
   allInstances. A Bag, Sequence or OrderedSet at the top is also asked through `->asSet()`
   and `->size()`, because `mv ?` cannot read it.
2. `TranslationOracle.java` (package `org.tzi.use.kodkod.transform.ocl`, so it can use the test
   class `PluginShell`) solves `model.properties` with `KodkodScrollingValidateCmd` and calls
   `next` until no state is left. `ModelConfigurator.forbid` forbids each state found up to
   renaming of objects, so this visits every state up to isomorphism. In each state it asks
   `mv ?` (`KodkodQueryCmd`) for each expression. `mv ?` evaluates the expression with USE,
   translates it with `DefaultExpressionVisitor`, the visitor that also translates invariants,
   evaluates the translation in the solution, and logs `ERROR The translation evaluates ...`
   when the two disagree.
3. `summarize.py` sorts each disagreement into a kind: `verdict` (a Boolean that is true on one
   side only), `undefined` (USE gives null, the translation false), `no-value` (an Integer
   outside `Integer_min..Integer_max` has no atom) and `value` (anything else). The expressions
   that disagree are compared with `expected.tsv`. It also lists the expressions that `mv ?`
   could not compare with a solution in some state (the translation refuses or throws, or the
   solution has no atom), with the number of states and of expressions, in
   `not-compared.tsv`, which is compared with `expected-not-compared.tsv`.

Bounds: `Integer` 0..2, exactly one `A` that is not a `B`, zero or one `B`, `n` defined or not,
any links of `R`: 152 states.

## Run it

You need USE 7.5.0 and JUnit 4.13.2 with Hamcrest 1.3, the same as `ci/build-and-test.sh`:

```
curl -fLO https://github.com/useocl/use/releases/download/v7.5.0/use-7.5.0-use-bin.zip
sha256sum use-7.5.0-use-bin.zip   # must equal USE_ZIP_SHA256 in .github/workflows/ci.yml
unzip -q use-7.5.0-use-bin.zip -d use
# junit-4.13.2.jar and hamcrest-core-1.3.jar from Maven Central into junit/, sums in ci.yml too
ci/translation-oracle/run.sh use junit oracle-out
```

It builds the plugin with `ci/build-and-test.sh --build-only`, compiles the test sources and the
driver, and runs one JVM per CPU. It takes about 2 minutes on 4 CPUs. It prints `OK: the
disagreements and the not-compared expressions are the ones expected` and exits 0, or prints
`FAILED` and a diff and exits 1. It fails on a disagreement that is new or gone, on an
expression that agreed and now is not compared (or the reverse), and on a change in the
number of states (152) or expressions. `ORACLE_DEPTH=1` gives a 20-second smoke run. Do not commit `expected.tsv` from a depth
other than 2.

CI runs it on every pull request and push to `develop`, in the `build-and-test` job of
`.github/workflows/ci.yml`. On a failure the job uploads `findings.txt`, both diffs and
`answers.tsv` as the `translation-oracle` artifact.

## Read a failure

Open `<out>/findings.txt`. Each entry gives the expression, the state as USE's value of
`A.allInstances->collect(a | Tuple{o, b = is a B, n, dst})`, USE's value, the translated value,
and `warn` when the translation logged a WARN. `<out>/answers.tsv` has every answer that was not
an agreement, one row per state.

- A `+` line in `expected.diff` is a new disagreement. Before you call it a bug, confirm it end
  to end. Write a model of `model.use` plus `context A inv probe: <a Boolean form of the
  expression>`, a `.cmd` file that builds the state with `!create` and `!set`, then `check`,
  `reset`, and `mv -validate` with bounds that hold only that state, and run
  `java -jar <use>/lib/use-gui.jar -nogui -nr m.use witness.cmd` with the plugin jar in
  `lib/plugins`. A bug is USE's `check` saying OK and the validator saying UNSATISFIABLE or
  TRIVIALLY_UNSATISFIABLE with no ERROR, or the reverse.
- A `-` line is a disagreement that is gone. If the change meant to fix it, run
  `ci/translation-oracle/run.sh use junit <out> --update` and commit the new `expected.tsv`.
- A line in `expected-not-compared.diff` is an expression that is now not compared (`+`),
  which hides it from the check, or one that is compared again (`-`), or a new number of
  states or expressions. Find out why before you run `--update`: a `+` row is a translation
  that now refuses, throws or has no atom for an expression it used to answer.
- `unexpected answer of mv ?` means an expression is not valid OCL or `mv ?` printed something
  new. That is an error in `expressions.py` or a change in `mv ?`, not in the translation.
- `the states were cut off` means the bounds grew: raise `ORACLE_MAX_STATES`.
- `the shards found different states` means the state order is not deterministic: run with
  `ORACLE_SHARDS=1`.

A `no-value` row is not by itself a wrong verdict: the validator solves `A.allInstances->size()
+ 1 = 3` and `collect(x | x.n + 1)->exists(y | y > 2)` correctly with `Integer_max = 2`, though
`mv ?` reads no value. Keep those rows in `expected.tsv`, and confirm a new one end to end.

## Known findings at the sync commit

None in `value` or `verdict`. The 155 `no-value` rows are Integers above `Integer_max`, inside
`+` and `collect`.

Fixed before this sync commit: `closure` dropped an undefined value that the body returns
(issue #121). `A.allInstances->closure(x | Set{x.src})->size() = 2` in a state with one
`a1 : A` and no `src` was TRIVIALLY_UNSATISFIABLE with no ERROR, while USE's `check` says OK.

## Not covered

- States with no `A` that is not a `B`: `A_min = 1`, because with `A_min = 0` the scrolling
  validator returns the empty state again at each `next` and never ends.
- More than two objects, Strings, Reals, enums, Set-valued attributes, association classes,
  qualifiers, operations, `let`, nested iterators, iterators over a non-closed source, and
  expressions deeper than 2 (3 for the asSet and size around a Bag, Sequence or OrderedSet).
- Bag, Sequence and OrderedSet values themselves: `mv ?` does not compare them.
- Invariants with `self`: every expression is closed. Outside an iterator body the only object
  is `null`, so `includes`, `including` and `excluding` of a real object are reached only inside
  `collect`, `select` and the other iterators, and Booleans built on them only at depth 1.
- The search itself: the check compares values in given states. It does not check that
  `mv -validate` finds a state or proves there is none.

## Coverage record

Covered: `ModelValidator/trunk/src/org/tzi/use/kodkod/transform/ocl/*.java`,
`src/org/tzi/kodkod/ocl/**` (the operation groups), `src/org/tzi/kodkod/model/**` (classes,
attributes, derived attributes, associations, inheritance), `src/org/tzi/use/kodkod/solution/*`
(the diagram of a solution and `SolutionValueReader`), `src/org/tzi/use/kodkod/plugin/KodkodQueryCmd.java`,
`src/org/tzi/kodkod/KodkodQueryCache.java`, `src/org/tzi/use/kodkod/UseScrollingKodkodModelValidator.java`
and `ModelConfigurator.forbid`.

Sync commit: `93af50e`. At that commit the check exits 0 with 152 states and 11897
expressions. The covered code differs from `22bee41` only in `SetOperationGroup.closure`,
which now keeps the undefined value, and `expected.tsv` lost the 35 closure rows. Before you move it forward, run the
check at the new commit and confirm that this file still describes the covered code: the
grammar still reaches the changed operations, and the findings above are still true. To see
what changed since, run `git diff <sync commit> -- ModelValidator/trunk/src`.
