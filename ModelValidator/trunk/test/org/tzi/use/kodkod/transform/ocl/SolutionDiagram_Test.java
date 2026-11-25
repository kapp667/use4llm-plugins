package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that the object diagram USE gets from a solution holds the values the
 * solution gives the attributes.
 */
public class SolutionDiagram_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  an_attribute : Integer\n"
			+ "end\n"
			+ "class B\n"
			+ "end\n"
			+ "associationclass AB between\n"
			+ "  A[*] role a\n"
			+ "  B[*] role b\n"
			+ "attributes\n"
			+ "  a_weight : Integer\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv five: self.an_attribute = 5\n"
			+ "context AB inv six: self.a_weight = 6\n";

	private Session session;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		MModel mModel = TestSpecifications.compile(SPEC);
		session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", -10);
		properties.put("Integer_max", 10);
		for (String element : new String[] { "A", "B", "AB" }) {
			properties.put(element + "_min", 1);
			properties.put(element + "_max", 1);
		}
		new UseKodkodModelValidator(session).validate(TestSpecifications.configure(TestSpecifications.transform(mModel), properties));
	}

	@Test
	public void attributeWithAnUnderscoreInItsNameGetsItsValue() {
		assertEquals("5", attributeValue("A", "an_attribute"));
	}

	@Test
	public void associationClassAttributeWithAnUnderscoreInItsNameGetsItsValue() {
		assertEquals("6", attributeValue("AB", "a_weight"));
	}

	private String attributeValue(String className, String attributeName) {
		for (MObject object : session.system().state().allObjects()) {
			if (object.cls().name().equals(className)) {
				return object.state(session.system().state()).attributeValue(attributeName).toString();
			}
		}
		return "no object of class " + className;
	}
}
