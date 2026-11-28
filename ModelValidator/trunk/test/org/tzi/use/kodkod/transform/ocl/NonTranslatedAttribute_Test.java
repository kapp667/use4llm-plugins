package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.DefaultConfigurationVisitor;
import org.tzi.use.kodkod.plugin.ConfigurationValidationCmd;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * An attribute that the translation leaves out (type OclAny, or a type the translation cannot
 * convert, such as a Tuple) keeps the value the user gave it when the object diagram is extracted
 * from the state (issue #103). A properties key for such an attribute is refused with an error
 * that names the attribute and its type, never dropped in silence (issue #105). The run prints no
 * error before its verdict for an attribute that no invariant reads.
 */
public class NonTranslatedAttribute_Test {

	private static final String OCLANY = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  n : Integer\n"
			+ "  x : OclAny\n"
			+ "end\n";

	private static final String WITH_TUPLE = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  n : Integer\n"
			+ "  x : OclAny\n"
			+ "  t : Tuple(k : Integer)\n"
			+ "end\n";

	private static final String BOUNDS = "[cfg]\nInteger_min = 0\nInteger_max = 3\nA_min = 1\nA_max = 1\nA_n = Set{2}\n";

	private static final String REFUSED = ", which the model validator does not translate, so this setting has no effect. "
			+ "The model validator does not search with this configuration.";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private KodkodModelValidatorConfiguration configuration;
	private boolean extraction;
	private PluginShell shell;

	@Before
	public void setUp() throws Exception {
		configuration = KodkodModelValidatorConfiguration.getInstance();
		configuration.setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		extraction = configuration.isAutomaticDiagramExtraction();
	}

	@After
	public void tearDown() {
		configuration.setAutomaticDiagramExtraction(extraction);
		if (shell != null) {
			shell.close();
		}
	}

	// claim 1
	@Test
	public void extractionKeepsTheValueOfAnOclAnyAttribute() throws Exception {
		String output = validateExtractedState(OCLANY, BOUNDS, "!create a : A", "!a.n := 2", "!a.x := 1");
		assertSatisfiable(output);
		assertEquals("1", attributeValue("a", "x").toString());
		assertTrue(output, checkAccepts());
	}

	// claim 2
	@Test
	public void extractionKeepsTheValueOfATupleAttribute() throws Exception {
		String output = validateExtractedState(WITH_TUPLE, BOUNDS, "!create a : A", "!a.n := 2", "!a.x := 1",
				"!a.t := Tuple{k = 5}");
		assertSatisfiable(output);
		assertEquals("Tuple{k=5}", attributeValue("a", "t").toString());
		assertTrue(output, checkAccepts());
	}

	// claim 3
	@Test
	public void extractionKeepsAnObjectValueOfAnOclAnyAttributeAsTheObjectOfTheNewDiagram() throws Exception {
		String output = validateExtractedState(OCLANY,
				"[cfg]\nInteger_min = 0\nInteger_max = 3\nA_min = 2\nA_max = 2\n",
				"!create a : A", "!create b : A", "!a.n := 2", "!a.x := b");
		assertSatisfiable(output);
		Value x = attributeValue("a", "x");
		assertTrue("a.x is " + x, x instanceof ObjectValue);
		MObject b = shell.system().state().objectByName("b");
		assertTrue("a.x is not the object b of the new diagram: " + x, ((ObjectValue) x).value() == b);
		assertTrue(output, checkAccepts());
	}

	// claim 4
	@Test
	public void extractionStillKeepsTheValueOfATranslatedAttribute() throws Exception {
		String output = validateExtractedState(OCLANY, BOUNDS, "!create a : A", "!a.n := 2", "!a.x := 1");
		assertSatisfiable(output);
		assertEquals("2", attributeValue("a", "n").toString());
	}

	// claim 5
	@Test
	public void withoutExtractionTheRunKeepsItsOutput() throws Exception {
		configuration.setAutomaticDiagramExtraction(false);
		shell = new PluginShell(OCLANY);
		shell.type("!create a : A");
		shell.type("!a.n := 2");
		String output = shell.run(new KodkodValidateCmd(), file("ok.properties", BOUNDS));
		assertSatisfiable(output);
		assertFalse(output, output.contains("ERROR"));
		assertFalse(output, output.contains("WARN"));
		assertTrue(output, checkAccepts());
		assertEquals("2", attributeValue(firstObjectName(), "n").toString());
	}

	// claim 6
	@Test
	public void keysOfAnOclAnyAttributeAreRefusedOneLineEach() throws Exception {
		shell = new PluginShell(WITH_TUPLE);
		String output = shell.run(new KodkodValidateCmd(), file("p105.properties",
				BOUNDS + "A_x_min = 1\nA_x_max = 1\nA_x = Set{1}\n"));
		int min = output.indexOf("A_x_min: attribute A::x has type OclAny" + REFUSED);
		int max = output.indexOf("A_x_max: attribute A::x has type OclAny" + REFUSED);
		int values = output.indexOf("A_x: attribute A::x has type OclAny" + REFUSED);
		assertTrue(output, min >= 0);
		assertTrue(output, max > min);
		assertTrue(output, values > max);
		assertTrue(output, Pattern.compile("ERROR\\s+(ERROR: )?A_x_min: attribute").matcher(output).find());
		assertNoVerdict(output);
		assertEquals(0, shell.system().state().allObjects().size());
	}

	// claim 7
	@Test
	public void everyKeyFormOfANonTranslatedAttributeIsRefused() throws Exception {
		shell = new PluginShell(WITH_TUPLE);
		String[][] forms = {
				{ "A_x = Set{1}", "A_x", "OclAny" },
				{ "A_x_min = 1", "A_x_min", "OclAny" },
				{ "A_x_max = 1", "A_x_max", "OclAny" },
				{ "A_x_minSize = 0", "A_x_minSize", "OclAny" },
				{ "A_x_maxSize = 1", "A_x_maxSize", "OclAny" },
				{ "A = Set{a1}\na1_x = Set{1}", "a1_x", "OclAny" },
				{ "A_t = Set{1}", "A_t", "Tuple(k:Integer)" },
				{ "A_t_min = 1", "A_t_min", "Tuple(k:Integer)" },
		};
		for (String[] form : forms) {
			String output = shell.run(new KodkodValidateCmd(), file(form[1] + ".properties", BOUNDS + form[0] + "\n"));
			String attribute = form[2].equals("OclAny") ? "A::x" : "A::t";
			assertTrue(form[1] + ": " + output, output.contains(form[1] + ": attribute " + attribute + " has type " + form[2] + REFUSED));
			assertNoVerdict(output);
		}
	}

	// claim 8
	@Test
	public void configCheckListsKeysOfANonTranslatedAttributeAndDoesNotCallTheFileValid() throws Exception {
		shell = new PluginShell(WITH_TUPLE);
		String output = shell.run(new ConfigurationValidationCmd(), file("p105.properties",
				BOUNDS + "A_x_min = 1\nA_x_max = 1\nA_x = Set{1}\n"));
		assertTrue(output, output.contains("A_x_min: attribute A::x has type OclAny" + REFUSED));
		assertTrue(output, output.contains("A_x_max: attribute A::x has type OclAny" + REFUSED));
		assertTrue(output, output.contains("A_x: attribute A::x has type OclAny" + REFUSED));
		assertFalse(output, output.contains("is valid."));
	}

	// claim 9
	@Test
	public void keysOfTranslatedAttributesStillWork() throws Exception {
		configuration.setAutomaticDiagramExtraction(false);
		shell = new PluginShell(WITH_TUPLE);
		shell.type("!create a : A");
		shell.type("!a.n := 2");
		String output = shell.run(new KodkodValidateCmd(), file("ok.properties", BOUNDS + "A_n_min = 1\nA_n_max = 1\n"));
		assertSatisfiable(output);
		assertFalse(output, output.contains("attribute A::"));
		assertEquals("2", attributeValue(firstObjectName(), "n").toString());
	}

	// claim 10
	@Test
	public void aKeyThatNamesNothingIsStillReportedAsUnknown() throws Exception {
		shell = new PluginShell(WITH_TUPLE);
		String output = shell.run(new KodkodValidateCmd(), file("zzz.properties", BOUNDS + "A_zzz = Set{1}\n"));
		assertTrue(output, output.contains("A_zzz: this key names no class, attribute, association, invariant or option of the model."));
		assertFalse(output, output.contains("attribute A::"));
		assertNoVerdict(output);
	}

	// claim 11
	@Test
	public void theFileThePluginWritesHoldsNoKeyOfANonTranslatedAttributeAndRuns() throws Exception {
		DefaultConfigurationVisitor defaults = new DefaultConfigurationVisitor(new File(folder.getRoot(), "M.use").getPath());
		TestSpecifications.transform(WITH_TUPLE).accept(defaults);
		String written = new String(Files.readAllBytes(defaults.getFile().toPath()), StandardCharsets.UTF_8);
		assertTrue(written, written.contains("A_min") && written.contains("A_n ="));
		assertFalse(written, Pattern.compile("^\\s*#?\\s*A_[xt](_|\\W)", Pattern.MULTILINE).matcher(written).find());
		shell = new PluginShell(WITH_TUPLE);
		String output = shell.run(new KodkodValidateCmd(), defaults.getFile().getPath());
		assertSatisfiable(output);
	}

	// claim 12, no invariant reads the Tuple attribute
	@Test
	public void anAttributeNoInvariantReadsGivesNoErrorBeforeTheVerdict() throws Exception {
		configuration.setAutomaticDiagramExtraction(false);
		shell = new PluginShell(WITH_TUPLE);
		String output = shell.run(new KodkodValidateCmd(), file("ok.properties", BOUNDS));
		assertSatisfiable(output);
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, Pattern.compile("INFO .*A::t").matcher(output).find());
	}

	// claim 12, an invariant reads the Tuple attribute: the run ends in an error and has no verdict
	@Test
	public void anInvariantThatReadsAnUntranslatedAttributeEndsInAnErrorWithoutVerdict() throws Exception {
		shell = new PluginShell(WITH_TUPLE + "constraints\ncontext A inv five: self.t.k = 5\n");
		String output = shell.run(new KodkodValidateCmd(), file("ok.properties", BOUNDS));
		assertTrue(output, output.contains("ERROR"));
		assertTrue(output, output.contains("A::five"));
		assertNoVerdict(output);
	}

	private String validateExtractedState(String spec, String properties, String... commands) throws Exception {
		configuration.setAutomaticDiagramExtraction(true);
		shell = new PluginShell(spec);
		for (String command : commands) {
			shell.type(command);
		}
		return shell.run(new KodkodValidateCmd(), file("cfg.properties", properties));
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}

	private Value attributeValue(String objectName, String attributeName) {
		MSystem system = shell.system();
		MObject object = system.state().objectByName(objectName);
		assertTrue("no object " + objectName + " in the diagram", object != null);
		return object.state(system.state()).attributeValue(attributeName);
	}

	private String firstObjectName() {
		return shell.system().state().allObjects().iterator().next().name();
	}

	private boolean checkAccepts() {
		shell.type("check");
		return Shell.getInstance().lastCheckResult();
	}

	private static void assertSatisfiable(String output) {
		assertTrue(output, Pattern.compile("INFO (TRIVIALLY_)?SATISFIABLE").matcher(output).find());
	}

	private static void assertNoVerdict(String output) {
		assertFalse(output, Pattern.compile("INFO (TRIVIALLY_)?(UN)?SATISFIABLE").matcher(output).find());
	}
}
