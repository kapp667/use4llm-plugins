package org.tzi.use.kodkod.transform.ocl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kodkod.ast.IntExpression;
import kodkod.ast.IntToExprCast;
import kodkod.ast.Node;
import kodkod.ast.Variable;

import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;

/**
 * Extension of DefaultExpressionVisitor to visit the general operation
 * expressions.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class StandardOperationVisitor extends DefaultExpressionVisitor {

	private List<Object> arguments;

	public StandardOperationVisitor(IModel model, Map<String, Node> variables, Map<String, Variable> replaceVariables,
			List<String> collectionVariables) {
		super(model, variables, replaceVariables, collectionVariables);
	}

	@Override
	public void visitStdOp(ExpStdOp exp) {
		arguments = new ArrayList<Object>();

		for (org.tzi.use.uml.ocl.expr.Expression expArg : exp.args()) {
			DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
			if (exp.opname().equals("flatten")) {
				visitor.acceptCollectionOfCollections();
			}
			expArg.processWithVisitor(visitor);
			arguments.add(visitor.getObject());
			set = set || visitor.isSet();
		}
		
		refuseStringOrderAndConcatenation(exp);
		refuseWhatSetsDoNotKeep(exp);

		if (exp.opname().equals("-") && !set && arguments.size() == 1) {
			handleMinus();
		} else {
			invokeMethod(exp.opname(), arguments, set);
		}
	}

	private static final Set<String> STRING_ORDER_AND_CONCATENATION = Set.of("<", "<=", ">", ">=", "+");

	private void refuseStringOrderAndConcatenation(ExpStdOp exp) {
		if (STRING_ORDER_AND_CONCATENATION.contains(exp.opname()) && exp.args().length == 2 && exp.args()[0].type().isTypeOfString()) {
			throw new TransformationException(LogMessages.stringOperationError(exp.toString()));
		}
	}

	/**
	 * Refuses an operation whose result depends on the duplicates of its source, or
	 * a comparison of collections that are not both sets.
	 */
	private void refuseWhatSetsDoNotKeep(ExpStdOp exp) {
		String opname = exp.opname();
		if ((opname.equals("size") || opname.equals("count") || opname.equals("sum")) && canHoldDuplicates(exp.args()[0].type())) {
			throw new TransformationException(LogMessages.collectionDuplicatesError(exp.toString()));
		}
		if ((opname.equals("=") || opname.equals("<>")) && (isCollectionButNotSet(exp.args()[0].type()) || isCollectionButNotSet(exp.args()[1].type()))) {
			throw new TransformationException(LogMessages.collectionComparisonError(exp.toString()));
		}
	}

	private static boolean isCollectionButNotSet(Type type) {
		return type.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && !type.isTypeOfSet();
	}

	private void handleMinus() {
		if (arguments.get(0) instanceof IntToExprCast) {
			IntToExprCast intToExprCast = (IntToExprCast) arguments.get(0);

			IntExpression negate = intToExprCast.intExpr().negate();
			String stringValue = negate.toString();
			stringValue = stringValue.substring(1).replace(")", "");
			if (!stringValue.startsWith("--")) {
				Integer integerValue = Integer.parseInt(stringValue);

				arguments.clear();
				arguments.add(negate.toExpression());
				visitConstInteger(integerValue);
				return;
			}
		}

		invokeMethod("negation", arguments, set);
	}
}
