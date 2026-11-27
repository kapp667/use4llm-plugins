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

	public sealed interface Reading {
		/**
		 * An unreadable result is not compared, so it disagrees with no value.
		 */
		boolean disagreesWith(Value useValue);
	}

	public record Read(Value value) implements Reading {
		@Override
		public boolean disagreesWith(Value useValue) {
			return !value.equals(useValue);
		}

		@Override
		public String toString() {
			return value.toString();
		}
	}

	public record NoValue(TupleSet tuples) implements Reading {
		@Override
		public boolean disagreesWith(Value useValue) {
			return true;
		}

		@Override
		public String toString() {
			return tuples.toString();
		}
	}

	public record Unreadable(String reason) implements Reading {
		@Override
		public boolean disagreesWith(Value useValue) {
			return false;
		}

		@Override
		public String toString() {
			return reason;
		}
	}

	private static Object evaluate(Evaluator evaluator, Node translation) {
		if (translation instanceof Formula) {
			return evaluator.evaluate((Formula) translation);
		}
		if (translation instanceof IntExpression) {
			return evaluator.evaluate((IntExpression) translation);
		}
		return evaluator.evaluate((kodkod.ast.Expression) translation);
	}

	/**
	 * An Integer outside the bounds of the solution gives the empty set, read as
	 * NoValue.
	 */
	public Reading read(Evaluator evaluator, Node translation, Type type) {
		if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && !type.isTypeOfSet()) {
			return new Unreadable("the translation represents a " + type + " by a set");
		}
		Object result = evaluate(evaluator, translation);
		if (result instanceof Boolean) {
			return new Read(BooleanValue.get((Boolean) result));
		}
		if (result instanceof Integer) {
			return new Read(IntegerValue.valueOf((Integer) result));
		}
		TupleSet tuples = (TupleSet) result;
		if (tuples.arity() != 1) {
			return unreadable(tuples);
		}
		if (!type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
			if (tuples.size() != 1) {
				return new NoValue(tuples);
			}
			Value value = valueOf(tuples.iterator().next().atom(0));
			return value == null ? unreadable(tuples) : new Read(value);
		}
		if (tuples.size() == 1 && TypeConstants.UNDEFINED_SET.equals(tuples.iterator().next().atom(0))) {
			return new Read(UndefinedValue.instance);
		}
		List<Value> elements = new ArrayList<Value>();
		for (Tuple tuple : tuples) {
			Value element = valueOf(tuple.atom(0));
			if (element == null) {
				return unreadable(tuples);
			}
			elements.add(element);
		}
		return new Read(new SetValue(((CollectionType) type).elemType(), elements));
	}

	private static Unreadable unreadable(TupleSet tuples) {
		return new Unreadable("cannot read the Kodkod result " + tuples);
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
