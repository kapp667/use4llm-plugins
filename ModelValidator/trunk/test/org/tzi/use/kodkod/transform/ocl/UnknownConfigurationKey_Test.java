package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.use.config.Options;
import org.tzi.use.kodkod.plugin.ConfigurationValidationCmd;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.uml.sys.MSystem;

/**
 * A key of a configuration file that names nothing of the model must be reported as an
 * error and the configuration refused, never ignored. Keys that name something must go on
 * working.
 */
public class UnknownConfigurationKey_Test {

	private static final String SPEC = "model Emp\n"
			+ "class Dept\nattributes\n  title : String\nend\n"
			+ "class Employee\nattributes\n  age : Integer\n  rate : Real\n  skills : Set(Integer)\nend\n"
			+ "association Works between\n  Employee [*] role staff\n  Dept [0..1] role dept\nend\n"
			+ "constraints\ncontext Employee inv ageOK: self.age >= 0\n";

	private static final String GLOBALS = "[default]\nInteger_min = -10\nInteger_max = 10\n"
			+ "Real_min = 0\nReal_max = 2\nReal_step = 0.5\n"
			+ "Dept_min = 0\nDept_max = 0\nWorks_min = 0\nWorks_max = 0\n";

	private static final String ONE_EMPLOYEE = "Employee_min = 1\nEmployee_max = 1\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final StringWriter log = new StringWriter();
	private WriterAppender appender;
	private Session session;

	@Before
	public void setUp() {
		appender = new WriterAppender(new PatternLayout("%p %m%n"), log);
		Logger.getLogger("org.tzi").addAppender(appender);
		session = new Session();
		// As in USE, the plugin watches the session before the model is opened, so it
		// transforms the model under test, not one left over from an earlier test.
		session.setSystem(new MSystem(TestSpecifications.compile("model Empty\n")));
		PluginModelFactory.INSTANCE.registerForSession(session);
		session.setSystem(new MSystem(TestSpecifications.compile(SPEC)));
		boolean plugins = Options.doPLUGIN;
		Options.doPLUGIN = false;
		Shell.createInstance(session, null);
		Options.doPLUGIN = plugins;
	}

	@After
	public void tearDown() {
		Logger.getLogger("org.tzi").removeAppender(appender);
	}

	private static boolean names(String output, String key) {
		return Pattern.compile("ERROR: .*" + key
				+ ".*names no class, attribute, association, invariant or option").matcher(output).find();
	}

	private static boolean hasVerdict(String output) {
		return output.contains("SATISFIABLE");
	}

	@Test
	public void validateRefusesAMisspeltClassName() throws Exception {
		String output = validate(file("typo-class.properties", GLOBALS + "Employe_min = 5\nEmploye_max = -3\n"));
		assertTrue(output, names(output, "Employe_min"));
		assertTrue(output, names(output, "Employe_max"));
		assertFalse(output, hasVerdict(output));
	}

	@Test
	public void configCheckDoesNotCallAMisspeltClassNameValid() throws Exception {
		String output = configCheck(file("typo-class.properties", GLOBALS + "Employe_min = 5\nEmploye_max = -3\n"));
		assertFalse(output, output.contains("Config default is valid."));
		assertTrue(output, output.contains("Employe_min"));
		assertTrue(output, output.contains("Employe_max"));
	}

	@Test
	public void validateRefusesAMisspeltAttributeName() throws Exception {
		String output = validate(file("typo-attr.properties",
				GLOBALS + ONE_EMPLOYEE + "Employee_agee = Set{-5}\n"));
		assertTrue(output, names(output, "Employee_agee"));
		assertFalse(output, hasVerdict(output));
	}

	@Test
	public void validateRefusesAnInvariantNameInTheWrongCase() throws Exception {
		String output = validate(file("typo-inv.properties",
				GLOBALS + ONE_EMPLOYEE + "Employee_age = Set{-5}\nEmployee_ageOk = inactive\n"));
		assertTrue(output, names(output, "Employee_ageOk"));
		assertFalse(output, hasVerdict(output));
	}

	@Test
	public void keepsEveryKnownKeyForm() throws Exception {
		String path = file("valid.properties", "[default]\nInteger_min = -2\nInteger_max = 10\nInteger = Set{7}\n"
				+ "String = Set{'x'}\nReal_min = 0\nReal_max = 2\nReal_step = 0.5\nReal = Set{1.5}\n"
				+ "aggregationcyclefreeness = on\nforbiddensharing = on\n"
				+ "Dept_min = 1\nDept_max = 1\nDept_title = Set{'x'}\nDept_title_min = 1\nDept_title_max = 1\n"
				+ "Employee_ageOK = active\nEmployee_min = 1\nEmployee_max = 2\n"
				+ "Employee_age = Set{3}\nEmployee_age_min = 1\nEmployee_age_max = 2\n"
				+ "Employee_rate = Set{1.5}\nEmployee_skills = Set{3}\n"
				+ "Employee_skills_minSize = 0\nEmployee_skills_maxSize = 1\n"
				+ "Works_min = 1\nWorks_max = 2\n");
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config default is valid."));
		assertFalse(vc, vc.contains("names no class"));
		String output = validate(path);
		assertFalse(output, output.contains("ERROR"));
		assertFalse(output, output.contains("WARN"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void keepsAnInvariantSwitchedOffByItsExactName() throws Exception {
		String output = validate(file("ok-inv.properties",
				GLOBALS + ONE_EMPLOYEE + "Employee_age = Set{-5}\nEmployee_ageOK = inactive\n"));
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}

	private String validate(final String... arguments) {
		new KodkodValidateCmd().performCommand(cmd("mv -validate", arguments));
		return log.toString();
	}

	private String configCheck(final String... arguments) {
		new ConfigurationValidationCmd().performCommand(cmd("mv -vc", arguments));
		return log.toString();
	}

	private IPluginShellCmd cmd(final String name, final String[] argumentList) {
		return new IPluginShellCmd() {
			@Override
			public void executeCmd(String cmd, String cmdArguments, String[] argList) {
			}

			@Override
			public String getCmd() {
				return name;
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
		};
	}
}
