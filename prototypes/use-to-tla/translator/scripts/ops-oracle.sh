#!/usr/bin/env bash
# The behaviour oracle of tlaops, both directions, in one run:
#   civstat     ops-probe/check-generated.sh: the generated spec against the hand spec, and USE's
#               replay of TLC's three counterexamples
#   corpus      scripts/ops-corpus.sh: the corpus models with a SOIL body, default settings
#   replays     scripts/ops-replays.sh: TLC's behaviours replayed in USE, state by state
#   scenarios   scripts/ops-scenarios.sh: USE's scenarios followed by TLC with the spec's steps
#   witnesses   scripts/ops-witnesses.sh: states TLC reaches, solved by the ModelValidator
# The probe models of action-probes, when there are any, go through corpus, replays and witnesses
# too, apart (ops-probe-*.tsv), and through scenarios with the others.
# Usage: scripts/ops-oracle.sh <out dir> <results dir>
# Output: <results dir>/ops-oracle.tsv (one row per part: rows, agree, the other results, seconds)
# and the parts' TSVs. Needs USE_HOME, USE_SRC, MV_JAR and TLA2TOOLS. TLA_OPS_MUTANT names a
# deliberate bug of the action translation (Ops.MUTANT), applied to every tlaops run.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${1:?out dir}"; res="${2:?results dir}"
: "${USE_HOME:?set USE_HOME}" "${USE_SRC:?set USE_SRC}" "${MV_JAR:?set MV_JAR}" "${TLA2TOOLS:?set TLA2TOOLS}"
rm -rf "$out"; mkdir -p "$out" "$res"
probes="$here/action-probes"
secs=()
part() { # <name> <command...>: run, log, time
  local name="$1" s="$EPOCHREALTIME"; shift
  "$@" >"$out/$name.log" 2>&1 || { echo "$name failed, see $out/$name.log" >&2; tail -5 "$out/$name.log" >&2; }
  secs+=("$name=$(awk -v a="$s" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.0f", b - a }')")
  echo "$name: $(tail -n 4 "$out/$name.log" | tr '\n' ' ')"
}
part civstat "$here/../ops-probe/check-generated.sh" "$out/civstat"
cp "$out/civstat/civstat-checks.tsv" "$res/ops-civstat.tsv" 2>/dev/null || true
part corpus "$here/scripts/ops-corpus.sh" "$out/corpus" "$res"
part replays "$here/scripts/ops-replays.sh" "$out/corpus" "$res"
specs="$out/witness-specs.tsv"
printf 'civstat atomic-birth\t%s\t%s\n' "$out/civstat/atomic" "$out/civstat/civstat.use" >"$specs"
withops() { # <ops-corpus out dir>: the cases with a translated operation, as witness specs
  for d in "$1"/cases/*/; do
    d="${d%/}"
    [ -f "$d/tla/ops.tsv" ] && awk -F'\t' '$1 == "operation" && $3 == "translated" { f = 1 } END { exit !f }' "$d/tla/ops.tsv" || continue
    printf '%s\t%s\t%s\n' "$(basename "$d")" "$d/tla" "$d/model/$(basename "$(cut -f2 "$d/model.tsv")")"
  done
}
withops "$out/corpus" >>"$specs"
if compgen -G "$probes/*.use" >/dev/null; then
  mkdir -p "$out/probe-res"
  part probe-corpus "$here/scripts/ops-corpus.sh" "$out/probes" "$out/probe-res" "$probes"
  part probe-replays "$here/scripts/ops-replays.sh" "$out/probes" "$out/probe-res"
  cp "$out/probe-res/ops-corpus.tsv" "$res/ops-probe-corpus.tsv"; cp "$out/probe-res/ops-replays.tsv" "$res/ops-probe-replays.tsv"
  withops "$out/probes" >>"$specs"
fi
part scenarios "$here/scripts/ops-scenarios.sh" "$out/scenarios" "$res"
part witnesses "$here/scripts/ops-witnesses.sh" "$out/witnesses" "$res" "$specs"
python3 -I - "$res" "${secs[@]}" <<'PY'
import csv, collections, os, sys
res, secs = sys.argv[1], dict(a.split("=") for a in sys.argv[2:])
def table(name, col):
    p = os.path.join(res, name)
    if not os.path.exists(p):
        return None
    rows = list(csv.DictReader(open(p), delimiter="\t", quoting=csv.QUOTE_NONE))
    if name == "ops-civstat.tsv":  # the rows with a replay: agree when USE and TLC agree
        return collections.Counter("agree" if r[col].endswith("; agree") else "disagree" for r in rows if r[col])
    return collections.Counter(r[col] for r in rows)
parts = [("civstat", "ops-civstat.tsv", "USE replay"), ("corpus", "ops-corpus.tsv", "load"), ("replays", "ops-replays.tsv", "result"),
         ("probe-replays", "ops-probe-replays.tsv", "result"), ("scenarios", "ops-scenarios.tsv", "result"),
         ("witnesses", "ops-witnesses.tsv", "result")]
with open(os.path.join(res, "ops-oracle.tsv"), "w") as f:
    f.write("part\trows\tagree\tother\tseconds\n")
    for name, tsv, col in parts:
        c = table(tsv, col) if tsv else None
        if tsv and c is None:
            continue
        other = "; ".join("%s %d" % kv for kv in sorted(c.items()) if kv[0] != "agree") if c else ""
        f.write("%s\t%s\t%s\t%s\t%s\n" % (name, sum(c.values()) if c else "", c.get("agree", "") if c else "", other,
                                          secs.get(name, "") if name != "probe-replays" else
                                          str(int(secs.get("probe-corpus", 0)) + int(secs.get("probe-replays", 0)))))
    f.write("total\t\t\t\t%d\n" % sum(int(v) for v in secs.values()))
print(open(os.path.join(res, "ops-oracle.tsv")).read())
PY
python3 -I "$here/scripts/relpaths.py" "$res"/ops-*.tsv
sed -i "s|$out/|<out>/|g; s|$here/|translator/|g" "$res"/ops-*.tsv
