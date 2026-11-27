"""Evidence per corpus class: distinct invariants compared and pairs by USE value.

Usage: python3 -I evidence.py <run dir> <results dir>   Output: <results dir>/evidence.tsv
An invariant is identified by its model file and qualified name, so a model in two cases (its
witness and its validator solution) counts once in the "W+W-+V distinct" row.
"""
import collections
import csv
import os
import sys

run, res = sys.argv[1], sys.argv[2]
models = list(csv.DictReader(open(os.path.join(res, "models.tsv")), delimiter="\t", quoting=csv.QUOTE_NONE))
dis = list(csv.DictReader(open(os.path.join(res, "disagreements.tsv")), delimiter="\t", quoting=csv.QUOTE_NONE))
bad = {(d["case"], d["state"], d["invariant"], d["object"]) for d in dis}
rows = []
distinct = collections.defaultdict(set)
for cls in ("W", "W-", "V", "P"):
    cnt = collections.Counter()
    n_models = 0
    for m in models:
        if m["class"] != cls or int(m["translated"]) == 0:
            continue
        n_models += 1
        tla = os.path.join(run, "cases", m["case"], "tla")
        translated = {r["invariant"] for r in csv.DictReader(open(os.path.join(tla, "invariants.tsv")), delimiter="\t",
                                                             quoting=csv.QUOTE_NONE) if r["status"] == "translated"}
        errs = set()
        ground = list(csv.DictReader(open(os.path.join(tla, "ground.tsv")), delimiter="\t", quoting=csv.QUOTE_NONE))
        for g in ground:
            if g["value"] == "error":
                errs.add((g["state"], g["invariant"]))
        for g in ground:
            if g["invariant"] not in translated:
                continue
            level = "inv" if g["object"] == "*" else "obj"
            if (g["state"], g["invariant"]) in errs and level == "obj":
                continue  # excluded, as in compare.py
            ok = (m["case"], g["state"], g["invariant"], g["object"]) not in bad
            cnt[(level, g["value"], ok)] += 1
        for i in translated:
            distinct[cls].add((m["model"], i))
    row = [cls, n_models, len(distinct[cls])]
    for level in ("inv", "obj"):
        for v in ("true", "false", "undefined", "error"):
            row.append("%d/%d" % (cnt[(level, v, True)], cnt[(level, v, False)]))
    rows.append(row)
union = distinct["W"] | distinct["W-"] | distinct["V"]
rows.append(["W+W-+V distinct", len({m for m, _ in union}), len(union)] + [""] * 8)
with open(os.path.join(res, "evidence.tsv"), "w") as f:
    f.write("\t".join(["class", "models", "distinct_invariants", "inv_true", "inv_false", "inv_undefined", "inv_error",
                       "obj_true", "obj_false", "obj_undefined", "obj_error"]) + "\n")
    f.write("# cells: agree/disagree pairs by USE's value\n")
    for r in rows:
        f.write("\t".join(str(x) for x in r) + "\n")
for r in rows:
    print(*r, sep="\t")
