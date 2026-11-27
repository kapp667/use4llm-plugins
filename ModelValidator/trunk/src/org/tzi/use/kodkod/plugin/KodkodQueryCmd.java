package org.tzi.use.kodkod.plugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import kodkod.ast.Node;
import kodkod.ast.Variable;
import kodkod.engine.Evaluator;
import kodkod.engine.fol2sat.UnboundLeafException;

import org.tzi.kodkod.KodkodQueryCache;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.type.TypeLiterals;
import org.tzi.use.kodkod.solution.SolutionValueReader;
import org.tzi.use.kodkod.solution.SolutionValueReader.Reading;
import org.tzi.use.kodkod.solution.SolutionValueReader.Unreadable;
import org.tzi.use.kodkod.transform.ocl.DefaultExpressionVisitor;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.MultiplicityViolationException;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.ocl.value.VarBindings;

/**
 * Prints the value of an OCL expression in the object diagram, as USE computes
 * it, and compares it with the value of its translation in the solution that
 * the diagram shows.
 * 
 * @author Hendrik Reitmann
 * 
 */

public class KodkodQueryCmd extends AbstractPlugin implements IPluginShellCmdDelegate {

	@Override
	public void performCommand(IPluginShellCmd pluginCommand) {
		if(!pluginCommand.getSession().hasSystem()){
			LOG.error("No model loaded.");
			return;
		}
		
		initialize(pluginCommand.getSession());

		String arguments = pluginCommand.getCmdArguments().trim();

		if (arguments.isEmpty()) {
			LOG.error("Usage: mv ? [enable|disable|enabled|ocl-expression]");
		} else if (arguments.toLowerCase().equals("enable")) {
			KodkodQueryCache.INSTANCE.setQueryEnabled(true);
		} else if (arguments.toLowerCase().equals("disable")) {
			KodkodQueryCache.INSTANCE.setQueryEnabled(false);
		} else if (arguments.toLowerCase().equals("enabled")) {
			LOG.info(KodkodQueryCache.INSTANCE.isQueryEnabled());
		} else {
			Expression expr = createExpression(arguments, mSystem.varBindings(), new PrintWriter(System.err));

			if (expr == null)
				return;

			query(arguments, expr);
		}
	}

	private void query(String ocl, Expression expr) {
		Value useValue;
		try {
			useValue = new org.tzi.use.uml.ocl.expr.Evaluator().eval(expr, mSystem.state(), mSystem.varBindings());
		} catch (MultiplicityViolationException e) {
			LOG.error("Could not evaluate. " + e.getMessage());
			return;
		}
		LOG.info(useValue.toStringWithType());

		compareWithSolution(ocl, expr, useValue);
	}

	/**
	 * Translates the expression. The translation registers each literal it
	 * meets in the model, which the next solve would bind. The literals that
	 * the translation adds are dropped afterwards, so a query does not change
	 * the universe of the next solve.
	 */
	private Node transformExpression(Expression expr) {
		Map<String, Node> variables = new TreeMap<String, Node>();
		List<TypeLiterals> types = literalTypes();
		List<Set<String>> literalsBefore = new ArrayList<Set<String>>();
		for (TypeLiterals type : types) {
			literalsBefore.add(new HashSet<String>(type.typeLiterals().keySet()));
		}
		try {
			DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model(), variables, new HashMap<String, Variable>(), new ArrayList<String>());
			expr.processWithVisitor(visitor);
			return visitor.getObject();
		} finally {
			for (int i = 0; i < types.size(); i++) {
				TypeLiterals type = types.get(i);
				for (String literal : new ArrayList<String>(type.typeLiterals().keySet())) {
					if (!literalsBefore.get(i).contains(literal)) {
						type.removeTypeLiteral(literal);
					}
				}
			}
		}
	}

	/**
	 * Returns the types of the model that hold literals: the built-in types,
	 * the enums and the object type of each class.
	 */
	private List<TypeLiterals> literalTypes() {
		List<TypeLiterals> types = new ArrayList<TypeLiterals>(model().typeFactory().typeLiterals());
		types.addAll(model().enumTypes());
		for (IClass clazz : model().classes()) {
			types.add(clazz.objectType());
		}
		return types;
	}

	private void compareWithSolution(String ocl, Expression expr, Value useValue) {
		Evaluator evaluator = KodkodQueryCache.INSTANCE.evaluatorFor(mSystem.state());
		if (evaluator == null) {
			notCompared(KodkodQueryCache.INSTANCE.isQueryEnabled()
					? "the state shown is not a solution found since mv ? enable, or it changed since"
					: "solutions are kept for comparison only after mv ? enable");
			return;
		}
		for (VarBindings.Entry variable : mSystem.varBindings()) {
			String name = variable.getVarName();
			if (!isObjectNamed(variable.getValue(), name) && !compilesWithout(ocl, name)) {
				notCompared("the variable " + name + " is bound to " + variable.getValue() + ", not to the object named " + name);
				return;
			}
		}

		Node translation;
		try {
			translation = transformExpression(expr);
		} catch (RuntimeException e) {
			notCompared(LogMessages.exceptionReason(e));
			return;
		}

		Reading reading;
		try {
			reading = new SolutionValueReader(mSystem.state()).read(evaluator, translation, expr.type());
		} catch (UnboundLeafException e) {
			notCompared("the solution has no atom for " + e.leaf());
			return;
		}

		if (reading instanceof Unreadable unreadable) {
			notCompared(unreadable.reason());
		} else if (reading.disagreesWith(useValue)) {
			LOG.error("The translation evaluates " + ocl + " differently from USE. USE: " + useValue + "; Kodkod: " + reading + ".");
		}
	}

	private static boolean isObjectNamed(Value value, String name) {
		return value instanceof ObjectValue object && object.value().name().equals(name);
	}

	private boolean compilesWithout(String ocl, String variable) {
		VarBindings others = new VarBindings();
		for (VarBindings.Entry binding : mSystem.varBindings()) {
			if (!binding.getVarName().equals(variable)) {
				others.push(binding.getVarName(), binding.getValue());
			}
		}
		return createExpression(ocl, others, new PrintWriter(new StringWriter())) != null;
	}

	private void notCompared(String reason) {
		LOG.info("Not compared with a solution: " + reason);
	}

	private Expression createExpression(String arguments, VarBindings bindings, PrintWriter err) {
		InputStream stream = new ByteArrayInputStream(arguments.getBytes());
		Expression expr = OCLCompiler.compileExpression(mSystem.model(), mSystem.state(), stream, "<input>", err, bindings);
		return expr;
	}

}
