package org.tzi.use.kodkod.transform.ocl;

import org.junit.Test;

/**
 * In USE, the navigation to an end of multiplicity 0 is typed as an object, so
 * it is undefined, not an empty set.
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

	@Test
	public void theNavigationToAnAssociationClassAcrossAZeroEndIsUndefined() {
		test("theNavigationToAnAssociationClassAcrossAZeroEndIsUndefined", "ada.idle_AC");
	}
}
