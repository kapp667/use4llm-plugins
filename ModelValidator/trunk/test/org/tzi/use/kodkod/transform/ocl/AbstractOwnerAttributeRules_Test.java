package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * mv -vc applies the attribute rules to attributes declared on an abstract class: the
 * objects that hold the attribute are those of its concrete subclasses, so a maximum
 * that they cannot reach is invalid. An abstract class cannot have objects of its own.
 */
public class AbstractOwnerAttributeRules_Test extends MvCommandSession {

	private static final String ABSTRACT = "model Abs\n"
			+ "abstract class A\nattributes\n  x : Integer\nend\n"
			+ "class B < A\nend\n";

	private static final String CONCRETE = "model AbsC\n"
			+ "class A\nattributes\n  x : Integer\nend\n"
			+ "class B < A\nend\n";

	private static final String RULE = "ATTRIBUTE_SETTINGS_MAX_STAR_OR_LESS_THAN_OR_EQUAL_CLASS_MAX";

	private static final String MAX_LINE = "A_x_max (2) should be less than or equal 1 "
			+ "(the maxima of A and its subclasses together).";

	private static String configuration(String name, int aMax, int xMin, int xMax) {
		return "[" + name + "]\nInteger_min = 0\nInteger_max = 3\n"
				+ "A_min = 0\nA_max = " + aMax + "\nB_min = 0\nB_max = 1\n"
				+ "A_x_min = " + xMin + "\nA_x_max = " + xMax + "\n";
	}

	@Test
	public void attributeMaxAboveTheSubclassMaximaOfAnAbstractOwnerIsInvalid() throws Exception {
		open(ABSTRACT);
		String output = configCheck(file("abs.properties", configuration("absbad", 1, 0, 2)));
		assertTrue(output, output.contains("Config absbad is invalid."));
		assertFalse(output, output.contains("Config absbad is valid."));
		assertTrue(output, output.contains("Rule " + RULE + " is violated."));
		assertTrue(output, output.contains(MAX_LINE));
		assertTrue(output, output.contains("Set A_x_max to 0."));
		assertTrue(output, output.contains("Set A_x_max to 1."));
		assertTrue(output, output.contains("Set A_x_max to -1."));
	}

	@Test
	public void noFixRaisesTheMaximumOfAnAbstractClass() throws Exception {
		open(ABSTRACT);
		String output = configCheck(file("abs.properties", configuration("absbad", 1, 0, 2)));
		assertTrue(output, output.contains("Config absbad is invalid."));
		assertFalse(output, output.contains("Set A_max to 2."));
	}

	@Test
	public void attributeMaxWithinTheSubclassMaximaOfAnAbstractOwnerIsValid() throws Exception {
		open(ABSTRACT);
		String output = configCheck(file("abs.properties", configuration("absok", 1, 0, 1)));
		assertTrue(output, output.contains("Config absok is valid."));
		assertFalse(output, output.contains(RULE));
	}

	@Test
	public void attributeMaxCountsTheOwnerAndItsSubclassesWhenTheOwnerIsConcrete() throws Exception {
		open(CONCRETE);
		String output = configCheck(file("absc.properties",
				configuration("both", 1, 0, 2) + "\n" + configuration("none", 0, 0, 2)));
		assertTrue(output, output.contains("Config both is valid."));
		assertTrue(output, output.contains("Config none is invalid."));
		assertTrue(output, output.contains("Rule " + RULE + " is violated."));
		assertTrue(output, output.contains(MAX_LINE));
	}

	@Test
	public void attributeMinAboveMaxOfAnAbstractOwnerIsInvalid() throws Exception {
		open(ABSTRACT);
		String output = configCheck(file("abs.properties", configuration("absmin", 1, 2, 1)));
		assertTrue(output, output.contains("Config absmin is invalid."));
		assertTrue(output, output.contains("Rule ATTRIBUTE_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX is violated."));
	}
}
