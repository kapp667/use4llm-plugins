package org.tzi.use.plugin.tla;

import java.io.File;
import java.lang.reflect.Field;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.value.BagValue;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.OrderedSetValue;
import org.tzi.use.uml.ocl.value.SequenceValue;
import org.tzi.use.uml.ocl.value.SetValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.DerivedAttributeController;
import org.tzi.use.uml.sys.DerivedLinkController;
import org.tzi.use.uml.sys.DerivedLinkControllerDerivedEnd;
import org.tzi.use.uml.sys.DerivedLinkControllerUnion;
import org.tzi.use.uml.sys.DerivedValueController;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MLinkSet;
import org.tzi.use.uml.sys.MLinkObject;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MObjectState;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.util.Log;

/**
 * Shell command "tla outdir [K] [seed]". From the current state S0 it builds K mutated copies,
 * evaluates every invariant on every state with USE's evaluator (ground.tsv), translates the
 * invariants and the states to TLA+ (UseModel.tla) and writes the TLC drivers: UseEval prints
 * each invariant's value on each state, UseMC checks the invariants in the model-checking shape
 * on S0, UseMCViol on the first state where USE finds a translated invariant false.
 */
public class TlaCommand implements IPluginShellCmdDelegate {

	@Override
	public void performCommand(IPluginShellCmd cmd) {
		PrintStream out = Log.out();
		String[] args = cmd.getCmdArguments().trim().split("\\s+");
		if (args.length < 1 || args[0].isEmpty() || !cmd.getSession().hasSystem()) {
			out.println("Usage: tla <outdir> [K] [seed], with a model loaded");
			return;
		}
		File dir = new File(args[0]);
		int k = args.length > 1 ? Integer.parseInt(args[1]) : 20;
		long seed = args.length > 2 ? Long.parseLong(args[2]) : 20261126L;
		try {
			run(cmd.getSession().system(), dir, k, seed, out);
		} catch (IOException ex) {
			out.println("tla: " + ex.getMessage());
		}
	}

	// ---------------------------------------------------------------- driver

	/**
	 * A copy of a system state. USE's copy constructor wires the copy's derived-value controllers
	 * to the maps of the source state, so a derived attribute or link update of the copy writes into
	 * the source and fails on an object only the copy has; rewire them to the copy's own maps.
	 */
	static MSystemState copy(String name, MSystemState src) {
		MSystemState c = new MSystemState(name, src);
		try {
			Field fo = MSystemState.class.getDeclaredField("fObjectStates");
			Field fl = MSystemState.class.getDeclaredField("fLinkSets");
			Field fc = MSystemState.class.getDeclaredField("derivedValuesController");
			fo.setAccessible(true);
			fl.setAccessible(true);
			fc.setAccessible(true);
			DerivedValueController[] old = (DerivedValueController[]) fc.get(c);
			@SuppressWarnings("unchecked")
			Map<MAssociation, MLinkSet> links = (Map<MAssociation, MLinkSet>) fl.get(c);
			@SuppressWarnings("unchecked")
			Map<MObject, MObjectState> objs = (Map<MObject, MObjectState>) fo.get(c);
			fc.set(c, new DerivedValueController[] {
					new DerivedLinkControllerDerivedEnd(c, links, (DerivedLinkController) old[0]),
					new DerivedLinkControllerUnion(c, links, (DerivedLinkController) old[1]),
					new DerivedAttributeController(c, objs, (DerivedAttributeController) old[2]) });
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		return c;
	}

	private void run(MSystem sys, File dir, int k, long seed, PrintStream out) throws IOException {
		dir.mkdirs();
		MModel model = sys.model();
		long t0 = System.nanoTime();
		List<MSystemState> states = new ArrayList<>();
		List<String> descr = new ArrayList<>();
		MSystemState s0 = copy("S0", sys.state());
		states.add(s0);
		descr.add("witness");
		Random rnd = new Random(seed);
		for (int i = 1; i <= k; i++) {
			MSystemState si = copy("S" + i, s0);
			descr.add(mutate(si, rnd, model, i));
			states.add(si);
		}

		List<MClassInvariant> invs = new ArrayList<>();
		for (MClassInvariant inv : model.getClassInvariantsIncludingImports()) if (inv.isActive()) invs.add(inv);

		// Ground truth from USE's evaluator: per invariant as check judges it, and per object.
		StringBuilder ground = new StringBuilder("state\tinvariant\tobject\tvalue\n");
		Map<String, String> truth = new TreeMap<>();
		Set<String> errorPairs = new TreeSet<>();
		for (MSystemState st : states) {
			for (MClassInvariant inv : invs) {
				String pair = st.name() + "\t" + inv.qualifiedName();
				String v = show(() -> new Evaluator().eval(inv.flaggedExpression(), st));
				ground.append(pair).append("\t*\t").append(v).append('\n');
				truth.put(pair, v);
				if (v.equals("error")) errorPairs.add(pair);
				if (inv.vars().size() != 1) continue;
				List<MObject> objs = new ArrayList<>(st.objectsOfClassAndSubClasses(inv.cls()));
				objs.sort(Comparator.comparing(MObject::name));
				for (MObject o : objs) {
					String w = show(() -> {
						VarBindings b = new VarBindings();
						b.push(inv.vars().varDecl(0).name(), new ObjectValue(o.cls(), o));
						Value r = new Evaluator().eval(inv.bodyExpression(), st, b);
						if (inv.isNegated() && r.isDefined()) r = BooleanValue.get(!((BooleanValue) r).value());
						return r;
					});
					ground.append(pair).append('\t').append(o.name()).append('\t').append(w).append('\n');
					if (w.equals("error")) errorPairs.add(pair);
				}
			}
		}
		long tUse = System.nanoTime();

		// Translation of the invariants; an invariant reading a stored attribute whose values a
		// state holds that TLA+ cannot express is refused too.
		Translator tr = new Translator();
		Map<Integer, String> inv1 = new TreeMap<>(), body1 = new TreeMap<>();
		Map<Integer, Set<String>> reads = new TreeMap<>();
		Map<Integer, Set<String>> constructs = new TreeMap<>();
		Map<Integer, Translator.Refusal> refused = new TreeMap<>();
		for (int i = 0; i < invs.size(); i++) {
			MClassInvariant inv = invs.get(i);
			Translator.Unit u = tr.begin();
			try {
				inv1.put(i, tr.invariant(inv));
				if (inv.vars().size() == 1) body1.put(i, tr.body(inv, "o"));
				reads.put(i, tr.reads(u));
				constructs.put(i, tr.constructs(u));
			} catch (Translator.Refusal r) {
				refused.put(i, r);
				inv1.remove(i);
				body1.remove(i);
			} finally {
				tr.end();
			}
		}
		// Object identities are TLC model values (O_<name>), so that TLC accepts symmetry over them.
		ids.clear();
		Set<String> allNames = new TreeSet<>();
		for (MSystemState st : states) for (MObject o : st.allObjects()) allNames.add(o.name());
		for (String n : allNames) ids.put(n, modelValue(n, ids.size()));
		Map<String, String> stDefs = new LinkedHashMap<>();
		while (true) {
			Set<String> needed = new TreeSet<>();
			for (Set<String> r : reads.values()) needed.addAll(r);
			Map<String, Translator.Refusal> bad = new TreeMap<>();
			stDefs.clear();
			for (MSystemState st : states) stDefs.put(st.name(), encodeState(st, model, needed, bad));
			if (bad.isEmpty()) break;
			for (Integer i : new ArrayList<>(reads.keySet()))
				for (String a : reads.get(i))
					if (bad.containsKey(a)) {
						refused.put(i, bad.get(a));
						reads.remove(i);
						inv1.remove(i);
						body1.remove(i);
						break;
					}
		}
		List<Integer> ok = new ArrayList<>(inv1.keySet());
		StringBuilder defs = new StringBuilder();
		for (String d : tr.opDefs.values()) defs.append(d).append('\n');
		StringBuilder invTsv = new StringBuilder("invariant\tstatus\tconstruct\tdetail\n");
		for (int i = 0; i < invs.size(); i++) {
			MClassInvariant inv = invs.get(i);
			Translator.Refusal r = refused.get(i);
			if (r != null) {
				invTsv.append(inv.qualifiedName()).append("\trefused\t").append(r.construct).append('\t')
						.append(r.getMessage().replace('\t', ' ').replace('\n', ' ')).append('\n');
				continue;
			}
			invTsv.append(inv.qualifiedName()).append("\ttranslated\t\t\n");
			defs.append("\\* ").append(inv.qualifiedName()).append('\n');
			defs.append("Inv_").append(i).append("(s) == ").append(inv1.get(i)).append("\n");
			if (body1.containsKey(i)) {
				defs.append("Body_").append(i).append("(s, o) == ").append(body1.get(i)).append("\n");
				String x = "x_inst_" + i;
				defs.append("Inst_").append(i).append("(s) == {").append(x).append(" \\in s.objs : s.cls[").append(x)
						.append("] \\in ").append(Translator.classSet(inv.cls())).append("}\n");
			}
			defs.append('\n');
		}
		StringBuilder stTsv = new StringBuilder("state\tobjects\tlinks\tmutation\n");
		StringBuilder stText = new StringBuilder();
		for (int j = 0; j < states.size(); j++) {
			MSystemState st = states.get(j);
			stText.append(st.name()).append(" == ").append(stDefs.get(st.name())).append("\n\n");
			stTsv.append(st.name()).append('\t').append(st.numObjects()).append('\t').append(st.allLinks().size())
					.append('\t').append(descr.get(j)).append('\n');
		}
		long tTr = System.nanoTime();

		write(dir, "ground.tsv", ground.toString());
		write(dir, "invariants.tsv", invTsv.toString());
		write(dir, "states.tsv", stTsv.toString());
		Set<String> used = new TreeSet<>();
		for (int i : ok) used.addAll(constructs.get(i));
		write(dir, "constructs.txt", String.join("\n", used) + "\n");
		write(dir, "timing.tsv", "use_ms\ttranslate_ms\n" + (tUse - t0) / 1000000 + "\t" + (tTr - tUse) / 1000000 + "\n");

		String prelude = new String(getClass().getResourceAsStream("/Prelude.tla").readAllBytes(), StandardCharsets.UTF_8);
		if (Translator.MUTANT.equals("div"))
			prelude = prelude.replace("JDiv(a, b) == IF (a >= 0) = (b > 0) THEN IAbs(a) \\div IAbs(b) ELSE -(IAbs(a) \\div IAbs(b))",
					"JDiv(a, b) == a \\div b");
		if (Translator.MUTANT.equals("and"))
			prelude = prelude.replace("BAnd(a, b) == IF a = FALSE \\/ b = FALSE THEN FALSE ELSE IF a = Undef \\/ b = Undef THEN Undef ELSE TRUE",
					"BAnd(a, b) == IF a = Undef \\/ b = Undef THEN Undef ELSE a /\\ b")
					.replace("BOr(a, b) == IF a = TRUE \\/ b = TRUE THEN TRUE ELSE IF a = Undef \\/ b = Undef THEN Undef ELSE FALSE",
							"BOr(a, b) == IF a = Undef \\/ b = Undef THEN Undef ELSE a \\/ b")
					.replace("BImp(a, b) == IF a = FALSE \\/ b = TRUE THEN TRUE ELSE IF a = Undef \\/ b = Undef THEN Undef ELSE FALSE",
							"BImp(a, b) == IF a = Undef \\/ b = Undef THEN Undef ELSE a => b");
		if (!Translator.MUTANT.isEmpty() && prelude.equals(new String(getClass().getResourceAsStream("/Prelude.tla").readAllBytes(),
				StandardCharsets.UTF_8)) && (Translator.MUTANT.equals("div") || Translator.MUTANT.equals("and")))
			throw new IllegalStateException("mutant " + Translator.MUTANT + " did not apply");
		StringBuilder objDecl = new StringBuilder();
		if (!ids.isEmpty()) objDecl.append("CONSTANTS ").append(String.join(", ", ids.values())).append('\n');
		Map<String, String> objNames = new TreeMap<>();
		for (Map.Entry<String, String> e : ids.entrySet()) objNames.put(e.getValue(), Translator.quote(e.getKey()));
		objDecl.append("\\* The USE name of each object.\nObjName == ").append(fn(objNames, "[x \\in {} |-> \"\"]")).append("\n\n");
		write(dir, "UseModel.tla", "---- MODULE UseModel ----\n" + prelude + "\n" + objDecl + defs + stText + "====\n");

		// The value of each translated invariant on each state, as a whole and per object. A pair
		// whose evaluation in USE raised an error goes to a module of its own, where TLC must fail.
		StringBuilder eval = new StringBuilder("---- MODULE UseEval ----\nEXTENDS UseModel\n");
		StringBuilder errTsv = new StringBuilder("module\tstate\tinvariant\tuse\n");
		int nErr = 0;
		for (MSystemState st : states) {
			for (int i : ok) {
				MClassInvariant inv = invs.get(i);
				// One string per value: TLC breaks a long tuple over several lines, never a string.
				String key = Translator.quote("R|" + st.name() + "|" + inv.qualifiedName() + "|");
				String all = "ASSUME PrintT(" + key + " \\o \"*|\" \\o Show(Inv_" + i + "(" + st.name() + ")))\n";
				if (errorPairs.contains(st.name() + "\t" + inv.qualifiedName())) {
					if (nErr < 30) {
						String m = "UseErr_" + nErr++;
						write(dir, m + ".tla", "---- MODULE " + m + " ----\nEXTENDS UseModel\n" + all + "====\n");
						write(dir, m + ".cfg", constants());
						errTsv.append(m).append('\t').append(st.name()).append('\t').append(inv.qualifiedName()).append('\t')
								.append(truth.get(st.name() + "\t" + inv.qualifiedName())).append('\n');
					}
					continue;
				}
				eval.append(all);
				if (body1.containsKey(i))
					eval.append("ASSUME \\A o \\in Inst_").append(i).append("(").append(st.name()).append(") : PrintT(")
							.append(key).append(" \\o ObjName[o] \\o \"|\" \\o Show(Body_").append(i).append("(").append(st.name())
							.append(", o)))\n");
			}
		}
		write(dir, "UseEval.tla", eval + "====\n");
		write(dir, "UseEval.cfg", constants());
		write(dir, "errors.tsv", errTsv.toString());

		// Model-checking shape: S0, and the first state where USE finds a translated invariant false.
		mc(dir, "UseMC", "S0", ok);
		symmetry(dir, s0, ok);
		String viol = null;
		for (MSystemState st : states) {
			for (int i : ok)
				if ("false".equals(truth.get(st.name() + "\t" + invs.get(i).qualifiedName()))) {
					viol = st.name();
					break;
				}
			if (viol != null) break;
		}
		if (viol != null) mc(dir, "UseMCViol", viol, ok);
		StringBuilder names = new StringBuilder("operator\tinvariant\n");
		for (int i : ok) names.append("MCInv_").append(i).append('\t').append(invs.get(i).qualifiedName()).append('\n');
		write(dir, "mc-invariants.tsv", names.toString());
		out.println("tla: " + states.size() + " states, " + invs.size() + " invariants, " + ok.size() + " translated, "
				+ errorPairs.size() + " USE error pairs, " + (viol == null ? "no violating state" : "violating state " + viol)
				+ ", written to " + dir);
	}

	private void mc(File dir, String name, String state, List<Integer> ok) throws IOException {
		StringBuilder m = new StringBuilder("---- MODULE " + name + " ----\nEXTENDS UseModel\nVARIABLE st\nInit == st = "
				+ state + "\nNext == UNCHANGED st\n");
		StringBuilder cfg = new StringBuilder(constants() + "INIT Init\nNEXT Next\n");
		for (int i : ok) {
			m.append("MCInv_").append(i).append(" == Inv_").append(i).append("(st) = TRUE\n");
			cfg.append("INVARIANT MCInv_").append(i).append('\n');
		}
		write(dir, name + ".tla", m + "====\n");
		write(dir, name + ".cfg", cfg.toString());
	}

	/**
	 * UseMC under SYMMETRY over the objects of S0's most populated class with 2 to 6 objects,
	 * which TLC accepts only for a set of model values. TLC materializes the n! permutations: a
	 * class of 31 objects ran out of heap.
	 */
	private void symmetry(File dir, MSystemState s0, List<Integer> ok) throws IOException {
		Map<String, List<String>> byClass = new TreeMap<>();
		for (MObject o : sortedObjects(s0)) byClass.computeIfAbsent(o.cls().name(), z -> new ArrayList<>()).add(ids.get(o.name()));
		String best = null;
		for (Map.Entry<String, List<String>> e : byClass.entrySet())
			if (e.getValue().size() >= 2 && e.getValue().size() <= 6 && (best == null || e.getValue().size() > byClass.get(best).size()))
				best = e.getKey();
		if (best == null) return;
		StringBuilder m = new StringBuilder("---- MODULE UseMCSym ----\nEXTENDS UseMC\n\\* The objects of class " + best + " in S0.\nSym == Permutations({"
				+ String.join(", ", byClass.get(best)) + "})\n====\n");
		StringBuilder cfg = new StringBuilder(constants() + "INIT Init\nNEXT Next\nSYMMETRY Sym\n");
		for (int i : ok) cfg.append("INVARIANT MCInv_").append(i).append('\n');
		write(dir, "UseMCSym.tla", m.toString());
		write(dir, "UseMCSym.cfg", cfg.toString());
	}

	/** The fixed prelude of every translated module. */
	static String prelude() {
		try {
			return new String(TlaCommand.class.getResourceAsStream("/Prelude.tla").readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	static void write(File dir, String name, String text) throws IOException {
		Files.write(new File(dir, name).toPath(), text.getBytes(StandardCharsets.UTF_8));
	}

	interface ValueSupplier {
		Value get();
	}

	private static String show(ValueSupplier f) {
		try {
			Value v = f.get();
			if (v == null) return "error";
			if (v.isUndefined()) return "undefined";
			return ((BooleanValue) v).isTrue() ? "true" : "false";
		} catch (RuntimeException ex) {
			return "error";
		}
	}

	// ---------------------------------------------------------------- mutations

	private static List<MObject> sortedObjects(MSystemState st) {
		List<MObject> l = new ArrayList<>(st.allObjects());
		l.sort(Comparator.comparing(MObject::name));
		return l;
	}

	private static String linkText(MLink l) {
		StringBuilder sb = new StringBuilder(l.association().name()).append("(");
		for (MAssociationEnd e : l.association().associationEnds()) {
			if (sb.charAt(sb.length() - 1) != '(') sb.append(",");
			sb.append(l.linkEnd(e).object().name());
		}
		return sb.append(")").toString();
	}

	private String mutate(MSystemState st, Random rnd, MModel model, int idx) {
		int n = 1 + rnd.nextInt(2);
		List<String> done = new ArrayList<>();
		for (int m = 0; m < n; m++) {
			int kind = rnd.nextInt(4);
			for (int attempt = 0; attempt < 4; attempt++) {
				String d;
				try {
					d = mutation((kind + attempt) % 4, st, rnd, model, idx * 10 + m);
				} catch (Exception ex) {
					d = null;
				}
				if (d != null) {
					done.add(d);
					break;
				}
			}
		}
		return done.isEmpty() ? "none" : String.join("; ", done);
	}

	private String mutation(int kind, MSystemState st, Random rnd, MModel model, int tag) throws Exception {
		switch (kind) {
		case 0: { // delete a link
			List<MLink> links = new ArrayList<>();
			for (MLink l : st.allLinks()) if (!l.isVirtual()) links.add(l);
			if (links.isEmpty()) return null;
			links.sort(Comparator.comparing(TlaCommand::linkText));
			MLink l = links.get(rnd.nextInt(links.size()));
			if (l instanceof MLinkObject) st.deleteObject((MLinkObject) l);
			else st.deleteLink(l);
			return "delete link " + linkText(l);
		}
		case 1: { // destroy an object and its links
			List<MObject> objs = sortedObjects(st);
			if (objs.isEmpty()) return null;
			MObject o = objs.get(rnd.nextInt(objs.size()));
			st.deleteObject(o);
			return "destroy " + o.name();
		}
		case 2: { // set an attribute to another value or to undefined
			List<Object[]> slots = new ArrayList<>();
			for (MObject o : sortedObjects(st))
				for (MAttribute a : o.cls().allAttributes())
					if (!a.isDerived()) slots.add(new Object[] { o, a });
			if (slots.isEmpty()) return null;
			Object[] p = slots.get(rnd.nextInt(slots.size()));
			MObject o = (MObject) p[0];
			MAttribute a = (MAttribute) p[1];
			MObjectState os = o.state(st);
			Value cur = os.attributeValue(a);
			Value nv = rnd.nextInt(3) == 0 ? UndefinedValue.instance : other(a.type(), cur, st, rnd);
			if (nv == null) nv = UndefinedValue.instance;
			os.setAttributeValue(a, nv);
			return "set " + o.name() + "." + a.name() + " := " + nv;
		}
		default: { // create a fresh object
			List<MClass> cls = new ArrayList<>();
			for (MClass c : model.classes()) if (!c.isAbstract() && !(c instanceof MAssociationClass)) cls.add(c);
			if (cls.isEmpty()) return null;
			cls.sort(Comparator.comparing(MClass::name));
			MClass c = cls.get(rnd.nextInt(cls.size()));
			String name = "tla_new" + tag;
			st.createObject(c, name);
			return "create " + name + " : " + c.name();
		}
		}
	}

	private static Value other(Type t, Value cur, MSystemState st, Random rnd) {
		if (t.isTypeOfInteger()) {
			int v = cur.isDefined() ? ((IntegerValue) cur).value() : 0;
			int[] c = { v + 1, v - 1, 0, -v, 2 * v + 1, rnd.nextInt(16) - 5 };
			int r = c[rnd.nextInt(c.length)];
			if (cur.isDefined() && r == v) r = v + 1;
			return IntegerValue.valueOf(r);
		}
		if (t.isTypeOfBoolean()) return BooleanValue.get(cur.isDefined() ? !((BooleanValue) cur).value() : rnd.nextBoolean());
		if (t.isTypeOfString()) {
			Set<String> pool = new TreeSet<>();
			for (MObject o : st.allObjects())
				for (Value v : o.state(st).attributeValueMap().values())
					if (v instanceof StringValue) pool.add(((StringValue) v).value());
			if (cur instanceof StringValue) pool.remove(((StringValue) cur).value());
			pool.add("zz" + rnd.nextInt(3));
			List<String> l = new ArrayList<>(pool);
			return new StringValue(l.get(rnd.nextInt(l.size())));
		}
		if (t.isTypeOfEnum()) {
			EnumType e = (EnumType) t;
			List<String> lits = new ArrayList<>(e.getLiterals());
			if (cur instanceof EnumValue) lits.remove(((EnumValue) cur).value());
			if (lits.isEmpty()) return null;
			return new EnumValue(e, lits.get(rnd.nextInt(lits.size())));
		}
		if (t.isTypeOfClass()) {
			List<MObject> l = new ArrayList<>();
			for (MObject o : sortedObjects(st))
				if (o.cls().conformsTo(t) && !(cur instanceof ObjectValue && ((ObjectValue) cur).value() == o)) l.add(o);
			if (l.isEmpty()) return null;
			MObject o = l.get(rnd.nextInt(l.size()));
			return new ObjectValue(o.cls(), o);
		}
		return null;
	}

	// ---------------------------------------------------------------- state encoding

	/**
	 * The function m as a balanced tree of @@ over k :> v (the keys are distinct, so @@ is
	 * associative). A chain of n @@ makes SANY's level checking quadratic once the keys are declared
	 * constants (model values): t101's 5,967 objects ran it out of memory.
	 */
	private static String fn(Map<String, String> m, String empty) {
		if (m.isEmpty()) return empty;
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, String> e : m.entrySet()) parts.add(e.getKey() + " :> " + e.getValue());
		return "(" + tree(parts, 0, parts.size()) + ")";
	}

	private static String tree(List<String> parts, int from, int to) {
		if (to - from == 1) return parts.get(from);
		int mid = (from + to) / 2;
		return (mid - from > 1 ? "(" + tree(parts, from, mid) + ")" : tree(parts, from, mid)) + " @@ "
				+ (to - mid > 1 ? "(" + tree(parts, mid, to) + ")" : tree(parts, mid, to));
	}

	/** USE object name -> TLC model value naming it, over every state of the run. */
	private final Map<String, String> ids = new TreeMap<>();

	/** A TLA+ identifier for an object: O_ and its USE name, or Ox_ and an index when the name is no identifier. */
	private static String modelValue(String name, int index) {
		return name.matches("[A-Za-z0-9_]+") ? "O_" + name : "Ox_" + index;
	}

	/** The cfg lines declaring Undef and every object as a model value. */
	private String constants() {
		StringBuilder sb = new StringBuilder("CONSTANT Undef = Undef\n");
		for (String id : ids.values()) sb.append("CONSTANT ").append(id).append(" = ").append(id).append('\n');
		return sb.toString();
	}

	private String encodeState(MSystemState st, MModel model, Set<String> needed, Map<String, Translator.Refusal> bad) {
		List<MObject> objs = sortedObjects(st);
		Set<String> names = new TreeSet<>();
		Map<String, String> cls = new TreeMap<>();
		for (MObject o : objs) {
			names.add(ids.get(o.name()));
			cls.put(ids.get(o.name()), Translator.quote(o.cls().name()));
		}
		Map<String, String> att = new TreeMap<>();
		for (MClass c : model.classes()) {
			for (MAttribute a : c.attributes()) {
				String key = c.name() + "::" + a.name();
				if (a.isDerived() || !needed.contains(key)) continue;
				Map<String, String> f = new TreeMap<>();
				try {
					for (MObject o : st.objectsOfClassAndSubClasses(c))
						f.put(ids.get(o.name()), value(o.state(st).attributeValue(a)));
				} catch (Translator.Refusal r) {
					bad.put(key, r);
					continue;
				}
				att.put(Translator.quote(key), fn(f, "[x \\in {} |-> Undef]"));
			}
		}
		Map<String, String> lnk = new TreeMap<>();
		for (MAssociation as : model.associations()) {
			// An association with an ordered end keeps its links in insertion order (MLinkSet), which
			// navigation to the ordered end follows: a sequence. Otherwise a set.
			java.util.Collection<String> tuples = as.isOrdered() ? new ArrayList<>() : new TreeSet<>();
			for (MLink l : st.linksOfAssociation(as).links()) {
				if (l.isVirtual()) continue;
				List<String> parts = new ArrayList<>();
				for (MAssociationEnd e : as.associationEnds()) parts.add(ids.get(l.linkEnd(e).object().name()));
				if (l instanceof MLinkObject) parts.add(ids.get(((MLinkObject) l).name()));
				tuples.add("<<" + String.join(", ", parts) + ">>");
			}
			lnk.put(Translator.quote(as.name()), as.isOrdered() ? "<<" + String.join(", ", tuples) + ">>"
					: "{" + String.join(", ", tuples) + "}");
		}
		return "[objs |-> {" + String.join(", ", names) + "}, cls |-> " + fn(cls, "[x \\in {} |-> \"\"]") + ", att |-> "
				+ fn(att, "[x \\in {} |-> 0]") + ", lnk |-> " + fn(lnk, "[x \\in {} |-> {}]") + "]";
	}

	private String value(Value v) {
		if (v.isUndefined()) return "Undef";
		if (v instanceof BooleanValue) return ((BooleanValue) v).value() ? "TRUE" : "FALSE";
		if (v instanceof IntegerValue) {
			int i = ((IntegerValue) v).value();
			return i < 0 ? "(" + i + ")" : Integer.toString(i);
		}
		if (v instanceof StringValue) return Translator.quote(((StringValue) v).value());
		if (v instanceof EnumValue) return Translator.quote(((EnumType) v.type()).name() + "::" + ((EnumValue) v).value());
		if (v instanceof ObjectValue) {
			String id = ids.get(((ObjectValue) v).value().name());
			if (id == null) throw new Translator.Refusal("state value: object of no state", v.toString());
			return id;
		}
		if (v instanceof SetValue) {
			Set<String> s = new TreeSet<>();
			for (Value e : (CollectionValue) v) s.add(value(e));
			return "{" + String.join(", ", s) + "}";
		}
		if (v instanceof BagValue) {
			Map<String, String> m = new TreeMap<>();
			Map<String, Integer> cnt = new LinkedHashMap<>();
			for (Value e : (CollectionValue) v) cnt.merge(value(e), 1, Integer::sum);
			for (Map.Entry<String, Integer> e : cnt.entrySet()) m.put(e.getKey(), e.getValue().toString());
			return fn(m, "EmptyBag");
		}
		if (v instanceof SequenceValue || v instanceof OrderedSetValue) {
			List<String> l = new ArrayList<>();
			for (Value e : (CollectionValue) v) l.add(value(e));
			return "<<" + String.join(", ", l) + ">>";
		}
		throw new Translator.Refusal("state value " + v.getClass().getSimpleName(), v.toString());
	}
}
