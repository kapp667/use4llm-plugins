package org.tzi.kodkod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import kodkod.ast.BinaryExpression;
import kodkod.ast.BinaryIntExpression;
import kodkod.ast.Comprehension;
import kodkod.ast.Decl;
import kodkod.ast.Expression;
import kodkod.ast.ExprToIntCast;
import kodkod.ast.Formula;
import kodkod.ast.IfExpression;
import kodkod.ast.IfIntExpression;
import kodkod.ast.IntConstant;
import kodkod.ast.IntExpression;
import kodkod.ast.IntToExprCast;
import kodkod.ast.NaryExpression;
import kodkod.ast.NaryIntExpression;
import kodkod.ast.Node;
import kodkod.ast.ProjectExpression;
import kodkod.ast.Relation;
import kodkod.ast.SumExpression;
import kodkod.ast.UnaryExpression;
import kodkod.ast.UnaryIntExpression;
import kodkod.ast.Variable;
import kodkod.ast.operator.ExprCastOperator;
import kodkod.ast.operator.ExprOperator;
import kodkod.ast.operator.IntCastOperator;
import kodkod.ast.operator.IntOperator;
import kodkod.ast.visitor.AbstractVoidVisitor;
import kodkod.instance.Bounds;
import kodkod.instance.Tuple;

/**
 * Computes the bitwidth that holds an interval for each integer expression
 * of a formula. The intervals come from the Integer atoms the bounds allow
 * in each relation and from the number of tuples each expression may hold;
 * they may be wider than the values the expressions take, never narrower
 * wherever a value can change the truth of the formula. It also returns the
 * Integer values that the formula may read as an atom and the bounds hold
 * no atom for.
 */
public final class IntegerRanges {

	/**
	 * The magnitude beyond which an interval counts as unbounded.
	 */
	private static final long LIMIT = 1L << 40;

	private final Bounds bounds;
	private final Set<IntExpression> singleReads;
	private final long[] universeIntegers;
	private final long universeSize;
	private final Map<Variable, Expression> declarations = new IdentityHashMap<Variable, Expression>();
	private final Map<Node, long[]> intervals = new IdentityHashMap<Node, long[]>();
	private final List<IntToExprCast> casts = new ArrayList<IntToExprCast>();
	private final List<Comprehension> comprehensions = new ArrayList<Comprehension>();
	private final Map<Expression, IntExpression[]> integerRanges;

	private IntegerRanges(Bounds bounds, Set<IntExpression> singleReads, Map<Expression, IntExpression[]> integerRanges) {
		this.bounds = bounds;
		this.singleReads = singleReads;
		this.integerRanges = integerRanges;
		universeSize = bounds.universe().size();
		long[] integers = null;
		for (Object atom : bounds.universe()) {
			if (atom instanceof Integer) {
				integers = union(integers, point((Integer) atom));
			}
		}
		universeIntegers = integers;
	}

	/**
	 * Returns the smallest bitwidth that holds the interval of every integer
	 * expression of the formula and every Integer atom of the bounds.
	 * singleReads holds the sums built over an expression that holds at most
	 * one atom wherever the sum can change the truth of the formula; each
	 * counts as one value.
	 */
	public static int requiredBitwidth(Formula formula, Bounds bounds, Set<IntExpression> singleReads,
			Map<Expression, IntExpression[]> integerRanges) {
		IntegerRanges ranges = new IntegerRanges(bounds, singleReads, integerRanges);
		int bitwidth = bitsFor(ranges.universeIntegers);
		for (IntExpression e : ranges.collect(formula)) {
			bitwidth = Math.max(bitwidth, bitsFor(ranges.interval(e)));
		}
		return bitwidth;
	}

	/**
	 * Returns the Integer values the bounds hold no atom for and the formula
	 * may read as an atom: the values of the interval of each integer cast to
	 * an atom and, for each range the formula holds, those from the lowest
	 * value its first end can take to the highest value its last end can
	 * take. integerRanges holds the ends of each range. Returns null when
	 * more than limit values are missing. Leaves out each interval that
	 * reaches beyond the int range: requiredBitwidth then returns a bitwidth
	 * above 32, which the solver refuses.
	 */
	public static Set<Integer> missingIntegers(Formula formula, Bounds bounds, Set<IntExpression> singleReads,
			Map<Expression, IntExpression[]> integerRanges, int limit) {
		IntegerRanges ranges = new IntegerRanges(bounds, singleReads, integerRanges);
		ranges.collect(formula);
		List<long[]> needed = new ArrayList<long[]>();
		for (IntToExprCast cast : ranges.casts) {
			needed.add(ranges.interval(cast.intExpr()));
		}
		for (Comprehension range : ranges.comprehensions) {
			if (integerRanges.containsKey(range) && ranges.rangeHull(range) != null) {
				needed.add(ranges.rangeHull(range));
			}
		}
		TreeSet<Long> atoms = new TreeSet<Long>();
		for (Object atom : bounds.universe()) {
			if (atom instanceof Integer) {
				atoms.add(Long.valueOf((Integer) atom));
			}
		}
		Set<Integer> missing = new TreeSet<Integer>();
		for (long[] interval : needed) {
			if (interval[0] > interval[1] || interval[0] < Integer.MIN_VALUE || interval[1] > Integer.MAX_VALUE) {
				continue;
			}
			long absent = interval[1] - interval[0] + 1 - atoms.subSet(interval[0], true, interval[1], true).size();
			if (absent > limit) {
				return null;
			}
			if (absent == 0) {
				continue;
			}
			for (long value = interval[0]; value <= interval[1]; value++) {
				if (!atoms.contains(value)) {
					missing.add((int) value);
				}
			}
			if (missing.size() > limit) {
				return null;
			}
		}
		return missing;
	}

	/**
	 * Returns the integer expressions of the formula, and records, once each,
	 * the declaration of each variable, each integer cast to an atom and each
	 * comprehension.
	 */
	private List<IntExpression> collect(Formula formula) {
		final List<IntExpression> integers = new ArrayList<IntExpression>();
		formula.accept(new AbstractVoidVisitor() {
			private final Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());

			@Override
			protected boolean visited(Node node) {
				return !seen.add(node);
			}

			@Override
			public void visit(Decl decl) {
				declarations.put(decl.variable(), decl.expression());
				super.visit(decl);
			}

			@Override
			public void visit(IntConstant e) {
				integers.add(e);
			}

			@Override
			public void visit(Comprehension e) {
				if (!seen.contains(e)) {
					comprehensions.add(e);
				}
				super.visit(e);
			}

			@Override
			public void visit(IntToExprCast e) {
				if (e.op() == IntCastOperator.INTCAST && !seen.contains(e)) {
					casts.add(e);
				}
				super.visit(e);
			}

			@Override
			public void visit(IfIntExpression e) {
				integers.add(e);
				super.visit(e);
			}

			@Override
			public void visit(ExprToIntCast e) {
				integers.add(e);
				super.visit(e);
			}

			@Override
			public void visit(NaryIntExpression e) {
				integers.add(e);
				super.visit(e);
			}

			@Override
			public void visit(BinaryIntExpression e) {
				integers.add(e);
				super.visit(e);
			}

			@Override
			public void visit(UnaryIntExpression e) {
				integers.add(e);
				super.visit(e);
			}

			@Override
			public void visit(SumExpression e) {
				integers.add(e);
				super.visit(e);
			}
		});
		return integers;
	}

	/**
	 * Returns an interval that holds every value of the integer expression
	 * that can change the truth of the formula.
	 */
	private long[] interval(IntExpression e) {
		long[] interval = intervals.get(e);
		if (interval == null) {
			interval = computeInterval(e);
			intervals.put(e, interval);
		}
		return interval;
	}

	private long[] computeInterval(IntExpression e) {
		if (e instanceof IntConstant) {
			return point(((IntConstant) e).value());
		}
		if (e instanceof ExprToIntCast) {
			ExprToIntCast cast = (ExprToIntCast) e;
			long size = size(cast.expression());
			if (cast.op() == ExprCastOperator.CARDINALITY) {
				return new long[] { 0, size };
			}
			long[] atoms = atoms(cast.expression());
			if (atoms == null) {
				return point(0);
			}
			long count = singleReads.contains(cast) ? 1 : Math.min(size, atoms[1] - atoms[0] + 1);
			return repeated(count, atoms);
		}
		if (e instanceof IfIntExpression) {
			IfIntExpression choice = (IfIntExpression) e;
			return union(interval(choice.thenExpr()), interval(choice.elseExpr()));
		}
		if (e instanceof BinaryIntExpression) {
			BinaryIntExpression binary = (BinaryIntExpression) e;
			return arithmetic(binary.op(), interval(binary.left()), interval(binary.right()));
		}
		if (e instanceof NaryIntExpression) {
			NaryIntExpression nary = (NaryIntExpression) e;
			long[] result = interval(nary.child(0));
			for (int i = 1; i < nary.size(); i++) {
				result = arithmetic(nary.op(), result, interval(nary.child(i)));
			}
			return result;
		}
		if (e instanceof UnaryIntExpression) {
			UnaryIntExpression unary = (UnaryIntExpression) e;
			long[] operand = interval(unary.intExpr());
			if (unary.op() == IntOperator.NEG) {
				return clamp(-operand[1], -operand[0]);
			}
			if (unary.op() == IntOperator.ABS) {
				long low = operand[0] <= 0 && operand[1] >= 0 ? 0 : Math.min(Math.abs(operand[0]), Math.abs(operand[1]));
				return clamp(low, magnitude(operand));
			}
			if (unary.op() == IntOperator.SGN) {
				return new long[] { -1, 1 };
			}
			return unbounded();
		}
		if (e instanceof SumExpression) {
			SumExpression sum = (SumExpression) e;
			long count = 1;
			for (Decl decl : sum.decls()) {
				count = multiply(count, size(decl.expression()));
			}
			return repeated(count, interval(sum.intExpr()));
		}
		return unbounded();
	}

	/**
	 * Returns an interval that holds the result of a binary integer operation
	 * on operands within the given intervals.
	 */
	private static long[] arithmetic(IntOperator op, long[] l, long[] r) {
		if (op == IntOperator.PLUS) {
			return clamp(l[0] + r[0], l[1] + r[1]);
		}
		if (op == IntOperator.MINUS) {
			return clamp(l[0] - r[1], l[1] - r[0]);
		}
		if (op == IntOperator.MULTIPLY) {
			long a = multiply(l[0], r[0]);
			long b = multiply(l[0], r[1]);
			long c = multiply(l[1], r[0]);
			long d = multiply(l[1], r[1]);
			return clamp(Math.min(Math.min(a, b), Math.min(c, d)), Math.max(Math.max(a, b), Math.max(c, d)));
		}
		if (op == IntOperator.DIVIDE) {
			long m = magnitude(l);
			return clamp(-m, m);
		}
		if (op == IntOperator.MODULO) {
			long m = Math.min(magnitude(l), magnitude(r));
			return clamp(-m, m);
		}
		return unbounded();
	}

	/**
	 * Returns an interval that holds the sum of at most count values of the
	 * given interval.
	 */
	private static long[] repeated(long count, long[] values) {
		return clamp(multiply(count, Math.min(values[0], 0)), multiply(count, Math.max(values[1], 0)));
	}

	/**
	 * Returns an interval that holds every Integer atom the expression may
	 * hold in any column, or null if it holds none.
	 */
	private long[] atoms(Expression e) {
		if (e instanceof Relation) {
			long[] result = null;
			for (Tuple tuple : bounds.upperBound((Relation) e)) {
				for (int i = 0; i < tuple.arity(); i++) {
					if (tuple.atom(i) instanceof Integer) {
						result = union(result, point((Integer) tuple.atom(i)));
					}
				}
			}
			return result;
		}
		if (e instanceof Variable) {
			Expression declaration = declarations.get(e);
			return declaration == null ? universeIntegers : atoms(declaration);
		}
		if (e == Expression.NONE) {
			return null;
		}
		if (e instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) e;
			if (binary.op() == ExprOperator.JOIN && binary.left().arity() == 1) {
				return atoms(binary.right());
			}
			if (binary.op() == ExprOperator.JOIN && binary.right().arity() == 1) {
				return atoms(binary.left());
			}
			return union(atoms(binary.left()), atoms(binary.right()));
		}
		if (e instanceof NaryExpression) {
			long[] result = null;
			for (Expression child : (NaryExpression) e) {
				result = union(result, atoms(child));
			}
			return result;
		}
		if (e instanceof UnaryExpression && ((UnaryExpression) e).op() != ExprOperator.REFLEXIVE_CLOSURE) {
			return atoms(((UnaryExpression) e).expression());
		}
		if (e instanceof ProjectExpression) {
			return atoms(((ProjectExpression) e).expression());
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			return union(atoms(choice.thenExpr()), atoms(choice.elseExpr()));
		}
		if (integerRanges.containsKey(e)) {
			return intersection(rangeHull((Comprehension) e), universeIntegers);
		}
		if (e instanceof Comprehension) {
			long[] result = null;
			for (Decl decl : ((Comprehension) e).decls()) {
				result = union(result, atoms(decl.expression()));
			}
			return result;
		}
		if (e instanceof IntToExprCast && ((IntToExprCast) e).op() == IntCastOperator.INTCAST) {
			return intersection(interval(((IntToExprCast) e).intExpr()), universeIntegers);
		}
		return universeIntegers;
	}

	/**
	 * Returns an upper bound on the number of tuples the expression may hold.
	 */
	private long size(Expression e) {
		long largest = power(universeSize, e.arity());
		if (e instanceof Relation) {
			return bounds.upperBound((Relation) e).size();
		}
		if (e instanceof Variable || (e instanceof IntToExprCast && ((IntToExprCast) e).op() == IntCastOperator.INTCAST)) {
			return 1;
		}
		if (e == Expression.NONE) {
			return 0;
		}
		if (e == Expression.INTS) {
			return bounds.ints().size();
		}
		if (e instanceof BinaryExpression) {
			BinaryExpression binary = (BinaryExpression) e;
			long combined = combine(binary.op(), size(binary.left()), size(binary.right()));
			if (binary.op() == ExprOperator.JOIN) {
				combined = Math.min(combined, multiply(ends(binary.left(), false), ends(binary.right(), true)));
			}
			return Math.min(largest, combined);
		}
		if (e instanceof NaryExpression) {
			NaryExpression nary = (NaryExpression) e;
			long result = size(nary.child(0));
			for (int i = 1; i < nary.size(); i++) {
				result = combine(nary.op(), result, size(nary.child(i)));
			}
			return Math.min(largest, result);
		}
		if (e instanceof UnaryExpression) {
			UnaryExpression unary = (UnaryExpression) e;
			return unary.op() == ExprOperator.TRANSPOSE ? size(unary.expression()) : largest;
		}
		if (e instanceof ProjectExpression) {
			return Math.min(largest, size(((ProjectExpression) e).expression()));
		}
		if (e instanceof IfExpression) {
			IfExpression choice = (IfExpression) e;
			return Math.max(size(choice.thenExpr()), size(choice.elseExpr()));
		}
		if (integerRanges.containsKey(e)) {
			long[] hull = rangeHull((Comprehension) e);
			return hull == null ? 0 : Math.min(bounds.ints().size(), hull[1] - hull[0] + 1);
		}
		if (e instanceof Comprehension) {
			long result = 1;
			for (Decl decl : ((Comprehension) e).decls()) {
				result = multiply(result, size(decl.expression()));
			}
			return Math.min(largest, result);
		}
		return largest;
	}

	/**
	 * Returns an upper bound on the number of distinct tuples of the
	 * expression once its first column (first is true) or its last column
	 * (first is false) is dropped. A join of a and b holds at most
	 * ends(a, false) * ends(b, true) tuples.
	 */
	private long ends(Expression e, boolean first) {
		if (e.arity() == 1) {
			return 1;
		}
		if (!(e instanceof Relation)) {
			return size(e);
		}
		Set<List<Object>> ends = new HashSet<List<Object>>();
		for (Tuple tuple : bounds.upperBound((Relation) e)) {
			List<Object> end = new ArrayList<Object>();
			for (int i = first ? 1 : 0; i < (first ? tuple.arity() : tuple.arity() - 1); i++) {
				end.add(tuple.atom(i));
			}
			ends.add(end);
		}
		return ends.size();
	}

	/**
	 * Returns an upper bound on the size of a binary relational operation on
	 * operands of the given sizes.
	 */
	private static long combine(ExprOperator op, long l, long r) {
		if (op == ExprOperator.UNION || op == ExprOperator.OVERRIDE) {
			return Math.min(LIMIT, l + r);
		}
		if (op == ExprOperator.INTERSECTION) {
			return Math.min(l, r);
		}
		if (op == ExprOperator.DIFFERENCE) {
			return l;
		}
		return multiply(l, r);
	}

	private static long[] point(long value) {
		return new long[] { value, value };
	}

	private static long[] unbounded() {
		return new long[] { -LIMIT, LIMIT };
	}

	private static long[] clamp(long low, long high) {
		return new long[] { Math.max(-LIMIT, low), Math.min(LIMIT, high) };
	}

	private static long[] union(long[] a, long[] b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return new long[] { Math.min(a[0], b[0]), Math.max(a[1], b[1]) };
	}

	/**
	 * Returns the interval from the lowest value the first end of the range
	 * can take to the highest value its last end can take, null when it holds
	 * none.
	 */
	private long[] rangeHull(Comprehension range) {
		IntExpression[] ends = integerRanges.get(range);
		long first = interval(ends[0])[0];
		long last = interval(ends[1])[1];
		return first > last ? null : new long[] { first, last };
	}

	private static long[] intersection(long[] a, long[] b) {
		if (a == null || b == null || a[1] < b[0] || b[1] < a[0]) {
			return null;
		}
		return new long[] { Math.max(a[0], b[0]), Math.min(a[1], b[1]) };
	}

	private static long magnitude(long[] interval) {
		return Math.max(Math.abs(interval[0]), Math.abs(interval[1]));
	}

	private static long multiply(long a, long b) {
		if (a == 0 || b == 0) {
			return 0;
		}
		if (Math.abs(a) > LIMIT / Math.abs(b)) {
			return (a < 0) == (b < 0) ? LIMIT : -LIMIT;
		}
		return a * b;
	}

	private static long power(long base, int exponent) {
		long result = 1;
		for (int i = 0; i < exponent; i++) {
			result = multiply(result, base);
		}
		return result;
	}

	/**
	 * Returns the smallest two's complement bitwidth that holds the interval.
	 */
	private static int bitsFor(long[] interval) {
		if (interval == null) {
			return 1;
		}
		int bits = 1;
		while (interval[0] < -(1L << (bits - 1)) || interval[1] > (1L << (bits - 1)) - 1) {
			bits++;
		}
		return bits;
	}
}
