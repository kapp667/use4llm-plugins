package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;

/**
 * Checks that any() on a Set or Bag is refused: when several elements satisfy
 * its body, USE returns the first one its hash-based collection iterates, which
 * the translation does not reproduce.
 */
public class SetAny_Test extends OCLTest {

	@Test
	public void anyOnASetOfIntegersIsRefused() {
		assertRefused("Set{3, 20}->any(i | true)", "Set {3,20}->any(i : Integer | true)");
	}

	@Test
	public void anyOnObjectsIsRefused() {
		assertRefused("Person.allInstances()->any(p | p.age > 1)", "Person.allInstances->any(p : Person | (p.age > 1))");
	}

	@Test
	public void anyOnABagIsRefused() {
		assertRefused("Bag{'a', 'b'}->any(s | true)", "Bag {'a','b'}->any(s : String | true)");
	}

	private void assertRefused(String ocl, String refusedPart) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertEquals("Cannot transform `" + refusedPart + "': when several elements satisfy its body, USE returns"
					+ " the first one its hash-based collection iterates, which the translation does not reproduce.", e.getMessage());
		}
	}
}
