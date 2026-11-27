# ModelValidator verification map

This directory is the maintained source for verifying the user-facing behavior of the ModelValidator plugin in
the USE shell. Read the index, then use the matching feature file as the recipe.

## Baseline preconditions

- `<work>` was made by `scripts/setup.sh` from the current sources and `scripts/doctor.sh` printed
  `doctor: worth driving`.
- Every journey uses the fixture model `fixtures/pets.use` and the properties files beside it, unless its file
  says otherwise. The fixture is small enough for SAT4J to answer in milliseconds.
- Every journey writes to its own `<evidence>/<name>` folder, new and empty.
- Never drive USE with a plugin jar that is not the one in `<work>/jars`.

## Driving conventions

- Start every journey from a fresh USE: one command file, one `drive.sh`, one `judge.sh`.
- Command files end with `exit`; a file without it leaves USE waiting on a closed stdin.
- Name the properties file in `mv -validate`. The default bounds are small, and a model can be UNSAT
  under them while it has a solution under wider ones (see `validate.md`).
- Treat the transcript as the only evidence. Do not infer a verdict from the properties file or the exit status.

## Proof and skip reporting

- A journey is verified only when `judge.sh` printed `PASS` with the options its feature file gives.
- Report an unreachable path with the command tried and the unmet precondition. Do not report a skipped entry
  point as verified through a different path.
- Evidence is the `<evidence>/<name>` folder: `transcript.txt`, `status`, `commands.cmd`, `run/`, `judgement.txt`.

## Feature entry contract

Each feature file starts with an H1 and one paragraph, then these H2s in order: `Sub-features`,
`How to get to it (user POV)`, `Driving it with the USE shell`, `Gotchas`.

## Features

- [Validate a model](./validate.md) covers `mv -validate` with a properties file: SAT, UNSAT and the default file.
- [Check the solution](./check-solution.md) covers USE's own `info state` and `check` on the diagram the validator built.
- [Query a value](./query.md) covers `mv ?`, the comparison of USE's value with the translation's.
- [Validate a configuration](./validate-config.md) covers `mv -vc`, the rules a properties file breaks.

Not mapped yet: `mv -config`, `mv -invIndep`, `mv -scrolling`, `mv -scrollingAll`, the CT variants,
`mv -modelReset` and the two GUI actions.
