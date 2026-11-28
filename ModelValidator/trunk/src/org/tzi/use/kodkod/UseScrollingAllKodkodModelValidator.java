package org.tzi.use.kodkod;

import org.tzi.use.main.Session;

public class UseScrollingAllKodkodModelValidator extends UseScrollingKodkodModelValidator {

	public UseScrollingAllKodkodModelValidator(Session session) {
		super(session);
	}

	@Override
	protected void handleSolution() {
		if (showDiagramAndCacheEvaluator() == Diagram.ACCEPTED) {
			solutions.add(solution.instance().relationTuples());
			refusedInSearch = 0;
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
