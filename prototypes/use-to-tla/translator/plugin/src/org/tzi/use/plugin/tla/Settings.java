package org.tzi.use.plugin.tla;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MOperation;

/**
 * The environment of a USE model, which the model does not say: how many objects of each class,
 * the values the environment passes as arguments, which steps it may take, and when the invariants
 * must hold. The translation depends only on the model and these settings.
 *
 * One directive per line, # starts a comment:
 *   objects C n                  a pool of n objects for class C (default 0: none)
 *   domain T v...                the arguments of type T: 'text', integers, a..b, true, false,
 *                                #literal, undefined; for a class, objects (the existing objects
 *                                of the class) and undefined. Default: Boolean and enumerations
 *                                all their values, other types none.
 *   param C::op p v...           the arguments of one parameter, instead of its type's domain
 *   step new C                   create an object of C, nothing else
 *   step call C::op              call op on an existing object of C or of a subclass
 *   step new C call op           create an object of C and call op on it, in one step
 *   contract C::op               translate the preconditions and postconditions of op, with or
 *                                without a body, for a trace that enters and exits it (openter,
 *                                opexit); no step of Next runs it
 *   invariants every-state       invariants and multiplicities hold in every state (default)
 *   invariants after-calls       ... in the initial state and after every step but "new C"
 *   invariants after C::op...    ... in the initial state and after a call of one of these
 *   skip name                    do not check this invariant (C::inv), postcondition (C::op::post)
 *                                or multiplicity (Assoc::end)
 *   bound T v...                 do not explore states where an attribute of type T (Integer or
 *                                String) holds a defined value outside these
 *   symmetry                     TLC may treat the objects of a pool as interchangeable
 */
final class Settings {

	static final class Step {
		final String kind; // new, call, new-call
		final String cls;
		final String op;

		Step(String kind, String cls, String op) {
			this.kind = kind;
			this.cls = cls;
			this.op = op;
		}

		@Override
		public String toString() {
			switch (kind) {
			case "new": return "step new " + cls;
			case "call": return "step call " + cls + "::" + op;
			default: return "step new " + cls + " call " + op;
			}
		}
	}

	final Map<String, Integer> objects = new TreeMap<>();
	final Map<String, List<String>> domains = new TreeMap<>();
	final Map<String, List<String>> params = new TreeMap<>();
	final List<Step> steps = new ArrayList<>();
	final Set<String> contracts = new TreeSet<>();
	String invariants = "every-state";
	final Set<String> after = new TreeSet<>();
	final Set<String> skip = new TreeSet<>();
	final Map<String, List<String>> bounds = new TreeMap<>();
	boolean symmetry = false;

	static Settings read(Path file) throws IOException {
		Settings s = new Settings();
		int n = 0;
		for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			n++;
			List<String> w = words(raw);
			if (w.isEmpty()) continue;
			try {
				s.directive(w);
			} catch (RuntimeException ex) {
				throw new IllegalArgumentException(file + ":" + n + ": " + ex.getMessage());
			}
		}
		return s;
	}

	/** Words of a line: 'quoted text' is one word, # outside quotes starts a comment unless a literal follows it. */
	static List<String> words(String line) {
		List<String> w = new ArrayList<>();
		int i = 0;
		while (i < line.length()) {
			char ch = line.charAt(i);
			if (Character.isWhitespace(ch)) {
				i++;
				continue;
			}
			if (ch == '#' && (i + 1 >= line.length() || !Character.isLetter(line.charAt(i + 1)))) break;
			int j = i;
			if (ch == '\'') {
				j = line.indexOf('\'', i + 1);
				if (j < 0) throw new IllegalArgumentException("unterminated string: " + line);
				j++;
			} else
				while (j < line.length() && !Character.isWhitespace(line.charAt(j))) j++;
			w.add(line.substring(i, j));
			i = j;
		}
		return w;
	}

	private void directive(List<String> w) {
		String d = w.get(0);
		List<String> rest = w.subList(1, w.size());
		switch (d) {
		case "objects":
			need(w, 3, 3);
			objects.put(w.get(1), Integer.parseInt(w.get(2)));
			return;
		case "domain":
			need(w, 2, Integer.MAX_VALUE);
			domains.put(w.get(1), new ArrayList<>(rest.subList(1, rest.size())));
			return;
		case "param":
			need(w, 3, Integer.MAX_VALUE);
			params.put(w.get(1) + "." + w.get(2), new ArrayList<>(w.subList(3, w.size())));
			return;
		case "step":
			if (w.size() == 3 && w.get(1).equals("new")) steps.add(new Step("new", w.get(2), null));
			else if (w.size() == 3 && w.get(1).equals("call") && w.get(2).contains("::")) {
				String[] q = w.get(2).split("::");
				steps.add(new Step("call", q[0], q[1]));
			} else if (w.size() == 5 && w.get(1).equals("new") && w.get(3).equals("call"))
				steps.add(new Step("new-call", w.get(2), w.get(4)));
			else throw new IllegalArgumentException("step new C | step call C::op | step new C call op");
			return;
		case "contract":
			need(w, 2, 2);
			if (!w.get(1).contains("::")) throw new IllegalArgumentException("contract C::op");
			contracts.add(w.get(1));
			return;
		case "invariants":
			need(w, 2, Integer.MAX_VALUE);
			invariants = w.get(1);
			if (invariants.equals("after")) after.addAll(w.subList(2, w.size()));
			else if (!invariants.equals("every-state") && !invariants.equals("after-calls") || w.size() > 2)
				throw new IllegalArgumentException("invariants every-state | after-calls | after C::op...");
			return;
		case "skip":
			need(w, 2, Integer.MAX_VALUE);
			skip.addAll(rest);
			return;
		case "bound":
			need(w, 3, Integer.MAX_VALUE);
			bounds.put(w.get(1), new ArrayList<>(rest.subList(1, rest.size())));
			return;
		case "symmetry":
			need(w, 1, 1);
			symmetry = true;
			return;
		default:
			throw new IllegalArgumentException("unknown directive " + d);
		}
	}

	private static void need(List<String> w, int min, int max) {
		if (w.size() < min || w.size() > max) throw new IllegalArgumentException("wrong number of words in: " + String.join(" ", w));
	}

	/**
	 * The default environment: 2 objects of each concrete class, Integer 0..2, String 'a' and 'b',
	 * object arguments the existing objects or undefined, a step "new C" for each class and a step
	 * "call C::op" for each operation that is not a query (a SOIL body, or none: then refused), an
 * override being reached by dispatch, every
	 * Integer and String attribute bounded by its domain, invariants in every state.
	 */
	static Settings defaults(MModel model) {
		Settings s = new Settings();
		s.domains.put("Integer", new ArrayList<>(Arrays.asList("0..2")));
		s.domains.put("String", new ArrayList<>(Arrays.asList("'a'", "'b'")));
		s.bounds.put("Integer", s.domains.get("Integer"));
		s.bounds.put("String", s.domains.get("String"));
		Map<String, MClass> classes = new TreeMap<>();
		for (MClass c : model.classes()) classes.put(c.name(), c);
		for (MClass c : classes.values()) {
			if (!c.isAbstract() && !(c instanceof MAssociationClass)) {
				s.objects.put(c.name(), 2);
				s.steps.add(new Step("new", c.name(), null));
			}
			s.domains.put(c.name(), new ArrayList<>(Arrays.asList("objects", "undefined")));
		}
		for (MClass c : classes.values()) {
			Map<String, MOperation> ops = new LinkedHashMap<>();
			for (MOperation op : c.operations()) ops.put(op.name(), op);
			for (MOperation op : ops.values()) {
				if (op.hasExpression()) continue; // a query operation: no step, expressions call it
				boolean inherited = false;
				for (MClass p : c.allParents()) if (p.operation(op.name(), false) != null) inherited = true;
				if (!inherited) s.steps.add(new Step("call", c.name(), op.name()));
			}
		}
		return s;
	}

	/** The settings as a file read() reads back. */
	String text() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Integer> e : objects.entrySet()) sb.append("objects ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
		for (Map.Entry<String, List<String>> e : domains.entrySet())
			sb.append("domain ").append(e.getKey()).append(' ').append(String.join(" ", e.getValue())).append('\n');
		for (Map.Entry<String, List<String>> e : params.entrySet()) {
			int dot = e.getKey().lastIndexOf('.');
			sb.append("param ").append(e.getKey(), 0, dot).append(' ').append(e.getKey().substring(dot + 1)).append(' ')
					.append(String.join(" ", e.getValue())).append('\n');
		}
		for (Step st : steps) sb.append(st).append('\n');
		for (String c : contracts) sb.append("contract ").append(c).append('\n');
		sb.append("invariants ").append(invariants).append(after.isEmpty() ? "" : " " + String.join(" ", after)).append('\n');
		if (!skip.isEmpty()) sb.append("skip ").append(String.join(" ", skip)).append('\n');
		for (Map.Entry<String, List<String>> e : bounds.entrySet())
			sb.append("bound ").append(e.getKey()).append(' ').append(String.join(" ", e.getValue())).append('\n');
		if (symmetry) sb.append("symmetry\n");
		return sb.toString();
	}
}
