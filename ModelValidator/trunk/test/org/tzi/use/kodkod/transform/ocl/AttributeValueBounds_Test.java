package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;

/**
 * Checks that Class_attr_max, the maximum number of objects whose attribute is
 * defined, sets no limit when it is -1 and limits that number otherwise.
 */
public class AttributeValueBounds_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n";

	private static final String DEFINED = SPEC
			+ "constraints\n"
			+ "context A inv definedX: self.x.isDefined()\n";

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("Integer_min", -10);
		properties.put("Integer_max", 10);
		properties.put("A_min", 2);
		properties.put("A_max", 2);
		properties.put("A_x_min", 0);
	}

	@Test
	public void noLimitOnDefinedValuesIsSatisfiable() {
		properties.put("A_x_max", -1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(DEFINED), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void limitBelowTheDefinedValuesAnInvariantNeedsIsUnsatisfiable() {
		properties.put("A_x_max", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(DEFINED), properties));
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void limitAtTheDefinedValuesAnInvariantNeedsIsSatisfiable() {
		properties.put("A_x_max", 2);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(DEFINED), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}
}
