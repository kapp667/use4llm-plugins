#!/usr/bin/env bash
# Replay TLC behaviours of the corpus specs in USE, step by step (scripts/replay.py): the
# counterexample of each violation that ops-corpus.sh found, and for each translated operation the
# shortest behaviour that ends with a call of it whose postconditions hold (a cover run). A cover
# run explores only states where every postcondition held, as USE rolls a call back otherwise.
# Usage: scripts/ops-replays.sh <ops-corpus.sh out dir> <results dir>
# Output: <results dir>/ops-replays.tsv, one row per replayed behaviour or cover run not reached.
# Needs TLA2TOOLS; uses the USE copy of the ops-corpus.sh run. JOBS (4) runs in parallel.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${1:?ops-corpus out dir}"; res="${2:?results dir}"
: "${TLA2TOOLS:?set TLA2TOOLS}"
export TLA2TOOLS here out
mkdir -p "$res"

jobs="$out/replay-jobs.txt"
: >"$jobs"
for d in "$out"/cases/*/; do
  d="${d%/}"
  [ -f "$d/tla/ops.tsv" ] || continue
  grep -q '^Error: Invariant .* is violated' "$d/tlc-checks.txt" 2>/dev/null && printf '%s\tviolation\t-\n' "$d" >>"$jobs"
  awk -F'\t' '$1 == "operation" && $3 == "translated" { print $2 }' "$d/tla/ops.tsv" | while read -r op; do
    printf '%s\tcover\t%s\n' "$d" "$op" >>"$jobs"
  done
done

one() { # <case dir> <kind> <op>
  local d="$1" kind="$2" op="$3" n r m
  m="$(cut -f2 "$d/model.tsv")"; m="$(basename "$m")"
  if [ "$kind" = violation ]; then
    n=violation; r="$d/replay-$n"; mkdir -p "$r"
    cp "$d/tlc-checks.txt" "$r/tlc.txt"; cp "$d/tla/UseOps.cfg" "$r/run.cfg"
  else
    local cid; cid="$(printf '%s' "$op" | tr -c 'A-Za-z0-9_\n' '_')"
    n="cover-$cid"; r="$d/replay-$n"; mkdir -p "$r"
    # Cover_n is violated by a call of op whose postconditions hold: the shortest such behaviour.
    { printf -- '---- MODULE %s ----\nEXTENDS UseOps\n' "Cover_$cid"
      printf 'PostsHold == TRUE'; awk -F'\t' 'NR > 1 && $2 ~ /^postcondition / { printf " /\\ %s", $1 }' "$d/tla/checks.tsv"
      printf '\nCover_%s == ~(last.op = "%s" /\\ PostsHold)\n====\n' "$cid" "$op"; } >"$d/tla/Cover_$cid.tla"
    { grep -E '^(CONSTANT|INIT|NEXT|SYMMETRY|CHECK_DEADLOCK)' "$d/tla/UseOps.cfg"
      grep -E '^CONSTRAINT' "$d/tla/UseOps.cfg" || true
      echo "CONSTRAINT PostsHold"; echo "INVARIANT Cover_$cid"; } >"$d/tla/Cover_$cid.cfg"
    cp "$d/tla/Cover_$cid.cfg" "$r/run.cfg"
    (cd "$d/tla" && timeout --kill-after=10 "${TLC_TIMEOUT:-120}" java -XX:+UseParallelGC -Xss512m -cp "$TLA2TOOLS" tlc2.TLC -workers 1 \
      -metadir "$r/states" -config "Cover_$cid.cfg" "Cover_$cid" </dev/null >"$r/tlc.txt" 2>&1) || true
    if ! grep -q '^Error: Invariant Cover_.* is violated' "$r/tlc.txt"; then
      local s; s="$(grep -oE '[0-9,]+ distinct states found' "$r/tlc.txt" | tail -1 | grep -oE '^[0-9,]+' | tr -d , || echo '?')"
      if grep -q 'Model checking completed. No error has been found.' "$r/tlc.txt"; then
        printf '%s\t%s\t0\t0\t0\t0\tnot reached in %s states\t\t0\t0\tnot reached\t\n' "$(basename "$d")" "$n" "$s" >"$r/row.tsv"
      else
        printf '%s\t%s\t0\t0\t0\t0\tTLC failed\t\t0\t0\tTLC failed\t%s\n' "$(basename "$d")" "$n" "$(grep -m1 -E 'Error|Exception' "$r/tlc.txt" | tr '\t' ' ')" >"$r/row.tsv"
      fi
      return 0
    fi
  fi
  rm -f "$r/dump.tsv"
  python3 -I "$here/scripts/replay.py" commands "$r/tlc.txt" "$d/tla" "$r/dump.tsv" >"$r/replay.cmd"
  (cd "$d/model" && timeout --kill-after=10 120 java -Djava.awt.headless=true -jar "$out/use/lib/use-gui.jar" -nogui -nr "$m" "$r/replay.cmd" \
    </dev/null 2>&1 | grep -v '^Picked up' >"$r/replay.txt") || true
  python3 -I "$here/scripts/replay.py" compare "$r/tlc.txt" "$d/tla" "$r/dump.tsv" "$r/replay.txt" "$r/run.cfg" "$(basename "$d")"$'\t'"$n" \
    >"$r/row.tsv" 2>"$r/compare.err" \
    || printf '%s\t%s\t0\t0\t0\t0\t\t\t0\t0\treplay failed\t%s\n' "$(basename "$d")" "$n" "$(tail -1 "$r/compare.err" | tr '\t' ' ')" >"$r/row.tsv"
}
export -f one
tr '\t' '\n' <"$jobs" | xargs -d '\n' -n 3 -P "${JOBS:-4}" bash -c 'one "$0" "$1" "$2"'
{ printf 'case\t%s\n' "$(python3 -I "$here/scripts/replay.py" header)"
  for d in "$out"/cases/*/replay-*/; do [ -f "$d/row.tsv" ] && cat "$d/row.tsv"; done | LC_ALL=C sort; } >"$res/ops-replays.tsv"
awk -F'\t' 'NR > 1 { n[$11]++ } END { for (k in n) printf "%s: %d\n", k, n[k] }' "$res/ops-replays.tsv" | LC_ALL=C sort
