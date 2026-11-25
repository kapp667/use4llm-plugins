package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.MapConfiguration;
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
 * Transforms and configures the small specifications that tests write inline.
 */
final class TestSpecifications {

	private TestSpecifications() {
	}

	/**
	 * Compiles the specification and transforms it as
	 * {@link #transform(MModel)} does.
	 */
	static IModel transform(String spec) {
		return transform(compile(spec));
	}

	/**
	 * Compiles the specification, and fails the test if it does not compile.
	 */
	static MModel compile(String spec) {
		MModel mModel = USECompiler.compileSpecification(new ByteArrayInputStream(spec.getBytes(StandardCharsets.UTF_8)), "M.use",
				new PrintWriter(System.err), new ModelFactory());
		if (mModel == null) {
			fail("The test specification does not compile.");
		}
		return mModel;
	}

	/**
	 * Transforms the model with the operation groups the plugin registers, so
	 * that the result does not depend on the tests that ran before.
	 */
	static IModel transform(MModel mModel) {
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
		return new ModelTransformator(new SimpleFactory(), typeFactory).transform(mModel);
	}

	/**
	 * Configures the model with the given properties, as a properties file
	 * would, and fails the test if the configuration reports a problem.
	 */
	static IModel configure(IModel model, Map<String, Object> properties) {
		return configure(model, new MapConfiguration(properties));
	}

	/**
	 * Configures the model with the given configuration, and fails the test if the
	 * configuration reports a problem.
	 */
	static IModel configure(IModel model, Configuration configuration) {
		StringWriter warnings = new StringWriter();
		PropertyConfigurationVisitor configurationVisitor = new PropertyConfigurationVisitor(configuration,
				new PrintWriter(warnings, true));
		model.accept(configurationVisitor);
		if (configurationVisitor.containErrors() || warnings.getBuffer().length() > 0) {
			fail("The configuration of the test model failed: " + warnings);
		}
		return model;
	}
}
