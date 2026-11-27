package org.tzi.use.plugin.tla;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MLinkObject;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.util.Log;

/**
 * Shell command "infotla file label [expr ;; expr ...]": appends one line to file, tab-separated:
 * the label, the current state in the state shape of tlaops ([objs, cls, att, lnk], each object
 * the identifier O_ and its name, values encoded as TlaCommand encodes them) and the value of each
 * OCL expression in the current state. A value TLA+ cannot hold is the string "?" and its Java
 * class. The name starts with "info" so that USE runs it while an evaluation is paused after a
 * failed postcondition (Shell.ppcShell), when the state is the one the call left.
 */
public class InfoTlaCommand implements IPluginShellCmdDelegate {

	@Override
	public void performCommand(IPluginShellCmd cmd) {
		PrintStream out = Log.out();
		String a = cmd.getCmdArguments().trim();
		String[] w = a.split("\\s+", 3);
		if (w.length < 2 || !cmd.getSession().hasSystem()) {
			out.println("Usage: infotla file label [expr ;; expr ...], with a model loaded");
			return;
		}
		MSystem sys = cmd.getSession().system();
		StringBuilder line = new StringBuilder(w[1]).append('\t').append(state(sys.state()));
		if (w.length > 2)
			for (String e : w[2].split(";;")) line.append('\t').append(eval(sys, e.trim()));
		try {
			Files.write(Paths.get(w[0]), (line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException ex) {
			out.println("infotla: " + ex.getMessage());
		}
	}

	private static final Function<String, String> ID = n -> "O_" + n;

	private static String value(org.tzi.use.uml.ocl.value.Value v) {
		try {
			return TlaCommand.value(v, ID);
		} catch (Translator.Refusal r) {
			return Translator.quote("?" + v.getClass().getSimpleName());
		}
	}

	private static String eval(MSystem sys, String expr) {
		StringWriter err = new StringWriter();
		Expression e = OCLCompiler.compileExpression(sys.model(), sys.state(), expr, "infotla", new PrintWriter(err), sys.varBindings());
		if (e == null) return Translator.quote("?compile: " + err.toString().trim().replace('\n', ' ').replace('\t', ' '));
		try {
			return value(new Evaluator().eval(e, sys.state(), sys.varBindings()));
		} catch (RuntimeException ex) {
			return Translator.quote("?error");
		}
	}

	/** The state as one TLA+ record: every object, every stored attribute by owner class, every association. */
	static String state(MSystemState st) {
		Set<String> objs = new TreeSet<>();
		Map<String, String> cls = new TreeMap<>();
		for (MObject o : st.allObjects()) {
			objs.add(ID.apply(o.name()));
			cls.put(ID.apply(o.name()), Translator.quote(o.cls().name()));
		}
		Map<String, String> att = new TreeMap<>();
		for (MClass c : st.system().model().classes())
			for (MAttribute a : c.attributes()) {
				if (a.isDerived()) continue;
				Map<String, String> f = new TreeMap<>();
				for (MObject o : st.objectsOfClassAndSubClasses(c)) f.put(ID.apply(o.name()), value(o.state(st).attributeValue(a)));
				att.put(Translator.quote(c.name() + "::" + a.name()), TlaCommand.fn(f, "<<>>"));
			}
		Map<String, String> lnk = new TreeMap<>();
		for (MAssociation as : st.system().model().associations()) {
			Collection<String> tuples = as.isOrdered() ? new ArrayList<>() : new TreeSet<>();
			for (MLink l : st.linksOfAssociation(as).links()) {
				if (l.isVirtual()) continue;
				List<String> parts = new ArrayList<>();
				for (MAssociationEnd e : as.associationEnds()) parts.add(ID.apply(l.linkEnd(e).object().name()));
				if (l instanceof MLinkObject) parts.add(ID.apply(((MLinkObject) l).name()));
				tuples.add("<<" + String.join(", ", parts) + ">>");
			}
			lnk.put(Translator.quote(as.name()), as.isOrdered() ? "<<" + String.join(", ", tuples) + ">>" : "{" + String.join(", ", tuples) + "}");
		}
		return "[objs |-> {" + String.join(", ", objs) + "}, cls |-> " + TlaCommand.fn(cls, "<<>>") + ", att |-> " + TlaCommand.fn(att, "<<>>")
				+ ", lnk |-> " + TlaCommand.fn(lnk, "<<>>") + "]";
	}
}
