-- TLC's path for NameIsUnique, step by step in USE
! p1 := new Person
! p1.birth('Ada', #female)
! p2 := new Person
! p2.birth('Ada', #female)
check
exit
