EXTENDS Integers, FiniteSets, Sequences, TLC
\* Fixed prelude of every translated USE model: OCL's undefined value and USE's semantics
\* of the operations that need more than one TLA+ operator.
CONSTANT Undef
Show(v) == IF v = Undef THEN "undefined" ELSE IF v = TRUE THEN "true" ELSE "false"
\* USE's three-valued Boolean operations (StandardOperationsBoolean).
BNot(a) == IF a = Undef THEN Undef ELSE ~a
BAnd(a, b) == IF a = FALSE \/ b = FALSE THEN FALSE ELSE IF a = Undef \/ b = Undef THEN Undef ELSE TRUE
BOr(a, b) == IF a = TRUE \/ b = TRUE THEN TRUE ELSE IF a = Undef \/ b = Undef THEN Undef ELSE FALSE
BImp(a, b) == IF a = FALSE \/ b = TRUE THEN TRUE ELSE IF a = Undef \/ b = Undef THEN Undef ELSE FALSE
BXor(a, b) == IF a = Undef \/ b = Undef THEN Undef ELSE a # b
\* Java's int division and remainder, which truncate toward zero (TLA+ \div and % floor).
IAbs(a) == IF a < 0 THEN -a ELSE a
JDiv(a, b) == IF (a >= 0) = (b > 0) THEN IAbs(a) \div IAbs(b) ELSE -(IAbs(a) \div IAbs(b))
JMod(a, b) == a - b * JDiv(a, b)
\* Collections: a Set is a set, a Sequence or OrderedSet a tuple, a Bag a function from its
\* elements to their positive counts.
SeqElems(q) == {q[i] : i \in 1..Len(q)}
RECURSIVE SumF(_, _)
SumF(S, f) == IF S = {} THEN 0 ELSE LET x == CHOOSE y \in S : TRUE IN f[x] + SumF(S \ {x}, f)
EmptyBag == [x \in {} |-> 0]
BagCount(b, x) == IF x \in DOMAIN b THEN b[x] ELSE 0
\* TLCEval materializes a function, which TLC otherwise re-evaluates at each application.
BagPlus(a, b) == TLCEval([e \in DOMAIN a \cup DOMAIN b |-> BagCount(a, e) + BagCount(b, e)])
SetToBag(S) == [x \in S |-> 1]
SeqToBag(q) == [x \in SeqElems(q) |-> Cardinality({i \in 1..Len(q) : q[i] = x})]
\* The sum of the bags G[x], each counted W[x] times, for x in S.
RECURSIVE BagSumW(_, _, _)
BagSumW(S, G, W) == IF S = {} THEN EmptyBag
                    ELSE LET x == CHOOSE y \in S : TRUE
                         IN BagPlus([e \in DOMAIN G[x] |-> G[x][e] * W[x]], BagSumW(S \ {x}, G, W))
