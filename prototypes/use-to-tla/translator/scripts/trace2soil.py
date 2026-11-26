"""Turn a TLC counterexample of a tlaops spec into USE shell commands that replay it.

Usage: python3 -I trace2soil.py <trace.json> <spec.cfg>
trace.json is TLC's -dumpTrace json output. Each step's `last` names what the environment did:
new C, a call, or new C and a call. Prints one command per line, then `check`.
Values: the cfg's object model values become object names, Undef becomes null, an "E::lit" string
whose E is an enumeration of the trace becomes the literal. A String argument that looks like one
of these is ambiguous: this prototype does not know the parameter types (B4 must).
"""
import json
import re
import sys

trace, cfg = sys.argv[1], sys.argv[2]
objs = set(re.findall(r"^CONSTANT (O_\w+) = ", open(cfg).read(), re.M))
steps = [a for a in json.load(open(trace))["counterexample"]["action"]]


def value(v):
    if v is True or v is False:
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if v == "Undef":
        return "null"
    if v in objs:
        return v
    if re.fullmatch(r"[A-Za-z_]\w*::\w+", v):
        return v
    return "'" + v.replace("'", "\\'") + "'"


out = []
for a in steps:
    # Each action is [[i, state i], action, [i + 1, state i + 1]].
    last, st = a[2][1]["last"], a[2][1]["st"]
    if last["step"] == "none":
        continue
    if last["step"] in ("new", "new+call"):
        cls = st["cls"][last["self"]]
        out.append("!create %s : %s" % (last["self"], cls))
    if last["step"] in ("call", "new+call"):
        op = last["op"].split("::")[1]
        out.append("!%s.%s(%s)" % (last["self"], op, ", ".join(value(x) for x in last["args"])))
for line in out:
    print(line)
print("check")
