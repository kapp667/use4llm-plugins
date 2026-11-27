#!/usr/bin/env python3
"""Read the answers.tsv that TranslationOracle wrote.

Usage: summarize.py <answers.tsv> <disagreements.tsv> [<expressions.txt>]
       summarize.py --check <answers.tsv>

The first form writes one row per expression whose translation gives another value than USE in
at least one state: expression, kind and "warn" when the translation logged a warning. It writes
beside it findings.txt, the shortest expressions of each kind with a state that shows it, and
prints a count of each outcome. It also writes not-compared.tsv beside it: the number of states,
the number of expressions (with the third argument), and every expression that "mv ?" could not
compare with a solution in some state (the translation refused it, threw, or has no atom). run.sh
compares that file with expected-not-compared.tsv, so an expression that agreed and now is not
compared, or a change in the count of states or expressions, fails. The kinds are:
  verdict    a Boolean that is true in USE and not in the translation, or the reverse: as an
             invariant, the validator decides this state the other way from USE
  undefined  a Boolean that USE leaves undefined and the translation reads as false: the
             invariant fails in both, so the verdict is the same, but an enclosing not differs
  no-value   an Integer outside Integer_min..Integer_max of model.properties, which the solution
             has no atom for: mv ? reads no value, or a Set(Integer) without it
  value      any other value
The second form exits 1 if an expression was not valid OCL or mv ? printed something unexpected
(outcome "other"), or if the states were cut off by maxStates.
"""
import collections
import os
import sys


def rows(path):
    with open(path, encoding="utf-8") as f:
        for line in f:
            yield line.rstrip("\n").split("\t")


def kind(use, kodkod):
    if use.endswith(" : Boolean"):
        use_value = use[: -len(" : Boolean")]
        if use_value == "null" and kodkod == "false":
            return "undefined"
        if (use_value == "true") != (kodkod == "true"):
            return "verdict"
    if kodkod == "[]" and use.endswith(" : Integer"):
        return "no-value"
    if use.endswith(" : Set(Integer)") and use.startswith("Set{"):
        elements = [e for e in use[4: use.index("} : ")].split(",") if e]
        kept = [e for e in elements if e == "null" or INTEGER_MIN <= int(e) <= INTEGER_MAX]
        if kept != elements and kodkod == "Set{" + ",".join(kept) + "}":
            return "no-value"
    return "value"


def bound(name):
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "model.properties")) as f:
        for line in f:
            key, _, value = line.partition("=")
            if key.strip() == name:
                return int(value)
    raise SystemExit(f"model.properties has no {name}")


INTEGER_MIN, INTEGER_MAX = bound("Integer_min"), bound("Integer_max")


def check(path):
    bad = 0
    for r in rows(path):
        if r[1] == "other":
            bad += 1
            if bad <= 10:
                print("unexpected answer of mv ?:", r[2], "->", r[4][:300])
        if r[1] == "end" and r[2] != "no more states":
            print("the states were cut off at", r[3], "; raise ORACLE_MAX_STATES")
            bad += 1
    if bad:
        print(bad, "unexpected answers")
    return 1 if bad else 0


def summarize(path, target, expressions=None):
    states, first, outcomes = {}, {}, collections.Counter()
    not_compared, state_count = set(), None
    seen_in = collections.defaultdict(set)
    for r in rows(path):
        if r[1] == "state":
            states[r[0]] = r[3]
        elif r[1] == "end":
            state_count = r[3]
            print("states:", r[3], "| agreeing answers:", r[4], "|", r[2])
        else:
            outcomes[r[1]] += 1
            if r[1] == "not-compared":
                not_compared.add(r[2])
            if r[1] == "differ":
                seen_in[r[2]].add(r[0])
                k = kind(r[3], r[4])
                old = first.get(r[2])
                # One expression can differ in several ways: keep the worst, verdict first.
                order = ["verdict", "undefined", "no-value", "value"]
                if old is None or order.index(k) < order.index(old[0]):
                    first[r[2]] = (k, r[5], r[0], r[3], r[4])
    print("outcomes other than agree:", dict(outcomes))
    with open(target, "w", encoding="utf-8") as f:
        for e in sorted(first):
            f.write(f"{e}\t{first[e][0]}\t{first[e][1]}\n")
    with open(os.path.join(os.path.dirname(target), "not-compared.tsv"), "w", encoding="utf-8") as f:
        f.write(f"states\t{state_count}\n")
        if expressions:
            with open(expressions, encoding="utf-8") as g:
                f.write(f"expressions\t{sum(1 for line in g if line.strip())}\n")
        for e in sorted(not_compared):
            f.write(f"not-compared\t{e}\n")
    by_kind = collections.defaultdict(list)
    for e, (k, warn, state, use, kodkod) in first.items():
        by_kind[k].append((len(e), e, warn, state, use, kodkod))
    with open(os.path.join(os.path.dirname(target), "findings.txt"), "w", encoding="utf-8") as f:
        for k in ["verdict", "undefined", "no-value", "value"]:
            found = sorted(by_kind[k])
            print(f"{k}: {len(found)} expressions")
            f.write(f"== {k}: {len(found)} expressions\n")
            for _, e, warn, state, use, kodkod in found[:25]:
                f.write(f"{e}\n    state {state}: {states.get(state, '?')}\n"
                        f"    USE: {use} | translation: {kodkod} {warn} | differs in {len(seen_in[e])} states\n")


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "--check":
        sys.exit(check(sys.argv[2]))
    if len(sys.argv) not in (3, 4):
        sys.exit(__doc__)
    summarize(*sys.argv[1:])
