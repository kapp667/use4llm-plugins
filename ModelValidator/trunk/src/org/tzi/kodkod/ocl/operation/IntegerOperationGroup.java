package org.tzi.kodkod.ocl.operation;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntConstant;

import org.tzi.kodkod.model.type.StringType;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLOperationGroup;

/**
 * Contains transformation methods for integer operations.
 * 
 * @author Hendrik
 */
public class IntegerOperationGroup extends OCLOperationGroup {

	public IntegerOperationGroup(TypeFactory typeFactory) {
		super(typeFactory);

		symbolOperationMapping.put("*", "multiply");
		symbolOperationMapping.put("-", "minus");
		// no "/": it is a Real division in OCL, which the translation does not support
		symbolOperationMapping.put("+", "plus");
		symbolOperationMapping.put("<", "less");
		symbolOperationMapping.put("<=", "lessOrEqual");
		symbolOperationMapping.put(">", "greater");
		symbolOperationMapping.put(">=", "greaterOrEqual");
	}

	public final Expression abs(Expression e) {
		return integer(integerUndefined(e), integerValue(e).abs());
	}

	public final Expression div(Expression l, Expression r) {
		return integer(eitherUndefined(l, r).or(integerValue(r).eq(IntConstant.constant(0))),
				integerValue(l).divide(integerValue(r)));
	}

	public final Expression floor(Expression e) {
		return e;
	}

	public final Expression greater(Expression l, Expression r) {
		return eitherUndefined(l, r).thenElse(undefined, integerValue(l).gt(integerValue(r)).thenElse(booleanTrue, booleanFalse));
	}

	public final Expression greaterOrEqual(Expression l, Expression r) {
		return eitherUndefined(l, r).thenElse(undefined, integerValue(l).gte(integerValue(r)).thenElse(booleanTrue, booleanFalse));
	}

	public final Expression less(Expression l, Expression r) {
		return eitherUndefined(l, r).thenElse(undefined, integerValue(l).lt(integerValue(r)).thenElse(booleanTrue, booleanFalse));
	}

	public final Expression lessOrEqual(Expression l, Expression r) {
		return eitherUndefined(l, r).thenElse(undefined, integerValue(l).lte(integerValue(r)).thenElse(booleanTrue, booleanFalse));
	}

	public final Expression max(Expression l, Expression r) {
		return integer(eitherUndefined(l, r), integerValue(l).gte(integerValue(r)).thenElse(integerValue(l), integerValue(r)));
	}

	public final Expression min(Expression l, Expression r) {
		return integer(eitherUndefined(l, r), integerValue(l).lte(integerValue(r)).thenElse(integerValue(l), integerValue(r)));
	}

	public final Expression minus(Expression l, Expression r) {
		return integer(eitherUndefined(l, r), integerValue(l).minus(integerValue(r)));
	}

	public final Expression mod(Expression l, Expression r) {
		return integer(eitherUndefined(l, r).or(integerValue(r).eq(IntConstant.constant(0))),
				integerValue(l).modulo(integerValue(r)));
	}

	public final Expression multiply(Expression l, Expression r) {
		return integer(eitherUndefined(l, r), integerValue(l).multiply(integerValue(r)));
	}

	public final Expression negation(Expression e) {
		return integer(integerUndefined(e), integerValue(e).negate());
	}

	public final Expression plus(Expression l, Expression r) {
		return integer(eitherUndefined(l, r), integerValue(l).plus(integerValue(r)));
	}

	public final Expression round(Expression e) {
		return e;
	}

	public final Expression toString(Expression src) {
		StringType stringType = (StringType) typeFactory.stringType();
		return src.eq(undefined).thenElse(undefined, src.join(stringType.toStringMap()));
	}

	private Formula eitherUndefined(Expression l, Expression r) {
		return integerUndefined(l).or(integerUndefined(r));
	}
}
