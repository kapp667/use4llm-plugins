package org.tzi.use.plugin.tla;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MClassifier;
import org.tzi.use.uml.mm.MNavigableElement;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.ocl.expr.*;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.Type;

/**
 * Translates USE's compiled OCL expressions into TLA+ expressions over a state value s
 * (a record with objs, cls, att and lnk, see TlaCommand). Every construct is either translated
 * with USE's evaluation semantics or refused with a Refusal naming the construct.
 */
final class Translator {

	static final class Refusal extends RuntimeException {
		private static final long serialVersionUID = 1L;
		final String construct;

		Refusal(String construct, String detail) {
			super(construct + (detail.isEmpty() ? "" : ": " + detail));
			this.construct = construct;
		}
	}

	/** The TLA+ representation family of a USE type. */
	enum K { BOOL, INT, STR, ENUM, OBJ, VOID, SET, BAG, SEQ, OSET }

	/** A deliberate translation bug (-Dtla.mutant=name), to show the comparison can fail. */
	static final String MUTANT = System.getProperty("tla.mutant", "");

	private int counter = 0;
	/** The TLA+ names of the state an expression reads, and of the state @pre reads (null outside a
	 * postcondition). Invariants read s; an operation's guard, body and postconditions read others. */
	String cur = "s", pre = null;
	/** Inside operations: a call on an object that does not exist in the state it reads is undefined
	 * (ExpObjOp.eval). Invariants keep the translation the corpus comparison checked. */
	boolean live = false;

	/** The state expression e reads. */
	private String st(Expression e) {
		if (!e.isPre()) return cur;
		if (pre == null) throw new Refusal("@pre outside a postcondition", e.toString());
		return pre;
	}
	private final Deque<Object> inlining = new ArrayDeque<>();
	/** A translation unit, an invariant or a query operation: the stored attributes it reads, as
	 * Owner::name, and the operations it calls. */
	static final class Unit {
		final Set<String> attrs = new TreeSet<>();
		final Set<String> constructs = new TreeSet<>();
		final Set<MOperation> calls = new java.util.LinkedHashSet<>();
	}

	private final Deque<Unit> units = new ArrayDeque<>();
	private final Map<MOperation, Unit> opUnits = new LinkedHashMap<>();
	private final Map<MOperation, String> opNames = new LinkedHashMap<>();
	/** TLA+ definitions of the query operations translated so far, in translation order. */
	final Map<MOperation, String> opDefs = new LinkedHashMap<>();
	private final Map<MOperation, Refusal> opRefused = new HashMap<>();

	Unit begin() {
		Unit u = new Unit();
		units.push(u);
		return u;
	}

	void end() {
		units.pop();
	}

	/** The stored attributes a unit reads, through the operations it calls too. */
	Set<String> reads(Unit u) {
		return closure(u, false);
	}

	/** The OCL constructs a unit translates, through the operations it calls too. */
	Set<String> constructs(Unit u) {
		return closure(u, true);
	}

	private Set<String> closure(Unit u, boolean constructs) {
		Set<String> res = new TreeSet<>(constructs ? u.constructs : u.attrs);
		Deque<MOperation> todo = new ArrayDeque<>(u.calls);
		Set<MOperation> seen = new java.util.HashSet<>();
		while (!todo.isEmpty()) {
			MOperation op = todo.pop();
			if (!seen.add(op) || !opUnits.containsKey(op)) continue;
			res.addAll(constructs ? opUnits.get(op).constructs : opUnits.get(op).attrs);
			todo.addAll(opUnits.get(op).calls);
		}
		return res;
	}

	String fresh(String p) {
		return p + "_" + (++counter);
	}

	// ---------------------------------------------------------------- types

	static K kind(Type t) {
		if (t.isTypeOfVoidType()) return K.VOID;
		if (t.isTypeOfBoolean()) return K.BOOL;
		if (t.isTypeOfInteger()) return K.INT;
		if (t.isTypeOfReal()) throw new Refusal("Real", "TLC has no reals (" + t + ")");
		if (t.isTypeOfUnlimitedNatural()) throw new Refusal("UnlimitedNatural", t.toString());
		if (t.isTypeOfString()) return K.STR;
		if (t.isTypeOfEnum()) return K.ENUM;
		if (t.isTypeOfClass()) return K.OBJ;
		if (t.isTypeOfTupleType()) throw new Refusal("Tuple", t.toString());
		if (t.isTypeOfOclAny()) throw new Refusal("OclAny-typed expression", t.toString());
		if (t.isTypeOfDataType()) throw new Refusal("DataType", t.toString());
		if (t.isTypeOfSet() || t.isTypeOfBag() || t.isTypeOfSequence() || t.isTypeOfOrderedSet()) {
			kind(((CollectionType) t).elemType());
			if (t.isTypeOfSet()) return K.SET;
			if (t.isTypeOfBag()) return K.BAG;
			if (t.isTypeOfSequence()) return K.SEQ;
			return K.OSET;
		}
		if (t.isTypeOfCollection()) throw new Refusal("Collection(T)-typed expression", t.toString());
		throw new Refusal("type " + t.getClass().getSimpleName(), t.toString());
	}

	static boolean isColl(K k) {
		return k == K.SET || k == K.BAG || k == K.SEQ || k == K.OSET;
	}

	static boolean isSeq(K k) {
		return k == K.SEQ || k == K.OSET;
	}

	private static Type elemType(Type t) {
		return ((CollectionType) t).elemType();
	}

	/** TLA+ set of the elements of collection c of kind k. */
	static String elems(K k, String c) {
		switch (k) {
		case SET: return c;
		case BAG: return "DOMAIN " + c;
		default: return "SeqElems(" + c + ")";
		}
	}

	static String quote(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (char ch : s.toCharArray()) {
			if (ch == '"' || ch == '\\') sb.append('\\').append(ch);
			else if (ch == '\n') sb.append("\\n");
			else if (ch == '\t') sb.append("\\t");
			else if (ch < 32 || ch > 126) throw new Refusal("non-ASCII string", s);
			else sb.append(ch);
		}
		return sb.append('"').toString();
	}

	static String classSet(MClassifier c) {
		Set<String> names = new TreeSet<>();
		names.add(c.name());
		if (c instanceof MClass) for (MClass d : ((MClass) c).allChildren()) names.add(d.name());
		StringBuilder sb = new StringBuilder("{");
		for (String n : names) {
			if (sb.length() > 1) sb.append(", ");
			sb.append(quote(n));
		}
		return sb.append("}").toString();
	}

	// ---------------------------------------------------------------- invariants

	/** Overall value of the invariant as USE's check computes it (flagged expression). */
	String invariant(MClassInvariant inv) {
		Expression e = inv.flaggedExpression();
		return tr(e, new HashMap<>());
	}

	/** Value of the body for one object, the invariant's single variable bound to o. */
	String body(MClassInvariant inv, String o) {
		Map<String, String> env = new HashMap<>();
		env.put(inv.vars().varDecl(0).name(), o);
		String b = tr(inv.bodyExpression(), env);
		return inv.isNegated() ? "BNot(" + b + ")" : b;
	}

	// ---------------------------------------------------------------- expressions

	private Map<String, String> bind(Map<String, String> env, String var, String tla) {
		Map<String, String> m = new HashMap<>(env);
		m.put(var, tla);
		return m;
	}

	String tr(Expression e, Map<String, String> env) {
		kind(e.type());
		units.peek().constructs.add(e.getClass().getSimpleName() + (e instanceof ExpStdOp ? ":" + ((ExpStdOp) e).opname() : ""));
		if (e instanceof ExpConstBoolean) return ((ExpConstBoolean) e).value() ? "TRUE" : "FALSE";
		if (e instanceof ExpConstInteger) {
			int v = ((ExpConstInteger) e).value();
			return v < 0 ? "(" + v + ")" : Integer.toString(v);
		}
		if (e instanceof ExpConstString) return quote(((ExpConstString) e).value());
		if (e instanceof ExpConstEnum) return quote(((EnumType) e.type()).name() + "::" + ((ExpConstEnum) e).value());
		if (e instanceof ExpUndefined) return "Undef";
		if (e instanceof ExpVariable) {
			String n = ((ExpVariable) e).getVarname();
			String v = env.get(n);
			if (v == null) throw new Refusal("free variable", n);
			return v;
		}
		if (e instanceof ExpLet) {
			ExpLet l = (ExpLet) e;
			String v = fresh("let");
			return "(LET " + v + " == " + tr(l.getVarExpression(), env) + " IN "
					+ tr(l.getInExpression(), bind(env, l.getVarname(), v)) + ")";
		}
		if (e instanceof ExpIf) {
			ExpIf i = (ExpIf) e;
			String c = fresh("c");
			return "(LET " + c + " == " + tr(i.getCondition(), env) + " IN IF " + c + " = TRUE THEN "
					+ tr(i.getThenExpression(), env) + (MUTANT.equals("if") ? " ELSE " + tr(i.getElseExpression(), env) + ")"
							: " ELSE IF " + c + " = FALSE THEN " + tr(i.getElseExpression(), env) + " ELSE Undef)");
		}
		if (e instanceof ExpAttrOp) return attr((ExpAttrOp) e, env);
		if (e instanceof ExpNavigation) return navigation((ExpNavigation) e, env);
		if (e instanceof ExpNavigationClassifierSource) {
			// From a link object to the object at one of its ends (MSystemState.getNavigableObject).
			ExpNavigationClassifierSource n = (ExpNavigationClassifierSource) e;
			MNavigableElement dst = n.getDestination();
			MAssociation as = dst.association();
			if (!(dst instanceof MAssociationEnd) || !(as instanceof MAssociationClass) || as.isOrdered())
				throw new Refusal("ExpNavigationClassifierSource", e.toString());
			String o = fresh("o"), l = fresh("l"), x = fresh("x");
			int last = as.associationEnds().size() + 1;
			return "(LET " + o + " == " + tr(n.getObjectExpression(), env) + " IN IF " + o + " = Undef THEN Undef ELSE CHOOSE "
					+ x + " \\in {" + l + "[" + index(as, dst) + "] : " + l + " \\in {" + l + " \\in " + st(e) + ".lnk[" + quote(as.name())
					+ "] : " + l + "[" + last + "] = " + o + "}} : TRUE)";
		}
		if (e instanceof ExpAllInstances) {
			MClassifier c = ((ExpAllInstances) e).getSourceType();
			if (!(c instanceof MClass)) throw new Refusal("allInstances of a non-class", c.name());
			String o = fresh("o");
			return "{" + o + " \\in " + st(e) + ".objs : " + st(e) + ".cls[" + o + "] \\in " + classSet(c) + "}";
		}
		if (e instanceof ExpIsKindOf || e instanceof ExpIsTypeOf || e instanceof ExpAsType) return typeTest(e, env);
		if (e instanceof ExpObjAsSet) {
			String o = fresh("o");
			return "(LET " + o + " == " + tr(((ExpObjAsSet) e).getObjectExpression(), env) + " IN IF " + o
					+ " = Undef THEN {} ELSE {" + o + "})";
		}
		if (e instanceof ExpEmptyCollection) {
			K k = kind(e.type());
			return k == K.SET ? "{}" : k == K.BAG ? "EmptyBag" : "<<>>";
		}
		if (e instanceof ExpCollectionLiteral) return literal((ExpCollectionLiteral) e, env);
		if (e instanceof ExpForAll || e instanceof ExpExists) return quantifier((ExpQuery) e, env, e instanceof ExpExists);
		if (e instanceof ExpSelect || e instanceof ExpReject) return select((ExpQuery) e, env, e instanceof ExpSelect);
		if (e instanceof ExpCollect) return collect((ExpCollect) e, env);
		if (e instanceof ExpIsUnique) return isUnique((ExpQuery) e, env);
		if (e instanceof ExpOne) return one((ExpQuery) e, env);
		if (e instanceof ExpAny) return any((ExpQuery) e, env);
		if (e instanceof ExpClosure) return closure((ExpQuery) e, env);
		if (e instanceof ExpObjOp) return call((ExpObjOp) e, env);
		if (e instanceof ExpStdOp) return stdOp((ExpStdOp) e, env);
		throw new Refusal(e.getClass().getSimpleName(), e.toString());
	}

	// ---------------------------------------------------------------- features

	private String attr(ExpAttrOp a, Map<String, String> env) {
		MAttribute at = a.attr();
		String o = fresh("o");
		String src = tr(a.objExp(), env);
		if (at.isDerived()) {
			if (inlining.contains(at)) throw new Refusal("recursive derived attribute", at.toString());
			inlining.push(at);
			String saved = cur;
			cur = st(a); // a derived attribute read @pre is evaluated in the pre-state (ExpAttrOp.eval)
			try {
				String self = fresh("self");
				String body = tr(at.getDeriveExpression(), bind(new HashMap<>(), "self", self));
				return "(LET " + o + " == " + src + " IN IF " + o + " = Undef THEN Undef ELSE LET " + self + " == " + o
						+ " IN " + body + ")";
			} finally {
				inlining.pop();
				cur = saved;
			}
		}
		units.peek().attrs.add(at.owner().name() + "::" + at.name());
		return "(LET " + o + " == " + src + " IN IF " + o + " = Undef THEN Undef ELSE " + st(a) + ".att["
				+ quote(at.owner().name() + "::" + at.name()) + "][" + o + "])";
	}

	/** Index (1-based) of a navigable element in the link tuples of its association. */
	private static int index(MAssociation as, MNavigableElement n) {
		if (n instanceof MAssociationEnd) return as.associationEnds().indexOf(n) + 1;
		if (n instanceof MAssociationClass) return as.associationEnds().size() + 1;
		throw new Refusal("navigation end " + n.getClass().getSimpleName(), n.toString());
	}

	private String navigation(ExpNavigation n, Map<String, String> env) {
		MNavigableElement src = n.getSource(), dst = n.getDestination();
		MAssociation as = dst.association();
		if (n.getQualifierExpression() != null && n.getQualifierExpression().length > 0 || dst.hasQualifiers()
				|| src.hasQualifiers())
			throw new Refusal("qualified association", as.name());
		if (dst.isDerived() || src.isDerived() || as.isDerived()) throw new Refusal("derived association end", as.name());
		if (as.isUnion() || dst.isUnion() || !as.getSubsets().isEmpty() || !as.getSubsettedBy().isEmpty()
				|| !as.getRedefinedBy().isEmpty() || !as.getRedefines().isEmpty() || !dst.getSubsettingEnds().isEmpty()
				|| !dst.getRedefiningEnds().isEmpty())
			throw new Refusal("subsets/union/redefines association", as.name());
		if (!as.allChildren().isEmpty() || !as.allParents().isEmpty())
			throw new Refusal("association generalization", as.name());
		K rk = kind(n.type());
		if (rk != K.OBJ && rk != K.SET && !(rk == K.OSET && as.isOrdered() && as.associationEnds().size() == 2))
			throw new Refusal("navigation yielding " + rk, as.name() + " to " + dst.nameAsRolename() + " : " + n.type());
		int si = index(as, src), di = index(as, dst);
		String o = fresh("o"), l = fresh("l"), t = fresh("t"), x = fresh("x"), m = fresh("m"), i = fresh("i");
		String links = st(n) + ".lnk[" + quote(as.name()) + "]";
		String targets;
		if (as.isOrdered()) {
			// The links of an association with an ordered end are a sequence in USE's insertion order.
			String sel = "SelectSeq(" + links + ", LAMBDA " + l + " : " + l + "[" + si + "] = " + o + ")";
			if (rk == K.OSET)
				return "(LET " + o + " == " + tr(n.getObjectExpression(), env) + " IN IF " + o + " = Undef THEN Undef ELSE LET "
						+ m + " == " + sel + " IN [" + i + " \\in 1..Len(" + m + ") |-> " + m + "[" + i + "][" + di + "]])";
			targets = "{" + l + "[" + di + "] : " + l + " \\in {" + l + " \\in SeqElems(" + links + ") : " + l + "[" + si
					+ "] = " + o + "}}";
		} else
			targets = "{" + l + "[" + di + "] : " + l + " \\in {" + l + " \\in " + links + " : " + l + "[" + si + "] = " + o
					+ "}}";
		String body;
		if (rk == K.OBJ)
			body = "LET " + t + " == " + targets + " IN IF " + t + " = {} THEN Undef ELSE IF Cardinality(" + t
					+ ") > 1 THEN Assert(FALSE, " + quote("multiplicity violation at end " + dst.nameAsRolename())
					+ ") ELSE CHOOSE " + x + " \\in " + t + " : TRUE";
		else
			body = targets;
		return "(LET " + o + " == " + tr(n.getObjectExpression(), env) + " IN IF " + o + " = Undef THEN "
				+ (MUTANT.equals("navundef") && rk == K.SET ? "{}" : "Undef") + " ELSE " + body + ")";
	}

	private String typeTest(Expression e, Map<String, String> env) {
		Expression src;
		Type target;
		if (e instanceof ExpIsKindOf) {
			src = ((ExpIsKindOf) e).getSourceExpr();
			target = ((ExpIsKindOf) e).getTargetType();
		} else if (e instanceof ExpIsTypeOf) {
			src = ((ExpIsTypeOf) e).getSourceExpr();
			target = ((ExpIsTypeOf) e).getTargetType();
		} else {
			src = ((ExpAsType) e).getSourceExpr();
			target = ((ExpAsType) e).getTargetType();
		}
		String name = e.getClass().getSimpleName();
		if (kind(src.type()) != K.OBJ || !target.isTypeOfClass())
			throw new Refusal(name + " on non-object", src.type() + " to " + target);
		String o = fresh("o");
		String v = tr(src, env);
		String desc = classSet((MClass) target);
		if (e instanceof ExpIsKindOf) // USE: an undefined value has type OclVoid, which conforms to every type
			return "(LET " + o + " == " + v + " IN IF " + o + " = Undef THEN " + (MUTANT.equals("kindof") ? "FALSE" : "TRUE")
					+ " ELSE " + st(e) + ".cls[" + o + "] \\in " + desc + ")";
		if (e instanceof ExpIsTypeOf)
			return "(LET " + o + " == " + v + " IN IF " + o + " = Undef THEN FALSE ELSE " + st(e) + ".cls[" + o + "] = "
					+ quote(((MClass) target).name()) + ")";
		return "(LET " + o + " == " + v + " IN IF " + o + " # Undef /\\ " + st(e) + ".cls[" + o + "] \\in " + desc + " THEN " + o
				+ " ELSE Undef)";
	}

	private String literal(ExpCollectionLiteral l, Map<String, String> env) {
		K k = kind(l.type());
		if (k != K.SET && k != K.SEQ) throw new Refusal(k + " literal", l.toString());
		List<String> parts = new ArrayList<>();
		List<String> single = new ArrayList<>();
		for (Expression x : l.getElemExpr()) {
			if (x instanceof ExpRange) {
				// USE: an undefined bound makes the range a single undefined element.
				if (!single.isEmpty()) parts.add(k == K.SET ? "{" + String.join(", ", single) + "}" : "<<" + String.join(", ", single) + ">>");
				single.clear();
				ExpRange r = (ExpRange) x;
				String a = fresh("a"), b = fresh("b"), i = fresh("i");
				String range = k == K.SET ? a + ".." + b : "[" + i + " \\in 1..(" + b + " - " + a + " + 1) |-> " + a + " + " + i + " - 1]";
				parts.add("(LET " + a + " == " + tr(r.getStart(), env) + " " + b + " == " + tr(r.getEnd(), env) + " IN IF " + a
						+ " = Undef \\/ " + b + " = Undef THEN " + (k == K.SET ? "{Undef}" : "<<Undef>>") + " ELSE " + range + ")");
			} else
				single.add(tr(x, env));
		}
		if (!single.isEmpty() || parts.isEmpty())
			parts.add(k == K.SET ? "{" + String.join(", ", single) + "}" : "<<" + String.join(", ", single) + ">>");
		return "(" + String.join(k == K.SET ? " \\cup " : " \\o ", parts) + ")";
	}

	// ---------------------------------------------------------------- iterators

	private ExpQuery checkVars(ExpQuery q, int max) {
		int n = q.getVariableDeclarations().size();
		if (n == 0) throw new Refusal("iterator without variable", q.toString());
		if (n > max) throw new Refusal(q.name() + " with " + n + " variables", q.toString());
		return q;
	}

	private String quantifier(ExpQuery q, Map<String, String> env, boolean exists) {
		checkVars(q, 9);
		Expression range = q.getRangeExpression();
		K k = kind(range.type());
		String r = fresh("r");
		StringBuilder sb = new StringBuilder("(LET " + r + " == " + tr(range, env) + " IN IF " + r + " = Undef THEN Undef ELSE ");
		Map<String, String> inner = env;
		for (VarDecl d : q.getVariableDeclarations()) {
			String v = fresh("v");
			inner = bind(inner, d.name(), v);
			sb.append(exists ? "\\E " : "\\A ").append(v).append(" \\in ").append(elems(k, r)).append(" : ");
		}
		sb.append("(").append(tr(q.getQueryExpression(), inner)).append(!exists && MUTANT.equals("forall") ? ") # FALSE)" : ") = TRUE)");
		return sb.toString();
	}

	private String select(ExpQuery q, Map<String, String> env, boolean doSelect) {
		checkVars(q, 1);
		K k = kind(q.getRangeExpression().type());
		String r = fresh("r"), v = fresh("v"), y = fresh("y");
		String p = "(" + tr(q.getQueryExpression(), bind(env, q.getVariableDeclarations().varDecl(0).name(), v)) + ")"
				+ (doSelect ? (MUTANT.equals("select") ? " # FALSE" : " = TRUE") : " # TRUE");
		String res;
		switch (k) {
		case SET: res = "{" + v + " \\in " + r + " : " + p + "}"; break;
		case BAG: res = "[" + y + " \\in {" + v + " \\in DOMAIN " + r + " : " + p + "} |-> " + r + "[" + y + "]]"; break;
		default: res = "SelectSeq(" + r + ", LAMBDA " + v + " : " + p + ")";
		}
		return "(LET " + r + " == " + tr(q.getRangeExpression(), env) + " IN IF " + r + " = Undef THEN Undef ELSE " + res + ")";
	}

	private String collect(ExpCollect q, Map<String, String> env) {
		checkVars(q, 1);
		K k = kind(q.getRangeExpression().type());
		Type bt = q.getQueryExpression().type();
		K bk = kind(bt);
		String r = fresh("r"), v = fresh("v"), f = fresh("f"), w = fresh("w"), x = fresh("x"), i = fresh("i");
		String body = tr(q.getQueryExpression(), bind(env, q.getVariableDeclarations().varDecl(0).name(), v));
		String head = "(LET " + r + " == " + tr(q.getRangeExpression(), env) + " IN IF " + r + " = Undef THEN Undef ELSE ";
		if (isSeq(k)) {
			if (isColl(bk)) throw new Refusal("flattening collect on a sequence", q.toString());
			return head + "[" + i + " \\in 1..Len(" + r + ") |-> LET " + v + " == " + r + "[" + i + "] IN " + body + "])";
		}
		String dom = elems(k, r);
		String weight = k == K.SET ? "[" + x + " \\in " + dom + " |-> 1]" : r;
		String asBag;
		if (isColl(bk)) // USE flattens one level; an undefined body value is added as an element
			asBag = "IF " + f + "[" + x + "] = Undef THEN (Undef :> 1) ELSE " + toBag(bk, f + "[" + x + "]");
		else
			asBag = "(" + f + "[" + x + "] :> 1)";
		if (MUTANT.equals("collect") && k == K.SET && !isColl(bk)) // a set, not a bag
			return head + "LET " + f + " == TLCEval([" + v + " \\in " + dom + " |-> " + body + "]) IN SetToBag({" + f + "[" + x + "] : " + x
					+ " \\in " + dom + "}))";
		// TLCEval: TLC applies a function constructor lazily, re-evaluating the body at each application.
		return head + "LET " + f + " == TLCEval([" + v + " \\in " + dom + " |-> " + body + "]) " + w + " == " + weight
				+ " IN BagSumW(" + dom + ", TLCEval([" + x + " \\in " + dom + " |-> " + asBag + "]), " + w + "))";
	}

	private static String toBag(K k, String c) {
		switch (k) {
		case SET: return "SetToBag(" + c + ")";
		case BAG: return c;
		default: return "SeqToBag(" + c + ")";
		}
	}

	private String isUnique(ExpQuery q, Map<String, String> env) {
		checkVars(q, 1);
		K k = kind(q.getRangeExpression().type());
		String r = fresh("r"), v = fresh("v"), f = fresh("f"), a = fresh("a"), b = fresh("b");
		String body = tr(q.getQueryExpression(), bind(env, q.getVariableDeclarations().varDecl(0).name(), v));
		String head = "(LET " + r + " == " + tr(q.getRangeExpression(), env) + " IN IF " + r + " = Undef THEN Undef ELSE ";
		if (isSeq(k))
			return head + "LET " + f + " == TLCEval([" + a + " \\in 1..Len(" + r + ") |-> LET " + v + " == " + r + "[" + a + "] IN "
					+ body + "]) IN \\A " + a + ", " + b + " \\in 1..Len(" + r + ") : " + a + " # " + b + " => " + f + "[" + a
					+ "] # " + f + "[" + b + "])";
		String dom = elems(k, r);
		String inj = "\\A " + a + ", " + b + " \\in " + dom + " : " + a + " # " + b + " => " + f + "[" + a + "] # " + f + "["
				+ b + "]";
		if (k == K.BAG) inj = "(\\A " + a + " \\in " + dom + " : " + r + "[" + a + "] = 1) /\\ " + inj;
		return head + "LET " + f + " == TLCEval([" + v + " \\in " + dom + " |-> " + body + "]) IN " + inj + ")";
	}

	private String one(ExpQuery q, Map<String, String> env) {
		checkVars(q, 1);
		K k = kind(q.getRangeExpression().type());
		String r = fresh("r"), v = fresh("v"), i = fresh("i");
		String body = tr(q.getQueryExpression(), bind(env, q.getVariableDeclarations().varDecl(0).name(), v));
		String head = "(LET " + r + " == " + tr(q.getRangeExpression(), env) + " IN IF " + r + " = Undef THEN Undef ELSE ";
		switch (k) {
		case SET: return head + "Cardinality({" + v + " \\in " + r + " : (" + body + ") = TRUE}) = 1)";
		case BAG: return head + "SumF({" + v + " \\in DOMAIN " + r + " : (" + body + ") = TRUE}, " + r + ") = 1)";
		default: return head + "Cardinality({" + i + " \\in 1..Len(" + r + ") : LET " + v + " == " + r + "[" + i + "] IN ("
				+ body + ") = TRUE}) = 1)";
		}
	}

	private String any(ExpQuery q, Map<String, String> env) {
		checkVars(q, 1);
		K k = kind(q.getRangeExpression().type());
		if (!isSeq(k)) // USE returns the first match in the iteration order of a hash set
			throw new Refusal("any on an unordered collection", q.toString());
		String r = fresh("r"), v = fresh("v"), i = fresh("i"), j = fresh("j"), m = fresh("m");
		String body = tr(q.getQueryExpression(), bind(env, q.getVariableDeclarations().varDecl(0).name(), v));
		return "(LET " + r + " == " + tr(q.getRangeExpression(), env) + " IN IF " + r + " = Undef THEN Undef ELSE LET " + m
				+ " == {" + i + " \\in 1..Len(" + r + ") : LET " + v + " == " + r + "[" + i + "] IN (" + body + ") = TRUE} IN IF "
				+ m + " = {} THEN Undef ELSE " + r + "[CHOOSE " + i + " \\in " + m + " : \\A " + j + " \\in " + m + " : " + i
				+ " <= " + j + "])";
	}

	private String closure(ExpQuery q, Map<String, String> env) {
		checkVars(q, 1);
		K k = kind(q.getRangeExpression().type());
		Type et = elemType(q.getRangeExpression().type());
		if (k != K.SET || kind(et) != K.OBJ) throw new Refusal("closure on " + k + " of " + kind(et), q.toString());
		K bk = kind(q.getQueryExpression().type());
		String r = fresh("r"), v = fresh("v"), f = fresh("f"), j = fresh("j"), b = fresh("b");
		String body = tr(q.getQueryExpression(), bind(env, q.getVariableDeclarations().varDecl(0).name(), v));
		String objs = pre == null ? cur + ".objs" : "(" + cur + ".objs \\cup " + pre + ".objs)";
		// The body's value is added as one element, or element-wise if it is a collection; an
		// undefined value is an element (USE's evalClosureAux). The result holds the range itself.
		String step = isColl(bk) ? "LET " + b + " == " + body + " IN IF " + b + " = Undef THEN {Undef} ELSE " + elems(bk, b)
				: "{" + body + "}";
		return "(LET " + r + " == " + tr(q.getRangeExpression(), env) + " IN IF " + r + " = Undef THEN Undef ELSE LET " + f + "["
				+ j + " \\in 0..(Cardinality(" + objs + ") + 1)] == IF " + j + " = 0 THEN " + r + " ELSE LET " + b + "_prev == " + f + "["
				+ j + " - 1] IN " + b + "_prev \\cup UNION {" + step + " : " + v + " \\in " + b + "_prev} IN " + f
				+ "[Cardinality(" + objs + ") + 1])";
	}

	// ---------------------------------------------------------------- query operations

	private String call(ExpObjOp c, Map<String, String> env) {
		if (!c.isPre()) return callIn(c, env);
		String saved = cur;
		cur = st(c);
		try {
			return callIn(c, env);
		} finally {
			cur = saved;
		}
	}

	private String callIn(ExpObjOp c, Map<String, String> env) {
		MOperation op = c.getOperation();
		Expression[] args = c.getArguments();
		if (kind(args[0].type()) != K.OBJ) throw new Refusal("operation call on non-object", op.toString());
		MClass stat = (MClass) args[0].type();
		// USE dispatches on the runtime class: group the classes conforming to the static one by the
		// operation they resolve to.
		Map<MOperation, Set<String>> impls = new LinkedHashMap<>();
		List<MClass> classes = new ArrayList<>();
		classes.add(stat);
		classes.addAll(stat.allChildren());
		for (MClass cl : classes) {
			if (cl.isAbstract()) continue;
			MOperation r = cl.operation(op.name(), true);
			impls.computeIfAbsent(r, z -> new TreeSet<>()).add(cl.name());
		}
		String o = fresh("o");
		List<String> names = new ArrayList<>();
		names.add(cur);
		names.add(o);
		StringBuilder lets = new StringBuilder();
		for (int i = 1; i < args.length; i++) {
			String p = fresh("p");
			names.add(p);
			lets.append(p).append(" == ").append(tr(args[i], env)).append(" ");
		}
		String argList = "(" + String.join(", ", names) + ")";
		List<String> branches = new ArrayList<>();
		for (Map.Entry<MOperation, Set<String>> en : impls.entrySet()) {
			StringBuilder set = new StringBuilder("{");
			for (String n : en.getValue()) set.append(set.length() > 1 ? ", " : "").append(quote(n));
			branches.add(cur + ".cls[" + o + "] \\in " + set + "} -> " + operation(en.getKey()) + argList);
		}
		if (branches.isEmpty()) throw new Refusal("operation call on a class without concrete subclass", op.toString());
		String dispatch = branches.size() == 1 ? branches.get(0).substring(branches.get(0).indexOf("-> ") + 3)
				: "CASE " + String.join(" [] ", branches);
		return "(LET " + o + " == " + tr(args[0], env) + " " + lets + "IN IF " + o + " = Undef" + (live ? " \\/ " + o + " \\notin " + cur + ".objs" : "")
				+ " THEN Undef ELSE " + dispatch + ")";
	}

	/** The name of the TLA+ operator of a query operation, translating it on first use. */
	private String operation(MOperation r) {
		units.peek().calls.add(r);
		if (opNames.containsKey(r)) return opNames.get(r);
		if (opRefused.containsKey(r)) throw opRefused.get(r);
		String name = "Op_" + (opNames.size() + opRefused.size()) + "_" + r.name();
		List<MOperation> before = new ArrayList<>(opNames.keySet());
		opNames.put(r, name);
		Unit u = new Unit();
		opUnits.put(r, u);
		units.push(u);
		String savedCur = cur, savedPre = pre;
		cur = "s";
		pre = null;
		try {
			if (!r.hasExpression()) throw new Refusal("operation without OCL body (SOIL or none)", r.toString());
			if (!r.preConditions().isEmpty() || !r.postConditions().isEmpty())
				throw new Refusal("query operation with pre/postconditions", r.toString());
			Map<String, String> inner = new HashMap<>();
			inner.put("self", name + "_self");
			List<String> params = new ArrayList<>();
			params.add("s");
			params.add(name + "_self");
			List<String> pn = r.paramNames();
			for (int i = 0; i < pn.size(); i++) {
				inner.put(pn.get(i), name + "_p" + i);
				params.add(name + "_p" + i);
			}
			String body = tr(r.expression(), inner);
			opDefs.put(r, "\\* " + r.cls().name() + "::" + r.name() + "\nRECURSIVE " + name + "("
					+ String.join(", ", java.util.Collections.nCopies(params.size(), "_")) + ")\n" + name + "("
					+ String.join(", ", params) + ") == " + body + "\n");
			return name;
		} catch (Refusal ex) {
			// Operations translated within this one may call it: drop them, a later use translates them again.
			for (MOperation m : new ArrayList<>(opNames.keySet()))
				if (!before.contains(m)) {
					opNames.remove(m);
					opDefs.remove(m);
					opUnits.remove(m);
				}
			opRefused.put(r, ex);
			throw ex;
		} finally {
			units.pop();
			cur = savedCur;
			pre = savedPre;
		}
	}

	// ---------------------------------------------------------------- standard operations

	/** Strict application: undefined if any argument is undefined, as USE's OPERATION kind. */
	private String strict(Expression[] args, Map<String, String> env, java.util.function.Function<String[], String> f) {
		String[] n = new String[args.length];
		StringBuilder sb = new StringBuilder("(LET ");
		StringBuilder test = new StringBuilder();
		for (int i = 0; i < args.length; i++) {
			n[i] = fresh("a");
			sb.append(n[i]).append(" == ").append(tr(args[i], env)).append(" ");
			test.append(i == 0 ? "" : " \\/ ").append(n[i]).append(" = Undef");
		}
		return sb.append("IN IF ").append(test).append(" THEN Undef ELSE ").append(f.apply(n)).append(")").toString();
	}

	private void comparable(Expression a, Expression b, String op) {
		K ka = kind(a.type()), kb = kind(b.type());
		if (ka == K.VOID || kb == K.VOID) return;
		if (ka != kb) throw new Refusal("'" + op + "' between " + ka + " and " + kb, a.type() + " vs " + b.type());
		if (!a.type().conformsTo(b.type()) && !b.type().conformsTo(a.type()))
			throw new Refusal("'" + op + "' between unrelated types", a.type() + " vs " + b.type());
	}

	private String stdOp(ExpStdOp e, Map<String, String> env) {
		String op = e.opname();
		Expression[] a = e.args();
		K k0 = a.length > 0 ? kind(a[0].type()) : null;
		K k1 = a.length > 1 ? kind(a[1].type()) : null;
		switch (op) {
		case "and": return "BAnd(" + tr(a[0], env) + ", " + tr(a[1], env) + ")";
		case "or": return "BOr(" + tr(a[0], env) + ", " + tr(a[1], env) + ")";
		case "implies": return "BImp(" + tr(a[0], env) + ", " + tr(a[1], env) + ")";
		case "xor": return "BXor(" + tr(a[0], env) + ", " + tr(a[1], env) + ")";
		case "not": return "BNot(" + tr(a[0], env) + ")";
		case "=":
			comparable(a[0], a[1], op);
			if (MUTANT.equals("eq")) return strict(a, env, n -> n[0] + " = " + n[1]);
			return "(" + tr(a[0], env) + " = " + tr(a[1], env) + ")";
		case "<>":
			comparable(a[0], a[1], op);
			return "(" + tr(a[0], env) + " # " + tr(a[1], env) + ")";
		case "isDefined": return "(" + tr(a[0], env) + " # Undef)";
		case "isUndefined":
		case "oclIsUndefined": return "(" + tr(a[0], env) + " = Undef)";
		default:
		}
		if (a.length == 2 && k0 == K.INT && k1 == K.INT) {
			switch (op) {
			case "+": return strict(a, env, n -> n[0] + " + " + n[1]);
			case "-": return strict(a, env, n -> n[0] + " - " + n[1]);
			case "*": return strict(a, env, n -> n[0] + " * " + n[1]);
			case "max": return strict(a, env, n -> "IF " + n[0] + " >= " + n[1] + " THEN " + n[0] + " ELSE " + n[1]);
			case "min": return strict(a, env, n -> "IF " + n[0] + " <= " + n[1] + " THEN " + n[0] + " ELSE " + n[1]);
			case "div": return strict(a, env, n -> "IF " + n[1] + " = 0 THEN Undef ELSE JDiv(" + n[0] + ", " + n[1] + ")");
			case "mod": return strict(a, env, n -> "IF " + n[1] + " = 0 THEN Undef ELSE JMod(" + n[0] + ", " + n[1] + ")");
			case "<": return strict(a, env, n -> n[0] + " < " + n[1]);
			case ">": return strict(a, env, n -> n[0] + " > " + n[1]);
			case "<=": return strict(a, env, n -> n[0] + " <= " + n[1]);
			case ">=": return strict(a, env, n -> n[0] + " >= " + n[1]);
			default:
			}
		}
		if (a.length == 1 && k0 == K.INT) {
			switch (op) {
			case "-": return strict(a, env, n -> "-" + n[0]);
			case "+": return strict(a, env, n -> n[0]);
			case "abs": return strict(a, env, n -> "IAbs(" + n[0] + ")");
			default:
			}
		}
		if (k0 == K.STR) {
			if ((op.equals("+") || op.equals("concat")) && k1 == K.STR) return strict(a, env, n -> n[0] + " \\o " + n[1]);
			if (op.equals("size") && a.length == 1) return strict(a, env, n -> "Len(" + n[0] + ")");
			throw new Refusal("String operation " + op, e.toString());
		}
		if (k0 != null && isColl(k0)) return collOp(e, op, a, k0, k1, env);
		throw new Refusal("operation " + op + (k0 == null ? "" : " on " + k0), e.toString());
	}

	private String collOp(ExpStdOp e, String op, Expression[] a, K k, K k1, Map<String, String> env) {
		String c = fresh("c");
		String head = "(LET " + c + " == " + tr(a[0], env) + " IN ";
		switch (op) {
		case "size":
			return strict(a, env, n -> size(k, n[0]));
		case "isEmpty":
			return strict(a, env, n -> size(k, n[0]) + " = 0");
		case "notEmpty":
			return strict(a, env, n -> size(k, n[0]) + " # 0");
		case "includes":
		case "excludes": {
			String x = fresh("x");
			String mem = member(k, c, x);
			return head + "LET " + x + " == " + tr(a[1], env) + " IN IF " + c + " = Undef THEN "
					+ (MUTANT.equals("includes") ? "Undef" : "FALSE") + " ELSE " + (op.equals("includes") ? mem : "~(" + mem + ")") + ")";
		}
		case "count": {
			String x = fresh("x"), i = fresh("i");
			String cnt = k == K.SET ? "IF " + x + " \\in " + c + " THEN 1 ELSE 0"
					: k == K.BAG ? "BagCount(" + c + ", " + x + ")"
							: "Cardinality({" + i + " \\in 1..Len(" + c + ") : " + c + "[" + i + "] = " + x + "})";
			return head + "LET " + x + " == " + tr(a[1], env) + " IN IF " + c + " = Undef THEN 0 ELSE " + cnt + ")";
		}
		case "includesAll":
		case "excludesAll": {
			String y = fresh("y");
			return strict(a, env, n -> "\\A " + y + " \\in " + elems(k1, n[1]) + " : "
					+ (op.equals("includesAll") ? member(k, n[0], y) : "~(" + member(k, n[0], y) + ")"));
		}
		case "sum": {
			if (kind(elemType(a[0].type())) != K.INT) throw new Refusal("sum over non-Integer", e.toString());
			String z = fresh("z");
			return strict(a, env, n -> "IF Undef \\in " + elems(k, n[0]) + " THEN Undef ELSE "
					+ (k == K.SET ? "SumF(" + n[0] + ", [" + z + " \\in " + n[0] + " |-> " + z + "])"
							: k == K.BAG ? "SumF(DOMAIN " + n[0] + ", [" + z + " \\in DOMAIN " + n[0] + " |-> " + z + " * " + n[0] + "[" + z + "]])"
									: "SumF(1..Len(" + n[0] + "), " + n[0] + ")"));
		}
		case "asSet":
			return strict(a, env, n -> elems(k, n[0]));
		case "asBag":
			return strict(a, env, n -> toBag(k, n[0]));
		case "including":
		case "excluding": {
			// SPECIAL in USE: undefined only when the collection is; an undefined element is a value.
			String x = fresh("x"), y = fresh("y");
			boolean inc = op.equals("including");
			String res;
			if (k == K.SET) res = c + (inc ? " \\cup {" : " \\ {") + x + "}";
			else if (k == K.BAG && inc) res = "BagPlus(" + c + ", (" + x + " :> 1))";
			else if (k == K.SEQ && inc) res = "Append(" + c + ", " + x + ")";
			else if (k == K.SEQ) res = "SelectSeq(" + c + ", LAMBDA " + y + " : " + y + " # " + x + ")";
			else throw new Refusal(op + " on " + k, e.toString());
			return head + "LET " + x + " == " + tr(a[1], env) + " IN IF " + c + " = Undef"
					+ (MUTANT.equals("excluding") ? " \\/ " + x + " = Undef" : "") + " THEN Undef ELSE " + res + ")";
		}
		case "append":
		case "prepend": {
			if (k != K.SEQ) throw new Refusal(op + " on " + k, e.toString());
			String x = fresh("x");
			return head + "LET " + x + " == " + tr(a[1], env) + " IN IF " + c + " = Undef THEN Undef ELSE "
					+ (op.equals("append") ? "Append(" + c + ", " + x + ")" : "<<" + x + ">> \\o " + c) + ")";
		}
		case "union":
			if (k == K.SET && k1 == K.SET) return strict(a, env, n -> n[0] + " \\cup " + n[1]);
			if ((k == K.BAG || k == K.SET) && (k1 == K.BAG || k1 == K.SET))
				return strict(a, env, n -> "BagPlus(" + toBag(k, n[0]) + ", " + toBag(k1, n[1]) + ")");
			throw new Refusal("union on " + k + " and " + k1, e.toString());
		case "intersection":
			if (k == K.SET && k1 == K.SET) return strict(a, env, n -> n[0] + " \\cap " + n[1]);
			throw new Refusal("intersection on " + k + " and " + k1, e.toString());
		case "-":
			if (k == K.SET && k1 == K.SET) return strict(a, env, n -> n[0] + " \\ " + n[1]);
			throw new Refusal("- on " + k, e.toString());
		case "symmetricDifference":
			if (k == K.SET && k1 == K.SET)
				return strict(a, env, n -> "(" + n[0] + " \\ " + n[1] + ") \\cup (" + n[1] + " \\ " + n[0] + ")");
			throw new Refusal("symmetricDifference on " + k, e.toString());
		case "at":
			if (isSeq(k) && k1 == K.INT) // out of range: USE catches the IndexOutOfBoundsException
				return strict(a, env, n -> "IF " + n[1] + " >= 1 /\\ " + n[1] + " <= Len(" + n[0] + ") THEN " + n[0] + "[" + n[1]
						+ "] ELSE Undef");
			break;
		case "flatten":
			if (!isColl(kind(elemType(a[0].type()))))
				return strict(a, env, n -> n[0]); // the elements are no collections: USE returns the collection itself
			break;
		case "first":
			if (isSeq(k)) return strict(a, env, n -> "IF Len(" + n[0] + ") = 0 THEN Undef ELSE " + n[0] + "[1]");
			break;
		case "last":
			if (isSeq(k)) return strict(a, env, n -> "IF Len(" + n[0] + ") = 0 THEN Undef ELSE " + n[0] + "[Len(" + n[0] + ")]");
			break;
		default:
		}
		throw new Refusal("collection operation " + op + " on " + k, e.toString());
	}

	private static String size(K k, String c) {
		return k == K.SET ? "Cardinality(" + c + ")" : k == K.BAG ? "SumF(DOMAIN " + c + ", " + c + ")" : "Len(" + c + ")";
	}

	private static String member(K k, String c, String x) {
		return x + " \\in " + elems(k, c);
	}
}
