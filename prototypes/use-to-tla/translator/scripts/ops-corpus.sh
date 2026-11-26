#!/usr/bin/env bash
# Translate the operations of every corpus model with a SOIL body under the default settings
# (Settings.defaults) and run TLC twice: with every check, for the first violation, and with none,
# for the size of the bounded state space.
# Usage: scripts/ops-corpus.sh <out dir> <results dir> [<model root>]
#   model root: translate every model with a SOIL body under it instead of the two corpora
# Output: <results dir>/ops-corpus.tsv (one row per model) and ops-refusals.tsv (operations and
# steps refused, by construct). Needs USE_HOME, USE_SRC and TLA2TOOLS. JOBS (4) runs in parallel,
# TLC_TIMEOUT (120 s) bounds each TLC run.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="${1:?out dir}"; res="${2:?results dir}"
use="${USE_HOME:?set USE_HOME}"; : "${USE_SRC:?set USE_SRC}" "${TLA2TOOLS:?set TLA2TOOLS}"
export TLA2TOOLS
"$here/build.sh" "$use" >/dev/null
rm -rf "$out"; mkdir -p "$out/use" "$res"
cp -r "$use/lib" "$use/etc" "$use/oclextensions" "$out/use/"
rm -f "$out/use/lib/plugins"/ModelValidatorPlugin-*.jar
cp "$here/build/use2tla.jar" "$out/use/lib/plugins/"
if [ -n "${3:-}" ]; then
  (cd "$3" && grep -rl --include='*.use' -w begin . | sed 's|^\./||' | LC_ALL=C sort | sed "s|^|$3\t|") >"$out/models.tsv"
else
  {
    (cd "$use/examples" && grep -rl --include='*.use' -w begin . | sed 's|^\./||' | LC_ALL=C sort | sed "s|^|$use/examples\t|")
    (cd "$USE_SRC" && grep -rl --include='*.use' -w begin use-core/src/test/resources use-gui/src/it/resources | LC_ALL=C sort | sed "s|^|$USE_SRC\t|")
  } >"$out/models.tsv"
fi
echo "candidates: $(wc -l <"$out/models.tsv") models with a 'begin'"

one() { # <root> <model>
  local root="$1" model="$2" id d s t
  id="$(printf '%s' "$model" | sed 's|[^A-Za-z0-9._-]|_|g')"
  d="$out/cases/$id"; rm -rf "$d"; mkdir -p "$d/model"
  cp -r "$root/$(dirname "$model")/." "$d/model/"
  printf 'tlaops default %s\nexit\n' "$d/tla" >"$d/cmd"
  (cd "$d/model" && timeout 120 java -Djava.awt.headless=true -jar "$out/use/lib/use-gui.jar" -nogui -nr "$(basename "$model")" "$d/cmd" \
    </dev/null >"$d/use.txt" 2>&1) || true
  printf '%s\t%s\n' "$root" "$model" >"$d/model.tsv"
  [ -f "$d/tla/UseOps.tla" ] || return 0
  grep -q $'^operation\t' "$d/tla/ops.tsv" || return 0
  grep -v '^INVARIANT ' "$d/tla/UseOps.cfg" >"$d/tla/Size.cfg"
  for run in checks:UseOps size:Size; do
    s="$EPOCHREALTIME"
    (cd "$d/tla" && timeout --kill-after=10 "${TLC_TIMEOUT:-120}" java -XX:+UseParallelGC -Xss512m -cp "$TLA2TOOLS" tlc2.TLC -workers 1 \
      -metadir "$d/tla/states-${run%%:*}" -config "${run##*:}.cfg" UseOps </dev/null >"$d/tlc-${run%%:*}.txt" 2>&1) \
      || echo "exit $?" >>"$d/tlc-${run%%:*}.txt"
    t="$(awk -v a="$s" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.1f", b - a }')"
    echo "$t" >"$d/tlc-${run%%:*}.seconds"
  done
  echo "$id done"
}
export -f one; export out
tr '\t' '\n' <"$out/models.tsv" | xargs -d '\n' -n 2 -P "${JOBS:-4}" bash -c 'one "$0" "$1"'
python3 -I - "$out" "$res" <<'PY'
import collections, csv, os, re, sys
out, res = sys.argv[1], sys.argv[2]
rows, hist, hist_models = [], collections.Counter(), collections.defaultdict(set)
def tlc(path):
    t = open(path, errors="replace").read() if os.path.exists(path) else ""
    m = re.search(r"Error: Invariant (\w+) is violated", t)
    states = re.search(r"([\d,]+) distinct states found", t)
    depth = re.search(r"depth of the complete state graph search is (\d+)", t)
    if m: r = "violated " + m.group(1)
    elif "Model checking completed. No error has been found." in t: r = "no violation"
    elif re.search(r"^exit 124", t, re.M): r = "timeout"
    else:
        e = re.search(r"^(Error:.*|.*Exception.*)$", t, re.M)
        r = "TLC error: " + (e.group(1)[:120] if e else "?")
    return r, states.group(1).replace(",", "") if states else "", depth.group(1) if depth else ""
for cid in sorted(os.listdir(os.path.join(out, "cases"))):
    d = os.path.join(out, "cases", cid)
    root, model = open(os.path.join(d, "model.tsv")).read().rstrip("\n").split("\t")
    ops_tsv = os.path.join(d, "tla", "ops.tsv")
    if not os.path.exists(ops_tsv):
        use = open(os.path.join(d, "use.txt"), errors="replace").read()
        if "tlaops:" not in use:
            rows.append([model, "model did not load"] + [""] * 11)
        continue
    rep = list(csv.DictReader(open(ops_tsv), delimiter="\t", quoting=csv.QUOTE_NONE))
    ops = [r for r in rep if r["kind"] == "operation"]
    if not ops:
        continue  # no operation with a SOIL body reached by a step
    def count(kind):
        rs = [r for r in rep if r["kind"] == kind]
        return "%d/%d" % (sum(r["status"] == "translated" for r in rs), len(rs))
    for r in rep:
        if r["kind"] in ("operation", "step") and r["status"] == "refused" and not (r["kind"] == "step" and r["name"].startswith("call ")):
            hist[(r["kind"], r["construct"])] += 1
            hist_models[(r["kind"], r["construct"])].add(model)
    chk, chk_states, chk_depth = tlc(os.path.join(d, "tlc-checks.txt"))
    size, size_states, _ = tlc(os.path.join(d, "tlc-size.txt"))
    sec = lambda n: open(os.path.join(d, "tlc-%s.seconds" % n)).read().strip() if os.path.exists(os.path.join(d, "tlc-%s.seconds" % n)) else ""
    refused = sorted({r["construct"] for r in ops if r["status"] == "refused"})
    rows.append([model, "translated", count("operation"), count("step"), count("invariant"), count("multiplicity"), count("postcondition"),
                 chk, chk_depth, sec("checks"), size, size_states, sec("size"), "; ".join(refused)])
with open(os.path.join(res, "ops-corpus.tsv"), "w") as f:
    f.write("\t".join(["model", "load", "operations", "steps", "invariants", "multiplicities", "postconditions", "tlc_checks", "depth",
                       "checks_seconds", "tlc_size", "distinct_states", "size_seconds", "operation_refusals"]) + "\n")
    for r in rows:
        f.write("\t".join(str(x) for x in r) + "\n")
with open(os.path.join(res, "ops-refusals.tsv"), "w") as f:
    f.write("kind\tconstruct\tcount\tmodels\n")
    for (k, c), n in hist.most_common():
        f.write("%s\t%s\t%d\t%d\n" % (k, c, n, len(hist_models[(k, c)])))
print(open(os.path.join(res, "ops-corpus.tsv")).read())
print(open(os.path.join(res, "ops-refusals.tsv")).read())
PY
