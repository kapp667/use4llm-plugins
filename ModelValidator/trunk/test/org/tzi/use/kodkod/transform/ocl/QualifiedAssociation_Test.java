package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

/**
 * Checks that the model validator refuses a model with a qualified association,
 * whose links and multiplicities it cannot translate, instead of solving it as
 * if the association had no qualifier.
 */
public class QualifiedAssociation_Test {

	@Test
	public void qualifiedAssociationIsRefused() {
		String spec = "model M\n"
				+ "class Bank\n"
				+ "end\n"
				+ "class Person\n"
				+ "end\n"
				+ "association Account between\n"
				+ "  Bank[*] qualifier (number : Integer)\n"
				+ "  Person[0..1] role customer\n"
				+ "end\n";
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform association `Account': "
				+ "qualified association ends are not supported."),
				TestSpecifications.transform(spec).transformationErrors());
	}

	@Test
	public void qualifiedAssociationClassIsRefused() {
		String spec = "model M\n"
				+ "class Company\n"
				+ "end\n"
				+ "class Person\n"
				+ "end\n"
				+ "associationclass Job between\n"
				+ "  Person[*] role employee qualifier (department : String)\n"
				+ "  Company[0..1] role employer\n"
				+ "end\n";
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform association `Job': "
				+ "qualified association ends are not supported."),
				TestSpecifications.transform(spec).transformationErrors());
	}
}
