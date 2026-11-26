package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;

/**
 * Checks that Integer_max bounds the elements of a Set(Integer) attribute, as
 * it bounds the values of an Integer attribute.
 */
public class IntegerCollectionBounds_Test {

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 10);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("A_s_min", 1);
		properties.put("A_s_max", 1);
	}

	/**
	 * Validates a class A with a Set(Integer) attribute s and the given
	 * invariant.
	 */
	private void validate(String invariant) {
		String spec = "model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  s : Set(Integer)\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv i: " + invariant + "\n";
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(spec), properties));
	}

	@Test
	public void elementWithinTheIntegerBoundsCanBeInTheSet() {
		validate("self.s->includes(5)");
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void elementAboveTheIntegerMaximumCannotBeInTheSet() {
		validate("self.s->includes(50)");
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}
}
