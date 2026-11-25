#!/usr/bin/env bash
# Judge one oracle case from its raw USE output.
# Usage: ci/oracle/parse.sh <output file> <exit status of the USE run>
# Prints "verdict<TAB>check<TAB>errors<TAB>note", the middle columns of an oracle.tsv row.
set -euo pipefail

[ "$#" -eq 2 ] || { sed -n '2,4p' "$0" >&2; exit 2; }
f="$1"
status="$2"

# The validator logs through log4j as "[HH:mm:ss ]LEVEL: message".
log='^([0-9:]+ )?'

# One line, cut to 200 characters, with tabs and CR characters turned into spaces.
clean() { tr '\t\r' '  ' | cut -c1-200; }

# The first sign that USE's check rejected the state: a line inside the structure check (each one
# is a violation, such as a multiplicity), a failed invariant, or a summary with failures.
check_failure() {
  awk '/^checking structure\.\.\.$/ { s = 1; next }
       /^checked structure in / { s = 0; next }
       s && NF { print; exit }
       /FAILED\.$/ { print; exit }
       /^checked [0-9]+ invariants? .*, [1-9][0-9]* failures?\.$/ { print; exit }' "$f"
}

errors="$(grep -cE "${log}ERROR: " "$f" || true)"
note="$(grep -m1 -E "${log}ERROR: " "$f" | clean || true)"
if [ "$status" -eq 124 ] || [ "$status" -eq 137 ]; then
  verdict=TIMEOUT
elif grep -qE "${log}INFO: (TRIVIALLY_)?SATISFIABLE$" "$f"; then
  verdict=SAT
elif grep -qE "${log}INFO: (TRIVIALLY_)?UNSATISFIABLE$" "$f"; then
  verdict=UNSAT
else
  verdict=ERROR
  # USE reports a model it cannot load as "<file>:<line>:<column>: message", outside log4j.
  [ -n "$note" ] || note="$(grep -m1 -E '\.use:[0-9]+:[0-9]+: ' "$f" | clean || true)"
  [ -n "$note" ] || note="$(grep -v '^[[:space:]]*$' "$f" | tail -n 1 | clean || true)"
fi

check=n/a
if [ "$verdict" = SAT ]; then
  failure="$(check_failure)"
  # A missing summary counts as a failure, so that a check that did not run never passes.
  if ! grep -qE '^checked [0-9]+ invariants? ' "$f"; then
    check=fail
    failure="USE's check printed no summary"
  elif [ -n "$failure" ]; then
    check=fail
  else
    check=ok
  fi
  if [ "$check" = fail ] && [ -z "$note" ]; then note="$(printf '%s\n' "$failure" | clean)"; fi
fi

printf '%s\t%s\t%s\t%s\n' "$verdict" "$check" "$errors" "$note"
