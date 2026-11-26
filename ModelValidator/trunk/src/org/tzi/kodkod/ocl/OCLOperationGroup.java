package org.tzi.kodkod.ocl;

import java.util.HashMap;
import java.util.Map;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IfExpression;
import kodkod.ast.IntExpression;
import kodkod.ast.IntToExprCast;
import kodkod.ast.Relation;
import kodkod.ast.operator.IntCastOperator;

import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.kodkod.model.type.TypeFactory;

/**
 * Abstract base class for all classes with transformation methods.
 * 
 * @author Hendrik Reitmann
 * 
 */
public abstract class OCLOperationGroup {

	protected TypeFactory typeFactory;
	protected Relation undefined;
	protected Relation undefined_Set;
	protected Expression booleanTrue;
	protected Expression booleanFalse;
	protected Map<String, String> symbolOperationMapping;

	public OCLOperationGroup(TypeFactory typeFactory) {
		this.typeFactory = typeFactory;

		undefined = typeFactory.undefinedType().relation();
		undefined_Set = typeFactory.undefinedSetType().relation();

		Map<String, Expression> typeLiterals = typeFactory.booleanType().typeLiterals();
		booleanTrue = typeLiterals.get(TypeConstants.BOOLEAN_TRUE);
		booleanFalse = typeLiterals.get(TypeConstants.BOOLEAN_FALSE);

		symbolOperationMapping = new HashMap<String, String>();
	}

	/**
	 * True if the class contains transformation methods for set operations.
	 * 
	 * @return
	 */
	public boolean isSetOperationGroup() {
		return false;
	}

	/**
	 * Does the transformation of the operation results in a set.
	 * 
	 * @param opName
	 * @return
	 */
	public boolean returnsSet(String opName) {
		return false;
	}

	/**
	 * Returns the mapping of symbol to the names of the transformation methods.
	 * 
	 * @return
	 */
	public Map<String, String> getSymbolOperationMapping() {
		return symbolOperationMapping;
	}

	/**
	 * Returns undefined when the condition holds, the Integer value otherwise.
	 * Read the result with integerValue and integerUndefined, which do not
	 * need its atom: the Integer bounds hold no atom for a value outside
	 * Integer_min and Integer_max that is not a literal, and KodkodSolver adds
	 * one only for the values the formula reads as an atom.
	 */
	protected final Expression integer(Formula undefinedIf, IntExpression value) {
		return undefinedIf.thenElse(undefined, value.toExpression());
	}

	/**
	 * Returns the value of an Integer expression, meaningful where it is
	 * defined: the integer expression of a cast, the value of the branch an
	 * if-then-else takes (the other branch when one is undefined), and the
	 * sum of the atoms of any other expression.
	 */
	protected final IntExpression integerValue(Expression e) {
		if (isIntegerCast(e)) {
			return ((IntToExprCast) e).intExpr();
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			if (choice.thenExpr() == undefined) {
				return integerValue(choice.elseExpr());
			}
			if (choice.elseExpr() == undefined) {
				return integerValue(choice.thenExpr());
			}
			return choice.condition().thenElse(integerValue(choice.thenExpr()), integerValue(choice.elseExpr()));
		}
		IntExpression read = e.sum();
		typeFactory.singleIntegerReads().add(read);
		return read;
	}

	/**
	 * Returns the formula that holds when an Integer expression is undefined:
	 * never for a cast, always for undefined, by the branch it takes for an
	 * if-then-else, and by comparison with undefined for any other expression.
	 */
	protected final Formula integerUndefined(Expression e) {
		if (isIntegerCast(e)) {
			return Formula.FALSE;
		}
		if (e == undefined) {
			return Formula.TRUE;
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			Formula condition = choice.condition();
			return condition.and(integerUndefined(choice.thenExpr())).or(condition.not().and(integerUndefined(choice.elseExpr())));
		}
		return e.eq(undefined);
	}

	/**
	 * Returns the formula that holds, where the expression is defined, when it
	 * is an Integer that integerValue can read: always for a cast, by the
	 * branch it takes for an if-then-else, and when it is a single Integer atom
	 * for any other expression.
	 */
	protected final Formula integerKind(Expression e) {
		if (isIntegerCast(e) || e == undefined) {
			return Formula.TRUE;
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			Formula condition = choice.condition();
			return condition.and(integerKind(choice.thenExpr())).or(condition.not().and(integerKind(choice.elseExpr())));
		}
		return e.one().and(e.in(Expression.INTS));
	}

	/**
	 * Returns the expression with each integer cast, alone or in a branch of
	 * an if-then-else, replaced by the empty set, for an equality of two
	 * expressions that are not both Integers: the formula then reads no cast
	 * as an atom, and needs no atom for its value.
	 */
	protected final Expression withoutIntegerCasts(Expression e) {
		if (isIntegerCast(e)) {
			return Expression.NONE;
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			return choice.condition().thenElse(withoutIntegerCasts(choice.thenExpr()), withoutIntegerCasts(choice.elseExpr()));
		}
		return e;
	}

	/**
	 * True if the expression may hold an Integer the Integer bounds hold no
	 * atom for: a cast of an integer expression other than an Integer literal
	 * of the model, alone or in a branch of an if-then-else.
	 */
	protected final boolean isComputedInteger(Expression e) {
		if (isIntegerCast(e)) {
			return !typeFactory.integerType().typeLiterals().containsValue(e);
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			return isComputedInteger(choice.thenExpr()) || isComputedInteger(choice.elseExpr());
		}
		return false;
	}

	private boolean isIntegerCast(Expression e) {
		return e instanceof IntToExprCast && ((IntToExprCast) e).op() == IntCastOperator.INTCAST;
	}
}
