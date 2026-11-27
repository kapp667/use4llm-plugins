---- MODULE HandRefinesGen ----
\* Every behaviour of the hand translation (../Civstat.tla, AtomicBirth = TRUE) is a behaviour of
\* the generated spec (UseOps), under the inverse of GenRefinesHand's mapping.
EXTENDS Civstat, TLC
CONSTANTS O_Person1, O_Person2, O_Person3, Fail, Unset

Lit == ("single" :> "CivilStatus::single" @@ "married" :> "CivilStatus::married"
        @@ "divorced" :> "CivilStatus::divorced" @@ "widowed" :> "CivilStatus::widowed")
Gen == ("female" :> "Gender::female" @@ "male" :> "Gender::male")
G(s) == [objs |-> s.objs, cls |-> [p \in Pool |-> "Person"],
         att |-> ("Person::alive" :> s.alive
                  @@ "Person::civstat" :> [p \in Pool |-> IF s.civstat[p] = Undef THEN Undef ELSE Lit[s.civstat[p]]]
                  @@ "Person::gender" :> [p \in Pool |-> IF s.gender[p] = Undef THEN Undef ELSE Gen[s.gender[p]]]
                  @@ "Person::name" :> s.name),
         lnk |-> ("Marriage" :> s.Marriage)]
GLast ==
  CASE last.op = "none" -> [step |-> "none", op |-> "none"]
    [] last.op = "birth" -> [step |-> "new+call", op |-> "Person::birth", self |-> last.self,
                             args |-> <<last.aName, Gen[last.aGender]>>,
                             pre |-> [G(last.pre) EXCEPT !.objs = @ \cup {last.self}]]
    [] last.op = "marry" -> [step |-> "call", op |-> "Person::marry", self |-> last.self,
                             args |-> <<last.aSpouse>>, pre |-> G(last.pre)]
    [] last.op = "divorce" -> [step |-> "call", op |-> "Person::divorce", self |-> last.self, args |-> <<>>, pre |-> G(last.pre)]
    [] last.op = "death" -> [step |-> "call", op |-> "Person::death", self |-> last.self, args |-> <<>>, pre |-> G(last.pre)]

GenSpec == INSTANCE UseOps WITH st <- G(st), last <- GLast
GSpec == GenSpec!Spec
====
