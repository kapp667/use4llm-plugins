package org.tzi.kodkod.ocl.operation;

import java.util.Map;

import kodkod.ast.Expression;
import kodkod.ast.Formula;

import org.tzi.kodkod.model.type.StringType;
import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLOperationGroup;

/**
 * Contains all boolean transformation.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class BooleanOperationGroup extends OCLOperationGroup {

	public BooleanOperationGroup(TypeFactory typeFactory) {
		super(typeFactory);
	}

	public final Expression and(Expression l, Expression r) {
		return l.eq(booleanFalse).or(r.eq(booleanFalse)).thenElse(booleanFalse,
				l.eq(undefined).or(r.eq(undefined)).thenElse(undefined, booleanTrue));
	}

	public final Expression and(Formula l, Expression r) {
		return l.thenElse(r, booleanFalse);
	}

	public final Expression and(Expression l, Formula r) {
		return r.thenElse(l, booleanFalse);
	}

	public final Formula and(Formula l, Formula r) {
		return l.and(r);
	}

	public final Expression implies(Expression l, Expression r) {
		return or(not(l), r);
	}

	public final Expression implies(Formula l, Expression r) {
		return or(l.not(), r);
	}

	public final Expression implies(Expression l, Formula r) {
		return or(not(l), r);
	}

	public final Formula implies(Formula l, Formula r) {
		return l.implies(r);
	}

	public final Expression not(Expression e) {
		return e.eq(undefined).thenElse(undefined, e.eq(booleanTrue).thenElse(booleanFalse, booleanTrue));
	}

	public final Formula not(Formula e) {
		return e.not();
	}

	public final Expression or(Expression l, Expression r) {
		return l.eq(booleanTrue).or(r.eq(booleanTrue)).thenElse(booleanTrue,
				l.eq(undefined).or(r.eq(undefined)).thenElse(undefined, booleanFalse));
	}

	public final Expression or(Formula l, Expression r) {
		return l.thenElse(booleanTrue, r);
	}

	public final Expression or(Expression l, Formula r) {
		return r.thenElse(booleanTrue, l);
	}

	public final Formula or(Formula l, Formula r) {
		return l.or(r);
	}

	public final Expression toString(Expression src) {
		StringType stringType = (StringType) typeFactory.stringType();
		return src.eq(undefined).thenElse(undefined, src.join(stringType.toStringMap()));
	}

	public final Expression toString(Formula src) {
		StringType stringType = (StringType) typeFactory.stringType();
		Map<String, Expression> typeLiterals = stringType.typeLiterals();
		return src.thenElse(typeLiterals.get(TypeConstants.STRING_TRUE), typeLiterals.get(TypeConstants.STRING_FALSE));
	}

	public final Expression xor(Expression l, Expression r) {
		return l.eq(undefined).or(r.eq(undefined)).thenElse(undefined, l.eq(r).thenElse(booleanFalse, booleanTrue));
	}

	public final Expression xor(Formula l, Expression r) {
		return r.eq(undefined).thenElse(undefined, l.iff(r.eq(booleanTrue)).thenElse(booleanFalse, booleanTrue));
	}

	public final Expression xor(Expression l, Formula r) {
		return l.eq(undefined).thenElse(undefined, r.iff(l.eq(booleanTrue)).thenElse(booleanFalse, booleanTrue));
	}

	public final Formula xor(Formula l, Formula r) {
		return l.and(r.not()).or(l.not().and(r));
	}
}