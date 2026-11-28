#!/usr/bin/env bash
# Run the ModelValidator end to end on every USE model of a corpus, then judge each solution
# with USE's own check. Each model is solved with the default bounds the validator writes, then
# with wide bounds derived from them by widen.sh. A model with a witness, a file beside it with the
# same name and the extension .cmd, .in or .soil whose "!" commands build a state USE's check
# accepts, is also solved with the exact sizes of that state, derived by witness.sh; the validator
# must find a solution there. A witness whose replay times out, whose state USE's check does not
# accept or has no object, or whose model has no default properties file, gets a SKIPPED row.
# Uses java and timeout from PATH.
# Usage: ci/oracle/run.sh <USE install dir> <plugin jar dir> <corpus dir> <out dir>
#   USE install dir: the unpacked USE binary release (searched for lib/use-gui.jar)
#   plugin jar dir:  holds ModelValidatorPlugin.jar and use-filmstrip.jar
#   corpus dir:      searched for *.use files; each one is a case
# Outputs: <out dir>/oracle.tsv, a default row, a wide row and, for a model with a witness, a
#          witness row per model in sorted order, <out dir>/metrics.tsv, a row per run of the
#          validator with the size of its problem and the time of each phase, n/a where the output
#          holds none, and <out dir>/cases/<n>/output.txt, wide.txt, replay.txt and witness.txt, the
#          raw USE outputs of the n-th model.
#          Exits 1 if and only if a SAT solution fails USE's check, the witness bounds are UNSAT,
#          or widen.sh or witness.sh fails.
# ORACLE_TIMEOUT sets the seconds allowed per run of USE, up to four per model (default 120).
set -euo pipefail

[ "$#" -eq 4 ] || { sed -n '2,21p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usedir="$(cd "$1" && pwd)"
jars="$(cd "$2" && pwd)"
corpus="$(cd "$3" && pwd)"
mkdir -p "$4"
out="$(cd "$4" && pwd)"
limit="${ORACLE_TIMEOUT:-120}"

# The release zip may or may not have a top folder: look for the jar instead of assuming a path.
usegui="$(find "$usedir" -path '*/lib/use-gui.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
[ -n "$usegui" ] || { echo "use-gui.jar not found under $usedir" >&2; exit 1; }
usehome="$(dirname "$(dirname "$usegui")")"

# A private USE copy whose plugins are the jars under test. USE refuses a second plugin of the
# same name, so the bundled builds go first.
if [ ! -d "$out/use" ]; then
  mkdir -p "$out/use"
  cp -r "$usehome/lib" "$out/use/"
  # etc holds USE's settings, oclextensions the OCL extension operations some models call;
  # USE looks for both beside lib.
  for d in etc oclextensions; do
    if [ -d "$usehome/$d" ]; then cp -r "$usehome/$d" "$out/use/"; fi
  done
  rm -f "$out/use/lib/plugins"/ModelValidatorPlugin-*.jar "$out/use/lib/plugins/use-filmstrip.jar"
  cp "$jars/ModelValidatorPlugin.jar" "$jars/use-filmstrip.jar" "$out/use/lib/plugins/"
fi

# Default bounds: with no properties file, mv -validate writes <model>.properties next to the
# model and solves with it. "info state" counts the solution's objects.
printf 'mv -validate\ninfo state\ncheck\nexit\n' >"$out/commands.cmd"

# Sorted with the C locale, so the order does not depend on the runner.
mapfile -t models < <(cd "$corpus" && find . -name '*.use' -type f | sed 's|^\./||' | LC_ALL=C sort)
[ "${#models[@]}" -gt 0 ] || { echo "no .use file under $corpus" >&2; exit 1; }

tsv="$out/oracle.tsv"
printf 'model\tbounds\tverdict\tcheck\tobjects\terrors\tseconds\tnote\n' >"$tsv"
metrics="$out/metrics.tsv"
printf 'model\tbounds\tverdict\tbitwidth\tatoms\tintegers\tvariables\tprimary\tclauses\tuse to kodkod ms\tkodkod to sat ms\tsolving ms\n' >"$metrics"
failures=0

# Runs USE on the model of the current case with a command file and sets status to its exit
# status. Usage: use_run <command file> <output file name>
use_run() {
  status=0
  (cd "$dir" && timeout --kill-after=10 "$limit" java -Djava.awt.headless=true -jar "$out/use/lib/use-gui.jar" \
    -nogui -nr "$(basename "$model")" "$1" </dev/null >"$2" 2>&1) || status=$?
}

# Runs use_run, then adds the row of the given bounds to the table.
# Usage: solve <bounds> <command file> <output file name>
solve() {
  local start status seconds row verdict check objects errors note
  start="$EPOCHREALTIME"
  use_run "$2" "$3"
  seconds="$(awk -v a="$start" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.1f", b - a }')"
  row="$("$here/parse.sh" "$dir/$3" "$status")"
  IFS=$'\t' read -r verdict check objects errors note <<<"$row"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$model" "$1" "$verdict" "$check" "$objects" "$errors" "$seconds" "$note" >>"$tsv"
  printf '%s\t%s\t%s\t%s\n' "$model" "$1" "$verdict" "$("$here/metrics.sh" "$dir/$3")" >>"$metrics"
  echo "[$n/${#models[@]}] $model, $1 bounds: $verdict, check $check, $objects objects, $errors errors, ${seconds}s"
  [ "$check" != fail ] || failures=$((failures + 1))
  # The witness state meets the witness bounds, so UNSAT is a wrong answer.
  if [ "$1" = witness ] && [ "$verdict" = UNSAT ]; then failures=$((failures + 1)); fi
}

# Adds a SKIPPED row of the given bounds to the table. Usage: skip <bounds> <reason>
skip() {
  printf '%s\t%s\tSKIPPED\tn/a\tn/a\t0\t0.0\t%s\n' "$model" "$1" "$2" >>"$tsv"
  echo "[$n/${#models[@]}] $model, $1 bounds: SKIPPED, $2"
}

n=0
for model in "${models[@]}"; do
  n=$((n + 1))
  dir="$out/cases/$(printf '%03d' "$n")"
  rm -rf "$dir"
  mkdir -p "$dir"
  # The whole folder, for the models it imports; the validator writes its properties file there.
  cp -r "$corpus/$(dirname "$model")/." "$dir/"
  solve default "$out/commands.cmd" output.txt
  defaults="$dir/$(basename "$model" .use).properties"
  if [ -f "$defaults" ]; then
    # Wide bounds: wide.properties, derived from the default ones, in the case folder.
    "$here/widen.sh" "$defaults" >"$dir/wide.properties"
    # USE resolves a file named in a command file against the folder of that command file.
    printf 'mv -validate wide.properties\ninfo state\ncheck\nexit\n' >"$dir/wide.cmd"
    solve wide "$dir/wide.cmd" wide.txt
  else
    # The validator writes the file even for a model it refuses; a missing one is still reported.
    skip wide "no default properties file to widen"
  fi
  witness=""
  for ext in cmd in soil; do
    candidate="$corpus/${model%.use}.$ext"
    if [ -f "$candidate" ] && grep -q '^!' "$candidate"; then witness="$candidate"; break; fi
  done
  if [ -z "$witness" ]; then
    :
  elif [ ! -f "$defaults" ]; then
    skip witness "no default properties file to derive the witness bounds from"
  else
    # Replay the witness, print the model and state witness.sh reads, then run USE's check.
    { grep '^!' "$witness"; printf 'info model\ninfo state\ncheck\nexit\n'; } >"$dir/replay.cmd"
    use_run "$dir/replay.cmd" replay.txt
    IFS=$'\t' read -r check objects failure < <("$here/state.sh" "$dir/replay.txt")
    if [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then
      skip witness "the replay of $(basename "$witness") timed out"
    elif [ "$check" != ok ]; then
      skip witness "USE's check does not accept the state of $(basename "$witness"): $failure"
    elif [ "$objects" = 0 ] || [ "$objects" = unknown ]; then
      skip witness "the state of $(basename "$witness") has no object"
    else
      "$here/witness.sh" "$defaults" "$dir/replay.cmd" "$dir/replay.txt" "$dir/output.txt" >"$dir/witness.properties"
      printf 'mv -validate witness.properties\ninfo state\ncheck\nexit\n' >"$dir/witness.cmd"
      solve witness "$dir/witness.cmd" witness.txt
    fi
  fi
done

echo "oracle: ${#models[@]} models, $failures failures (a solution USE's check rejects, or UNSAT witness bounds); table in $tsv"
[ "$failures" -eq 0 ]
