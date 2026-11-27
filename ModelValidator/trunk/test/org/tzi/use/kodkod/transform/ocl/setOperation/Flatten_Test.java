package org.tzi.use.kodkod.transform.ocl.setOperation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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
	public void nullElementOfABagIsKeptAsAnUndefinedElement() {
		test("nullElementOfABagIsKeptAsAnUndefinedElement", "Bag{Set{1}, null}->flatten()");
	}

	@Test
	public void undefinedResultOfCollectNestedOnASetIsKeptAsAnUndefinedElement() {
		test("undefinedResultOfCollectNestedOnASetIsKeptAsAnUndefinedElement",
				"Set{1,2}->collectNested(i | if i = 1 then oclUndefined(Set(Integer)) else Set{i} endif)->flatten()");
	}

	@Test
	public void undefinedResultOfCollectNestedOnASequenceIsSkipped() {
		test("undefinedResultOfCollectNestedOnASequenceIsSkipped",
				"Sequence{1,2}->collectNested(i | if i = 1 then oclUndefined(Set(Integer)) else Set{i} endif)->flatten()");
	}

	@Test
	public void collectNestedOnACollectionIsRefused() {
		assertRefused("(if true then Sequence{1,2} else Set{1,2} endif)->collectNested(i | if i = 1 then oclUndefined(Set(Integer)) else Set{i} endif)->flatten()",
				"Cannot transform `if true then Sequence {1,2} else Set {1,2} endif->collectNested(i : Integer | if (i = 1) then null else Set {i} endif)':"
						+ " its source has the type Collection, and USE's flatten() keeps an undefined collection of its result"
						+ " for a Set or a Bag source but skips it for a Sequence or an OrderedSet source.");
	}

	@Test
	public void orderedSetWithAnUndefinedElementIsRefused() {
		assertRefused("OrderedSet{OrderedSet{1}, null}->flatten()",
				"Cannot transform `OrderedSet {OrderedSet {1},null}': USE fails to flatten an ordered set when one of its element collections is undefined,"
						+ " and an element of this ordered set may be undefined.");
	}

	@Test
	public void orderedSetOfLiteralsIsFlattened() {
		test("orderedSetOfLiteralsIsFlattened", "OrderedSet{OrderedSet{1}, OrderedSet{2}}->flatten()");
	}

	private void assertRefused(String ocl, String message) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertEquals(message, e.getMessage());
		}
	}

}
