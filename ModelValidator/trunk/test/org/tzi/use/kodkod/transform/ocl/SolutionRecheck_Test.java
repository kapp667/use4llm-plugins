package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator;
import org.tzi.use.main.Session;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Checks that an invariant USE finds false in the object diagram of a solution
 * is logged as an error, and that the validation does not end on that solution.
 */
public class SolutionRecheck_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv five: self.x = 5\n";

	private final List<LoggingEvent> errors = new ArrayList<LoggingEvent>();
	private final AppenderSkeleton recorder = new AppenderSkeleton() {
		@Override
		protected void append(LoggingEvent event) {
			if (event.getLevel().isGreaterOrEqual(Level.ERROR)) {
				errors.add(event);
			}
		}

		@Override
		public boolean requiresLayout() {
			return false;
		}

		@Override
		public void close() {
		}
	};

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		Logger.getLogger(ObjectDiagramCreator.class).addAppender(recorder);
	}

	@After
	public void tearDown() {
		Logger.getLogger(ObjectDiagramCreator.class).removeAppender(recorder);
	}

	@Test
	public void invariantThatUseFindsFalseInTheSolutionIsAnError() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 9);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		IModel model = TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
		OutcomeRecorder validator = new OutcomeRecorder();
		validator.validate(model);
		assertEquals("SATISFIABLE", validator.outcome);

		ObjectDiagramCreator creator = new ObjectDiagramCreator(model, session);
		creator.create(validator.relationTuples());
		MSystemState state = session.system().state();
		MObject object = state.allObjects().iterator().next();
		object.state(state).setAttributeValue(object.cls().attribute("x", true), IntegerValue.valueOf(4));

		assertEquals(ObjectDiagramCreator.ErrorType.INVARIANT, creator.hasDiagramErrors());
		assertEquals(1, errors.size());
		assertTrue(String.valueOf(errors.get(0).getMessage()), String.valueOf(errors.get(0).getMessage()).contains("A::five"));
	}

	@Test
	public void validationDoesNotEndOnASolutionThatUseRejects() throws Exception {
		// the translation answers 3, the smallest, and USE answers 20, the first in its hash set
		MModel mModel = TestSpecifications.compile("model M\n"
				+ "class A\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv three: Set{3, 20}->any(i | true) = 3\n");
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 20);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		new UseKodkodModelValidator(session).validate(TestSpecifications.configure(TestSpecifications.transform(mModel), properties));

		MSystemState state = session.system().state();
		for (MClassInvariant invariant : mModel.classInvariants()) {
			assertEquals(invariant.name(), BooleanValue.TRUE, new Evaluator().eval(invariant.expandedExpression(), state));
		}
	}
}
