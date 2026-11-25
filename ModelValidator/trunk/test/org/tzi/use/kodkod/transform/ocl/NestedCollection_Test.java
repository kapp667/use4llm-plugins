package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;

/**
 * Checks that an expression whose value is a collection of collections is
 * refused, except as the source of flatten().
 */
public class NestedCollection_Test extends OCLTest {

	@Test
	public void sizeOfACollectionOfCollectionsIsRefused() {
		assertRefused("Set{Set{1,2}}->size()", "Set {Set {1,2}}");
	}

	@Test
	public void typeTestOfACollectionOfCollectionsIsRefused() {
		assertRefused("Set{Set{1,2}}->oclIsKindOf(Set(Integer))", "Set {Set {1,2}}");
	}

	@Test
	public void typeTestAgainstACollectionOfCollectionsIsRefused() {
		assertRefused("Set{1,2}->oclIsKindOf(Set(Set(Integer)))", "Set(Set(Integer))");
	}

	@Test
	public void collectNestedOfCollectionsIsRefused() {
		assertRefused("Set{1,2}->collectNested(i | Set{i})->size()", "Set {1,2}->collectNested(i : Integer | Set {i})");
	}

	@Test
	public void emptyCollectionOfCollectionsIsRefused() {
		assertRefused("oclEmpty(Set(Set(Integer)))->isEmpty()", "oclEmpty(Set(Set(Integer)))");
	}

	@Test
	public void includingACollectionIsRefused() {
		assertRefused("oclEmpty(Set(Set(Integer)))->including(Set{1})->size()", "oclEmpty(Set(Set(Integer)))->including(Set {1})");
	}

	@Test
	public void selectionOfCollectionsIsRefused() {
		assertRefused("Set{1,2}->selectByKind(Set(Integer))", "Set {1,2}->selectByKind(Set(Integer))");
	}

	@Test
	public void letOfACollectionOfCollectionsIsRefused() {
		assertRefused("(let s = Set{Set{1}} in s)->size()", "let s : Set(Set(Integer)) = Set {Set {1}} in s");
	}

	@Test
	public void flattenToACollectionOfCollectionsIsRefused() {
		assertRefused("Set{Set{Set{1}}}->flatten()", "Set {Set {Set {1}}}->flatten");
	}

	@Test
	public void flattenOfALiteralOfCollections() {
		test("flattenOfALiteralOfCollections", "Set{Set{1,2},Set{3}}->flatten()");
	}

	@Test
	public void flattenOfALiteralOfCollectionsCanBeUsed() {
		test("flattenOfALiteralOfCollectionsCanBeUsed", "Set{Set{1,2},Set{2,3}}->flatten()->size()");
	}

	@Test
	public void flattenOfCollectNested() {
		test("flattenOfCollectNested", "Set{1,2}->collectNested(i | Set{i, 3})->flatten()");
	}

	private void assertRefused(String ocl, String refusedPart) {
		try {
			toKodkod(toOCLExpression(ocl));
			fail("The expression was translated: " + ocl);
		} catch (TransformationException e) {
			assertEquals("Cannot transform `" + refusedPart + "': collections of collections are not supported.", e.getMessage());
		}
	}
}
