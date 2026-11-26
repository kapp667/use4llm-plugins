"""Compare USE's invariant values (ground.tsv) with TLC's for every corpus case.

Usage: python3 -I compare.py <run dir> <results dir>
Corpus classes: W, a witness replay USE's check accepts (the oracle's definition); W-, a witness
replay USE's check rejects, still a state with objects; V, the ModelValidator's solution for a
model with invariants; P, the semantics probes under probes/. Writes models.tsv, refusals.tsv, agreement.tsv, disagreements.tsv,
errors.tsv, mc.tsv, timing.tsv, translation-coverage.tsv and constructs.tsv to <results dir>
and prints the headline numbers.
"""
import collections
import csv
import os
import re
import sys

run, res = sys.argv[1], sys.argv[2]
cases_dir = os.path.join(run, "cases")
ROW = re.compile(r'^"R\|([^|"]*)\|([^|"]*)\|([^|"]*)\|(true|false|undefined)"$')


def tsv(path):
    if not os.path.exists(path):
        return []
    with open(path, newline="") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def read(path):
    return open(path, errors="replace").read() if os.path.exists(path) else ""


def tlc_error(text):
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.startswith("Error:") or "Assumption" in line and "false" in line or "***Parse Error***" in line \
                or "Semantic errors" in line or "Exception" in line:
            nxt = lines[i + 1].strip() if i + 1 < len(lines) else ""
            return (line.strip() + " " + nxt)[:300]
    return ""


def tlc_values(text):
    vals = {}
    for line in text.splitlines():
        m = ROW.match(line.strip())
        if m:
            vals[(m.group(1), m.group(2), m.group(3))] = m.group(4)
    return vals


models, disagreements, mc_rows, err_rows, timing = [], [], [], [], []
refusals = collections.defaultdict(collections.Counter)  # class -> construct -> invariants
refusal_models = collections.defaultdict(set)
agree = collections.Counter()  # (class, level, use value, outcome) -> count

for cid in sorted(os.listdir(cases_dir)):
    d = os.path.join(cases_dir, cid)
    with open(os.path.join(d, "case.tsv")) as f:
        (_, path, source, status, check, objects, failure, use_s, eval_s, mc_s, viol_s) = f.read().rstrip("\n").split("\t")
    objects = objects.replace(",", "")
    tla = os.path.join(d, "tla")
    ran = os.path.exists(os.path.join(tla, "invariants.tsv"))
    has_objects = objects not in ("0", "unknown")
    if "/probes/" in path and ran:
        cls = "P"
    elif status != "0" or not has_objects or not ran:
        cls = "-"
    elif source == "validator":
        cls = "V"
    else:
        cls = "W" if check == "ok" else "W-"
    invs = tsv(os.path.join(tla, "invariants.tsv"))
    translated = [r["invariant"] for r in invs if r["status"] == "translated"]
    refused = [r for r in invs if r["status"] == "refused"]
    if cls == "-":
        verdict = "not in corpus (exit %s, check %s, %s objects, tla %s)" % (status, check, objects, "ran" if ran else "did not run")
    elif not invs:
        verdict = "no invariants"
    elif not refused:
        verdict = "translated fully"
    elif translated:
        verdict = "translated partially"
    else:
        verdict = "refused"
    if cls != "-":
        for r in refused:
            refusals[cls][r["construct"]] += 1
            refusal_models[(cls, r["construct"])].add(cid)
    eval_text = read(os.path.join(d, "tlc-eval.txt"))
    err = tlc_error(eval_text) if eval_text else ""
    vals = tlc_values(eval_text)
    ground = tsv(os.path.join(tla, "ground.tsv"))
    errs = {(r["state"], r["invariant"]): r for r in tsv(os.path.join(tla, "errors.tsv"))}
    use_err_pairs = {(g["state"], g["invariant"]) for g in ground if g["value"] == "error"}
    n_pairs = n_dis = n_excl = 0
    if cls != "-" and translated:
        tset = set(translated)
        for g in ground:
            if g["invariant"] not in tset:
                continue
            pair = (g["state"], g["invariant"])
            level = "invariant" if g["object"] == "*" else "object"
            if pair in use_err_pairs:
                if level == "object" or pair not in errs:
                    n_excl += 1  # checked at the invariant level in an error module, or beyond the cap
                    continue
                e = errs[pair]
                text = read(os.path.join(d, "tlc-%s.txt" % e["module"]))
                v = tlc_values(text).get((g["state"], g["invariant"], "*"))
                t = v if v else ("error" if "multiplicity violation" in text or "Assert" in text else "tlc failed")
                err_rows.append([cid, g["state"], g["invariant"], g["value"], t, tlc_error(text)])
            else:
                t = vals.pop((g["state"], g["invariant"], g["object"]), "missing")
            n_pairs += 1
            outcome = "agree" if t == g["value"] else "disagree"
            agree[(cls, level, g["value"], outcome)] += 1
            if outcome == "disagree":
                n_dis += 1
                disagreements.append([cls, cid, g["state"], g["invariant"], g["object"], g["value"], t, err])
        for key, t in sorted(vals.items()):  # printed by TLC but absent from USE's ground truth
            level = "invariant" if key[2] == "*" else "object"
            agree[(cls, level, "absent", "disagree")] += 1
            n_dis += 1
            disagreements.append([cls, cid, key[0], key[1], key[2], "absent", t, err])
        # Model-checking shape.
        mc = read(os.path.join(d, "tlc-mc.txt"))
        mc_ok = "Model checking completed. No error has been found." in mc
        mc_states = re.search(r"(\d+) distinct states found", mc)
        viol = read(os.path.join(d, "tlc-viol.txt"))
        vm = re.search(r"^Init == st = (S\d+)$", read(os.path.join(tla, "UseMCViol.tla")), re.M)
        viol_state = vm.group(1) if vm else ""
        names = {r["operator"]: r["invariant"] for r in tsv(os.path.join(tla, "mc-invariants.tsv"))}
        vv = re.search(r"Error: Invariant (MCInv_\d+) is violated", viol)
        reported = names.get(vv.group(1), "") if vv else ""
        use_false = {g["invariant"] for g in ground if g["state"] == viol_state and g["object"] == "*" and g["value"] == "false"}
        s0_truth = {g["invariant"]: g["value"] for g in ground if g["state"] == "S0" and g["object"] == "*"}
        s0_all_true = all(s0_truth.get(i) == "true" for i in translated)
        mc_rows.append([cls, cid, "no violation" if mc_ok else ("violation: " + tlc_error(mc)),
                        "yes" if s0_all_true else "no", mc_states.group(1) if mc_states else "?", mc_s,
                        viol_state or "none", reported or ("no violation reported" if viol_state else ""),
                        ("yes" if reported in use_false else "NO") if viol_state else "", viol_s])
        tm = tsv(os.path.join(tla, "timing.tsv"))
        timing.append([cls, cid, len(translated), objects, eval_s, mc_s, viol_s,
                       tm[0]["use_ms"] if tm else "", tm[0]["translate_ms"] if tm else ""])
    models.append([cls, cid, path, source, verdict, len(invs), len(translated), len(refused), n_pairs, n_dis, n_excl,
                   use_s, eval_s, err])

os.makedirs(res, exist_ok=True)


def write(name, header, rows):
    with open(os.path.join(res, name), "w") as f:
        f.write("\t".join(header) + "\n")
        for r in rows:
            f.write("\t".join(str(x) for x in r) + "\n")


write("models.tsv", ["class", "case", "model", "source", "verdict", "invariants", "translated", "refused", "pairs",
                     "disagree", "excluded", "use_seconds", "tlc_eval_seconds", "tlc_error"], models)
rows = []
for cls in ("W", "W-", "V", "P"):
    for c, n in refusals[cls].most_common():
        rows.append([cls, c, n, len(refusal_models[(cls, c)])])
write("refusals.tsv", ["class", "construct", "invariants", "models"], rows)
rows = []
for cls in ("W", "W-", "V", "P"):
    for lv in ("invariant", "object"):
        for uv in ("true", "false", "undefined", "error", "absent"):
            a, b = agree[(cls, lv, uv, "agree")], agree[(cls, lv, uv, "disagree")]
            if a or b:
                rows.append([cls, lv, uv, a, b])
write("agreement.tsv", ["class", "level", "use_value", "agree", "disagree"], rows)
write("disagreements.tsv", ["class", "case", "state", "invariant", "object", "use", "tlc", "tlc_error"], disagreements)
write("errors.tsv", ["case", "state", "invariant", "use", "tlc", "tlc_message"], err_rows)
write("mc.tsv", ["class", "case", "S0_result", "S0_all_true_in_USE", "S0_distinct_states", "S0_seconds", "violating_state",
                 "tlc_reported", "reported_is_use_false", "viol_seconds"], mc_rows)
write("timing.tsv", ["class", "case", "translated_invariants", "objects_S0", "tlc_eval_seconds", "tlc_mc_seconds",
                     "tlc_viol_seconds", "plugin_use_ms", "plugin_translate_ms"], timing)

# Translation coverage over the distinct models whose invariants reached the translator, in any
# case (a validator case translates even when the validator finds no state), and the constructs
# the translated invariants exercise in the corpus cases.
by_model, constructs = {}, collections.defaultdict(set)
for m in models:
    cid, path = m[1], m[2]
    invs = tsv(os.path.join(cases_dir, cid, "tla", "invariants.tsv"))
    if invs:
        by_model.setdefault(path, invs)
    if m[0] != "-" and m[6] > 0:
        for line in read(os.path.join(cases_dir, cid, "tla", "constructs.txt")).split():
            constructs[line].add((m[0], cid))
hist, hist_models = collections.Counter(), collections.defaultdict(set)
cov = collections.Counter()
for path, invs in by_model.items():
    t = sum(1 for i in invs if i["status"] == "translated")
    cov["models"] += 1
    cov["invariants"] += len(invs)
    cov["translated"] += t
    cov["fully" if t == len(invs) else "partially" if t else "none"] += 1
    for i in invs:
        if i["status"] == "refused":
            hist[i["construct"]] += 1
            hist_models[i["construct"]].add(path)
write("translation-coverage.tsv", ["construct", "invariants", "models"],
      [["(models with invariants)", cov["invariants"], cov["models"]],
       ["(translated)", cov["translated"], "%d fully, %d partially, %d none" % (cov["fully"], cov["partially"], cov["none"])]]
      + [[c, n, len(hist_models[c])] for c, n in hist.most_common()])
write("constructs.tsv", ["construct", "cases", "by_class"],
      [[c, len(s), " ".join("%s:%d" % kv for kv in sorted(collections.Counter(k for k, _ in s).items()))]
       for c, s in sorted(constructs.items(), key=lambda x: (-len(x[1]), x[0]))])

print("translation coverage:", dict(cov))
for cls in ("W", "W-", "V", "P"):
    ms = [m for m in models if m[0] == cls]
    print(cls, "models:", len(ms), dict(collections.Counter(m[4] for m in ms)))
    print(cls, "refusals:", refusals[cls].most_common())
    for r in [r for r in rows if r[0] == cls]:
        print(" ", r)
print("not in corpus:", sum(1 for m in models if m[0] == "-"))
print("disagreements:", len(disagreements), "error checks:", len(err_rows))
