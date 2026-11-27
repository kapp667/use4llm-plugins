# Query a value

`mv ? <ocl expression>` prints the value USE computes for an OCL expression in the current diagram, then
compares it with the value of the plugin's translation in the solution the diagram came from. A difference is
an `ERROR` that names both values.

## Sub-features

- `query-value` prints the value and its type, as USE computes it.
- `query-compare` compares it with the solution after `mv ? enable` and a `mv -validate`.
- `query-switch` `mv ? enable`, `mv ? disable` and `mv ? enabled` control whether solutions are kept.

## How to get to it (user POV)

- Type `mv ? enable`, then `mv -validate <file>`, then `mv ? Person.allInstances()->size()` at the `use>` prompt.

## Driving it with the USE shell

Preconditions:

- `fixtures/pets.use` and `fixtures/pets-sat.properties`. The comparison only happens for a solution found after
  `mv ? enable` and only until the diagram changes.

- **Compare.** `scripts/drive.sh <work> fixtures/pets.use fixtures/query.cmd <evidence>/query`, then
  `scripts/judge.sh <evidence>/query --expect SAT --require 'INFO: 1 : Integer$' --require 'INFO: true : Boolean$'`.
  The transcript has the value lines `INFO: 1 : Integer` (twice) and `INFO: true : Boolean`, and no
  `Not compared` line. Judge prints `PASS`.
- **Nothing to compare is not a pass.** `fixtures/query-no-enable.cmd` omits `mv ? enable`. The plugin logs
  `Not compared with a solution: solutions are kept for comparison only after mv ? enable`; `judge.sh` exits 1.
  `scripts/prove.sh` runs this control.
- **A disagreement.** The plugin prints `ERROR: The translation evaluates <ocl> differently from USE. USE: ...; Kodkod: ....`
  `judge.sh` fails on any ERROR. Not covered by `prove.sh`, which has no translation defect.

## Gotchas

- A passing comparison prints nothing extra. The only positive signals are the value line and the absence of a
  `Not compared` line and of an ERROR, so `judge.sh` treats `Not compared` as a failure. Never accept a query
  run that has the value but a `Not compared` line.
- A comparison is skipped, with `Not compared`, when the expression has a string literal the solution has no atom
  for (`p.nick = 'Rex'` when the solver chose `'Tom'`: `the solution has no atom for String_Rex`), when it
  yields a Bag (`collect` gives `WARN: Collect operation ... unsupported type Bag` and the translation uses a
  Set), and when it calls `any()` (refused, see `ModelValidator/trunk/README` section 4).
- Changing the diagram (`!create`, `!insert`, another `mv -validate`) drops the kept solution; query again right
  after the `mv -validate` it belongs to.
