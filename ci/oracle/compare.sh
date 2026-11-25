#!/usr/bin/env bash
# Compare the verdicts of an oracle run with the expected ones committed for its corpus.
# Usage: ci/oracle/compare.sh <expected verdicts file> <oracle.tsv of the run>
# Both files hold the model, bounds, verdict and check columns of oracle.tsv; the run's other
# columns are ignored. Prints each row that differs, "-" for the expected one and "+" for the
# run's, and exits 1 if any does.
# After a change that rightly changes verdicts, write the new expected verdicts with
#   cut -f1-4 <oracle.tsv of the run> > <expected verdicts file>
set -euo pipefail

[ "$#" -eq 2 ] || { sed -n '2,8p' "$0" >&2; exit 2; }
[ -f "$2" ] || { echo "compare.sh: $2 not found" >&2; exit 1; }

if ! diff --unchanged-line-format= --old-line-format='-%L' --new-line-format='+%L' \
    "$1" <(cut -f1-4 "$2"); then
  echo "compare.sh: the verdicts of $2 differ from $1" >&2
  exit 1
fi
