package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.StringWriter;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.InvariantIndepChecker;
import org.tzi.kodkod.KodkodModelValidator;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;

/**
 * Checks that the model validator refuses to validate a model with an
 * invariant it cannot transform, instead of searching for a solution that
 * ignores that invariant.
 */
public class UntransformedInvariant_Test {

	private static final String SPEC = "model M\n"
			+ "class Person\n"
			+ "attributes\n"
			+ "  age : Integer\n"
			+ "end\n"
			+ "constraints\n"
			+ "context Person inv halfAge: self.age / 2 = 1\n";

	private static final String SPEC_WITH_TRANSFORMED_INVARIANTS = SPEC
			+ "context Person inv positiveAge: self.age > 0\n"
			+ "context Person inv smallAge: self.age < 10\n";

	private IModel model;
	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		model = TestSpecifications.transform(SPEC);
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
	}

	@Test
	public void validationReportsNoOutcome() {
		validator.validate(model);
		assertNull(validator.outcome);
	}

	@Test
	public void validationErrorNamesTheInvariant() {
		StringWriter log = new StringWriter();
		Logger logger = Logger.getLogger(KodkodModelValidator.class);
		WriterAppender appender = new WriterAppender(new PatternLayout("%m%n"), log);
		logger.addAppender(appender);
		try {
			validator.validate(model);
		} finally {
			logger.removeAppender(appender);
		}
		assertTrue(log.toString(), log.toString().contains("Person::halfAge"));
	}

	@Test
	public void invariantIndependenceCheckReportsTheRefusalOnce() {
		IModel modelWithTransformedInvariants = TestSpecifications.transform(SPEC_WITH_TRANSFORMED_INVARIANTS);
		StringWriter log = new StringWriter();
		Logger logger = Logger.getLogger(KodkodModelValidator.class);
		WriterAppender appender = new WriterAppender(new PatternLayout("%m%n"), log);
		logger.addAppender(appender);
		try {
			new InvariantIndepChecker(null).validate(modelWithTransformedInvariants);
		} finally {
			logger.removeAppender(appender);
		}
		assertEquals(log.toString(), 1, log.toString().split("Cannot validate the model", -1).length - 1);
	}
}
