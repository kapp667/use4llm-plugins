package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.KodkodSolver;
import org.tzi.kodkod.model.iface.IModel;

/**
 * Checks that an Integer result outside Integer_min and Integer_max keeps its
 * value, as in OCL, whose Integers have no bounds, through a comparison,
 * {@code =}, {@code <>}, a query operation, an if-then-else, a range, floor
 * and a collection that holds it, and that {@code =} still holds between
 * equal values that are not Integers. A result beyond the bitwidth keeps its
 * value too, up to the larger of the bitwidth and the maximum bitwidth, and
 * the solver adds an atom for each value outside the Integer bounds that a
 * collection or a range may hold, up to the maximum number of computed
 * Integer values; above either limit, the solver refuses the model. This
 * class also checks that the size of an undefined Set is undefined, as in
 * USE: {@code =} holds between such a size and itself,
 * {@code oclIsUndefined()} holds for it, and neither {@code size() + 1 = 1}
 * nor {@code size() >= 0} holds.
 */
public class IntegerArithmetic_Test {

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		KodkodModelValidatorConfiguration.getInstance().setBitwidth(KodkodModelValidatorConfiguration.DEFAULT_BITWIDTH);
		KodkodModelValidatorConfiguration.getInstance().setMaxBitwidth(KodkodModelValidatorConfiguration.DEFAULT_MAX_BITWIDTH);
		KodkodModelValidatorConfiguration.getInstance().setMaxComputedIntegers(KodkodModelValidatorConfiguration.DEFAULT_MAX_COMPUTED_INTEGERS);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("A_x_min", 1);
		properties.put("A_x_max", 1);
	}

	@After
	public void tearDown() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setBitwidth(KodkodModelValidatorConfiguration.DEFAULT_BITWIDTH);
		KodkodModelValidatorConfiguration.getInstance().setMaxBitwidth(KodkodModelValidatorConfiguration.DEFAULT_MAX_BITWIDTH);
		KodkodModelValidatorConfiguration.getInstance().setMaxComputedIntegers(KodkodModelValidatorConfiguration.DEFAULT_MAX_COMPUTED_INTEGERS);
	}

	/**
	 * Returns the configured model of a class A with an Integer attribute x, a
	 * Set(Integer) attribute s, a query operation shifted(d) that returns
	 * x + d, and the given invariant, with the given Integer_min and
	 * Integer_max.
	 */
	private IModel model(String invariant, int min, int max) {
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
		return TestSpecifications.configure(TestSpecifications.transform(spec), properties);
	}

	private void validate(String invariant, int min, int max) {
		validator.validate(model(invariant, min, max));
	}

	/**
	 * Returns the message of the IllegalStateException that KodkodSolver.solve
	 * throws for model(invariant, min, max); fails the test if it throws none.
	 */
	private String refusal(String invariant, int min, int max) throws Exception {
		IModel model = model(invariant, min, max);
		try {
			new KodkodSolver().solve(model);
		} catch (IllegalStateException e) {
			return e.getMessage();
		}
		fail("the solver did not refuse the model");
		return null;
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
	public void sizeOfAnUndefinedSetIsUndefined() {
		properties.put("A_s_min", 0);
		properties.put("A_s_max", 0);
		validate("self.s->size().oclIsUndefined()", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void sizeOfAnUndefinedSetPlusOneIsNotOne() {
		properties.put("A_s_min", 0);
		properties.put("A_s_max", 0);
		validate("self.s->size() + 1 = 1", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void sizeOfAnUndefinedSetIsNotAtLeastZero() {
		properties.put("A_s_min", 0);
		properties.put("A_s_max", 0);
		validate("self.s->size() >= 0", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
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

	@Test
	public void sumAboveTheBitwidthIsComputed() {
		validate("self.x = 100 and self.x + 100 > 127", -128, 127);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void sumAboveTheBitwidthIsNotNegative() {
		validate("self.x = 100 and self.x + 100 < 0", -128, 127);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void productAboveTheBitwidthIsComputed() {
		validate("self.x = 50 and self.x * self.x > 2000", -100, 100);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void sumAboveTheMaximumBitwidthIsRefusedWithTheBitwidthItNeeds() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setMaxBitwidth(8);
		String message = refusal("self.x = 100 and self.x + 100 > 127", -128, 127);
		assertTrue(message, message.contains("need bitwidth 9,"));
	}

	@Test
	public void sumWithinTheBitwidthAboveTheMaximumBitwidthIsComputed() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setBitwidth(16);
		KodkodModelValidatorConfiguration.getInstance().setMaxBitwidth(8);
		validate("self.x = 100 and self.x + 100 > 127", -128, 127);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void productAboveTheLargestBitwidthIsRefused() throws Exception {
		String message = refusal("self.x * 100000 * 100000 > 0", 0, 10);
		assertTrue(message, message.contains("need a bitwidth above 32"));
	}

	@Test
	public void setWithinTheIntegerBoundsDoesNotIncludeASumAboveThem() {
		validate("self.s->includes(self.x + 20)", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void setOfASumAboveTheIntegerMaximumIsNotEmpty() {
		validate("self.x = 5 and Set{self.x + 20}->size() = 0", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void includingASumAboveTheIntegerMaximumAddsAnElement() {
		validate("self.x = 5 and self.s->including(self.x + 20)->size() = self.s->size()", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void setsOfSumsAboveTheIntegerMaximumWithDifferentValuesAreNotEqual() {
		validate("self.x = 5 and Set{self.x + 20} = Set{self.x + 30}", 0, 10);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void rangeUpToASumAboveTheIntegerMaximumHoldsEveryIntegerOfIt() {
		validate("self.x = 5 and Set{0..(self.x + 20)}->size() = 26", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void inclusionOfAProductAboveTheLargestBitwidthIsRefusedForItsBitwidth() throws Exception {
		String message = refusal("self.s->includes(self.x * 100000 * 100000)", 0, 10);
		assertTrue(message, message.contains("need a bitwidth above 32"));
	}

	@Test
	public void setOfASumAboveTheMaximumNumberOfComputedIntegersIsRefused() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setMaxComputedIntegers(5);
		String message = refusal("self.x = 5 and Set{self.x + 20}->size() = 0", 0, 10);
		assertTrue(message, message.contains("more than 5 Integer values"));
	}

	@Test
	public void rangeElementsTimesTwoWithinTheIntegerBoundsAreComputed() {
		validate("Set{1..5}->exists(e | self.s->includes(e * 2))", -10, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void rangeElementsPlusOneWithinTheIntegerBoundsAreComputed() {
		validate("Set{1..5}->collect(e | e + 1)->includes(self.x)", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void sizeOfARangeWithinTheIntegerBoundsIsComputed() {
		validate("self.s->includes(Set{1..5}->size())", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void collectedRangeElementsTimesTwoWithinTheIntegerBoundsAreComputed() {
		validate("Set{1..5}->collect(e | e + 1)->exists(f | self.s->includes(f * 2))", 0, 20);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void rangeOfAnInactiveInvariantNeedsNoComputedIntegers() {
		properties.put("A_wide", "inactive");
		validate("true\ncontext A inv wide: Set{1..5000}->notEmpty()", 0, 10);
		assertEquals("SATISFIABLE", validator.outcome);
	}
}
