#!/usr/bin/env bash
# Read the size of the problem and the time of each phase of one validator run from its USE output.
# Usage: ci/oracle/metrics.sh <output file>
# Prints "bitwidth<TAB>atoms<TAB>integers<TAB>variables<TAB>primary<TAB>clauses<TAB>use to kodkod ms<TAB>kodkod to sat ms<TAB>solving ms",
# with n/a for each value the output does not hold, as in a run that stops before solving.
set -euo pipefail

[ "$#" -eq 1 ] || { sed -n '2,5p' "$0" >&2; exit 2; }

# The validator logs through log4j as "[HH:mm:ss ]INFO: message": the bitwidth with the search, the
# universe before solving, the USE to Kodkod time, and the SAT size and times once solved.
awk 'BEGIN { for (i = 1; i <= 9; i++) v[i] = "n/a" }
     { sub(/\r$/, "") }
     /INFO: Searching solution with SatSolver .* and bitwidth [0-9]+/ {
       s = $0; sub(/.* and bitwidth /, "", s); sub(/[^0-9].*/, "", s); v[1] = s
     }
     /INFO: Universe: [0-9]+ atoms, [0-9]+ of them Integer values$/ {
       s = $0; sub(/.*INFO: Universe: /, "", s); split(s, f, " "); v[2] = f[1]; v[3] = f[3]
     }
     /INFO: Translation time \(USE to Kodkod\): [0-9]+ ms$/ { v[7] = $(NF - 1) }
     /INFO: Translation time \(Kodkod to SAT\): [0-9]+ ms; Solving time: [0-9]+ ms; SAT variables: / {
       s = $0; sub(/.*INFO: Translation time \(Kodkod to SAT\): /, "", s)
       gsub(/[^0-9]+/, " ", s); split(s, f, " ")
       v[8] = f[1]; v[9] = f[2]; v[4] = f[3]; v[5] = f[4]; v[6] = f[5]
     }
     END { for (i = 1; i <= 9; i++) printf "%s%s", v[i], (i < 9 ? "\t" : "\n") }' "$1"
