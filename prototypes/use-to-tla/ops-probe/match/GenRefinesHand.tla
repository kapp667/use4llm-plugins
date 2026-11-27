---- MODULE GenRefinesHand ----
\* Every behaviour of the generated spec (UseOps, from civstat.use and settings/civstat.settings)
\* is a behaviour of the hand translation (../Civstat.tla, AtomicBirth = TRUE), under the mapping
\* below. TLC checks it as the property HandSpec on UseOps.
EXTENDS UseOps

Lit == ("CivilStatus::single" :> "single" @@ "CivilStatus::married" :> "married"
        @@ "CivilStatus::divorced" :> "divorced" @@ "CivilStatus::widowed" :> "widowed"
        @@ "Gender::female" :> "female" @@ "Gender::male" :> "male")
E(v) == IF v = Undef THEN Undef ELSE Lit[v]
\* A generated state as the hand spec's state record.
H(s) == [objs |-> s.objs, name |-> s.att["Person::name"],
         civstat |-> [p \in Pool_Person |-> E(s.att["Person::civstat"][p])],
         gender |-> [p \in Pool_Person |-> E(s.att["Person::gender"][p])],
         alive |-> s.att["Person::alive"], Marriage |-> s.lnk["Marriage"]]
\* The hand spec's last: its pre of a new+birth step is the state before new, the generated one's
\* the state after new (USE's @pre of birth()).
HLast ==
  CASE last.step = "none" -> [op |-> "none"]
    [] last.op = "Person::birth" -> [op |-> "birth", self |-> last.self, aName |-> last.args[1],
                                     aGender |-> E(last.args[2]),
                                     pre |-> [H(last.pre) EXCEPT !.objs = @ \ {last.self}]]
    [] last.op = "Person::marry" -> [op |-> "marry", self |-> last.self, aSpouse |-> last.args[1], pre |-> H(last.pre)]
    [] last.op = "Person::divorce" -> [op |-> "divorce", self |-> last.self, pre |-> H(last.pre)]
    [] last.op = "Person::death" -> [op |-> "death", self |-> last.self, pre |-> H(last.pre)]

Hand == INSTANCE Civstat WITH Pool <- Pool_Person, Names <- {"Ada", "Bob"}, AtomicBirth <- TRUE,
          objs <- st.objs, name <- H(st).name, civstat <- H(st).civstat, gender <- H(st).gender,
          alive <- H(st).alive, Marriage <- H(st).Marriage, last <- HLast
HandSpec == Hand!Spec
====
