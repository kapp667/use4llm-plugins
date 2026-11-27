#!/usr/bin/env bash
# Show the behaviour checks can fail: rerun the whole behaviour oracle (scripts/ops-oracle.sh)
# once per deliberate bug of the action translation (Ops.MUTANT) and say which checks catch it.
# Usage: scripts/ops-mutants.sh <out dir> <results file> [<mutant>...]
# Output: <results file>, one row per mutant (scripts/mutant-row.py says the rule): how often it
# changed the translation (0: no model exercises it), then per check "caught" or "-": civstat (the
# hand spec), replays, scenarios, witnesses, probes (the replays of the probe models of
# action-probes), corpus_changed (ops-corpus.tsv differs, which no oracle judges), and caught.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${1:?out dir}"; res="${2:?results file}"; shift 2
mkdir -p "$out"
list="$(cat <<'LIST'
frame-att|an attribute assignment loses the other objects' values of that attribute (a frame not kept)
frame-link|a link insertion loses the association's other links (a frame not kept)
frame-objs|new forgets the objects that exist (a frame not kept)
guard|the preconditions are ignored (no guard)
atpre|@pre in a postcondition reads the state after the call
atpre-new|in "new C call op", @pre reads the state before new instead of after
init|new does not apply the init expressions
dispatch|a call runs the operation of the step's class, not the override of the object's class
result|a result the body never assigns is no failure
nofail|a failed statement is skipped and the body goes on
stale-read|the statements of a body read the state at entry, not the one the previous statements left
ifundef|an if with an undefined condition runs the then branch
undef-target|an assignment to an undefined object is no failure
dup-link|inserting a link already there, or deleting a missing one, is no failure
new-fresh|new inside a body may take an object that exists
mult|a multiplicity upper bound one too high
LIST
)"
[ "$#" -eq 0 ] || list="$(printf '%s\n' "$list" | grep -E "^($(IFS='|'; echo "$*"))\|")"
if [ ! -f "$out/baseline-res/ops-oracle.tsv" ]; then
  "$here/scripts/ops-oracle.sh" "$out/baseline" "$out/baseline-res" >"$out/baseline.log" 2>&1
fi
printf 'mutant\tdescription\tapplied\tcivstat\treplays\tscenarios\twitnesses\tprobes\tcorpus_changed\tcaught\tfirst_sign\n' >"$res"
while IFS='|' read -r name descr; do
  [ -n "$name" ] || continue
  TLA_OPS_MUTANT="$name" "$here/scripts/ops-oracle.sh" "$out/$name" "$out/$name-res" >"$out/$name.log" 2>&1 || true
  applied="$(grep -rhoE "mutant $name applied [0-9]+ times" "$out/$name" 2>/dev/null | awk '{ s += $4 } END { print s + 0 }')"
  python3 -I "$here/scripts/mutant-row.py" "$out/baseline-res" "$out/$name-res" "$name" "$descr" "$applied" >>"$res"
  echo "$name: $(tail -n 1 "$res" | cut -f3-10)"
  rm -rf "$out/$name"
done <<<"$list"
python3 -I "$here/scripts/relpaths.py" "$res"
sed -i "s|$out/|<out>/|g; s|$here/|translator/|g" "$res"
