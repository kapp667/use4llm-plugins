package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidator;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.impl.SimpleFactory;
import org.tzi.kodkod.model.type.PrimitiveTypeFactory;
import org.tzi.use.kodkod.transform.ModelTransformator;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

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

	private IModel model;
	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		MModel mModel = USECompiler.compileSpecification(new ByteArrayInputStream(SPEC.getBytes(StandardCharsets.UTF_8)), "M.use",
				new PrintWriter(System.err), new ModelFactory());
		model = new ModelTransformator(new SimpleFactory(), new PrimitiveTypeFactory()).transform(mModel);
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

	private static class OutcomeRecorder extends KodkodModelValidator {

		private String outcome;

		@Override
		protected void satisfiable() {
			outcome = "SATISFIABLE";
		}

		@Override
		protected void trivially_satisfiable() {
			outcome = "TRIVIALLY_SATISFIABLE";
		}

		@Override
		protected void trivially_unsatisfiable() {
			outcome = "TRIVIALLY_UNSATISFIABLE";
		}

		@Override
		protected void unsatisfiable() {
			outcome = "UNSATISFIABLE";
		}
	}
}
