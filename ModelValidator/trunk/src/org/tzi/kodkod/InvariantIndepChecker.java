package org.tzi.kodkod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kodkod.ast.Relation;
import kodkod.instance.TupleSet;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.api.UseApiException;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator;
import org.tzi.use.main.Session;

/**
 * Checks the invariant independence.
 *
 * @author Hendrik Reitmann
 *
 */
public class InvariantIndepChecker extends KodkodModelValidator {

	private static final Logger LOG = Logger.getLogger(InvariantIndepChecker.class);

	private Session session;

	private List<IInvariant> inactiveInvariants;
	private List<IInvariant> negatedInvariants;
	private IInvariant currentInvariant;

	public InvariantIndepChecker(Session session) {
		this.session = session;
	}

	/**
	 * Validation to check the independence of a single invariant.
	 */
	public void validate(IModel model, String className, String invariantName) {
		if (refusesUntransformedElements(model)) {
			return;
		}

		IClass clazz = model.getClass(className);
		if (clazz == null) {
			LOG.error(LogMessages.noClassError(className));
			return;
		}
		IInvariant invariant = clazz.getInvariant(invariantName);
		if (invariant == null) {
			LOG.error(LogMessages.noClassInvariantError(className, invariantName));
			return;
		}

		Collection<IInvariant> invariants = model.classInvariants();
		saveInvariantStates(invariants);
		Map<Logger, Level> logLevels = saveLogLevels();
		try {
			changeLogLevels(null);
			activateAllInvariants(invariants);

			currentInvariant = invariant;
			checkInvariant(model);
		} finally {
			resetInvariantStates(invariants);
			resetLogLevels(logLevels);
		}
	}

	@Override
	public void validate(IModel model) {
		if (refusesUntransformedElements(model)) {
			return;
		}

		Collection<IInvariant> invariants = model.classInvariants();
		saveInvariantStates(invariants);
		Map<Logger, Level> logLevels = saveLogLevels();
		try {
			changeLogLevels(Level.WARN);
			activateAllInvariants(invariants);

			for (IInvariant invariant : invariants) {
				currentInvariant = invariant;
				checkInvariant(model);
			}
		} finally {
			resetInvariantStates(invariants);
			resetLogLevels(logLevels);
		}
	}

	private void checkInvariant(IModel model) {
		currentInvariant.negate();
		super.validate(model);
		currentInvariant.reset();
	}

	private void activateAllInvariants(Collection<IInvariant> invariants) {
		for (IInvariant inv : invariants) {
			inv.reset();
		}
	}

	/**
	 * Records which invariants are inactive and which are negated, so that
	 * resetInvariantStates can give them these states back.
	 */
	private void saveInvariantStates(Collection<IInvariant> invariants) {
		inactiveInvariants = new ArrayList<IInvariant>();
		negatedInvariants = new ArrayList<IInvariant>();

		for (IInvariant inv : invariants) {
			if (!inv.isActivated()) {
				inactiveInvariants.add(inv);
			}
			if (inv.isNegated()) {
				negatedInvariants.add(inv);
			}
		}
	}

	private void resetInvariantStates(Collection<IInvariant> invariants) {
		for (IInvariant inv : invariants) {
			inv.reset();
		}
		for (IInvariant inv : negatedInvariants) {
			inv.negate();
		}
		for (IInvariant inv : inactiveInvariants) {
			inv.deactivate();
		}
	}

	private Map<Logger, Level> saveLogLevels() {
		Map<Logger, Level> logLevels = new HashMap<Logger, Level>();
		for (Logger log : loggers()) {
			logLevels.put(log, log.getLevel());
		}
		return logLevels;
	}

	/**
	 * Sets the level of the solver's logger and of this checker's logger.
	 *
	 * @param validatorLevel the level of the model validator's logger, or null to keep it.
	 */
	private void changeLogLevels(Level validatorLevel) {
		if (validatorLevel != null) {
			Logger.getLogger(KodkodModelValidator.class).setLevel(validatorLevel);
			Logger.getLogger(KodkodSolver.class).setLevel(validatorLevel);
		}
		LOG.setLevel(Level.INFO);
	}

	private void resetLogLevels(Map<Logger, Level> logLevels) {
		for (Map.Entry<Logger, Level> entry : logLevels.entrySet()) {
			entry.getKey().setLevel(entry.getValue());
		}
	}

	private Logger[] loggers() {
		return new Logger[] { Logger.getLogger(KodkodModelValidator.class), Logger.getLogger(KodkodSolver.class), LOG };
	}

	/**
	 * Creates the object diagram of the solution in USE.
	 *
	 * @return true if USE accepts the object diagram, false if it refuses it.
	 */
	private boolean createObjectDiagram(Map<Relation, TupleSet> relationTuples){
		LOG.info(LogMessages.objDiagramCreation);

		session.reset();
		ObjectDiagramCreator diagramCreator = new ObjectDiagramCreator(model, session);
		try {
			diagramCreator.create(relationTuples);
			return diagramCreator.hasDiagramErrors() == ObjectDiagramCreator.ErrorType.NONE;
		} catch (UseApiException ex) {
			if (LOG.isDebugEnabled()) {
				LOG.error(LogMessages.objDiagramCreationError, ex);
			} else {
				LOG.error(LogMessages.objDiagramCreationError + " Reason: " + ex.getMessage());
			}
			return false;
		}
	}

	/**
	 * Reports "Independent" only if USE accepts the object diagram of the solution.
	 */
	private void reportIndependence() {
		if (createObjectDiagram(solution.instance().relationTuples())) {
			LOG.info(currentInvariant.qualifiedName() + ": Independent");
		} else {
			LOG.error(LogMessages.indepSolutionRefusedError(currentInvariant.qualifiedName()));
		}
	}
	
	@Override
	protected void satisfiable() {
		reportIndependence();
	}

	@Override
	protected void trivially_satisfiable() {
		reportIndependence();
	}

	@Override
	protected void trivially_unsatisfiable() {
		LOG.info(currentInvariant.qualifiedName() + ": Dependent");
	}

	@Override
	protected void unsatisfiable() {
		LOG.info(currentInvariant.qualifiedName() + ": Not independent for given properties");
	}
}
