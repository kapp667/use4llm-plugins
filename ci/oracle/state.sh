#!/usr/bin/env bash
# Judge the object diagram of a USE run from the output of its "info state" and "check" commands.
# Usage: ci/oracle/state.sh <output file>
# Prints "check<TAB>objects<TAB>failure": ok or fail, the number of objects "info state" counted
# (unknown if it printed none), and the first sign of a failure, empty when the check passed.
set -euo pipefail

[ "$#" -eq 1 ] || { sed -n '2,5p' "$0" >&2; exit 2; }
f="$1"

# The total of USE's "info state" class table.
objects="$(awk '/^class +: #objects/ { c = 1 } c && /^total +:/ { print $3; exit }' "$f")"
[ -n "$objects" ] || objects=unknown

# The first sign that USE's check rejected the state: a line inside the structure check (each one
# is a violation, such as a multiplicity), a failed invariant, or a summary with failures.
failure="$(awk '/^checking structure\.\.\.$/ { s = 1; next }
                /^checked structure in / { s = 0; next }
                s && NF { print; exit }
                /FAILED\.$/ { print; exit }
                /^checked [0-9]+ invariants? .*, [1-9][0-9]* failures?\.$/ { print; exit }' "$f" | tr '\t\r' '  ' | cut -c1-200)"

# A missing summary counts as a failure, so that a check that did not run never passes.
if ! grep -qE '^checked [0-9]+ invariants? ' "$f"; then
  check=fail
  failure="USE's check printed no summary"
elif [ -n "$failure" ]; then
  check=fail
else
  check=ok
fi

printf '%s\t%s\t%s\n' "$check" "$objects" "$failure"
