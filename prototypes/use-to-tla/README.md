# USE to TLA+: throwaway prototype

**Do not merge. Do not copy this code into a plugin.** This folder records an experiment. A real plugin, if one follows, will be written from scratch next to `ModelValidator/` and `FilmstripTransformation/`.

## Question

Can a deterministic translator turn a USE model (classes, associations, OCL invariants, operations) into a TLA+ specification that TLC checks, with the same verdicts as USE?

## What the prototype shows

- `translator/` is a USE plugin, `tla <dir>`. It translates the loaded model and the current state, plus 20 mutations of that state, into TLA+. For every state, it compares the value of each invariant, globally and per object, as USE's evaluator and TLC give it.
  - 701 of 831 invariants translate, 48 of them in a hand-written probe. Every other invariant is refused with the name of the construct.
  - 357 distinct invariants in 37 models give the same value in USE and TLC, with true, false and undefined values all present (`translator/results/evidence.tsv`).
  - All 11 deliberate translation bugs are caught (`translator/results/mutants.tsv`).
- The same plugin's `tlaops <settings> <dir>` translates the operations with a SOIL body into a TLA+ spec of the model's behaviour, under a settings file that states the environment (object pools, argument domains, the steps the environment may take, when the invariants must hold). For `civstat` it reproduces the hand translation exactly: the two specs refine each other, with the same state counts (`ops-probe/check-generated.sh`).
- `ops-probe/` translates by hand the operations of USE's `civstat` example, following fixed rules. TLC finds that `birth()` lets two people share a name, and USE confirms it on the replayed path. Symmetry over objects cuts 22.7 million states to 215,729.

The full report is `REPORT.md` and the next steps are in `PLAN.md`. Both are in French.

## Rerun

Set four variables:

- `USE_HOME` names an unpacked USE 7.5.0 release.
- `USE_SRC` names a checkout of USE's sources at `v7.5.0`. Its test models are the second corpus.
- `MV_JAR` names the `ModelValidatorPlugin.jar` that `ci/build-and-test.sh --build-only` writes.
- `TLA2TOOLS` names `tla2tools.jar` from the TLA+ tools release.

Then run `translator/all.sh`. The corpus run takes about 5 minutes on 4 cores, and the mutant runs take about 25 more. `CORPUS=<file> translator/run.sh <out> <results>` reruns a subset.

For the generated operations spec, run `ops-probe/check-generated.sh <out>` (`FULL=1` adds 4 persons and 5 with symmetry), and `translator/scripts/ops-corpus.sh <out> <results>` for the corpus models with a SOIL body. The settings format is in the comment of `translator/plugin/src/org/tzi/use/plugin/tla/Settings.java`.

For the hand operations probe, copy `examples/soil/civstat/civstat.use` from the USE release into `ops-probe/`, then run `java -cp "$TLA2TOOLS" tlc2.TLC -config sym-5.cfg CivstatSym` there.

## Known limits

- The oracle's witness corpus has few invariants. Most evidence comes from rejected witness states, ModelValidator solutions and a hand-written probe.
- `tla` translates invariants only. `tlaops` translates SOIL bodies, preconditions, postconditions and binary multiplicities. It refuses by name `while`, `for` over an unordered collection, calls of SOIL operations inside a body, `destroy`, link objects, operations without a body and classes with a state machine (`translator/results/ops-refusals.tsv`).
- `TlaCommand` works around kapp667/use4llm#1 by reflection.

USE bugs found on the way: kapp667/use4llm#1 and kapp667/use4llm#2.
