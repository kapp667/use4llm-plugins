package org.tzi.use.kodkod.transform.ocl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.rules.ExternalResource;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseCTScrollingAllKodkodModelValidator;
import org.tzi.use.kodkod.UseCTScrollingKodkodModelValidator;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.UseKodkodModelValidator.Diagram;
import org.tzi.use.kodkod.UseScrollingAllKodkodModelValidator;
import org.tzi.use.kodkod.UseScrollingKodkodModelValidator;
import org.tzi.use.main.Session;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.value.VarBindings;
import org.tzi.use.uml.sys.MSystem;

import kodkod.ast.Node;
import kodkod.ast.Variable;

/**
 * Shared by the tests of refused solutions: the log of the validator, the bounds of a model, and
 * validators whose answer to "did USE accept this solution" follows a script.
 */
class RefusalSupport extends ExternalResource {

	/** In a script, this entry means "build the real diagram and let USE check it". */
	static final Diagram REAL = null;

	static final String NOT_PROVED = "UNSATISFIABLE is not proved";

	private final List<LoggingEvent> events = new ArrayList<LoggingEvent>();
	private final AppenderSkeleton recorder = new AppenderSkeleton() {
		@Override
		protected void append(LoggingEvent event) {
			events.add(event);
		}

		@Override
		public boolean requiresLayout() {
			return false;
		}

		@Override
		public void close() {
		}
	};

	private Logger tzi;
	private Level levelBefore;

	@Override
	protected void before() throws Throwable {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		tzi = Logger.getLogger("org.tzi");
		levelBefore = tzi.getLevel();
		tzi.setLevel(Level.INFO);
		tzi.addAppender(recorder);
	}

	@Override
	protected void after() {
		tzi.removeAppender(recorder);
		tzi.setLevel(levelBefore);
	}

	Session session(MModel mModel) {
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		return session;
	}

	/** The model with the given bounds of Integer and of the class A. */
	IModel bounded(MModel mModel, int integerMin, int integerMax, int aMin, int aMax) throws Exception {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", integerMin);
		properties.put("Integer_max", integerMax);
		properties.put("A_min", aMin);
		properties.put("A_max", aMax);
		return TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
	}

	/** Adds the classifying term A.allInstances()->size(), as the command mv -validateCT does. */
	void addSizeTerm(UseCTScrollingKodkodModelValidator validator, MModel mModel, IModel model) {
		StringWriter err = new StringWriter();
		Expression term = OCLCompiler.compileExpression(mModel, "A.allInstances()->size()", "<term>", new PrintWriter(err),
				new VarBindings());
		if (term == null) {
			throw new IllegalStateException(err.toString());
		}
		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, new HashMap<String, Node>(),
				new HashMap<String, Variable>(), new ArrayList<String>());
		term.processWithVisitor(visitor);
		validator.addClassifyingTerm("size", term, visitor.getObject());
	}

	String log() {
		StringBuilder text = new StringBuilder();
		for (LoggingEvent event : events) {
			text.append(event.getLevel()).append(' ').append(event.getMessage()).append('\n');
		}
		return text.toString();
	}

	int count(String message) {
		int count = 0;
		for (LoggingEvent event : events) {
			if (message.equals(String.valueOf(event.getMessage()))) {
				count++;
			}
		}
		return count;
	}

	/** The ERROR events whose message contains the text. */
	List<LoggingEvent> errorsContaining(String text) {
		List<LoggingEvent> found = new ArrayList<LoggingEvent>();
		for (LoggingEvent event : events) {
			if (event.getLevel().isGreaterOrEqual(Level.ERROR) && String.valueOf(event.getMessage()).contains(text)) {
				found.add(event);
			}
		}
		return found;
	}

	/** The answers of USE to the solutions, in order. After the last one, every solution gets the rest answer. */
	static final class Script {
		private final Diagram[] first;
		private final Diagram rest;
		private int calls;

		Script(Diagram rest, Diagram... first) {
			this.first = first;
			this.rest = rest;
		}

		/** The answer for the next solution, or REAL. */
		Diagram next() {
			Diagram answer = calls < first.length ? first[calls] : rest;
			calls++;
			return answer;
		}

		int calls() {
			return calls;
		}
	}

	static Diagram[] times(int n, Diagram answer) {
		Diagram[] answers = new Diagram[n];
		for (int i = 0; i < n; i++) {
			answers[i] = answer;
		}
		return answers;
	}

	static Diagram[] concat(Diagram[] a, Diagram... b) {
		Diagram[] all = new Diagram[a.length + b.length];
		System.arraycopy(a, 0, all, 0, a.length);
		System.arraycopy(b, 0, all, a.length, b.length);
		return all;
	}

	static class Plain extends UseKodkodModelValidator {
		final Script script;

		Plain(Session session, Script script) {
			super(session);
			this.script = script;
		}

		@Override
		protected Diagram showDiagramAndCacheEvaluator() {
			Diagram answer = script.next();
			return answer == REAL ? super.showDiagramAndCacheEvaluator() : answer;
		}
	}

	static class Scrolling extends UseScrollingKodkodModelValidator {
		final Script script;

		Scrolling(Session session, Script script) {
			super(session);
			this.script = script;
		}

		int accepted() {
			return solutions.size();
		}

		@Override
		protected Diagram showDiagramAndCacheEvaluator() {
			Diagram answer = script.next();
			return answer == REAL ? super.showDiagramAndCacheEvaluator() : answer;
		}
	}

	static class ScrollingAll extends UseScrollingAllKodkodModelValidator {
		final Script script;

		ScrollingAll(Session session, Script script) {
			super(session);
			this.script = script;
		}

		int accepted() {
			return solutions.size();
		}

		@Override
		protected Diagram showDiagramAndCacheEvaluator() {
			Diagram answer = script.next();
			return answer == REAL ? super.showDiagramAndCacheEvaluator() : answer;
		}
	}

	static class CTScrolling extends UseCTScrollingKodkodModelValidator {
		final Script script;

		CTScrolling(Session session, Script script) {
			super(session);
			this.script = script;
		}

		int accepted() {
			return solutions.size();
		}

		@Override
		protected Diagram showDiagramAndCacheEvaluator() {
			Diagram answer = script.next();
			return answer == REAL ? super.showDiagramAndCacheEvaluator() : answer;
		}
	}

	static class CTScrollingAll extends UseCTScrollingAllKodkodModelValidator {
		final Script script;

		CTScrollingAll(Session session, Script script) {
			super(session);
			this.script = script;
		}

		int accepted() {
			return solutions.size();
		}

		@Override
		protected Diagram showDiagramAndCacheEvaluator() {
			Diagram answer = script.next();
			return answer == REAL ? super.showDiagramAndCacheEvaluator() : answer;
		}
	}
}
