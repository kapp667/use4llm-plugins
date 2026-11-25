package org.tzi.use.kodkod.transform.ocl.booleanOperation;

import org.junit.Test;
import org.tzi.use.kodkod.transform.ocl.OCLTest;

/**
 * Boolean operations on undefined operands, alone and under a negation: an
 * undefined result must stay undefined and never turn into true or false.
 * In the test object diagram, every attribute of ada is undefined.
 */
public class UndefinedOperand_Test extends OCLTest {
	@Test
	public void test1() {
		test("test1", "not (oclUndefined(Integer) > 3)");
	}

	@Test
	public void test2() {
		test("test2", "not (ada.age > 150)");
	}

	@Test
	public void test3() {
		test("test3", "not (ada.age <= 150)");
	}

	@Test
	public void test4() {
		test("test4", "not ada.alive");
	}

	@Test
	public void test5() {
		test("test5", "not (not ada.alive)");
	}

	@Test
	public void test6() {
		test("test6", "not (ada.alive and true)");
	}

	@Test
	public void test7() {
		test("test7", "not (true and ada.alive)");
	}

	@Test
	public void test8() {
		test("test8", "false and ada.alive");
	}

	@Test
	public void test9() {
		test("test9", "not (ada.alive or false)");
	}

	@Test
	public void test10() {
		test("test10", "true or ada.alive");
	}

	@Test
	public void test11() {
		test("test11", "not (ada.alive implies false)");
	}

	@Test
	public void test12() {
		test("test12", "false implies ada.alive");
	}

	@Test
	public void test13() {
		test("test13", "not (ada.alive xor true)");
	}

	@Test
	public void test14() {
		test("test14", "not Set{1, 2}->forAll(i | i > ada.age)");
	}

	@Test
	public void test15() {
		test("test15", "not Set{1, 2}->exists(i | i < ada.age)");
	}

	@Test
	public void test16() {
		test("test16", "not ada.luckyNumbers->includes(1)");
	}

	@Test
	public void test17() {
		test("test17", "not ada.luckyNumbers->isEmpty()");
	}

	@Test
	public void test18() {
		test("test18", "not Set{1, 2}->one(i | i > ada.age)");
	}

	@Test
	public void test19() {
		test("test19", "not Set{1, 2}->isUnique(i | ada.age)");
	}

	@Test
	public void test20() {
		test("test20", "not (if ada.alive then true else false endif)");
	}

}
