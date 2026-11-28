package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A configuration that mv -vc calls valid must load and search without an error in
 * mv -validate. When the configuration cannot be loaded, mv -vc says so: it calls the
 * configuration invalid and prints the error text of the loader under it.
 */
public class ValidConfigurationLoads_Test extends MvCommandSession {

	private static final String STR = "model Str\nclass C\nattributes\n  s : String\nend\n";

	private static final String STR_CONFIGS = "[s1]\nString_min = 1\nString_max = 3\nString = Set{'x'}\n\n"
			+ "[s2]\nString_min = 1\nString_max = 3\nString = Set{'x'}\n"
			+ "C_min = 1\nC_max = 3\nC_s_min = 3\nC_s_max = 3\n\n"
			+ "[s3]\nString_min = 3\nString_max = 3\nString = Set{'x'}\nC_min = 3\nC_max = 3\n\n"
			+ "[sok]\nString_max = 3\nC_min = 1\nC_max = 1\n";

	private static final String ORG = "model Org\nclass Dept\nend\nclass Employee\nend\n"
			+ "association Works between\n  Employee [0..*] role staff\n  Dept [1] role dept\nend\n";

	private static final String ORG_CONFIGS = "[o1]\nDept = Set{d1}\nEmployee = Set{e1}\nWorks = Set{(e1,d1)}\n\n"
			+ "[o2]\nDept_min = 2\nDept_max = 2\nDept = Set{d1}\n"
			+ "Employee_min = 1\nEmployee_max = 1\nEmployee = Set{e1}\nWorks = Set{(e1,d1)}\n";

	private static final String NUM = "model Num\nclass C\nattributes\n  x : Integer\nend\n";

	private static final String NUM_HEAD = "Integer_min = 0\nInteger_max = 3\nC_min = 1\nC_max = 1\n";

	@Test
	public void stringConfigurationsAreValidAndSolveWithoutError() throws Exception {
		open(STR);
		String path = file("str.properties", STR_CONFIGS);
		String vc = configCheck(path);
		for (String name : new String[] { "s1", "s2", "s3", "sok" }) {
			assertTrue(vc, vc.contains("Config " + name + " is valid."));
		}
		for (String name : new String[] { "s1", "s2", "s3" }) {
			log.getBuffer().setLength(0);
			String output = validate(path, name);
			assertFalse(name + ": " + output, output.contains("ERROR"));
			assertTrue(name + ": " + output, output.contains("INFO SATISFIABLE"));
			assertFalse(name + ": " + output, output.contains("UNSATISFIABLE"));
			assertTrue(name, useCheckPasses());
		}
	}

	@Test
	public void organisationConfigurationsAreValidAndSolveWithoutError() throws Exception {
		open(ORG);
		String path = file("org.properties", ORG_CONFIGS);
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config o1 is valid."));
		assertTrue(vc, vc.contains("Config o2 is valid."));

		log.getBuffer().setLength(0);
		String o1 = validate(path, "o1");
		assertFalse(o1, o1.contains("ERROR"));
		assertTrue(o1, o1.contains("INFO TRIVIALLY_SATISFIABLE"));
		assertEquals(2, session.system().state().allObjects().size());
		assertEquals(1, session.system().state().allLinks().size());
		assertTrue(useCheckPasses());

		open(ORG);
		log.getBuffer().setLength(0);
		String o2 = validate(path, "o2");
		assertFalse(o2, o2.contains("ERROR"));
		assertTrue(o2, o2.contains("INFO SATISFIABLE"));
		assertEquals(3, session.system().state().allObjects().size());
		assertTrue(useCheckPasses());
	}

	@Test
	public void configCheckReportsAConfigurationTheLoaderRejects() throws Exception {
		open(NUM);
		String path = file("num.properties", "[num]\n" + NUM_HEAD + "C_x = 5\n");
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config num is invalid."));
		assertFalse(vc, vc.contains("Config num is valid."));
		assertTrue(vc, vc.contains("C_x: Not the correct syntax. Use the syntax Set{a,b,c}."));
	}

	@Test
	public void configCheckKeepsAValidConfigurationValidWhenTheLoaderAcceptsIt() throws Exception {
		open(NUM);
		String path = file("num.properties", "[num]\n" + NUM_HEAD + "C_x = Set{1,2}\n");
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config num is valid."));
		log.getBuffer().setLength(0);
		String output = validate(path, "num");
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
	}

	@Test
	public void aLinkNamingAnUnknownObjectIsInvalidAndIsNotSearched() throws Exception {
		open(ORG);
		String path = file("e9.properties", "[e9]\nDept = Set{d1}\nEmployee = Set{e1}\nWorks = Set{(e9,d1)}\n");
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config e9 is invalid."));
		assertFalse(vc, vc.contains("Config e9 is valid."));
		log.getBuffer().setLength(0);
		String output = validate(path, "e9");
		assertTrue(output, output.contains("ERROR"));
		assertFalse(output, output.contains("SATISFIABLE"));
	}
}
