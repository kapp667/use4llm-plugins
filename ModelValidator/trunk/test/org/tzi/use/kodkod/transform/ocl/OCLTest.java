package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import kodkod.ast.Expression;
import kodkod.ast.Formula;
import kodkod.ast.IntExpression;
import kodkod.ast.Node;
import kodkod.ast.Relation;
import kodkod.ast.Variable;
import kodkod.engine.Evaluator;
import kodkod.instance.Bounds;
import kodkod.instance.Instance;
import kodkod.instance.Tuple;
import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;
import kodkod.instance.Universe;
import kodkod.util.ints.IntIterator;

import org.apache.commons.configuration.MapConfiguration;
import org.junit.BeforeClass;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.helper.PrintHelper;
import org.tzi.kodkod.model.config.impl.AssociationConfigurator;
import org.tzi.kodkod.model.config.impl.AttributeConfigurator;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IAssociation;
import org.tzi.kodkod.model.iface.IAttribute;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.type.IntegerType;
import org.tzi.kodkod.model.type.TypeAtoms;
import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.kodkod.model.visitor.BoundsVisitor;
import org.tzi.kodkod.ocl.OCLGroupRegistry;
import org.tzi.kodkod.ocl.operation.AnyOperationGroup;
import org.tzi.kodkod.ocl.operation.BooleanOperationGroup;
import org.tzi.kodkod.ocl.operation.ClassOperationGroup;
import org.tzi.kodkod.ocl.operation.CollectionConstructorGroup;
import org.tzi.kodkod.ocl.operation.ConditionalOperationGroup;
import org.tzi.kodkod.ocl.operation.IntegerOperationGroup;
import org.tzi.kodkod.ocl.operation.SetOperationGroup;
import org.tzi.kodkod.ocl.operation.VariableOperationGroup;
import org.tzi.use.config.Options;
import org.tzi.use.config.Options.WarningType;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.kodkod.transform.enrich.ObjectDiagramModelEnricher;
import org.tzi.use.main.Session;
import org.tzi.use.main.shell.Shell;
import org.tzi.use.parser.ocl.OCLCompiler;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.EnumValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.SetValue;
import org.tzi.use.uml.ocl.value.StringValue;
import org.tzi.use.uml.ocl.value.UndefinedValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Base class of the OCL translation tests. The value USE computes for an OCL
 * expression on the object diagram built in {@link #setUpBeforeClass()} has to
 * equal the value Kodkod computes for the translated expression on the same
 * object diagram, encoded as an exact Kodkod instance.
 * 
 * @author Hendrik
 */
public class OCLTest {

	/**
	 * The integers of the universe are -INTEGER_BOUND to INTEGER_BOUND: the results
	 * of the arithmetic operations of the tests have to be in the universe, and
	 * Kodkod limits it to about 215 atoms for the associations of arity 4.
	 */
	private static final int INTEGER_BOUND = 40;

	protected static IModel model;
	private static MSystem mSystem;
	private static StringWriter compileErrors;
	private static PrintWriter errorWriter;
	private static Evaluator kodkodEvaluator;

	/** Same as the default configurator, but with the upper bound equal to the lower bound. */
	private static class ExactAssociationConfigurator extends AssociationConfigurator {
		/**
		 * The model constraint requires an Undefined tuple for each object that has
		 * no link at a 0..1 end of a binary association (not of an association class).
		 */
		@Override
		public TupleSet lowerBound(IAssociation association, int arity, TupleFactory tupleFactory) {
			TupleSet lower = super.lowerBound(association, arity, tupleFactory);
			if (association.isBinaryAssociation() && association.associationClass() == null) {
				for (int end = 0; end < 2; end++) {
					if (association.associationEnds().get(end).multiplicity().isZeroOne()) {
						int other = 1 - end;
						TupleSet unlinked = association.associationEnds().get(other).associatedClass().upperBound(tupleFactory).clone();
						for (Tuple tuple : lower) {
							unlinked.remove(tupleFactory.tuple(tuple.atom(other)));
						}
						for (Tuple object : unlinked) {
							Object[] atoms = new Object[2];
							atoms[other] = object.atom(0);
							atoms[end] = TypeConstants.UNDEFINED;
							lower.add(tupleFactory.tuple(atoms));
						}
					}
				}
			}
			return lower;
		}

		@Override
		public TupleSet upperBound(IAssociation association, int arity, TupleFactory tupleFactory) {
			return lowerBound(association, arity, tupleFactory);
		}
	}

	private static class ExactAttributeConfigurator extends AttributeConfigurator {
		ExactAttributeConfigurator(IAttribute attribute) {
			super(attribute);
		}

		@Override
		public TupleSet upperBound(IAttribute attribute, int arity, TupleFactory tupleFactory) {
			return lowerBound(attribute, arity, tupleFactory);
		}
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File file = new File("test/org/tzi/use/kodkod/transform/ocl/testModel.use");

		MModel mModel;
		try (FileInputStream specStream = new FileInputStream(file)){
			mModel = USECompiler.compileSpecification(specStream, "testModel.use", new PrintWriter(System.err), new ModelFactory());
		}

		Session session = new Session();
		mSystem = new MSystem(mModel);
		session.setSystem(mSystem);
		compileErrors = new StringWriter();
		errorWriter = new PrintWriter(compileErrors, true);

		Options.setCheckWarningsUnrelatedTypes(WarningType.IGNORE);
		Options.doPLUGIN = false;
		Shell.createInstance(session, null);
		Shell shell = Shell.getInstance();
		shell.processLineSafely("!create ada:Person");
		shell.processLineSafely("!create bob:Person");
		shell.processLineSafely("!create cyd:Person");
		shell.processLineSafely("!create dan:Person");
		shell.processLineSafely("!create eve:Person");
		shell.processLineSafely("!create ibm:Company");
		shell.processLineSafely("!create apple:Company");
		shell.processLineSafely("!create uf:UndefinedFactory");
		shell.processLineSafely("!create jobAdaIbm:Job_AC between (ada,ibm)");
		shell.processLineSafely("!create parentAdaBob:Parent_AC between (ada,bob)");
		shell.processLineSafely("!create parentAdaCyd:Parent_AC between (ada,cyd)");
		shell.processLineSafely("!create petShop:Company");
		shell.processLineSafely("!create wolfi:Animal");
		shell.processLineSafely("!create stan:Animal");
		shell.processLineSafely("!create jobBobApple:Job_AC between (bob,apple)");
		shell.processLineSafely("!create jobBobIbm:Job_AC between (bob,ibm)");
		shell.processLineSafely("!create buyAdaPetShopWolfi:Buy_AC between (ada,petShop,wolfi)");
		shell.processLineSafely("!create buyBobPetShopStan:Buy_AC between (bob,petShop,stan)");
		shell.processLineSafely("!create buyAdaPetShopStan:Buy_AC between (ada,petShop,stan)");
		shell.processLineSafely("!create examAdaBobCyd:Exam_AC between (ada,bob,cyd)");
		shell.processLineSafely("!create examDanBobCyd:Exam_AC between (dan,bob,cyd)");

		model = PluginModelFactory.INSTANCE.getModel(mModel);

		OCLGroupRegistry registry = OCLGroupRegistry.INSTANCE;
		registry.unregisterAll();
		registry.registerOperationGroup(new VariableOperationGroup(model.typeFactory()));
		registry.registerOperationGroup(new IntegerOperationGroup(model.typeFactory()));
		registry.registerOperationGroup(new BooleanOperationGroup(model.typeFactory()));
		registry.registerOperationGroup(new ClassOperationGroup(model.typeFactory()));
		registry.registerOperationGroup(new AnyOperationGroup(model.typeFactory(), true));
		registry.registerOperationGroup(new ConditionalOperationGroup(model.typeFactory()));
		registry.registerOperationGroup(new SetOperationGroup(model.typeFactory()));
		registry.registerOperationGroup(new CollectionConstructorGroup(model.typeFactory()));

		encodeObjectDiagram(mModel);
	}

	/**
	 * Configures the model so that every class, association and attribute
	 * relation is bound exactly to the tuples of the object diagram of mSystem,
	 * checks that the resulting Kodkod instance holds the object diagram.
	 */
	private static void encodeObjectDiagram(MModel mModel) throws Exception {
		KodkodModelValidatorConfiguration configuration = KodkodModelValidatorConfiguration.getInstance();
		configuration.setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		configuration.setBitwidth(KodkodModelValidatorConfiguration.DEFAULT_BITWIDTH);

		MSystemState state = mSystem.state();
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", -INTEGER_BOUND);
		properties.put("Integer_max", INTEGER_BOUND);
		Map<String, Integer> expectedTuples = new HashMap<String, Integer>();
		for (MClass cls : mModel.classes()) {
			int objects = 0;
			for (MObject object : state.allObjects()) {
				if (object.cls() == cls) {
					objects++;
				}
			}
			properties.put(cls.name() + "_min", objects);
			properties.put(cls.name() + "_max", objects);
			expectedTuples.put(cls.name(), objects);
		}

		model.reset();
		StringWriter warnings = new StringWriter();
		PropertyConfigurationVisitor configurationVisitor = new PropertyConfigurationVisitor(new MapConfiguration(properties), new PrintWriter(warnings, true));
		model.accept(configurationVisitor);
		if (configurationVisitor.containErrors() || warnings.getBuffer().length() > 0) {
			fail("The configuration of the test model failed: " + warnings);
		}

		Map<String, Integer> links = new HashMap<String, Integer>();
		for (MLink link : state.allLinks()) {
			String name = link.association().name();
			links.put(name, links.containsKey(name) ? links.get(name) + 1 : 1);
		}
		for (IAssociation association : model.associations()) {
			ExactAssociationConfigurator configurator = new ExactAssociationConfigurator();
			int number = links.containsKey(association.name()) ? links.get(association.name()) : 0;
			configurator.setLimits(number, number);
			association.setConfigurator(configurator);
			expectedTuples.put(association.name(), number);
		}

		// Attributes: the enricher extracts the defined values, the undefined ones are added here.
		Map<IAttribute, Integer> definedObjects = new HashMap<IAttribute, Integer>();
		Map<IAttribute, Integer> attributeTuples = new HashMap<IAttribute, Integer>();
		for (IClass clazz : model.classes()) {
			for (IAttribute attribute : clazz.attributes()) {
				attribute.setConfigurator(new ExactAttributeConfigurator(attribute));
				definedObjects.put(attribute, 0);
				attributeTuples.put(attribute, 0);
			}
		}
		for (MObject object : state.allObjects()) {
			IClass clazz = model.getClass(object.cls().name());
			for (MAttribute mAttribute : object.cls().allAttributes()) {
				IAttribute attribute = clazz.getAttribute(mAttribute.name());
				Value value = object.state(state).attributeValue(mAttribute);
				int tuples = 1;
				if (value.isUndefined()) {
					String undefined = attribute.type().isSet() ? TypeConstants.UNDEFINED_SET : TypeConstants.UNDEFINED;
					attribute.getConfigurator().addSpecificValue(new String[] { object.cls().name() + "_" + object.name(), undefined });
				} else {
					if (value.isCollection()) {
						tuples = ((CollectionValue) value).size();
						if (tuples == 0) {
							fail("The attribute " + object.name() + "." + mAttribute.name() + " is an empty set, which cannot be encoded as an exact Kodkod instance.");
						}
					}
					definedObjects.put(attribute, definedObjects.get(attribute) + 1);
				}
				attributeTuples.put(attribute, attributeTuples.get(attribute) + tuples);
			}
		}
		for (IAttribute attribute : definedObjects.keySet()) {
			((AttributeConfigurator) attribute.getConfigurator()).setLimits(definedObjects.get(attribute), definedObjects.get(attribute));
		}

		new ObjectDiagramModelEnricher().enrichModel(mSystem, model);

		Evaluator evaluator = createEvaluator();

		// the enricher only logs its errors: check that the instance holds what the object diagram holds
		for (IClass clazz : model.classes()) {
			checkTupleNumber(evaluator, clazz.relation(), expectedTuples.get(clazz.name()));
			for (IAttribute attribute : clazz.attributes()) {
				checkTupleNumber(evaluator, attribute.relation(), attributeTuples.get(attribute));
			}
		}
		for (IAssociation association : model.associations()) {
			int linkTuples = 0;
			for (Tuple tuple : evaluator.evaluate(association.relation())) {
				if (!containsAtom(tuple, TypeConstants.UNDEFINED)) {
					linkTuples++;
				}
			}
			assertEquals("Number of links of " + association.name() + " in the Kodkod instance", expectedTuples.get(association.name()).intValue(), linkTuples);
		}

	}

	/**
	 * Creates the Kodkod instance of the configured model, i.e. its exact bounds,
	 * and an evaluator on it. It is created again for each test, because the
	 * translation of an OCL expression adds literals (strings, enum literals) to
	 * the model.
	 * <p>
	 * The model constraint is not solved: the object diagram of the test violates
	 * the multiplicities of the model, so it is unsatisfiable.
	 */
	private static Evaluator createEvaluator() {
		Universe universe = createUniverse();
		Bounds bounds = new Bounds(universe);
		model.accept(new BoundsVisitor(bounds, universe.factory()));
		Instance instance = new Instance(universe);
		for (Relation relation : bounds.relations()) {
			if (!bounds.lowerBound(relation).equals(bounds.upperBound(relation))) {
				fail("The bounds of " + relation + " are not exact: " + bounds.lowerBound(relation) + " and " + bounds.upperBound(relation));
			}
			instance.add(relation, bounds.lowerBound(relation));
		}
		for (IntIterator ints = bounds.ints().iterator(); ints.hasNext();) {
			int i = ints.next();
			instance.add(i, bounds.exactBound(i));
		}
		kodkod.engine.config.Options options = new kodkod.engine.config.Options();
		options.setBitwidth(KodkodModelValidatorConfiguration.getInstance().bitwidth());
		return new Evaluator(instance, options);
	}

	/**
	 * Same as the universe of the {@code KodkodSolver}.
	 */
	private static Universe createUniverse() {
		Set<Object> atoms = new LinkedHashSet<Object>();

		Set<TypeAtoms> typeAtoms = new LinkedHashSet<TypeAtoms>(model.enumTypes());
		typeAtoms.addAll(model.typeFactory().typeAtoms());
		for (TypeAtoms typeAtom : typeAtoms) {
			atoms.addAll(typeAtom.atoms());
			if (typeAtom.isInteger()) {
				atoms.addAll(((IntegerType) typeAtom).toStringAtoms());
			}
		}
		for (IClass clazz : model.classes()) {
			atoms.addAll(clazz.objectType().atoms());
		}

		return new Universe(atoms);
	}

	private static boolean containsAtom(Tuple tuple, Object atom) {
		for (int i = 0; i < tuple.arity(); i++) {
			if (tuple.atom(i).equals(atom)) {
				return true;
			}
		}
		return false;
	}

	private static void checkTupleNumber(Evaluator evaluator, Expression relation, int expected) {
		int actual = evaluator.evaluate(relation).size();
		if (actual != expected) {
			fail("The Kodkod instance of the test has " + actual + " tuples in " + relation + " instead of " + expected + ".");
		}
	}

	protected org.tzi.use.uml.ocl.expr.Expression toOCLExpression(String ocl) {
		compileErrors.getBuffer().setLength(0);
		return OCLCompiler.compileExpression(mSystem.model(), mSystem.state(), ocl, "Test", errorWriter, mSystem.varBindings());
	}

	protected Object toKodkod(org.tzi.use.uml.ocl.expr.Expression expression) {
		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, new HashMap<String, Node>(), new HashMap<String, IClass>(), new HashMap<String, Variable>(), new ArrayList<String>());
		expression.processWithVisitor(visitor);
		return visitor.getObject();
	}

	protected String formatText(String strGivenText) {
		StringBuffer sbFormattedText = new StringBuffer(strGivenText);

		for (int i = 0; i < sbFormattedText.length(); i++) {
			if (sbFormattedText.charAt(i) == '\n')
				sbFormattedText.deleteCharAt(i);

			if (sbFormattedText.charAt(i) == '\r')
				sbFormattedText.deleteCharAt(i);

			if (sbFormattedText.charAt(i) == '\t')
				sbFormattedText.deleteCharAt(i);
		}

		return sbFormattedText.toString();
	}

	protected String removeWhitespaces(String strGivenText) {
		strGivenText = strGivenText.trim();
		StringBuffer buffer = new StringBuffer();

		SortedSet<Integer> numbers = new TreeSet<Integer>();
		for (int i = 0; i < strGivenText.length(); i++) {
			if (strGivenText.charAt(i) == ' ') {
				numbers.add(i);
			}
		}

		Integer start = null;
		Integer end = null;
		int beginIndex = 0;

		for (Integer num : numbers) {
			if (start == null || end == null) {
				start = num;
				end = num;
			} else if (end.equals(num - 1)) {
				end = num;
			} else {
				if (start.equals(end)) {
				} else if (start.equals(end - 1)) {
					buffer.append(strGivenText.substring(beginIndex, end));
					beginIndex = end + 1;
				} else {
					buffer.append(strGivenText.substring(beginIndex, start + 1));
					beginIndex = end + 1;
				}

				start = num;
				end = num;
			}
		}

		if (start != null && end != null) {
			String append = null;
			if (start.equals(end)) {
				buffer.append(strGivenText.substring(beginIndex));
				append = strGivenText.substring(beginIndex);
			} else if (start.equals(end - 1)) {
				buffer.append(strGivenText.substring(beginIndex, end));
				append = strGivenText.substring(beginIndex, end);
				beginIndex = end + 1;
			} else {
				buffer.append(strGivenText.substring(beginIndex, start + 1));
				append = strGivenText.substring(beginIndex, start + 1);
				beginIndex = end + 1;
			}
			if (buffer.toString().length() <= beginIndex) {
				if (!append.equals(strGivenText.substring(beginIndex))) {
					buffer.append(strGivenText.substring(beginIndex));
				}
			}

		} else {
			buffer.append(strGivenText.substring(beginIndex));
		}

		for (int i = 0; i < buffer.length(); i++) {
			if (buffer.charAt(i) == ' ') {
				if ((i - 4) >= 0 && buffer.substring(i - 4, i).equals("Int[")) {
					buffer.deleteCharAt(i);
					i--;
				} else {
					if ((i + 1) < buffer.length() && buffer.charAt(i + 1) == ']') {
						buffer.deleteCharAt(i);
						i--;
					} else {
						if ((i - 3) >= 0 && buffer.substring(i - 3, i).equals("Int")) {
							buffer.deleteCharAt(i);
							i--;
						}
					}
				}
			}
		}

		return buffer.toString();
	}

	protected void test(String test, String ocl, String expected) {
		test(test, ocl);
	}

	protected void test(String test, String ocl) {
		System.out.println(test + " ocl:");
		System.out.println("\t" + ocl);

		org.tzi.use.uml.ocl.expr.Expression expression = toOCLExpression(ocl);
		assertNotNull("The OCL expression does not compile: " + ocl + System.lineSeparator() + compileErrors, expression);
		Object transform = toKodkod(expression);
		assertNotNull("The OCL expression is not translated to Kodkod: " + ocl, transform);

		Value useValue;
		try {
			useValue = new org.tzi.use.uml.ocl.expr.Evaluator().eval(expression, mSystem.state(), mSystem.varBindings());
		} catch (RuntimeException e) {
			throw new AssertionError("USE cannot evaluate: " + ocl, e);
		}
		Value kodkodValue;
		try {
			kodkodEvaluator = createEvaluator();
			kodkodValue = evaluateKodkod(transform, expression);
		} catch (RuntimeException e) {
			throw new AssertionError("Kodkod cannot evaluate: " + ocl + System.lineSeparator() + PrintHelper.prettyKodkod((Node) transform), e);
		}

		// the translation represents every collection as a set: Bag, Sequence and OrderedSet lose their duplicates and order
		if (useValue instanceof CollectionValue && !(useValue instanceof SetValue)) {
			useValue = ((CollectionValue) useValue).asSet();
		}

		System.out.println("USE: " + useValue);
		System.out.println("Kodkod: " + kodkodValue);
		assertEquals("OCL: " + ocl + System.lineSeparator() + "Kodkod: " + PrintHelper.prettyKodkod((Node) transform) + System.lineSeparator(), useValue, kodkodValue);
	}

	/**
	 * Evaluates the translated expression on the Kodkod instance of the object
	 * diagram and converts the result to a USE value.
	 */
	protected Value evaluateKodkod(Object transform, org.tzi.use.uml.ocl.expr.Expression expression) {
		if (transform instanceof Formula) {
			return BooleanValue.get(kodkodEvaluator.evaluate((Formula) transform));
		}
		if (transform instanceof IntExpression) {
			return IntegerValue.valueOf(kodkodEvaluator.evaluate((IntExpression) transform));
		}
		if (!(transform instanceof Expression)) {
			throw new AssertionError("Unexpected result of the translation: " + transform.getClass());
		}

		TupleSet tuples = kodkodEvaluator.evaluate((Expression) transform);
		if (tuples.arity() != 1) {
			throw new AssertionError("Unexpected arity " + tuples.arity() + " of the Kodkod result " + tuples);
		}
		if (expression.type().isKindOfCollection(VoidHandling.INCLUDE_VOID) && !expression.type().isTypeOfVoidType()) {
			// Kodkod collections are sets, whatever the OCL collection kind is (see test)
			if (tuples.size() == 1 && tuples.iterator().next().atom(0).equals(TypeConstants.UNDEFINED_SET)) {
				return UndefinedValue.instance;
			}
			List<Value> elements = new ArrayList<Value>();
			for (Tuple tuple : tuples) {
				elements.add(toUseValue(tuple.atom(0)));
			}
			return new SetValue(((CollectionType) expression.type()).elemType(), elements);
		}
		if (tuples.size() != 1) {
			throw new AssertionError("Expected one atom as Kodkod result of the non-collection expression, found " + tuples);
		}
		return toUseValue(tuples.iterator().next().atom(0));
	}

	/**
	 * Converts an atom of the Kodkod universe to the USE value it represents.
	 */
	private Value toUseValue(Object atom) {
		if (atom instanceof Integer) {
			return IntegerValue.valueOf((Integer) atom);
		}
		String name = atom.toString();
		if (name.equals(TypeConstants.UNDEFINED) || name.equals(TypeConstants.UNDEFINED_SET)) {
			return UndefinedValue.instance;
		}
		if (name.equals(TypeConstants.BOOLEAN_TRUE) || name.equals(TypeConstants.TRUE)) {
			return BooleanValue.TRUE;
		}
		if (name.equals(TypeConstants.BOOLEAN_FALSE) || name.equals(TypeConstants.FALSE)) {
			return BooleanValue.FALSE;
		}
		if (name.startsWith(TypeConstants.STRING + "_")) {
			return new StringValue(name.substring(TypeConstants.STRING.length() + 1));
		}
		for (EnumType enumType : mSystem.model().enumTypes()) {
			String prefix = enumType.name() + "_";
			if (name.startsWith(prefix) && enumType.contains(name.substring(prefix.length()))) {
				return new EnumValue(enumType, name.substring(prefix.length()));
			}
		}
		for (MObject object : mSystem.state().allObjects()) {
			if (name.equals(object.cls().name() + "_" + object.name())) {
				return new ObjectValue(object.cls(), object);
			}
		}
		throw new AssertionError("The harness cannot convert the Kodkod atom " + name + " to a USE value");
	}

	public static void main(String[] args) {
		String test = "(!(((Undefined_Set in (none + Int[1])) =>  " + "Undefined_Set else " + "(none + Int[1])) = Undefined_Set) && "
				+ "one ((Undefined_Set in (none + Int[1])) => " + "Undefined_Set else " + "(none + Int[1])) && "
				+ "(some i: (Undefined_Set in (none + Int[1])) => " + "Undefined_Set else " + "(none + Int[1]) | "
				+ "  Boolean_True = Boolean_True)) => " + "((Undefined_Set in (none + Int[1])) => " + "Undefined_Set else "
				+ "(none + Int[1])) else " + "Undefined";

		OCLTest t = new OCLTest();
		System.out.println(t.formatText(test));
		System.out.println(t.removeWhitespaces(t.formatText(test)));
	}
}
