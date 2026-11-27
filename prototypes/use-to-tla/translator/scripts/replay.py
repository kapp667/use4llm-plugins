"""Replay a TLC error trace of a tlaops spec in USE, step by step, and compare.

Usage:
  python3 -I replay.py commands <tlc log> <spec dir> <dump file> > replay.cmd
  python3 -I replay.py compare <tlc log> <spec dir> <dump file> <USE output> <cfg> <run name>
    prints one TSV row (see HEADER)

commands writes the USE commands that take the trace's steps: "new C" creates the pool object
under its own name, a call calls the operation with the arguments written for their parameter
types (types.tsv), "new C call op" does both. After each step, infotla dumps USE's state and
"check" judges it. A trace ending on a violated postcondition ends with "c", since USE pauses
there and runs only "info" commands, infotla among them.

Objects a body creates get a SOIL variable named after their pool object, and USE's state is
read with them mapped to it (on the last step, by the bijection that makes the states equal).

compare checks, at each step: USE ran the step without an error (but the last one, when its
postcondition fails), USE's state equals TLC's (the live objects, their classes, the attributes
and associations the spec's state holds), and so does the result of a call (counted with the
states), the invariants and multiplicities the cfg checks hold
in USE where TLC checked them, and the check TLC found violated is violated in USE too.
"""
import itertools
import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ beside the scripts
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tlaval as T  # noqa: E402

HEADER = "run\tsteps\tstates_compared\tstates_equal\tuse_errors\ttlc_end\tuse_end\tverdicts_compared\tverdicts_agree\tresult\tdetail"


def read_types(spec):
    t = {"pool": {}, "class": {}, "enum": {}, "attribute": {}, "association": {}, "param": {}, "result": {}, "operation": {}}
    for line in open(os.path.join(spec, "types.tsv")).read().splitlines()[1:]:
        kind, name, index, detail, typ = line.split("\t")
        if kind == "pool":
            t["pool"][name] = detail.split()
        elif kind == "class":
            t["class"][name] = (detail.split(), typ == "abstract")
        elif kind == "enum":
            t["enum"][name] = detail.split()
        elif kind == "attribute":
            t["attribute"][name] = typ
        elif kind == "association":
            t["association"][name] = detail.split()
        elif kind == "param":
            t["param"].setdefault(name, []).append((detail, typ))
        elif kind == "result":
            t["result"][name] = typ
        elif kind == "operation":
            t["operation"][name] = detail
    return t


def read_checks(spec):
    rows = [l.split("\t") for l in open(os.path.join(spec, "checks.tsv")).read().splitlines()[1:]]
    return {op: what for op, what in rows}


def soil(v, typ, types, var):
    """The SOIL text of TLA+ value v of USE type typ; var names a pool object."""
    if v == ("M", "Undef"):
        return "null"
    m = re.fullmatch(r"(Set|Bag|Sequence|OrderedSet)\((.*)\)", typ)
    if m:
        coll, elem = m.groups()
        if coll == "Set":
            parts = [soil(e, elem, types, var) for e in v[1]]
        elif coll == "Bag":
            parts = [soil(e, elem, types, var) for e, n in v[1] for _ in range(n[1])]
        else:
            parts = [soil(e, elem, types, var) for e in T.seq(v)]
        return "%s{%s}" % (coll, ", ".join(parts))
    if typ == "Integer":
        return str(v[1])
    if typ == "Boolean":
        return "true" if v[1] else "false"
    if typ == "String":
        return "'" + v[1].replace("\\", "\\\\").replace("'", "\\'") + "'"
    if typ in types["enum"]:
        assert v[0] == "S" and v[1].startswith(typ + "::"), (v, typ)
        return v[1]
    if typ in types["class"]:
        assert v[0] == "M", (v, typ)
        return var(v[1])
    raise ValueError("no SOIL text for a value of type %s" % typ)


def cls_of(state, obj):
    return T.get(T.field(state, "cls"), ("M", obj))[1]


def live(state):
    return [o[1] for o in T.field(state, "objs")[1]]


def pool_index(types, obj):
    for c, objs in types["pool"].items():
        if obj in objs:
            return objs.index(obj)
    return 0


def new_by_body(prev, cur, last, types):
    """The objects the step's body created, per class, in pool order."""
    created = set(live(cur)) - set(live(prev))
    if T.field(last, "step")[1] in ("new", "new+call"):
        created.discard(T.field(last, "self")[1])
    by = {}
    for o in sorted(created, key=lambda o: pool_index(types, o)):
        by.setdefault(cls_of(cur, o), []).append(o)
    return by


def selector(c, known):
    q = "%s.allInstances->select(x | x.oclIsTypeOf(%s))" % (c, c)
    if known:
        q += "->reject(x | Set{%s}->includes(x))" % ", ".join(sorted(known))
    return q + "->asSequence()"


def steps(trace, types):
    """For each step: (index, last, prev state, state, body-created objects by class)."""
    out = []
    for i in range(1, len(trace)):
        out.append((i, trace[i]["last"], trace[i - 1]["st"], trace[i]["st"], new_by_body(trace[i - 1]["st"], trace[i]["st"], trace[i]["last"], types)))
    return out


def commands(log, spec, dump):
    types = read_types(spec)
    err, trace = T.tlc_trace(open(log, errors="replace").read())
    if not trace:
        sys.exit("replay: no error trace in " + log)
    viol = re.match(r"Invariant (\w+) is violated", err)
    post_end = bool(viol) and viol.group(1).startswith("Post_")
    known = {}  # class -> pool objects USE has a variable for
    out = ["infotla %s s0" % dump]
    for i, last, prev, cur, body in steps(trace, types):
        kind = T.field(last, "step")[1]
        self_ = T.field(last, "self")[1]
        out.append("-- step %d" % i)
        if kind in ("new", "new+call"):
            c = cls_of(cur, self_)
            out.append("!create %s : %s" % (self_, c))
            known.setdefault(c, set()).add(self_)
        if kind == "new+call":
            out.append("infotla %s pre%d" % (dump, i))
        if kind in ("call", "new+call"):
            op = T.field(last, "op")[1]
            ps = types["param"].get(op, [])
            args = T.seq(T.field(last, "args"))
            text = [soil(a, typ, types, lambda o: o) for a, (_, typ) in zip(args, ps)]
            res = "tla_result := " if op in types["result"] else ""  # the result, compared with TLC's
            out.append("!%s%s.%s(%s)" % (res, self_, op.split("::")[1], ", ".join(text)))
        out.append("infotla %s s%d%s" % (dump, i, " tla_result" if kind != "new" and T.field(last, "op")[1] in types["result"] else ""))
        if i == len(trace) - 1 and post_end:
            out.append("c")
            break
        # A variable for each object the body created, for later steps; b<i> records its object.
        bound = []
        for c in sorted(body):
            for o in body[c]:
                out.append("!%s := %s->at(1)" % (o, selector(c, known.get(c, set()))))
                known.setdefault(c, set()).add(o)
                bound.append(o)
        if bound:
            out.append("infotla %s b%d %s" % (dump, i, " ;; ".join(bound)))
        out.append("check")
    out.append("exit")
    print("\n".join(out))


def segments(text, cmdname):
    """USE's output split by the commands it echoes: [(command, [output lines])]."""
    segs, cur = [], None
    prefix = cmdname + "> "
    for line in text.splitlines():
        if line.startswith(prefix):
            cur = (line[len(prefix):], [])
            segs.append(cur)
        elif cur is not None:
            cur[1].append(line)
    return segs


def use_verdicts(lines):
    """What a "check" output says: failed invariants (C::name) and violated multiplicity ends (A::role)."""
    failed, mult = set(), set()
    text = "\n".join(lines)
    for m in re.finditer(r"checking invariant \(\d+\) `([^']+)': FAILED", text):
        failed.add(m.group(1))
    for m in re.finditer(r"Multiplicity constraint violation in association `([^']+)':.*?at association end `([^']+)'", text, re.S):
        mult.add(m.group(1) + "::" + m.group(2))
    return failed, mult


def failed_conditions(lines, kind):
    """The names of the pre- or postconditions an [Error] block of a call output lists."""
    names, on = set(), False
    for line in lines:
        if re.match(r"\[Error\] \d+ %scondition" % kind, line):
            on = True
            continue
        if on:
            m = re.match(r"  (\w+): ", line)
            if m:
                names.add(m.group(1))
            elif line.strip() == "" or line.startswith("+---"):
                on = False
    return names


def dumps(path):
    out = {}
    if os.path.exists(path):
        for line in open(path, encoding="utf-8").read().splitlines():
            parts = line.split("\t")
            out[parts[0]] = [T.parse(p) for p in parts[1:]]
    return out


def use_name(ident):
    return ident[2:] if ident.startswith("O_") else ident


def match_new(use_state, tlc_state, body, use2pool, att, lnk):
    """Map the USE objects no mapping names to the objects the body created in TLC, class by
    class, by the bijection that makes the states equal (the first one if none does)."""
    fresh = {}
    for o in live(use_state):
        if o not in use2pool:
            fresh.setdefault(cls_of(use_state, o), []).append(o)
    choices = []
    for c in sorted(body):
        us = fresh.get(c, [])
        if len(us) != len(body[c]):
            return
        choices.append([list(zip(p, body[c])) for p in itertools.permutations(us)])
    first = None
    for combo in itertools.product(*choices):
        m = dict(use2pool)
        m.update(dict(pair for part in combo for pair in part))
        first = first or m
        if T.restrict(T.rename(use_state, m), att, lnk) == T.restrict(tlc_state, att, lnk):
            use2pool.update(m)
            return
    if first:
        use2pool.update(first)


def mark_applies(settings, last):
    mode = re.search(r"^invariants (.*)$", settings, re.M).group(1).split()
    step = T.field(last, "step")[1]
    if mode[0] == "every-state":
        return True
    if mode[0] == "after-calls":
        return step != "new"
    return step == "none" or T.field(last, "op")[1] in mode[1:]


def compare(log, spec, dump, useout, cfg, run):
    types = read_types(spec)
    checks = read_checks(spec)
    settings = open(os.path.join(spec, "settings.txt")).read()
    checked = set(re.findall(r"^INVARIANT (\w+)$", open(cfg).read(), re.M)) & set(checks)
    err, trace = T.tlc_trace(open(log, errors="replace").read())
    viol = re.match(r"Invariant (\w+) is violated", err or "").group(1)
    d = dumps(dump)
    segs = segments(open(useout, errors="replace").read(), os.path.basename(cfg_cmd(useout)))
    att, lnk = list(types["attribute"]), list(types["association"])
    use2pool = {}
    for c, objs in types["pool"].items():
        for o in objs:
            use2pool["O_" + o] = o  # created by the replay under the pool object's name
    problems, compared, equal, verdicts, agree, errors = [], 0, 0, 0, 0, 0

    def same(label, tlc_state):
        nonlocal compared, equal
        if label not in d:
            problems.append("no USE dump " + label)
            return
        compared += 1
        u = T.restrict(T.rename(d[label][0], use2pool), att, lnk)
        t = T.restrict(tlc_state, att, lnk)
        if u == t:
            equal += 1
        else:
            problems.append("%s: state differs: %s" % (label, "; ".join(T.diff(t, u)[:3])))

    same("s0", trace[0]["st"])
    # Output of each step: the commands from "-- step i" up to the next one.
    by_step, cur = {}, None
    for cmd, out in segs:
        m = re.match(r"-- step (\d+)$", cmd)
        if m:
            cur = int(m.group(1))
            by_step[cur] = []
        elif cur is not None:
            by_step[cur].append((cmd, out))
    final = len(trace) - 1
    use_end = ""
    for i, last, prev, cur_st, body in steps(trace, types):
        label = "s%d" % i
        bound = [o for c in sorted(body) for o in body[c]]
        if "b%d" % i in d:  # the variables the replay bound to the objects the body created
            for o, uo in zip(bound, d["b%d" % i][1:]):
                use2pool[uo[1]] = o
        elif bound and label in d:  # the last step: the objects no mapping names, matched by class
            match_new(d[label][0], cur_st, body, use2pool, att, lnk)
        if T.field(last, "step")[1] == "new+call":
            same("pre%d" % i, T.field(last, "pre"))
        same(label, cur_st)
        if len(d.get(label, [])) > 1 and not (i == final and viol.startswith("Post_")):  # the call's result
            compared += 1
            want = T.rename(T.field(T.field(last, "out"), "v_result"), {})
            got = T.rename(d[label][1], use2pool)
            if got == want:
                equal += 1
            else:
                problems.append("%s: result: TLC %s, USE %s" % (label, T.render(want), T.render(got)))
        call_out = [l for cmd, out in by_step.get(i, []) if cmd.startswith("!") for l in out]
        check_out = [l for cmd, out in by_step.get(i, []) if cmd == "check" for l in out]
        bad = [l for l in call_out if l.startswith("[Error]") or l.startswith("Error:")]
        if i == final and viol.startswith("Post_"):
            names = failed_conditions(call_out, "post")
            use_end = "post " + ",".join(sorted(names)) if names else "no failed postcondition"
            want = checks[viol].split("::")[-1]
            verdicts += 1
            if want in names:
                agree += 1
            else:
                problems.append("TLC: %s, USE: %s" % (checks[viol], use_end))
            continue
        if bad:
            errors += 1
            problems.append("step %d: USE: %s" % (i, bad[0]))
        failed, mult = use_verdicts(check_out)
        if i == final and not viol.startswith("Cover"):
            what = checks[viol]
            name = what.split(" ", 1)[1]
            hit = name in failed if what.startswith("invariant") else name in mult
            use_end = "; ".join(sorted(["inv " + f for f in failed] + ["mult " + m for m in mult])) or "check ok"
            verdicts += 1
            if hit:
                agree += 1
            else:
                problems.append("TLC: %s violated, USE: %s" % (what, use_end))
            continue
        if i == final:
            use_end = "accepted"
        if not mark_applies(settings, last) or not check_out:
            continue
        for chk in sorted(checked):
            what = checks[chk]
            if what.startswith("postcondition"):
                continue
            name = what.split(" ", 1)[1]
            verdicts += 1
            bad_use = name in failed if what.startswith("invariant") else name in mult
            if bad_use:
                problems.append("step %d: TLC: %s holds, USE: violated" % (i, what))
            else:
                agree += 1
    tlc_end = "reached " + viol[6:] if viol.startswith("Cover_") else checks.get(viol, viol)
    result = "agree" if not problems else "disagree"
    print("\t".join(str(x) for x in [run, final, compared, equal, errors, tlc_end, use_end, verdicts, agree, result,
                                     " | ".join(problems)[:600]]))


def cfg_cmd(useout):
    """The command file a USE output was produced from: the name it echoes before '> '."""
    for line in open(useout, errors="replace"):
        m = re.match(r"^(\S+\.cmd)> ", line)
        if m:
            return m.group(1)
    return "replay.cmd"


if __name__ == "__main__":
    if sys.argv[1] == "commands":
        commands(*sys.argv[2:5])
    elif sys.argv[1] == "compare":
        compare(*sys.argv[2:8])
    elif sys.argv[1] == "header":
        print(HEADER)
