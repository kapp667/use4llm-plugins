# Notes for agents working in this repository

## The USE to TLA+ prototype must not be lost

The branch `claude/brave-gauss-m0bmj8` holds the USE to TLA+ prototype, under `prototypes/use-to-tla/`. It is about 80 files and 7,400 lines that exist on no other branch. Draft PR #43 shows it, marked "do not merge".

- Never delete, force-push, rebase or reset the branch `claude/brave-gauss-m0bmj8`.
- Never close or merge PR #43. Only the owner decides what happens to it.
- Do not push to that branch unless the owner asks for it. Merging `develop` into it is the only update it has had.
- Branch cleanups and stale-branch sweeps skip this branch.
- The branch of the same name in kapp667/use4llm is a separate piece of work (PR #3). The same rules apply to it.
