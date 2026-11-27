package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kodkod.ast.Formula;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.tzi.kodkod.InvariantIndepChecker;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that the invariant independence check reports "Independent" only when
 * USE accepts the object diagram of the solution, in the single and the all mode.
 * The solver is made to disagree with USE by replacing the formula of an invariant.
 */
public class IndepSolutionCheck_Test {

	private static final String REFUSAL = "A::five: Cannot decide the independence: USE does not accept the object diagram of the solution.";

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv five: self.x = 5\n";

	private final List<LoggingEvent> events = new ArrayList<LoggingEvent>();
	private final AppenderSkeleton recorder = new AppenderSkeleton() {
		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
		}

		@Override
		public boolean requiresLayout() {
			return false;
		}

		@Override
		public void close() {
		}
	};

	private Level checkerLevel;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		checkerLevel = Logger.getLogger(InvariantIndepChecker.class).getLevel();
		Logger.getLogger(InvariantIndepChecker.class).addAppender(recorder);
		Logger.getLogger(ObjectDiagramCreator.class).addAppender(recorder);
	}

	@After
	public void tearDown() {
		Logger.getLogger(InvariantIndepChecker.class).removeAppender(recorder);
		Logger.getLogger(ObjectDiagramCreator.class).removeAppender(recorder);
		Logger.getLogger(InvariantIndepChecker.class).setLevel(checkerLevel);
	}

	private IModel configured(MModel mModel, int min, int max) throws Exception {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", min);
		properties.put("Integer_max", max);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		return TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
	}

	private Session sessionFor(MModel mModel) {
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		return session;
	}

	/** The solver holds `five` false in every state, USE holds it true in every state. */
	private IModel disagreeingModel(MModel mModel) throws Exception {
		IModel model = configured(mModel, 5, 5);
		model.getInvariant("A::five").setFormula(Formula.FALSE);
		return model;
	}

	private List<String> messages(String logger, Level atLeast) {
		List<String> result = new ArrayList<String>();
		for (LoggingEvent event : events) {
			if (event.getLoggerName().equals(logger) && event.getLevel().isGreaterOrEqual(atLeast)) {
				result.add(String.valueOf(event.getMessage()));
			}
		}
		return result;
	}

	private int count(List<String> messages, String text) {
		int n = 0;
		for (String message : messages) {
			if (message.equals(text)) {
				n++;
			}
		}
		return n;
	}

	private boolean anyContains(List<String> messages, String part) {
		for (String message : messages) {
			if (message.contains(part)) {
				return true;
			}
		}
		return false;
	}

	private void assertRefused(List<String> checker, List<String> creator) {
		assertEquals("verdict `Independent' printed although USE refuses the solution: " + checker, 0, count(checker, "A::five: Independent"));
		assertEquals("the refusal is logged once: " + checker, 1, count(messages(InvariantIndepChecker.class.getName(), Level.ERROR), REFUSAL));
		assertTrue("USE's own error about the diagram is logged: " + creator, anyContains(creator, "A::five"));
	}

	@Test
	public void singleModeDoesNotReportIndependentWhenUseRefusesTheSolution() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		IModel model = disagreeingModel(mModel);

		new InvariantIndepChecker(sessionFor(mModel)).validate(model, "A", "five");

		assertRefused(messages(InvariantIndepChecker.class.getName(), Level.INFO),
				messages(ObjectDiagramCreator.class.getName(), Level.ERROR));
	}

	@Test
	public void allModeDoesNotReportIndependentWhenUseRefusesTheSolution() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		IModel model = disagreeingModel(mModel);

		new InvariantIndepChecker(sessionFor(mModel)).validate(model);

		assertRefused(messages(InvariantIndepChecker.class.getName(), Level.INFO),
				messages(ObjectDiagramCreator.class.getName(), Level.ERROR));
	}

	@Test
	public void allModeReportsIndependentWhenUseAcceptsTheSolution() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		IModel model = configured(mModel, 4, 5);

		new InvariantIndepChecker(sessionFor(mModel)).validate(model);

		List<String> checker = messages(InvariantIndepChecker.class.getName(), Level.INFO);
		assertEquals(checker.toString(), 1, count(checker, "A::five: Independent"));
		assertTrue(messages(InvariantIndepChecker.class.getName(), Level.WARN).toString(),
				messages(InvariantIndepChecker.class.getName(), Level.WARN).isEmpty());
		assertTrue(messages(ObjectDiagramCreator.class.getName(), Level.WARN).toString(),
				messages(ObjectDiagramCreator.class.getName(), Level.WARN).isEmpty());
	}

	@Test
	public void invariantStatesAreRestoredAfterARefusalInSingleMode() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		IModel model = disagreeingModel(mModel);

		new InvariantIndepChecker(sessionFor(mModel)).validate(model, "A", "five");

		IInvariant five = model.getInvariant("A::five");
		assertFalse(five.isNegated());
		assertTrue(five.isActivated());
	}

	@Test
	public void invariantStatesAreRestoredAfterARefusalInAllMode() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		IModel model = disagreeingModel(mModel);

		new InvariantIndepChecker(sessionFor(mModel)).validate(model);

		IInvariant five = model.getInvariant("A::five");
		assertFalse(five.isNegated());
		assertTrue(five.isActivated());
	}

	@Test
	public void allModeGoesOnWithTheNextInvariantAfterARefusal() throws Exception {
		String spec = SPEC + "context A inv pos: self.x >= 0\n";
		MModel mModel = TestSpecifications.compile(spec);
		IModel model = disagreeingModel(mModel);

		new InvariantIndepChecker(sessionFor(mModel)).validate(model);

		List<String> checker = messages(InvariantIndepChecker.class.getName(), Level.INFO);
		assertEquals(checker.toString(), 0, count(checker, "A::five: Independent"));
		assertEquals(checker.toString(), 1, count(messages(InvariantIndepChecker.class.getName(), Level.ERROR), REFUSAL));
		assertEquals(checker.toString(), 1, count(checker, "A::pos: Dependent") + count(checker, "A::pos: Not independent for given properties"));
	}
}
