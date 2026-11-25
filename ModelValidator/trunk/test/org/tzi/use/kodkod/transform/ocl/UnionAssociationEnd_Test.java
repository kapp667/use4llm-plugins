package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.MapConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.impl.SimpleFactory;
import org.tzi.kodkod.model.type.PrimitiveTypeFactory;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.kodkod.ocl.OCLGroupRegistry;
import org.tzi.kodkod.ocl.operation.AnyOperationGroup;
import org.tzi.kodkod.ocl.operation.BooleanOperationGroup;
import org.tzi.kodkod.ocl.operation.ClassOperationGroup;
import org.tzi.kodkod.ocl.operation.CollectionConstructorGroup;
import org.tzi.kodkod.ocl.operation.ConditionalOperationGroup;
import org.tzi.kodkod.ocl.operation.IntegerOperationGroup;
import org.tzi.kodkod.ocl.operation.SetOperationGroup;
import org.tzi.kodkod.ocl.operation.VariableOperationGroup;
import org.tzi.use.kodkod.transform.ModelTransformator;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;

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
		validator.validate(configure(transform(SPEC)));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void unionWithALinkOfNoSubsettingAssociationIsUnsatisfiable() {
		links("ab", 3);
		validator.validate(configure(transform(SPEC)));
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void unionWithoutConfiguredLinksIsNotLimitedToTheDefaultNumber() {
		validator.validate(configure(transform(SPEC)));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	@Test
	public void navigationThroughAUnionEndGivesTheSubsettingLinksOnly() {
		properties.put("ab_min", 0);
		properties.put("ab_max", -1);
		validator.validate(configure(transform(NAVIGATION)));
		assertTrue(validator.outcome, String.valueOf(validator.outcome).endsWith("UNSATISFIABLE"));
	}

	@Test
	public void navigationThroughAUnionEndGivesTheSubsettingLink() {
		properties.put("ab_min", 0);
		properties.put("ab_max", -1);
		validator.validate(configure(transform(NAVIGATION_CONTROL)));
		assertEquals("SATISFIABLE", validator.outcome);
	}

	private void links(String association, int number) {
		properties.put(association + "_min", number);
		properties.put(association + "_max", number);
	}

	private static IModel transform(String spec) {
		MModel mModel = USECompiler.compileSpecification(new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8)), "M.use",
				new PrintWriter(System.err), new ModelFactory());
		TypeFactory typeFactory = new PrimitiveTypeFactory();
		OCLGroupRegistry registry = OCLGroupRegistry.INSTANCE;
		registry.unregisterAll();
		registry.registerOperationGroup(new VariableOperationGroup(typeFactory));
		registry.registerOperationGroup(new IntegerOperationGroup(typeFactory));
		registry.registerOperationGroup(new BooleanOperationGroup(typeFactory));
		registry.registerOperationGroup(new ClassOperationGroup(typeFactory));
		registry.registerOperationGroup(new AnyOperationGroup(typeFactory, true));
		registry.registerOperationGroup(new ConditionalOperationGroup(typeFactory));
		registry.registerOperationGroup(new SetOperationGroup(typeFactory));
		registry.registerOperationGroup(new CollectionConstructorGroup(typeFactory));
		IModel model = new ModelTransformator(new SimpleFactory(), typeFactory).transform(mModel);
		assertEquals("[]", model.transformationErrors().toString());
		return model;
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
