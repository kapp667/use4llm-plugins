package org.tzi.use.kodkod.transform.ocl;

import org.junit.Test;

/**
 * USE reads a missing 0..1 link as undefined, not as an empty set, so set operations
 * that take such a navigation as an argument see undefined. Only the conversion of the
 * navigation itself to a set, with an arrow, yields the empty set.
 */
public class ZeroOrOneNavigation_Test extends OCLTest {

	@Test
	public void includingAnAbsentEmployerAddsUndefined() {
		test("includingAnAbsentEmployerAddsUndefined", "Person.allInstances()->select(p | Company.allInstances()->including(p.employerAC)->size() = 3)->size()");
	}

	@Test
	public void excludingAnAbsentEmployer() {
		test("excludingAnAbsentEmployer", "Person.allInstances()->select(p | Company.allInstances()->excluding(p.employerAC)->size() = 3)->size()");
	}

	@Test
	public void anAbsentEmployerIsNotIncludedInItsSet() {
		test("anAbsentEmployerIsNotIncludedInItsSet", "Person.allInstances()->forAll(p | p.employerAC->includes(p.employerAC))");
	}

	@Test
	public void anAbsentEmployerIsExcludedFromItsSet() {
		test("anAbsentEmployerIsExcludedFromItsSet", "Person.allInstances()->select(p | p.employerAC->excludes(p.employerAC))->size()");
	}

	@Test
	public void countOfAnAbsentEmployer() {
		test("countOfAnAbsentEmployer", "Person.allInstances()->select(p | p.employerAC->count(p.employerAC) = 0)->size()");
	}

	@Test
	public void unionWithASetOfAnAbsentEmployer() {
		test("unionWithASetOfAnAbsentEmployer", "Person.allInstances()->select(p | p.employerAC->union(Set{p.employerAC})->size() = 1)->size()");
	}

	@Test
	public void anAbsentEmployerAsASetIsEmpty() {
		test("anAbsentEmployerAsASetIsEmpty", "Person.allInstances()->select(p | p.employerAC->isEmpty())->size()");
	}

	@Test
	public void anAbsentEmployerIsUndefined() {
		test("anAbsentEmployerIsUndefined", "Person.allInstances()->select(p | p.employerAC.isUndefined())->size()");
	}

	@Test
	public void anAbsentEmployerEqualsNull() {
		test("anAbsentEmployerEqualsNull", "Person.allInstances()->select(p | p.employerAC = null)->size()");
	}

	@Test
	public void collectOfEmployers() {
		test("collectOfEmployers", "Person.allInstances()->collect(p | p.employerAC)->asSet()");
	}

	@Test
	public void employeesOfAnAbsentEmployer() {
		test("employeesOfAnAbsentEmployer", "Person.allInstances()->select(p | p.employerAC.employeeAC->isEmpty())->size()");
	}
}
