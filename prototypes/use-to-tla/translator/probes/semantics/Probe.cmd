!create p1 : P
!create p2 : P
!create p3 : Q
!create p4 : P
!set p1.i := -7
!set p1.j := 2
!set p1.b := true
!set p1.s := 'a'
!set p1.c := #red
!set p1.q := p3
!set p2.i := 7
!set p2.j := -2
!set p2.b := false
!set p2.s := 'bb'
!set p2.q := p1
!set p3.i := 3
!set p3.j := 0
!set p3.c := #green
!set p4.i := 3
!insert (p1, p4) into Link
!insert (p1, p2) into Link
!insert (p1, p3) into Link
!insert (p2, p1) into Pair
