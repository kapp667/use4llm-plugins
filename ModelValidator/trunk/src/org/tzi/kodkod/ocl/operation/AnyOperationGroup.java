package org.tzi.kodkod.ocl.operation;

import kodkod.ast.Expression;
import kodkod.ast.Formula;

import org.tzi.kodkod.model.type.StringType;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLOperationGroup;

/**
 * Contains all ocl any transformation methods
 * 
 * @author Hendrik Reitmann
 * 
 */
public class AnyOperationGroup extends OCLOperationGroup {

	public AnyOperationGroup(TypeFactory typeFactory, boolean registerSymbols) {
		super(typeFactory);

		if (registerSymbols) {
			symbolOperationMapping.put("=", "equality");
			symbolOperationMapping.put("<>", "inequality");
		}
	}

	public Formula equality(Expression l, Expression r) {
		if (isComputedInteger(l) || isComputedInteger(r)) {
			return integerEquality(l, r);
		}
		return l.eq(r);
	}

	public Formula equality(Formula l, Expression r) {
		return l.and(r.eq(booleanTrue)).or(l.not().and(r.eq(booleanFalse)));
	}

	public Formula equality(Expression l, Formula r) {
		return l.eq(booleanTrue).and(r).or(l.eq(booleanFalse).and(r.not()));
	}

	public Formula equality(Formula l, Formula r) {
		return l.iff(r);
	}

	public Formula inequality(Expression l, Expression r) {
		return equality(l, r).not();
	}

	public Formula inequality(Formula l, Expression r) {
		return r.eq(booleanTrue).implies(l.not()).and(r.eq(booleanFalse).implies(l));
	}

	public Formula inequality(Expression l, Formula r) {
		return l.eq(booleanTrue).implies(r.not()).and(l.eq(booleanFalse).implies(r));
	}

	public Formula inequality(Formula l, Formula r) {
		return l.iff(r).not();
	}

	public Formula isDefined(Expression src) {
		return src.eq(undefined).not();
	}

	public Formula isDefined(Formula src) {
		return Formula.TRUE;
	}

	public Expression oclAsType(Expression src, Expression cls) {
		return src.in(cls).thenElse(src, undefined);
	}

	public Formula oclAsType(Formula src, Expression cls) {
		return src;
	}

	public Formula oclIsKindOf(Expression src, Expression cls) {
		return src.eq(undefined).or(src.in(instanceAtoms(cls)));
	}

	public Formula oclIsKindOf(Formula src, Expression cls) {
		return cls.eq(typeFactory.booleanType().relation()).or(cls.eq(Expression.UNIV));
	}

	public Formula oclIsTypeOf(Expression src, Expression cls) {
		return cls.eq(Expression.UNIV).not().and(src.in(instanceAtoms(cls)));
	}

	public Formula oclIsTypeOf(Formula src, Expression cls) {
		return cls.eq(typeFactory.booleanType().relation());
	}

	/**
	 * Returns the atoms of the values of the given type. The literals and the
	 * toString atoms of Strings are not in the String relation, whose bounds
	 * constrain the attribute values only.
	 */
	private Expression instanceAtoms(Expression cls) {
		StringType stringType = (StringType) typeFactory.stringType();
		if (cls != stringType.relation()) {
			return cls;
		}
		Expression atoms = cls.union(Expression.UNIV.join(stringType.toStringMap()));
		for (Expression literal : stringType.typeLiterals().values()) {
			atoms = atoms.union(literal);
		}
		return atoms;
	}

	public Formula isUndefined(Expression src) {
		return src.eq(undefined);
	}

	public Formula isUndefined(Formula src) {
		return Formula.FALSE;
	}

	/**
	 * Returns the formula that holds when two expressions, at least one a
	 * computed Integer, are equal: both undefined, or both defined and either
	 * both Integers of equal value or, when not both are Integers, equal atoms,
	 * an integer cast counting as none.
	 */
	private Formula integerEquality(Expression l, Expression r) {
		Formula lUndefined = integerUndefined(l);
		Formula rUndefined = integerUndefined(r);
		Formula integers = integerKind(l).and(integerKind(r));
		Formula equal = integers.and(integerValue(l).eq(integerValue(r))).or(integers.not().and(withoutIntegerCasts(l).eq(withoutIntegerCasts(r))));
		return lUndefined.and(rUndefined).or(lUndefined.not().and(rUndefined.not()).and(equal));
	}
}
