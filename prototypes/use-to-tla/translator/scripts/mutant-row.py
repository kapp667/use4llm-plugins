"""One row of ops-mutants.tsv: which behaviour checks a mutant's oracle run catches.

Usage: python3 -I mutant-row.py <unmutated results dir> <mutant results dir> <name> <description> <applied>

A check catches the mutant when one of its rows gets worse than in the unmutated run: from an
accepted result (agree, not reached, the negative control caught) to any other, or from one
failure to another. A row that is missing (an operation no longer translated, so no cover run)
is a change of the translation that no oracle judged: it is named, but it catches nothing. For
civstat, any difference of TLC's verdict, state count, depth or USE's replay (the hand spec is the
oracle). corpus_changed says that ops-corpus.tsv differs (TLC's verdicts or state counts), which
no oracle judges. A mutant applied 0 times changed no translation: no model exercises it.
"""
import csv
import os
import sys

base, mut, name, descr, applied = sys.argv[1:6]
signs = []


def rows(d, f):
    p = os.path.join(d, f)
    return list(csv.DictReader(open(p), delimiter="\t", quoting=csv.QUOTE_NONE)) if os.path.exists(p) else None


def check(f, key, ok=lambda v: v in ("agree", "not reached", "negative control caught")):
    b, m = rows(base, f), rows(mut, f)
    if b is None and m is None:
        return "n/a"
    if m is None:
        signs.append(f + " missing")
        return "caught"
    bk = {tuple(r[k] for k in key): r for r in b or []}
    mk = {tuple(r[k] for k in key): r for r in m}
    missing = [k for k in bk if k not in mk]
    if missing:
        signs.append("%s %s: row missing (no catch)" % (f, "/".join(missing[0])))
    for k, br in bk.items():
        mr = mk.get(k)
        if mr is None:
            continue
        bv, mv = br["result"], mr["result"]
        if not ok(mv) and (ok(bv) or bv != mv):
            signs.append("%s %s: %s %s" % (f, "/".join(k), mv, mr.get("detail", mr.get("note", ""))))
            return "caught"
    return "-"


def civstat():
    cols = ["run", "tlc", "distinct_states", "depth", "USE replay"]
    b = [tuple(r[c] for c in cols) for r in rows(base, "ops-civstat.tsv") or []]
    m = [tuple(r[c] for c in cols) for r in rows(mut, "ops-civstat.tsv") or []]
    if b != m:
        d = [x for x in m if x not in b]
        signs.append("civstat: " + (" ".join(d[0]) if d else "rows missing"))
        return "caught"
    return "-"


c = civstat()
rep = check("ops-replays.tsv", ["case", "run"])
scn = check("ops-scenarios.tsv", ["scenario"])
wit = check("ops-witnesses.tsv", ["spec", "witness"], ok=lambda v: v != "disagree")
prb = check("ops-probe-replays.tsv", ["case", "run"])
cols = ["model", "tlc_checks", "distinct_states", "tlc_size"]
cb, cm = rows(base, "ops-corpus.tsv"), rows(mut, "ops-corpus.tsv")
changed = "changed" if [tuple(r[x] for x in cols) for r in cb or []] != [tuple(r[x] for x in cols) for r in cm or []] else "-"
caught = "yes" if "caught" in (c, rep, scn, wit, prb) else ("not exercised" if applied == "0" else "no")
print("\t".join([name, descr, applied, c, rep, scn, wit, prb, changed, caught, " | ".join(signs)[:400].replace("\t", " ")]))
