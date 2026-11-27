package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;

/**
 * USE answers a type test from the runtime type of the value, and computes that type
 * for a collection from its elements. The translation does not, so a type test whose
 * source or target type is a collection is refused rather than answered.
 */
public class CollectionTypeTest_Test extends OCLTest {

	@Test
	public void kindOfASetOfIntegersIsRefused() {
		assertRefused("Set{1, 2}->oclIsKindOf(Set(Integer))", "Set {1,2}->oclIsKindOf(Set(Integer))");
	}

	@Test
	public void typeOfASetOfIntegersIsRefused() {
		assertRefused("Set{1, 2}->oclIsTypeOf(Set(Integer))", "Set {1,2}->oclIsTypeOf(Set(Integer))");
	}

	@Test
	public void kindOfAllInstancesIsRefused() {
		assertRefused("Person.allInstances()->oclIsKindOf(Set(Person))", "Person.allInstances->oclIsKindOf(Set(Person))");
	}

	@Test
	public void kindOfASetAgainstAnElementTypeIsRefused() {
		assertRefused("Set{1}->oclIsKindOf(OclAny)", "Set {1}->oclIsKindOf(OclAny)");
	}

	@Test
	public void kindOfANavigationAsASetIsRefused() {
		assertRefused("Person.allInstances()->select(p | p.employerAC->oclIsKindOf(Set(Company)))->size()",
				"p.employerAC.oclIsKindOf(Set(Company))");
	}

	@Test
	public void castOfASetIsRefused() {
		assertRefused("Set{1}->oclAsType(Set(Integer))->size()", "Set {1}.oclAsType(Set(Integer))");
	}

	@Test
	public void kindOfAnIntegerAgainstASetTypeIsRefused() {
		assertRefused("1.oclIsKindOf(Set(Integer))", "1.oclIsKindOf(Set(Integer))");
	}

	@Test
	public void typeOfAnObjectAgainstABagTypeIsRefused() {
		assertRefused("Person.allInstances()->forAll(p | not p.oclIsTypeOf(Bag(Person)))", "p.oclIsTypeOf(Bag(Person))");
	}

	@Test
	public void elementwiseKindOfAgainstASetTypeIsRefused() {
		assertRefused("Set{1, 2}.oclIsKindOf(Set(Integer))", "$e.oclIsKindOf(Set(Integer))");
	}

	@Test
	public void kindOfAnElementAgainstItsTypeIsTranslated() {
		test("kindOfAnElementAgainstItsTypeIsTranslated", "Set{1, 2}.oclIsKindOf(Integer)");
	}

	private void assertRefused(String ocl, String refusedPart) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertTrue(e.getMessage(), e.getMessage().startsWith("Cannot transform `" + refusedPart + "':"));
		}
	}
}
