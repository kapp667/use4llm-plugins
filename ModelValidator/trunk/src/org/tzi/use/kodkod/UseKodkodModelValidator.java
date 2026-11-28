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

	/** The solutions USE refused in the run of this validator. */
	protected int refusedSolutions = 0;

	public UseKodkodModelValidator(Session session) {
		this.session = session;
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
		if(showDiagramAndCacheEvaluator() == Diagram.REFUSED){
			refusedSolutions++;
			LOG.info("USE found errors in the solution. Trying to find a new solution!");
			
			session.reset();
			newSolution(solution.instance().relationTuples());
		} else {
			evaluateInactiveInvariants();
		}
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
		return showInUse(relationTuples) == Diagram.REFUSED;
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
	 * Set up the model to forbid previous solutions and start a model
	 * validation to find a new solution.
	 */
	protected void newSolution(Map<Relation, TupleSet> relationTuples) {
		ModelConfigurator modelConfigurator = (ModelConfigurator) model.getConfigurator();
		modelConfigurator.forbid(relationTuples);
		validate(model);
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
