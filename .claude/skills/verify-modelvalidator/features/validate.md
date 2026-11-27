# Validate a model

`mv -validate [file.properties [section]]` translates the loaded model to Kodkod, searches for an object
diagram within the bounds of a properties file, and prints a verdict. On a solution it builds the diagram in
USE's current state.

## Sub-features

- `validate-sat` finds a diagram within explicit bounds and loads it into the current state.
- `validate-unsat` reports `UNSATISFIABLE` when no diagram fits the bounds.
- `validate-default` with no argument writes `<model>.properties` beside the model and solves with it.
- `validate-section` picks a named configuration section of a properties file.
- `validate-refuse` is the expected refusal of an invalid configuration before any search.

## How to get to it (user POV)

- Type `mv -validate pets-sat.properties` at the `use>` prompt after loading a model.
- Type `mv -validate` for the generated default bounds.
- Type `mv -validate pets-sat.properties sat` to name the configuration.
- Choose `Model Validator` > `Validation` in the GUI (not driven by this skill).

## Driving it with the USE shell

Preconditions:

- `doctor.sh` passed. Model `fixtures/pets.use`; properties `fixtures/pets-sat.properties` and
  `fixtures/pets-unsat.properties` (the same bounds, but `Person_age = Set{10}` against the invariant `age >= 18`).

- **SAT.** Run `scripts/drive.sh <work> fixtures/pets.use fixtures/validate-sat.cmd <evidence>/validate-sat`, then
  `scripts/judge.sh <evidence>/validate-sat --expect SAT`. The transcript has `INFO: SATISFIABLE`,
  `info state` with `Person : 1` and `Pet : 1`, and `checked 2 invariants ..., 0 failures.` Judge prints `PASS`.
- **UNSAT.** Run `scripts/drive.sh <work> fixtures/pets.use fixtures/validate-unsat.cmd <evidence>/validate-unsat`,
  then `scripts/judge.sh <evidence>/validate-unsat --expect UNSAT`. The transcript has
  `INFO: TRIVIALLY_UNSATISFIABLE` and an `INFO: Unsatisfiable proof:` line.
- **Wrong verdict is loud.** `scripts/judge.sh <evidence>/validate-unsat --expect SAT` must exit 1 with
  `FAIL: expected verdict SAT, got UNSAT`. `scripts/prove.sh` runs this control.
- **Default bounds.** Write a command file with `mv -validate`, `info state`, `check`, `exit`. The file
  `pets.properties` appears in `<evidence>/<name>/run`. Judge with `--expect SAT` or `--expect UNSAT` as the
  model dictates; on Pets the default gives UNSAT (see Gotchas), so `--expect UNSAT` is the right answer.
- **Refusal.** Run `mv -validate pets-badbounds.properties`. A configuration that breaks a stopping rule is
  refused before any search, with two `ERROR` lines and no verdict (measured with `mv -validate pets-badbounds.properties` on the Pets fixture):
  `ERROR: The configuration breaks these rules. mv -vc also reports rules that do not stop the search.` followed
  by the rule, its values and the fixes, then
  `ERROR: Error while reading .properties file. The model validator does not search with this configuration.`
  `mv -vc` names the broken rule too (see `validate-config.md`).
- **Other refusals.** These also end with an `ERROR` and no verdict. An object diagram extraction that fails
  (`AbstractPlugin.enrichModel`). Elements the translation cannot transform (`KodkodModelValidator`). An
  `<assoc>_max` below the number of listed links (`PropertyConfigurationVisitor`).

## Gotchas

- `TRIVIALLY_SATISFIABLE` and `TRIVIALLY_UNSATISFIABLE` are verdicts too; `judge.sh` maps them to SAT and UNSAT.
- A section name is optional. Without one the plugin logs `INFO: No configuration name given. Using first
  configuration of the file.` and uses the first section.
- On Pets the default file has `Integer_max = 10`, and the `adult` invariant needs an age of 18 or more, so the
  search is UNSAT. That verdict is right for those bounds. Read the bounds the run wrote before suspecting the
  plugin.
- A String attribute with no String settings gives an ERROR, not a verdict. The default file always has
  `String_max`, so this only happens with a hand-written file.
- Any WARN that `known-warnings.txt` does not list fails the judge.
- Exit status 0 only means USE reached `exit`. The verdict line is the signal.
- Do not run the same `mv -validate` twice in one command file; `judge.sh` expects one verdict line and fails
  otherwise. Pass `--verdicts 2` when the journey needs two.
