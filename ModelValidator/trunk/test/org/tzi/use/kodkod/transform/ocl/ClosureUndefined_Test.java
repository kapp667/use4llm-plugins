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

/**
 * closure keeps the null its body gives and applies the body to it, as USE
 * does. With one object a1 of A and no link, USE evaluates
 * A.allInstances->closure(x | Set{x.src}) to Set{null,a1}.
 */
public class ClosureUndefined_Test {

	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
	}

	@Test
	public void closureKeepsTheNullTheBodyGives() {
		validator.validate(oneObjectOfA("A.allInstances->closure(x | Set{x.src})->size() = 2", 0));
		assertSatisfiable();
	}

	@Test
	public void closureWithoutTheNullTheBodyGivesIsUnsatisfiable() {
		validator.validate(oneObjectOfA("A.allInstances->closure(x | Set{x.src})->size() = 1", 0));
		assertThat(validator.outcome, anyOf(equalTo("UNSATISFIABLE"), equalTo("TRIVIALLY_UNSATISFIABLE")));
	}

	@Test
	public void closureKeepsTheNullOfAnUndefinedBody() {
		validator.validate(oneObjectOfA("A.allInstances->closure(x | oclUndefined(Set(A)))->size() = 2", 0));
		assertSatisfiable();
	}

	@Test
	public void closureAppliesTheBodyToNull() {
		// a1 gives null, null gives b1: USE evaluates the closure to Set{null,a1,b1}
		validator.validate(oneObjectOfA("A.allInstances->reject(a | a.oclIsTypeOf(B))"
				+ "->closure(x | if x.oclIsUndefined() then B.allInstances else Set{null} endif)->size() = 3", 1));
		assertSatisfiable();
	}

	private void assertSatisfiable() {
		assertThat(validator.outcome, anyOf(equalTo("SATISFIABLE"), equalTo("TRIVIALLY_SATISFIABLE")));
	}

	private static IModel oneObjectOfA(String invariant, int objectsOfB) {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("B_min", objectsOfB);
		properties.put("B_max", objectsOfB);
		properties.put("R_min", 0);
		properties.put("R_max", 0);
		return TestSpecifications.configure(TestSpecifications.transform("model M\n"
				+ "class A\n"
				+ "end\n"
				+ "class B < A\n"
				+ "end\n"
				+ "association R between\n"
				+ "  A [0..1] role src\n"
				+ "  A [*] role dst\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv probe: " + invariant + "\n"), properties);
	}
}
