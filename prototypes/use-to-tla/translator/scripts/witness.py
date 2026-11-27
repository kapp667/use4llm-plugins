"""States TLC reaches, as witnesses for the ModelValidator.

Usage:
  python3 -I witness.py reach <spec dir>              writes Reach.tla, Reach.cfg: the spec explored
                                                      only through states where every check holds
  python3 -I witness.py pick <spec dir> <dump> <n> <out dir>
                                                      writes <out dir>/w<i>.cmd for up to n states

ci/oracle/witness.sh derives the validator's bounds from a state: each class exactly its objects,
each association exactly its links, the Integer bounds widened to the integer literals of the
commands, String_max to their distinct string literals. States with the same sizes and literals
give the same bounds, so pick takes one state per distinct bounds key (the first TLC dumped, which
is the shallowest), the largest keys first, and writes the commands that build it in USE, then
"info model", "info state" and "check".
"""
import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ beside the scripts
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tlaval as T  # noqa: E402
import replay as R  # noqa: E402


def reach(spec):
    checks = [l.split("\t")[0] for l in open(os.path.join(spec, "checks.tsv")).read().splitlines()[1:]]
    open(os.path.join(spec, "Reach.tla"), "w").write(
        "---- MODULE Reach ----\nEXTENDS UseOps\n\\* Every check of the spec: the invariants, multiplicities and postconditions.\n"
        "ChecksHold == TRUE%s\n====\n" % "".join(" /\\ " + c for c in checks))
    cfg = [l for l in open(os.path.join(spec, "UseOps.cfg")).read().splitlines()
           if re.match(r"(CONSTANT|INIT|NEXT|CHECK_DEADLOCK|CONSTRAINT)\b", l)]
    open(os.path.join(spec, "Reach.cfg"), "w").write("\n".join(cfg + ["CONSTRAINT ChecksHold"]) + "\n")


def dump_states(path):
    text = open(path, errors="replace").read()
    for block in re.split(r"\n(?=State \d+:)", text):
        if not block.startswith("State "):
            continue
        st = {}
        for part in re.split(r"(?:^|\n)/\\ ", block.split("\n", 1)[1]):
            part = part.strip()
            if part:
                name, val = part.split(" = ", 1)
                st[name.strip()] = T.parse(val.replace("\n", " "))
        yield st["st"]


def commands(state, types):
    live = R.live(state)
    out = ["!create %s : %s" % (o, R.cls_of(state, o)) for o in sorted(live, key=lambda o: R.pool_index(types, o))]
    att = dict(T.field(state, "att")[1])
    for a, typ in types["attribute"].items():
        f = dict(att[("S", a)][1])
        for o in live:
            v = f.get(("M", o))
            if v is not None and v != ("M", "Undef"):
                out.append("!set %s.%s := %s" % (o, a.split("::")[1], R.soil(v, typ, types, lambda x: x)))
    lnk = dict(T.field(state, "lnk")[1])
    for a in types["association"]:
        for t in lnk[("S", a)][1]:
            out.append("!insert (%s) into %s" % (", ".join(x[1] for x in T.seq(t)), a))
    return out


def key(state, cmds):
    live = R.live(state)
    by = {}
    for o in live:
        c = R.cls_of(state, o)
        by[c] = by.get(c, 0) + 1
    links = {a[1]: len(v[1]) for a, v in T.field(state, "lnk")[1]}
    text = "\n".join(cmds)
    ints = [int(x) for x in re.findall(r"(?:^|[^A-Za-z0-9_.'])(-?\d+)\b", re.sub(r"'[^']*'", "''", text))]
    strings = len(set(re.findall(r"'[^']*'", text)))
    return (tuple(sorted(by.items())), tuple(sorted(links.items())), min(ints, default=0), max(ints, default=0), strings)


def pick(spec, dump, n, out):
    types = R.read_types(spec)
    seen, order = {}, []
    total = 0
    for st in dump_states(dump):
        total += 1
        cmds = commands(st, types)
        k = key(st, cmds)
        if k not in seen:
            seen[k] = cmds
            order.append(k)
    first = {k: i for i, k in enumerate(order)}
    order.sort(key=lambda k: (-(sum(c for _, c in k[0]) + sum(c for _, c in k[1])), first[k]))
    os.makedirs(out, exist_ok=True)
    for i, k in enumerate(order[:int(n)], 1):
        open(os.path.join(out, "w%d.cmd" % i), "w").write("\n".join(seen[k] + ["info model", "info state", "check", "exit"]) + "\n")
    print("%d\t%d\t%d" % (total, len(order), min(len(order), int(n))))


if __name__ == "__main__":
    if sys.argv[1] == "reach":
        reach(sys.argv[2])
    else:
        pick(*sys.argv[2:6])
