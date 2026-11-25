package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that the object diagram USE gets from a solution holds a negative Real
 * value when the default locale writes its minus sign as another character.
 */
public class RealAtom_Test {

	private static final String SPEC = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  salary : Real\n"
			+ "end\n";

	private Locale defaultLocale;

	@Before
	public void setUp() {
		defaultLocale = Locale.getDefault(Locale.Category.FORMAT);
		Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("sv"));
	}

	@After
	public void tearDown() {
		Locale.setDefault(Locale.Category.FORMAT, defaultLocale);
	}

	@Test
	public void negativeRealValueInASwedishLocale() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		MModel mModel = TestSpecifications.compile(SPEC);
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Real_min", -1);
		properties.put("Real_max", -1);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		new UseKodkodModelValidator(session).validate(TestSpecifications.configure(TestSpecifications.transform(mModel), properties));
		MObject object = session.system().state().allObjects().iterator().next();
		assertEquals("-1.0", object.state(session.system().state()).attributeValue("salary").toString());
	}
}
