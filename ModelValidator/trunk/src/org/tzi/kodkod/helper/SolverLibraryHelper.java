package org.tzi.kodkod.helper;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.tzi.kodkod.KodkodModelValidatorConfiguration;

public final class SolverLibraryHelper {

	private SolverLibraryHelper() {
	}
	
	public static String[] tryAvailableSolvers(){
		Set<String> res = new LinkedHashSet<String>();
		String currentSolver = KodkodModelValidatorConfiguration.getInstance().satFactory().toString();
		
		Set<String> solverList = new LinkedHashSet<String>(KodkodModelValidatorConfiguration.SOLVER_MAP.values());
		solverList.add(KodkodModelValidatorConfiguration.PLINGELING_NAME);
		
		for(String solver : solverList){
			try {
				KodkodModelValidatorConfiguration.getInstance().setSatFactory(solver);
				// if no error occurred, add solver to the list
				res.add(solver);
			} catch (IOException e) {
				// if an error occurs, solver is not available
			}
		}
		
		// reset to original solver
		try {
			KodkodModelValidatorConfiguration.getInstance().setSatFactory(currentSolver);
		} catch (IOException e) {
			// reseting failed, oopsie
		}
		
		return res.toArray(new String[res.size()]);
	}
	
}
