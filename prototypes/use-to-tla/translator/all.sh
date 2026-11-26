#!/usr/bin/env bash
# The one command that reruns everything: the corpus through USE and TLC (run.sh, writing the
# summary TSVs under results/), then the deliberate-bug mutants (scripts/mutants.sh, writing
# results/mutants.tsv). About 5 + 25 minutes on 4 cores.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$here/run.sh"
"$here/scripts/mutants.sh"
python3 -I "$here/scripts/evidence.py" "$here/results/run" "$here/results"
