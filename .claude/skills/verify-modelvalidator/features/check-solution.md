# Check the solution

USE's own `check` command judges the object diagram that `mv -validate` built, with USE's evaluator and not the
plugin's translation. It is the independent second opinion on a SATISFIABLE verdict.

## Sub-features

- `check-structure` verifies multiplicities and structure of the diagram.
- `check-invariants` evaluates every invariant on the diagram and counts failures.
- `check-nonempty` confirms the diagram has objects, so the check is not vacuous.

## How to get to it (user POV)

- Type `info state`, then `check`, at the `use>` prompt after `mv -validate`.
- Choose `State` > `Check structure` or `Check invariants` in the GUI (not driven by this skill).

## Driving it with the USE shell

Preconditions:

- A `validate-sat` journey (see `validate.md`). The command file ends `info state`, `check`, `exit`.

- **Run.** `scripts/judge.sh <evidence>/validate-sat --expect SAT`. It reads `ci/oracle/state.sh`: the
  `info state` table total must be at least 1 and the transcript must end the check with
  `checked N invariants in Xs, 0 failures.`
- **Proof lines.** `info state` shows `Person : 1` and `Pet : 1`, `total : 2`; `check` shows
  `checked structure in ...`, `checking invariant (1) `Person::adult': OK.`, `checking invariant (2) `Pet::hasNick': OK.` and `checked 2 invariants in ..., 0 failures.`
- **A check that never ran fails.** Delete the `check` line from a command file or the `checked` lines from a
  transcript copy: `judge.sh` exits 1 with `USE's check printed no summary`. `scripts/prove.sh` runs this control.

## Gotchas

- After an UNSATISFIABLE verdict the diagram is empty and `check` still prints `0 failures`. That result is
  vacuous; `judge.sh --expect UNSAT` ignores it on purpose.
- `info state` must come before `check`; the judge reads the object count from it.
- A SAT verdict followed by a `check` with failures means the translation accepted a diagram USE rejects. Treat
  it as a plugin defect. `judge.sh` fails it. Not covered by `prove.sh`.
