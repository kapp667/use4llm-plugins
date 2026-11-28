package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.kodkod.transform.enrich.ObjectDiagramModelEnricher;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.ocl.expr.EvalContext;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * An object diagram state read with objExtraction on fixes the attribute values
 * of the user's objects, whatever the element type of a collection value. A
 * state that breaks an invariant gives UNSATISFIABLE and stays as it is, a
 * state that satisfies it gives a solution that keeps the user's values, and a
 * value the translation cannot carry is refused with an ERROR and no verdict.
 * The models share one shape: an enum Color, a class B, and a class A with one
 * attribute v of the type under test.
 */
public class ObjectDiagramCollectionExtraction_Test {

	private static final Pattern SATISFIABLE = Pattern.compile("INFO (TRIVIALLY_)?SATISFIABLE");
	private static final Pattern UNSATISFIABLE = Pattern.compile("INFO (TRIVIALLY_)?UNSATISFIABLE");

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final boolean extraction = KodkodModelValidatorConfiguration.getInstance().isAutomaticDiagramExtraction();
	private PluginShell shell;

	@Before
	public void extractionOn() {
		KodkodModelValidatorConfiguration.getInstance().setAutomaticDiagramExtraction(true);
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
	public void enumCollectionThatBreaksTheInvariantIsUnsatisfiableAndKeepsItsValue() throws Exception {
		String output = validate("Set(Color)", "self.v->includes(Color::red)", 0, "Set{Color::green}");
		assertUnsatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->collect(x | x.v) = Bag{Color::green}"));
	}

	// claim 2
	@Test
	public void objectCollectionThatBreaksTheInvariantIsUnsatisfiableAndKeepsItsValue() throws Exception {
		String output = validate("Set(B)", "self.v->size() = 2", 2, "Set{b1}", "!create b1 : B", "!create b2 : B");
		assertUnsatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->collect(x | x.v) = Bag{b1}"));
	}

	// claim 3
	@Test
	public void objectCollectionThatSatisfiesTheInvariantKeepsItsValue() throws Exception {
		String output = validate("Set(B)", "self.v->size() = 2", 2, "Set{b1,b2}", "!create b1 : B", "!create b2 : B");
		assertSatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->collect(x | x.v) = Bag{b1,b2}"));
		assertTrue(output, stateIsValid());
	}

	// claim 4
	@Test
	public void enumCollectionThatSatisfiesTheInvariantKeepsItsValue() throws Exception {
		String output = validate("Set(Color)", "self.v->includes(Color::red)", 0, "Set{Color::red,Color::green}");
		assertSatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->collect(x | x.v) = Bag{Color::red,Color::green}"));
		assertTrue(output, stateIsValid());
	}

	// claim 5
	@Test
	public void nestedCollectionIsRefusedWithoutAVerdict() throws Exception {
		String output = validate("Set(Set(Integer))", "true", 0, "Set{Set{1}}");
		assertTrue(output, Pattern.compile("^ERROR Error while extracting information from the object diagram\\..*"
				+ "Nested collections not supported", Pattern.MULTILINE).matcher(output).find());
		assertFalse(output, output.contains("SATISFIABLE"));
		assertFalse(output, output.contains("Object diagram extraction successful"));
	}

	// claim 6
	@Test
	public void integerCollectionThatBreaksTheInvariantStaysUnsatisfiable() throws Exception {
		String output = validate("Set(Integer)", "self.v->includes(2)", 0, "Set{1}");
		assertUnsatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->collect(x | x.v) = Bag{1}"));
	}

	// claim 7
	@Test
	public void enumValueThatBreaksTheInvariantStaysUnsatisfiable() throws Exception {
		String output = validate("Color", "self.v = Color::red", 0, "Color::green");
		assertUnsatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->collect(x | x.v) = Bag{Color::green}"));
	}

	// claim 8
	@Test
	public void withoutExtractionTheStateIsNotReadAndTheModelIsSatisfiable() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setAutomaticDiagramExtraction(false);
		String output = validate("Set(Color)", "self.v->includes(Color::red)", 0, "Set{Color::green}");
		assertSatisfiableWithoutError(output);
		assertTrue(output, stateIsValid());
	}

	// claim 9: what the extraction hands to the translation
	@Test
	public void extractionFixesAnEnumCollectionToItsElements() throws Exception {
		IModel model = enrich("Set(Color)", "Set{Color::green}");
		assertFixed(model, "A_a", "Color_green");
	}

	// claim 9
	@Test
	public void extractionFixesAnObjectCollectionToItsElements() throws Exception {
		IModel model = enrich("Set(B)", "Set{b1}", "!create b1 : B");
		assertFixed(model, "A_a", "B_b1");
	}

	// claim 9
	@Test
	public void extractionRefusesANestedCollection() throws Exception {
		try {
			enrich("Set(Set(Integer))", "Set{Set{1}}");
		} catch (IllegalStateException refused) {
			assertEquals("Error while extracting information from the object diagram.", refused.getMessage());
			return;
		}
		fail("a nested collection was dropped from the state and the extraction reported success");
	}

	// claim 10
	@Test
	public void emptyCollectionThatBreaksTheInvariantIsUnsatisfiableAndStaysEmpty() throws Exception {
		String output = validate("Set(Integer)", "self.v->notEmpty()", 0, "Set{}");
		assertUnsatisfiableWithoutError(output);
		assertTrue("the state of a.v was not kept: " + output, evaluate("A.allInstances()->forAll(x | x.v->isEmpty())"));
	}

	private void assertFixed(IModel model, String... expected) {
		Set<String[]> fixed = model.getClass("A").getAttribute("v").getConfigurator().getSpecificValues();
		assertTrue("A.v has no specific value " + Arrays.toString(expected) + ", it has " + fixed.stream().map(Arrays::toString).collect(Collectors.toList()),
				fixed.stream().anyMatch(value -> Arrays.equals(value, expected)));
	}

	private void assertSatisfiableWithoutError(String output) {
		assertTrue(output, SATISFIABLE.matcher(output).find());
		assertFalse(output, UNSATISFIABLE.matcher(output).find());
		assertFalse(output, output.contains("ERROR"));
	}

	private void assertUnsatisfiableWithoutError(String output) {
		assertTrue(output, UNSATISFIABLE.matcher(output).find());
		assertFalse(output, SATISFIABLE.matcher(output).find());
		assertFalse(output, output.contains("ERROR"));
	}

	/**
	 * Opens the model and builds the state: the object a, the given commands for
	 * more objects, then the value of a.v.
	 */
	private void open(String type, String invariant, String value, String... objects) {
		shell = new PluginShell("model M\n"
				+ "enum Color { red, green }\n"
				+ "class B\nend\n"
				+ "class A\nattributes\n  v : " + type + "\nend\n"
				+ "constraints\n"
				+ "context A inv I: " + invariant + "\n");
		shell.type("!create a : A");
		for (String line : objects) {
			shell.type(line);
		}
		shell.type("!set a.v := " + value);
	}

	/**
	 * Builds the state and runs mv -validate with one A and the given number of B.
	 */
	private String validate(String type, String invariant, int objectsOfB, String value, String... objects) throws Exception {
		open(type, invariant, value, objects);
		File file = folder.newFile("bounds.properties");
		Files.write(file.toPath(), ("[c]\nInteger_min = 0\nInteger_max = 3\nA_min = 1\nA_max = 1\n"
				+ "B_min = " + objectsOfB + "\nB_max = " + objectsOfB + "\n").getBytes(StandardCharsets.UTF_8));
		return shell.run(new KodkodValidateCmd(), file.getPath());
	}

	/**
	 * Builds the state and lets the extraction read it into the transformed model.
	 */
	private IModel enrich(String type, String value, String... objects) {
		open(type, "true", value, objects);
		MSystem system = shell.system();
		IModel model = TestSpecifications.transform(system.model());
		new ObjectDiagramModelEnricher().enrichModel(system, model);
		return model;
	}

	private boolean evaluate(String query) {
		MSystem system = shell.system();
		MSystemState state = system.state();
		StringWriter errors = new StringWriter();
		VarBindings objects = new VarBindings();
		for (MObject object : state.allObjects()) {
			objects.push(object.name(), object.value());
		}
		Expression expression = OCLCompiler.compileExpression(system.model(), state, query, "query",
				new PrintWriter(errors), objects);
		if (expression == null) {
			fail("the query does not compile: " + errors);
		}
		return expression.eval(new EvalContext(state, state, objects, null, "")).toString().equals("true");
	}

	private boolean stateIsValid() {
		return shell.system().state().check(new PrintWriter(new StringWriter()), false, false, false, new ArrayList<String>());
	}
}
