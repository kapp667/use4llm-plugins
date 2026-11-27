package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.use.config.Options;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.uml.sys.MSystem;

/**
 * Tests mv -validate on configuration files.
 */
public class KodkodValidateCmd_Test {

	private static final String SPEC = "model M\n"
			+ "class Employee\n"
			+ "attributes\n"
			+ "  salary : Integer\n"
			+ "end\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final StringWriter log = new StringWriter();
	private WriterAppender appender;
	private Session session;

	@Before
	public void setUp() {
		appender = new WriterAppender(new PatternLayout("%p %m%n"), log);
		Logger.getLogger("org.tzi").addAppender(appender);
		open(SPEC);
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

	@After
	public void tearDown() {
		Logger.getLogger("org.tzi").removeAppender(appender);
	}

	private static final String BOUNDS = "Integer_min = -2\n"
			+ "Integer_max = 2\n"
			+ "Employee_salary_min = 1\n"
			+ "Employee_salary_max = 1\n";

	@Test
	public void refusesAClassMinAboveItsMax() throws Exception {
		String output = check(file("bad.properties", "[bad]\n" + BOUNDS + "Employee_min = 3\nEmployee_max = 2\n"));
		assertTrue(output, output.contains("ERROR"));
		assertTrue(output, output.contains("CLASS_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX"));
		assertFalse(output, output.contains("SATISFIABLE"));
	}

	@Test
	public void refusesAValueThatIsNotANumber() throws Exception {
		String output = check(file("nan.properties", "[nan]\n" + BOUNDS + "Employee_min = 1\nEmployee_max = many\n"));
		assertTrue(output, output.contains("ERROR"));
		assertTrue(output, output.contains("Employee_max"));
		assertFalse(output, output.contains("SATISFIABLE"));
	}

	@Test
	public void solvesDespiteASuperfluousSetting() throws Exception {
		String output = check(file("real.properties",
				"[real]\n" + BOUNDS + "Employee_min = 1\nEmployee_max = 1\nReal_min = -1.0\nReal_max = 1.0\nReal_step = 0.5\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void solvesWithBoundsOnTheTotalOfAnAbstractClass() throws Exception {
		open("model F\nabstract class Fruit\nend\nclass Apple < Fruit\nend\n");
		String output = check(file("fruit.properties", "[fruit]\nFruit_min = 1\nFruit_max = 1\nApple_min = 0\nApple_max = 3\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertEquals(output, 1, session.system().state().allObjects().size());
	}

	@Test
	public void solvesWithAnAttributeMaxAboveItsClassMax() throws Exception {
		String output = check(file("attr.properties", "[attr]\nInteger_min = -2\nInteger_max = 2\n"
				+ "Employee_min = 1\nEmployee_max = 1\nEmployee_salary_min = 1\nEmployee_salary_max = 3\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void solvesWithARequiredLinkOfAnAssociationClass() throws Exception {
		open("model AC\nclass Person\nend\nclass Company\nend\n"
				+ "associationclass Job between\n  Person[*] role worker\n  Company[*] role employer\nend\n");
		String output = check(file("ac.properties", "[ac]\nPerson = Set{p1}\nPerson_min = 1\nPerson_max = 1\n"
				+ "Company = Set{c1}\nCompany_min = 1\nCompany_max = 1\n"
				+ "Job_ac = Set{j1}\nJob_min = 1\nJob_max = 1\nJob = Set{(j1, p1, c1)}\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
	}

	@Test
	public void solvesWithARequiredLinkBetweenGeneratedObjects() throws Exception {
		open("model G\nclass Person\nend\nclass Company\nend\n"
				+ "association Works between\n  Person[*] role worker\n  Company[*] role employer\nend\n");
		String output = check(file("gen.properties", "[gen]\nPerson_min = 2\nPerson_max = 2\n"
				+ "Company_min = 1\nCompany_max = 1\nWorks_min = 1\nWorks_max = 2\nWorks = Set{(person2, company1)}\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
	}

	@Test
	public void solvesWithASingleRealValue() throws Exception {
		open("model R\nclass C\nattributes\n  r : Real\nend\n");
		String output = check(file("real.properties", "[real]\nReal_min = 1.0\nReal_max = 1.0\nReal_step = 0.5\n"
				+ "C_min = 1\nC_max = 1\nC_r_min = 1\nC_r_max = 1\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void solvesWithIntegerValuesGivenForAnAttribute() throws Exception {
		open("model I\nclass C\nattributes\n  x : Integer\nend\n");
		String output = check(file("int.properties", "[int]\nC_min = 1\nC_max = 1\nC_x_min = 1\nC_x_max = 1\nC_x = Set{1,2}\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void refusesAStringAttributeWhenTheStringSettingsAreMissing() throws Exception {
		open("model S\nclass C\nattributes\n  name : String\nend\n");
		String output = check(file("nostring.properties", "[nostring]\nC_min = 1\nC_max = 1\nC_name_min = 1\nC_name_max = 1\n"));
		assertTrue(output, output.contains("ERROR"));
		assertTrue(output, output.contains("STRING_SETTINGS_NESSESSARY"));
		assertFalse(output, output.contains("SATISFIABLE"));
	}

	@Test
	public void solvesWithAStringAttributeAndTheStringSettings() throws Exception {
		open("model S\nclass C\nattributes\n  name : String\nend\n");
		String output = check(file("string.properties", "[string]\nString_min = 1\nString_max = 1\n"
				+ "C_min = 1\nC_max = 1\nC_name_min = 1\nC_name_max = 1\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void solvesWithAStringAttributeThatNoObjectCanHold() throws Exception {
		open("model S\nclass C\nattributes\n  name : String\nend\n");
		String output = check(file("noobject.properties", "[noobject]\nC_min = 0\nC_max = 0\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("SATISFIABLE"));
	}

	@Test
	public void keepsTheAttributeMaxWhenEveryObjectHasAValue() throws Exception {
		open("model D\nclass C\nattributes\n  x : Integer\nend\n");
		String output = check(file("alldefined.properties", "[alldefined]\nInteger_min = 1\nInteger_max = 1\nC_min = 2\nC_max = 2\nC_x_min = -1\nC_x_max = 1\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
	}

	@Test
	public void solvesWhenTheAttributeMaxAllowsEveryObjectAValue() throws Exception {
		open("model D\nclass C\nattributes\n  x : Integer\nend\n");
		String output = check(file("alldefined.properties", "[alldefined]\nInteger_min = 1\nInteger_max = 1\nC_min = 2\nC_max = 2\nC_x_min = -1\nC_x_max = 2\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}

	private String check(String... arguments) {
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
