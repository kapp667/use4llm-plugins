package org.tzi.use.kodkod.transform.ocl;

import java.util.Map;

import kodkod.ast.Relation;
import kodkod.instance.TupleSet;

import org.tzi.kodkod.KodkodModelValidator;

/**
 * A validator that records the outcome of the last validation, null if there
 * was none, and gives the tuples of its solution.
 */
class OutcomeRecorder extends KodkodModelValidator {

	String outcome;

	/**
	 * Returns the tuples of the relation in the solution of the last
	 * validation, which must have found one.
	 */
	TupleSet tuples(Relation relation) {
		return solution.instance().tuples(relation);
	}

	/**
	 * Returns the tuples of every relation in the solution of the last
	 * validation. The validation must have found a solution.
	 */
	Map<Relation, TupleSet> relationTuples() {
		return solution.instance().relationTuples();
	}

	@Override
	protected void satisfiable() {
		outcome = "SATISFIABLE";
	}

	@Override
	protected void trivially_satisfiable() {
		outcome = "TRIVIALLY_SATISFIABLE";
	}

	@Override
	protected void trivially_unsatisfiable() {
		outcome = "TRIVIALLY_UNSATISFIABLE";
	}

	@Override
	protected void unsatisfiable() {
		outcome = "UNSATISFIABLE";
	}
}
