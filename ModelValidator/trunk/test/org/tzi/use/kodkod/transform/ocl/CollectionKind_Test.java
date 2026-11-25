package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;

/**
 * Checks that an operation whose result depends on the duplicates or the order
 * of a Bag, Sequence or OrderedSet is refused, and that operations which depend
 * only on the elements present are still translated.
 */
public class CollectionKind_Test extends OCLTest {

	@Test
	public void sizeOfABagIsRefused() {
		assertRefused("Bag{1,1}->size()", "Bag {1,1}->size", "duplicates");
	}

	@Test
	public void countOfASequenceIsRefused() {
		assertRefused("Sequence{1,1}->count(1)", "Sequence {1,1}->count(1)", "duplicates");
	}

	@Test
	public void sumOfACollectIsRefused() {
		assertRefused("Set{1,2}->collect(x | 0)->sum()", "Set {1,2}->collect(x : Integer | 0)->sum", "duplicates");
	}

	@Test
	public void sizeOfACollectionIsRefused() {
		assertRefused("(let c : Collection(Integer) = Bag{1,1} in c)->size()",
				"let c : Collection(Integer) = Bag {1,1} in c->size", "duplicates");
	}

	@Test
	public void isUniqueOnABagIsRefused() {
		assertRefused("Bag{1,1}->isUnique(x | x)", "Bag {1,1}->isUnique(x : Integer | x)", "duplicates");
	}

	@Test
	public void oneOnASequenceIsRefused() {
		assertRefused("Sequence{1,1}->one(x | x = 1)", "Sequence {1,1}->one(x : Integer | (x = 1))", "duplicates");
	}

	@Test
	public void anyOnASequenceIsRefused() {
		assertRefused("Sequence{3,1}->any(x | true)", "Sequence {3,1}->any(x : Integer | true)", "order");
	}

	@Test
	public void anyOnAnOrderedSetIsRefused() {
		assertRefused("OrderedSet{3,1}->any(x | true)", "OrderedSet {3,1}->any(x : Integer | true)", "order");
	}

	@Test
	public void equalityOfBagsIsRefused() {
		assertComparisonRefused("Bag{1,1} = Bag{1}", "(Bag {1,1} = Bag {1})");
	}

	@Test
	public void inequalityOfASetAndABagIsRefused() {
		assertComparisonRefused("Set{1} <> Bag{1}", "(Set {1} <> Bag {1})");
	}

	@Test
	public void equalityOfOrderedSetsIsRefused() {
		assertComparisonRefused("OrderedSet{1,2} = OrderedSet{2,1}", "(OrderedSet {1,2} = OrderedSet {2,1})");
	}

	@Test
	public void sizeOfAnOrderedSet() {
		test("sizeOfAnOrderedSet", "OrderedSet{2,1,2}->size()");
	}

	@Test
	public void sizeOfACollectAsSet() {
		test("sizeOfACollectAsSet", "Set{1,2}->collect(x | 0)->asSet()->size()");
	}

	@Test
	public void isUniqueOnAnOrderedSet() {
		test("isUniqueOnAnOrderedSet", "OrderedSet{2,1}->isUnique(x | x)");
	}

	@Test
	public void includesOnABag() {
		test("includesOnABag", "Bag{1,1,2}->includes(1)");
	}

	@Test
	public void equalityOfSets() {
		test("equalityOfSets", "Set{1,2} = Set{2,1}");
	}

	private void assertRefused(String ocl, String refusedPart, String lost) {
		assertMessage(ocl, "Cannot transform `" + refusedPart + "': its result depends on the " + lost
				+ " of a collection that is not a set, which the transformation does not keep.");
	}

	private void assertComparisonRefused(String ocl, String refusedPart) {
		assertMessage(ocl, "Cannot transform `" + refusedPart + "': only two sets can be compared.");
	}

	private void assertMessage(String ocl, String message) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertEquals(message, e.getMessage());
		}
	}
}
