package org.tzi.use.plugin.tla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tzi.use.uml.mm.MAggregationKind;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MMultiplicity;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.mm.MPrePostCondition;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.sys.soil.*;

/**
 * Translates the operations of a USE model with SOIL bodies, under a Settings environment, into a
 * TLA+ specification TLC can check. The state is one record st = [objs, cls, att, lnk] in the
 * shape Translator reads; last records the step that led to it, with the state before the call
 * (pre) that @pre reads. A SOIL body is a function from a configuration (the state and the SOIL
 * variables) to the configuration after it, or Fail when USE would roll the call back; whatever a
 * statement does not assign keeps its value. A precondition is a guard; a postcondition is an
 * invariant over (last.pre, st). Every construct is translated with USE's semantics or refused.
 */
final class Ops {

	private final MModel model;
	private final Settings set;
	private final Translator tr = new Translator();

	/** kind, name, status, construct, detail: one row per step, operation, invariant, postcondition, multiplicity. */
	final List<String[]> report = new ArrayList<>();
	/** TLC invariant operator -> what it checks. */
	final Map<String, String> checks = new LinkedHashMap<>();

	private final Map<MClass, List<String>> pools = new LinkedHashMap<>();
	/** Stored attribute key Owner::name -> attribute, for the attributes the state holds. */
	private final Map<String, MAttribute> attrs = new TreeMap<>();
	private final Map<String, MAssociation> assocs = new TreeMap<>();
	private final StringBuilder defs = new StringBuilder();
	private final Map<MOperation, String> done = new LinkedHashMap<>();
	private final Map<MOperation, Translator.Refusal> refused = new HashMap<>();

	Ops(MModel model, Settings set) {
		this.model = model;
		this.set = set;
		tr.live = true;
	}

	static String id(String s) {
		return s.replaceAll("[^A-Za-z0-9_]", "_");
	}

	/** A fresh TLA+ name, from the counter the expression translator uses too. */
	private String fresh(String p) {
		return tr.fresh(p);
	}

	private static String q(String s) {
		return Translator.quote(s);
	}

	// ---------------------------------------------------------------- the module

	String module(String name) {
		Map<String, MClass> byName = new TreeMap<>();
		for (MClass c : model.classes()) byName.put(c.name(), c);
		for (String c : set.objects.keySet())
			if (!byName.containsKey(c)) throw new IllegalArgumentException("settings: no class " + c);
		List<String> consts = new ArrayList<>();
		for (MClass c : byName.values()) {
			int n = set.objects.getOrDefault(c.name(), 0);
			if (n == 0) continue;
			if (c.isAbstract() || c instanceof MAssociationClass)
				throw new IllegalArgumentException("settings: objects of " + c.name() + ", an abstract class or an association class");
			List<String> p = new ArrayList<>();
			for (int i = 1; i <= n; i++) p.add("O_" + c.name() + i);
			pools.put(c, p);
			consts.addAll(p);
		}
		StringBuilder m = new StringBuilder("---- MODULE " + name + " ----\n");
		m.append(TlaCommand.prelude()).append('\n');
		m.append("\\* Fail: the call is rolled back (a failed precondition, or a statement USE refuses).\n");
		m.append("\\* Unset: the result variable before the body assigns it.\nCONSTANTS Fail, Unset\n");
		if (!consts.isEmpty()) m.append("\\* The object pools, one per class (settings: objects).\nCONSTANTS ").append(String.join(", ", consts)).append('\n');
		for (Map.Entry<MClass, List<String>> e : pools.entrySet())
			m.append("Pool_").append(e.getKey().name()).append(" == {").append(String.join(", ", e.getValue())).append("}\n");
		m.append("AllObjs == ").append(pools.isEmpty() ? "{}" : String.join(" \\cup ", poolNames(pools.keySet()))).append('\n');
		StringBuilder cls = new StringBuilder();
		for (Map.Entry<MClass, List<String>> e : pools.entrySet())
			cls.append(cls.length() == 0 ? "" : " @@ ").append("[o \\in Pool_").append(e.getKey().name()).append(" |-> ").append(q(e.getKey().name())).append("]");
		m.append("ClassOf == ").append(cls.length() == 0 ? "[o \\in {} |-> \"\"]" : cls).append("\n\n");

		// The state: the stored attributes of the classes with a pool, the plain associations.
		for (MClass c : model.classes())
			for (MAttribute a : c.attributes()) {
				if (a.isDerived() || pool(c).isEmpty()) continue;
				try {
					Translator.kind(a.type());
				} catch (Translator.Refusal r) {
					report.add(row("attribute", c.name() + "::" + a.name(), "not in state", r.construct, r.getMessage()));
					continue;
				}
				attrs.put(c.name() + "::" + a.name(), a);
			}
		for (MAssociation as : model.associations()) {
			String why = plainAssociation(as);
			if (why == null) assocs.put(as.name(), as);
			else report.add(row("association", as.name(), "not in state", why, ""));
		}
		StringBuilder att = new StringBuilder();
		for (Map.Entry<String, MAttribute> e : attrs.entrySet())
			att.append(att.length() == 0 ? "" : " @@ ").append(q(e.getKey())).append(" :> [o \\in ").append(poolExpr((MClass) e.getValue().owner()))
					.append(" |-> Undef]");
		StringBuilder lnk = new StringBuilder();
		for (String a : assocs.keySet()) lnk.append(lnk.length() == 0 ? "" : " @@ ").append(q(a)).append(" :> {}");
		m.append("InitState == [objs |-> {}, cls |-> ClassOf, att |-> ").append(att.length() == 0 ? "[x \\in {} |-> 0]" : "(" + att + ")")
				.append(", lnk |-> ").append(lnk.length() == 0 ? "[x \\in {} |-> {}]" : "(" + lnk + ")").append("]\n\n");

		// Creation, with init expressions, and the value domains.
		StringBuilder creation = new StringBuilder();
		for (MClass c : pools.keySet()) {
			try {
				creation.append(newObject(c));
			} catch (Translator.Refusal r) {
				refusedNew.put(c, r);
			}
		}

		// Steps.
		List<String> next = new ArrayList<>();
		StringBuilder actions = new StringBuilder();
		for (Settings.Step s : set.steps) {
			try {
				String[] a = step(s);
				actions.append(a[0]);
				next.add(a[1]);
				report.add(row("step", s.toString().substring(5), "translated", "", ""));
			} catch (Translator.Refusal r) {
				report.add(row("step", s.toString().substring(5), "refused", r.construct, r.getMessage()));
			}
		}
		for (Map.Entry<MOperation, String> e : done.entrySet()) report.add(row("operation", qn(e.getKey()), "translated", "", ""));
		for (Map.Entry<MOperation, Translator.Refusal> e : refused.entrySet())
			report.add(row("operation", qn(e.getKey()), "refused", e.getValue().construct, e.getValue().getMessage()));

		// Invariants, multiplicities and postconditions, checked where the settings say.
		String mark = mark();
		StringBuilder inv = new StringBuilder();
		List<MClassInvariant> invs = new ArrayList<>();
		for (MClassInvariant i : model.getClassInvariantsIncludingImports()) if (i.isActive()) invs.add(i);
		for (MClassInvariant i : invs) {
			String n = "Inv_" + id(i.cls().name() + "_" + i.name());
			if (set.skip.contains(i.qualifiedName())) {
				report.add(row("invariant", i.qualifiedName(), "skipped", "settings", ""));
				continue;
			}
			Translator.Unit u = tr.begin();
			try {
				tr.cur = "s";
				tr.pre = null;
				String body = tr.invariant(i);
				inv.append("\\* ").append(i.qualifiedName()).append('\n').append(n).append("_value(s) == ").append(body).append('\n');
				inv.append(n).append(" == ").append(mark).append(n).append("_value(st) = TRUE\n\n");
				checks.put(n, "invariant " + i.qualifiedName());
				report.add(row("invariant", i.qualifiedName(), "translated", "", ""));
			} catch (Translator.Refusal r) {
				report.add(row("invariant", i.qualifiedName(), "refused", r.construct, r.getMessage()));
			} finally {
				tr.end();
			}
		}
		for (MAssociation as : model.associations()) multiplicity(as, mark, inv);
		for (MOperation op : done.keySet()) {
			inv.append(postText.get(op));
			for (String[] c : postChecks.get(op)) {
				if (c[0] != null) checks.put(c[0], "postcondition " + c[1]);
				report.add(row("postcondition", c[1], c[0] == null ? "skipped" : "translated", c[0] == null ? "settings" : "", ""));
			}
		}

		m.append(tr.opDefs.isEmpty() ? "" : "\\* Query operations.\n");
		for (String d : tr.opDefs.values()) m.append(d).append('\n');
		m.append(creation).append(defs);
		m.append("VARIABLES st, last\nvars == <<st, last>>\n\n");
		m.append("Init == st = InitState /\\ last = [step |-> \"none\", op |-> \"none\"]\n\n");
		m.append(actions);
		m.append("Next ==\n").append(next.isEmpty() ? "  FALSE /\\ UNCHANGED vars  \\* no step translated\n" : "  \\/ " + String.join("\n  \\/ ", next) + "\n");
		m.append("\nSpec == Init /\\ [][Next]_vars\n\n").append(inv);
		String bound = bound();
		if (bound != null) m.append("\\* Settings: bound. States beyond it are not explored.\nBound == ").append(bound).append("\n\n");
		if (set.symmetry && !pools.isEmpty())
			m.append("Sym == ").append(String.join(" \\cup ", perms())).append("\n\n");
		m.append("====\n");
		cfg = cfg(consts, bound != null);
		return m.toString();
	}

	String cfg;
	private final Map<MClass, Translator.Refusal> refusedNew = new HashMap<>();

	private String cfg(List<String> consts, boolean bound) {
		StringBuilder c = new StringBuilder("CONSTANT Undef = Undef\nCONSTANT Fail = Fail\nCONSTANT Unset = Unset\n");
		for (String o : consts) c.append("CONSTANT ").append(o).append(" = ").append(o).append('\n');
		c.append("INIT Init\nNEXT Next\n");
		for (String n : checks.keySet()) c.append("INVARIANT ").append(n).append('\n');
		if (bound) c.append("CONSTRAINT Bound\n");
		if (set.symmetry && !pools.isEmpty()) c.append("SYMMETRY Sym\n");
		c.append("CHECK_DEADLOCK FALSE\n");
		return c.toString();
	}

	private List<String> perms() {
		List<String> l = new ArrayList<>();
		for (MClass c : pools.keySet()) l.add("Permutations(Pool_" + c.name() + ")");
		return l;
	}

	private static List<String> poolNames(Set<MClass> cs) {
		List<String> l = new ArrayList<>();
		for (MClass c : cs) l.add("Pool_" + c.name());
		return l;
	}

	private static String[] row(String kind, String name, String status, String construct, String detail) {
		return new String[] { kind, name, status, construct, detail.replace('\t', ' ').replace('\n', ' ') };
	}

	private static String qn(MOperation op) {
		return op.cls().name() + "::" + op.name();
	}

	/** The pool objects of c and of its subclasses. */
	private List<String> pool(MClass c) {
		List<String> l = new ArrayList<>();
		for (Map.Entry<MClass, List<String>> e : pools.entrySet())
			if (e.getKey() == c || e.getKey().isSubClassifierOf(c)) l.addAll(e.getValue());
		return l;
	}

	private String poolExpr(MClass c) {
		return "{" + String.join(", ", pool(c)) + "}";
	}

	/** null when the association is one whose links are a set of tuples the actions can change; else why not. */
	private static String plainAssociation(MAssociation as) {
		if (as instanceof MAssociationClass) return "association class";
		if (as.hasQualifiedEnds()) return "qualified association";
		if (as.isOrdered()) return "ordered association";
		if (as.isDerived()) return "derived association";
		if (as.isUnion() || !as.getSubsets().isEmpty() || !as.getSubsettedBy().isEmpty() || !as.getRedefinedBy().isEmpty()
				|| !as.getRedefines().isEmpty())
			return "subsets/union/redefines association";
		if (!as.allChildren().isEmpty() || !as.allParents().isEmpty()) return "association generalization";
		for (MAssociationEnd e : as.associationEnds())
			if (e.isDerived() || e.isUnion() || !e.getSubsettingEnds().isEmpty() || !e.getRedefiningEnds().isEmpty())
				return "subsets/union/redefines association";
		if (as.aggregationKind() != MAggregationKind.NONE) return "aggregation or composition";
		return null;
	}

	private String mark() {
		switch (set.invariants) {
		case "every-state": return "";
		case "after-calls": return "last.step # \"new\" => ";
		default:
			List<String> ops = new ArrayList<>();
			for (String o : set.after) ops.add(q(o));
			return "(last.step = \"none\" \\/ last.op \\in {" + String.join(", ", ops) + "}) => ";
		}
	}

	// ---------------------------------------------------------------- multiplicities

	/** USE's structure check of a binary association (MSystemState.validateBinaryAssociations). */
	private void multiplicity(MAssociation as, String mark, StringBuilder inv) {
		List<MAssociationEnd> ends = as.associationEnds();
		if (!assocs.containsKey(as.name()) || ends.size() != 2) {
			for (MAssociationEnd e : ends)
				report.add(row("multiplicity", as.name() + "::" + e.nameAsRolename(), "refused",
						assocs.containsKey(as.name()) ? "multiplicity of an n-ary association" : "structure check of a non-plain association", ""));
			return;
		}
		for (int k = 0; k < 2; k++) {
			MAssociationEnd from = ends.get(k), to = ends.get(1 - k);
			String key = as.name() + "::" + to.nameAsRolename();
			if (set.skip.contains(key)) {
				report.add(row("multiplicity", key, "skipped", "settings", ""));
				continue;
			}
			String n = "Mult_" + id(as.name() + "_" + to.nameAsRolename());
			List<String> ranges = new ArrayList<>();
			for (MMultiplicity.Range r : to.multiplicity().getRanges())
				ranges.add(r.getUpper() == MMultiplicity.MANY ? "n >= " + r.getLower() : "(n >= " + r.getLower() + " /\\ n <= " + r.getUpper() + ")");
			inv.append("\\* ").append(as.name()).append(": each ").append(from.cls().name()).append(" has ").append(to.multiplicity())
					.append(" ").append(to.nameAsRolename()).append("\n");
			inv.append(n).append(" == ").append(mark).append("\\A o \\in {x \\in st.objs : st.cls[x] \\in ").append(Translator.classSet(from.cls()))
					.append("} :\n  LET n == Cardinality({l[").append(2 - k).append("] : l \\in {l \\in st.lnk[").append(q(as.name()))
					.append("] : l[").append(k + 1).append("] = o}}) IN ").append(String.join(" \\/ ", ranges)).append("\n\n");
			checks.put(n, "multiplicity " + key);
			report.add(row("multiplicity", key, "translated", "", ""));
		}
	}

	// ---------------------------------------------------------------- expressions

	private String expr(Expression e, String state, String preState, Map<String, String> env) {
		String sc = tr.cur, sp = tr.pre;
		tr.cur = state;
		tr.pre = preState;
		try {
			return tr.tr(e, env);
		} finally {
			tr.cur = sc;
			tr.pre = sp;
		}
	}

	/** The environment of the SOIL variables of configuration c. */
	private static Map<String, String> envOf(String c, Set<String> vars) {
		Map<String, String> env = new HashMap<>();
		for (String v : vars) env.put(v, c + ".v_" + v);
		return env;
	}

	// ---------------------------------------------------------------- objects

	/** New_C(s, p): s with p, a fresh object of C, its attributes undefined or set by their init
	 * expressions in model order, each evaluated in the state the previous ones left (MObjectState.initialize). */
	private String newObject(MClass c) {
		if (!c.getAllOwnedProtocolStateMachines().isEmpty()) throw new Translator.Refusal("class with a protocol state machine", c.name());
		List<MAttribute> init = new ArrayList<>();
		for (MAttribute a : c.allAttributes()) if (a.getInitExpression().isPresent()) init.add(a);
		init.sort((x, y) -> Integer.compare(x.getPositionInModel(), y.getPositionInModel()));
		StringBuilder b = new StringBuilder("\\* new " + c.name() + "\nNew_" + c.name() + "(s, p) ==\n  LET s0 == [s EXCEPT !.objs = @ \\cup {p}] IN\n");
		String prev = "s0";
		Translator.Unit u = tr.begin();
		try {
			for (MAttribute a : init) {
				String key = ((MClass) a.owner()).name() + "::" + a.name();
				if (!attrs.containsKey(key)) throw new Translator.Refusal("init of an attribute the state does not hold", key);
				Map<String, String> env = new HashMap<>();
				env.put("self", "p");
				String v = expr(a.getInitExpression().get(), prev, null, env);
				String nx = fresh("s");
				b.append("  LET ").append(nx).append(" == [").append(prev).append(" EXCEPT !.att[").append(q(key)).append("][p] = ").append(v)
						.append("] IN\n");
				prev = nx;
			}
		} finally {
			tr.end();
		}
		return b.append("  ").append(prev).append("\n\n").toString();
	}

	// ---------------------------------------------------------------- steps

	private String[] step(Settings.Step s) {
		MClass c = model.getClass(s.cls);
		if (c == null) throw new Translator.Refusal("settings: no class", s.cls);
		if (pool(c).isEmpty()) throw new Translator.Refusal("class without objects", s.cls);
		String tag = id(s.kind + "_" + s.cls + (s.op == null ? "" : "_" + s.op));
		if (s.kind.equals("new")) {
			if (refusedNew.containsKey(c)) throw refusedNew.get(c);
			if (!pools.containsKey(c)) throw new Translator.Refusal("new of a class without pool", c.name());
			String a = "\\* step new " + c.name() + "\nStep_" + tag + "(p) ==\n  /\\ st' = New_" + c.name() + "(st, p)\n"
					+ "  /\\ last' = [step |-> \"new\", op |-> " + q("new " + c.name()) + ", self |-> p, args |-> <<>>, pre |-> st]\n\n";
			return new String[] { a, "\\E p \\in Pool_" + c.name() + " \\ st.objs : Step_" + tag + "(p)" };
		}
		MOperation op = c.operation(s.op, true);
		if (op == null) throw new Translator.Refusal("settings: no operation", s.cls + "::" + s.op);
		if (s.kind.equals("new-call") && !pools.containsKey(c)) throw new Translator.Refusal("new of a class without pool", c.name());
		if (s.kind.equals("new-call") && refusedNew.containsKey(c)) throw refusedNew.get(c);
		// USE runs the operation the runtime class resolves (MObjectOperationCallStatement).
		List<MClass> classes = new ArrayList<>(s.kind.equals("new-call") ? List.of(c) : concrete(c));
		Map<MOperation, Set<String>> impls = new LinkedHashMap<>();
		for (MClass k : classes) impls.computeIfAbsent(k.operation(s.op, true), z -> new TreeSet<>()).add(k.name());
		for (MOperation impl : impls.keySet()) operation(impl);
		List<String> ps = new ArrayList<>(op.paramNames());
		List<String> doms = new ArrayList<>();
		for (int i = 0; i < ps.size(); i++) doms.add(domain(op, i, s.kind.equals("new-call") ? "s1" : "st"));
		String args = ps.isEmpty() ? "" : ", " + String.join(", ", argNames(ps));
		StringBuilder disp = new StringBuilder();
		for (Map.Entry<MOperation, Set<String>> e : impls.entrySet()) {
			StringBuilder set = new StringBuilder();
			for (String n : e.getValue()) set.append(set.length() == 0 ? "" : ", ").append(q(n));
			disp.append(disp.length() == 0 ? "" : " [] ").append("s.cls[self] \\in {").append(set).append("} -> [op |-> ").append(q(qn(e.getKey())))
					.append(", r |-> Run_").append(done.get(e.getKey())).append("(s, self").append(args).append(")]");
		}
		String call = "\\* step " + s.toString().substring(5) + ": the call on self in state s, the operation its class resolves\nCall_" + tag
				+ "(s, self" + args + ") == CASE " + disp + "\n";
		String rec = ", self |-> self, args |-> <<" + String.join(", ", argNames(ps)) + ">>";
		String out = outRecord(impls.keySet());
		String a, quant;
		if (s.kind.equals("call")) {
			a = call + "Step_" + tag + "(self" + args + ") ==\n  LET x == Call_" + tag + "(st, self" + args + ") IN\n  /\\ x.r # Fail\n  /\\ st' = x.r.s\n"
					+ "  /\\ last' = [step |-> \"call\", op |-> x.op" + rec + ", pre |-> st" + out + "]\n\n";
			StringBuilder qf = new StringBuilder("\\E self \\in {o \\in st.objs : st.cls[o] \\in " + Translator.classSet(c) + "}");
			for (int i = 0; i < ps.size(); i++) qf.append(", ").append(argNames(ps).get(i)).append(" \\in ").append(doms.get(i));
			quant = qf + " : Step_" + tag + "(self" + args + ")";
		} else {
			a = call + "Step_" + tag + "(p" + args + ") ==\n  LET s1 == New_" + c.name() + "(st, p) IN\n  LET x == Call_" + tag + "(s1, p" + args
					+ ") IN\n  /\\ x.r # Fail\n  /\\ st' = x.r.s\n  /\\ last' = [step |-> \"new+call\", op |-> x.op"
					+ rec.replace("self |-> self", "self |-> p") + ", pre |-> s1" + out + "]\n\n";
			StringBuilder qf = new StringBuilder("\\E p \\in Pool_" + c.name() + " \\ st.objs : LET s1 == New_" + c.name() + "(st, p) IN \\E ");
			if (ps.isEmpty()) quant = "\\E p \\in Pool_" + c.name() + " \\ st.objs : Step_" + tag + "(p)";
			else {
				for (int i = 0; i < ps.size(); i++) qf.append(i == 0 ? "" : ", ").append(argNames(ps).get(i)).append(" \\in ").append(doms.get(i));
				quant = qf + " : Step_" + tag + "(p" + args + ")";
			}
		}
		return new String[] { a, quant };
	}

	private static List<String> argNames(List<String> ps) {
		List<String> l = new ArrayList<>();
		for (String p : ps) l.add("a_" + p);
		return l;
	}

	private List<MClass> concrete(MClass c) {
		List<MClass> l = new ArrayList<>();
		for (MClass k : pools.keySet()) if (k == c || k.isSubClassifierOf(c)) l.add(k);
		return l;
	}

	/** The values the environment passes for parameter i of op, in state s (settings: param, domain). */
	private String domain(MOperation op, int i, String s) {
		VarDecl d = op.paramList().varDecl(i);
		Type t = d.type();
		List<String> vals = set.params.get(op.cls().name() + "::" + op.name() + "." + d.name());
		String tn = t.isTypeOfClass() ? ((MClass) t).name() : t.toString();
		if (vals == null) vals = set.domains.get(tn);
		if (vals == null) {
			if (t.isTypeOfBoolean()) vals = List.of("true", "false");
			else if (t.isTypeOfEnum()) {
				vals = new ArrayList<>();
				for (String l : ((EnumType) t).getLiterals()) vals.add("#" + l);
			} else throw new Translator.Refusal("no domain for a parameter of type " + tn, qn(op) + "(" + d.name() + ")");
		}
		return values(t, vals, s);
	}

	/** A TLA+ set of the values listed for type t. */
	private String values(Type t, List<String> vals, String s) {
		Translator.K k = Translator.kind(t);
		List<String> parts = new ArrayList<>();
		List<String> single = new ArrayList<>();
		for (String v : vals) {
			if (v.equals("undefined")) single.add("Undef");
			else if (k == Translator.K.OBJ && v.equals("objects"))
				parts.add("{o \\in " + s + ".objs : " + s + ".cls[o] \\in " + Translator.classSet((MClass) t) + "}");
			else if (k == Translator.K.INT && v.matches("-?\\d+\\.\\.-?\\d+")) {
				String[] ab = v.split("\\.\\.");
				parts.add("(" + ab[0] + ")..(" + ab[1] + ")");
			} else if (k == Translator.K.INT && v.matches("-?\\d+")) single.add("(" + v + ")");
			else if (k == Translator.K.STR && v.startsWith("'")) single.add(q(v.substring(1, v.length() - 1)));
			else if (k == Translator.K.BOOL && (v.equals("true") || v.equals("false"))) single.add(v.toUpperCase());
			else if (k == Translator.K.ENUM && v.startsWith("#") && ((EnumType) t).getLiterals().contains(v.substring(1)))
				single.add(q(((EnumType) t).name() + "::" + v.substring(1)));
			else throw new Translator.Refusal("settings: value " + v + " for type " + t, "");
		}
		if (!single.isEmpty() || parts.isEmpty()) parts.add("{" + String.join(", ", single) + "}");
		return String.join(" \\cup ", parts);
	}

	private String bound() {
		List<String> c = new ArrayList<>();
		for (Map.Entry<String, MAttribute> e : attrs.entrySet()) {
			Type t = e.getValue().type();
			List<String> b = set.bounds.get(t.toString());
			if (b == null || !(t.isTypeOfInteger() || t.isTypeOfString())) continue;
			c.add("(\\A o \\in st.objs \\cap DOMAIN st.att[" + q(e.getKey()) + "] : st.att[" + q(e.getKey()) + "][o] \\in " + values(t, b, "st")
					+ " \\cup {Undef})");
		}
		return c.isEmpty() ? null : String.join("\n  /\\ ", c);
	}

	// ---------------------------------------------------------------- operations

	/** The variables a body reads or writes: self, the parameters, result, the declared and assigned ones. */
	private static Set<String> variables(MOperation op) {
		Set<String> v = new LinkedHashSet<>();
		v.add("self");
		v.addAll(op.paramNames());
		if (op.hasResultType()) v.add("result");
		collect(op.getStatement(), v, null);
		return v;
	}

	/** Adds the variables statement s declares or assigns to vars, and the ones it assigns to assigned. */
	private static void collect(MStatement s, Set<String> vars, Set<String> assigned) {
		if (s instanceof MSequenceStatement) for (MStatement x : ((MSequenceStatement) s).getStatements()) collect(x, vars, assigned);
		else if (s instanceof MBlockStatement) {
			for (VarDecl d : ((MBlockStatement) s).getVariableDeclarations()) {
				vars.add(d.name());
				if (assigned != null) assigned.add(d.name());
			}
			collect(((MBlockStatement) s).getBody(), vars, assigned);
		} else if (s instanceof MVariableAssignmentStatement) {
			vars.add(((MVariableAssignmentStatement) s).getVariableName());
			if (assigned != null) assigned.add(((MVariableAssignmentStatement) s).getVariableName());
		} else if (s instanceof MConditionalExecutionStatement) {
			collect(((MConditionalExecutionStatement) s).getThenStatement(), vars, assigned);
			collect(((MConditionalExecutionStatement) s).getElseStatement(), vars, assigned);
		}
	}

	/** Translates op (Pre_, Body_, Run_ definitions) once; throws its refusal. */
	private void operation(MOperation op) {
		if (done.containsKey(op)) return;
		if (refused.containsKey(op)) throw refused.get(op);
		String n = id(op.cls().name() + "_" + op.name());
		Translator.Unit u = tr.begin();
		try {
			if (!op.hasStatement())
				throw new Translator.Refusal(op.hasExpression() ? "call of a query operation as a step" : "operation without body (pre/post only)", qn(op));
			if (!(op.cls() instanceof MClass)) throw new Translator.Refusal("operation of a non-class", qn(op));
			MClass oc = (MClass) op.cls();
			if (!oc.getAllOwnedProtocolStateMachines().isEmpty()) throw new Translator.Refusal("class with a protocol state machine", oc.name());
			for (MClass k : concrete(oc))
				if (!k.getAllOwnedProtocolStateMachines().isEmpty()) throw new Translator.Refusal("class with a protocol state machine", k.name());
			for (VarDecl d : op.paramList()) Translator.kind(d.type());
			Set<String> vars = variables(op);
			List<String> params = new ArrayList<>();
			params.add("s");
			params.add("self");
			for (String p : op.paramNames()) params.add("a_" + p);
			String sig = "(" + String.join(", ", params) + ")";
			StringBuilder b = new StringBuilder("\\* " + qn(op) + "\n");
			// Preconditions: USE refuses the call unless each is true (MSystem.evaluatePreConditions).
			Map<String, String> penv = new HashMap<>();
			penv.put("self", "self");
			for (String p : op.paramNames()) penv.put(p, "a_" + p);
			List<String> pres = new ArrayList<>();
			for (MPrePostCondition pc : op.preConditions())
				pres.add("(" + expr(pc.expression(), "s", null, penv) + ") = TRUE  \\* pre " + pc.name());
			b.append("Pre_").append(n).append(sig).append(" ==").append(pres.isEmpty() ? " TRUE\n" : "\n  /\\ " + String.join("\n  /\\ ", pres) + "\n");
			// The body, from the configuration at entry.
			StringBuilder c0 = new StringBuilder("[s |-> s");
			for (String v : vars) {
				String init = v.equals("self") ? "self" : op.paramNames().contains(v) ? "a_" + v : v.equals("result") ? "Unset" : "Undef";
				c0.append(", v_").append(v).append(" |-> ").append(init);
			}
			c0.append("]");
			String body = stmt(op.getStatement(), "c0", vars, "  ");
			b.append("Body_").append(n).append(sig).append(" ==\n  LET c0 == ").append(c0).append(" IN\n").append(body).append("\n");
			// A declared result must be assigned (MSystem.assertResultValueValid).
			String fin = op.hasResultType() ? "IF r = Fail THEN Fail ELSE IF r.v_result = Unset THEN Fail ELSE r" : "r";
			b.append("Run_").append(n).append(sig).append(" == IF Pre_").append(n).append(sig).append(" THEN (LET r == Body_")
					.append(n).append(sig).append(" IN ").append(fin).append(") ELSE Fail\n\n");
			posts(op);
			defs.append(b);
			done.put(op, n);
		} catch (Translator.Refusal r) {
			refused.put(op, r);
			throw r;
		} finally {
			tr.end();
		}
	}

	/** The variables the posts of the operations read in their final value: result and assigned parameters. */
	private String outRecord(Set<MOperation> impls) {
		Set<String> out = new TreeSet<>();
		for (MOperation op : impls) out.addAll(outVars(op));
		if (out.isEmpty()) return "";
		List<String> f = new ArrayList<>();
		for (String v : out) f.add("v_" + v + " |-> IF \"v_" + v + "\" \\in DOMAIN x.r THEN x.r.v_" + v + " ELSE Undef");
		return ", out |-> [" + String.join(", ", f) + "]";
	}

	private static Set<String> outVars(MOperation op) {
		Set<String> assigned = new TreeSet<>();
		collect(op.getStatement(), new LinkedHashSet<>(), assigned);
		Set<String> out = new TreeSet<>();
		if (op.hasResultType()) out.add("result");
		for (String p : op.paramNames()) if (assigned.contains(p)) out.add(p);
		return out;
	}

	private final Map<MOperation, String> postText = new HashMap<>();
	private final Map<MOperation, List<String[]>> postChecks = new HashMap<>();

	/**
	 * Postconditions: on a step that ran op, each holds in (last.pre, st) (MSystem.evaluatePostConditions).
	 * USE rolls back a call whose postcondition fails, so a postcondition that does not translate
	 * leaves the step's successors unknown: the operation is refused. A postcondition the settings
	 * skip is not checked, and its step is kept.
	 */
	private void posts(MOperation op) {
		StringBuilder inv = new StringBuilder();
		List<String[]> rows = new ArrayList<>();
		Set<String> out = outVars(op);
		Map<String, String> env = new HashMap<>();
		env.put("self", "last.self");
		List<String> ps = op.paramNames();
		for (int i = 0; i < ps.size(); i++) env.put(ps.get(i), out.contains(ps.get(i)) ? "last.out.v_" + ps.get(i) : "last.args[" + (i + 1) + "]");
		if (op.hasResultType()) env.put("result", "last.out.v_result");
		for (MPrePostCondition pc : op.postConditions()) {
			String key = qn(op) + "::" + pc.name();
			if (set.skip.contains(key)) {
				rows.add(new String[] { null, key });
				continue;
			}
			String n = "Post_" + id(op.cls().name() + "_" + op.name() + "_" + pc.name());
			try {
				String e = expr(pc.expression(), "st", "last.pre", env);
				inv.append("\\* post ").append(key).append('\n').append(n).append(" == last.op = ").append(q(qn(op))).append(" => (")
						.append(e).append(") = TRUE\n\n");
				rows.add(new String[] { n, key });
			} catch (Translator.Refusal r) {
				throw new Translator.Refusal("postcondition: " + r.construct, key + ": " + r.getMessage());
			}
		}
		postText.put(op, inv.toString());
		postChecks.put(op, rows);
	}

	// ---------------------------------------------------------------- statements

	/**
	 * The configuration after statement s from configuration c, or Fail. A statement fails, and USE
	 * rolls the whole call back, where its evaluation throws (EvalUtil, MSystem): an undefined
	 * object to assign to or to link, a link that already exists or does not exist.
	 */
	private String stmt(MStatement s, String c, Set<String> vars, String ind) {
		Map<String, String> env = envOf(c, vars);
		String state = c + ".s";
		if (s instanceof MEmptyStatement) return ind + c;
		if (s instanceof MSequenceStatement) {
			List<MStatement> l = ((MSequenceStatement) s).getStatements();
			if (l.isEmpty()) return ind + c;
			StringBuilder b = new StringBuilder();
			String prev = c;
			for (int i = 0; i < l.size(); i++) {
				if (i == l.size() - 1) {
					b.append(stmt(l.get(i), prev, vars, ind));
					break;
				}
				String nx = fresh("c");
				b.append(ind).append("LET ").append(nx).append(" ==\n").append(stmt(l.get(i), prev, vars, ind + "    ")).append("\n")
						.append(ind).append("IN IF ").append(nx).append(" = Fail THEN Fail ELSE\n");
				prev = nx;
			}
			return b.toString();
		}
		if (s instanceof MBlockStatement) {
			MBlockStatement bl = (MBlockStatement) s;
			if (bl.getVariableDeclarations().isEmpty()) return stmt(bl.getBody(), c, vars, ind);
			// declare assigns undefined to each variable (MBlockStatement.execute).
			List<String> ex = new ArrayList<>();
			for (VarDecl d : bl.getVariableDeclarations()) ex.add("!.v_" + d.name() + " = Undef");
			String nx = fresh("c");
			return ind + "LET " + nx + " == [" + c + " EXCEPT " + String.join(", ", ex) + "] IN\n" + stmt(bl.getBody(), nx, vars, ind);
		}
		if (s instanceof MVariableAssignmentStatement) {
			MVariableAssignmentStatement va = (MVariableAssignmentStatement) s;
			MRValue rv = va.getValue();
			if (rv instanceof MRValueNewObject) {
				MNewObjectStatement ns = ((MRValueNewObject) rv).getNewObjectStatement();
				String[] made = create(ns, state, ind);
				return made[0] + ind + "[" + c + " EXCEPT !.s = " + made[1] + ", !.v_" + va.getVariableName() + " = " + made[2] + "]";
			}
			return ind + "[" + c + " EXCEPT !.v_" + va.getVariableName() + " = " + rvalue(rv, state, env) + "]";
		}
		if (s instanceof MNewObjectStatement) {
			String[] made = create((MNewObjectStatement) s, state, ind);
			return made[0] + ind + "[" + c + " EXCEPT !.s = " + made[1] + "]";
		}
		if (s instanceof MAttributeAssignmentStatement) {
			MAttributeAssignmentStatement aa = (MAttributeAssignmentStatement) s;
			MAttribute a = aa.getAttribute();
			String key = ((MClass) a.owner()).name() + "::" + a.name();
			if (a.isDerived()) throw new Translator.Refusal("assignment to a derived attribute", key);
			if (!attrs.containsKey(key)) throw new Translator.Refusal("assignment to an attribute the state does not hold", key);
			String o = fresh("o");
			return ind + "LET " + o + " == " + expr(aa.getObject(), state, null, env) + " IN IF " + o + " = Undef THEN Fail ELSE [" + c
					+ " EXCEPT !.s.att[" + q(key) + "][" + o + "] = " + rvalue(aa.getRValue(), state, env) + "]";
		}
		if (s instanceof MConditionalExecutionStatement) {
			// An undefined condition runs the else branch (MConditionalExecutionStatement.execute).
			MConditionalExecutionStatement ce = (MConditionalExecutionStatement) s;
			String b = fresh("b");
			return ind + "LET " + b + " == " + expr(ce.getCondition(), state, null, env) + " IN\n" + ind + "IF " + b + " = TRUE THEN\n"
					+ stmt(ce.getThenStatement(), c, vars, ind + "  ") + "\n" + ind + "ELSE\n" + stmt(ce.getElseStatement(), c, vars, ind + "  ");
		}
		if (s instanceof MLinkInsertionStatement || s instanceof MLinkDeletionStatement) {
			boolean ins = s instanceof MLinkInsertionStatement;
			MAssociation as = ins ? ((MLinkInsertionStatement) s).getAssociation() : ((MLinkDeletionStatement) s).getAssociation();
			List<MRValue> parts = ins ? ((MLinkInsertionStatement) s).getParticipants() : ((MLinkDeletionStatement) s).getParticipants();
			if (!assocs.containsKey(as.name()))
				throw new Translator.Refusal((ins ? "insert into " : "delete from ") + "a non-plain association", as.name() + ": " + plainAssociation(as));
			List<String> ps = new ArrayList<>();
			StringBuilder lets = new StringBuilder();
			StringBuilder undef = new StringBuilder();
			for (MRValue rv : parts) {
				String p = fresh("p");
				ps.add(p);
				lets.append(lets.length() == 0 ? "" : " ").append(p).append(" == ").append(rvalue(rv, state, env));
				undef.append(undef.length() == 0 ? "" : " \\/ ").append(p).append(" = Undef");
			}
			String t = "<<" + String.join(", ", ps) + ">>";
			String links = state + ".lnk[" + q(as.name()) + "]";
			return ind + "LET " + lets + " IN IF " + undef + " THEN Fail ELSE IF " + t + (ins ? " \\in " : " \\notin ") + links + " THEN Fail ELSE ["
					+ c + " EXCEPT !.s.lnk[" + q(as.name()) + "] = @ " + (ins ? "\\cup" : "\\") + " {" + t + "}]";
		}
		if (s instanceof MIterationStatement) {
			Translator.K k = Translator.kind(((MIterationStatement) s).getRange().type());
			throw new Translator.Refusal(k == Translator.K.SET || k == Translator.K.BAG ? "for over an unordered collection (USE iterates in hash order)"
					: "for over a sequence", s.toString());
		}
		if (s instanceof MWhileStatement) throw new Translator.Refusal("while loop (unbounded)", s.toString());
		if (s instanceof MObjectOperationCallStatement) throw new Translator.Refusal("operation call statement in a body", s.toString());
		if (s instanceof MObjectDestructionStatement) throw new Translator.Refusal("destroy in a body", s.toString());
		if (s instanceof MNewLinkObjectStatement) throw new Translator.Refusal("new link object in a body", s.toString());
		if (s instanceof MLibraryOperationCallStatement) throw new Translator.Refusal("library operation call (ReadLine, WriteLine...)", s.toString());
		throw new Translator.Refusal("statement " + s.getClass().getSimpleName(), s.toString());
	}

	private String rvalue(MRValue rv, String state, Map<String, String> env) {
		if (rv instanceof MRValueExpression) return expr(((MRValueExpression) rv).getExpression(), state, null, env);
		if (rv instanceof MRValueOperationCall) throw new Translator.Refusal("call of an operation with a SOIL body inside a body", rv.toString());
		if (rv instanceof MRValueNewObject) throw new Translator.Refusal("new inside an expression of a statement", rv.toString());
		throw new Translator.Refusal("rvalue " + rv.getClass().getSimpleName(), rv.toString());
	}

	/** LET prefix, new state, new object: a fresh object of the pool. CHOOSE picks a fixed one; under
	 * symmetry any other fresh one gives a state of the same class. No fresh object left: Fail. */
	private String[] create(MNewObjectStatement ns, String state, String ind) {
		MClass k = ns.getObjectClass();
		if (ns.getObjectName() != null) throw new Translator.Refusal("new with an object name", ns.toString());
		if (!pools.containsKey(k)) throw new Translator.Refusal("new of a class without pool", k.name());
		if (refusedNew.containsKey(k)) throw refusedNew.get(k);
		String f = fresh("f");
		String pre = ind + "IF Pool_" + k.name() + " \\ " + state + ".objs = {} THEN Fail ELSE\n" + ind + "LET " + f + " == CHOOSE x \\in Pool_"
				+ k.name() + " \\ " + state + ".objs : TRUE IN\n";
		return new String[] { pre, "New_" + k.name() + "(" + state + ", " + f + ")", f };
	}
}
