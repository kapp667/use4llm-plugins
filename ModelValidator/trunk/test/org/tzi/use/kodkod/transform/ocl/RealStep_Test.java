package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.MapConfiguration;
import org.junit.Test;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.validation.ValidityRule;
import org.tzi.kodkod.validation.ValidityRuleValidator;
import org.tzi.use.kodkod.plugin.gui.model.data.SettingsConfiguration;
import org.tzi.use.kodkod.plugin.gui.util.ChangeConfiguration;
import org.tzi.use.uml.ocl.expr.Expression;

/**
 * The Real values of the search space run from Real_min to Real_max in steps of
 * Real_step and are named to two decimals. A step below 0.01 adds no new value,
 * and a step of zero or less never reaches Real_max, so both the configuration
 * and the check of mv -vc must refuse such a step instead of building values
 * without end.
 */
public class RealStep_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  r : Real\n"
			+ "end\n";

	@Test
	public void aStepOfAHalfIsAccepted() {
		assertFalse(configurationFails(0, 1, 0.5));
	}

	@Test
	public void aZeroStepIsAnError() {
		assertTrue(configurationFails(0, 1, 0.0));
	}

	@Test
	public void aNegativeStepIsAnError() {
		assertTrue(configurationFails(0, 1, -0.5));
	}

	@Test
	public void aStepThatIsNotANumberIsAnError() {
		assertTrue(configurationFails(0, 1, Double.NaN));
	}

	@Test
	public void aStepBelowTheNamePrecisionIsAnError() {
		assertTrue(configurationFails(1, 2, 1e-17));
	}

	@Test(timeout = 10000)
	public void validityCheckReportsAZeroStep() throws Exception {
		assertTrue(stepRuleViolated(0, 1, 0.0));
	}

	@Test(timeout = 10000)
	public void validityCheckReportsAStepBelowTheNamePrecision() throws Exception {
		assertTrue(stepRuleViolated(1, 2, 1e-17));
	}

	@Test(timeout = 10000)
	public void validityCheckAcceptsAStepOfAHalf() throws Exception {
		assertFalse(stepRuleViolated(0, 1, 0.5));
	}

	private boolean configurationFails(double min, double max, double step) {
		IModel model = TestSpecifications.transform(SPEC);
		PropertyConfigurationVisitor visitor = new PropertyConfigurationVisitor(new MapConfiguration(properties(min, max, step)),
				new PrintWriter(new StringWriter(), true));
		model.accept(visitor);
		return visitor.containErrors();
	}

	private boolean stepRuleViolated(double min, double max, double step) throws Exception {
		IModel model = TestSpecifications.transform(SPEC);
		SettingsConfiguration settings = new SettingsConfiguration(model);
		ChangeConfiguration.toSettings(model, new MapConfiguration(properties(min, max, step)), settings);
		return new ValidityRuleValidator().validate(settings, model, new ArrayList<Expression>())
				.containsKey(ValidityRule.REAL_SETTINGS_STEP_GREATER_THAN_ZERO_AND_LESS_THAN_OR_EQUAL_MAX_MINUS_MIN);
	}

	private static Map<String, Object> properties(double min, double max, double step) {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("Real_min", min);
		properties.put("Real_max", max);
		properties.put("Real_step", step);
		return properties;
	}
}
