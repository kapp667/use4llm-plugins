package org.tzi.kodkod.helper;

import java.io.File;
import java.util.List;

import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.DefaultConfigurationValues;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.use.util.StringUtil;

import kodkod.engine.Statistics;
import kodkod.instance.Universe;
import static org.tzi.use.util.StringUtil.inQuotes;

/**
 * Contains the different log messages.
 * 
 * @author Hendrik Reitmann
 * 
 */
public final class LogMessages {

	public static final String modelConfigurationSuccessful = "Model configuration successful";

	public static final String letNotReachableWarning = "This state should not be reachable!";

	public static final String invTransformSuccessful = "Invariant transformation successful";

	public static final String modelTransformError = "Model transformation was not successful.";

	public static final String modelTransformSuccessful = "Model transformation successful";

	public static final String NO_SOLVER_SETUP = "No SAT solver active.";
	
	public static final String solverConfigBitwidthError = "Bitwidth has to be an int value.";
	
	public static final String WRONG_BITWIDTH_WARNING = "The bitwidth has to be a value between 1 and 32.";

	public static final String maxComputedIntegersError = "The maximum number of computed Integer values has to be a value between 0 and "
			+ KodkodModelValidatorConfiguration.LARGEST_COMPUTED_INTEGERS + ".";

	public static final String configDiagramExtractionError = "Possible values for AutomaticObjectDiagramExtraction are only " + inQuotes("on") + " or " + inQuotes("off") + ".";

	public static final String objDiagramCreation = "Create object diagram";

	public static final String objDiagramCreationError = "Error during object diagram creation";
	
	public static final String PROPERTIES_NO_CONFIGURATION_WARNING = "No configuration name given. Using first configuration of the file.";

	public static final String propertiesConfigurationReadError = "Error while reading .properties file";

	public static final String propertiesConfigurationCreateError = "Error while creating .properties file";

	public static final String propertiesConfigurationWriteError = "Error while writing .properties file";

	public static final String configurationInvalid = "The configuration breaks these rules. mv -vc also reports rules that do not stop the search.";
	public static final String configurationRefused = "The model validator does not search with this configuration.";
	public static final String configurationValueError = "A configuration value cannot be read";
	public static final String configurationCheckError = "The configuration could not be checked against the validity rules";
	public static final String configurationError = "Error during model configuration. Please check the log for concrete hints.";
	
	public static final String className$Error = "Class names starting with a " + inQuotes("$") + " are not allowed!";

	public static final String solverConfigSaveError = "Error while saving KodkodSolver configuration";

	public static final String solverConfigSaved = "Saved KodkodSolver configuration";
	
	public static final String PLINGELING_NOT_EXECUTABLE = "Solver file for plingeling is not executable and could not be made executable. The file has to be executable in order to use the solver.";

	public static final String modelResetSuccessful = "Reset successful";

	public static final String validationException = "Error while model validation! Please check your configuration.";
	
	public static final String validationOutOfMemory = "Out Of Memory Error! It might be necessary to restart USE.";

	public static final String valueConversionNestedCollections = "Nested collections not supported!";

	public static final String objDiagramExtractionError = "Error while extracting information from the object diagram.";

	public static final String objDiagramExtractionSuccessful = "Object diagram extraction successful.";

	public static final String pagingNext = inQuotes("next") + " to search a new solution";

	public static final String pagingPrevious = inQuotes("previous") + " to get the last solution";

	public static final String pagingFirst = "First solution reached";

	public static final String pagingCmdError = "Set a configuration file or enter next for a new solution respectively previous for the last solution.";

	public static final String pagingCmdFileFirst = "Set a configuration file first!";
	
	public static final String enrichWithLoadedInvariants = "Enrich the model with loaded invariants";

	public static final String errorWithLoadedInvariants = "Error while enriching the model with loaded invariants";

	public static final String inactiveInvariantEval = "Evaluation of inactive invariants";

	public static final String noSuchMethodError = "Please read the Readme file";

	public static final String solutionForbidError = "Error while creating the formula to forbid the last solution!";

	public static final String showSolutionIndexToSmall = "Please enter an index greater 0.";

	public static final String closureObjectMessage = "Closure is only available for collections with objects.";

	public static final String closureCollectionMessage = "Source type is not a collection.";
	
	public static String startModelTransform(String name) {
		return "Start model transformation for " + inQuotes(name);
	}

	public static String invTransformError(String name) {
		return "Cannot transform invariant " + inQuotes(name) + ".";
	}

	public static String untransformedElementsError(List<String> errors) {
		return "Cannot validate the model: the following elements cannot be transformed, and a solution would ignore them.\n"
				+ String.join("\n", errors);
	}

	public static String subsetsNotBinaryError(String end, String subsettedEnd) {
		return "Cannot transform the constraint " + inQuotes("subsets " + subsettedEnd) + " on association end " + inQuotes(end)
				+ ": only binary associations are supported.";
	}

	public static String subsetsDerivedError(String end, String subsettedEnd) {
		return "Cannot transform the constraint " + inQuotes("subsets " + subsettedEnd) + " on association end " + inQuotes(end)
				+ ": derived associations are not supported.";
	}

	public static String redefinesNotBinaryError(String redefinedAssociation, String redefiningAssociation) {
		return redefinesError(redefinedAssociation, redefiningAssociation, "only binary associations are supported.");
	}

	public static String redefinesAssociationClassError(String redefinedAssociation, String redefiningAssociation) {
		return redefinesError(redefinedAssociation, redefiningAssociation, "association classes are not supported.");
	}

	public static String redefinesDerivedError(String redefinedAssociation, String redefiningAssociation) {
		return redefinesError(redefinedAssociation, redefiningAssociation, "derived associations are not supported.");
	}

	public static String redefinesUnionError(String redefinedAssociation, String redefiningAssociation) {
		return redefinesError(redefinedAssociation, redefiningAssociation, "union associations are not supported.");
	}

	private static String redefinesError(String redefinedAssociation, String redefiningAssociation, String reason) {
		return "Cannot transform the redefinition of association " + inQuotes(redefinedAssociation) + " by "
				+ inQuotes(redefiningAssociation) + ": " + reason;
	}

	public static String unionNotBinaryError(String association) {
		return "Cannot transform the union ends of association " + inQuotes(association) + ": only binary associations are supported.";
	}

	public static String unionAssociationClassError(String associationClass) {
		return "Cannot transform the union ends of association class " + inQuotes(associationClass) + ": association classes are not supported.";
	}

	public static String qualifiedAssociationError(String association) {
		return "Cannot transform association " + inQuotes(association) + ": qualified association ends are not supported.";
	}

	public static String collectionOfCollectionsError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": collections of collections are not supported.";
	}

	public static String flattenOrderedSetError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": USE fails to flatten an ordered set that holds an undefined collection.";
	}

	public static String flattenCollectNestedOnCollectionError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": its source has the type Collection, and USE's flatten() keeps an undefined collection of its result"
				+ " for a Set or a Bag source but skips it for a Sequence or an OrderedSet source.";
	}

	public static String collectionDuplicatesError(String expression) {
		return "Cannot transform " + inQuotes(expression)
				+ ": its result depends on the duplicates of a collection that is not a set, which the transformation does not keep.";
	}

	public static String anyError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": when several elements satisfy its body, USE returns"
				+ " the first one its hash-based collection iterates, which the translation does not reproduce.";
	}

	public static String collectionOrderError(String expression) {
		return "Cannot transform " + inQuotes(expression)
				+ ": its result depends on the order of a collection that is not a set, which the transformation does not keep.";
	}

	public static String sortedByError(String expression) {
		return "Cannot transform " + inQuotes(expression)
				+ ": sortedBy returns an ordered collection, whose order the translation does not keep.";
	}

	public static String collectionTypeTestError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": a type test whose source or target type is a collection depends"
				+ " on the runtime type USE computes for a collection from its elements, which the translation does not reproduce.";
	}

	public static String objectReferenceError(String expression, String objectName) {
		return "Cannot transform " + inQuotes(expression) + ": write it as the variable " + objectName + ", which is translated.";
	}

	public static String unsupportedExpressionError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": the translation does not handle this kind of expression.";
	}

	public static String collectionComparisonError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": only two sets can be compared.";
	}

	public static String stringOperationError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": comparing (<, <=, >, >=) or concatenating (+) String values is not supported.";
	}

	public static String realValueError(String expression) {
		return "Cannot transform " + inQuotes(expression) + ": Real values are not supported.";
	}

	public static String modelTransformStoppedError(String name, String reason) {
		return "Model " + inQuotes(name) + ", whose transformation stopped: " + reason;
	}

	/**
	 * Some exceptions, a NullPointerException for one, have no message: their
	 * class name is the only reason there is to report.
	 */
	public static String exceptionReason(Exception exception) {
		return exception.getMessage() != null ? exception.getMessage() : exception.toString();
	}

	public static String invTransformSuccessful(String name) {
		return "Transformation of invariant " + inQuotes(name) + " successful";
	}

	public static String solverConfigWrongArgumentError(String name) {
		return StringUtil.inQuotes(name) + " is not configurable. Only SatSolver and bitwidth.";
	}

	public static String solverConfigSyntaxError(String argument) {
		return StringUtil.inQuotes(argument) + " has not the correct syntax. Use " + inQuotes("name") + " := " + inQuotes("value") + ".";
	}

	public static String invIndepSyntaxError(String argument) {
		return StringUtil.inQuotes(argument) + " does not have the correct syntax. Use " + inQuotes("class") + " - " + inQuotes("invariant") + ".";
	}

	public static String unsupportedCollectionWarning(String collectionName) {
		return "This approach does not support " + collectionName + ". All collections will be handled like sets.";
	}

	public static String differentSymbolOperationMappingsError(String operator, String mapping) {
		return "Different operation names for one operator are not possible. Mapping of operator " + operator + " is " + mapping + ".";
	}

	public static String doubleSymbolOperationMappingWarning(String operator) {
		return "For operator " + operator + " exists more than one operation. Please make sure, that the implementations have different parameters!";
	}

	public static String kodkodStatistics(Statistics statistics) {
		return "Translation time (Kodkod to SAT): " + statistics.translationTime() + " ms; Solving time: " + statistics.solvingTime() + " ms; SAT variables: "
				+ statistics.variables() + " (" + statistics.primaryVariables() + " primary); Clauses: " + statistics.clauses();
	}

	public static String universeSize(Universe universe) {
		int integers = 0;
		for (Object atom : universe) {
			if(atom instanceof Integer){
				integers++;
			}
		}
		return "Universe: " + universe.size() + " atoms, " + integers + " of them Integer values";
	}

	public static String newBitwidth(int newBitwidth) {
		return "Set bitwidth to " + newBitwidth + ".";
	}

	public static String newMaxBitwidth(int newMaxBitwidth) {
		return "Set maximum bitwidth to " + newMaxBitwidth + ".";
	}

	public static String newMaxComputedIntegers(int newMaxComputedIntegers) {
		return "Set maximum number of computed Integer values to " + newMaxComputedIntegers + ".";
	}

	public static String addedComputedIntegers(int added) {
		return "Added " + added + " Integer values outside Integer_min and Integer_max, for the values the Integer expressions of the model may compute.";
	}

	public static String computedIntegersAboveMaximum(int maxComputedIntegers) {
		return "The Integer expressions of the model may compute more than " + maxComputedIntegers
				+ " Integer values outside Integer_min and Integer_max, above the maximum number of computed Integer values. Raise it with: mv -config "
				+ KodkodModelValidatorConfiguration.MAX_COMPUTED_INTEGERS_KEY + ":=N, or narrow the values the expressions compute.";
	}

	public static String raisedBitwidth(int bitwidth, int needed) {
		return "The Integer expressions of the model need bitwidth " + needed + ", above the bitwidth " + bitwidth + ": solving with bitwidth " + needed + ".";
	}

	public static String bitwidthAboveMaximum(int needed, int maxBitwidth) {
		if(needed > KodkodModelValidatorConfiguration.LARGEST_BITWIDTH){
			return "The Integer expressions of the model need a bitwidth above " + KodkodModelValidatorConfiguration.LARGEST_BITWIDTH
					+ ", the largest the solver supports. Narrow Integer_min and Integer_max, or the values the expressions compute.";
		}
		return "The Integer expressions of the model need bitwidth " + needed + ", above the maximum bitwidth " + maxBitwidth
				+ ". Raise it with: mv -config " + KodkodModelValidatorConfiguration.MAX_BITWIDTH_KEY + ":=" + needed;
	}

	public static String newSatSolver(String satFactory) {
		return "Set SatSolver to " + inQuotes(satFactory) + ".";
	}

	public static String newAutomaticDiagramExtraction(boolean automaticDiagramExtraction) {
		String mode = automaticDiagramExtraction ? "Enable" : "Disable";
		return mode + " automatic extraction of an available object diagram.";
	}

	public static String noSatSolverLibraryError(String satFactory) {
		return "Could not load the library for SatSolver " + inQuotes(satFactory) + ".";
	}

	public static String noSatSolverWarning(String solverName) {
		return "No solver " + inQuotes(solverName) + " available.";
	}
	
	public static String defaultSolverLibraryFallback(String solverName) {
		return "Using solver " + inQuotes(solverName) + " as a fallback.";
	}
	
	public static String availableSatSolvers(String[] solvers){
		return "Detected instantiable SAT solvers are: " + StringUtil.fmtSeq(solvers, ", ");
	}

	public static String fileCmdError(File file) {
		return "Cannot read file " + file.getAbsolutePath() + "!";
	}

	public static String typeConvertError(String name) {
		return "Cannot convert type " + name;
	}

	public static String noEnumTypeError(String name) {
		return "No enum type " + name;
	}

	public static String modelTransformTime(long timeMs) {
		return "Translation time (USE to Kodkod): " + timeMs + " ms";
	}

	public static String noValueCreation(String type) {
		return "Creation of values for " + type + " not implemented!";
	}

	public static Object searchSolution(String satFactory, int bitwidth) {
		return "Searching solution with SatSolver " + inQuotes(satFactory) + " and bitwidth " + bitwidth + "...";
	}

	public static String libraryPathWarning(String defaultSatFactory, String exceptionMsg) {
		return "Only default SatSolver " + inQuotes(defaultSatFactory) + " can be used! " + exceptionMsg;
	}

	public static String noClassInvariantError(String className, String invariantName) {
		return "Class " + inQuotes(className) + " has no invariant " + inQuotes(invariantName);
	}

	public static String noClassError(String className) {
		return "Class " + inQuotes(className) + " does not exist";
	}

	public static String aggregationcyclefreenessInfo() {
		String status = DefaultConfigurationValues.AGGREGATIONCYCLEFREENESS ? "on" : "off";
		return "Only on and off are possible values of aggregationcyclefreeness! Using default " + inQuotes(status) + ".";
	}

	public static String forbiddensharingInfo() {
		String status = DefaultConfigurationValues.FORBIDDENSHARING ? "on" : "off";
		return "Only on and off are possible values of forbiddensharing! Using default " + inQuotes(status) + ".";
	}
	
	public static String invariantConfigWarning(String state) {
		return state + " is not possible for an invariant. Possible states for an invariant are "
				+ inQuotes("active") + ", " + inQuotes("inactive") + " and "
				+ inQuotes("negate") + ". Using default " + inQuotes("active") + ".";
	}

	public static String showSolution(int index) {
		return "Show solution " + index;
	}

	public static String showSolutionIndexToBig(int index) {
		return "There are only " + index + " solutions.";
	}

	public static String sizeConfigWarning(String name) {
		return "The value for " + name + " is not a number! The default value will be used.";
	}

	public static String realStepConfigError(double step) {
		return "The step " + step + " is not at least " + DefaultConfigurationValues.realStepMinimum
				+ ", the precision of the Real values of the search space.";
	}

	public static String setSyntaxConfigError(String syntax) {
		return "Not the correct syntax. Use the syntax " + syntax + ".";
	}

	public static String complexElementConfigError(String className) {
		return "Only defined objects of class " + className
				+ " can be used. Use the specific class objects or the objects defined by the minimum object size.";
	}

	public static String unexpectedInvariantResult(IInvariant invariant) {
		String message;
		if(invariant.isNegated()){
			message = "USE finds the negated invariant %s true in the object diagram of the solution.";
		}
		else {
			message = "USE finds the invariant %s false in the object diagram of the solution.";
		}
		message += " The translation and USE disagree here, so the solution is not valid.";
		return String.format(message, inQuotes(invariant.qualifiedName()));
	}

	public static String flagChangeInfo(IInvariant inv, boolean didDisable) {
		String message;
		if(didDisable){
			message = "Overwrite property configuration with generator configuration disabling invariant %s.";
		}
		else {
			message = "Overwrite property configuration with generator configuration negating invariant %s.";
		}
		return String.format(message, inQuotes(inv.qualifiedName()));
	}

}
