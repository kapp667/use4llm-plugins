package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;

/**
 * A set attribute whose value is undefined has no size: the bounds minSize and maxSize
 * of a collection attribute apply to defined values only, as in USE, where a state with
 * an undefined set passes check.
 */
public class UndefinedSetSize_Test extends MvCommandSession {

	private static final String UNDEFINED = "model USet\n"
			+ "class C\nattributes\n  s : Set(Integer)\nend\n"
			+ "constraints\ncontext C inv undef: self.s->isUndefined()\n";

	private static final String DEFINED = "model USet\n"
			+ "class C\nattributes\n  s : Set(Integer)\nend\n"
			+ "constraints\ncontext C inv def: self.s->notEmpty()\n";

	private static final String U1 = "[u1]\nInteger_min = 0\nInteger_max = 3\n"
			+ "C_min = 1\nC_max = 1\nC_s_min = 0\nC_s_max = 1\nC_s_minSize = 2\nC_s_maxSize = 3\n";

	private Value s(MObject object) {
		return object.state(session.system().state()).attributeValue("s");
	}

	@Test
	public void anUndefinedSetNeedsNoMinimumSize() throws Exception {
		open(UNDEFINED);
		String output = validate(file("u.properties", U1), "u1");
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertEquals(1, session.system().state().allObjects().size());
		for (MObject object : session.system().state().allObjects()) {
			assertTrue(object.name(), s(object).isUndefined());
		}
		assertTrue(useCheckPasses());
	}

	@Test
	public void definedSetsStayWithinTheirSizeBounds() throws Exception {
		open(DEFINED);
		String output = validate(file("u.properties", U1), "u1");
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertEquals(1, session.system().state().allObjects().size());
		for (MObject object : session.system().state().allObjects()) {
			int size = ((CollectionValue) s(object)).size();
			assertTrue(object.name() + " has " + size + " elements", size >= 2 && size <= 3);
		}
		assertTrue(useCheckPasses());
	}
}
