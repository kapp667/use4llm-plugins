package org.tzi.use.kodkod.transform.ocl;

import org.tzi.kodkod.KodkodModelValidator;

/**
 * A validator that records the outcome of the last validation, null if there
 * was none.
 */
class OutcomeRecorder extends KodkodModelValidator {

	String outcome;

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
