package org.tzi.use.kodkod.transform.ocl;

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
import org.tzi.use.kodkod.plugin.ConfigurationValidationCmd;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.uml.sys.MSystem;

/**
 * Runs the configuration check command, mv -vc, on configurations whose
 * attribute maximum A_x_max is above the maximum of the declaring class A but
 * within the maxima of A and its subclass B together: the attribute belongs to
 * the objects of both.
 */
public class SubclassAttributeMaxValidation_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "end\n"
			+ "class B < A\n"
			+ "end\n";

	private static final String RULE = "ATTRIBUTE_SETTINGS_MAX_STAR_OR_LESS_THAN_OR_EQUAL_CLASS_MAX";

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
		// As in USE, the plugin watches the session before the model is opened, so
		// it transforms this model instead of keeping the one of an earlier test.
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

	private String configuration(String name, int attributeMax) {
		return "[" + name + "]\n"
				+ "Integer_min = -10\n"
				+ "Integer_max = 10\n"
				+ "A_min = 1\n"
				+ "A_max = 1\n"
				+ "B_min = 1\n"
				+ "B_max = 1\n"
				+ "A_x_min = 0\n"
				+ "A_x_max = " + attributeMax + "\n";
	}

	@Test
	public void attributeMaxWithinTheMaximaOfTheClassAndItsSubclassesIsValid() throws Exception {
		String output = check(file("vcx.properties", configuration("vcx", 2)));
		assertTrue(output, output.contains("Config vcx is valid."));
		assertFalse(output, output.contains(RULE));
	}

	@Test
	public void attributeMaxAboveTheMaximaOfTheClassAndItsSubclassesIsStillInvalid() throws Exception {
		String output = check(file("vcover.properties", configuration("vcover", 3)));
		assertTrue(output, output.contains("Config vcover is invalid."));
		assertTrue(output, output.contains("Rule " + RULE + " is violated."));
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}

	private String check(String... arguments) {
		final String[] argumentList = arguments;
		new ConfigurationValidationCmd().performCommand(new IPluginShellCmd() {
			@Override
			public void executeCmd(String cmd, String cmdArguments, String[] argList) {
			}

			@Override
			public String getCmd() {
				return "mv -vc";
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
