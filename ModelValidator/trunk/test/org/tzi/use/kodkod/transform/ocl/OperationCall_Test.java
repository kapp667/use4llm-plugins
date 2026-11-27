package org.tzi.use.kodkod.transform.ocl;

import org.junit.Test;

public class OperationCall_Test extends OCLTest {

	@Test
	public void coworkersOfADefinedPerson() {
		test("coworkersOfADefinedPerson", "ada.coworkers()");
	}

	@Test
	public void coworkersOfAnUndefinedPersonAreUndefined() {
		test("coworkersOfAnUndefinedPersonAreUndefined", "uf.person.coworkers()");
	}

	@Test
	public void coworkersOfAbsentBestFriendsHaveNoSize() {
		test("coworkersOfAbsentBestFriendsHaveNoSize", "Person.allInstances()->select(p | p.bestFriend.coworkers()->size() = 1)->size()");
	}

	@Test
	public void employerOfAnUndefinedPersonIsUndefined() {
		test("employerOfAnUndefinedPersonIsUndefined", "uf.person.currentEmployer()");
	}

	@Test
	public void setOfAnIfWithAnUndefinedConditionHasNoSize() {
		test("setOfAnIfWithAnUndefinedConditionHasNoSize", "ada.numbersIfAlive()->size()");
	}

	@Test
	public void setOfAnIfWithAnUndefinedConditionIsNotKnownToBeEmpty() {
		test("setOfAnIfWithAnUndefinedConditionIsNotKnownToBeEmpty", "ada.numbersIfAlive()->isEmpty()");
	}

	@Test
	public void setArgumentOfNullHasNoSize() {
		test("setArgumentOfNullHasNoSize", "ada.sizeOf(null)");
	}

	@Test
	public void setArgumentOfADefinedSetHasItsSize() {
		test("setArgumentOfADefinedSetHasItsSize", "ada.sizeOf(bob.luckyNumbers)");
	}

	@Test
	public void setResultOfNullHasNoSize() {
		test("setResultOfNullHasNoSize", "ada.noNumbers()->size()");
	}

	@Test
	public void setResultOfNullEqualsAnUndefinedSet() {
		test("setResultOfNullEqualsAnUndefinedSet", "ada.noNumbers() = ada.luckyNumbers");
	}
}
