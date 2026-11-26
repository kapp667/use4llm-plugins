#!/usr/bin/env bash
# Show the comparison can fail: rerun the cases that have translated invariants once per
# deliberate translation bug and count the disagreements each one causes.
# Usage: scripts/mutants.sh [<baseline run dir> [<results dir>]] (defaults: results/run, results)
# Output: <results dir>/mutants.tsv, <results dir>/mutants/<name>/
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base="${1:-$here/results/run}"
res="${2:-$here/results}"
subset="$res/mutants/corpus.tsv"
mkdir -p "$res/mutants"
awk -F'\t' 'NR > 1 && $7 > 0 && $1 != "-" { print $2 }' "$res/models.tsv" | while read -r id; do
  cat "$base/cases/$id/corpus-line.tsv"
done >"$subset"
printf 'mutant\tdescription\tcases\tpairs\tdisagree\tinvariant_level\tobject_level\tin_corpus_cases\tin_probe\tinvariants_hit\n' >"$res/mutants.tsv"
while IFS='|' read -r name descr; do
  out="$res/mutants/$name"
  CORPUS="$subset" TLA_MUTANT="$name" "$here/run.sh" "$out/run" "$out" >"$out.log" 2>&1 || true
  python3 -I - "$out" "$name" "$descr" >>"$res/mutants.tsv" <<'PY'
import csv, sys
out, name, descr = sys.argv[1:4]
rows = list(csv.DictReader(open(out + "/models.tsv"), delimiter="\t"))
dis = list(csv.DictReader(open(out + "/disagreements.tsv"), delimiter="\t"))
pairs = sum(int(r["pairs"]) for r in rows)
inv = sum(1 for d in dis if d["object"] == "*")
probe = sum(1 for d in dis if d["class"] == "P")
hit = len({(d["case"], d["invariant"]) for d in dis})
print("\t".join([name, descr, str(len(rows)), str(pairs), str(len(dis)), str(inv), str(len(dis) - inv),
                 str(len(dis) - probe), str(probe), str(hit)]))
PY
  rm -rf "$out/run/cases"/*/model "$out/run/use"
  echo "$name: $(tail -n 1 "$res/mutants.tsv")"
done <<'LIST'
div|Integer div and mod with TLA+ floor division instead of Java truncation
and|and, or, implies strict in undefined instead of USE's Kleene tables
forall|forAll counting an undefined body as true instead of false
select|select keeping elements whose body is undefined
includes|includes/excludes on an undefined collection undefined instead of false
kindof|oclIsKindOf of an undefined value false instead of true (OclVoid conforms to every type)
excluding|including/excluding strict in the element (the first translator bug found)
collect|Set->collect yielding a set instead of a bag
navundef|navigation from an undefined object to a many-end yielding the empty set instead of undefined
eq|= strict in undefined instead of undefined = undefined being true
if|if with an undefined condition taking the else branch instead of being undefined
LIST
