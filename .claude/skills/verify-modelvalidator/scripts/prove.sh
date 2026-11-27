#!/usr/bin/env bash
# Prove the harness end to end on the small Pets model in fixtures/: the journeys must PASS and the
# negative controls must FAIL. A control that passes means the judge would let a wrong verdict through.
# Usage: prove.sh <work dir> <evidence root>
#   work dir:       the one setup.sh made
#   evidence root:  a new or empty folder; each run gets its own subfolder with transcript.txt and judgement.txt
# Journeys (each must PASS):
#   validate-sat      mv -validate on bounds that have a solution, then USE's info state and check
#   validate-unsat    mv -validate on bounds no solution fits (an adult invariant against age 10)
#   query             mv ? enable, a solution, three queries the plugin compares with USE, then check
#   vc                mv -vc on a valid and on an invalid configuration
# Controls (each must FAIL):
#   wrong-verdict     the UNSAT transcript judged as if SAT were expected
#   no-check          the SAT transcript with its check summary cut out
#   query-no-enable   mv ? without enable: nothing is compared, which must not pass
# Exits 0 only when every journey passed and every control failed.
set -euo pipefail

[ "$#" -eq 2 ] || { sed -n '2,17p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fix="$(cd "$here/../fixtures" && pwd)"
work="$(cd "$1" && pwd)"
mkdir -p "$2"
root_ev="$(cd "$2" && pwd)"
bad=0

"$here/doctor.sh" "$work"

drive() { "$here/drive.sh" "$work" "$fix/pets.use" "$fix/$2" "$root_ev/$1" >/dev/null; }

journey() { # name, command file, judge options...
  local name="$1" cmd="$2"
  shift 2
  drive "$name" "$cmd"
  if "$here/judge.sh" "$root_ev/$name" "$@" >/dev/null; then
    echo "PASS journey $name"
  else
    echo "FAIL journey $name (it should pass):"
    sed 's/^/    /' "$root_ev/$name/judgement.txt"
    bad=1
  fi
}

control() { # name, evidence dir, regex of the reason it must give, judge options...; the judge must fail
  local name="$1" dir="$2" reason="$3"
  shift 3
  if "$here/judge.sh" "$dir" "$@" >"$root_ev/$name.judgement.txt" 2>&1; then
    echo "FAIL control $name: the judge passed a run it must reject"
    bad=1
  elif ! grep -qE "^FAIL: .*($reason)" "$root_ev/$name.judgement.txt"; then
    echo "FAIL control $name: rejected, but not for the expected reason ($reason):"
    sed 's/^/    /' "$root_ev/$name.judgement.txt"
    bad=1
  else
    echo "PASS control $name (rejected: $(grep -m1 -E "^FAIL: .*($reason)" "$root_ev/$name.judgement.txt" | cut -c7-160))"
  fi
}

journey validate-sat validate-sat.cmd --expect SAT
journey validate-unsat validate-unsat.cmd --expect UNSAT
journey query query.cmd --expect SAT --require 'INFO: 1 : Integer$' --require 'INFO: true : Boolean$'
journey vc vc.cmd --expect NONE --require 'Config sat is valid\.' --require 'Config badbounds is invalid\.' \
  --require 'Rule CLASS_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX is violated\.'

control wrong-verdict "$root_ev/validate-unsat" "expected verdict SAT, got UNSAT" --expect SAT

mkdir -p "$root_ev/no-check"
cp -r "$root_ev/validate-sat/." "$root_ev/no-check/"
grep -v -E '^(checking |checked )' "$root_ev/validate-sat/transcript.txt" >"$root_ev/no-check/transcript.txt"
control no-check "$root_ev/no-check" "check printed no summary" --expect SAT

drive query-no-enable query-no-enable.cmd
control query-no-enable "$root_ev/query-no-enable" "did not compare with a solution" --expect SAT

if [ "$bad" -eq 0 ]; then echo "prove: every journey passed and every control failed; evidence in $root_ev"; else echo "prove: FAILED; evidence in $root_ev"; fi
[ "$bad" -eq 0 ]
