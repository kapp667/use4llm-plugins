package org.tzi.use.kodkod.transform.ocl;

import org.junit.Test;

/**
 * In USE, a navigation from an undefined object is undefined, also when it goes
 * to a derived association end or comes from one. USE does not evaluate the
 * derive expression then. A forward navigation goes to the derived end, a
 * backward navigation to the other end.
 */
public class DerivedEndNavigation_Test extends OCLTest {

	@Test
	public void employeesOfAnAbsentEmployerIncludeNobody() {
		test("employeesOfAnAbsentEmployerIncludeNobody", "Person.allInstances()->select(p | p.employerAC.derivedEmployee->includes(p))->size()");
	}

	@Test
	public void staffOfAnAbsentEmployerIncludesNobody() {
		test("staffOfAnAbsentEmployerIncludesNobody", "Person.allInstances()->select(p | p.employerAC.staff->includes(p))->size()");
	}

	@Test
	public void backwardToASetFromAnUndefinedObject() {
		test("backwardToASetFromAnUndefinedObject", "uf.company.derivedEmployee");
	}

	@Test
	public void forwardToASetFromAnUndefinedObject() {
		test("forwardToASetFromAnUndefinedObject", "uf.company.staff");
	}

	@Test
	public void backwardToAnObjectFromAnUndefinedObject() {
		test("backwardToAnObjectFromAnUndefinedObject", "uf.person.staffEmployer");
	}

	@Test
	public void forwardToAnObjectFromAnUndefinedObject() {
		test("forwardToAnObjectFromAnUndefinedObject", "uf.person.derivedEmployer");
	}

	@Test
	public void backwardToAnObjectFromAnUndefinedObjectWhenEveryDerivedValueIsUndefined() {
		test("backwardToAnObjectFromAnUndefinedObjectWhenEveryDerivedValueIsUndefined", "uf.person.befriender");
	}

	@Test
	public void attributeAfterBackwardToAnObject() {
		test("attributeAfterBackwardToAnObject", "ada.staffEmployer.name");
	}

	@Test
	public void selectByAttributeAfterBackwardToAnObject() {
		test("selectByAttributeAfterBackwardToAnObject", "Person.allInstances()->select(p | p.staffEmployer.name = 'IBM')->size()");
	}

	@Test
	public void attributeAfterForwardToAnObject() {
		test("attributeAfterForwardToAnObject", "ada.checkedEmployer.name");
	}

	@Test
	public void selectByAttributeAfterForwardToAnObject() {
		test("selectByAttributeAfterForwardToAnObject", "Person.allInstances()->select(p | p.checkedEmployer.name = 'IBM')->size()");
	}

	@Test
	public void backwardInAnOperationOfTheOtherClass() {
		test("backwardInAnOperationOfTheOtherClass", "ibm.namedStaff()");
	}

	@Test
	public void forwardInAnOperationOfTheOtherClass() {
		test("forwardInAnOperationOfTheOtherClass", "ibm.namedStaffBySelect()");
	}

	@Test
	public void forwardToASetWhenTheDeriveExpressionIsNull() {
		test("forwardToASetWhenTheDeriveExpressionIsNull", "ibm.nullStaff");
	}

	@Test
	public void forwardToASetHasNoElementWhenTheDeriveExpressionIsNull() {
		test("forwardToASetHasNoElementWhenTheDeriveExpressionIsNull", "ibm.nullStaff->size()");
	}

	@Test
	public void backwardToASetWhenTheDeriveExpressionIsNull() {
		test("backwardToASetWhenTheDeriveExpressionIsNull", "ada.nullEmployer");
	}

	@Test
	public void forwardToASetLeavesOutTheUndefinedElementsOfTheDerivedValue() {
		test("forwardToASetLeavesOutTheUndefinedElementsOfTheDerivedValue", "ibm.staffFriend");
	}
}
