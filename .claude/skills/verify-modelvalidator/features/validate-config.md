# Validate a configuration

`mv -vc <file.properties> [config names]` checks the configurations of a properties file against the loaded
model and names each rule a configuration breaks, with the fixes that would repair it. It runs no search and
prints no SAT or UNSAT verdict.

## Sub-features

- `vc-valid` reports `Config <name> is valid.`
- `vc-invalid` reports `Config <name> is invalid.` with the rule and the bound values that break it.
- `vc-select` checks only the named configurations; with no name, every configuration of the file.

## How to get to it (user POV)

- Type `mv -vc pets-sat.properties` at the `use>` prompt after loading a model.
- Choose `Model Validator` > `Configuration` in the GUI (not driven by this skill).

## Driving it with the USE shell

Preconditions:

- `fixtures/pets.use`, `fixtures/pets-sat.properties` (valid) and `fixtures/pets-badbounds.properties`
  (`Person_min = 3`, `Person_max = 1`).

- **Run.** `scripts/drive.sh <work> fixtures/pets.use fixtures/vc.cmd <evidence>/vc`, then
  `scripts/judge.sh <evidence>/vc --expect NONE --require 'Config sat is valid\.' --require 'Config badbounds is invalid\.' --require 'Rule CLASS_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX is violated\.'`.
- **Proof lines.** `Config sat is valid.`; `Config badbounds is invalid.` then
  `Rule CLASS_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX is violated.` and `Person_min (3) should be less than or equal Person_max (1).`
- **Verdict absent.** `--expect NONE` makes the judge fail if a SAT or UNSAT line shows up; `mv -vc` must not search.

## Gotchas

- The result is one INFO block, not one line per configuration; `--require` each `Config <name>` line the
  journey is about, or an unexpected `valid` for a bad file passes unnoticed.
- The section name in brackets (`[sat]`) is the configuration name the output uses.
- `prove.sh` covers only the valid file and the min-above-max rule, not the other rules or the `[config names]` selection.
