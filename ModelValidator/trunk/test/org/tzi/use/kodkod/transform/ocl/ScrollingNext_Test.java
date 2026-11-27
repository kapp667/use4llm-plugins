package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.use.kodkod.plugin.KodkodScrollingValidateCmd;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.sys.MSystem;

/**
 * Tests that mv -scrolling shows each state of the bounds once, then reports
 * that no state is left.
 */
public class ScrollingNext_Test {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private PluginShell shell;

	@After
	public void tearDown() {
		shell.close();
	}

	@Test
	public void showsTheStateAfterTheEmptyState() throws Exception {
		shell = new PluginShell("model M\nclass A\nend\n");
		assertEquals(Arrays.asList("A=0", "A=1"), scroll("A_min = 0\nA_max = 1\n"));
	}

	@Test
	public void showsTheStateThatAddsAnObjectToAClassWithNone() throws Exception {
		shell = new PluginShell("model M\nclass A\nend\nclass B\nend\n");
		assertEquals(Arrays.asList("A=1 B=0", "A=1 B=1"), scroll("A_min = 1\nA_max = 1\nB_min = 0\nB_max = 1\n"));
	}

	/**
	 * Runs mv -scrolling, then next until it reports that no state is left.
	 * Returns the number of objects of each class in each state shown, sorted.
	 */
	private List<String> scroll(String bounds) throws Exception {
		String properties = file("bounds.properties", "[bounds]\nInteger_min = 0\nInteger_max = 1\n" + bounds);
		String output = shell.run(new KodkodScrollingValidateCmd(), properties);
		List<String> states = new ArrayList<String>();
		for (int next = 0; next < 10 && !output.contains("UNSATISFIABLE"); next++) {
			states.add(objectsPerClass());
			output = shell.run(new KodkodScrollingValidateCmd(), "next");
		}
		assertTrue(states + "\n" + output, output.contains("UNSATISFIABLE"));
		Collections.sort(states);
		return states;
	}

	private String objectsPerClass() {
		MSystem system = shell.system();
		List<String> counts = new ArrayList<String>();
		for (MClass cls : system.model().classes()) {
			counts.add(cls.name() + "=" + system.state().objectsOfClass(cls).size());
		}
		Collections.sort(counts);
		return String.join(" ", counts);
	}

	private String file(String name, String content) throws Exception {
		File file = folder.newFile(name);
		Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
		return file.getPath();
	}
}
