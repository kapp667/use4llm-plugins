package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Pattern;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.Test;
import org.tzi.kodkod.model.config.impl.ModelConfigurator;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.plugin.PluginModelFactory;
import org.tzi.use.kodkod.plugin.gui.model.data.SettingsConfiguration;
import org.tzi.use.kodkod.plugin.gui.util.ChangeConfiguration;

/**
 * The state of an invariant and the on/off options of a configuration file are read
 * ignoring case, by the loader that configures the search and by the reader behind mv -vc
 * and the pre-search check alike. A value that is none of the possible ones is an error:
 * the search does not run and mv -vc does not call the configuration valid.
 */
public class InvariantAndOptionValues_Test extends MvCommandSession {

	private static final String SPEC = "model Pets\n"
			+ "class Person\nattributes\n  name : String\n  age : Integer\nend\n"
			+ "class Pet\nattributes\n  nick : String\nend\n"
			+ "association Owns between\n  Person[1] role owner\n  Pet[0..*] role pets\nend\n"
			+ "constraints\ncontext Person inv adult: self.age >= 18\ncontext Pet inv hasNick: self.nick <> ''\n";

	private static String configuration(String name, String extraLines) {
		return "[" + name + "]\nInteger_min = -10\nInteger_max = 40\nString_max = 10\n"
				+ "Person_min = 1\nPerson_max = 1\nPerson_name = Set{'Ada'}\nPerson_age = Set{30}\n"
				+ "Pet_min = 1\nPet_max = 2\nPet_nick = Set{'Rex','Tom'}\nOwns_min = 1\nOwns_max = 2\n"
				+ extraLines;
	}

	private String properties(String name, String extraLines) throws Exception {
		open(SPEC);
		return file(name + ".properties", configuration(name, extraLines));
	}

	private static boolean hasVerdict(String output) {
		return output.contains("SATISFIABLE");
	}

	private static void assertNoDefaultReplacement(String output) {
		assertFalse(output, output.contains("Using default"));
		assertFalse(output, output.contains("is not possible for an invariant"));
		assertFalse(output, output.contains("Only on and off"));
	}

	private static boolean invariantError(String output, String invariant, String value) {
		return Pattern.compile("ERROR A configuration value cannot be read: " + Pattern.quote(invariant)
				+ ": .{0,2}" + Pattern.quote(value) + ".{0,2} is not a state of an invariant\\.").matcher(output).find();
	}

	private static boolean optionError(String output, String option, String value) {
		return Pattern.compile("ERROR A configuration value cannot be read: " + option
				+ ": .{0,2}" + Pattern.quote(value) + ".{0,2} is not a value of " + option + "\\.").matcher(output).find();
	}

	private static PropertiesConfiguration read(String lines) throws Exception {
		PropertiesConfiguration pc = new PropertiesConfiguration();
		pc.load(new StringReader(lines));
		return pc;
	}

	private IModel load(String lines) throws Exception {
		open(SPEC);
		IModel model = PluginModelFactory.INSTANCE.getModel(session.system().model());
		model.reset();
		model.accept(new PropertyConfigurationVisitor(read(lines), new PrintWriter(new StringWriter())));
		return model;
	}

	private static IInvariant invariant(IModel model, String className, String name) {
		for (IInvariant inv : model.getClass(className).invariants()) {
			if (inv.name().equals(name)) {
				return inv;
			}
		}
		throw new AssertionError("no invariant " + className + "::" + name);
	}

	private static SettingsConfiguration settings(IModel model, String lines) throws Exception {
		SettingsConfiguration settings = new SettingsConfiguration(model);
		ChangeConfiguration.toSettings(model, read(lines), settings);
		return settings;
	}

	@Test
	public void validateHonoursANegateStateWrittenInCapitals() throws Exception {
		String output = validate(properties("neg-cap", "Person_adult = Negate\nPet_hasNick = active\n"));
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
		assertNoDefaultReplacement(output);
	}

	@Test
	public void validateHonoursAnInactiveStateWrittenInCapitals() throws Exception {
		String output = validate(properties("inact-cap", "Person_adult = INACTIVE\nPet_hasNick = active\n"));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("WARN"));
		assertNoDefaultReplacement(output);
		assertTrue(useCheckPasses());
	}

	@Test
	public void loaderDeactivatesAnInvariantWhoseStateIsWrittenInCapitals() throws Exception {
		IModel model = load("Person_adult = INACTIVE\n");
		assertFalse(invariant(model, "Person", "adult").isActivated());
		assertTrue(invariant(model, "Pet", "hasNick").isActivated());
	}

	@Test
	public void validateRefusesAnUnknownInvariantState() throws Exception {
		String output = validate(properties("inv-bad", "Person_adult = maybe\nPet_hasNick = active\n"));
		assertTrue(output, invariantError(output, "Person::adult", "maybe"));
		assertTrue(output, output.contains("The model validator does not search with this configuration."));
		assertFalse(output, hasVerdict(output));
	}

	@Test
	public void configCheckDoesNotCallAnUnknownInvariantStateValid() throws Exception {
		String output = configCheck(properties("inv-bad", "Person_adult = maybe\nPet_hasNick = active\n"));
		assertTrue(output, output.contains("ERROR"));
		assertTrue(output, output.contains("Person::adult"));
		assertTrue(output, output.contains("maybe"));
		assertFalse(output, output.contains("Config inv-bad is valid."));
	}

	@Test
	public void bothReadersAcceptAnOptionOffWrittenInCapitals() throws Exception {
		String lines = "aggregationcyclefreeness = Off\nforbiddensharing = OFF\n";
		String output = validate(properties("agg-offcap", lines));
		assertNoDefaultReplacement(output);
		assertFalse(output, output.contains("ERROR"));

		IModel model = load(lines);
		ModelConfigurator configurator = (ModelConfigurator) model.getConfigurator();
		assertFalse(configurator.isAggregationCycleFree());
		assertFalse(configurator.isForbiddensharing());
		SettingsConfiguration settings = settings(model, lines);
		assertFalse(settings.getOptionSettings().isAggregationcyclefreeness());
		assertFalse(settings.getOptionSettings().isForbiddensharing());
	}

	@Test
	public void bothReadersAcceptAnOptionOnWrittenInCapitals() throws Exception {
		String lines = "aggregationcyclefreeness = ON\nforbiddensharing = On\n";
		IModel model = load(lines);
		ModelConfigurator configurator = (ModelConfigurator) model.getConfigurator();
		assertTrue(configurator.isAggregationCycleFree());
		assertTrue(configurator.isForbiddensharing());
		SettingsConfiguration settings = settings(model, lines);
		assertTrue(settings.getOptionSettings().isAggregationcyclefreeness());
		assertTrue(settings.getOptionSettings().isForbiddensharing());
	}

	@Test
	public void validateRefusesAnUnknownAggregationCycleFreenessValue() throws Exception {
		String path = properties("agg-maybe", "aggregationcyclefreeness = maybe\n");
		String output = validate(path);
		assertTrue(output, optionError(output, "aggregationcyclefreeness", "maybe"));
		assertTrue(output, output.contains("The model validator does not search with this configuration."));
		assertFalse(output, hasVerdict(output));

		log.getBuffer().setLength(0);
		String vc = configCheck(path);
		assertTrue(vc, optionError(vc, "aggregationcyclefreeness", "maybe"));
		assertFalse(vc, vc.contains("Config agg-maybe is valid."));
	}

	@Test
	public void validateRefusesAnUnknownForbiddenSharingValue() throws Exception {
		String path = properties("fs-maybe", "forbiddensharing = maybe\n");
		String output = validate(path);
		assertTrue(output, optionError(output, "forbiddensharing", "maybe"));
		assertFalse(output, hasVerdict(output));

		log.getBuffer().setLength(0);
		String vc = configCheck(path);
		assertTrue(vc, optionError(vc, "forbiddensharing", "maybe"));
		assertFalse(vc, vc.contains("Config fs-maybe is valid."));
	}

	@Test
	public void aLowerCaseNegateStillGivesUnsatisfiableAndAValidConfiguration() throws Exception {
		String path = properties("neg-low", "Person_adult = negate\nPet_hasNick = active\n");
		String output = validate(path);
		assertTrue(output, output.contains("INFO UNSATISFIABLE"));
		assertFalse(output, output.contains("WARN"));
		assertFalse(output, output.contains("ERROR"));

		log.getBuffer().setLength(0);
		String vc = configCheck(path);
		assertTrue(vc, vc.contains("Config neg-low is valid."));
	}

	@Test
	public void lowerCaseActiveAndOptionsStillSolve() throws Exception {
		String path = properties("lower", "Person_adult = active\nPet_hasNick = active\n"
				+ "aggregationcyclefreeness = on\nforbiddensharing = off\n");
		String output = validate(path);
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("ERROR"));
		assertNoDefaultReplacement(output);
		assertTrue(useCheckPasses());
	}

	@Test
	public void aFileWithoutInvariantAndOptionKeysUsesTheDefaultsSilently() throws Exception {
		String output = validate(properties("plain", ""));
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("WARN"));
		assertNoDefaultReplacement(output);

		IModel model = load("");
		assertTrue(invariant(model, "Person", "adult").isActivated());
		assertFalse(invariant(model, "Person", "adult").isNegated());
		ModelConfigurator configurator = (ModelConfigurator) model.getConfigurator();
		assertEquals(true, configurator.isAggregationCycleFree());
		assertEquals(true, configurator.isForbiddensharing());
	}
}
