package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.log4j.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.UseKodkodModelValidator.Diagram;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.Plain;
import org.tzi.use.kodkod.transform.ocl.RefusalSupport.Script;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.uml.mm.MModel;

/**
 * A solution of which USE could not even build the object diagram is not a state that USE accepted
 * (issue #147). It counts as a refused solution: the search looks for another one, and a search that
 * ends without one gives the error that no verdict is proved, never a SATISFIABLE or UNSATISFIABLE
 * verdict.
 */
public class NotCreatedSolution_Test {

	private static final String WITHOUT_INVARIANT = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n";

	private static final String WITH_TUPLE = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  n : Integer\n"
			+ "  t : Tuple(k : A)\n"
			+ "end\n";

	@Rule
	public RefusalSupport support = new RefusalSupport();

	private boolean extraction;
	private PluginShell shell;

	@Before
	public void setUp() {
		extraction = KodkodModelValidatorConfiguration.getInstance().isAutomaticDiagramExtraction();
	}

	@After
	public void tearDown() {
		KodkodModelValidatorConfiguration.getInstance().setAutomaticDiagramExtraction(extraction);
		if (shell != null) {
			shell.close();
		}
	}

	// claim 1
	@Test
	public void aStateUseCannotRebuildIsARefusedSolution() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setAutomaticDiagramExtraction(true);
		shell = new PluginShell(WITH_TUPLE);
		shell.type("!create a : A");
		shell.type("!a.n := 4");
		shell.type("!a.t := Tuple{k = a}");
		MModel mModel = shell.system().model();
		IModel model = support.bounded(mModel, 4, 4, 1, 1);
		new UseKodkodModelValidator(shell.session()).validate(model);

		// the trigger fired: USE could not keep the tuple that holds an object
		assertTrue(support.log(), support.log().contains("Cannot keep the value of a.t"));
		assertNoUnsatisfiable();
		assertOneNoVerdictError(1);
	}

	// claim 2
	@Test
	public void aNotCreatedSolutionCountsAmongTheRefusedSolutions() throws Exception {
		MModel mModel = TestSpecifications.compile(WITHOUT_INVARIANT);
		Session session = support.session(mModel);
		IModel model = support.bounded(mModel, 0, 1, 1, 1);
		Script script = new Script(Diagram.ACCEPTED, Diagram.NOT_CREATED, Diagram.REFUSED);
		new Plain(session, script).validate(model);

		assertEquals(support.log(), 2, script.calls());
		assertNoUnsatisfiable();
		assertOneNoVerdictError(2);
	}

	// claim 3
	@Test
	public void aNotCreatedSolutionFollowedByAnAcceptedOneIsSatisfiable() throws Exception {
		shell = new PluginShell(WITHOUT_INVARIANT);
		MModel mModel = shell.system().model();
		IModel model = support.bounded(mModel, 0, 1, 1, 1);
		Script script = new Script(RefusalSupport.REAL, Diagram.NOT_CREATED);
		new Plain(shell.session(), script).validate(model);

		assertEquals(support.log(), 2, script.calls());
		assertNoUnsatisfiable();
		assertEquals(support.log(), 0, support.errorsContaining(RefusalSupport.NOT_PROVED).size());
		assertEquals(support.log(), 1, shell.system().state().allObjects().size());
		shell.type("check");
		assertTrue(support.log(), Shell.getInstance().lastCheckResult());
	}

	private void assertNoUnsatisfiable() {
		assertEquals(support.log(), 0, support.count("UNSATISFIABLE"));
		assertEquals(support.log(), 0, support.count("TRIVIALLY_UNSATISFIABLE"));
	}

	private void assertOneNoVerdictError(int refusals) {
		List<LoggingEvent> errors = support.errorsContaining(RefusalSupport.NOT_PROVED);
		assertEquals(support.log(), 1, errors.size());
		String message = String.valueOf(errors.get(0).getMessage());
		assertTrue(message, message.contains("refused " + refusals + " solution"));
		assertFalse(support.log(), support.log().contains("neither SATISFIABLE nor UNSATISFIABLE"));
	}
}
