package org.tzi.use.kodkod.transform.ocl;

import org.junit.Test;

/**
 * USE types the navigation to an association end as an object when no range of
 * the end's multiplicity goes above 1. The end noEmployer has the multiplicity
 * 0, so the navigation to it is an object, and undefined.
 */
public class ZeroMultiplicityEnd_Test extends OCLTest {

	@Test
	public void theNavigationToAZeroEndIsUndefined() {
		test("theNavigationToAZeroEndIsUndefined", "ada.noEmployer");
	}

	@Test
	public void everyNavigationToAZeroEndIsUndefined() {
		test("everyNavigationToAZeroEndIsUndefined", "Person.allInstances()->select(p | p.noEmployer.isUndefined())->size()");
	}

	@Test
	public void aZeroEndAsASetIsEmpty() {
		test("aZeroEndAsASetIsEmpty", "Person.allInstances()->select(p | p.noEmployer->isEmpty())->size()");
	}
}
