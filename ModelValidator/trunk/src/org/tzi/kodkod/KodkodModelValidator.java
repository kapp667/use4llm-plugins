package org.tzi.kodkod;

import kodkod.engine.Evaluator;
import kodkod.engine.Solution;
import kodkod.engine.Statistics;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IModel;

/**
 * Abstract base class for all validation functionalities.
 * 
 * @author Hendrik Reitmann
 */
public abstract class KodkodModelValidator {

	private static final Logger LOG = Logger.getLogger(KodkodModelValidator.class);

	protected IModel model;
	protected Solution solution;
	protected Evaluator evaluator;

	/**
	 * Validates the given model. If an element of the model could not be
	 * transformed, it logs an error naming every such element and returns
	 * without solving: a solution would ignore those elements.
	 * 
	 * @param model
	 */
	public void validate(IModel model) {
		this.model = model;
		evaluator = null;

		if (refusesUntransformedElements(model)) {
			return;
		}
		
		KodkodSolver kodkodSolver = new KodkodSolver();
		try {
			solution = kodkodSolver.solve(model);
		} catch (Exception e) {
			LOG.error(LogMessages.validationException + " (" + e.getMessage() + ")");
			return;
		} catch (OutOfMemoryError oome) {
			LOG.error(LogMessages.validationOutOfMemory + " (" + oome.getMessage() + ")");
			return;
		}

		LOG.info(solution.outcome());

		Statistics statistics = solution.stats();
		LOG.info(LogMessages.kodkodStatistics(statistics));

		switch (solution.outcome()) {
		case SATISFIABLE:
			storeEvaluator(kodkodSolver);
			satisfiable();
			break;
		case TRIVIALLY_SATISFIABLE:
			storeEvaluator(kodkodSolver);
			trivially_satisfiable();
			break;
		case TRIVIALLY_UNSATISFIABLE:
			trivially_unsatisfiable();
			break;
		case UNSATISFIABLE:
			unsatisfiable();
			break;
		default:
			throw new IllegalStateException("Kodkod returned unknown solution outcome.");
		}
	}

	/**
	 * Logs an error naming every element of the model that could not be
	 * transformed, if there is any.
	 * 
	 * @param model
	 * @return true if the model has such elements and must not be validated.
	 */
	protected boolean refusesUntransformedElements(IModel model) {
		if (model.transformationErrors().isEmpty()) {
			return false;
		}
		LOG.error(LogMessages.untransformedElementsError(model.transformationErrors()));
		return true;
	}

	private void storeEvaluator(KodkodSolver kodkodSolver) {
		evaluator = kodkodSolver.evaluator();
	}

	protected abstract void satisfiable();

	protected abstract void trivially_satisfiable();

	protected abstract void trivially_unsatisfiable();

	protected abstract void unsatisfiable();
}
