#!/usr/bin/env bash
# The one command that reruns everything: the corpus through USE and TLC (run.sh, writing the
# summary TSVs), then the deliberate-bug mutants (scripts/mutants.sh, writing mutants.tsv).
# Usage: all.sh [<out dir> [<results dir>]] (defaults: results/run, results).
# About 5 + 25 minutes on 4 cores.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
out="${1:-$here/results/run}"
res="${2:-$here/results}"
"$here/run.sh" "$out" "$res"
"$here/scripts/mutants.sh" "$out" "$res"
python3 -I "$here/scripts/evidence.py" "$out" "$res"
python3 -I "$here/scripts/relpaths.py" "$res"/*.tsv
