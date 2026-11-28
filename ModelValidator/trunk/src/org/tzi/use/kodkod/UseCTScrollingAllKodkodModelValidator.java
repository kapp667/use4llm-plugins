package org.tzi.use.kodkod;

import org.tzi.use.main.Session;

/**
 * Class for the model validation with scrolling functionality using classifier terms.
 * 
 * @author Frank Hilken
 */
public class UseCTScrollingAllKodkodModelValidator extends UseCTScrollingKodkodModelValidator {

	public UseCTScrollingAllKodkodModelValidator(Session session) {
		super(session);
	}

	@Override
	protected void handleSolution() {
		if (showDiagramAndCacheEvaluator() == Diagram.ACCEPTED) {
			readSolutionTerms(session.system().state());
			solutions.add(solution.instance().relationTuples());
			refusedInSearch = 0;
			LOG.info("Found solution no. " + solutions.size());
			newSolution(solution.instance().relationTuples());
		} else {
			refuseSolution();
		}
	}
	
	@Override
	protected void trivially_unsatisfiable() {
		super.trivially_unsatisfiable();
		finished();
	}

	@Override
	protected void unsatisfiable() {
		super.unsatisfiable();
		finished();
	}
	
	private void finished(){
		solutionIndex = solutions.size()-1;
		LOG.info("Found "+solutions.size()+" solutions");
	}
}
