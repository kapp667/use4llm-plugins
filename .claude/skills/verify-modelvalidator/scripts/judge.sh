#!/usr/bin/env bash
# Judge one drive.sh run. Exits 0 only when every expectation holds; anything else is FAIL, so a
# wrong or missing verdict cannot pass silently.
# Usage: judge.sh <evidence dir> --expect SAT|UNSAT|NONE [options]
#   --expect SAT      one SATISFIABLE verdict, then USE's check must accept the solution with 0 failures
#                     and "info state" must count at least one object
#   --expect UNSAT    one UNSATISFIABLE verdict. USE's check is not consulted: after UNSAT the diagram is
#                     empty and check passes on it, which proves nothing
#   --expect NONE     no verdict line at all (mv -vc, mv ?, mv -config)
#   --require REGEX   some transcript line matches this extended regex (repeatable)
#   --forbid REGEX    no transcript line matches (repeatable)
#   --allow REGEX     an ERROR or WARN line that matches is not a defect (repeatable); use it only for a
#                     message the journey provokes on purpose, and pair it with --require
#   --allow-empty     with SAT, accept a solution that has no object
#   --verdicts N      the number of verdict lines expected (default 1 with SAT or UNSAT, 0 with NONE)
# Always failing, whatever the options: an exit status of USE other than 0 (124 and 137 are a timeout),
# a Java exception, an ERROR line, a WARN line that neither known-warnings.txt nor --allow names, and
# "Not compared with a solution" (mv ? did not compare, which is not a pass).
# Writes the same report to <evidence dir>/judgement.txt.
set -euo pipefail

[ "$#" -ge 3 ] || { sed -n '2,20p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
oracle="$root/ci/oracle"
ev="$(cd "$1" && pwd)"
shift
t="$ev/transcript.txt"
[ -f "$t" ] && [ -f "$ev/status" ] || { echo "FAIL: $ev holds no transcript.txt and status; run drive.sh" >&2; exit 1; }

expect=""
verdicts=""
allow_empty=0
requires=()
forbids=()
allows=()
while [ "$#" -gt 0 ]; do
  case "$1" in
    --expect) expect="${2:?}"; shift 2 ;;
    --require) requires+=("${2:?}"); shift 2 ;;
    --forbid) forbids+=("${2:?}"); shift 2 ;;
    --allow) allows+=("${2:?}"); shift 2 ;;
    --allow-empty) allow_empty=1; shift ;;
    --verdicts) verdicts="${2:?}"; shift 2 ;;
    *) echo "unknown option $1" >&2; exit 2 ;;
  esac
done
case "$expect" in SAT|UNSAT|NONE) ;; *) echo "--expect SAT|UNSAT|NONE is required" >&2; exit 2 ;; esac
if [ -z "$verdicts" ]; then
  if [ "$expect" = NONE ]; then verdicts=0; else verdicts=1; fi
fi

problems=()
fail() { problems+=("$1"); }
status="$(cat "$ev/status")"
log='^([0-9:]+ )?'

if [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then fail "USE timed out (exit status $status)"
elif [ "$status" -ne 0 ]; then fail "USE exited with status $status"; fi

# The verdicts, counted line by line rather than taken from the first match.
nverdicts="$(grep -cE "${log}INFO: (TRIVIALLY_)?(UN)?SATISFIABLE$" "$t" || true)"
[ "$nverdicts" -eq "$verdicts" ] || fail "expected $verdicts verdict line(s), found $nverdicts"

IFS=$'\t' read -r verdict check objects errors note < <("$oracle/parse.sh" "$t" "$status")
shown_verdict="$verdict"
if [ "$expect" = NONE ]; then
  [ "$nverdicts" -ne 0 ] || shown_verdict=none
elif [ "$verdict" != "$expect" ]; then
  fail "expected verdict $expect, got $verdict"
fi

if [ "$expect" = SAT ] && [ "$verdict" = SAT ]; then
  [ "$check" = ok ] || fail "USE's check did not accept the solution: $note"
  if [ "$objects" = 0 ] || [ "$objects" = unknown ]; then
    [ "$allow_empty" -eq 1 ] || fail "the solution has no object ($objects); a check on an empty state proves nothing"
  fi
fi

# Defect signals: every ERROR and WARN line must be named by --allow or by known-warnings.txt.
known=()
while IFS= read -r line; do
  case "$line" in ''|'#'*) ;; *) known+=("$line") ;; esac
done <"$here/../known-warnings.txt"
named() { # line, patterns...
  local line="$1" p
  shift
  for p in "$@"; do if grep -qE -- "$p" <<<"$line"; then return 0; fi; done
  return 1
}
nknown=0
while IFS= read -r line; do
  if named "$line" ${allows[@]+"${allows[@]}"}; then continue; fi
  if named "$line" "${known[@]}"; then nknown=$((nknown + 1)); continue; fi
  fail "defect signal: $(tr '\t\r' '  ' <<<"$line" | cut -c1-200)"
done < <(grep -E "${log}(ERROR|WARN): " "$t" || true)

if grep -qE '^\s*(Exception in thread|at [a-z]+\.[A-Za-z0-9_.$]+\(.*\.java:[0-9]+\))|java\.lang\.[A-Za-z]*(Exception|Error)' "$t"; then
  fail "a Java exception or stack trace is in the transcript"
fi
if grep -q 'Not compared with a solution' "$t"; then
  fail "mv ? did not compare with a solution: $(grep -m1 'Not compared with a solution' "$t" | cut -c1-200)"
fi

for p in ${requires[@]+"${requires[@]}"}; do
  grep -qE -- "$p" "$t" || fail "no line matches the required pattern: $p"
done
for p in ${forbids[@]+"${forbids[@]}"}; do
  if grep -qE -- "$p" "$t"; then fail "a line matches the forbidden pattern: $p"; fi
done

report="$ev/judgement.txt"
{
  echo "transcript: $t"
  echo "expected: $expect; verdict: $shown_verdict; verdict lines: $nverdicts; check: $check; objects: $objects; ERROR lines: $errors; known environment WARN lines: $nknown; USE exit status: $status"
  if [ "${#problems[@]}" -eq 0 ]; then
    echo "PASS"
  else
    for p in "${problems[@]}"; do echo "FAIL: $p"; done
  fi
} | tee "$report"
[ "${#problems[@]}" -eq 0 ]
