package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.use.kodkod.plugin.KodkodQueryCmd;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;

/**
 * collect gives a Bag, which the translation reads as the set of its elements.
 * Where the outcome depends only on which elements are present, the verdict and
 * the answer of mv ? are USE's, with no warning. Where it reads how often an
 * element occurs, the translation refuses with an ERROR, with no warning either.
 * Two objects of A both have n = 0, so the Bag is Bag{0,0} and the set is Set{0}.
 */
public class CollectDuplicates_Test {

	private static final String ELEMENTS_ONLY = "A.allInstances->collect(a | a.n)->asSet()->size() = 1"
			+ " and A.allInstances->collect(a | a.n)->includes(0)"
			+ " and A.allInstances->collect(a | a.n)->exists(v | v = 0)"
			+ " and A.allInstances->collect(a | a.n)->forAll(v | v = 0)";

	private static final String DUPLICATES = "A.allInstances->collect(a | a.n)->size() = 2";

	private static final String PROPERTIES = "[two]\n"
			+ "Integer_min = 0\n"
			+ "Integer_max = 0\n"
			+ "String_max = 0\n"
			+ "A_min = 2\n"
			+ "A_max = 2\n"
			+ "A_n_min = 2\n"
			+ "A_n_max = 2\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private PluginShell shell;

	@After
	public void tearDown() {
		if (shell != null) {
			shell.run(new KodkodQueryCmd(), "disable");
			shell.close();
		}
	}

	@Test
	public void validatesACollectReadOnlyThroughItsElementsWithoutAWarning() throws Exception {
		String output = validate(ELEMENTS_ONLY);
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("WARN"));
		assertFalse(output, output.contains("ERROR"));
	}

	@Test
	public void refusesToValidateACollectWhoseDuplicatesAreReadWithoutAWarning() throws Exception {
		String output = validate(DUPLICATES);
		assertTrue(output, output.contains("ERROR Cannot validate the model"));
		assertTrue(output, output.contains("duplicates"));
		assertFalse(output, output.contains("SATISFIABLE"));
		assertFalse(output, output.contains("WARN"));
	}

	@Test
	public void comparesTheAnswersOnACollectReadOnlyThroughItsElements() throws Exception {
		validate(ELEMENTS_ONLY);
		String[][] valuesOfQueries = {
				{ "A.allInstances->collect(a | a.n)->asSet()", "Set\\{0\\} : Set\\(Integer\\)" },
				{ "A.allInstances->collect(a | a.n)->includes(0)", "true : Boolean" },
				{ "A.allInstances->collect(a | a.n)->exists(v | v = 0)", "true : Boolean" },
				{ "A.allInstances->collect(a | a.n)->forAll(v | v = 0)", "true : Boolean" },
				{ "A.allInstances->collect(a | a.n)->select(v | v = 0)->notEmpty()", "true : Boolean" },
				{ "A.allInstances->collectNested(a | a.n)->asSet()", "Set\\{0\\} : Set\\(Integer\\)" } };
		for (String[] valueOfQuery : valuesOfQueries) {
			String output = shell.run(new KodkodQueryCmd(), valueOfQuery[0]);
			assertTrue(valueOfQuery[0] + ": " + output, output.trim().matches("INFO " + valueOfQuery[1]));
		}
	}

	@Test
	public void refusesToCompareTheSizeOfACollectWithoutAWarning() throws Exception {
		validate(ELEMENTS_ONLY);
		String output = shell.run(new KodkodQueryCmd(), "A.allInstances->collect(a | a.n)->size()");
		assertTrue(output, output.contains("INFO 2 : Integer"));
		assertTrue(output, output.contains("Not compared with a solution"));
		assertTrue(output, output.contains("duplicates"));
		assertFalse(output, output.contains("WARN"));
	}

	private String validate(String invariant) throws Exception {
		shell = new PluginShell("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  n : Integer\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv elements: " + invariant + "\n");
		shell.run(new KodkodQueryCmd(), "enable");
		File file = folder.newFile("two.properties");
		Files.write(file.toPath(), PROPERTIES.getBytes(StandardCharsets.UTF_8));
		return shell.run(new KodkodValidateCmd(), file.getPath());
	}
}
