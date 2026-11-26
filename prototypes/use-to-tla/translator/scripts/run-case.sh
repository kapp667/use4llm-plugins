#!/usr/bin/env bash
# Run one corpus case: replay the witness in USE, check it, run the tla command, then TLC on
# the eval module, the model-checking module of S0 and the one of the first violating state.
# Usage: run-case.sh <out dir> <corpus root> <model path relative to the root> <witness path | validator>
#   validator: S0 is the solution the ModelValidator finds with its default bounds (mv -validate).
set -uo pipefail
out="$1"; root="$2"; model="$3"; witness="$4"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tla2tools="${TLA2TOOLS:?set TLA2TOOLS}"
limit="${CASE_TIMEOUT:-300}"
id="$(printf '%s' "$root/$model" | sed 's|^/||; s|[^A-Za-z0-9._-]|_|g' | sed 's|.*examples_||; s|.*use4llm_||')"
if [ "$witness" = validator ]; then id="V__$id"; else id="W__$id"; fi
case="$out/cases/$id"
rm -rf "$case"; mkdir -p "$case/model"
cp -r "$root/$(dirname "$model")/." "$case/model/"
if [ "$witness" = validator ]; then
  printf 'mv -validate\ninfo state\ncheck\ntla %s\nexit\n' "$case/tla" >"$case/replay.cmd"
else
  { grep '^!' "$witness"; printf 'info state\ncheck\ntla %s\nexit\n' "$case/tla"; } >"$case/replay.cmd"
fi
start="$EPOCHREALTIME"
printf '%s\t%s\t%s\n' "$root" "$model" "$witness" >"$case/corpus-line.tsv"
(cd "$case/model" && timeout --kill-after=10 "$limit" java -Dtla.mutant="${TLA_MUTANT:-}" -Djava.awt.headless=true -jar "$out/use/lib/use-gui.jar" \
  -nogui -nr "$(basename "$model")" "$case/replay.cmd" </dev/null >"$case/use.txt" 2>&1); status=$?
use_s="$(awk -v a="$start" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.2f", b - a }')"
IFS=$'\t' read -r check objects failure < <("$here/../../../ci/oracle/state.sh" "$case/use.txt")
tlc() { # <module> <log>: run TLC, print seconds
  local s="$EPOCHREALTIME"
  (cd "$case/tla" && timeout --kill-after=10 "$limit" java -Xss512m -XX:+UseParallelGC -cp "$tla2tools" tlc2.TLC -workers 1 \
    -metadir "$case/tla/states-$1" -config "$1.cfg" "$1.tla" </dev/null >"$case/$2" 2>&1)
  awk -v a="$s" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.2f", b - a }'
}
eval_s=""; mc_s=""; viol_s=""
if [ -f "$case/tla/UseEval.tla" ]; then
  eval_s="$(tlc UseEval tlc-eval.txt)"
  mc_s="$(tlc UseMC tlc-mc.txt)"
  if [ -f "$case/tla/UseMCViol.tla" ]; then viol_s="$(tlc UseMCViol tlc-viol.txt)"; fi
  for m in "$case"/tla/UseErr_*.tla; do
    [ -f "$m" ] || continue
    m="$(basename "$m" .tla)"; tlc "$m" "tlc-$m.txt" >/dev/null
  done
fi
printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$root/$model" "$(basename "$witness")" "$status" "$check" \
  "$objects" "$(printf '%s' "$failure" | tr '\t' ' ')" "$use_s" "$eval_s" "$mc_s" "$viol_s" >"$case/case.tsv"
echo "$id: use $status/$check/$objects objects ${use_s}s, tlc eval ${eval_s:-n/a}s mc ${mc_s:-n/a}s viol ${viol_s:-n/a}s"
