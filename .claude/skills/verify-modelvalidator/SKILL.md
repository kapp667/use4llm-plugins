---
name: verify-modelvalidator
description: Drive the real ModelValidator plugin (mv -validate, mv ?, mv -vc and USE's own check) inside the USE shell and capture judged evidence. Use when a change touches ModelValidator/trunk and a unit test is not enough proof, or when asked to confirm a verdict, a bound, a translation or a configuration rule in the real tool.
---

# Verify the ModelValidator plugin in USE

The surface is the USE shell, a command line. The plugin adds `mv` commands to it. A run loads a model, feeds
USE a command file and reads what the shell printed. All paths below are relative to the repo root unless
they start with `<`; `<work>` and `<evidence>` are folders you choose outside the checkout.

The rule of this skill: a run counts only after `judge.sh` printed `PASS` for it. A transcript you read by
eye is not a verdict. `judge.sh` fails on a wrong or missing verdict, a missing or failing `check`, any ERROR,
any WARN it does not know, a stack trace, a timeout, and an `mv ?` that did not compare.

## Launch

USE and a build are needed once per checkout state; there is no server to keep alive.

1. Get USE 7.5.0 into a new folder (verifies the SHA-256 that `.github/workflows/ci.yml` pins; needs
   access to github.com). It prints the install dir on its last line.
   `.claude/skills/verify-modelvalidator/scripts/fetch-use.sh <new dir>`
   A USE you already have works if it is 7.5.0: pass its folder instead (the one above `lib/use-gui.jar`).
2. Build the jars and make a private USE copy that loads them (about a minute; needs Java 21 and a JDK on PATH).
   `.claude/skills/verify-modelvalidator/scripts/setup.sh <USE install dir> <work>`
   It runs `ci/build-and-test.sh --build-only`, then copies USE's `lib`, `etc` and `oclextensions` to
   `<work>/use` and swaps the bundled ModelValidator and Filmstrip jars for the built ones, as
   `ci/oracle/run.sh` does. Rerun it after every source change: the jars are not rebuilt by anything else.
3. Ready means `doctor.sh` prints `doctor: worth driving`.

## Doctor

`.claude/skills/verify-modelvalidator/scripts/doctor.sh <work>` is read-only and takes seconds. Run it before
the first drive and whenever a result looks odd. It checks Java 21 or later, one copy of each plugin jar in
`<work>/use/lib/plugins` identical to `<work>/jars`, no source of `ModelValidator/trunk` newer than the jar
(a stale build is the most common false result), and that USE lists `mv -validate`, `mv ?`, `mv -vc`,
`mv -config`, `mv -invIndep` and `mv -scrolling` once each. Any FAIL line: rerun `setup.sh`.

## Drive

One command file per journey. Write USE shell commands into it, one per line, ending with `exit`, then:

`.claude/skills/verify-modelvalidator/scripts/drive.sh <work> <model.use> <command file> <evidence>/<name>`

`drive.sh` runs `java -jar <work>/use/lib/use-gui.jar -nogui -nr <model> <command file>`, the same non-interactive
start as `ci/oracle/run.sh`. It copies the model's folder and the command file into `<evidence>/<name>/run`, so
the properties files beside the model are found and the validator's own writes stay out of your checkout.
A properties file named in a command file resolves against the command file's folder. A new `<evidence>/<name>`
is required; a drive never overwrites evidence.

Then judge it:

`.claude/skills/verify-modelvalidator/scripts/judge.sh <evidence>/<name> --expect SAT|UNSAT|NONE [--require REGEX] [--forbid REGEX] [--allow REGEX]`

- `--expect SAT`: one `INFO: SATISFIABLE` (or `TRIVIALLY_SATISFIABLE`) line, then `info state` with at least one
  object and `check` ending `0 failures`. Put `info state` and `check` after `mv -validate` in the command file.
- `--expect UNSAT`: one `UNSATISFIABLE` line. `check` is not consulted: after UNSAT the diagram is empty and
  `check` passes on it, which proves nothing.
- `--expect NONE`: no verdict line (for `mv -vc`, `mv ?` alone, `mv -config`). Add `--require` lines for what
  the journey must print.

The feature files in `.claude/skills/verify-modelvalidator/features/` hold the command file and the judge
options for each feature. Read the index first: `.claude/skills/verify-modelvalidator/features/README.md`.
Bounds, not defaults, make a journey repeatable: write a properties file like
`.claude/skills/verify-modelvalidator/fixtures/pets-sat.properties` with explicit value sets.

## Evidence

Each run leaves, in `<evidence>/<name>/`: `transcript.txt` (everything USE printed, the commands echoed),
`status` (USE's exit status), `commands.cmd`, `run/` (the model, its properties, the command file) and
`judgement.txt` (the judge's report). Proof standards:

- Drive the real commands through the real shell. Do not call plugin classes from a test harness.
- Keep the action and the state: the transcript has the command echo, the verdict, `info state` and `check`.
- A SAT journey needs USE's `check` as the second opinion; the plugin's verdict alone is not proof.
- A journey that was never able to fail proves nothing. `prove.sh` runs negative controls for that reason.
- Report `PASS`/`FAIL` from `judgement.txt` with the evidence path. A `WARN` the judge counted as known
  (`known-warnings.txt`) is environmental; any other WARN or ERROR is a defect signal until explained.

To prove the harness itself (after editing a script, or on a new machine):
`.claude/skills/verify-modelvalidator/scripts/prove.sh <work> <evidence root>`
It runs four journeys on the small Pets model in `fixtures/` (they must PASS) and three controls (they must
FAIL, each for a named reason: a wrong verdict, a `check` that never ran, an `mv ?` with nothing to compare).

## Cleanup

Nothing runs in the background, so no process needs killing. Remove only what you made:
`rm -rf <work>` (the private USE and the jars, rebuilt by `setup.sh`) and the folder `fetch-use.sh` filled.
Keep `<evidence>`; it is the proof. Never edit or delete the USE install dir you were given.
Properties files the validator writes land in `<evidence>/<name>/run`, never beside the original model.

## Helpers

All in `.claude/skills/verify-modelvalidator/scripts/`, executable, usage in each file's header:
`fetch-use.sh`, `setup.sh`, `doctor.sh`, `drive.sh`, `judge.sh`, `prove.sh`. `judge.sh` reuses
`ci/oracle/parse.sh` and `ci/oracle/state.sh`, the oracle's own parsing of verdicts and of USE's check.
For a whole corpus of models use `ci/oracle/run.sh` instead; this skill is for one journey at a time.

## Isolation

Two drives can run side by side: each has its own `<evidence>/<name>/run`. Share one `<work>`; it is only
read while driving. `setup.sh` rewrites `<work>`, so do not run it while a drive uses the same `<work>`.

## Not covered yet

`mv -config`, `mv -invIndep`, `mv -scrolling` and the GUI actions (Kodkod Validation and Configuration) have
no journey in `features/`. Add one per feature file when a change touches them, and add it to the index.
