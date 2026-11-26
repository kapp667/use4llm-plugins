"""TLA+ values as TLC prints them and as the plugin's infotla command writes them.

A value parses to a canonical tuple, so that equal TLA+ values give equal tuples:
  ('B', bool), ('I', int), ('S', str), ('M', name) for a model value or other identifier,
  ('SET', sorted elements), ('FN', sorted (key, value) pairs).
A tuple <<a, b>> is the function (1 :> a @@ 2 :> b) and a record [f |-> v] the function
("f" :> v), as in TLA+. render() writes a canonical value back as TLA+ text.
"""
import re

_TOKEN = re.compile(r'\s*(?:(<<|>>|\|->|:>|@@|[{}\[\](),])|("(?:[^"\\]|\\.)*")|(-?\d+)|([A-Za-z_][A-Za-z0-9_]*))')


def _tokens(text):
    pos, out = 0, []
    text = text.rstrip()
    while pos < len(text):
        m = _TOKEN.match(text, pos)
        if not m:
            raise ValueError("cannot read TLA+ value at %r" % text[pos:pos + 40])
        pos = m.end()
        if m.group(1):
            out.append(("p", m.group(1)))
        elif m.group(2):
            out.append(("s", re.sub(r'\\(.)', lambda x: {"n": "\n", "t": "\t"}.get(x.group(1), x.group(1)), m.group(2)[1:-1])))
        elif m.group(3):
            out.append(("i", int(m.group(3))))
        else:
            out.append(("id", m.group(4)))
    return out


def fn(pairs):
    return ("FN", tuple(sorted(pairs)))


def tup(items):
    return fn((("I", i + 1), v) for i, v in enumerate(items))


def parse(text):
    toks = _tokens(text)
    v, i = _value(toks, 0)
    if i != len(toks):
        raise ValueError("trailing text after a TLA+ value: %r" % (toks[i:i + 5],))
    return v


def _value(t, i):
    kind, x = t[i]
    if kind == "s":
        return ("S", x), i + 1
    if kind == "i":
        return ("I", x), i + 1
    if kind == "id":
        if x in ("TRUE", "FALSE"):
            return ("B", x == "TRUE"), i + 1
        return ("M", x), i + 1
    if x == "{":
        items, i = _list(t, i + 1, "}")
        return ("SET", tuple(sorted(set(items)))), i
    if x == "<<":
        items, i = _list(t, i + 1, ">>")
        return tup(items), i
    if x == "[":
        pairs, i = [], i + 1
        while t[i] != ("p", "]"):
            name = t[i][1]
            assert t[i + 1] == ("p", "|->"), t[i:i + 3]
            v, i = _value(t, i + 2)
            pairs.append((("S", name), v))
            if t[i] == ("p", ","):
                i += 1
        return fn(pairs), i + 1
    if x == "(":
        # ( term @@ term ... ) where a term is k :> v or a function in parentheses; or (v).
        terms, i = [], i + 1
        while True:
            v, i = _value(t, i)
            if t[i] == ("p", ":>"):
                w, i = _value(t, i + 1)
                terms.append([(v, w)])
            else:
                terms.append(None if v[0] != "FN" else list(v[1]))
                single = v
            if t[i] == ("p", ")"):
                break
            assert t[i] == ("p", "@@"), t[i:i + 3]
            i += 1
        if len(terms) == 1 and terms[0] is None:  # (v), such as (-1)
            return single, i + 1
        merged = {}
        for term in terms:
            assert term is not None, "a non-function inside @@"
            for k, w in term:
                merged.setdefault(k, w)  # f @@ g: f wins
        return fn(merged.items()), i + 1
    raise ValueError("unexpected token %r" % (t[i],))


def _list(t, i, close):
    items = []
    while t[i] != ("p", close):
        v, i = _value(t, i)
        items.append(v)
        if t[i] == ("p", ","):
            i += 1
    return items, i + 1


def field(v, name):
    """The value of field (or key) name of a function or record."""
    for k, w in v[1]:
        if k == ("S", name):
            return w
    raise KeyError(name)


def get(v, key):
    for k, w in v[1]:
        if k == key:
            return w
    raise KeyError(key)


def items(v):
    return list(v[1])


def seq(v):
    """The elements of a tuple <<...>>, in order."""
    if v[0] != "FN":
        raise ValueError("not a tuple: %r" % (v,))
    return [w for _, w in sorted(v[1], key=lambda p: p[0][1])]


def rename(v, m):
    """v with each model value x replaced by m[x] (unknown ones kept)."""
    if v[0] == "M":
        return ("M", m.get(v[1], v[1]))
    if v[0] == "SET":
        return ("SET", tuple(sorted(set(rename(x, m) for x in v[1]))))
    if v[0] == "FN":
        return fn((rename(k, m), rename(w, m)) for k, w in v[1])
    return v


def render(v):
    t, x = v
    if t == "B":
        return "TRUE" if x else "FALSE"
    if t == "I":
        return str(x) if x >= 0 else "(%d)" % x
    if t == "S":
        return '"' + x.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'
    if t == "M":
        return x
    if t == "SET":
        return "{" + ", ".join(render(e) for e in x) + "}"
    keys = [k for k, _ in x]
    if not keys:
        return "<<>>"
    if all(k[0] == "I" for k in keys) and sorted(k[1] for k in keys) == list(range(1, len(keys) + 1)):
        return "<<" + ", ".join(render(w) for w in seq(v)) + ">>"
    if all(k[0] == "S" and re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", k[1]) for k in keys):
        return "[" + ", ".join("%s |-> %s" % (k[1], render(w)) for k, w in x) + "]"
    return "(" + " @@ ".join("%s :> %s" % (render(k), render(w)) for k, w in x) + ")"


def tlc_trace(text):
    """The states of the error trace in TLC's output: a list of {variable: value}, and the error line."""
    m = re.search(r"^Error: (.*violated.*)$", text, re.M)
    if not m:
        return None, []
    err = m.group(1)
    states = []
    for block in re.split(r"\n(?=State \d+: )", text[m.end():]):
        if not block.startswith("State "):
            continue
        body = block.split("\n", 1)[1] if "\n" in block else ""
        body = body.split("\n\n", 1)[0]
        st = {}
        for part in re.split(r"(?:^|\n)/\\ ", body):
            part = part.strip()
            if not part:
                continue
            name, val = part.split(" = ", 1)
            st[name.strip()] = parse(val.replace("\n", " "))
        states.append(st)
    return err, states


def objects(v):
    """The live objects of a state record."""
    return [o[1] for o in field(v, "objs")[1]]


def restrict(state, att_keys, lnk_keys):
    """A state record cut to what both sides hold: the live objects and their classes, the
    attributes att_keys over the live objects, the associations lnk_keys."""
    live = set(field(state, "objs")[1])
    cls = fn((k, w) for k, w in field(state, "cls")[1] if k in live)
    att_all = dict(field(state, "att")[1])
    att = fn((("S", a), fn((k, w) for k, w in att_all[("S", a)][1] if k in live)) for a in att_keys if ("S", a) in att_all)
    lnk_all = dict(field(state, "lnk")[1])
    lnk = fn((("S", a), lnk_all[("S", a)]) for a in lnk_keys if ("S", a) in lnk_all)
    return fn([(("S", "objs"), ("SET", tuple(sorted(live)))), (("S", "cls"), cls), (("S", "att"), att), (("S", "lnk"), lnk)])


def diff(a, b, path=""):
    """Where two canonical values differ, as short readable lines."""
    if a == b:
        return []
    if a[0] == b[0] == "FN":
        da, db = dict(a[1]), dict(b[1])
        out = []
        for k in sorted(set(da) | set(db)):
            p = path + "[" + render(k) + "]"
            if k not in da:
                out.append("%s: only on the right, %s" % (p, render(db[k])))
            elif k not in db:
                out.append("%s: only on the left, %s" % (p, render(da[k])))
            else:
                out.extend(diff(da[k], db[k], p))
        return out
    return ["%s: %s vs %s" % (path or "value", render(a), render(b))]
