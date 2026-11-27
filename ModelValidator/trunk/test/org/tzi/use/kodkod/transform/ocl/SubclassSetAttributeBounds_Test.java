package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.solution.ObjectDiagramCreator;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that the bounds of a Set attribute declared in a class, A_nums_min,
 * A_nums_max, A_nums_minSize and A_nums_maxSize, cover the objects of its
 * subclasses as they cover the objects of the class itself, as USE reads
 * A.allInstances() and the attribute of a subclass object.
 */
public class SubclassSetAttributeBounds_Test {

	private static final String CONCRETE = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "  nums : Set(Integer)\n"
			+ "end\n"
			+ "class B < A\n"
			+ "end\n";

	private static final String ABSTRACT = "model M\n"
			+ "abstract class A\n"
			+ "attributes\n"
			+ "  x : Integer\n"
			+ "  nums : Set(Integer)\n"
			+ "end\n"
			+ "class B < A\n"
			+ "end\n";

	private OutcomeRecorder validator;
	private Map<String, Object> properties;
	private Session session;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("Integer_min", -10);
		properties.put("Integer_max", 10);
	}

	private void objects(String className, int min, int max) {
		properties.put(className + "_min", min);
		properties.put(className + "_max", max);
	}

	private void nums(String suffix, int value) {
		properties.put("A_nums" + suffix, value);
	}

	/**
	 * Validates the specification, and when a solution is found, opens it as
	 * USE's state.
	 */
	private void validate(String spec) throws Exception {
		MModel mModel = TestSpecifications.compile(spec);
		session = new Session();
		session.setSystem(new MSystem(mModel));
		IModel model = TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
		validator.validate(model);
		if ("SATISFIABLE".equals(validator.outcome)) {
			ObjectDiagramCreator creator = new ObjectDiagramCreator(model, session);
			creator.create(validator.relationTuples());
			assertEquals(ObjectDiagramCreator.ErrorType.NONE, creator.hasDiagramErrors());
		}
	}

	/** The number of objects of A and its subclasses whose nums is defined. */
	private int definedNums() {
		int defined = 0;
		for (MObject object : session.system().state().allObjects()) {
			if (!nums(object).isUndefined()) {
				defined++;
			}
		}
		return defined;
	}

	private Value nums(MObject object) {
		return object.state(session.system().state()).attributeValue("nums");
	}

	private int objectCount() {
		return session.system().state().allObjects().size();
	}

	@Test
	public void definedValuesOfASetAttributeCountTheObjectsOfSubclasses() throws Exception {
		objects("A", 1, 1);
		objects("B", 1, 1);
		nums("_min", 2);
		nums("_max", 2);
		validate(CONCRETE);
		assertEquals("SATISFIABLE", validator.outcome);
		assertEquals(2, objectCount());
		assertEquals(2, definedNums());
	}

	@Test
	public void collectionSizesOfASetAttributeApplyToTheObjectsOfSubclasses() throws Exception {
		objects("A", 1, 1);
		objects("B", 1, 1);
		nums("_min", -1);
		nums("_minSize", 3);
		nums("_maxSize", 3);
		validate(CONCRETE);
		assertEquals("SATISFIABLE", validator.outcome);
		assertEquals(2, objectCount());
		for (MObject object : session.system().state().allObjects()) {
			assertEquals(object.name(), 3, ((CollectionValue) nums(object)).size());
		}
	}

	@Test
	public void definedValuesOfASetAttributeOfAnAbstractClassAreHeldByTheSubclassObjects() throws Exception {
		objects("B", 2, 2);
		nums("_min", 2);
		nums("_max", 2);
		nums("_minSize", 1);
		nums("_maxSize", 1);
		validate(ABSTRACT);
		assertEquals("SATISFIABLE", validator.outcome);
		assertEquals(2, objectCount());
		assertEquals(2, definedNums());
		for (MObject object : session.system().state().allObjects()) {
			assertEquals(object.name(), 1, ((CollectionValue) nums(object)).size());
		}
	}

	@Test
	public void moreDefinedValuesThanObjectsOfTheClassAndItsSubclassesAreUnsatisfiable() throws Exception {
		objects("A", 1, 1);
		objects("B", 1, 1);
		nums("_min", 3);
		nums("_max", 3);
		validate(CONCRETE);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void boundsOfASetAttributeStillApplyWhenNoSubclassObjectExists() throws Exception {
		objects("A", 1, 1);
		objects("B", 0, 0);
		nums("_min", 1);
		nums("_max", 1);
		nums("_minSize", 2);
		nums("_maxSize", 2);
		validate(CONCRETE);
		assertEquals("SATISFIABLE", validator.outcome);
		assertEquals(1, objectCount());
		assertEquals(1, definedNums());
		for (MObject object : session.system().state().allObjects()) {
			assertEquals(object.name(), 2, ((CollectionValue) nums(object)).size());
		}
	}
}
