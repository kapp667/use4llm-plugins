package org.tzi.use.kodkod.plugin;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.config.impl.DefaultConfigurationVisitor;
import org.tzi.kodkod.model.config.impl.PropertyConfigurationVisitor;
import org.tzi.kodkod.validation.AbstractFix;
import org.tzi.kodkod.validation.ValidityRule;
import org.tzi.kodkod.validation.ValidityRuleValidator;
import org.tzi.kodkod.validation.ValidityRuleViolence;
import org.tzi.use.kodkod.plugin.gui.model.data.SettingsConfiguration;
import org.tzi.use.kodkod.plugin.gui.util.ChangeConfiguration;

/**
 * A base class for plugins that can be configured using property files.
 * With just a file name without a sector name given, the first sector of
 * the properties file will be used. 
 * 
 * @author Frank Hilken
 */
public abstract class ConfigurablePlugin extends AbstractPlugin {

	/**
	 * Configuration of the model with the data from the given configuration.
	 * 
	 * @throws ConfigurationException
	 *             If the configuration breaks a validity rule, after an error
	 *             naming each broken rule is logged.
	 */
	protected void configureModel(Configuration config, PrintWriter warningsOut) throws ConfigurationException {
		checkConfiguration(config);
		model().reset();
		PropertyConfigurationVisitor newConfigurationVisitor = new PropertyConfigurationVisitor(config, warningsOut);
		model().accept(newConfigurationVisitor);
		
		if (newConfigurationVisitor.containErrors()) {
			throw new ConfigurationException(LogMessages.configurationError);
		}
		
		LOG.info(LogMessages.modelConfigurationSuccessful);
	}
	
	/**
	 * Configuration with the default search space.
	 */
	protected void configureModel(PrintWriter warningsOut) throws IOException, ConfigurationException {
		model().reset();
		DefaultConfigurationVisitor configurationVisitor = new DefaultConfigurationVisitor(mModel.filename());
		model().accept(configurationVisitor);
		configureModel(extractConfigFromFile(configurationVisitor.getFile()), warningsOut);
		
		LOG.info(LogMessages.modelConfigurationSuccessful);
	}
	
	/**
	 * Checks the configuration against the validity rules whose violation makes it
	 * unusable: every rule of mv -vc except those of superfluous or imperfect settings.
	 * 
	 * @throws ConfigurationException
	 *             If a rule is broken or a value cannot be read, after an error
	 *             that says why is logged.
	 */
	private void checkConfiguration(Configuration config) throws ConfigurationException {
		SettingsConfiguration settings = new SettingsConfiguration(model());
		SortedMap<ValidityRule, ValidityRuleViolence[]> violations;
		try {
			ChangeConfiguration.toSettings(model(), config, settings);
			violations = new TreeMap<>(new ValidityRuleValidator().validate(settings, model(), extractInvariants()));
		} catch (ConversionException e) {
			LOG.error(LogMessages.configurationValueError + ": " + e.getMessage());
			throw new ConfigurationException(LogMessages.configurationRefused);
		} catch (Exception e) {
			LOG.error(LogMessages.configurationCheckError, e);
			throw new ConfigurationException(LogMessages.configurationRefused);
		}
		violations.keySet().removeIf(rule -> !rule.breaksTheConfiguration());
		if (!violations.isEmpty()) {
			LOG.error(LogMessages.configurationInvalid + "\n" + describeViolations(violations));
			throw new ConfigurationException(LogMessages.configurationRefused);
		}
	}

	/**
	 * Describes each violated rule with its violations and the fixes they offer.
	 * 
	 * @param violationsByValidityRules
	 *            The violations of validity rules for the rules.
	 * @return The description, a line for each rule, violation and fix.
	 */
	static String describeViolations(SortedMap<ValidityRule, ValidityRuleViolence[]> violationsByValidityRules) {
		String logString = "";
		for (Entry<ValidityRule, ValidityRuleViolence[]> violationsByValidityRule : violationsByValidityRules
				.entrySet()) {
			logString += "    Rule " + violationsByValidityRule.getKey().name() + " is violated.\n";
			for (ValidityRuleViolence violation : violationsByValidityRule.getValue()) {
				logString += "        " + violation.getExplenation() + "\n";
				AbstractFix[] fixes = violation.getFixes();
				if (fixes.length == 1) {
					logString += "            To get closer to validity perform the following fix:\n";
				} else if (fixes.length > 1) {
					logString += "            To get closer to validity perform one of the following fixes:\n";
				}
				for (AbstractFix fix : fixes) {
					logString += "                " + (fix.resolvesProblemCompletely() ? "[fF]" : "[pF]") + " | "
							+ fix.getExplenation() + "\n";
				}
			}
		}
		return logString;
	}

	protected Configuration extractConfigFromFile(File file) throws ConfigurationException {
		HierarchicalINIConfiguration hierarchicalINIConfiguration = readConfiguration(file);
		if(hierarchicalINIConfiguration.getSections().isEmpty()){
			return hierarchicalINIConfiguration.getSection(null);
		} else {
			String section = hierarchicalINIConfiguration.getSections().iterator().next();
			return hierarchicalINIConfiguration.getSection(section);
		}
	}
	
	protected Configuration extractConfigFromFile(File file, String section) throws ConfigurationException {
		HierarchicalINIConfiguration hierarchicalINIConfiguration = readConfiguration(file);
		if(!hierarchicalINIConfiguration.getSections().contains(section)){
			if(section == null){
				String sName = hierarchicalINIConfiguration.getSections().iterator().next();
				return hierarchicalINIConfiguration.getSection(sName);
			} else {
				throw new ConfigurationException("Selected section does not exist in properties file.");
			}
		}
		
		return hierarchicalINIConfiguration.getSection(section);
	}
	
	private HierarchicalINIConfiguration readConfiguration(File f) throws ConfigurationException {
		HierarchicalINIConfiguration hierarchicalINIConfiguration = new HierarchicalINIConfiguration();
		try {
			hierarchicalINIConfiguration.load(new USECommentFilterReader(new FileReader(f)));
		} catch (IOException ex) {
			throw new ConfigurationException(ex);
		}
		return hierarchicalINIConfiguration;
	}
	
}
