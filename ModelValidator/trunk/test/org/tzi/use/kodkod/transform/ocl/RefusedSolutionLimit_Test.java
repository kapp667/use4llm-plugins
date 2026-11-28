package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseKodkodModelValidator.Diagram;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.CTScrolling;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.CTScrollingAll;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.Plain;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.Scrolling;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.ScrollingAll;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.Script;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;

/**
 * A search in which USE refuses solution after solution stops after 10 refused solutions with an
 * error, and gives no verdict (issue #146). The count restarts at each accepted solution and at each
 * scroll to the next solution.
 */
public class RefusedSolutionLimit_Test {

	private static final String WITHOUT_INVARIANT = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n";

	private static final String LIMIT = "No verdict. USE refused 10 solution(s) of the translation, the limit of refused solutions"
			+ " in one search. The search stops here, so the model is neither SATISFIABLE nor UNSATISFIABLE for USE.";

	@Rule
	public RefusalSupport support = new RefusalSupport();

	private final MModel mModel = TestSpecifications.compile(WITHOUT_INVARIANT);
	private Session session;

	@Before
	public void setUp() {
		session = support.session(mModel);
	}

	private IModel model(int aMin, int aMax) throws Exception {
		// 21 solutions with one A
		return support.bounded(mModel, 0, 20, aMin, aMax);
	}

	// claim 4
	@Test
	public void theSearchStopsAfterTenRefusedSolutionsWithAnError() throws Exception {
		Script script = new Script(Diagram.REFUSED);
		new Plain(session(), script).validate(model(1, 1));

		assertStoppedByTheLimit(script, 10);
	}

	// claim 6
	@Test
	public void theScrollingValidatorStopsAfterTenRefusedSolutions() throws Exception {
		Script script = new Script(Diagram.REFUSED);
		new Scrolling(session(), script).validate(model(1, 1));

		assertStoppedByTheLimit(script, 10);
	}

	// claim 6
	@Test
	public void theScrollingAllValidatorStopsAfterTenRefusedSolutionsAndKeepsTheAcceptedOnes() throws Exception {
		Script script = new Script(Diagram.REFUSED, RefusalSupport.REAL, RefusalSupport.REAL, RefusalSupport.REAL);
		ScrollingAll validator = new ScrollingAll(session(), script);
		validator.validate(model(1, 1));

		assertStoppedByTheLimit(script, 13);
		assertEquals(support.log(), 3, validator.accepted());
	}

	// claim 6
	@Test
	public void theCTScrollingValidatorStopsAfterTenRefusedSolutions() throws Exception {
		Script script = new Script(Diagram.REFUSED);
		IModel model = model(0, 2);
		CTScrolling validator = new CTScrolling(session(), script);
		support.addSizeTerm(validator, mModel, model);
		validator.validate(model);

		assertStoppedByTheLimit(script, 10);
	}

	// claim 6
	@Test
	public void theCTScrollingAllValidatorStopsAfterTenRefusedSolutionsAndKeepsTheAcceptedOnes() throws Exception {
		Script script = new Script(Diagram.REFUSED, RefusalSupport.REAL);
		IModel model = model(0, 2);
		CTScrollingAll validator = new CTScrollingAll(session(), script);
		support.addSizeTerm(validator, mModel, model);
		validator.validate(model);

		assertStoppedByTheLimit(script, 11);
		assertEquals(support.log(), 1, validator.accepted());
	}

	// claim 7
	@Test
	public void nineRefusedSolutionsThenAnAcceptedOneIsSatisfiable() throws Exception {
		Script script = new Script(RefusalSupport.REAL, RefusalSupport.times(9, Diagram.REFUSED));
		new Plain(session(), script).validate(model(1, 1));

		assertEquals(support.log(), 10, script.calls());
		assertEquals(support.log(), 0, support.errorsContaining("No verdict").size());
		assertEquals(support.log(), 0, support.count("UNSATISFIABLE"));
		assertEquals(support.log(), 1, session.system().state().allObjects().size());
	}

	// claim 7
	@Test
	public void theCountRestartsAtAnAcceptedSolutionAndAtNextSolution() throws Exception {
		Script script = new Script(RefusalSupport.REAL,
				RefusalSupport.concat(RefusalSupport.concat(RefusalSupport.times(9, Diagram.REFUSED), RefusalSupport.REAL),
						RefusalSupport.times(9, Diagram.REFUSED)));
		Scrolling validator = new Scrolling(session(), script);
		validator.validate(model(1, 1));
		assertEquals(support.log(), 10, script.calls());
		assertEquals(support.log(), 1, validator.accepted());

		validator.nextSolution();

		assertEquals(support.log(), 20, script.calls());
		assertEquals(support.log(), 2, validator.accepted());
		assertEquals(support.log(), 0, support.errorsContaining("No verdict").size());
		assertEquals(support.log(), 0, support.count("UNSATISFIABLE"));
	}

	private Session session() {
		return session;
	}

	/** The seam was called the given number of times, and the log has the limit error once and no verdict. */
	private void assertStoppedByTheLimit(Script script, int calls) {
		assertEquals(support.log(), calls, script.calls());
		assertEquals(support.log(), 1, support.errorsContaining("No verdict").size());
		assertEquals(support.log(), 1, support.errorsContaining(LIMIT).size());
		assertEquals(support.log(), 0, support.errorsContaining(RefusalSupport.NOT_PROVED).size());
		assertEquals(support.log(), 0, support.count("UNSATISFIABLE"));
		assertEquals(support.log(), 0, support.count("TRIVIALLY_UNSATISFIABLE"));
	}
}
