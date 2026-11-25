package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;

/**
 * Checks that an expression whose value is a Real, or a collection of Reals, is
 * refused, and that a type test against Real is refused too.
 */
public class RealValue_Test extends OCLTest {

	@Test
	public void realLiteralIsRefused() {
		assertRefused("1.5 = 1", "1.5");
	}

	@Test
	public void variableOfTypeRealIsRefused() {
		assertRefused("let x : Real = 1 in x > 0", "x");
	}

	@Test
	public void collectionOfRealsIsRefused() {
		assertRefused("oclEmpty(Set(Real))->isEmpty()", "oclEmpty(Set(Real))");
	}

	@Test
	public void typeTestAgainstRealIsRefused() {
		assertRefused("1.oclIsKindOf(Real)", "Real");
	}

	@Test
	public void selectionOfRealsIsRefused() {
		assertRefused("Set{1}->selectByKind(Real)->isEmpty()", "Set {1}->selectByKind(Real)");
	}

	@Test
	public void integerArithmetic() {
		test("integerArithmetic", "1 + 2 = 3");
	}

	private void assertRefused(String ocl, String refusedPart) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertEquals("Cannot transform `" + refusedPart + "': Real values are not supported.", e.getMessage());
		}
	}
}
