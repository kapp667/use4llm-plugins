package org.tzi.use.kodkod.plugin;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import kodkod.ast.Node;
import kodkod.ast.Variable;
import kodkod.engine.Evaluator;
import kodkod.engine.fol2sat.UnboundLeafException;

import org.tzi.kodkod.KodkodQueryCache;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.use.kodkod.solution.SolutionValueReader;
import org.tzi.use.kodkod.transform.ocl.DefaultExpressionVisitor;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.MultiplicityViolationException;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.uml.ocl.value.Value;

/**
 * Cmd-Class for queries. Prints the value of an OCL expression in the object
 * diagram, as USE computes it, and compares it with the value of its
 * translation in the solution that the diagram shows.
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
			Expression expr = createExpression(arguments);

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

	private Node transformExpression(Expression expr) {
		Map<String, Node> variables = new TreeMap<String, Node>();
		Map<String, IClass> variableClasses = new TreeMap<String, IClass>();

		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model(), variables, variableClasses, new HashMap<String, Variable>(),
				new ArrayList<String>());
		expr.processWithVisitor(visitor);
		return visitor.getObject();
	}

	private void compareWithSolution(String ocl, Expression expr, Value useValue) {
		Evaluator evaluator = KodkodQueryCache.INSTANCE.evaluatorFor(mSystem.state());
		if (evaluator == null) {
			notCompared(KodkodQueryCache.INSTANCE.isQueryEnabled() ? "no solution for this state"
					: "solutions are kept for comparison only after mv ? enable");
			return;
		}
		Type type = expr.type();
		if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && !type.isTypeOfSet()) {
			notCompared("the translation represents a " + type + " by a set");
			return;
		}

		Node translation;
		try {
			translation = transformExpression(expr);
		} catch (RuntimeException e) {
			notCompared(LogMessages.exceptionReason(e));
			return;
		}

		Object result;
		try {
			result = SolutionValueReader.evaluate(evaluator, translation);
		} catch (UnboundLeafException e) {
			notCompared("the solution has no atom for " + e.leaf());
			return;
		}

		Value kodkodValue = new SolutionValueReader(mSystem.state()).read(result, type);
		if (!useValue.equals(kodkodValue)) {
			LOG.error("The translation evaluates " + ocl + " differently from USE. USE: " + useValue + "; Kodkod: "
					+ (kodkodValue == null ? result : kodkodValue) + ".");
		}
	}

	private void notCompared(String reason) {
		LOG.info("Not compared with a solution: " + reason);
	}

	private Expression createExpression(String arguments) {
		InputStream stream = new ByteArrayInputStream(arguments.getBytes());
		Expression expr = OCLCompiler.compileExpression(mSystem.model(), mSystem.state(), stream, "<input>", new PrintWriter(System.err),
				mSystem.varBindings());
		return expr;
	}

}
