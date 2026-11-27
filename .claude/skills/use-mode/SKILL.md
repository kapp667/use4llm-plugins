---
name: use-mode
description: >-
  Lead multi-agent bug fixing in kapp667/use4llm and kapp667/use4llm-plugins on top of
  /poteto-mode, within a fixed budget: a clear spec first, then separate agents for the tests
  and the fix, each on the cheapest model that does its role well, with hidden journeys and one
  gate script as the judge. Use for /use-mode, or when you orchestrate fixes of USE or the
  ModelValidator plugin.
disable-model-invocation: true
---

# Use mode

Follow `/poteto-mode`. This skill adds how to lead work in these two repos when the budget is
fixed and the work is done by agents.

## The goal

The ModelValidator plugin is faithful to OCL as USE 7.5 evaluates it and is never wrong in
silence. Every verdict or answer is right, or an explicit ERROR. A WARN followed by a verdict
counts as silent. A SAT verdict is proved only when USE's `check` accepts the state. Open every
brief, PR `## Why` and report with this goal.

## You lead, agents do the work

- Your tokens are for orchestration. You plan, cut the work into lots, write briefs, read the
  agents' reports, run the gate script, open PRs and merge them. You do not diagnose a bug or
  write a fix yourself, and you do not read long logs or whole files: a script or an agent does.
- Each lot is one issue, or a few issues in the same files. Lots that run at the same time touch
  disjoint files, each in its own worktree.
- Every agent prompt carries a `Budget: $N` line. Before a wave, estimate its cost from the
  measured cost of past agents in each role. When the budget left does not cover the wave, tell
  the user the calculation and ask for the amount. Within an envelope, spend it on the most
  harmful bugs first, the ones that give a wrong verdict with no ERROR.

## Brief an agent so it succeeds

An agent knows only its brief. Write the shared rules of each role once, in a brief file, and
give each agent a pointer to it plus its issues, its worktree and its evidence folder. Every
brief states:

- the goal above, and what done means for this role;
- the inputs it gets, and the files it must not open or change;
- the one command that checks its own work, `ci/gate.sh`, and the output that counts as a pass;
- the report it returns: each step with its command and exit code, and every bug it found but
  did not fix, one line each, so you can open an issue.

## One bug, three roles

Use the cheapest model that does each role well. Spend on the spec: every later role works
from it, and a gap in it costs a failed implementation downstream.

1. **Spec writer.** Opus at high effort. It reproduces the bug on the real tool and writes the
   spec, which is the only input of the next role. The spec gives the behavior USE gives, with
   the USE output that shows it, and the behavior the plugin must give: the right value, or the
   ERROR text. Each expected behavior is a claim a test can check, with its input and its
   expected output. The spec also gives the root cause (file, method, line) and the smallest fix
   in plain words. It writes no code.
2. **Test writer.** Sonnet at high effort. From the spec alone, it follows the pstack `tdd` skill
   and the `principle-test-behavior-not-implementation` skill. It commits a failing JUnit test
   on its own, shown failing for the intended reason. It also writes the end-to-end journeys
   (see Gates) as hidden checks, shown failing on `develop`. It writes no production code.
3. **Implementer.** Haiku at medium effort, or high when the fix spans several places. It gets
   the spec and the JUnit tests, never the journeys. It runs `ci/gate.sh` without journeys
   until every gate passes. It changes production code only. It never edits, weakens, skips or
   deletes a test. When it thinks a test is wrong, it stops and reports why.
4. **You verify.** Run `ci/gate.sh` with the hidden journeys on the implementer's worktree, and
   read the diff. The check is a script, so it costs no agent. A failing gate goes to a fresh
   implementer with the gate output. When two implementations fail on the same premise, send
   the lot back to the spec writer, because the spec or the test is wrong.

The implementer cannot see the journeys, so a fix that passes them was written for the issue,
not for the checks.

## Gates

`ci/gate.sh` runs every gate and prints one PASS or FAIL line per gate: the JUnit suite, the
corpus oracle on both corpora, the OCL translation oracle, and, when it is given a journeys
folder, the journeys judged with `.claude/skills/verify-modelvalidator`. Read its header for the
arguments and the journey format. A run counts only after the script prints `ALL GATES PASS`.
Before you trust a changed gate, run it on `develop` and see each gate pass there.

## Shipping

- Start each worktree from a freshly fetched `origin/develop`.
- The session may push to one branch per repo. Merge the lots of a wave into that branch and
  open one draft PR per wave, assigned to the repo owner, with the headings Why, Decision and
  reasons, What changed, Verification, Found, not fixed. Mark it ready and merge it once CI is
  green.
- Open an issue for each bug found and not fixed.
- Privacy: no session links, no local paths and no personal data in commits, PRs, issues or
  files. AI mentions are fine. A commit message ends with a `Co-Authored-By: Claude <model>`
  line only. `.claude/hooks/privacy-gate.sh` checks each push and each GitHub post.

## Talking to the user

Answer in the user's language. Use short sentences and plain words. Lead with what changed for
them, give each claim its evidence, and say what is left and what it costs.
