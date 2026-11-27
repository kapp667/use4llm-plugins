package org.tzi.use.kodkod.transform.ocl.setOperation;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.kodkod.transform.ocl.OCLTest;

public class Flatten_Test extends OCLTest {
	@Test
	public void test1() {
		String ocl = "Set{}->flatten()";
		String expected = "none ";
		test("test1", ocl, expected);
	}

	@Test
	public void test2() {
		String ocl = "Set{1}->flatten()";
		String expected = "(Undefined_Set in (none + Int[1])) => Undefined_Set else (none + Int[1]) ";
		test("test2", ocl, expected);
	}

	@Test
	public void test3() {
		String ocl = "Set{1,2}->flatten()";
		String expected = "(Undefined_Set in (none + Int[1] + Int[2])) => Undefined_Set else (none + Int[1] + Int[2]) ";
		test("test3", ocl, expected);
	}

	@Test
	public void test4() {
		String ocl = "Set{1,2,3}->flatten()";
		String expected = "(Undefined_Set in (none + Int[1] + Int[2] + Int[3])) => Undefined_Set else (none + Int[1] + Int[2] + Int[3]) ";
		test("test4", ocl, expected);
	}

	@Test
	public void test5() {
		String ocl = "Set{Undefined,1}->flatten()";
		String expected = "(Undefined_Set in (none + Undefined + Int[1])) => Undefined_Set else (none + Undefined + Int[1]) ";
		test("test5", ocl, expected);
	}

	@Test
	public void test6() {
		String ocl = "Set{Undefined,1,2}->flatten()";
		String expected = "(Undefined_Set in (none + Undefined + Int[1] + Int[2])) => Undefined_Set else (none + Undefined + Int[1] + Int[2]) ";
		test("test6", ocl, expected);
	}

	@Test
	public void test7() {
		String ocl = "Set{Undefined}->flatten()";
		String expected = "(Undefined_Set in (none + Undefined)) => Undefined_Set else (none + Undefined) ";
		test("test7", ocl, expected);
	}

	@Test
	public void undefinedSetOfATypeFlattenedIsUndefined() {
		test("undefinedSetOfATypeFlattenedIsUndefined", "oclUndefined(Set(Integer))->flatten()");
	}

	@Test
	public void nullElementOfASetIsSkipped() {
		test("nullElementOfASetIsSkipped", "Set{Set{1}, null}->flatten()");
	}

	@Test
	public void undefinedElementOfASetIsSkipped() {
		test("undefinedElementOfASetIsSkipped", "Set{Set{1}, ada.luckyNumbers}->flatten()");
	}

	@Test
	public void undefinedElementIsSkippedForEachObject() {
		test("undefinedElementIsSkippedForEachObject", "Person.allInstances()->select(p | Set{p.luckyNumbers, Set{1}}->flatten()->includes(1))");
	}

	@Test
	public void setOfUndefinedCollectionsFlattensToTheEmptySet() {
		test("setOfUndefinedCollectionsFlattensToTheEmptySet", "Set{ada.luckyNumbers, oclUndefined(Set(Integer))}->flatten()");
	}

	@Test
	public void nullElementOfASequenceIsSkipped() {
		test("nullElementOfASequenceIsSkipped", "Sequence{Sequence{1}, null}->flatten()");
	}

	@Test
	public void undefinedElementOfABagIsKeptAsAnUndefinedElement() {
		test("undefinedElementOfABagIsKeptAsAnUndefinedElement", "Bag{Set{1}, ada.luckyNumbers}->flatten()");
	}

	@Test
	public void undefinedResultOfCollectNestedOnASequenceIsSkipped() {
		test("undefinedResultOfCollectNestedOnASequenceIsSkipped",
				"Sequence{1,2}->collectNested(i | if i = 1 then oclUndefined(Set(Integer)) else Set{i} endif)->flatten()");
	}

	@Test(expected = TransformationException.class)
	public void orderedSetWithAnUndefinedElementIsRefused() {
		toKodkod(toOCLExpression("OrderedSet{OrderedSet{1}, null}->flatten()"));
	}

	@Test
	public void orderedSetOfLiteralsIsFlattened() {
		test("orderedSetOfLiteralsIsFlattened", "OrderedSet{OrderedSet{1}, OrderedSet{2}}->flatten()");
	}

}
