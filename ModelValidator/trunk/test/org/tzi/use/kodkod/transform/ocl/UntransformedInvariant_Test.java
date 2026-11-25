package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
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
import org.tzi.kodkod.InvariantIndepChecker;
import org.tzi.kodkod.KodkodModelValidator;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.impl.SimpleFactory;
import org.tzi.kodkod.model.type.PrimitiveTypeFactory;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLGroupRegistry;
import org.tzi.kodkod.ocl.operation.AnyOperationGroup;
import org.tzi.kodkod.ocl.operation.BooleanOperationGroup;
import org.tzi.kodkod.ocl.operation.ClassOperationGroup;
import org.tzi.kodkod.ocl.operation.CollectionConstructorGroup;
import org.tzi.kodkod.ocl.operation.ConditionalOperationGroup;
import org.tzi.kodkod.ocl.operation.IntegerOperationGroup;
import org.tzi.kodkod.ocl.operation.SetOperationGroup;
import org.tzi.kodkod.ocl.operation.VariableOperationGroup;
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

	private static final String SPEC_WITH_TRANSFORMED_INVARIANTS = SPEC
			+ "context Person inv positiveAge: self.age > 0\n"
			+ "context Person inv smallAge: self.age < 10\n";

	private IModel model;
	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		model = transform(SPEC);
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
	}

	/**
	 * Transforms the specification with the operation groups the plugin
	 * registers, so that only the invariants the plugin cannot transform fail,
	 * whichever tests ran before.
	 */
	private static IModel transform(String spec) {
		MModel mModel = USECompiler.compileSpecification(new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8)), "M.use",
				new PrintWriter(System.err), new ModelFactory());
		TypeFactory typeFactory = new PrimitiveTypeFactory();
		OCLGroupRegistry registry = OCLGroupRegistry.INSTANCE;
		registry.unregisterAll();
		registry.registerOperationGroup(new VariableOperationGroup(typeFactory));
		registry.registerOperationGroup(new IntegerOperationGroup(typeFactory));
		registry.registerOperationGroup(new BooleanOperationGroup(typeFactory));
		registry.registerOperationGroup(new ClassOperationGroup(typeFactory));
		registry.registerOperationGroup(new AnyOperationGroup(typeFactory, true));
		registry.registerOperationGroup(new ConditionalOperationGroup(typeFactory));
		registry.registerOperationGroup(new SetOperationGroup(typeFactory));
		registry.registerOperationGroup(new CollectionConstructorGroup(typeFactory));
		return new ModelTransformator(new SimpleFactory(), typeFactory).transform(mModel);
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
		IModel modelWithTransformedInvariants = transform(SPEC_WITH_TRANSFORMED_INVARIANTS);
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
