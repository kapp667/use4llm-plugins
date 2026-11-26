#!/usr/bin/env bash
# Check that each existing USE scenario is a behaviour the tlaops spec allows (scripts/scenario.py):
# the example scripts of the USE release that call an operation (openter or a SOIL call), the
# shell tests of USE's sources whose expected output holds a precondition or postcondition verdict,
# and the behaviour probes in action-probes (x.cmd beside x.use).
# For each: USE runs the scenario with a state dump after each command, the settings follow from
# what it did, tlaops translates the model under them, and TLC follows USE's states with the
# spec's step actions, printing every check's value, which are compared with USE's verdicts.
# Usage: scripts/ops-scenarios.sh <out dir> <results dir> [<USE copy>]
# Output: <results dir>/ops-scenarios.tsv. Needs USE_HOME, USE_SRC and TLA2TOOLS. JOBS (4).
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${1:?out dir}"; res="${2:?results dir}"
use="${USE_HOME:?set USE_HOME}"; : "${USE_SRC:?set USE_SRC}" "${TLA2TOOLS:?set TLA2TOOLS}"
export TLA2TOOLS here out
mkdir -p "$out" "$res"
usecopy="${3:-}"
if [ -z "$usecopy" ]; then
  "$here/build.sh" "$use" >/dev/null
  usecopy="$out/use"; rm -rf "$usecopy"; mkdir -p "$usecopy"
  cp -r "$use/lib" "$use/etc" "$use/oclextensions" "$usecopy/"
  rm -f "$usecopy/lib/plugins"/ModelValidatorPlugin-*.jar
  cp "$here/build/use2tla.jar" "$usecopy/lib/plugins/"
fi
export usecopy
python3 -I "$here/scripts/scenario.py" find "$use/examples" "$USE_SRC/use-gui/src/it/resources/testfiles/shell" "$here/action-probes" >"$out/scenarios.tsv"
echo "scenarios: $(wc -l <"$out/scenarios.tsv")"

one() { # <name> <script> <model> <prefix scripts>
  local name="$1" script="$2" model="$3" prefix="$4" w m
  w="$out/cases/$(printf '%s' "$name" | tr -c 'A-Za-z0-9._\n' '_')"
  rm -rf "$w"; mkdir -p "$w"
  # shellcheck disable=SC2086
  m="$(python3 -I "$here/scripts/scenario.py" prepare "$script" "$model" "$w" $prefix)"
  mkdir -p "$w/model"; cp -r "$(dirname "$m")/." "$w/model/"
  usecmd() { (cd "$w/model" && timeout --kill-after=10 120 java -Djava.awt.headless=true -jar "$usecopy/lib/use-gui.jar" -nogui -nr \
    "$(basename "$m")" "$1" </dev/null 2>&1 | grep -v '^Picked up') || true; }
  usecmd "$w/run1.cmd" >"$w/use1.txt"
  if python3 -I "$here/scripts/scenario.py" settings "$w" 2>"$w/settings.err"; then
    printf 'tlaops %s %s\nexit\n' "$w/scenario.settings" "$w/spec" >"$w/run2.cmd"
    usecmd "$w/run2.cmd" >"$w/use2.txt"
    if python3 -I "$here/scripts/scenario.py" module "$w" 2>"$w/module.err"; then
      (cd "$w/spec" && timeout --kill-after=10 "${TLC_TIMEOUT:-120}" java -XX:+UseParallelGC -Xss512m -cp "$TLA2TOOLS" tlc2.TLC -workers 1 \
        -metadir "$w/spec/states" -config Trace.cfg Trace </dev/null >"$w/spec/tlc-trace.txt" 2>&1) || true
    fi
  fi
  python3 -I "$here/scripts/scenario.py" compare "$w" "$name" >"$w/row.tsv" 2>"$w/compare.err" \
    || printf '%s\t%s\t\t\t\t\t\t\t\t\t\t\tscenario failed\t%s\n' "$name" "$(basename "$m")" \
         "$(cat "$w/settings.err" "$w/module.err" "$w/compare.err" 2>/dev/null | grep -v '^ ' | tail -1 | tr '\t' ' ')" >"$w/row.tsv"
  echo "$name: $(cut -f13 "$w/row.tsv")"
}
export -f one
tr '\t' '\n' <"$out/scenarios.tsv" | xargs -d '\n' -n 4 -P "${JOBS:-4}" bash -c 'one "$0" "$1" "$2" "$3"'
# Negative control: the scenario check can fail. prepare.soil with Ada named "Adb" in USE's state
# after her birth: the spec's birth step cannot lead there, so the trace must stop at step 3.
neg="$out/cases/negative-control"
src="$out/cases/examples_soil_civstat_prepare.soil"
rm -rf "$neg"; cp -r "$src" "$neg"; rm -rf "$neg/spec/states"
python3 -I - "$neg/spec/Trace.tla" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p).read()
line = re.search(r"^S_s3 == .*$", s, re.M).group(0)
assert line.count('"Ada"') == 1
open(p, "w").write(s.replace(line, line.replace('"Ada"', '"Adb"')))
PY
(cd "$neg/spec" && java -XX:+UseParallelGC -cp "$TLA2TOOLS" tlc2.TLC -workers 1 -metadir "$neg/spec/states" -config Trace.cfg Trace \
  </dev/null >"$neg/spec/tlc-trace.txt" 2>&1) || true
python3 -I "$here/scripts/scenario.py" compare "$neg" "negative control: prepare.soil, Ada named Adb after birth" \
  | awk -F'\t' 'BEGIN { OFS = "\t" } { $13 = ($13 == "disagree" ? "negative control caught" : "negative control MISSED"); print }' >"$neg/row.tsv"
echo "negative control: $(cut -f10,13 "$neg/row.tsv")"
{ python3 -I "$here/scripts/scenario.py" header
  for f in "$out"/cases/*/row.tsv; do cat "$f"; done | LC_ALL=C sort; } >"$res/ops-scenarios.tsv"
awk -F'\t' 'NR > 1 { n[$13]++ } END { for (k in n) printf "%s: %d\n", k, n[k] }' "$res/ops-scenarios.tsv" | LC_ALL=C sort
