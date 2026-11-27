mv ? enable
mv -validate pets-sat.properties
mv ? Person.allInstances()->size()
mv ? Person.allInstances()->select(p | p.age = 30)->size()
mv ? Person.allInstances()->forAll(p | p.age >= 18)
info state
check
exit
