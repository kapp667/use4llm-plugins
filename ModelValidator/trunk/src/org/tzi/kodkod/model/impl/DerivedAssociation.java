package org.tzi.kodkod.model.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.tzi.kodkod.model.iface.IAssociationEnd;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.impl.DerivedAssociationEnd.Parameter;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.kodkod.transform.ocl.DefaultExpressionVisitor;
import org.tzi.use.util.StringUtil;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntConstant;
import kodkod.ast.IntExpression;
import kodkod.ast.Node;
import kodkod.ast.Relation;
import kodkod.ast.Variable;

/**
 * @author Frank Hilken
 */
public class DerivedAssociation extends Association {

	DerivedAssociation(IModel model, String name) {
		super(model, name);
	}

	@Override
	public Formula constraints() {
		return Formula.and(multiplicityDefinitions(), cycleFreenessDefinitions());
	}

	/**
	 * Creates the formula for the multiplicity of each end that is not
	 * 0..*. The derived links are not in the solver's relation, so each end is
	 * counted on the derive expression instead.
	 */
	@Override
	protected Formula multiplicityDefinitions() {
		List<Formula> formulas = new ArrayList<Formula>();
		for (int index = 0; index < associationEnds().size(); index++) {
			Multiplicity multiplicity = associationEnds().get(index).multiplicity();
			if (multiplicity == null || multiplicity.isZeroMany()) {
				continue;
			}
			formulas.add(endMultiplicityDefinition(index, multiplicity));
		}
		return Formula.and(formulas);
	}

	/**
	 * Creates the multiplicity formula of the end with the given index of a
	 * binary derived association. For the derived end, each object of the
	 * other end has as many derived objects as the multiplicity allows. For
	 * the other end, each object of the derived end has as many objects of
	 * this end, whose derived set holds it, as the multiplicity allows.
	 */
	private Formula endMultiplicityDefinition(int index, Multiplicity multiplicity) {
		IAssociationEnd end = associationEnds().get(index);
		if (!isBinaryAssociation()) {
			throw new TransformationException("The multiplicity of end " + StringUtil.inQuotes(end.name())
					+ " of n-ary derived association " + StringUtil.inQuotes(name())
					+ " is not supported.");
		}
		IAssociationEnd otherEnd = associationEnds().get(1 - index);
		DerivedAssociationEnd derivedEnd = derivedEnd();

		if (end == derivedEnd) {
			Variable x = Variable.unary("x");
			IntExpression count = derivedSet(derivedEnd, x).count();
			return countInRanges(count, multiplicity)
					.forAll(x.oneOf(otherEnd.associatedClass().inheritanceOrRegularRelation()));
		}

		Variable y = Variable.unary("y");
		Variable x = Variable.unary("x");
		Expression linkedObjects = y.in(derivedSet(derivedEnd, x))
				.comprehension(x.oneOf(end.associatedClass().inheritanceOrRegularRelation()));
		return countInRanges(linkedObjects.count(), multiplicity)
				.forAll(y.oneOf(derivedEnd.associatedClass().inheritanceOrRegularRelation()));
	}

	private DerivedAssociationEnd derivedEnd() {
		for (IAssociationEnd end : associationEnds()) {
			if (end instanceof DerivedAssociationEnd) {
				return (DerivedAssociationEnd) end;
			}
		}
		throw new TransformationException("Derived association does not have derived association end.");
	}

	/**
	 * Returns the objects that the derive expression of the derived end yields
	 * for the given object, the parameter of the expression being bound to it
	 * as navigation does. The undefined values are left out.
	 */
	private Expression derivedSet(DerivedAssociationEnd derivedEnd, Variable self) {
		org.tzi.use.uml.ocl.expr.Expression derivedExpression = derivedEnd.derivedExpression();
		if (derivedExpression == null) {
			throw new TransformationException("Derived association end " + StringUtil.inQuotes(derivedEnd.name())
					+ " has no derive expression.");
		}
		if (derivedExpression.type().isTypeOfVoidType()) {
			return Expression.NONE;
		}
		List<Parameter> parameters = derivedEnd.derivedParameters();
		if (parameters.size() != 1) {
			throw new TransformationException("Derived association end " + StringUtil.inQuotes(derivedEnd.name())
					+ " must have exactly one parameter.");
		}

		Map<String, Node> variables = new HashMap<String, Node>();
		variables.put(parameters.get(0).getName(), self);
		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, variables,
				new HashMap<String, Variable>(), new ArrayList<String>());
		derivedExpression.processWithVisitor(visitor);

		if (!(visitor.getObject() instanceof Expression)) {
			throw new TransformationException("Derive expression of " + StringUtil.inQuotes(derivedEnd.name())
					+ " is not translated to objects.");
		}
		Expression set = (Expression) visitor.getObject();
		Relation undefined = model.typeFactory().undefinedType().relation();
		Relation undefinedSet = model.typeFactory().undefinedSetType().relation();
		return set.eq(undefinedSet).thenElse(Expression.NONE, set.difference(undefined));
	}

	/**
	 * Creates the formula that the count lies in one of the ranges of the
	 * multiplicity, as Association does.
	 */
	private Formula countInRanges(IntExpression count, Multiplicity multiplicity) {
		Formula formula = null;
		for (Range range : multiplicity.getRanges()) {
			Formula rangeFormula = Formula.TRUE;
			if (range.getLower() > 0) {
				rangeFormula = count.gte(IntConstant.constant(range.getLower()));
			}
			if (range.getUpper() != Multiplicity.MANY) {
				rangeFormula = rangeFormula.and(count.lte(IntConstant.constant(range.getUpper())));
			}
			formula = (formula == null) ? rangeFormula : formula.or(rangeFormula);
		}
		if (formula == null) {
			throw new TransformationException("A multiplicity of a derived association has no range.");
		}
		return formula;
	}

	@Override
	protected Formula cycleFreenessDefinitions() {
		//TODO cycle-freeness
		return Formula.TRUE;
	}

}
