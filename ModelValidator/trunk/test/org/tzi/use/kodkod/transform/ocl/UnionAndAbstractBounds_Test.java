package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.tzi.use.uml.mm.MAssociation;

/**
 * The bounds of a union association and of an attribute of an abstract class are checked
 * by the configuration rules like any other bound: a configuration that breaks a rule is
 * refused by mv -validate (no verdict) and called invalid by mv -vc. A union association
 * that has no maximum in the file stays unbounded.
 */
public class UnionAndAbstractBounds_Test extends MvCommandSession {

	private static final String SPEC = "model M68\n"
			+ "abstract class Fruit\nattributes\n  w : Integer\nend\n"
			+ "class Apple < Fruit\nend\n"
			+ "class A\nattributes\n  v : Integer\nend\n"
			+ "class C\nend\n"
			+ "association ab between\n  A[*] role a\n  C[*] role c union\nend\n"
			+ "association sub between\n  A[*] role a2\n  C[*] role c2 subsets c\nend\n";

	private static final String BASE = "[default]\nFruit_min = 0\nFruit_max = 2\nApple_min = 1\nApple_max = 2\n"
			+ "A_min = 1\nA_max = 2\nC_min = 1\nC_max = 2\nInteger_min = 0\nInteger_max = 5\n";

	private static final String MIN_MAX_ASSOCIATION = "ASSOCIATION_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX_WHEN_MAX_NOT_STAR";
	private static final String NEGATIVE_ASSOCIATION = "ASSOCIATION_SETTINGS_MAX_STAR_OR_GREATER_THAN_OR_EQUAL_ZERO";
	private static final String MIN_MAX_ATTRIBUTE = "ATTRIBUTE_SETTINGS_MIN_LESS_THAN_OR_EQUAL_MAX";
	private static final String NEGATIVE_ATTRIBUTE = "ATTRIBUTE_SETTINGS_MAX_STAR_OR_GREATER_THAN_OR_EQUAL_ZERO";

	private String properties(String name, String extraLines) throws Exception {
		open(SPEC);
		return file(name + ".properties", BASE + (extraLines.contains("sub_max") ? "" : "sub_max = 2\n") + extraLines);
	}

	private static void assertRefused(String output, String rule) {
		assertTrue(output, output.contains("ERROR The configuration breaks these rules."));
		assertTrue(output, output.contains("Rule " + rule + " is violated."));
		assertTrue(output, output.contains("The model validator does not search with this configuration."));
		assertFalse(output, output.contains("SATISFIABLE"));
	}

	private static void assertInvalid(String output, String rule) {
		assertTrue(output, output.contains("Config default is invalid."));
		assertFalse(output, output.contains("Config default is valid."));
		assertTrue(output, output.contains("Rule " + rule + " is violated."));
	}

	private int linksOf(String association) {
		MAssociation assoc = session.system().model().getAssociation(association);
		return session.system().state().linksOfAssociation(assoc).size();
	}

	@Test
	public void validateRefusesAUnionAssociationWithMinAboveMax() throws Exception {
		String output = validate(properties("union-minmax", "ab_min = 3\nab_max = 2\n"));
		assertRefused(output, MIN_MAX_ASSOCIATION);
		assertTrue(output, output.contains("ab_min (3)"));
		assertTrue(output, output.contains("ab_max (2)"));
	}

	@Test
	public void configCheckCallsAUnionAssociationWithMinAboveMaxInvalid() throws Exception {
		assertInvalid(configCheck(properties("union-minmax", "ab_min = 3\nab_max = 2\n")), MIN_MAX_ASSOCIATION);
	}

	@Test
	public void validateRefusesAUnionAssociationWithANegativeMax() throws Exception {
		String output = validate(properties("union-neg", "ab_max = -5\n"));
		assertRefused(output, NEGATIVE_ASSOCIATION);
		assertTrue(output, output.contains("ab_max (-5) should be greater than or equal -1."));
	}

	@Test
	public void configCheckCallsAUnionAssociationWithANegativeMaxInvalid() throws Exception {
		assertInvalid(configCheck(properties("union-neg", "ab_max = -5\n")), NEGATIVE_ASSOCIATION);
	}

	@Test
	public void validateRefusesAnAbstractClassAttributeWithMinAboveMax() throws Exception {
		String output = validate(properties("abs-minmax", "Fruit_w_min = 3\nFruit_w_max = 2\n"));
		assertRefused(output, MIN_MAX_ATTRIBUTE);
		assertTrue(output, output.contains("Fruit_w"));
		assertTrue(output, output.contains("(3)"));
		assertTrue(output, output.contains("(2)"));
	}

	@Test
	public void configCheckCallsAnAbstractClassAttributeWithMinAboveMaxInvalid() throws Exception {
		assertInvalid(configCheck(properties("abs-minmax", "Fruit_w_min = 3\nFruit_w_max = 2\n")), MIN_MAX_ATTRIBUTE);
	}

	@Test
	public void validateRefusesAnAbstractClassAttributeWithANegativeMax() throws Exception {
		String output = validate(properties("abs-neg", "Fruit_w_max = -5\n"));
		assertRefused(output, NEGATIVE_ATTRIBUTE);
		assertTrue(output, output.contains("Fruit_w_max (-5)"));
	}

	@Test
	public void configCheckCallsAnAbstractClassAttributeWithANegativeMaxInvalid() throws Exception {
		assertInvalid(configCheck(properties("abs-neg", "Fruit_w_max = -5\n")), NEGATIVE_ATTRIBUTE);
	}

	@Test
	public void aUnionAssociationWithinItsBoundsStillSolves() throws Exception {
		String path = properties("union-ok", "ab_min = 1\nab_max = 2\n");
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config default is valid."));
		log.getBuffer().setLength(0);
		String output = validate(path);
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertTrue(linksOf("ab") >= 1);
		assertTrue(useCheckPasses());
	}

	@Test
	public void aUnionAssociationWithOnlyAMinimumIsUnbounded() throws Exception {
		String path = properties("union-minonly", "ab_min = 2\n");
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config default is valid."));
		log.getBuffer().setLength(0);
		String output = validate(path);
		assertFalse(output, output.contains("ERROR"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertTrue(linksOf("ab") >= 2);
		assertTrue(useCheckPasses());
	}

	@Test
	public void plainAssociationAndConcreteAttributeBoundsStayRefused() throws Exception {
		assertRefused(validate(properties("plain-minmax", "sub_min = 3\n")), MIN_MAX_ASSOCIATION);

		log.getBuffer().setLength(0);
		assertRefused(validate(properties("plain-neg", "sub_max = -5\n")), NEGATIVE_ASSOCIATION);

		log.getBuffer().setLength(0);
		String path = properties("conc-minmax", "A_v_min = 3\nA_v_max = 2\n");
		assertRefused(validate(path), MIN_MAX_ATTRIBUTE);

		log.getBuffer().setLength(0);
		assertInvalid(configCheck(path), MIN_MAX_ATTRIBUTE);
	}
}
