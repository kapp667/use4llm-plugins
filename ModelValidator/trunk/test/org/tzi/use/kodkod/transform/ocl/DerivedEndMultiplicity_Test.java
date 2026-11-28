package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.config.Options;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.sys.MSystem;

import kodkod.ast.Formula;

/**
 * The multiplicity of an end of a derived association is a constraint, as USE
 * checks it: the derived end holds the objects its derive expression yields
 * (issue #132). Before, the translation left it out, so the solver proposed
 * states that USE refused one after the other, and a model whose bounds make
 * the multiplicity fail ended as unsatisfiable only after the refusals.
 */
public class DerivedEndMultiplicity_Test {

	private static final String OWNS = "model D132\n"
			+ "class A\n"
			+ "end\n"
			+ "class B\n"
			+ "attributes\n"
			+ "  v : Integer\n"
			+ "end\n"
			+ "association Owns between\n"
			+ "  A[0..1] role owner\n"
			+ "  B[*] role items\n"
			+ "end\n";

	private static final String BOUNDED = OWNS
			+ "association Small between\n"
			+ "  A[*] role smallOwner\n"
			+ "  B[1..*] role small derived = self.items->select(b | b.v < 10)\n"
			+ "end\n";

	private static final String UNBOUNDED = OWNS
			+ "association Small between\n"
			+ "  A[*] role smallOwner\n"
			+ "  B[*] role small derived = self.items->select(b | b.v < 10)\n"
			+ "end\n";

	/** The model of USE's examples, in which a derived end subsets an end, which USE refuses. */
	private static final String SUBSETS_DERIVED = "model Derived\n"
			+ "class A\n"
			+ "end\n"
			+ "class B\n"
			+ "attributes\n"
			+ "  value : Integer\n"
			+ "end\n"
			+ "association AllLinks between\n"
			+ "  A[0..*] role allAs\n"
			+ "  B[*] role allBs\n"
			+ "end\n"
			+ "association SmallBs between\n"
			+ "  A[0..1] role aOfSmallB subsets allAs\n"
			+ "  B[*] role smallBs subsets allBs derived = self.allBs->select(b | b.value < 10)\n"
			+ "end\n";

	private static final String BOUNDS = "Integer_min = 0\n"
			+ "Integer_max = 20\n"
			+ "A_min = 1\n"
			+ "A_max = 1\n"
			+ "B_min = 0\n"
			+ "B_max = 2\n"
			+ "Owns_min = 0\n"
			+ "Owns_max = 2\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final StringWriter log = new StringWriter();
	private WriterAppender appender;
	private Session session;

	@Before
	public void setUp() {
		appender = new WriterAppender(new PatternLayout("%p %m%n"), log);
		Logger.getLogger("org.tzi").addAppender(appender);
	}

	@After
	public void tearDown() {
		Logger.getLogger("org.tzi").removeAppender(appender);
	}

	private void open(String spec) {
		session = new Session();
		// As in USE, the plugin watches the session before the model is opened, so it
		// transforms the model under test, not one left over from an earlier test.
		session.setSystem(new MSystem(TestSpecifications.compile("model Empty\n")));
		PluginModelFactory.INSTANCE.registerForSession(session);
		session.setSystem(new MSystem(TestSpecifications.compile(spec)));
		boolean plugins = Options.doPLUGIN;
		Options.doPLUGIN = false;
		Shell.createInstance(session, null);
		Options.doPLUGIN = plugins;
	}

	/** Claim 1: the lower bound 1 of the derived end holds in the solution, so USE refuses no state. */
	@Test
	public void aDerivedEndWithALowerBoundGivesAStateUseAccepts() throws Exception {
		open(BOUNDED);
		String output = validate(file("one.properties", "[one]\n" + BOUNDS + "B_v = Set{1,20}\n"), "one");
		assertFalse(output, output.contains("ERROR"));
		assertFalse(output, output.contains("Trying to find a new solution!"));
		assertEquals(output, 1, count(output, "INFO SATISFIABLE"));
		assertTrue(output, session.system().state().checkStructure(new PrintWriter(new StringWriter())));
		CollectionValue small = (CollectionValue) query("A.allInstances->collect(a | a.small)");
		assertTrue("the derived end of the state is empty: " + small, small.size() > 0);
	}

	/** Claim 2: every B has v = 20, so the derived end is always empty and its lower bound 1 fails. */
	@Test
	public void aDerivedEndThatTheBoundsLeaveEmptyIsUnsatisfiableWithoutRefusals() throws Exception {
		open(BOUNDED);
		String output = validate(file("none.properties", "[none]\n" + BOUNDS + "B_v = Set{20}\n"), "none");
		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, 0, count(output, "INFO SATISFIABLE"));
		assertEquals(output, 1, count(output, "INFO UNSATISFIABLE"));
		assertFalse(output, output.contains("Trying to find a new solution!"));
	}

	/** Claim 3: the solver sees the multiplicity: with one A and no B it finds no solution. */
	@Test
	public void theSolverFindsNoSolutionWhenNoBCanFillTheDerivedEnd() throws Exception {
		IModel model = TestSpecifications.transform(BOUNDED);
		assertNotEquals(Formula.TRUE, model.getAssociation("Small").constraints());
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 20);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("B_min", 0);
		properties.put("B_max", 0);
		properties.put("Owns_min", 0);
		properties.put("Owns_max", 0);
		TestSpecifications.configure(model, properties);
		OutcomeRecorder validator = new OutcomeRecorder();
		validator.validate(model);
		assertTrue("outcome " + validator.outcome, validator.outcome != null && validator.outcome.endsWith("UNSATISFIABLE"));
	}

	/** Claim 4: a derived end of multiplicity * keeps working. */
	@Test
	public void aDerivedEndWithoutBoundsStillGivesAStateUseAccepts() throws Exception {
		open(UNBOUNDED);
		String output = validate(file("many.properties", "[one]\n" + BOUNDS + "B_v = Set{1,20}\n"), "one");
		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, 1, count(output, "INFO SATISFIABLE"));
		assertTrue(output, session.system().state().checkStructure(new PrintWriter(new StringWriter())));
	}

	/** Claim 5: USE refuses a subsets on a derived end, and the validator still ends in an error for it. */
	@Test
	public void subsetsOnADerivedEndStillEndsInAnError() throws Exception {
		open(SUBSETS_DERIVED);
		String output = validate(file("sub.properties", "[sub]\nInteger_min = 0\nInteger_max = 20\nA_min = 1\nA_max = 1\n"
				+ "B_min = 0\nB_max = 2\nB_value = Set{1,20}\n"), "sub");
		assertTrue(output, output.contains("ERROR"));
		assertTrue(output, output.contains("derived associations are not supported"));
		assertEquals(output, 0, count(output, "INFO SATISFIABLE"));
	}

	private Object query(String ocl) {
		StringWriter errors = new StringWriter();
		Expression expression = OCLCompiler.compileExpression(session.system().model(), session.system().state(), ocl, "Test",
				new PrintWriter(errors), session.system().varBindings());
		if (expression == null) {
			throw new AssertionError("The query does not compile: " + errors);
		}
		return new Evaluator().eval(expression, session.system().state(), session.system().varBindings());
	}

	private static int count(String text, String part) {
		int count = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
			count++;
		}
		return count;
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}

	private String validate(String... arguments) {
		final String[] argumentList = arguments;
		new KodkodValidateCmd().performCommand(new IPluginShellCmd() {
			@Override
			public void executeCmd(String cmd, String cmdArguments, String[] argList) {
			}

			@Override
			public String getCmd() {
				return "mv -validate";
			}

			@Override
			public Session getSession() {
				return session;
			}

			@Override
			public Shell getShell() {
				return Shell.getInstance();
			}

			@Override
			public String getCmdArguments() {
				return " " + String.join(" ", argumentList);
			}

			@Override
			public String[] getCmdArgumentList() {
				return argumentList;
			}
		});
		return log.toString();
	}
}
