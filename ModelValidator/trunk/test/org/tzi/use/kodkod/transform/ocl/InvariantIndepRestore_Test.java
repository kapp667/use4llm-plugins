package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.InvariantIndepChecker;
import org.tzi.kodkod.KodkodModelValidator;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.KodkodSolver;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.plugin.InvariantIndepCmd;
import org.tzi.use.kodkod.plugin.KodkodQueryCmd;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that mv -invIndep leaves nothing behind: the verdicts of the next
 * mv -validate are printed, the invariants have the states they had, and the
 * loggers have the levels they had, whatever way the check ends.
 */
public class InvariantIndepRestore_Test {

	private static final String SPEC = "model Two\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  n : Integer\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv isOne: self.n = 1\n"
			+ "context A inv isTwo: self.n = 2\n";

	private static final String OFF = "[off]\n"
			+ "Integer_min = 0\n"
			+ "Integer_max = 3\n"
			+ "A_min = 1\n"
			+ "A_max = 1\n"
			+ "A_n = Set{1,2}\n"
			+ "A_isTwo = inactive\n";

	private static final String PLAIN = "[plain]\n"
			+ "Integer_min = 0\n"
			+ "Integer_max = 3\n"
			+ "A_min = 1\n"
			+ "A_max = 1\n"
			+ "A_n = Set{1,2}\n";

	private static final Pattern VERDICT = Pattern.compile("^INFO (TRIVIALLY_)?(UN)?SATISFIABLE$", Pattern.MULTILINE);

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final Map<Logger, Level> levels = new LinkedHashMap<Logger, Level>();
	private PluginShell shell;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		for (Logger logger : loggers()) {
			levels.put(logger, logger.getLevel());
		}
	}

	@After
	public void tearDown() {
		if (shell != null) {
			shell.run(new KodkodQueryCmd(), "disable");
			shell.close();
		}
		for (Map.Entry<Logger, Level> level : levels.entrySet()) {
			level.getKey().setLevel(level.getValue());
		}
	}

	private static Logger[] loggers() {
		return new Logger[] { Logger.getLogger(KodkodModelValidator.class), Logger.getLogger(KodkodSolver.class),
				Logger.getLogger(InvariantIndepChecker.class) };
	}

	// 1
	@Test
	public void validateAfterIndependenceOfAllPrintsItsVerdict() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);
		String plain = file("plain.properties", PLAIN);

		shell.run(new InvariantIndepCmd(), off, "all");
		String output = shell.run(new KodkodValidateCmd(), plain);

		assertEquals(output, 1, verdicts(output));
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
		assertTrue(output, output.contains("INFO Universe:"));
		assertTrue(output, output.contains("INFO Searching solution"));
	}

	// 2
	@Test
	public void validateAfterIndependenceOfAllPrintsTheVerdictItPrintedBefore() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);

		String first = shell.run(new KodkodValidateCmd(), off);
		shell.run(new InvariantIndepCmd(), off, "all");
		String third = shell.run(new KodkodValidateCmd(), off);

		assertTrue(first, first.contains("INFO SATISFIABLE"));
		assertTrue(third, third.contains("INFO SATISFIABLE"));
		assertTrue(third, third.contains("INFO Universe: 15 atoms, 4 of them Integer values"));
		assertEquals(first, 1, verdicts(first));
		assertEquals(third, 1, verdicts(third));
	}

	// 3
	@Test
	public void unknownInvariantIsAnErrorAndLeavesTheNextValidateAlone() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);
		String plain = file("plain.properties", PLAIN);

		String indep = shell.run(new InvariantIndepCmd(), off, "A::nope");
		String output = shell.run(new KodkodValidateCmd(), plain);

		assertTrue(indep, indep.contains("ERROR Class `A' has no invariant `nope'"));
		assertEquals(indep, 0, verdicts(indep));
		assertEquals(output, 1, verdicts(output));
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
	}

	// 4
	@Test
	public void unknownClassIsAnErrorAndLeavesTheNextValidateAlone() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);
		String plain = file("plain.properties", PLAIN);

		String indep = shell.run(new InvariantIndepCmd(), off, "Nope::isOne");
		String output = shell.run(new KodkodValidateCmd(), plain);

		assertTrue(indep, indep.contains("ERROR Class `Nope' does not exist"));
		assertEquals(indep, 0, verdicts(indep));
		assertEquals(output, 1, verdicts(output));
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
	}

	// 5
	@Test
	public void unknownClassLeavesInvariantStatesAndLogLevels() throws Exception {
		assertRestored("Nope", "isOne", false);
	}

	// 5
	@Test
	public void unknownInvariantLeavesInvariantStatesAndLogLevels() throws Exception {
		assertRestored("A", "nope", false);
	}

	// 5
	@Test
	public void allInvariantsLeaveInvariantStatesAndLogLevels() throws Exception {
		assertRestored(null, null, true);
	}

	// 5, the path that keeps working
	@Test
	public void oneInvariantLeavesInvariantStatesAndLogLevels() throws Exception {
		assertRestored("A", "isOne", false);
	}

	// 6
	@Test
	public void independenceOfAllReportsEachInvariantAndNothingAboveInfo() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);

		String output = shell.run(new InvariantIndepCmd(), off, "all");

		assertTrue(output, output.contains("INFO A::isOne: Independent"));
		assertTrue(output, output.contains("INFO A::isTwo: Independent"));
		assertFalse(output, output.contains("WARN "));
		assertFalse(output, output.contains("ERROR "));
	}

	// 7
	@Test
	public void independenceOfOneInvariantLeavesTheNextValidateAlone() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);
		String plain = file("plain.properties", PLAIN);

		String indep = shell.run(new InvariantIndepCmd(), off, "A::isOne");
		String output = shell.run(new KodkodValidateCmd(), plain);

		assertTrue(indep, indep.contains("INFO SATISFIABLE"));
		assertTrue(indep, indep.contains("INFO A::isOne: Independent"));
		assertEquals(output, 1, verdicts(output));
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
	}

	// 8
	@Test
	public void theDiagramOfIndependenceOfAllIsNotComparedWithTheEarlierSolution() throws Exception {
		shell = new PluginShell(SPEC);
		String off = file("off.properties", OFF);
		shell.run(new KodkodQueryCmd(), "enable");
		shell.run(new KodkodValidateCmd(), off);
		shell.run(new InvariantIndepCmd(), off, "all");

		String output = shell.run(new KodkodQueryCmd(), "A.allInstances()->any(true).n");

		assertTrue(output, output.contains("INFO 1 : Integer"));
		assertTrue(output, output.contains("Not compared with a solution: the state shown is not a solution found since mv ? enable, or it changed since"));
		assertFalse(output, output.contains("ERROR"));
	}

	/**
	 * Sets one invariant inactive and one negated, and the loggers to levels the
	 * check does not use, runs the check, and compares both with the levels and
	 * states before it.
	 */
	private void assertRestored(String className, String invariantName, boolean all) throws Exception {
		MModel mModel = TestSpecifications.compile(SPEC);
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 3);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		IModel model = TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
		IInvariant isOne = model.getInvariant("A::isOne");
		IInvariant isTwo = model.getInvariant("A::isTwo");
		isOne.deactivate();
		isTwo.negate();
		boolean[] statesBefore = { isOne.isActivated(), isOne.isNegated(), isTwo.isActivated(), isTwo.isNegated() };
		assertFalse("setup: isOne is inactive", statesBefore[0]);
		assertTrue("setup: isTwo is negated", statesBefore[3]);
		Logger.getLogger(KodkodModelValidator.class).setLevel(Level.ERROR);
		Logger.getLogger(KodkodSolver.class).setLevel(Level.FATAL);
		Logger.getLogger(InvariantIndepChecker.class).setLevel(Level.WARN);
		Level[] before = effectiveLevels();
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		InvariantIndepChecker checker = new InvariantIndepChecker(session);

		if (all) {
			checker.validate(model);
		} else {
			checker.validate(model, className, invariantName);
		}

		assertEquals("isOne is activated", statesBefore[0], isOne.isActivated());
		assertEquals("isOne is negated", statesBefore[1], isOne.isNegated());
		assertEquals("isTwo is activated", statesBefore[2], isTwo.isActivated());
		assertEquals("isTwo is negated", statesBefore[3], isTwo.isNegated());
		Level[] after = effectiveLevels();
		for (int i = 0; i < before.length; i++) {
			assertEquals("effective level of " + loggers()[i].getName(), before[i], after[i]);
		}
	}

	private static Level[] effectiveLevels() {
		Logger[] loggers = loggers();
		Level[] result = new Level[loggers.length];
		for (int i = 0; i < loggers.length; i++) {
			result[i] = loggers[i].getEffectiveLevel();
		}
		return result;
	}

	private static int verdicts(String output) {
		Matcher matcher = VERDICT.matcher(output);
		int n = 0;
		while (matcher.find()) {
			n++;
		}
		return n;
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}
}
