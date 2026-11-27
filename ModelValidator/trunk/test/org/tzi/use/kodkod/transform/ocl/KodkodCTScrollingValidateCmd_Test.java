package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.use.kodkod.plugin.KodkodCTScrollingValidateCmd;

public class KodkodCTScrollingValidateCmd_Test {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private PluginShell shell;

	@Before
	public void setUp() {
		shell = new PluginShell("model M\nclass Person\nend\n");
	}

	@After
	public void tearDown() {
		shell.close();
	}

	@Test
	public void reportsATermThatOverflowsInTheTranslation() throws Exception {
		String output = shell.run(new KodkodCTScrollingValidateCmd(),
				file("people.properties", "[people]\nInteger_min = 0\nInteger_max = 3\nPerson_min = 1\nPerson_max = 1\n"),
				"-f", file("terms.txt", "[big] 3 * 3 * 3\n"));
		assertTrue(output, output.contains("Kodkod: []; USE: 27"));
		assertTrue(output, output.contains("big: 27"));
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}
}
