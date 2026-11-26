package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;

/**
 * Checks that an Integer result outside Integer_min and Integer_max keeps its
 * value, as in OCL, whose Integers have no bounds, through a comparison,
 * {@code =}, {@code <>}, a query operation, an if-then-else, a range and
 * floor, and that {@code =} still holds between equal values that are not
 * Integers, including the size of an undefined Set.
 */
public class IntegerArithmetic_Test {

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		KodkodModelValidatorConfiguration.getInstance().setBitwidth(KodkodModelValidatorConfiguration.DEFAULT_BITWIDTH);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("A_x_min", 1);
		properties.put("A_x_max", 1);
	}

	/**
	 * Validates a class A with an Integer attribute x, a Set(Integer)
	 * attribute s, a query operation shifted(d) that returns x + d, and the
	 * given invariant, with the given Integer_min and Integer_max.
	 */
	private void validate(String invariant, int min, int max) {
		String spec = "model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  x : Integer\n"
				+ "  s : Set(Integer)\n"
				+ "operations\n"
				+ "  shifted(d : Integer) : Integer = self.x + d\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv i: " + invariant + "\n";
		properties.put("Integer_min", min);
		properties.put("Integer_max", max);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(spec), properties));
	}

	@Test
	public void differenceBelowTheIntegerMinimumIsComputed() {
		validate("self.x = 9 and self.x - 100 < -50", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void sumsAboveTheIntegerMaximumWithDifferentValuesAreNotEqual() {
		validate("self.x + 20 = self.x + 30", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void sumsAboveTheIntegerMaximumWithDifferentValuesDiffer() {
		validate("self.x = 5 and self.x + 20 <> self.x + 30", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void queryResultsAboveTheIntegerMaximumWithDifferentValuesAreNotEqual() {
		validate("self.shifted(20) = self.shifted(30)", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void choicesAboveTheIntegerMaximumWithDifferentValuesAreNotEqual() {
		validate("(if self.x >= 0 then self.x + 20 else 0 endif) = (if self.x >= 0 then self.x + 30 else 1 endif)", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void choiceBetweenASumAboveTheIntegerMaximumAndAnAttributeKeepsTheSum() {
		validate("self.x = 5 and (if self.x > 0 then self.x + 20 else self.x endif) = self.x + 20"
				+ " and (if self.x > 0 then self.x + 20 else self.x endif) <> self.x + 30", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void choiceBetweenASumAndAStringEqualsTheString() {
		validate("self.x = 0 and (if self.x > 0 then self.x + 20 else 'a' endif) = 'a'", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void sizesOfAnUndefinedSetAreEqual() {
		properties.put("A_s_min", 0);
		properties.put("A_s_max", 0);
		validate("self.s->size() = self.s->size()", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void rangeUpToASumAboveTheIntegerMaximumIncludesTheIntegersBelowIt() {
		validate("self.x = 5 and Set{0..(self.x + 20)}->includes(10)", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void floorOfASumAboveTheIntegerMaximumKeepsTheSum() {
		validate("self.x = 5 and (self.x + 20).floor() = self.x + 20 and (self.x + 20).floor() <> self.x + 30", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}
}
