package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import kodkod.ast.Relation;
import kodkod.instance.TupleSet;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.config.Options;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Checks the object diagram of a solution when a class inherits two attributes
 * of one name from two superclasses. USE gives such an object one slot per
 * declaring class and reads self.s in context A and in context B from different
 * slots, so the diagram must fill each slot from the relation of its own class.
 */
public class MultipleInheritanceAttribute_Test {

	private static final String CLASSES_A_FIRST = "class A\n"
			+ "attributes\n"
			+ "  s : Integer\n"
			+ "end\n"
			+ "class B\n"
			+ "attributes\n"
			+ "  s : Integer\n"
			+ "end\n"
			+ "class C < A, B\n"
			+ "end\n";

	private static final String CLASSES_B_FIRST = "class B\n"
			+ "attributes\n"
			+ "  s : Integer\n"
			+ "end\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  s : Integer\n"
			+ "end\n"
			+ "class C < A, B\n"
			+ "end\n";

	private static final String INVARIANTS = "constraints\n"
			+ "context A inv sA: self.s = 1\n"
			+ "context B inv sB: self.s = 2\n";

	private static final String SET_SPEC = "model MI3Set\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  s : Set(Integer)\n"
			+ "end\n"
			+ "class B\n"
			+ "attributes\n"
			+ "  s : Set(Integer)\n"
			+ "end\n"
			+ "class C < A, B\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv sA: self.s = Set{1}\n"
			+ "context B inv sB: self.s = Set{2}\n";

	private static final String BOUNDS = "[sat]\n"
			+ "Integer_min = 0\n"
			+ "Integer_max = 3\n"
			+ "A_min = 0\n"
			+ "A_max = 0\n"
			+ "B_min = 0\n"
			+ "B_max = 0\n"
			+ "C_min = 1\n"
			+ "C_max = 1\n"
			+ "A_s = Set{1,2}\n"
			+ "B_s = Set{1,2}\n"
			+ "A_sA = active\n"
			+ "B_sB = active\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final StringWriter log = new StringWriter();
	private WriterAppender appender;
	private Session session;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		appender = new WriterAppender(new PatternLayout("%p %m%n"), log);
		Logger.getLogger("org.tzi").addAppender(appender);
	}

	@After
	public void tearDown() {
		Logger.getLogger("org.tzi").removeAppender(appender);
	}

	@Test
	public void validatesAModelWhoseSuperclassesDeclareTheSameAttributeName() throws Exception {
		open("model MI3\n" + CLASSES_A_FIRST + INVARIANTS);
		String output = validate(BOUNDS);
		assertFalse(output, output.contains("ERROR"));
		assertFalse(output, output.contains("Trying to find a new solution"));
		assertEquals(output, 1, count(output, "SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertEquals(output, 1, session.system().state().allObjects().size());
		assertEquals("C", onlyObject().cls().name());
		assertTrue("A::sA", holds("A::sA"));
		assertTrue("B::sB", holds("B::sB"));
	}

	@Test
	public void eachInheritedSlotHoldsTheValueOfItsOwnClassAfterValidation() throws Exception {
		open("model MI3\n" + CLASSES_A_FIRST + INVARIANTS);
		String output = validate(BOUNDS);
		assertEquals(output, 1, session.system().state().allObjects().size());
		assertEquals("1", slot("A").toString());
		assertEquals("2", slot("B").toString());
	}

	@Test
	public void eachInheritedSlotHoldsTheValueOfItsOwnClassWhenARelationComesFirst() throws Exception {
		assertSlotsFromSolution(CLASSES_A_FIRST, "A_s", "B_s");
	}

	@Test
	public void eachInheritedSlotHoldsTheValueOfItsOwnClassWhenBRelationComesFirst() throws Exception {
		assertSlotsFromSolution(CLASSES_A_FIRST, "B_s", "A_s");
	}

	@Test
	public void eachInheritedSlotHoldsTheValueOfItsOwnClassWhenBIsDeclaredBeforeA() throws Exception {
		assertSlotsFromSolution(CLASSES_B_FIRST, "A_s", "B_s");
		assertSlotsFromSolution(CLASSES_B_FIRST, "B_s", "A_s");
	}

	@Test
	public void validatesTwoInheritedSetAttributesOfOneName() throws Exception {
		open(SET_SPEC);
		String output = validate(BOUNDS);
		assertFalse(output, output.contains("ERROR"));
		assertFalse(output, output.contains("Trying to find a new solution"));
		assertEquals(output, 1, count(output, "SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertEquals(output, 1, session.system().state().allObjects().size());
		assertEquals("Set{1}", slot("A").toString());
		assertEquals("Set{2}", slot("B").toString());
		assertTrue("A::sA", holds("A::sA"));
		assertTrue("B::sB", holds("B::sB"));
	}

	@Test
	public void validatesInheritedAttributesOfDifferentNames() throws Exception {
		open("model MI4\n"
				+ "class A\nattributes\n  s : Integer\nend\n"
				+ "class B\nattributes\n  t : Integer\nend\n"
				+ "class C < A, B\nend\n"
				+ "constraints\n"
				+ "context A inv sA: self.s = 1\n"
				+ "context B inv tB: self.t = 2\n");
		String output = validate("[sat]\nInteger_min = 0\nInteger_max = 3\nA_min = 0\nA_max = 0\nB_min = 0\nB_max = 0\n"
				+ "C_min = 1\nC_max = 1\nA_s = Set{1,2}\nB_t = Set{1,2}\nA_sA = active\nB_tB = active\n");
		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, 1, count(output, "SATISFIABLE"));
		assertEquals(output, 1, session.system().state().allObjects().size());
		MSystemState state = session.system().state();
		assertEquals("1", onlyObject().state(state).attributeValue("s").toString());
		assertEquals("2", onlyObject().state(state).attributeValue("t").toString());
		assertTrue("A::sA", holds("A::sA"));
		assertTrue("B::tB", holds("B::tB"));
	}

	@Test
	public void aSubclassObjectGetsTheValueOfTheAttributeItsSuperclassDeclares() throws Exception {
		open("model Single\n"
				+ "class A\nattributes\n  s : Integer\nend\n"
				+ "class B < A\nend\n"
				+ "constraints\n"
				+ "context A inv sA: self.s = 2\n");
		String output = validate("[sat]\nInteger_min = 0\nInteger_max = 3\nA_min = 0\nA_max = 0\n"
				+ "B_min = 1\nB_max = 1\nA_sA = active\n");
		assertFalse(output, output.contains("ERROR"));
		assertEquals(output, 1, count(output, "SATISFIABLE"));
		assertEquals("B", onlyObject().cls().name());
		assertEquals("2", onlyObject().state(session.system().state()).attributeValue("s").toString());
		assertTrue("A::sA", holds("A::sA"));
	}

	/**
	 * Solves the model, then builds the diagram from a solution whose relations
	 * are handed over with the relation named first before the one named second.
	 */
	private void assertSlotsFromSolution(String classes, String first, String second) throws Exception {
		MModel mModel = TestSpecifications.compile("model MI3\n" + classes + INVARIANTS);
		session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 3);
		properties.put("A_min", 0);
		properties.put("A_max", 0);
		properties.put("B_min", 0);
		properties.put("B_max", 0);
		properties.put("C_min", 1);
		properties.put("C_max", 1);
		IModel model = TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
		OutcomeRecorder validator = new OutcomeRecorder();
		validator.validate(model);
		assertEquals("SATISFIABLE", validator.outcome);

		Map<Relation, TupleSet> solution = validator.relationTuples();
		Map<Relation, TupleSet> ordered = new LinkedHashMap<Relation, TupleSet>();
		for (String name : new String[] { first, second }) {
			for (Map.Entry<Relation, TupleSet> entry : solution.entrySet()) {
				if (entry.getKey().name().equals(name)) {
					ordered.put(entry.getKey(), entry.getValue());
				}
			}
		}
		ordered.putAll(solution);

		ObjectDiagramCreator creator = new ObjectDiagramCreator(model, session);
		creator.create(ordered);
		assertEquals(1, session.system().state().allObjects().size());
		assertEquals(first + " before " + second, "1", slot("A").toString());
		assertEquals(first + " before " + second, "2", slot("B").toString());
		assertEquals(ObjectDiagramCreator.ErrorType.NONE, creator.hasDiagramErrors());
	}

	private void open(String spec) {
		session = new Session();
		// As in USE, the plugin watches the session before the model is opened, so it
		// transforms the model under test, not one left over from an earlier test.
		session.setSystem(new MSystem(TestSpecifications.compile("model Empty\n")));
		PluginModelFactory.INSTANCE.registerForSession(session);
		session.setSystem(new MSystem(TestSpecifications.compile(spec)));
		boolean plugins = Options.doPLUGIN;
		Options.doPLUGIN = false;
		Shell.createInstance(session, null);
		Options.doPLUGIN = plugins;
	}

	private String validate(String properties) throws Exception {
		File file = folder.newFile("mi.properties");
		Files.write(file.toPath(), properties.getBytes(StandardCharsets.UTF_8));
		final String[] argumentList = new String[] { file.getPath() };
		new KodkodValidateCmd().performCommand(new IPluginShellCmd() {
			@Override
			public void executeCmd(String cmd, String cmdArguments, String[] argList) {
			}

			@Override
			public String getCmd() {
				return "mv -validate";
			}

			@Override
			public Session getSession() {
				return session;
			}

			@Override
			public Shell getShell() {
				return Shell.getInstance();
			}

			@Override
			public String getCmdArguments() {
				return " " + String.join(" ", argumentList);
			}

			@Override
			public String[] getCmdArgumentList() {
				return argumentList;
			}
		});
		return log.toString();
	}

	private static int count(String text, String word) {
		int count = 0;
		for (int at = text.indexOf(word); at >= 0; at = text.indexOf(word, at + 1)) {
			count++;
		}
		return count;
	}

	private MObject onlyObject() {
		return session.system().state().allObjects().iterator().next();
	}

	/** The value of the slot of attribute s that the given class declares, in the only object. */
	private org.tzi.use.uml.ocl.value.Value slot(String declaringClass) {
		MAttribute attribute = session.system().model().getClass(declaringClass).attribute("s", false);
		return onlyObject().state(session.system().state()).attributeValue(attribute);
	}

	/** Whether USE evaluates the class invariant true in the current state. */
	private boolean holds(String qualifiedName) {
		MSystem system = session.system();
		BooleanValue result = (BooleanValue) new Evaluator().eval(
				system.model().getClassInvariant(qualifiedName).expandedExpression(), system.state());
		return result.isTrue();
	}
}
