package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.kodkod.plugin.KodkodQueryCmd;
import org.tzi.use.kodkod.plugin.KodkodScrollingValidateCmd;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;

public class KodkodQueryCmd_Test {

	private static final String SPEC = "model M\n"
			+ "enum Color { red, green }\n"
			+ "class Person\n"
			+ "attributes\n"
			+ "  age : Integer\n"
			+ "  name : String\n"
			+ "  color : Color\n"
			+ "end\n";

	private static final String PROPERTIES = "[people]\n"
			+ "Integer_min = 0\n"
			+ "Integer_max = 3\n"
			+ "String_max = 2\n"
			+ "Person_min = 2\n"
			+ "Person_max = 2\n"
			+ "Person_age_min = 2\n"
			+ "Person_age_max = 2\n"
			+ "Person_name_min = 2\n"
			+ "Person_name_max = 2\n"
			+ "Person_color_min = 2\n"
			+ "Person_color_max = 2\n";

	private static final String NOT_COMPARED = "Not compared with a solution";

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

	@Test
	public void printsTheValueOfUseWithItsType() throws Exception {
		solve();
		String output = query("Person.allInstances");
		assertTrue(output, output.contains("INFO Set{person1,person2} : Set(Person)"));
		assertFalse(output, output.contains("ERROR"));
	}

	@Test
	public void printsNothingMoreWhenTheSolutionAgrees() throws Exception {
		solve();
		String[][] valuesOfQueries = {
				{ "Person.allInstances->size()", "2 : Integer" },
				{ "person1.age", "[0-3] : Integer" },
				{ "person1.age >= 0", "true : Boolean" },
				{ "person1.name", "'.*' : String" },
				{ "person1.color", "Color::(red|green) : Color" },
				{ "Set{1, 2}", "Set\\{1,2\\} : Set\\(Integer\\)" },
				{ "null", "null : OclVoid" } };
		for (String[] valueOfQuery : valuesOfQueries) {
			String output = query(valueOfQuery[0]);
			assertTrue(valueOfQuery[0] + ": " + output, output.trim().matches("INFO " + valueOfQuery[1]));
		}
	}

	@Test
	public void printsTheNewValueOnceTheStateChanged() throws Exception {
		solve();
		shell.type("!set person1.age := 7");
		String output = query("person1.age");
		assertTrue(output, output.contains("INFO 7 : Integer"));
		assertTrue(output, output.contains(NOT_COMPARED));
		assertFalse(output, output.contains("ERROR"));
	}

	@Test
	public void doesNotCompareOnceAnObjectIsCreated() throws Exception {
		solve();
		shell.type("!create person3 : Person");
		String output = query("Person.allInstances->size()");
		assertTrue(output, output.contains("INFO 3 : Integer"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareOnceAnObjectIsDestroyed() throws Exception {
		solve();
		shell.type("!destroy person2");
		String output = query("Person.allInstances->size()");
		assertTrue(output, output.contains("INFO 1 : Integer"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareOnceTheStateChangedWithoutAnEvent() throws Exception {
		solve();
		UseSystemApi nativeApi = UseSystemApi.create(shell.system(), false);
		nativeApi.setAttributeValue("person1", "age", "7");
		String output = query("person1.age");
		assertTrue(output, output.contains("INFO 7 : Integer"));
		assertNotComparedWithoutError(output);
		nativeApi.createObject("Person", "person3");
		output = query("Person.allInstances->size()");
		assertTrue(output, output.contains("INFO 3 : Integer"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareOnceTheLastStatementIsUndone() throws Exception {
		solve();
		shell.type("undo");
		String output = query("Person.allInstances->size()");
		assertNotComparedWithoutError(output);
	}

	@Test
	public void reportsAnIntegerThatOverflowsInTheTranslation() throws Exception {
		solve();
		String output = query("3 * 3 * 3");
		assertTrue(output, output.contains("INFO 27 : Integer"));
		String error = lineStartingWith(output, "ERROR");
		assertTrue(output, error.contains("USE: 27") && error.contains("Kodkod: []"));
	}

	@Test
	public void doesNotCompareALiteralTheSolutionHasNoAtomFor() throws Exception {
		solve();
		String output = query("'zzz'");
		assertTrue(output, output.contains("INFO 'zzz' : String"));
		assertNotComparedWithoutError(output);
		output = query("Color::green");
		assertTrue(output, output.contains("INFO Color::green : Color"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareBeforeASolve() {
		String output = query("1 + 2");
		assertTrue(output, output.contains("INFO 3 : Integer"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareAfterScrollingBackToAnEarlierSolution() throws Exception {
		shell.run(new KodkodScrollingValidateCmd(), properties());
		shell.run(new KodkodScrollingValidateCmd(), "next");
		String output = query("Person.allInstances->size()");
		assertFalse(output, output.contains(NOT_COMPARED));
		shell.run(new KodkodScrollingValidateCmd(), "previous");
		output = query("Person.allInstances->size()");
		assertTrue(output, output.contains("INFO 2 : Integer"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareOnceDisabled() throws Exception {
		solve();
		shell.run(new KodkodQueryCmd(), "disable");
		String output = query("Person.allInstances->size()");
		assertTrue(output, output.contains("INFO 2 : Integer"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareABagWithTheSetOfTheTranslation() throws Exception {
		solve();
		String output = query("Person.allInstances->collect(p | p.age)");
		assertTrue(output, output.contains(" : Bag(Integer)"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareAnExpressionTheTranslationRefuses() throws Exception {
		solve();
		String output = query("1 / 2");
		assertTrue(output, output.contains("INFO 0.5 : Real"));
		assertNotComparedWithoutError(output);
	}

	@Test
	public void doesNotCompareAVariableBoundToAnotherValue() throws Exception {
		solve();
		shell.type("!person2 := person1");
		String output = query("person2 = person1");
		assertTrue(output, output.contains("INFO true : Boolean"));
		assertNotComparedWithoutError(output);
		assertTrue(output, lineStartingWith(output, "INFO " + NOT_COMPARED).contains("person2"));
		shell.type("!five := 5");
		output = query("five + 1");
		assertTrue(output, output.contains("INFO 6 : Integer"));
		assertNotComparedWithoutError(output);
		assertTrue(output, lineStartingWith(output, "INFO " + NOT_COMPARED).contains("five"));
	}

	@Test
	public void comparesAnIteratorVariableThatHidesABoundVariable() throws Exception {
		solve();
		shell.type("!person2 := person1");
		String output = query("Person.allInstances->select(person2 | person2.age >= 0)->size()");
		assertTrue(output, output.contains("INFO 2 : Integer"));
		assertFalse(output, output.contains(NOT_COMPARED));
		assertFalse(output, output.contains("ERROR"));
	}

	private static void assertNotComparedWithoutError(String output) {
		assertTrue(output, output.contains(NOT_COMPARED));
		assertFalse(output, output.contains("ERROR"));
		assertFalse(output, output.contains("Exception"));
	}

	private static String lineStartingWith(String output, String start) {
		for (String line : output.split("\n")) {
			if (line.startsWith(start)) {
				return line;
			}
		}
		return "";
	}

	private void solve() throws Exception {
		shell.run(new KodkodValidateCmd(), properties());
	}

	private String query(String ocl) {
		return shell.run(new KodkodQueryCmd(), ocl);
	}

	private String properties() throws Exception {
		File file = folder.newFile("people.properties");
		Files.write(file.toPath(), PROPERTIES.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}
}
