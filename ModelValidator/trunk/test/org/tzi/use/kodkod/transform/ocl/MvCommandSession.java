package org.tzi.use.kodkod.transform.ocl;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.After;
import org.junit.Rule;
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
 * Base of the tests that run the shell commands mv -vc and mv -validate on a model
 * and a configuration file, and read back what the command logged and what USE
 * holds as its state.
 */
abstract class MvCommandSession {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	protected final StringWriter log = new StringWriter();
	protected Session session;
	private WriterAppender appender;

	/** Opens the model as USE does: the plugin watches the session before the model is set. */
	protected void open(String spec) {
		if (appender == null) {
			appender = new WriterAppender(new PatternLayout("%p %m%n"), log);
			Logger.getLogger("org.tzi").addAppender(appender);
		}
		session = new Session();
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
		if (appender != null) {
			Logger.getLogger("org.tzi").removeAppender(appender);
		}
	}

	protected String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}

	/** The text mv -vc has logged so far. */
	protected String configCheck(String... arguments) {
		new ConfigurationValidationCmd().performCommand(cmd("mv -vc", arguments));
		return log.toString();
	}

	/** The text mv -validate has logged so far. */
	protected String validate(String... arguments) {
		new KodkodValidateCmd().performCommand(cmd("mv -validate", arguments));
		return log.toString();
	}

	/** USE's check command on the current state: true when no invariant fails. */
	protected boolean useCheckPasses() {
		StringWriter out = new StringWriter();
		return session.system().state().check(new PrintWriter(out), false, false, false, Collections.<String>emptyList());
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
