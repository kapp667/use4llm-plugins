#!/usr/bin/env bash
# Compare the ModelValidator's translation of OCL with USE's evaluator: every expression of the
# grammar in expressions.py up to a depth, in every state of model.use within model.properties,
# asked with "mv ?". Then compare the expressions whose translation gives another value than USE
# with expected.tsv, and the expressions "mv ?" could not compare, with the numbers of states and
# expressions, with expected-not-compared.tsv. Uses javac, java and python3 from PATH.
# Usage: ci/translation-oracle/run.sh <USE install dir> <JUnit jar dir> <out dir> [--update]
#   USE install dir: the unpacked USE 7.5.0 binary release (searched for lib/use-core-*.jar)
#   JUnit jar dir:   holds junit-4.13.2.jar and hamcrest-core-1.3.jar (the test sources need it)
#   --update:        write the disagreements found into expected.tsv and the not-compared
#                    expressions into expected-not-compared.tsv instead of comparing
# Outputs: <out dir>/answers.tsv, every answer of mv ? that is not an agreement, and
#          <out dir>/disagreements.tsv, one row per expression whose value differs in some state,
#          <out dir>/not-compared.tsv, the numbers of states and expressions and the expressions
#          that were not compared with a solution in some state.
# Exits 1 if the disagreements differ from expected.tsv, if the not-compared expressions or the
# numbers of states or expressions differ from expected-not-compared.tsv, if an expression is not valid OCL, or if
# the states were cut off by ORACLE_MAX_STATES.
# ORACLE_DEPTH sets the depth of the grammar (default 2), ORACLE_MAX_STATES the states (default 400),
# ORACLE_SHARDS the number of JVMs that share the expressions (default: the number of CPUs).
set -euo pipefail

update=0
if [ "${4:-}" = --update ]; then update=1; set -- "$1" "$2" "$3"; fi
[ "$#" -eq 3 ] || { sed -n '2,20p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../.." && pwd)"
junit="$(cd "$2" && pwd)"
mkdir -p "$3"
out="$(cd "$3" && pwd)"

"$root/ci/build-and-test.sh" --build-only "$1" "$out/build" >"$out/build.log"
usecore="$(find "$1" -path '*/lib/use-core-*.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
usegui="$(find "$1" -path '*/lib/use-gui.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
cp="$out/build/mv/classes:$usecore:$usegui:$junit/junit-4.13.2.jar:$junit/hamcrest-core-1.3.jar"
tc="$out/classes"
mkdir -p "$tc"
find "$root/ModelValidator/trunk/test" -name '*.java' >"$out/sources.txt"
echo "$here/TranslationOracle.java" >>"$out/sources.txt"
javac -nowarn -encoding UTF-8 --release 21 -proc:none -cp "$cp" -d "$tc" @"$out/sources.txt"

python3 "$here/expressions.py" "${ORACLE_DEPTH:-2}" >"$out/expressions.txt"
echo "expressions: $(wc -l <"$out/expressions.txt")"
describe='A.allInstances->collect(a | Tuple{o = a, b = a.oclIsTypeOf(B), n = a.n, dst = a.dst})'
# One JVM per shard of the expressions; each one finds the same states in the same order.
shards="${ORACLE_SHARDS:-$(nproc)}"
rm -f "$out"/shard-*
split -n "r/$shards" -d "$out/expressions.txt" "$out/shard-"
pids=()
for s in "$out"/shard-[0-9]*; do
  # The plugin reads its configuration relative to the working directory.
  (cd "$root/ModelValidator/trunk" && exec java -Djava.awt.headless=true -Xss8m -cp "$tc:$cp" \
    org.tzi.use.kodkod.transform.ocl.TranslationOracle "$here/model.use" "$here/model.properties" \
    "$s" "${ORACLE_MAX_STATES:-400}" "$describe" "$s.tsv" >"$s.log" 2>&1) &
  pids+=("$!")
done
for p in "${pids[@]}"; do wait "$p"; done
first="$(ls "$out"/shard-[0-9]*.tsv | sed -n 1p)"
for t in "$out"/shard-[0-9]*.tsv; do
  cmp -s <(grep -P '\tstate\t' "$first") <(grep -P '\tstate\t' "$t") ||
    { echo "the shards found different states: $first, $t" >&2; exit 1; }
done
{ grep -P '\tstate\t' "$first"; cat "$out"/shard-[0-9]*.tsv | grep -vP '\t(state|end)\t'
  awk -F'\t' '$2 == "end" { s = $4; a += $5; r = $3 } END { print "0\tend\t" r "\t" s "\t" a "\t" }' "$out"/shard-[0-9]*.tsv
} >"$out/answers.tsv"

python3 "$here/summarize.py" "$out/answers.tsv" "$out/disagreements.tsv" "$out/expressions.txt"
if [ "$update" -eq 1 ]; then
  cp "$out/disagreements.tsv" "$here/expected.tsv"
  cp "$out/not-compared.tsv" "$here/expected-not-compared.tsv"
  echo "expected.tsv and expected-not-compared.tsv updated"
  exit 0
fi
diff -u "$here/expected.tsv" "$out/disagreements.tsv" >"$out/expected.diff" && status=0 || status=1
diff -u "$here/expected-not-compared.tsv" "$out/not-compared.tsv" >"$out/expected-not-compared.diff" || status=1
python3 "$here/summarize.py" --check "$out/answers.tsv" || status=1
if [ "$status" -eq 0 ]; then
  echo "OK: the disagreements and the not-compared expressions are the ones expected"
else
  echo "FAILED: see $out/expected.diff, $out/expected-not-compared.diff and $out/answers.tsv"
  sed -n '1,40p' "$out/expected.diff" "$out/expected-not-compared.diff"
fi
exit "$status"
