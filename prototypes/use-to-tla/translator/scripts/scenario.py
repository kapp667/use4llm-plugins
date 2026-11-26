"""Check that a USE scenario (a command script) is a behaviour the tlaops spec allows.

Usage, in this order (scripts/ops-scenarios.sh runs them):
  python3 -I scenario.py prepare <script> <model> <work dir>   writes run1.cmd, lines.json
  (USE runs run1.cmd: tlaops default, then the scenario with infotla dumps and checks)
  python3 -I scenario.py settings <work dir>                   writes scenario.settings
  (USE runs tlaops with scenario.settings into <work dir>/spec)
  python3 -I scenario.py module <work dir>                     writes spec/Trace.tla, Trace.cfg
  (TLC runs Trace)
  python3 -I scenario.py compare <work dir> <name>              prints one TSV row (HEADER)

The scenario's lines run in USE as written, with "infotla" dumping the state after each command
and the receiver and arguments before each call. A step is:
  new       !create x : C, !x := new C or !new C outside an operation: Step_new_C(p) of the spec;
  call      !x.op(...) or !v := x.op(...) outside an operation, op with a SOIL body: Step_call_C_op
            of the spec, C the class that declares op, so that the spec dispatches to the override
            of x's class, and the spec's result is the value USE assigns to v; a call USE refuses (a precondition, a failed statement) must be one whose
            Call_ is Fail, and leaves the state as it was; USE's rollback after "c" is a step back
            to the state before;
  enter     !openter x op(...): no change; the spec's preconditions give their verdicts;
  exit      !opexit [r]: the contract of op (settings: contract), its preconditions in the state
            at openter, its postconditions over that state and the state at opexit;
  env       any other command that may change the state (!set, !insert, !delete, !destroy, the
            commands inside openter ... opexit): the next state is USE's (the environment).
TLC follows the trace with the spec's step actions: each step is enabled and leads to USE's
state, and at each state TLC prints the value of every check (invariants, multiplicities,
postconditions), which compare holds against USE's verdicts.
"""
import json
import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ beside the scripts
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tlaval as T  # noqa: E402

HEADER = ("scenario\tmodel\tcommands\tsteps\tnew\tcall\tcontract\tenv\tunchecked\tallowed\tverdicts_compared\tverdicts_agree"
          "\tresult\tdetail")


# ---------------------------------------------------------------- prepare

def split_args(s):
    """Top-level comma-separated parts of s."""
    parts, depth, cur, q = [], 0, "", None
    for ch in s:
        if q:
            cur += ch
            if ch == q:
                q = None
            continue
        if ch in "'\"":
            q = ch
        elif ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            parts.append(cur.strip())
            cur = ""
            continue
        cur += ch
    if cur.strip():
        parts.append(cur.strip())
    return parts


def flatten(path, model):
    """The scenario's command lines, with included command files inlined (paths relative to the
    including file, as USE resolves them; a path that does not exist is looked up by its name in
    the including file's folder, then without case), expected-output (*), # and -- lines dropped."""
    out = []
    for raw in open(path, errors="replace").read().splitlines():
        line = raw.strip()
        if not line or line.startswith("*") or line.startswith("#") or line.startswith("--"):
            continue
        m = re.match(r"(open|read)\s+(\S+)$", line)
        if m:
            target = os.path.normpath(os.path.join(os.path.dirname(path), m.group(2)))
            if not os.path.exists(target):  # abcd_*.cmd name c:/use/civstat/...: the file beside it
                target = os.path.join(os.path.dirname(path), os.path.basename(m.group(2)))
            if not os.path.exists(target):  # tollcoll01.cmd reads Point_southConnect.cmd: case differs
                same = [f for f in os.listdir(os.path.dirname(target)) if f.lower() == os.path.basename(target).lower()]
                target = os.path.join(os.path.dirname(target), same[0]) if same else target
            if not os.path.exists(target) or not re.search(r"\.(cmd|soil|in|use)$", target):
                out.append(line)  # USE reports a missing file; a .testsuite is no command file
                continue
            if target.endswith(".use"):
                model.append(target)
            else:
                out.extend(flatten(target, model))
            continue
        m = re.match(r"!\s*create\s+([\w\s,]+?)\s*:\s*(\w+)\s*$", line)
        if m and "," in m.group(1):
            out.extend("!create %s : %s" % (n.strip(), m.group(2)) for n in m.group(1).split(","))
            continue
        out.append(line)
    return out


def classify(line):
    if re.match(r"(exit|quit)\b", line):
        return {"kind": "exit"}
    if line == "c":
        return {"kind": "continue"}
    if not line.startswith("!"):
        return {"kind": "pass"}
    body = line[1:].strip().rstrip(";").strip()
    m = re.match(r"openter\s+(\S+)\s+(\w+)\s*\((.*)\)$", body)
    if m:
        return {"kind": "enter", "target": m.group(1), "op": m.group(2), "args": split_args(m.group(3))}
    m = re.match(r"opexit\s*(.*)$", body)
    if m:
        return {"kind": "exit-op", "result": m.group(1).strip()}
    m = re.match(r"create\s+(\w+)\s*:\s*(\w+)$", body)
    if m:
        return {"kind": "new", "cls": m.group(2)}
    m = re.match(r"(?:\w+\s*:=\s*)?new\s+(\w+)\s*(?:\(.*\))?$", body)
    if m:
        return {"kind": "new", "cls": m.group(1)}
    m = re.match(r"(?:(\w+)\s*:=\s*)?(\w+)\s*\.\s*(\w+)\s*\((.*)\)$", body)
    if m and ";" not in m.group(4):
        return {"kind": "call?", "target": m.group(2), "op": m.group(3), "args": split_args(m.group(4)), "var": m.group(1)}
    return {"kind": "env"}


def prepare(script, model, work, *prefix):
    """prefix: scripts that run before the scenario, for one split into numbered files."""
    os.makedirs(work, exist_ok=True)
    models = []
    lines = [l for p in prefix for l in flatten(p, models)] + flatten(script, models)
    if models:  # the scenario opens its model itself (open x.use)
        model = models[0]
    dump = os.path.join(work, "dump.tsv")
    if os.path.exists(dump):
        os.remove(dump)
    cmd = ["tlaops default %s" % os.path.join(work, "default"), "infotla %s init" % dump]
    rows = []
    for j, line in enumerate(lines, 1):
        c = classify(line)
        c["line"], c["j"] = line, j
        rows.append(c)
        if c["kind"] == "exit":
            break
        cmd.append("-- line %d" % j)
        if c["kind"] in ("enter", "call?"):
            cmd.append("infotla %s a%d %s" % (dump, j, " ;; ".join([c["target"]] + c["args"])))
        if c["kind"] == "exit-op" and c["result"]:
            cmd.append("infotla %s r%d %s" % (dump, j, c["result"]))
        cmd.append(line)
        if c["kind"] != "pass":  # with the value a call assigned, if it assigns one
            cmd.append("infotla %s s%d%s" % (dump, j, " " + c["var"] if c["kind"] == "call?" and c.get("var") else ""))
            cmd.append("check")
    cmd.append("exit")
    open(os.path.join(work, "run1.cmd"), "w").write("\n".join(cmd) + "\n")
    json.dump({"model": model, "rows": rows}, open(os.path.join(work, "lines.json"), "w"), indent=1)
    print(model)


# ---------------------------------------------------------------- settings

def read_dumps(work):
    out, order = {}, []
    path = os.path.join(work, "dump.tsv")
    if os.path.exists(path):
        for line in open(path, encoding="utf-8").read().splitlines():
            parts = line.split("\t")
            out[parts[0]] = [T.parse(p) for p in parts[1:]]
            order.append(parts[0])
    return out, order


def read_tsv(path):
    rows = open(path).read().splitlines()
    head = rows[0].split("\t")
    return [dict(zip(head, r.split("\t"))) for r in rows[1:]]


def segments(work):
    """USE's output of run1.cmd: {line index: [output lines of its commands]} and the commands."""
    text = open(os.path.join(work, "use1.txt"), errors="replace").read()
    segs, cur, by = [], None, {}
    for line in text.splitlines():
        if line.startswith("run1.cmd> "):
            cmd = line[len("run1.cmd> "):]
            m = re.match(r"-- line (\d+)$", cmd)
            if m:
                cur = int(m.group(1))
                by[cur] = []
                continue
            if cur is not None:
                by[cur].append((cmd, []))
        elif cur is not None and by[cur]:
            by[cur][-1][1].append(line)
    return by


def classes(types_rows):
    parents, abstract = {}, set()
    for r in types_rows:
        if r["kind"] == "class":
            parents[r["name"]] = r["detail"].split()
            if r["type"] == "abstract":
                abstract.add(r["name"])
    return parents, abstract


def declaring(declared, parents, cls, op):
    """The class that first declares op above cls (cls itself, or the ancestor whose own
    ancestors do not declare op): a step there dispatches to the receiver's override."""
    have = [c for c in [cls] + parents.get(cls, []) if c + "::" + op in declared]
    top = [c for c in have if not any(p in have for p in parents.get(c, []))]
    return top[0] if top else cls


def resolve(op_rows, parents, cls, op):
    """The operation (Owner::op) a call of op on an object of cls runs, among op_rows' names."""
    for c in [cls] + parents.get(cls, []):
        if c + "::" + op in op_rows:
            return c + "::" + op
    return None


def receiver(a):
    """The receiver, the arguments and the receiver's class of an a<j> dump [state, self, args...]."""
    if not a or len(a) < 2 or a[1][0] != "M" or a[1][1] == "Undef":
        return None, [], None
    return a[1], a[2:], T.get(T.field(a[0], "cls"), a[1])[1]


def analyse(work):
    """The steps of the trace, from USE's run: what each line did, its receiver and arguments."""
    meta = json.load(open(os.path.join(work, "lines.json")))
    d, order = read_dumps(work)
    by = segments(work)
    types0 = read_tsv(os.path.join(work, "default", "types.tsv"))
    parents, abstract = classes(types0)
    bodyless = {r["name"] for r in types0 if r["kind"] == "declared" and r["detail"] != "body"}
    withbody = {r["name"] for r in types0 if r["kind"] == "declared" and r["detail"] == "body"}
    steps, stack, prev = [], [], "init"
    for c in meta["rows"]:
        j, kind = c["j"], c["kind"]
        if kind in ("exit", "pass"):
            continue
        out = [l for cmd, o in by.get(j, []) if cmd == c["line"] for l in o]
        checks = [l for cmd, o in by.get(j, []) if cmd == "check" for l in o]
        label = "s%d" % j
        if label not in d:
            continue
        st = {"j": j, "line": c["line"], "label": label, "prev": prev, "out": out, "check": checks, "depth": len(stack)}
        paused = any("Evaluation is paused" in l for l in out)
        st["paused"] = paused
        if kind == "continue":
            st["kind"] = "env"
            st["note"] = "c: USE unwinds the paused call"
        elif kind == "new" and not stack:
            st["kind"] = "new"
            st["cls"] = c["cls"]
        elif kind == "call?" and not stack:
            recv, args, rc = receiver(d.get("a%d" % j))
            qn = resolve(withbody | bodyless, parents, rc, c["op"]) if rc else None
            if qn in withbody:
                st.update(kind="call", cls=declaring(withbody | bodyless, parents, rc, c["op"]), op=c["op"], qn=qn, recv=recv, args=args,
                          pre_label="a%d" % j)
                st["refused"] = any(l.startswith("Error: precondition false") or re.match(r"\[Error\] \d+ precondition", l) for l in out) \
                    or (any(l.startswith("Error:") for l in out) and not any(re.match(r"\[Error\] \d+ postcondition", l) for l in out))
                st["post_failed"] = any(re.match(r"\[Error\] \d+ postcondition", l) for l in out)
                if c.get("var") and not paused and len(d[label]) > 1:
                    st["result_label"] = label  # d[label][1]: the value USE assigned
            else:
                st["kind"] = "env"
                st["note"] = "a call of an operation without SOIL body" if qn else "no operation %s on the receiver" % c["op"]
        elif kind == "enter":
            recv, args, rc = receiver(d.get("a%d" % j))
            qn = resolve(withbody | bodyless, parents, rc, c["op"]) if rc else None
            refused = any(l.startswith("Error:") for l in out)
            st.update(kind="enter", cls=rc, op=c["op"], qn=qn, recv=recv, args=args, refused=refused)
            if not refused:
                stack.append(st)
        elif kind == "exit-op":
            refused = [l for l in out if l.startswith("Error:") and not l.startswith("Error: postcondition false")]
            if not stack or refused:  # outside an operation, or a missing result: USE keeps the frame
                st["kind"] = "env"
                st["note"] = "opexit refused: " + (refused[0] if refused else "no operation")
            else:
                e = stack.pop()
                r = d.get("r%d" % j)
                st.update(kind="exit", enter=e["j"], cls=e["cls"], op=e["op"], qn=e["qn"], recv=e["recv"], args=e["args"],
                          enter_label=e["prev"], result=r[1] if r else None, depth=len(stack))
        else:
            st["kind"] = "env"
            if stack:
                st["note"] = "inside " + stack[-1]["line"]
        steps.append(st)
        prev = label
    return steps, d, order, parents, abstract


def setting_value(v, typ, enums):
    if v == ("M", "Undef"):
        return "undefined"
    if typ == "Integer":
        return str(v[1])
    if typ == "Boolean":
        return "true" if v[1] else "false"
    if typ == "String":
        return None if "'" in v[1] else "'" + v[1] + "'"
    if typ in enums:
        return "#" + v[1].split("::", 1)[1]
    return None


def settings(work):
    steps, d, order, parents, abstract = analyse(work)
    types0 = read_tsv(os.path.join(work, "default", "types.tsv"))
    enums = {r["name"] for r in types0 if r["kind"] == "enum"}
    ptypes = {}
    for r in types0:
        if r["kind"] == "param":
            ptypes.setdefault(r["name"], []).append((r["detail"], r["type"]))
    # Pools: the most objects of each class alive at once, over every dump.
    most = {}
    for label in order:
        count = {}
        for o, c in T.field(d[label][0], "cls")[1]:
            count[c[1]] = count.get(c[1], 0) + 1
        for c, n in count.items():
            most[c] = max(most.get(c, 0), n)
    lines = ["# Derived from the scenario by scripts/scenario.py: pools as large as the most objects alive at once."]
    for c in sorted(most):
        lines.append("objects %s %d" % (c, most[c]))
    news = sorted({s["cls"] for s in steps if s["kind"] == "new"})
    for c in news:
        lines.append("step new %s" % c)
    params = {}
    for s in steps:
        if s["kind"] == "call":
            key = "step call %s::%s" % (s["cls"], s["op"])
            if key not in lines:
                lines.append(key)
            for (pname, typ), v in zip(ptypes.get(s["qn"], []), s["args"]):
                if typ in parents:
                    params.setdefault((s["qn"], pname), set()).update(["objects", "undefined"])
                else:
                    sv = setting_value(v, typ, enums)
                    params.setdefault((s["qn"], pname), set()).add(sv if sv else "?")
        if s["kind"] in ("enter", "exit") and s.get("cls"):
            key = "contract %s::%s" % (s["cls"], s["op"])
            if key not in lines:
                lines.append(key)
    for (qn, p), vals in sorted(params.items()):
        if "?" not in vals:
            lines.append("param %s %s %s" % (qn, p, " ".join(sorted(vals))))
    lines.append("invariants every-state")
    open(os.path.join(work, "scenario.settings"), "w").write("\n".join(lines) + "\n")
    json.dump(steps, open(os.path.join(work, "steps.json"), "w"), indent=1, default=list)


# ---------------------------------------------------------------- module

def spec_info(spec):
    types = read_tsv(os.path.join(spec, "types.tsv"))
    ops = read_tsv(os.path.join(spec, "ops.tsv"))
    checks = read_tsv(os.path.join(spec, "checks.tsv"))
    pools = {r["name"]: r["detail"].split() for r in types if r["kind"] == "pool"}
    parents = {r["name"]: r["detail"].split() for r in types if r["kind"] == "class"}
    attrs = [r["name"] for r in types if r["kind"] == "attribute"]
    assocs = [r["name"] for r in types if r["kind"] == "association"]
    results = {r["name"]: r["type"] for r in types if r["kind"] == "result"}
    pres = {}
    for r in types:
        if r["kind"] == "pre":
            pres.setdefault(r["detail"].rsplit("::", 1)[0], []).append((r["name"], r["detail"].rsplit("::", 1)[1]))
    steps_ok = {r["name"] for r in ops if r["kind"] == "step" and r["status"] == "translated"}
    step_why = {r["name"]: r["construct"] for r in ops if r["kind"] == "step" and r["status"] != "translated"}
    contracts_ok = {r["name"] for r in ops if r["kind"] == "contract" and r["status"] == "translated"}
    contract_why = {r["name"]: r["construct"] for r in ops if r["kind"] == "contract" and r["status"] != "translated"}
    return dict(pools=pools, parents=parents, attrs=attrs, assocs=assocs, results=results, pres=pres, steps_ok=steps_ok,
                step_why=step_why, contracts_ok=contracts_ok, contract_why=contract_why, checks=checks)


def ident(s):
    return re.sub(r"[^A-Za-z0-9_]", "_", s)


def module(work):
    spec = os.path.join(work, "spec")
    info = spec_info(spec)
    steps = json.load(open(os.path.join(work, "steps.json")))
    d, order = read_dumps(work)
    pools, parents = info["pools"], info["parents"]
    # USE object -> pool object: at its first dump, the free pool object of least index, as the
    # spec's CHOOSE takes for a new inside a body (TLC orders model values as the cfg lists them).
    mapping, history = {}, {}
    refusal = None
    for label in order:
        state = d[label][0]
        live = {o[1]: T.get(T.field(state, "cls"), o)[1] for o in T.field(state, "objs")[1]}
        for o in [x for x in mapping if x not in live]:
            mapping.pop(o)
        fresh = sorted((o for o in live if o not in mapping), key=lambda o: (len(o), o))
        for o in fresh:
            c = live[o]
            if c not in pools:
                refusal = refusal or "objects of %s, which has no pool (%s)" % (c, "an association class or abstract" if c in parents else "?")
                continue
            used = set(mapping.values())
            free = [p for p in pools[c] if p not in used]
            if not free:
                refusal = refusal or "pool of %s exhausted" % c
                continue
            mapping[o] = free[0]
        history[label] = dict(mapping)
    json.dump({"history": history, "refusal": refusal}, open(os.path.join(work, "mapping.json"), "w"), indent=1)

    def pool_of(c):
        return [p for k, ps in pools.items() if k == c or c in parents.get(k, []) for p in ps]

    def owner(a):
        return a.split("::")[0]

    def state_text(label):
        m = history[label]
        st = T.rename(d[label][0], m)
        live = set(T.field(st, "objs")[1])
        att_all = dict(T.field(st, "att")[1])
        att = []
        for a in info["attrs"]:
            f = dict(att_all.get(("S", a), ("FN", ()))[1])
            att.append(T.render(("S", a)) + " :> (" + " @@ ".join(
                "%s :> %s" % (p, T.render(f.get(("M", p), ("M", "Undef"))) if ("M", p) in live else "Undef") for p in pool_of(owner(a))) + ")")
        lnk_all = dict(T.field(st, "lnk")[1])
        lnk = [T.render(("S", a)) + " :> " + T.render(lnk_all.get(("S", a), ("SET", ()))) for a in info["assocs"]]
        return "[objs |-> %s, cls |-> ClassOf, att |-> %s, lnk |-> %s]" % (
            T.render(("SET", tuple(sorted(live)))), "(" + " @@ ".join(att) + ")" if att else "<<>>", "(" + " @@ ".join(lnk) + ")" if lnk else "<<>>")

    def val(v, label):
        return T.render(T.rename(v, history[label]))

    labels = ["init"] + [s["label"] for s in steps]
    text = ["---- MODULE Trace ----", "EXTENDS UseOps", "VARIABLE k", "\\* The states of USE's run, in the state shape of UseOps."]
    for label in sorted(set(labels + [s.get("enter_label") for s in steps if s.get("enter_label")]), key=lambda x: (x != "init", len(x), x)):
        text.append("S_%s == %s" % (label, state_text(label)))
    acts, diag, kinds = [], [], []
    for k, s in enumerate(steps, 1):
        cur = "S_" + s["label"]
        kind, note = s["kind"], s.get("note", "")
        a = None
        if kind == "new":
            p = history[s["label"]]
            created = [o for o in p if o not in history[s["prev"]]]
            key = "new " + s["cls"]
            if key in info["steps_ok"] and len(created) == 1:
                a = "st' = %s /\\ Step_%s(%s)" % (cur, ident("new_" + s["cls"]), p[created[0]])
                diag.append('ASSUME PrintT("D|%d|" \\o ToString(New_%s(S_%s, %s)))' % (k, s["cls"], s["prev"], p[created[0]]))
            else:
                kind, note = "unchecked", info["step_why"].get(key, "no single new object")
        elif kind == "call":
            key = "call %s::%s" % (s["cls"], s["op"])
            tag = ident("call_%s_%s" % (s["cls"], s["op"]))
            if key in info["steps_ok"]:
                self_ = val(s["recv"], s["pre_label"])
                args = [val(x, s["pre_label"]) for x in s["args"]]
                sig = ", ".join([self_] + args)
                if s["refused"]:
                    a = "st' = st /\\ st = %s /\\ last' = [step |-> \"refused\", op |-> %s] /\\ Call_%s(st, %s).r = Fail" % (
                        cur, T.render(("S", "refused " + s["qn"])), tag, sig)
                else:
                    a = "st' = %s /\\ Step_%s(%s)" % (cur, tag, sig)
                    if s.get("result_label") and s["qn"] in info["results"]:  # the spec's result is USE's
                        a += " /\\ last'.out.v_result = %s" % val(d[s["result_label"]][1], s["result_label"])
                diag.append('ASSUME PrintT("D|%d|" \\o (LET x == Call_%s(S_%s, %s) IN IF x.r = Fail THEN "\\"Fail\\"" ELSE ToString(x.r.s)))' % (k, tag, s["prev"], sig))
                if s["qn"] in info["pres"]:
                    diag.append('ASSUME PrintT("P|%d|" \\o %s)' % (k, " \\o ".join("Bit(%s(S_%s, %s))" % (pc, s["prev"], sig) for pc, _ in info["pres"][s["qn"]])))
            else:
                kind, note = "unchecked", info["step_why"].get(key, "step not translated")
        elif kind in ("enter", "exit"):
            if s.get("qn") and s["qn"] in info["contracts_ok"]:
                self_ = val(s["recv"], s["prev"] if kind == "enter" else s["enter_label"])
                args = [val(x, s["prev"] if kind == "enter" else s["enter_label"]) for x in s["args"]]
                sig = ", ".join([self_] + args)
                n = ident(s["qn"].replace("::", "_"))
                at = "S_" + (s["prev"] if kind == "enter" else s["enter_label"])
                if kind == "enter":  # the spec's guard: true when USE enters, false when it refuses
                    a = "st' = st /\\ last' = [step |-> \"enter\", op |-> %s] /\\ %sPre_%s(%s, %s)" % (
                        T.render(("S", "enter " + s["qn"])), "~" if s["refused"] else "", n, at, sig)
                    if s["qn"] in info["pres"]:
                        diag.append('ASSUME PrintT("P|%d|" \\o %s)' % (k, " \\o ".join("Bit(%s(%s, %s))" % (pc, at, sig) for pc, _ in info["pres"][s["qn"]])))
                else:
                    out = ""
                    if s["qn"] in info["results"]:
                        out = ", out |-> [v_result |-> %s]" % (val(s["result"], s["prev"]) if s["result"] else "Undef")
                    a = ("st' = st /\\ last' = [step |-> \"call\", op |-> %s, self |-> %s, args |-> <<%s>>, pre |-> %s%s] /\\ Pre_%s(%s, %s)"
                         % (T.render(("S", s["qn"])), self_, ", ".join(args), at, out, n, at, sig))
            else:
                kind, note = "unchecked", info["contract_why"].get(s.get("qn") or "", "no operation " + str(s.get("op")))
        if a is None:
            a = "st' = %s /\\ last' = [step |-> \"env\", op |-> \"env\"]" % cur
            if kind not in ("env", "unchecked"):
                kind = "unchecked"
        acts.append("  \\/ k = %d /\\ k' = %d /\\ %s  \\* %s" % (k - 1, k, a, s["line"].replace("\\", "/")))
        kinds.append({"k": k, "kind": kind, "note": note, "j": s["j"]})
    names = [r["operator"] for r in info["checks"]]
    text += ["TInit == k = 0 /\\ st = S_init /\\ last = [step |-> \"none\", op |-> \"none\"]",
             "TNext ==", *(acts if acts else ["  FALSE"]),
             "\\* Violated once TLC has followed every step: the trace is a behaviour of the spec.",
             "Complete == k < %d" % len(steps),
             "\\* The value of every check of the spec in each state of the trace, one string (TLC breaks a long",
             "\\* tuple over several lines, never a string).",
             'Bit(b) == IF b THEN "1" ELSE "0"',
             'Report == PrintT("R|" \\o ToString(k) \\o "|" \\o %s)' % (" \\o ".join("Bit(%s)" % n for n in names) if names else '""'), *diag, "===="]
    open(os.path.join(spec, "Trace.tla"), "w").write("\n".join(text) + "\n")
    cfg = [l for l in open(os.path.join(spec, "UseOps.cfg")).read().splitlines() if l.startswith("CONSTANT")]
    cfg += ["INIT TInit", "NEXT TNext", "CONSTRAINT Report", "INVARIANT Complete", "CHECK_DEADLOCK FALSE"]
    open(os.path.join(spec, "Trace.cfg"), "w").write("\n".join(cfg) + "\n")
    json.dump(kinds, open(os.path.join(work, "kinds.json"), "w"), indent=1)


# ---------------------------------------------------------------- compare

def use_check(lines):
    failed, mult = set(), set()
    text = "\n".join(lines)
    for m in re.finditer(r"checking invariant \(\d+\) `([^']+)': FAILED", text):
        failed.add(m.group(1))
    for m in re.finditer(r"Multiplicity constraint violation in association `([^']+)':.*?at association end `([^']+)'", text, re.S):
        mult.add(m.group(1) + "::" + m.group(2))
    ran = "checked " in text and "invariant" in text
    return ran, failed, mult


def failed_names(lines, kind):
    names, on = set(), False
    for line in lines:
        if re.match(r"\[Error\] \d+ %scondition" % kind, line):
            on = True
            continue
        if on:
            m = re.match(r"  (\w+): ", line)
            if m:
                names.add(m.group(1))
            elif not line.startswith("    "):
                on = False
    return names


def compare(work, name):
    meta = json.load(open(os.path.join(work, "lines.json")))
    model = os.path.basename(meta["model"])
    ncmd = len([r for r in meta["rows"] if r["line"].startswith("!")])
    spec = os.path.join(work, "spec")
    row = {"scenario": name, "model": model, "commands": ncmd}
    try:
        mp = json.load(open(os.path.join(work, "mapping.json")))
        steps = json.load(open(os.path.join(work, "steps.json")))
        kinds = json.load(open(os.path.join(work, "kinds.json")))
    except FileNotFoundError as e:
        return emit(row, result="not run", detail=str(e))
    if mp["refusal"]:
        return emit(row, steps=len(steps), result="refused", detail=mp["refusal"])
    info = spec_info(spec)
    tlc = open(os.path.join(spec, "tlc-trace.txt"), errors="replace").read()
    rep, dg, pv = {}, {}, {}
    for line in tlc.splitlines():
        if not re.match(r'"[RDP]\|', line):
            continue
        tag, k, v = T.parse(line)[1].split("|", 2)
        if tag == "R":
            rep.setdefault(int(k), set()).add(tuple(b == "1" for b in v))
        elif tag == "D":
            dg[int(k)] = T.parse(v)
        else:
            pv[int(k)] = [b == "1" for b in v]
    count = {x: sum(1 for kk in kinds if kk["kind"] == x) for x in ("new", "call", "enter", "exit", "env", "unchecked")}
    row.update(steps=len(steps), new=count["new"], call=count["call"], contract=count["exit"], env=count["env"],
               unchecked=count["unchecked"])
    problems = []
    if "Error: Invariant Complete is violated" in tlc:
        row["allowed"] = "yes"
    elif "Model checking completed. No error has been found." in tlc:
        reached = max(rep) if rep else -1
        bad = reached + 1
        s = steps[bad - 1] if 0 < bad <= len(steps) else None
        why = ""
        if s and bad in dg:
            d, _ = read_dumps(work)
            hist = mp["history"]
            got = dg[bad]
            if got == ("S", "Fail"):
                why = "the spec refuses the step (Fail)"
            else:
                want = T.rename(d[s["label"]][0], hist[s["label"]])
                why = "; ".join(T.diff(T.restrict(got, info["attrs"], info["assocs"]), T.restrict(want, info["attrs"], info["assocs"]))[:3])
                why = "spec vs USE: " + (why or "none (the trace module's state differs from USE's dump)")
        row["allowed"] = "no, step %d" % bad
        problems.append("step %d (%s) not allowed: %s" % (bad, s["line"] if s else "?", why))
    else:
        e = re.search(r"^(Error:.*|.*Exception.*)$", tlc, re.M)
        row["allowed"] = "TLC error"
        problems.append((e.group(1) if e else "TLC failed")[:200])
    names = [r["operator"] for r in info["checks"]]
    what = {r["operator"]: r["checks"] for r in info["checks"]}
    compared = agree = 0
    for k, (s, kk) in enumerate(zip(steps, kinds), 1):
        vals = rep.get(k)
        if not vals:
            continue
        if len(vals) > 1:
            problems.append("step %d: TLC reached it with different verdicts" % k)
            continue
        verdict = dict(zip(names, next(iter(vals))))
        ran, failed, mult = use_check(s["check"])
        if ran and s["depth"] == 0 and not s.get("paused"):
            for op_, w in what.items():
                if w.startswith("invariant ") or w.startswith("multiplicity "):
                    n_ = w.split(" ", 1)[1]
                    use_ok = n_ not in (failed if w.startswith("invariant") else mult)
                    compared += 1
                    if use_ok == verdict[op_]:
                        agree += 1
                    else:
                        problems.append("step %d: %s: USE %s, TLC %s" % (k, w, use_ok, verdict[op_]))
        if kk["kind"] == "call" and not s["refused"]:
            fails = failed_names(s["out"], "post")
            for op_, w in what.items():
                if w.startswith("postcondition " + s["qn"] + "::"):
                    compared += 1
                    use_ok = w.rsplit("::", 1)[1] not in fails
                    if use_ok == verdict[op_]:
                        agree += 1
                    else:
                        problems.append("step %d: %s: USE %s, TLC %s" % (k, w, use_ok, verdict[op_]))
        if kk["kind"] == "exit":
            said = dict(re.findall(r"^postcondition `(\w+)' is (true|false)", "\n".join(s["out"]), re.M))
            for op_, w in what.items():
                if w.startswith("postcondition " + s["qn"] + "::"):
                    n_ = w.rsplit("::", 1)[1]
                    if n_ in said:
                        compared += 1
                        if (said[n_] == "true") == verdict[op_]:
                            agree += 1
                        else:
                            problems.append("step %d: %s: USE %s, TLC %s" % (k, w, said[n_], verdict[op_]))
        if k in pv and s.get("qn") in info["pres"]:
            pres = info["pres"][s["qn"]]
            if kk["kind"] == "enter":
                said = dict(re.findall(r"^precondition `(\w+)' is (true|false)", "\n".join(s["out"]), re.M))
                use = {n_: said[n_] == "true" for _, n_ in pres if n_ in said}
            else:
                fails = failed_names(s["out"], "pre")
                use = {n_: n_ not in fails for _, n_ in pres}
            for (pc, n_), tv in zip(pres, pv[k]):
                if n_ in use:
                    compared += 1
                    if use[n_] == tv:
                        agree += 1
                    else:
                        problems.append("step %d: precondition %s: USE %s, TLC %s" % (k, n_, use[n_], tv))
    unchecked = sorted({kk["note"] for kk in kinds if kk["kind"] == "unchecked"})
    result = "TLC error" if row["allowed"] == "TLC error" and len(problems) == 1 else "agree" if not problems else "disagree"
    detail = " | ".join(problems)
    if unchecked:
        detail = (detail + " | " if detail else "") + "unchecked: " + "; ".join(unchecked)
    emit(row, verdicts_compared=compared, verdicts_agree=agree, result=result, detail=detail)


def emit(row, **kw):
    row.update(kw)
    print("\t".join(str(row.get(h, "")) for h in HEADER.split("\t"))[:1200])


VERDICT = re.compile(r"^\*.*((pre|post)condition .*(is (true|false)|does not hold)|(pre|post)condition false)", re.M)


def find(examples, shell, probes=None):
    """The scenarios: the example scripts with a line that calls an operation (openter or a SOIL
    call), the shell tests whose expected output holds a precondition or postcondition verdict,
    the shell tests scenarios.txt in the probe folder lists, and the probe scripts (x.cmd beside x.use). One
    line each: name, script, model, prefix scripts (space-separated)."""
    for root, _, files in sorted(os.walk(examples)):
        for f in sorted(files):
            if not re.search(r"\.(cmd|soil|in)$", f):
                continue
            path = os.path.join(root, f)
            if not any(classify(l)["kind"] in ("enter", "call?") for l in flatten(path, [])):
                continue
            here = [x for x in os.listdir(root) if x.endswith(".use")]
            prefix = []
            if not here and os.path.basename(root) == "SplitCommands":
                up = os.path.dirname(root)
                here = [os.path.join("..", x) for x in os.listdir(up) if x.endswith(".use")]
                base = re.sub(r"\d*\.cmd$", "", f)
                prefix = sorted(os.path.join(root, x) for x in files if re.fullmatch(r"%s\d+\.cmd" % re.escape(base.replace("RaiseSalary", "")), x))
            if len(here) != 1:
                continue
            print("\t".join(["examples/" + os.path.relpath(path, examples), path, os.path.normpath(os.path.join(root, here[0])), " ".join(prefix)]))
    for f in sorted(os.listdir(shell)):
        if f.endswith(".in") and VERDICT.search(open(os.path.join(shell, f), errors="replace").read()):
            print("\t".join(["shell/" + f, os.path.join(shell, f), os.path.join(shell, f[:-3] + ".use"), ""]))
    listed = os.path.join(probes or "", "scenarios.txt")
    if probes and os.path.exists(listed):  # shell tests of USE's sources taken as probes
        for line in open(listed).read().splitlines():
            f = line.split("#")[0].strip()
            if f:
                print("\t".join(["probes/shell/" + f, os.path.join(shell, f), os.path.join(shell, f[:-3] + ".use"), ""]))
    if probes and os.path.isdir(probes):
        for f in sorted(os.listdir(probes)):
            if f.endswith(".cmd") and os.path.exists(os.path.join(probes, f[:-4] + ".use")):
                print("\t".join(["probes/" + f, os.path.join(probes, f), os.path.join(probes, f[:-4] + ".use"), ""]))


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "find":
        find(*sys.argv[2:5])
    elif cmd == "prepare":
        prepare(*sys.argv[2:])
    elif cmd == "settings":
        settings(sys.argv[2])
    elif cmd == "module":
        module(sys.argv[2])
    elif cmd == "compare":
        compare(sys.argv[2], sys.argv[3])
    elif cmd == "header":
        print(HEADER)
