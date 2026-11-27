package org.tzi.use.kodkod.transform.ocl;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.WriterAppender;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.tzi.kodkod.KodkodModelValidator;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.plugin.KodkodValidateCmd;

public class OclAnyAttribute_Test {

	private static final String ONE_X = "model M\n"
			+ "class A\n"
			+ "attributes\n"
			+ "  x : OclAny\n"
			+ "end\n"
			+ "constraints\n"
			+ "context A inv one: self.x = 1\n";

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private OutcomeRecorder validator;

	@Before
	public void setUp() throws Exception {
		KodkodModelValidatorConfiguration.getInstance().setSatFactory(KodkodModelValidatorConfiguration.DEFAULT_SATFACTORY);
		validator = new OutcomeRecorder();
	}

	@Test
	public void invariantOnAnOclAnyAttributeIsRefused() {
		validator.validate(oneObjectOfA(ONE_X));
		assertNull(validator.outcome);
	}

	@Test
	public void refusalNamesTheOclAnyAttributeAndItsType() {
		IModel model = oneObjectOfA(ONE_X);
		StringWriter log = new StringWriter();
		Logger logger = Logger.getLogger(KodkodModelValidator.class);
		WriterAppender appender = new WriterAppender(new PatternLayout("%m%n"), log);
		logger.addAppender(appender);
		try {
			validator.validate(model);
		} finally {
			logger.removeAppender(appender);
		}
		assertTrue(log.toString(), log.toString().contains("A::x"));
		assertTrue(log.toString(), log.toString().contains("OclAny"));
	}

	@Test
	public void invariantOnASetOfOclAnyAttributeIsRefused() {
		validator.validate(oneObjectOfA("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  s : Set(OclAny)\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv two: self.s = Set{2}\n"));
		assertNull(validator.outcome);
	}

	@Test
	public void invariantOnADerivedOclAnyAttributeIsSolved() {
		validator.validate(oneObjectOfA("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  d : OclAny derive: 1\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv one: self.d = 1\n"));
		assertThat(validator.outcome, anyOf(equalTo("SATISFIABLE"), equalTo("TRIVIALLY_SATISFIABLE")));
	}

	@Test
	public void extractedStateWithAnOclAnyValueIsSolvedWithoutError() throws Exception {
		KodkodModelValidatorConfiguration configuration = KodkodModelValidatorConfiguration.getInstance();
		boolean extraction = configuration.isAutomaticDiagramExtraction();
		configuration.setAutomaticDiagramExtraction(true);
		PluginShell shell = new PluginShell("model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  x : OclAny\n"
				+ "  k : Integer\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv positive: self.k > 0\n");
		try {
			shell.type("!create a : A");
			shell.type("!a.x := 1");
			shell.type("!a.k := 2");
			File properties = folder.newFile("a.properties");
			Files.write(properties.toPath(), "[a]\nInteger_min = 0\nInteger_max = 3\nA_min = 1\nA_max = 1\n".getBytes(StandardCharsets.UTF_8));
			String output = shell.run(new KodkodValidateCmd(), properties.getPath());
			assertTrue(output, output.matches("(?s).*INFO (TRIVIALLY_)?SATISFIABLE.*"));
			assertFalse(output, output.contains("ERROR"));
		} finally {
			shell.close();
			configuration.setAutomaticDiagramExtraction(extraction);
		}
	}

	private static IModel oneObjectOfA(String spec) {
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put("Integer_min", 0);
		properties.put("Integer_max", 3);
		properties.put("A_min", 1);
		properties.put("A_max", 1);
		return TestSpecifications.configure(TestSpecifications.transform(spec), properties);
	}
}
