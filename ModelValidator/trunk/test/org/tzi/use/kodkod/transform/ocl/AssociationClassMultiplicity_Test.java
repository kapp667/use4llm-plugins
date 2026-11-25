package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;

/**
 * Checks that the multiplicity of an association class's end other than the
 * first counts the objects linked at that end.
 */
public class AssociationClassMultiplicity_Test {

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("Company_min", 1);
		properties.put("Company_max", 1);
	}

	/** One company, the given number of persons, and one WorksFor link to each. */
	private void validateBinary(String personMultiplicity, int persons) {
		String spec = "model M\n"
				+ "class Company end\n"
				+ "class Person end\n"
				+ "associationclass WorksFor between\n"
				+ "  Company[*] role employer\n"
				+ "  Person[" + personMultiplicity + "] role employee\n"
				+ "end\n";
		properties.put("Person_min", persons);
		properties.put("Person_max", persons);
		properties.put("WorksFor_min", persons);
		properties.put("WorksFor_max", persons);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(spec), properties));
	}

	/** One company, one person, the given number of projects, and one Assignment link to each. */
	private void validateTernary(String projectMultiplicity, int projects) {
		String spec = "model M\n"
				+ "class Company end\n"
				+ "class Person end\n"
				+ "class Project end\n"
				+ "associationclass Assignment between\n"
				+ "  Company[*] role company\n"
				+ "  Person[*] role person\n"
				+ "  Project[" + projectMultiplicity + "] role project\n"
				+ "end\n";
		properties.put("Person_min", 1);
		properties.put("Person_max", 1);
		properties.put("Project_min", projects);
		properties.put("Project_max", projects);
		properties.put("Assignment_min", projects);
		properties.put("Assignment_max", projects);
		validator.validate(TestSpecifications.configure(TestSpecifications.transform(spec), properties));
	}

	@Test
	public void oneLinkMeetsAMultiplicityOfOne() {
		validateBinary("1", 1);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void oneLinkMeetsAMultiplicityOfOneOrMore() {
		validateBinary("1..*", 1);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void threeLinksExceedAMultiplicityOfAtMostTwo() {
		validateBinary("0..2", 3);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void oneLinkMeetsAMultiplicityOfOneOnTheThirdEnd() {
		validateTernary("1", 1);
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void threeLinksExceedAMultiplicityOfAtMostTwoOnTheThirdEnd() {
		validateTernary("0..2", 3);
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}
}
