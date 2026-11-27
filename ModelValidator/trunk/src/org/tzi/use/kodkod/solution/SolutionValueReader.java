package org.tzi.use.kodkod.solution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kodkod.ast.Formula;
import kodkod.ast.IntExpression;
import kodkod.ast.Node;
import kodkod.engine.Evaluator;
import kodkod.instance.Tuple;
import kodkod.instance.TupleSet;

import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.SetValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Reads the value that a solution gives the translation of an OCL expression as
 * a value of USE, so that it can be compared with the value USE computes in the
 * object diagram of the solution.
 */
public class SolutionValueReader {

	private static final String STRING_PREFIX = TypeConstants.STRING + "_";

	private final Map<String, Value> valuesOfAtoms = new HashMap<String, Value>();

	/**
	 * Creates a reader for the solution whose object diagram is the given state.
	 */
	public SolutionValueReader(MSystemState state) {
		valuesOfAtoms.put(TypeConstants.TRUE, BooleanValue.TRUE);
		valuesOfAtoms.put(TypeConstants.FALSE, BooleanValue.FALSE);
		valuesOfAtoms.put(TypeConstants.UNDEFINED, UndefinedValue.instance);
		for (EnumType enumType : state.system().model().enumTypes()) {
			for (String literal : enumType.getLiterals()) {
				valuesOfAtoms.put(enumType.name() + "_" + literal, new EnumValue(enumType, literal));
			}
		}
		for (MObject object : state.allObjects()) {
			valuesOfAtoms.put(object.cls().name() + "_" + object.name(), new ObjectValue(object.cls(), object));
		}
	}

	/**
	 * Evaluates the translation of an expression in a solution. Returns a
	 * TupleSet, an Integer or a Boolean, as the kind of the translation is.
	 */
	public static Object evaluate(Evaluator evaluator, Node translation) {
		if (translation instanceof Formula) {
			return evaluator.evaluate((Formula) translation);
		}
		if (translation instanceof IntExpression) {
			return evaluator.evaluate((IntExpression) translation);
		}
		return evaluator.evaluate((kodkod.ast.Expression) translation);
	}

	/**
	 * Returns the value that a result of evaluate stands for, for an expression
	 * of the given type. A collection is read as the set of its elements, as the
	 * translation represents it. Returns null if the result stands for no value,
	 * as the empty set that Kodkod gives an Integer outside the bounds.
	 */
	public Value read(Object result, Type type) {
		if (result instanceof Boolean) {
			return BooleanValue.get((Boolean) result);
		}
		if (result instanceof Integer) {
			return IntegerValue.valueOf((Integer) result);
		}
		TupleSet tuples = (TupleSet) result;
		if (tuples.arity() != 1) {
			return null;
		}
		if (!type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
			return tuples.size() == 1 ? valueOf(tuples.iterator().next().atom(0)) : null;
		}
		if (tuples.size() == 1 && TypeConstants.UNDEFINED_SET.equals(tuples.iterator().next().atom(0))) {
			return UndefinedValue.instance;
		}
		List<Value> elements = new ArrayList<Value>();
		for (Tuple tuple : tuples) {
			Value element = valueOf(tuple.atom(0));
			if (element == null) {
				return null;
			}
			elements.add(element);
		}
		return new SetValue(((CollectionType) type).elemType(), elements);
	}

	private Value valueOf(Object atom) {
		if (atom instanceof Integer) {
			return IntegerValue.valueOf((Integer) atom);
		}
		String name = (String) atom;
		if (name.startsWith(STRING_PREFIX)) {
			return new StringValue(name.substring(STRING_PREFIX.length()));
		}
		return valuesOfAtoms.get(name);
	}
}
