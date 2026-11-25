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
import org.tzi.kodkod.model.iface.IAssociation;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.impl.SimpleFactory;
import org.tzi.kodkod.model.type.PrimitiveTypeFactory;
import org.tzi.use.kodkod.transform.ModelTransformator;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Checks that the model validator refuses to validate a model whose
 * transformation stopped on an error, instead of searching for a solution of
 * the part that was transformed.
 */
public class PartialModel_Test {

	private static final String SPEC = "model M\n"
			+ "class Person\n"
			+ "end\n"
			+ "association Knows between\n"
			+ "  Person[1] role knower\n"
			+ "  Person[1] role known\n"
			+ "end\n";

	private IModel model;
	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		MModel mModel = USECompiler.compileSpecification(new ByteArrayInputStream(SPEC.getBytes(StandardCharsets.UTF_8)), "M.use",
				new PrintWriter(System.err), new ModelFactory());
		model = new ModelTransformator(new FailingAssociationFactory(), new PrimitiveTypeFactory()).transform(mModel);
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
	}

	@Test
	public void validationReportsNoOutcome() {
		validator.validate(model);
		assertNull(validator.outcome);
	}

	@Test
	public void validationErrorGivesTheReason() {
		StringWriter log = new StringWriter();
		Logger logger = Logger.getLogger(KodkodModelValidator.class);
		WriterAppender appender = new WriterAppender(new PatternLayout("%m%n"), log);
		logger.addAppender(appender);
		try {
			validator.validate(model);
		} finally {
			logger.removeAppender(appender);
		}
		assertTrue(log.toString(), log.toString().contains("Model `M', whose transformation stopped: no association for Knows"));
	}

	private static class FailingAssociationFactory extends SimpleFactory {

		@Override
		public IAssociation createAssociation(IModel model, String name) {
			throw new IllegalStateException("no association for " + name);
		}
	}
}
