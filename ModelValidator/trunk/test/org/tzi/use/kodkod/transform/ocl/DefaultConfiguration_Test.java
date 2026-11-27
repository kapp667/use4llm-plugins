package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileReader;

import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.config.impl.DefaultConfigurationVisitor;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.UseKodkodModelValidator;
import org.tzi.use.kodkod.plugin.USECommentFilterReader;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;

/**
 * Checks that the default configuration, the one the validator writes when it
 * is given no properties file, gives an object to a class with a Real
 * attribute or an OclAny attribute.
 */
public class DefaultConfiguration_Test {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void classWithARealAttributeGetsAnObject() throws Exception {
		String spec = "model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  salary : Real\n"
				+ "end\n";
		assertEquals(1, objectsOfTheDefaultSolution(spec));
	}

	@Test
	public void classWithAnOclAnyAttributeGetsAnObject() throws Exception {
		String spec = "model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  x : OclAny\n"
				+ "end\n";
		assertEquals(1, objectsOfTheDefaultSolution(spec));
	}

	private int objectsOfTheDefaultSolution(String spec) throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		MModel mModel = TestSpecifications.compile(spec);
		IModel model = TestSpecifications.transform(mModel);
		DefaultConfigurationVisitor defaults = new DefaultConfigurationVisitor(new File(folder.getRoot(), "M.use").getPath());
		model.accept(defaults);
		HierarchicalINIConfiguration file = new HierarchicalINIConfiguration();
		file.load(new USECommentFilterReader(new FileReader(defaults.getFile())));
		TestSpecifications.configure(model, file.getSection(file.getSections().iterator().next()));
		Session session = new Session();
		session.setSystem(new MSystem(mModel));
		new UseKodkodModelValidator(session).validate(model);
		return session.system().state().allObjects().size();
	}
}
