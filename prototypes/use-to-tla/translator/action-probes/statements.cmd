-- The cases of StatementEffectTest, in its order, on the state TestSystem builds: O1, O3, O5 of C1,
-- O2, O4, O6 of C2, and the link (O3, O4) of A1. Undo and redo are the shell's, not behaviours.
!create O1 : C1
!create O2 : C2
!create O3 : C1
!create O4 : C2
!create O5 : C1
!create O6 : C2
!insert (O3, O4) into A1
-- testVariableAssignment: simple variable assignment, assignment of new object, assignment of return value
!x := O1.varAssign()
!x := O1.newObject()
!x := O1.s1(0)
-- testAttributeAssignment: basic attribute assignment
!O1.setInt(42)
!O1.setInt(43)
-- testObjectCreation: basic object creation, object creation with mandatory object name
!O1.makeC1()
!O1.createNamed()
-- testObjectDestruction: basic object destruction
!O1.destroyOther(O5)
-- testLinkInsertion: basic link insertion
!O1.link(O2)
-- testLinkDeletion: basic link deletion
!O3.unlink(O4)
-- testConditional: condition = true, condition = false, condition = oclUndefined(Boolean)
!x := O1.cond(true)
!x := O1.cond(false)
!x := O1.condUndef()
-- testIteration: silly example
!x := O1.sum()
-- testOperationCall: failed precondition, failed postcondition, proxy fail
!O1.failEnter()
c
!O1.failExit()
c
!O1.proxyFail()
c
-- testOpEnterOpExit: openter, opexit with missing result value, opexit with correct result value.
-- The test expects the operation to stay on the stack after the failed opexit; the shell runs
-- opexit without undo, so the operation is gone (USE 7.5.0), and the last case enters it again.
!openter O1 u1()
!opexit
!openter O1 u1()
!opexit 'asdf'
exit
