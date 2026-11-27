package org.tzi.use.kodkod.transform.ocl.anyOperation;

import org.junit.Test;
import org.tzi.use.kodkod.transform.ocl.OCLTest;

public class OclAsType_Test extends OCLTest {

	@Test
	public void test1() {
		test("test1", "Set{true, 1}->select(x | x.oclIsTypeOf(Boolean))->forAll(x | x.oclAsType(Boolean) and true)");
	}

	@Test
	public void test2() {
		test("test2", "Set{true, 1}->select(x | x.oclIsTypeOf(Integer))->collect(x | x.oclAsType(Integer) + 2)");
	}

}
