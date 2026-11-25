package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import kodkod.instance.Tuple;

import org.apache.commons.configuration.MapConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.impl.SimpleFactory;
import org.tzi.kodkod.model.type.PrimitiveTypeFactory;
import org.tzi.use.kodkod.transform.ModelTransformator;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

/**
 * Checks that a {@code subsets} constraint on an association end holds in the
 * solutions, as USE's check judges it: every link through the subsetting end
 * is also a link through the subsetted end, between the same objects at the
 * corresponding ends.
 */
public class SubsetsAssociationEnd_Test {

	/** The subsetting end c is at the position of the subsetted end d. */
	private static final String SAME_POSITION = "model M\n"
			+ "class T1\n"
			+ "end\n"
			+ "class T2 < T1\n"
			+ "end\n"
			+ "association bd between\n"
			+ "  T2[*] role d\n"
			+ "  T1[*] role b\n"
			+ "end\n"
			+ "association ac between\n"
			+ "  T2[*] role c subsets d\n"
			+ "  T1[*] role a\n"
			+ "end\n";

	/**
	 * The subsetting end c is at the first position, the subsetted end b at the
	 * second: a link (c, a) of ac needs the link (d, b) = (a, c) of bd.
	 */
	private static final String OTHER_POSITION = "model M\n"
			+ "class T1\n"
			+ "end\n"
			+ "class T2 < T1\n"
			+ "end\n"
			+ "class T3 < T1\n"
			+ "end\n"
			+ "association bd between\n"
			+ "  T1[*] role d\n"
			+ "  T1[*] role b\n"
			+ "end\n"
			+ "association ac between\n"
			+ "  T2[*] role c subsets b\n"
			+ "  T3[*] role a\n"
			+ "end\n";

	/** A 0..1 end, whose missing links the validator represents with an undefined value. */
	private static final String ZERO_ONE = "model M\n"
			+ "class T1\n"
			+ "end\n"
			+ "class T2 < T1\n"
			+ "end\n"
			+ "association bd between\n"
			+ "  T2[*] role d\n"
			+ "  T1[*] role b\n"
			+ "end\n"
			+ "association ac between\n"
			+ "  T2[0..1] role c subsets d\n"
			+ "  T1[*] role a\n"
			+ "end\n";

	/** A derived subsetting end, whose links the validator does not hold in a relation. */
	private static final String DERIVED = "model M\n"
			+ "class T1\n"
			+ "end\n"
			+ "class T2 < T1\n"
			+ "end\n"
			+ "association bd between\n"
			+ "  T2[*] role d\n"
			+ "  T1[*] role b\n"
			+ "end\n"
			+ "association ac between\n"
			+ "  T2[*] role c subsets d derived = self.d\n"
			+ "  T1[*] role a\n"
			+ "end\n";

	private OutcomeRecorder validator;
	private Map<String, Object> properties;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
		properties = new HashMap<String, Object>();
		properties.put("T1_min", 0);
		properties.put("T1_max", 0);
		properties.put("T2_min", 1);
		properties.put("T2_max", 1);
	}

	@Test
	public void subsettingLinkWithoutSubsettedLinkIsUnsatisfiable() {
		links("ac", 1);
		links("bd", 0);
		validator.validate(configure(transform(SAME_POSITION)));
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void subsettingLinkWithSubsettedLinkIsSatisfiable() {
		links("ac", 1);
		links("bd", 1);
		validator.validate(configure(transform(SAME_POSITION)));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void subsettedLinkOfAnEndAtAnotherPositionLinksTheCorrespondingObjects() {
		properties.put("T3_min", 1);
		properties.put("T3_max", 1);
		links("ac", 1);
		links("bd", 1);
		IModel model = configure(transform(OTHER_POSITION));
		validator.validate(model);
		Tuple ac = validator.tuples(model.getAssociation("ac").relation()).iterator().next();
		Tuple bd = validator.tuples(model.getAssociation("bd").relation()).iterator().next();
		assertEquals(ac.atom(1) + ", " + ac.atom(0), bd.atom(0) + ", " + bd.atom(1));
	}

	@Test
	public void missingZeroOneLinkNeedsNoSubsettedLink() {
		links("ac", 0);
		links("bd", 0);
		validator.validate(configure(transform(ZERO_ONE)));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void subsetsOnADerivedAssociationIsRefused() {
		IModel model = transform(DERIVED);
		assertEquals(Arrays.asList("Model `M', whose transformation stopped: Cannot transform the constraint "
				+ "`subsets bd::d' on association end `ac::c': derived associations are not supported."),
				model.transformationErrors());
	}

	private void links(String association, int number) {
		properties.put(association + "_min", number);
		properties.put(association + "_max", number);
	}

	private static IModel transform(String spec) {
		MModel mModel = USECompiler.compileSpecification(new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8)), "M.use",
				new PrintWriter(System.err), new ModelFactory());
		return new ModelTransformator(new SimpleFactory(), new PrimitiveTypeFactory()).transform(mModel);
	}

	private IModel configure(IModel model) {
		StringWriter warnings = new StringWriter();
		PropertyConfigurationVisitor configurationVisitor = new PropertyConfigurationVisitor(new MapConfiguration(properties),
				new PrintWriter(warnings, true));
		model.accept(configurationVisitor);
		if (configurationVisitor.containErrors() || warnings.getBuffer().length() > 0) {
			fail("The configuration of the test model failed: " + warnings);
		}
		return model;
	}
}
