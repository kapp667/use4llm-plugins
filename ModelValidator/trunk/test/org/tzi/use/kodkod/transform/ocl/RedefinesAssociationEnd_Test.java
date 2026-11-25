package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;

/**
 * Checks that an association redefined by another one behaves as in USE: it
 * has no link between objects of the redefining ends' classes, and navigation
 * and multiplicities through its ends also go through the links of the
 * redefining association.
 */
public class RedefinesAssociationEnd_Test {

	private static final String CLASSES = "model M\n"
			+ "class A\n"
			+ "end\n"
			+ "class B\n"
			+ "end\n"
			+ "class C < A\n"
			+ "end\n"
			+ "class D < B\n"
			+ "end\n";

	private static final String REDEFINING = "association CD between\n"
			+ "  C[*] role c redefines a\n"
			+ "  D[*] role d redefines b\n"
			+ "end\n";

	private static final String SPEC = CLASSES
			+ "association AB between\n"
			+ "  A[*] role a\n"
			+ "  B[*] role b\n"
			+ "end\n"
			+ REDEFINING;

	private static final String NAVIGATION = SPEC
			+ "constraints\n"
			+ "context C inv someB: self.b->notEmpty()\n";

	private static final String NAVIGATION_CONTROL = SPEC
			+ "constraints\n"
			+ "context C inv noB: self.b->isEmpty()\n";

	/** b is 1 and AB cannot link a C, so each c needs its b through CD. */
	private static final String MULTIPLICITY = CLASSES
			+ "association AB between\n"
			+ "  A[*] role a\n"
			+ "  B[1] role b\n"
			+ "end\n"
			+ "association CD between\n"
			+ "  C[*] role c redefines a\n"
			+ "  D[1] role d redefines b\n"
			+ "end\n";

	/** At a 0..1 end, a redefining link stands in for the undefined value. */
	private static final String ZERO_ONE = CLASSES
			+ "association AB between\n"
			+ "  A[*] role a\n"
			+ "  B[0..1] role b\n"
			+ "end\n"
			+ "association CD between\n"
			+ "  C[*] role c redefines a\n"
			+ "  D[0..1] role d redefines b\n"
			+ "end\n";

	/** CD redefines the ends of AB, so its links must also be links of X. */
	private static final String SUBSETS = CLASSES
			+ "association X between\n"
			+ "  A[*] role x\n"
			+ "  B[*] role y\n"
			+ "end\n"
			+ "association AB between\n"
			+ "  A[*] role a subsets x\n"
			+ "  B[*] role b subsets y\n"
			+ "end\n"
			+ REDEFINING;

	/** CD redefines the ends of AB, so its links also fill the union U. */
	private static final String UNION = CLASSES
			+ "association U between\n"
			+ "  A[*] role u union\n"
			+ "  B[*] role v union\n"
			+ "end\n"
			+ "association AB between\n"
			+ "  A[*] role a subsets u\n"
			+ "  B[*] role b subsets v\n"
			+ "end\n"
			+ REDEFINING;

	/** EF redefines CD, which redefines AB. */
	private static final String CHAIN = CLASSES
			+ "class E < C\n"
			+ "end\n"
			+ "class F < D\n"
			+ "end\n"
			+ SPEC.substring(CLASSES.length())
			+ "association EF between\n"
			+ "  E[*] role e redefines c\n"
			+ "  F[*] role f redefines d\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv someB: self.b->notEmpty()\n";

	private static final String TERNARY = CLASSES
			+ "association AB between\n"
			+ "  A[*] role a\n"
			+ "  B[*] role b\n"
			+ "  B[*] role b2\n"
			+ "end\n"
			+ "association CD between\n"
			+ "  C[*] role c redefines a\n"
			+ "  D[*] role d redefines b\n"
			+ "  D[*] role d2 redefines b2\n"
			+ "end\n";

	private static final String DERIVED = CLASSES
			+ "association AB between\n"
			+ "  A[*] role a\n"
			+ "  B[*] role b\n"
			+ "end\n"
			+ "association CD between\n"
			+ "  C[*] role c redefines a\n"
			+ "  D[*] role d redefines b derived = D.allInstances()\n"
			+ "end\n";

	private static final String REDEFINED_UNION = CLASSES
			+ "association AB between\n"
			+ "  A[*] role a union\n"
			+ "  B[*] role b union\n"
			+ "end\n"
			+ REDEFINING;

	private static final String COMPOSITION = "model M\n"
			+ "class A\n"
			+ "end\n"
			+ "class C < A\n"
			+ "end\n"
			+ "composition AA between\n"
			+ "  A[0..1] role whole\n"
			+ "  A[*] role part\n"
			+ "end\n"
			+ "composition CA between\n"
			+ "  C[0..1] role cwhole redefines whole\n"
			+ "  A[*] role cpart redefines part\n"
			+ "end\n";

	private static final String ASSOCIATION_CLASS = CLASSES
			+ "associationclass AB between\n"
			+ "  A[*] role a\n"
			+ "  B[*] role b\n"
			+ "end\n"
			+ "associationclass CD < AB between\n"
			+ "  C[*] role c redefines a\n"
			+ "  D[*] role d redefines b\n"
			+ "end\n";

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
	}

	@Test
	public void linkOfTheRedefinedAssociationBetweenRedefiningClassesIsUnsatisfiable() {
		objects(0, 0, 1, 1);
		links("AB", 1);
		links("CD", 0);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SPEC), properties));
		assertUnsatisfiable();
	}

	@Test
	public void linkOfTheRedefinedAssociationToOneRedefiningClassIsUnsatisfiable() {
		objects(1, 0, 0, 1);
		links("AB", 1);
		links("CD", 0);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SPEC), properties));
		assertUnsatisfiable();
	}

	@Test
	public void linkOfTheRedefinedAssociationBetweenItsOwnClassesIsSatisfiable() {
		objects(1, 1, 1, 1);
		links("AB", 1);
		links("CD", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SPEC), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void navigationThroughARedefinedEndCanReachTheRedefiningLink() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(NAVIGATION), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void navigationThroughARedefinedEndCannotBeEmptyWithARedefiningLink() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(NAVIGATION_CONTROL), properties));
		assertUnsatisfiable();
	}

	@Test
	public void multiplicityOfARedefinedEndCountsTheRedefiningLinks() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(MULTIPLICITY), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void navigationThroughARedefinedZeroOneEndCanBeDefinedByTheRedefiningLink() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(ZERO_ONE
				+ "constraints\ncontext C inv definedB: self.b.isDefined()\n"), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void navigationThroughARedefinedZeroOneEndCannotBeUndefinedWithARedefiningLink() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(ZERO_ONE
				+ "constraints\ncontext C inv undefinedB: self.b.isUndefined()\n"), properties));
		assertUnsatisfiable();
	}

	@Test
	public void redefiningLinkWithoutTheLinkItsRedefinedEndsSubsetIsUnsatisfiable() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		links("X", 0);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SUBSETS), properties));
		assertUnsatisfiable();
	}

	@Test
	public void redefiningLinkWithTheLinkItsRedefinedEndsSubsetIsSatisfiable() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		links("X", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(SUBSETS), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void unionAboveARedefinedAssociationCanHoldTheRedefiningLink() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		links("U", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(UNION), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void unionAboveARedefinedAssociationCannotBeEmptyWithARedefiningLink() {
		objects(0, 0, 1, 1);
		links("AB", 0);
		links("CD", 1);
		links("U", 0);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(UNION), properties));
		assertUnsatisfiable();
	}

	@Test
	public void redefinitionInvolvingAnAssociationClassIsRefused() {
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform the redefinition of association "
				+ "`AB' by `CD': association classes are not supported."),
				TestSpecifications.transform(ASSOCIATION_CLASS).transformationErrors());
	}

	@Test
	public void navigationThroughAnIndirectlyRedefinedEndCanReachTheLastRedefiningLink() {
		objects(0, 0, 0, 0);
		properties.put("E_min", 1);
		properties.put("E_max", 1);
		properties.put("F_min", 1);
		properties.put("F_max", 1);
		links("AB", 0);
		links("CD", 0);
		links("EF", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(CHAIN), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void redefinitionInvolvingAnNaryAssociationIsRefused() {
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform the redefinition of association "
				+ "`AB' by `CD': only binary associations are supported."),
				TestSpecifications.transform(TERNARY).transformationErrors());
	}

	@Test
	public void redefinitionInvolvingADerivedAssociationIsRefused() {
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform the redefinition of association "
				+ "`AB' by `CD': derived associations are not supported."),
				TestSpecifications.transform(DERIVED).transformationErrors());
	}

	@Test
	public void redefinitionInvolvingAUnionIsRefused() {
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform the redefinition of association "
				+ "`AB' by `CD': union associations are not supported."),
				TestSpecifications.transform(REDEFINED_UNION).transformationErrors());
	}

	@Test
	public void cycleThroughARedefinedCompositionIsUnsatisfiable() {
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("C_min", 1);
		properties.put("C_max", 1);
		links("AA", 1);
		links("CA", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(COMPOSITION), properties));
		assertUnsatisfiable();
	}

	@Test
	public void partOwnedThroughARedefiningCompositionIsSatisfiable() {
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		properties.put("C_min", 1);
		properties.put("C_max", 1);
		links("AA", 0);
		links("CA", 1);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(COMPOSITION), properties));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	private void objects(int a, int b, int c, int d) {
		int[] numbers = { a, b, c, d };
		String[] classes = { "A", "B", "C", "D" };
		for (int i = 0; i < classes.length; i++) {
			properties.put(classes[i] + "_min", numbers[i]);
			properties.put(classes[i] + "_max", numbers[i]);
		}
	}

	private void links(String association, int number) {
		properties.put(association + "_min", number);
		properties.put(association + "_max", number);
	}

	private void assertUnsatisfiable() {
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}
}
