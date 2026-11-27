# Operations probe: civstat by hand, as a translator would

Throwaway. Local only. Source model: `examples/soil/civstat/civstat.use` of USE 7.5.0 (copied here).
`Civstat.tla` is a hand translation that follows fixed rules, one per construct:

| USE | TLA+ |
| --- | --- |
| objects of a class | a finite pool of TLC model values (`Pool`), `objs` = created ones |
| attribute | a function `[Pool -> values]`, `Undef` until assigned (`Undef` is a model value) |
| association | a set of tuples; navigation to a 0..1 end gives the object or `Undef` |
| `new C` | action `Create(p)` |
| SOIL body | an action; every variable the body does not assign is `UNCHANGED` |
| `pre` | a conjunct of the action (a failing pre means USE refuses the call) |
| `post`, `@pre` | invariant `PostsHold` over the step: `last.pre` holds the state before the call |
| class invariant | `INVARIANT` |
| multiplicity | `INVARIANT MarriageMultiplicity` |
| String `substring`, `size` | refused (`nameCapitalThenSmallLetters`) |
| environment | `Next` = any operation, any object, any argument from finite domains |

## Results (measured)

| Run | Result |
| --- | --- |
| `every-state.cfg`: invariants in every state | `attributesDefined` fails after `new Person`, before `birth()` |
| `atomic-birth.cfg`: `new` + `birth()` as one step | `nameIsUnique` fails: two `birth('Ada', ...)` |
| replay of that path in USE (`replay-nameIsUnique.cmd`) | USE: `Person::nameIsUnique: FAILED` |
| other invariants + all posts, pool 3 | no error, 11,161 distinct states, 1 s |
| pool 4 | no error, 524,545 states, 9 s |
| pool 5 | no error, 22,701,481 states, 6 min 27 s |
| pool 5 with `SYMMETRY Permutations(Pool)` (`CivstatSym.tla`, `sym-5.cfg`) | no error, 215,729 states, 10 s |
| `CivstatBroken.tla`: marry() no longer sets the spouse's civstat | `PostsHold` fails at step 4, `Marry(p2,p1)` |

## What it taught

1. SOIL bodies translate mechanically, frame included: what the body does not assign is unchanged.
2. When invariants must hold is not in the USE model. Checking them in every state fails at once
   (a fresh object has undefined attributes). Grouping `new` with `birth()` was a choice made by hand.
3. The environment is not in the USE model either: pool sizes, value domains (`Names`), which
   operations the environment may call. A translator needs a config file for it, as the ModelValidator
   needs a properties file for its bounds. Translator(model, config) stays deterministic.
4. TLC rules: `Undef` and object identities must be model values (equality with any value; symmetry).
5. TLC found a defect USE's one-state check never shows by itself: `birth()` lets two people share a name.

Rerun: `java -cp ../tools/tla2tools.jar tlc2.TLC -config sym-5.cfg CivstatSym`
