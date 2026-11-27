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
}
