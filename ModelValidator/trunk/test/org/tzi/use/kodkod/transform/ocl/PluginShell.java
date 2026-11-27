package org.tzi.use.kodkod.transform.ocl;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringWriter;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.tzi.use.config.Options;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.uml.sys.MSystem;

class PluginShell {

	private final StringWriter log = new StringWriter();
	private final WriterAppender appender = new WriterAppender(new PatternLayout("%p %m%n"), log);
	private final Session session = new Session();

	PluginShell(String spec) {
		Logger.getLogger("org.tzi").addAppender(appender);
		session.setSystem(new MSystem(TestSpecifications.compile("model Empty\n")));
		PluginModelFactory.INSTANCE.registerForSession(session);
		session.setSystem(new MSystem(TestSpecifications.compile(spec)));
		boolean plugins = Options.doPLUGIN;
		Options.doPLUGIN = false;
		Shell.createInstance(session, null);
		Options.doPLUGIN = plugins;
	}

	String run(IPluginShellCmdDelegate command, String... arguments) {
		int start = log.getBuffer().length();
		PrintStream standardError = System.err;
		ByteArrayOutputStream written = new ByteArrayOutputStream();
		System.setErr(new PrintStream(written, true));
		try {
			command.performCommand(new IPluginShellCmd() {
				@Override
				public void executeCmd(String cmd, String cmdArguments, String[] argList) {
				}

				@Override
				public String getCmd() {
					return "mv";
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
					return " " + String.join(" ", arguments);
				}

				@Override
				public String[] getCmdArgumentList() {
					return arguments;
				}
			});
		} finally {
			System.setErr(standardError);
		}
		return log.getBuffer().substring(start) + written;
	}

	MSystem system() {
		return session.system();
	}

	void type(String line) {
		Shell.getInstance().processLineSafely(line);
	}

	void close() {
		Logger.getLogger("org.tzi").removeAppender(appender);
	}
}
