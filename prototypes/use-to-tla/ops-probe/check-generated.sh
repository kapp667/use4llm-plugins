#!/usr/bin/env bash
# Reproduce the hand probe's results with the generated spec: tlaops translates civstat.use under
# settings/civstat*.settings, TLC checks it, the refinement modules in match/ check it against
# Civstat.tla in both directions, and USE replays TLC's counterexamples step by step, its state
# compared with TLC's at each step.
# Usage: ops-probe/check-generated.sh <out dir>   Output: <out dir>/civstat-checks.tsv
# Needs USE_HOME (USE 7.5.0) and TLA2TOOLS. FULL=1 also runs 4 persons (about 1 min) and 5 with
# symmetry (about 2 min).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tr="$here/../translator"
out="${1:?out dir}"
use="${USE_HOME:?set USE_HOME}"; tla="${TLA2TOOLS:?set TLA2TOOLS}"
"$tr/build.sh" "$use" >/dev/null
rm -rf "$out"; mkdir -p "$out/use"
cp -r "$use/lib" "$use/etc" "$use/oclextensions" "$out/use/"
rm -f "$out/use/lib/plugins"/ModelValidatorPlugin-*.jar
cp "$tr/build/use2tla.jar" "$out/use/lib/plugins/"
python3 -I - "$use/examples/soil/civstat/civstat.use" "$out" <<'PY'
import sys
src, out = sys.argv[1], sys.argv[2]
s = open(src).read()
open(out + "/civstat.use", "w").write(s)
# The deliberately broken marry(): the spouse's civstat is no longer set (as CivstatBroken.tla).
a = "  self.civstat    := #married;\n  aSpouse.civstat := #married\nend"
assert s.count(a) == 1
open(out + "/civstat-broken.use", "w").write(s.replace(a, "  self.civstat    := #married\nend"))
PY
s="$here/settings"
{ cat "$s/civstat.settings"; echo "skip Person::nameIsUnique"; } >"$out/rest-3.settings"
{ cat "$out/rest-3.settings"; echo "objects Person 4"; } >"$out/rest-4.settings"
{ cat "$out/rest-3.settings"; echo "objects Person 5"; echo "symmetry"; } >"$out/sym-5.settings"
usecmd() { # <model> <cmd file>
  (cd "$out" && timeout 120 java -Djava.awt.headless=true -jar "$out/use/lib/use-gui.jar" -nogui -nr "$1" "$2" </dev/null 2>&1 | grep -v '^Picked up')
}
gen() { # <name> <model> <settings>
  printf 'tlaops %s %s\nexit\n' "$3" "$out/$1" >"$out/$1.cmd"
  usecmd "$2" "$out/$1.cmd" | grep 'tlaops:'
}
tlc() { # <name> [<module>]: TLC on <name>/UseOps or a module copied there; prints the log path
  local m="${2:-UseOps}"
  (cd "$out/$1" && java -XX:+UseParallelGC -cp "$tla" tlc2.TLC -workers 1 -metadir "states-$m" \
    -config "$m.cfg" "$m" </dev/null >"tlc-$m.txt" 2>&1) || true
  echo "$out/$1/tlc-$m.txt"
}
summary() { # <log>
  local f="$1" r
  r="$(grep -oE 'Error: (Invariant [A-Za-z0-9_]+ is violated|Action property.*violated)' "$f" | head -1 || true)"
  [ -n "$r" ] || r="$(grep -q 'Model checking completed. No error has been found.' "$f" && echo "no error" || echo "TLC failed: $(grep -m1 -E 'Error|Exception' "$f")")"
  printf '%s\t%s\t%s\t%s\n' "$r" "$(grep -oE '[0-9,]+ distinct states found' "$f" | tail -1 | grep -oE '^[0-9,]+' || echo '?')" \
    "$(grep -oE 'search is [0-9]+' "$f" | grep -oE '[0-9]+' || echo '?')" "$(grep -oE 'Finished in .* at' "$f" | sed 's/Finished in //; s/ at$//')"
}
replay() { # <name> <model>: USE replays the counterexample step by step (scripts/replay.py); prints
  # USE's verdict on the last state, the states equal to TLC's, and whether USE and TLC agree
  local d="$out/$1"
  rm -f "$d/dump.tsv"
  python3 -I "$tr/scripts/replay.py" commands "$d/tlc-UseOps.txt" "$d" "$d/dump.tsv" >"$d/replay.cmd"
  usecmd "$2" "$d/replay.cmd" >"$d/replay.txt" || true
  python3 -I "$tr/scripts/replay.py" compare "$d/tlc-UseOps.txt" "$d" "$d/dump.tsv" "$d/replay.txt" "$d/UseOps.cfg" "$1" \
    | awk -F'\t' '{ printf "%s; %s of %s states equal; %s", $7, $4, $3, $10; if ($11 != "") printf ": %s", $11; print "" }'
}
gen atomic civstat.use "$s/civstat.settings"
gen every civstat.use "$s/civstat-every-state.settings"
gen rest-3 civstat.use "$out/rest-3.settings"
gen broken civstat-broken.use "$out/rest-3.settings"
printf 'run\tsettings\ttlc\tdistinct_states\tdepth\ttime\tUSE replay\n' >"$out/civstat-checks.tsv"
row() { printf '%s\t%s\t%s\t%s\n' "$1" "$2" "$(summary "$3")" "${4:-}" >>"$out/civstat-checks.tsv"; }
row every-state civstat-every-state.settings "$(tlc every)" "$(replay every civstat.use)"
row atomic-birth civstat.settings "$(tlc atomic)" "$(replay atomic civstat.use)"
row rest-3 "civstat.settings + skip nameIsUnique" "$(tlc rest-3)"
cp "$here/Civstat.tla" "$here/match/"*.tla "$here/match/"*.cfg "$out/rest-3/"
row "generated refines hand (3)" rest-3 "$(tlc rest-3 GenRefinesHand)"
row "hand refines generated (3)" rest-3 "$(tlc rest-3 HandRefinesGen)"
# The refinement check can fail: a mapping that gives every birth the gender female.
sed 's/MODULE GenRefinesHand/MODULE WrongMapping/; s/aGender |-> E(last.args\[2\])/aGender |-> "female"/' "$out/rest-3/GenRefinesHand.tla" >"$out/rest-3/WrongMapping.tla"
cp "$out/rest-3/GenRefinesHand.cfg" "$out/rest-3/WrongMapping.cfg"
grep -q 'aGender |-> "female"' "$out/rest-3/WrongMapping.tla"
row "negative control: wrong mapping" rest-3 "$(tlc rest-3 WrongMapping)"
row broken-marry "rest-3 on civstat-broken.use" "$(tlc broken)" "$(replay broken civstat-broken.use)"
if [ -n "${FULL:-}" ]; then
  gen rest-4 civstat.use "$out/rest-4.settings"
  gen sym-5 civstat.use "$out/sym-5.settings"
  row rest-4 "rest-3 + objects Person 4" "$(tlc rest-4)"
  row sym-5 "rest-3 + objects Person 5 + symmetry" "$(tlc sym-5)"
fi
cat "$out/civstat-checks.tsv"
