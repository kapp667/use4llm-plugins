package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;

/**
 * Checks that an association with {@code union} ends holds, as in USE, the
 * links of the associations whose ends subset its ends, and only them.
 */
public class UnionAssociationEnd_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "end\n"
			+ "class B\n"
			+ "end\n"
			+ "class C < A\n"
			+ "end\n"
			+ "class D < B\n"
			+ "end\n"
			+ "class E < A\n"
			+ "end\n"
			+ "class F < B\n"
			+ "end\n"
			+ "association ab between\n"
			+ "  A[*] role a union\n"
			+ "  B[*] role b union\n"
			+ "end\n"
			+ "association cd between\n"
			+ "  C[*] role c subsets a\n"
			+ "  D[*] role d subsets b\n"
			+ "end\n"
			+ "association ef between\n"
			+ "  E[*] role e subsets a\n"
			+ "  F[*] role f subsets b\n"
			+ "end\n";

	private static final String NAVIGATION = SPEC
			+ "constraints\n"
			+ "context C inv twoB: self.b->size() = 2\n";

	private static final String NAVIGATION_CONTROL = SPEC
			+ "constraints\n"
			+ "context C inv oneB: self.b->size() = 1\n";

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		for (String cls : new String[] { "A", "B" }) {
			properties.put(cls + "_min", 0);
			properties.put(cls + "_max", 0);
		}
		for (String cls : new String[] { "C", "D", "E", "F" }) {
			properties.put(cls + "_min", 1);
			properties.put(cls + "_max", 1);
		}
		links("cd", 1);
		links("ef", 1);
	}

	@Test
	public void unionWithTheLinksOfItsSubsettingAssociationsIsSatisfiable() {
		links("ab", 2);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SPEC), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void unionWithALinkOfNoSubsettingAssociationIsUnsatisfiable() {
		links("ab", 3);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SPEC), properties));
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void unionWithoutConfiguredLinksIsNotLimitedToTheDefaultNumber() {
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SPEC), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void navigationThroughAUnionEndGivesTheSubsettingLinksOnly() {
		properties.put("ab_min", 0);
		properties.put("ab_max", -1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(NAVIGATION), properties));
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void navigationThroughAUnionEndGivesTheSubsettingLink() {
		properties.put("ab_min", 0);
		properties.put("ab_max", -1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(NAVIGATION_CONTROL), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	private void links(String association, int number) {
		properties.put(association + "_min", number);
		properties.put(association + "_max", number);
	}
}
