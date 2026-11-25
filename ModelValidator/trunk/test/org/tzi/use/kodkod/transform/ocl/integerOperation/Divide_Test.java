package org.tzi.use.kodkod.transform.ocl.integerOperation;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.kodkod.transform.ocl.OCLTest;

/**
 * In OCL, Integer / Integer is a Real (1 / 2 = 0.5). The translation has no
 * Real arithmetic, so it refuses `/` instead of computing an integer division;
 * `div` is the integer division (Div_Test).
 */
public class Divide_Test extends OCLTest {
	@Test(expected = TransformationException.class)
	public void test1() {
		toKodkod(toOCLExpression("1 / 2"));
	}

}
