#!/usr/bin/env bash
# Run the ModelValidator end to end on every USE model of a corpus, then judge each solution
# with USE's own check. Uses java and timeout from PATH.
# Usage: ci/oracle/run.sh <USE install dir> <plugin jar dir> <corpus dir> <out dir>
#   USE install dir: the unpacked USE binary release (searched for lib/use-gui.jar)
#   plugin jar dir:  holds ModelValidatorPlugin.jar and use-filmstrip.jar
#   corpus dir:      searched for *.use files; each one is a case
# Outputs: <out dir>/oracle.tsv, one row per case in sorted order, and <out dir>/cases/<n>/output.txt,
#          the raw USE output of row n. Exits 1 if and only if a SAT solution fails USE's check.
# ORACLE_TIMEOUT sets the seconds allowed per case (default 120).
set -euo pipefail

[ "$#" -eq 4 ] || { sed -n '2,10p' "$0" >&2; exit 2; }
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
# model and solves with it.
printf 'mv -validate\ncheck\nexit\n' >"$out/commands.cmd"

# Sorted with the C locale, so the order does not depend on the runner.
mapfile -t models < <(cd "$corpus" && find . -name '*.use' -type f | sed 's|^\./||' | LC_ALL=C sort)
[ "${#models[@]}" -gt 0 ] || { echo "no .use file under $corpus" >&2; exit 1; }

tsv="$out/oracle.tsv"
printf 'model\tbounds\tverdict\tcheck\terrors\tseconds\tnote\n' >"$tsv"
failures=0
n=0
for model in "${models[@]}"; do
  n=$((n + 1))
  dir="$out/cases/$(printf '%03d' "$n")"
  rm -rf "$dir"
  mkdir -p "$dir"
  # The whole folder, for the models it imports; the validator writes its properties file there.
  cp -r "$corpus/$(dirname "$model")/." "$dir/"
  start="$EPOCHREALTIME"
  status=0
  (cd "$dir" && timeout --kill-after=10 "$limit" java -Djava.awt.headless=true -jar "$out/use/lib/use-gui.jar" \
    -nogui -nr "$(basename "$model")" "$out/commands.cmd" </dev/null >output.txt 2>&1) || status=$?
  seconds="$(awk -v a="$start" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.1f", b - a }')"
  row="$("$here/parse.sh" "$dir/output.txt" "$status")"
  IFS=$'\t' read -r verdict check errors note <<<"$row"
  printf '%s\tdefault\t%s\t%s\t%s\t%s\t%s\n' "$model" "$verdict" "$check" "$errors" "$seconds" "$note" >>"$tsv"
  echo "[$n/${#models[@]}] $model: $verdict, check $check, $errors errors, ${seconds}s"
  [ "$check" != fail ] || failures=$((failures + 1))
done

echo "oracle: ${#models[@]} cases, $failures failing USE's check; table in $tsv"
[ "$failures" -eq 0 ]
