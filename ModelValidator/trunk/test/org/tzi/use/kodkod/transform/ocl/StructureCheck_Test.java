package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that a structure error USE finds in the object diagram of a solution,
 * other than the aggregation cycles and the sharing the configuration allows,
 * is logged as an error and refuses the solution (issue #77).
 */
public class StructureCheck_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "end\n"
			+ "class B\n"
			+ "end\n"
			+ "association R between\n"
			+ "  A[1] role a\n"
			+ "  B[1] role b\n"
			+ "end\n";

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
	public void multiplicityViolationInTheSolutionDiagramIsAnError() throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("B_min", 1);
		properties.put("B_max", 1);
		IModel model = TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
		OutcomeRecorder validator = new OutcomeRecorder();
		validator.validate(model);
		assertEquals("SATISFIABLE", validator.outcome);

		ObjectDiagramCreator creator = new ObjectDiagramCreator(model, session);
		creator.create(validator.relationTuples());
		// a B linked to no A breaks the multiplicity 1 of the end a, as a translation fault in multiplicities would
		session.system().state().createObject(mModel.getClass("B"), "unlinked");

		assertEquals(ObjectDiagramCreator.ErrorType.STRUCTURE, creator.hasDiagramErrors());
		assertFalse("no error logged for the multiplicity violation", errors.isEmpty());
	}
}
