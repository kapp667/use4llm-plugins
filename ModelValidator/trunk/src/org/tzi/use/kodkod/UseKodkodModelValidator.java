package org.tzi.use.kodkod;

import java.util.Map;

import kodkod.ast.Relation;
import kodkod.engine.Solution;
import kodkod.instance.TupleSet;

import org.apache.log4j.Logger;
import org.tzi.kodkod.KodkodModelValidator;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.KodkodQueryCache;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.helper.ProofHelper;
import org.tzi.kodkod.model.config.impl.ModelConfigurator;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.api.UseApiException;
import org.tzi.use.kodkod.solution.NonTranslatedValues;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator.ErrorType;
import org.tzi.use.main.Session;

/**
 * Class for a simple model validation with subsequent object diagram
 * creation.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class UseKodkodModelValidator extends KodkodModelValidator {

	protected static final Logger LOG = Logger.getLogger(UseKodkodModelValidator.class);

	protected Session session;

	/** The most solutions USE may refuse in one search. Reaching it stops the search with an error. */
	public static final int MAX_REFUSED_SOLUTIONS = 10;

	/** The solutions USE refused in the run of this validator. */
	protected int refusedSolutions = 0;

	/** The solutions USE refused since the search started or since its last accepted solution. */
	protected int refusedInSearch = 0;

	/** Set when a solution is forbidden, so that the search runs again. */
	private boolean searchAgain = false;

	public UseKodkodModelValidator(Session session) {
		this.session = session;
	}

	/**
	 * Validates the model. A refused solution asks for another search, which runs in this loop and not
	 * inside the search that found the refused solution, so that the retries do not nest.
	 */
	@Override
	public void validate(IModel model) {
		refusedInSearch = 0;
		do {
			searchAgain = false;
			super.validate(model);
		} while (searchAgain);
	}

	@Override
	protected void satisfiable() {
		handleSolution();
	}

	@Override
	protected void trivially_satisfiable() {
		handleSolution();
	}

	/**
	 * Handles a satisfiable solution.
	 */
	protected void handleSolution() {
		if (showDiagramAndCacheEvaluator() == Diagram.ACCEPTED) {
			evaluateInactiveInvariants();
		} else {
			refuseSolution();
		}
	}

	/**
	 * Counts the solution just found as refused by USE, and forbids it so that the search looks for
	 * another one. The refusal that reaches MAX_REFUSED_SOLUTIONS in one search stops the search with an
	 * error, and gives no verdict.
	 */
	protected void refuseSolution() {
		refusedSolutions++;
		refusedInSearch++;
		session.reset();
		if (refusedInSearch >= MAX_REFUSED_SOLUTIONS) {
			LOG.error(LogMessages.refusedLimitError(MAX_REFUSED_SOLUTIONS));
			return;
		}
		LOG.info("USE found errors in the solution. Trying to find a new solution!");
		newSolution(solution.instance().relationTuples());
	}

	@Override
	protected void trivially_unsatisfiable() {
		handleFailure();
	}

	@Override
	protected void unsatisfiable() {
		handleFailure();
	}

	private void handleFailure() {
		// a proof concerns the translation; after a refused solution it does not hold for USE
		if(solution.proof() != null && refusedSolutions == 0){
			LOG.info(ProofHelper.buildProofString(solution.proof(), true));
		}
	}

	/**
	 * Logs an error instead of an UNSATISFIABLE verdict when USE refused a
	 * solution of the translation: the search then proves nothing about USE.
	 */
	@Override
	protected void reportOutcome(Solution.Outcome outcome) {
		boolean unsatisfiable = outcome == Solution.Outcome.UNSATISFIABLE
				|| outcome == Solution.Outcome.TRIVIALLY_UNSATISFIABLE;
		if (unsatisfiable && refusedSolutions > 0) {
			LOG.error(LogMessages.notProvedError(outcome.toString(), refusedSolutions));
		} else {
			super.reportOutcome(outcome);
		}
	}
	
	/**
	 * Creates the object diagram of the solution just found, as showDiagram
	 * does. Once USE accepts the diagram, queries compare their value in it
	 * with the solution.
	 */
	protected Diagram showDiagramAndCacheEvaluator() {
		Diagram diagram = showInUse(solution.instance().relationTuples());
		if (diagram == Diagram.ACCEPTED) {
			KodkodQueryCache.INSTANCE.cache(evaluator, session.system().state());
		}
		return diagram;
	}

	/**
	 * Creates the object diagram of a solution in USE. Returns true when USE
	 * finds an error in it, a structure error or an invariant that USE finds
	 * false, so that the caller looks for another solution.
	 */
	protected boolean showDiagram(Map<Relation, TupleSet> relationTuples) {
		return showInUse(relationTuples) != Diagram.ACCEPTED;
	}

	public enum Diagram {
		NOT_CREATED, REFUSED, ACCEPTED
	}

	private Diagram showInUse(Map<Relation, TupleSet> relationTuples) {
		LOG.info(LogMessages.objDiagramCreation);

		// the values of the attributes the translation leaves out are not in the solution: keep them across the reset
		NonTranslatedValues kept = null;
		if (KodkodModelValidatorConfiguration.getInstance().isAutomaticDiagramExtraction()) {
			try {
				kept = NonTranslatedValues.collect(model, session.system().state());
			} catch (IllegalStateException ex) {
				LOG.error(ex.getMessage());
				return Diagram.NOT_CREATED;
			}
		}

		KodkodQueryCache.INSTANCE.clear();
		session.reset();
		ObjectDiagramCreator diagramCreator = new ObjectDiagramCreator(model, session);
		try {
			diagramCreator.create(relationTuples);
			if (kept != null && !diagramCreator.restoreNonTranslatedValues(kept)) {
				return Diagram.NOT_CREATED;
			}
		} catch (UseApiException ex) {
			if (LOG.isDebugEnabled()) {
				LOG.error(LogMessages.objDiagramCreationError, ex);
			} else {
				LOG.error(LogMessages.objDiagramCreationError + " Reason: " + ex.getMessage());
			}
			return Diagram.NOT_CREATED;
		}
		return diagramCreator.hasDiagramErrors() == ErrorType.NONE ? Diagram.ACCEPTED : Diagram.REFUSED;
	}

	/**
	 * Forbids the given solution, so that the next search does not find it again. The search
	 * itself is run by validate.
	 */
	protected void newSolution(Map<Relation, TupleSet> relationTuples) {
		ModelConfigurator modelConfigurator = (ModelConfigurator) model.getConfigurator();
		modelConfigurator.forbid(relationTuples);
		searchAgain = true;
	}

	/**
	 * Evaluation of the inactive invariants.
	 * 
	 * Probably for the generation of default properties file. If a normal run
	 * fails with that, all invariants are deactivated and another run is tried.
	 * If it succeeds, all invariants that are valid in the found solution are
	 * enabled in the properties file.
	 */
	private void evaluateInactiveInvariants() {
		boolean info = false;
		for (IClass clazz : model.classes()) {
			for (IInvariant invariant : clazz.invariants()) {
				if (!invariant.isActivated()) {
					if (!info) {
						LOG.debug(LogMessages.inactiveInvariantEval);
						info = true;
					}

					LOG.debug("Invariant " + invariant.qualifiedName() + ": " + evaluator.evaluate(invariant.formula()));
				}
			}
		}
	}
}
