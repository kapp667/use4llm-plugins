#!/usr/bin/env bash
# Run every gate a ModelValidator change must pass, and print one PASS or FAIL line per gate:
# the JUnit suite, the corpus oracle on USE's examples and on a tests corpus, the OCL
# translation oracle, and, if given, end-to-end journeys judged in the USE shell.
# Usage: ci/gate.sh <USE install dir> <JUnit jar dir> <tests corpus dir> <out dir> [<journeys dir>]
#   USE install dir: the unpacked USE 7.5.0 binary release; its examples/ folder is one corpus
#   JUnit jar dir:   holds junit-4.13.2.jar and hamcrest-core-1.3.jar
#   journeys dir:    one folder per journey with model.use, journey.cmd and judge.args (the
#                    options of .claude/skills/verify-modelvalidator/scripts/judge.sh, quoted as
#                    in a shell, e.g. --expect SAT --require 'INFO: true'), plus any properties
#                    file journey.cmd names
# Logs stay in <out dir>, one per gate. Exits 0 only when every gate passed.
set -uo pipefail

[ "$#" -ge 4 ] || { sed -n '2,11p' "$0" >&2; exit 2; }
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
use="$(cd "$1" && pwd)"; junit="$(cd "$2" && pwd)"; tests="$(cd "$3" && pwd)"
mkdir -p "$4"; out="$(cd "$4" && pwd)"
journeys="${5:-}"
cd "$root"
fail=0

gate() { # name, command...
	local name="$1"; shift
	if "$@" >"$out/$name.log" 2>&1; then
		echo "PASS $name"
	else
		echo "FAIL $name (log: $out/$name.log)"; tail -n 15 "$out/$name.log"; fail=1
	fi
}

gate junit ci/build-and-test.sh "$use" "$junit" "$out/build"
for corpus in "examples:$use/examples" "tests:$tests"; do
	name="${corpus%%:*}"; dir="${corpus#*:}"
	gate "oracle-$name" bash -c 'ci/oracle/run.sh "$1" "$2" "$3" "$4" && ci/oracle/compare.sh "ci/oracle/expected/$5.tsv" "$4/oracle.tsv"' \
		_ "$use" "$out/build" "$dir" "$out/oracle-$name" "$name"
done
gate translation ci/translation-oracle/run.sh "$use" "$junit" "$out/translation"

if [ -n "$journeys" ]; then
	scripts=.claude/skills/verify-modelvalidator/scripts
	gate setup "$scripts/setup.sh" "$use" "$out/e2e-work"
	for j in "$journeys"/*/; do
		j="${j%/}"; name="$(basename "$j")"
		rm -rf "$out/e2e/$name"; mkdir -p "$out/e2e"
		gate "journey-$name" bash -c '"$1/drive.sh" "$2" "$3/model.use" "$3/journey.cmd" "$4" >/dev/null; xargs -a "$3/judge.args" "$1/judge.sh" "$4"' \
			_ "$scripts" "$out/e2e-work" "$j" "$out/e2e/$name"
	done
fi

if [ "$fail" -eq 0 ]; then echo "ALL GATES PASS"; else echo "SOME GATES FAIL"; fi
exit "$fail"
