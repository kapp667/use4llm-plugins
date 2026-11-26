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
 * Runs the configuration check command, mv -vc, on configuration files.
 */
public class ConfigurationValidationCmd_Test {

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

	@Test
	public void namesTheRuleAClassMinAboveItsMaxBreaks() throws Exception {
		String output = check(file("bad.properties", "[bad]\nEmployee_min = 3\nEmployee_max = 2\n"));
		assertTrue(output, output.contains("Config bad is invalid."));
		assertTrue(output, output.contains("Rule CLASS_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX is violated."));
		assertTrue(output, output.contains("Employee_min (3) should be less than or equal Employee_max (2)."));
	}

	@Test
	public void reportsAMissingFileAsAnError() throws Exception {
		String output = check(new File(folder.getRoot(), "missing.properties").getPath());
		assertTrue(output, output.contains("ERROR Could not read from file."));
	}

	@Test
	public void refusesANameTheFileDoesNotHold() throws Exception {
		String path = file("two.properties", "[ok]\nEmployee_min = 1\nEmployee_max = 1\n");
		String output = check(path, "ok", "nosuch");
		assertTrue(output, output.contains("ERROR"));
		assertFalse(output, output.contains("Config ok"));
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
