#!/usr/bin/env python3
"""Print every OCL expression of a small typed grammar up to a depth, one per line.

Usage: ci/translation-oracle/expressions.py <depth>

The expressions are closed: they read the state through A.allInstances and B.allInstances,
and through the variable x only inside an iterator body. They use the names of model.use
beside this file. Each line is a well-typed USE 7.5 expression, sorted, without duplicates.

Kinds of value: bool, int, obj (an A, possibly undefined), set (Set(A)), bag (Bag(A)),
iset, ibag, iseq, ioset (Set, Bag, Sequence and OrderedSet of Integer), nset (Set(Set(Integer))).
"""
import sys
from itertools import product

KINDS = ["bool", "int", "obj", "set", "bag", "iset", "ibag", "iseq", "ioset", "nset"]
COLLECTIONS = ["set", "bag", "iset", "ibag", "iseq", "ioset"]
INT_COLLECTIONS = ["iset", "ibag", "iseq", "ioset"]


def atoms(scoped):
    a = {k: set() for k in KINDS}
    a["int"] |= {"0", "1"}
    a["bool"] |= {"true"}
    a["set"] |= {"A.allInstances", "B.allInstances"}
    if scoped:
        a["obj"] |= {"x"}
        a["int"] |= {"x.n", "x.d"}
        a["bool"] |= {"x.oclIsTypeOf(B)"}
        a["set"] |= {"x.dst"}
        a["obj"] |= {"x.src"}
    return a


def grow(prev, scoped, closed):
    """One more level: every operation applied to expressions of the levels below.

    prev: expressions of depth <= d-1 in this scope. closed: closed expressions of depth <= d-1,
    the sources of iterators. Iterators only appear in closed scope, so x is never shadowed.
    """
    n = {k: set(v) for k, v in prev.items()}
    I, B, O, S = prev["int"], prev["bool"], prev["obj"], prev["set"]
    small_int = {e for e in I if len(e) <= 4}  # the second operand of a binary operation
    for c in COLLECTIONS + ["nset"]:
        for e in prev[c]:
            n["int"].add(f"{e}->size()")
            n["bool"].add(f"{e}->isEmpty()")
    for c in ["iset", "ibag", "iseq"]:
        for e in prev[c]:
            n["int"].add(f"{e}->sum()")
            for i in small_int:
                n["bool"].add(f"{e}->includes({i})")
                n["int"].add(f"{e}->count({i})")
    for a, b in product(I, small_int):
        n["int"].add(f"({a} + {b})")
        n["bool"].add(f"({a} = {b})")
        n["bool"].add(f"({a} < {b})")
    for e in I:
        n["bool"].add(f"{e}.oclIsUndefined()")
    for e in B:
        n["bool"].add(f"(not {e})")
        n["bool"].add(f"{e}.oclIsUndefined()")
        for c in B:
            if len(c) <= 20:
                n["bool"].add(f"({e} and {c})")
                n["bool"].add(f"({e} or {c})")
                n["bool"].add(f"({e} implies {c})")
        for a in small_int:
            n["int"].add(f"if {e} then {a} else 0 endif")
    for e in O:
        n["bool"].add(f"{e}.oclIsUndefined()")
        n["bool"].add(f"{e}.oclIsKindOf(B)")
        n["obj"].add(f"{e}.src")
        n["obj"].add(f"{e}.oclAsType(B)")
        n["set"].add(f"{e}.dst")
        n["int"].add(f"{e}.n")
        n["int"].add(f"{e}.d")
        if scoped:
            n["bool"].add(f"({e} = x)")
    for e in S:
        # null is an OclVoid, not an A: it fits only where any object does.
        n["bool"].add(f"{e}->includes(null)")
        n["set"].add(f"{e}->including(null)")
        n["set"].add(f"{e}->excluding(null)")
        for o in O:
            if len(o) <= 5:
                n["bool"].add(f"{e}->includes({o})")
                n["set"].add(f"{e}->including({o})")
                n["set"].add(f"{e}->excluding({o})")
        for f in S:
            if len(f) <= 15:
                n["set"].add(f"{e}->union({f})")
                n["set"].add(f"{e}->intersection({f})")
                n["set"].add(f"({e} - {f})")
                n["bool"].add(f"({e} = {f})")
        n["bag"].add(f"{e}->asBag()")
    for e in prev["bag"]:
        n["set"].add(f"{e}->asSet()")
    for i in small_int:
        for j in small_int:
            n["iset"].add(f"Set{{{i}, {j}}}")
            n["ibag"].add(f"Bag{{{i}, {j}}}")
            n["iseq"].add(f"Sequence{{{i}, {j}}}")
            n["ioset"].add(f"OrderedSet{{{i}, {j}}}")
    for c in INT_COLLECTIONS:
        for e in prev[c]:
            n["iset"].add(f"{e}->asSet()")
            n["ibag"].add(f"{e}->asBag()")
            for i in small_int:
                n["iset" if c == "iset" else c].add(f"{e}->including({i})")
                n["iset" if c == "iset" else c].add(f"{e}->excluding({i})")
    for e in prev["ibag"]:
        for f in prev["ibag"]:
            if len(f) <= 15:
                n["bool"].add(f"({e} = {f})")
    for e in prev["iset"]:
        for f in prev["iset"]:
            if len(f) <= 15:
                n["nset"].add(f"Set{{{e}, {f}}}")
                n["bool"].add(f"({e} = {f})")
    for e in prev["nset"]:
        n["iset"].add(f"{e}->flatten()")
    if not scoped:
        bodies = closed_bodies
        for s in closed["set"]:
            for b in bodies["bool"]:
                n["bool"].add(f"{s}->forAll(x | {b})")
                n["bool"].add(f"{s}->exists(x | {b})")
                n["set"].add(f"{s}->select(x | {b})")
                n["set"].add(f"{s}->reject(x | {b})")
            for i in bodies["int"]:
                n["ibag"].add(f"{s}->collect(x | {i})")
            for o in bodies["obj"]:
                n["bag"].add(f"{s}->collect(x | {o})")
            for t in bodies["set"]:
                n["bag"].add(f"{s}->collect(x | {t})")
                if "B.allInstances" not in s:  # closure needs a body of the source's type
                    n["set"].add(f"{s}->closure(x | {t})")
    return n


def mentions_x(e):
    return "x" in e.replace("excluding", "").replace("exists", "")


def generate(depth):
    global closed_bodies
    closed = atoms(False)
    scoped = atoms(True)
    for _ in range(depth):
        # An iterator body is one level below the iterator, and must use x.
        closed_bodies = {k: {e for e in v if mentions_x(e)} for k, v in scoped.items()}
        next_closed = grow(closed, False, closed)
        scoped = grow(scoped, True, closed)
        closed = next_closed
    # mv ? cannot read a Bag, a Sequence or an OrderedSet: read the deepest ones once more
    # through a Set and a size, which it can read.
    readable = set()
    for c in ["bag", "ibag", "iseq", "ioset"]:
        for e in closed[c]:
            readable |= {f"{e}->asSet()", f"{e}->size()"}
    return sorted(readable | {e for v in closed.values() for e in v})


closed_bodies = {}

if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    for e in generate(int(sys.argv[1])):
        print(e)
