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
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

public class CollectionAttributeDiagram_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  s : Set(Integer)\n"
			+ "  b : Bag(Integer)\n"
			+ "  q : Sequence(Integer)\n"
			+ "  o : OrderedSet(Integer)\n"
			+ "  u : Set(Integer)\n"
			+ "  emptied : Set(Integer) init: Set{1}\n"
			+ "  replaced : Set(Integer) init: Set{1}\n"
			+ "end\n"
			+ "class B < A\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv emptySet: self.s->isEmpty()\n"
			+ "context A inv emptyBag: self.b->isEmpty()\n"
			+ "context A inv emptySequence: self.q->isEmpty()\n"
			+ "context A inv emptyOrderedSet: self.o->isEmpty()\n"
			+ "context A inv undefinedSet: self.u->isUndefined()\n"
			+ "context A inv emptiedInit: self.emptied->isEmpty()\n"
			+ "context A inv replacedInit: self.replaced = Set{2}\n";

	private Session session;
	private ObjectDiagramCreator creator;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		MModel mModel = TestSpecifications.compile(SPEC);
		session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 3);
		for (String className : new String[] { "A", "B" }) {
			properties.put(className + "_min", 1);
			properties.put(className + "_max", 1);
		}
		letAttributeBeUndefined(properties, "A", "u");
		IModel model = TestSpecifications.configure(TestSpecifications.transform(mModel), properties);
		OutcomeRecorder validator = new OutcomeRecorder();
		validator.validate(model);
		assertEquals("SATISFIABLE", validator.outcome);
		creator = new ObjectDiagramCreator(model, session);
		creator.create(validator.relationTuples());
	}

	@Test
	public void emptySetAttributeIsTheEmptySet() {
		assertEquals("Set{}", attributeValue("A", "s").toString());
	}

	@Test
	public void emptyBagAttributeIsTheEmptyBag() {
		assertEquals("Bag{}", attributeValue("A", "b").toString());
	}

	@Test
	public void emptySequenceAttributeIsTheEmptySequence() {
		assertEquals("Sequence{}", attributeValue("A", "q").toString());
	}

	@Test
	public void emptyOrderedSetAttributeIsTheEmptyOrderedSet() {
		assertEquals("OrderedSet{}", attributeValue("A", "o").toString());
	}

	@Test
	public void emptySetAttributeOfASubclassObjectIsTheEmptySet() {
		assertEquals("Set{}", attributeValue("B", "s").toString());
	}

	@Test
	public void undefinedSetAttributeIsUndefined() {
		assertTrue(attributeValue("A", "u").isUndefined());
	}

	@Test
	public void emptySetAttributeIsEmptyAfterAnInitValue() {
		assertEquals("Set{}", attributeValue("A", "emptied").toString());
	}

	@Test
	public void setAttributeHoldsOnlyTheElementsOfTheSolutionAfterAnInitValue() {
		assertEquals("Set{2}", attributeValue("A", "replaced").toString());
	}

	@Test
	public void useFindsNoInvariantFalseInTheDiagram() {
		assertEquals(ObjectDiagramCreator.ErrorType.NONE, creator.hasDiagramErrors());
	}

	private static void letAttributeBeUndefined(Map<String, Object> properties, String className, String attributeName) {
		properties.put(className + "_" + attributeName + "_min", 0);
	}

	private Value attributeValue(String className, String attributeName) {
		for (MObject object : session.system().state().allObjects()) {
			if (object.cls().name().equals(className)) {
				return object.state(session.system().state()).attributeValue(attributeName);
			}
		}
		throw new AssertionError("no object of class " + className);
	}
}
