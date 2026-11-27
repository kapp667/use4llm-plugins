package org.tzi.use.kodkod.transform.ocl;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;

public class EmptyDomainAttribute_Test {

	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
	}

	@Test
	public void attributeTypedByAnAbstractClassWithNoSubclassIsSolved() {
		validator.validate(oneObjectOfA("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  b : B\n"
				+ "end\n"
				+ "abstract class B\n"
				+ "end\n"));
		assertSatisfiable();
	}

	@Test
	public void attributeTypedByAnAbstractClassWithOnlyAbstractSubclassesIsSolved() {
		validator.validate(oneObjectOfA("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  b : B\n"
				+ "end\n"
				+ "abstract class B\n"
				+ "end\n"
				+ "abstract class C < B\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv undefined: self.b.isUndefined()\n"));
		assertSatisfiable();
	}

	@Test
	public void attributeTypedByAnAbstractClassWithAConcreteSubclassStaysDefined() {
		validator.validate(oneObjectOfA("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  b : B\n"
				+ "end\n"
				+ "abstract class B\n"
				+ "end\n"
				+ "class C < B\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv undefined: self.b.isUndefined()\n"));
		assertThat(validator.outcome, anyOf(equalTo("UNSATISFIABLE"), equalTo("TRIVIALLY_UNSATISFIABLE")));
	}

	@Test
	public void attributeTypedByAnAbstractClassWhoseConcreteSubclassHasMaxZeroIsSolved() {
		validator.validate(oneObjectOfA(CONCRETE_SUBCLASS_SPEC, 0));
		assertSatisfiable();
	}

	@Test
	public void attributeTypedByAnAbstractClassWhoseConcreteSubclassCanHaveAnObjectStaysDefined() {
		validator.validate(oneObjectOfA(CONCRETE_SUBCLASS_SPEC + UNDEFINED_INVARIANT, 1));
		assertThat(validator.outcome, anyOf(equalTo("UNSATISFIABLE"), equalTo("TRIVIALLY_UNSATISFIABLE")));
	}

	@Test
	public void attributeTypedByAConcreteClassWithMaxZeroIsSolved() {
		validator.validate(oneObjectOfA("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  b : C\n"
				+ "end\n"
				+ "class C\n"
				+ "end\n", 0));
		assertSatisfiable();
	}

	@Test
	public void attributeTypedByAClassWithOneConcreteSubclassStillAllowedStaysDefined() {
		validator.validate(twoSubclasses(0, 1, UNDEFINED_INVARIANT));
		assertThat(validator.outcome, anyOf(equalTo("UNSATISFIABLE"), equalTo("TRIVIALLY_UNSATISFIABLE")));
	}

	@Test
	public void attributeTypedByAClassWithAllConcreteSubclassesAtMaxZeroIsSolved() {
		validator.validate(twoSubclasses(0, 0, ""));
		assertSatisfiable();
	}

	private static final String CONCRETE_SUBCLASS_SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  b : B\n"
			+ "end\n"
			+ "abstract class B\n"
			+ "end\n"
			+ "class C < B\n"
			+ "end\n";

	private static final String UNDEFINED_INVARIANT = "constraints\n"
			+ "context A inv undefined: self.b.isUndefined()\n";

	private static IModel twoSubclasses(int maxOfC, int maxOfD, String constraints) {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("C_min", 0);
		properties.put("C_max", maxOfC);
		properties.put("D_min", 0);
		properties.put("D_max", maxOfD);
		return TestSpecifications.configure(TestSpecifications.transform(CONCRETE_SUBCLASS_SPEC
				+ "class D < B\n"
				+ "end\n"
				+ constraints), properties);
	}

	private void assertSatisfiable() {
		assertThat(validator.outcome, anyOf(equalTo("SATISFIABLE"), equalTo("TRIVIALLY_SATISFIABLE")));
	}

	private static IModel oneObjectOfA(String spec) {
		return oneObjectOfA(spec, 1);
	}

	private static IModel oneObjectOfA(String spec, int maxOfC) {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("C_min", 0);
		properties.put("C_max", maxOfC);
		return TestSpecifications.configure(TestSpecifications.transform(spec), properties);
	}
}
