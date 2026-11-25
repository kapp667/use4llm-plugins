#!/usr/bin/env bash
# Judge one oracle case from its raw USE output.
# Usage: ci/oracle/parse.sh <output file> <exit status of the USE run>
# Prints "verdict<TAB>check<TAB>objects<TAB>errors<TAB>note", the middle columns of an oracle.tsv row.
set -euo pipefail

[ "$#" -eq 2 ] || { sed -n '2,4p' "$0" >&2; exit 2; }
f="$1"
status="$2"

# The validator logs through log4j as "[HH:mm:ss ]LEVEL: message".
log='^([0-9:]+ )?'

# One line, cut to 200 characters, with tabs and CR characters turned into spaces.
clean() { tr '\t\r' '  ' | cut -c1-200; }

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
objects=n/a
if [ "$verdict" = SAT ]; then
  IFS=$'\t' read -r check objects failure < <("$(dirname "${BASH_SOURCE[0]}")/state.sh" "$f")
  if [ "$check" = fail ] && [ -z "$note" ]; then note="$failure"; fi
fi

printf '%s\t%s\t%s\t%s\t%s\n' "$verdict" "$check" "$objects" "$errors" "$note"
