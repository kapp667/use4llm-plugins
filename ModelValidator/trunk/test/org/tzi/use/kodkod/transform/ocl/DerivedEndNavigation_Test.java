package org.tzi.use.kodkod.transform.ocl;

import org.junit.Test;

/**
 * In USE, a navigation from an undefined object is undefined, also when it goes
 * to a derived association end or comes from one. USE does not evaluate the
 * derive expression then. A forward navigation goes to the derived end, a
 * backward navigation to the other end. The derived end derivedEmployer is an
 * object, the derived end staff is a set.
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
}
