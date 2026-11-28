package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.type.TypeLiterals;
import org.tzi.use.kodkod.plugin.KodkodQueryCmd;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.kodkod.plugin.PluginModelFactory;

/**
 * Checks that the literals a query registers in the model while it translates
 * the expression do not stay there, since the next solve would bind them.
 */
public class QueryLiterals_Test {

	private static final String SPEC = "model Lit\n"
			+ "enum Color { red, green }\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  c : Color\n"
			+ "  n : Integer\n"
			+ "end\n";

	private static final String PROPERTIES = "[one]\n"
			+ "Integer_min = 0\n"
			+ "Integer_max = 3\n"
			+ "A_min = 1\n"
			+ "A_max = 1\n"
			+ "A_n = Set{1,2}\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private PluginShell shell;

	@Before
	public void setUp() {
		shell = new PluginShell(SPEC);
		shell.run(new KodkodQueryCmd(), "enable");
	}

	@After
	public void tearDown() {
		shell.run(new KodkodQueryCmd(), "disable");
		shell.close();
	}

	// 1
	@Test
	public void queriesWithObjectAndEnumLiteralsLeaveTheLiteralsAsTheyWere() throws Exception {
		solve();
		String object = object();
		Map<String, Set<String>> before = literals();

		String output = query(object + ".c");
		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, before, literals());

		output = query("#red");
		assertTrue(output, output.contains("INFO Color::red : Color"));
		assertEquals(output, before, literals());

		output = query("Color::green");
		assertTrue(output, output.contains("INFO Color::green : Color"));
		assertEquals(output, before, literals());
	}

	// 2
	@Test
	public void aQueryThatFailsToTranslateLeavesTheLiteralsAsTheyWere() throws Exception {
		solve();
		String object = object();
		Map<String, Set<String>> before = literals();

		String output = query(object + ".c = #red and 1 / 2 > 0");

		assertTrue(output, output.contains("Not compared with a solution"));
		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, before, literals());
	}

	// 3
	@Test
	public void queriesLeaveTheUniverseAndTheStateOfTheNextSolveAlone() throws Exception {
		String properties = file("zero.properties", "[zero]\nA_min = 0\nA_max = 0\n");
		shell.type("!create a1 : A");
		shell.type("!set a1.c := #red");
		query("a1.c");
		query("#red");
		shell.type("reset");

		String output = shell.run(new KodkodValidateCmd(), properties);

		assertTrue(output, output.contains("INFO Universe: 8 atoms, 0 of them Integer values"));
		assertTrue(output, output.contains("INFO TRIVIALLY_SATISFIABLE"));
		assertEquals(0, shell.system().state().allObjects().size());
	}

	// 4
	@Test
	public void literalsOfTheConfigurationSurviveAQuery() throws Exception {
		solve();
		String object = object();
		Map<String, Set<String>> before = literals();
		assertTrue("the configuration registers the literal of the object: " + before, before.get("A").contains("A_" + object));

		String output = query(object + ".c");

		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, before, literals());
		assertTrue(literals().get("A").contains("A_" + object));
	}

	/** The literals of every type that holds some: the built-in types, the enums and the object type of each class. */
	private Map<String, Set<String>> literals() {
		IModel model = PluginModelFactory.INSTANCE.getModel(shell.system().model());
		Map<String, Set<String>> result = new TreeMap<String, Set<String>>();
		for (TypeLiterals type : model.typeFactory().typeLiterals()) {
			result.put(type.name(), new HashSet<String>(type.typeLiterals().keySet()));
		}
		for (TypeLiterals type : model.enumTypes()) {
			result.put(type.name(), new HashSet<String>(type.typeLiterals().keySet()));
		}
		for (IClass clazz : model.classes()) {
			result.put(clazz.name(), new HashSet<String>(clazz.objectType().typeLiterals().keySet()));
		}
		return result;
	}

	private String object() {
		return shell.system().state().allObjects().iterator().next().name();
	}

	private void solve() throws Exception {
		String output = shell.run(new KodkodValidateCmd(), file("one.properties", PROPERTIES));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	private String query(String ocl) {
		return shell.run(new KodkodQueryCmd(), ocl);
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}
}
