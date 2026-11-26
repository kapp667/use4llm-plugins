package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Checks the shell commands the plugin declares to USE in its descriptor.
 */
public class PluginCommands_Test {

	@Test
	public void noCommandDownloadsSolverLibraries() throws Exception {
		List<String> commands = shellCommandsAndAliases();
		assertFalse(commands.toString(), commands.contains("modelvalidator -downloadSolvers"));
		assertFalse(commands.toString(), commands.contains("mv -dl"));
	}

	private static List<String> shellCommandsAndAliases() throws Exception {
		NodeList nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new File("useplugin.xml")).getElementsByTagName("command");
		List<String> commands = new ArrayList<String>();
		for (int i = 0; i < nodes.getLength(); i++) {
			Element command = (Element) nodes.item(i);
			commands.add(command.getAttribute("shellcmd"));
			commands.add(command.getAttribute("alias"));
		}
		return commands;
	}
}
