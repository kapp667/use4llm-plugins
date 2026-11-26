package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;

/**
 * Checks that the order and the concatenation of String values, which the
 * transformation does not encode, are refused instead of being translated as
 * Integer operations.
 */
public class StringOperation_Test extends OCLTest {

	@Test
	public void lessOnStringsIsRefused() {
		assertRefused("'a' < 'b'", "('a' < 'b')");
	}

	@Test
	public void lessOrEqualOnStringsIsRefused() {
		assertRefused("'a' <= 'b'", "('a' <= 'b')");
	}

	@Test
	public void greaterOnStringsIsRefused() {
		assertRefused("'a' > 'b'", "('a' > 'b')");
	}

	@Test
	public void greaterOrEqualOnStringsIsRefused() {
		assertRefused("'a' >= 'b'", "('a' >= 'b')");
	}

	@Test
	public void lessOnAStringAttributeIsRefused() {
		assertRefused("Person.allInstances()->forAll(p | p.name < 'b')", "(p.name < 'b')");
	}

	@Test
	public void plusOnStringsIsRefused() {
		assertRefused("'a' + 'b' = 'ab'", "('a' + 'b')");
	}

	private void assertRefused(String ocl, String refusedPart) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertEquals("Cannot transform `" + refusedPart + "': comparing (<, <=, >, >=) or concatenating (+) String values is not supported.",
					e.getMessage());
		}
	}
}
