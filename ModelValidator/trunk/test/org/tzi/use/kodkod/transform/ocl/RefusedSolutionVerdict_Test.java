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
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.UseScrollingAllKodkodModelValidator;
import org.tzi.use.kodkod.UseScrollingKodkodModelValidator;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks the verdict of a validation in which USE refused a solution of the
 * translation. The translation and USE then disagree on the model, so a search
 * that ends without another solution does not prove that the model is
 * unsatisfiable. A later solution that USE accepts is a model under OCL, and
 * stays satisfiable.
 */
public class RefusedSolutionVerdict_Test {

	private static final String WITH_INVARIANT = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv five: self.x = 5\n";

	private static final String WITHOUT_INVARIANT = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n";

	private static final String NOT_PROVED = "UNSATISFIABLE is not proved";

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

	private Logger tzi;
	private Level levelBefore;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		tzi = Logger.getLogger("org.tzi");
		levelBefore = tzi.getLevel();
		tzi.setLevel(Level.INFO);
		tzi.addAppender(recorder);
	}

	@After
	public void tearDown() {
		tzi.removeAppender(recorder);
		tzi.setLevel(levelBefore);
	}

	@Test
	public void noUnsatisfiableVerdictWhenUseRefusedTheOnlySolutionOfTheTranslation() throws Exception {
		MModel mModel = TestSpecifications.compile(WITH_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 4, 4);
		// the solver ignores the invariant, which USE then finds false, as when the translation differs from USE
		final IInvariant five = model.getInvariant("A::five");
		five.deactivate();
		new UseKodkodModelValidator(session) {
			@Override
			protected boolean showDiagramAndCacheEvaluator() {
				five.activate();
				boolean refused = super.showDiagramAndCacheEvaluator();
				five.deactivate();
				return refused;
			}
		}.validate(model);

		assertNoUnsatisfiableVerdict();
		assertOneNoVerdictError(1);
	}

	@Test
	public void noUnsatisfiableVerdictWhenUseRefusedASolutionThatOclAccepts() throws Exception {
		MModel mModel = TestSpecifications.compile(WITH_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 5, 5);
		new UseKodkodModelValidator(session) {
			private boolean first = true;

			@Override
			protected boolean showDiagramAndCacheEvaluator() {
				if (first) {
					first = false;
					return true;
				}
				return super.showDiagramAndCacheEvaluator();
			}
		}.validate(model);

		assertNoUnsatisfiableVerdict();
		assertOneNoVerdictError(1);
	}

	@Test
	public void theErrorCountsTheSolutionsUseRefused() throws Exception {
		MModel mModel = TestSpecifications.compile(WITHOUT_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 0, 1);
		new UseKodkodModelValidator(session) {
			private int refusals = 2;

			@Override
			protected boolean showDiagramAndCacheEvaluator() {
				return refusals-- > 0;
			}
		}.validate(model);

		assertNoUnsatisfiableVerdict();
		assertOneNoVerdictError(2);
	}

	@Test
	public void aLaterSolutionThatUseAcceptsStaysSatisfiable() throws Exception {
		MModel mModel = TestSpecifications.compile(WITHOUT_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 0, 1);
		new UseKodkodModelValidator(session) {
			private boolean first = true;

			@Override
			protected boolean showDiagramAndCacheEvaluator() {
				if (first) {
					first = false;
					return true;
				}
				return super.showDiagramAndCacheEvaluator();
			}
		}.validate(model);

		assertEquals(log(), 2, count("SATISFIABLE"));
		assertNoUnsatisfiableVerdict();
		assertEquals(0, noVerdictErrors().size());
	}

	@Test
	public void unsatisfiableWithoutARefusalStillPrintsTheVerdict() throws Exception {
		MModel mModel = TestSpecifications.compile(WITH_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 4, 4);
		new UseKodkodModelValidator(session).validate(model);

		assertTrue(log(), count("UNSATISFIABLE") + count("TRIVIALLY_UNSATISFIABLE") > 0);
		assertEquals(0, noVerdictErrors().size());
	}

	@Test
	public void scrollingValidatorPrintsNoUnsatisfiableVerdictAfterARefusal() throws Exception {
		MModel mModel = TestSpecifications.compile(WITH_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 5, 5);
		new UseScrollingKodkodModelValidator(session) {
			private boolean first = true;

			@Override
			protected boolean showDiagramAndCacheEvaluator() {
				if (first) {
					first = false;
					return true;
				}
				return super.showDiagramAndCacheEvaluator();
			}
		}.validate(model);

		assertNoUnsatisfiableVerdict();
		assertOneNoVerdictError(1);
	}

	@Test
	public void scrollingAllValidatorPrintsNoUnsatisfiableVerdictAfterARefusal() throws Exception {
		MModel mModel = TestSpecifications.compile(WITH_INVARIANT);
		Session session = session(mModel);
		IModel model = bounded(mModel, 5, 5);
		new UseScrollingAllKodkodModelValidator(session) {
			private boolean first = true;

			@Override
			protected boolean showDiagramAndCacheEvaluator() {
				if (first) {
					first = false;
					return true;
				}
				return super.showDiagramAndCacheEvaluator();
			}
		}.validate(model);

		assertNoUnsatisfiableVerdict();
		assertOneNoVerdictError(1);
	}

	private Session session(MModel mModel) {
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		return session;
	}

	/** The model with exactly one A and the given bounds of Integer. */
	private IModel bounded(MModel mModel, int integerMin, int integerMax) throws Exception {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", integerMin);
		properties.put("Integer_max", integerMax);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		return TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
	}

	private String log() {
		StringBuilder text = new StringBuilder();
		for (LoggingEvent event : events) {
			text.append(event.getLevel()).append(' ').append(event.getMessage()).append('\n');
		}
		return text.toString();
	}

	private int count(String message) {
		int count = 0;
		for (LoggingEvent event : events) {
			if (message.equals(String.valueOf(event.getMessage()))) {
				count++;
			}
		}
		return count;
	}

	private List<LoggingEvent> noVerdictErrors() {
		List<LoggingEvent> found = new ArrayList<LoggingEvent>();
		for (LoggingEvent event : events) {
			if (event.getLevel().isGreaterOrEqual(Level.ERROR) && String.valueOf(event.getMessage()).contains(NOT_PROVED)) {
				found.add(event);
			}
		}
		return found;
	}

	private void assertNoUnsatisfiableVerdict() {
		assertEquals(log(), 0, count("UNSATISFIABLE"));
		assertEquals(log(), 0, count("TRIVIALLY_UNSATISFIABLE"));
	}

	private void assertOneNoVerdictError(int refusals) {
		List<LoggingEvent> errors = noVerdictErrors();
		assertEquals(log(), 1, errors.size());
		String message = String.valueOf(errors.get(0).getMessage());
		assertTrue(message, message.contains("refused " + refusals + " solution"));
	}
}
