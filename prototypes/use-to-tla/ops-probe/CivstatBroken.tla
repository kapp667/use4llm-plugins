---- MODULE CivstatBroken ----
\* Hand translation of examples/soil/civstat/civstat.use (USE 7.5.0), written the way a
\* deterministic translator would: one rule per construct, no judgment about the domain.
EXTENDS Integers, FiniteSets, TLC

CONSTANTS Pool, Names, AtomicBirth, Undef   \* the environment: not in the USE model; Undef is a TLC model value


CivilStatus == {"single", "married", "divorced", "widowed"}
Gender == {"female", "male"}

VARIABLES objs, name, civstat, gender, alive, Marriage, last
vars == <<objs, name, civstat, gender, alive, Marriage, last>>
st == [objs |-> objs, name |-> name, civstat |-> civstat, gender |-> gender,
       alive |-> alive, Marriage |-> Marriage]

\* Navigation over association Marriage (wife [0..1], husband [0..1]).
HusbandIn(s, p) == IF \E l \in s.Marriage : l[1] = p
                   THEN (CHOOSE l \in s.Marriage : l[1] = p)[2] ELSE Undef
WifeIn(s, p)    == IF \E l \in s.Marriage : l[2] = p
                   THEN (CHOOSE l \in s.Marriage : l[2] = p)[1] ELSE Undef
Husband(p) == HusbandIn(st, p)
Wife(p)    == WifeIn(st, p)
\* Query operation spouse(), inlined.
Spouse(p) == IF gender[p] = "female" THEN Husband(p) ELSE Wife(p)
Attr(f, p) == IF p = Undef THEN Undef ELSE f[p]

Init == /\ objs = {}
        /\ name = [p \in Pool |-> Undef] /\ civstat = [p \in Pool |-> Undef]
        /\ gender = [p \in Pool |-> Undef] /\ alive = [p \in Pool |-> Undef]
        /\ Marriage = {}
        /\ last = [op |-> "none"]

\* ! p := new Person
Create(p) == /\ p \in Pool \ objs
             /\ objs' = objs \cup {p}
             /\ UNCHANGED <<name, civstat, gender, alive, Marriage>>
             /\ last' = [op |-> "create", self |-> p, pre |-> st]

\* birth(aName, aGender): pre freshUnlinkedPerson; body = 4 assignments.
BirthBody(self, aName, aGender) ==
  /\ name' = [name EXCEPT ![self] = aName]
  /\ gender' = [gender EXCEPT ![self] = aGender]
  /\ civstat' = [civstat EXCEPT ![self] = "single"]
  /\ alive' = [alive EXCEPT ![self] = TRUE]
  /\ UNCHANGED <<objs, Marriage>>
Birth(self, aName, aGender) ==
  /\ self \in objs
  /\ name[self] = Undef /\ civstat[self] = Undef /\ gender[self] = Undef
  /\ alive[self] = Undef /\ Wife(self) = Undef /\ Husband(self) = Undef
  /\ BirthBody(self, aName, aGender)
  /\ last' = [op |-> "birth", self |-> self, aName |-> aName, aGender |-> aGender, pre |-> st]

\* With AtomicBirth, "new Person" is always followed at once by birth(): one step.
CreateAndBirth(p, aName, aGender) ==
  /\ p \in Pool \ objs
  /\ objs' = objs \cup {p}
  /\ name' = [name EXCEPT ![p] = aName]
  /\ gender' = [gender EXCEPT ![p] = aGender]
  /\ civstat' = [civstat EXCEPT ![p] = "single"]
  /\ alive' = [alive EXCEPT ![p] = TRUE]
  /\ UNCHANGED Marriage
  /\ last' = [op |-> "birth", self |-> p, aName |-> aName, aGender |-> aGender, pre |-> st]

\* marry(aSpouse)
Marry(self, aSpouse) ==
  /\ self \in objs
  /\ aSpouse # Undef                                \* pre aSpouseDefined
  /\ alive[self] = TRUE                             \* pre isAlive
  /\ Attr(alive, aSpouse) = TRUE                    \* pre aSpouseAlive
  /\ civstat[self] # "married"                      \* pre isUnmarried
  /\ Attr(civstat, aSpouse) # "married"             \* pre aSpouseUnmarried
  /\ gender[self] # Attr(gender, aSpouse)           \* pre differentGenders
  /\ Marriage' = IF gender[self] = "female" THEN Marriage \cup {<<self, aSpouse>>}
                                           ELSE Marriage \cup {<<aSpouse, self>>}
  /\ civstat' = [civstat EXCEPT ![self] = "married"]
  /\ UNCHANGED <<objs, name, gender, alive>>
  /\ last' = [op |-> "marry", self |-> self, aSpouse |-> aSpouse, pre |-> st]

\* divorce()
Divorce(self) ==
  /\ self \in objs
  /\ civstat[self] = "married"                                        \* pre isMarried
  /\ alive[self] = TRUE                                               \* pre isAlive
  /\ gender[self] = "female" => Attr(alive, Husband(self)) = TRUE     \* pre husbandAlive
  /\ gender[self] = "male" => Attr(alive, Wife(self)) = TRUE          \* pre wifeAlive
  /\ LET spouse == Spouse(self) IN
     /\ spouse # Undef   \* USE would fail on spouse.civstat := ... with spouse undefined
     /\ civstat' = [civstat EXCEPT ![self] = "divorced", ![spouse] = "divorced"]
     /\ Marriage' = IF gender[self] = "female" THEN Marriage \ {<<self, spouse>>}
                                              ELSE Marriage \ {<<spouse, self>>}
  /\ UNCHANGED <<objs, name, gender, alive>>
  /\ last' = [op |-> "divorce", self |-> self, pre |-> st]

\* death()
Death(self) ==
  /\ self \in objs
  /\ alive[self] = TRUE                                               \* pre isAlive
  /\ alive' = [alive EXCEPT ![self] = FALSE]
  /\ IF civstat[self] = "married"
     THEN LET sp == Spouse(self) IN
          /\ sp # Undef
          /\ civstat' = [civstat EXCEPT ![sp] = "widowed"]
          /\ Marriage' = IF gender[self] = "female" THEN Marriage \ {<<self, sp>>}
                                                   ELSE Marriage \ {<<sp, self>>}
     ELSE UNCHANGED <<civstat, Marriage>>
  /\ UNCHANGED <<objs, name, gender>>
  /\ last' = [op |-> "death", self |-> self, pre |-> st]

Next ==
  \/ ~AtomicBirth /\ \E p \in Pool : Create(p)
  \/ ~AtomicBirth /\ \E p \in Pool, n \in Names, g \in Gender : Birth(p, n, g)
  \/ AtomicBirth /\ \E p \in Pool, n \in Names, g \in Gender : CreateAndBirth(p, n, g)
  \/ \E p \in Pool, q \in Pool \cup {Undef} : Marry(p, q)
  \/ \E p \in Pool : Divorce(p)
  \/ \E p \in Pool : Death(p)

Spec == Init /\ [][Next]_vars
Sym == Permutations(Pool)

\* ---- Class invariants (USE: context Person inv ...) ----
AttributesDefined == \A self \in objs :
  name[self] # Undef /\ civstat[self] # Undef /\ gender[self] # Undef /\ alive[self] # Undef
\* nameCapitalThenSmallLetters: REFUSED (String.substring and String.size have no TLC equivalent).
NameIsUnique == \A self \in objs : \A self2 \in objs : self # self2 => name[self] # name[self2]
FemaleHasNoWife == \A self \in objs : gender[self] = "female" => Wife(self) = Undef
MaleHasNoHusband == \A self \in objs : gender[self] = "male" => Husband(self) = Undef
\* Multiplicities of Marriage: wife [0..1], husband [0..1].
MarriageMultiplicity == \A p \in objs :
  Cardinality({l \in Marriage : l[1] = p}) <= 1 /\ Cardinality({l \in Marriage : l[2] = p}) <= 1

\* ---- Postconditions, checked on the step that ran the operation; @pre reads last.pre ----
PostsHold ==
  LET s == last.self IN
  CASE last.op = "birth" ->
         name[s] = last.aName /\ civstat[s] = "single" /\ gender[s] = last.aGender /\ alive[s] = TRUE
    [] last.op = "marry" ->
         /\ civstat[s] = "married"
         /\ gender[s] = "female" => (Husband(s) = last.aSpouse /\ civstat[Husband(s)] = "married")
         /\ gender[s] = "male" => (Wife(s) = last.aSpouse /\ civstat[Wife(s)] = "married")
    [] last.op = "divorce" ->
         /\ civstat[s] = "divorced"
         /\ gender[s] = "female" => (Husband(s) = Undef
                                     /\ civstat[HusbandIn(last.pre, s)] = "divorced")
         /\ gender[s] = "male" => (Wife(s) = Undef /\ civstat[WifeIn(last.pre, s)] = "divorced")
    [] last.op = "death" ->
         /\ alive[s] = FALSE
         /\ (gender[s] = "female" /\ Husband(s) # Undef) =>
              (WifeIn(st, HusbandIn(last.pre, s)) = Undef
               /\ civstat[HusbandIn(last.pre, s)] = "widowed")
         /\ (gender[s] = "male" /\ Wife(s) # Undef) =>
              (HusbandIn(st, WifeIn(last.pre, s)) = Undef
               /\ civstat[WifeIn(last.pre, s)] = "widowed")
    [] OTHER -> TRUE
====
