-- TestSystem's objects and its link (O3, O4), as in statements.cmd.
!create O1 : C1
!create O2 : C2
!create O3 : C1
!create O4 : C2
!insert (O3, O4) into A1
-- stale-read: int is 43
!O1.setTwice()
-- undef-target: USE fails, nothing changes
!O1.setOther(null)
-- dup-link: the first insertion succeeds, the second and the deletion of a missing link fail
!O1.link(O2)
!O1.link(O2)
!O3.unlink(O2)
-- nofail: the link (O1, O2) exists, so the call fails and int stays 43
!O1.linkThenSet(O2)
-- testLinkInsertion towards objects already linked: USE inserts, and its check reports that A1's
-- 0..1 ends are violated (mult)
!O3.link(O2)
exit
