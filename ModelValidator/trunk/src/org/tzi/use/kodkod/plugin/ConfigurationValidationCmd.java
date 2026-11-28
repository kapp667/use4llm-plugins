package org.tzi.use.kodkod.plugin;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.config.impl.UnknownConfigurationKeys;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.validation.ValidityRule;
import org.tzi.kodkod.validation.ValidityRuleValidator;
import org.tzi.kodkod.validation.ValidityRuleViolence;
import org.tzi.kodkod.validation.ui.UIElements;
import org.tzi.use.kodkod.plugin.gui.model.data.SettingsConfiguration;
import org.tzi.use.main.shell.runtime.IPluginShellCmd;

/**
 * CLI plugin for validation of configurations.
 * 
 * @author Jan Prien
 *
 */
public final class ConfigurationValidationCmd extends AbstractConfigurationProcessingCmd {

	/**
	 * The logger.
	 */
	protected static final Logger LOG = Logger.getLogger(ConfigurationValidationCmd.class);

	@Override
	public void performCommand(IPluginShellCmd pluginCommand) {
		if (!pluginCommand.getSession().hasSystem()) {
			LOG.error(UIElements.CLI_NoModelLoaded);
			return;
		}

		String[] arguments = pluginCommand.getCmdArgumentList();
		arguments = parseArguments(arguments);
		if (arguments == null || !validateArguments(arguments, LOG)) {
			return;
		}
		String configFilePath = extractConfigFilePath(arguments, LOG);
		String[] names = extractNames(arguments, LOG);

		initialize(pluginCommand.getSession());
		final IModel model = model();

		final LoadedConfigFileManager loaded = LoadedConfigFileManager.loadEditableState(model, mModel, configFilePath);
		if (loaded == null) {
			return;
		}
		final Map<String, SettingsConfiguration> configurationsForNames = loaded.getConfigurations();
		if (!validateNameArguments(configurationsForNames, names, LOG)) {
			return;
		}
		filterForNameArguments(configurationsForNames, names);
		processValidation(model, configurationsForNames, loaded.getSections(), loaded.getUnknownKeys(), LOG);
	}

	/**
	 * Processes the validation. A configuration is valid when no validity rule is
	 * violated and the loader of mv -validate accepts it. The loader runs on the
	 * model without searching.
	 *
	 * @param model
	 *            The class diagram of the UML/OCL model.
	 * @param configurationsForNames
	 *            The configurations for their names.
	 * @param sections
	 *            The configuration section of each name.
	 * @param log
	 *            The logger.
	 */
	private void processValidation(final IModel model, final Map<String, SettingsConfiguration> configurationsForNames,
			final Map<String, Configuration> sections, final Map<String, List<String>> unknownKeys, final Logger log) {
		String logString = "Validation results:\n\n";
		for (Entry<String, SettingsConfiguration> configurationForName : configurationsForNames.entrySet()) {
			String name = configurationForName.getKey();

			List<String> unknown = unknownKeys.get(name);
			if (unknown != null) {
				logString += "Config " + name + " is invalid.\n";
				for (String key : unknown) {
					logString += "    " + UnknownConfigurationKeys.message(key) + "\n";
				}
				logString += "\n";
				continue;
			}

			SettingsConfiguration config = configurationForName.getValue();
			SortedMap<ValidityRule, ValidityRuleViolence[]> violationsByValdityRules;
			try {
				violationsByValdityRules = new ValidityRuleValidator().validate(config, model, extractInvariants());
			} catch (Exception e) {
				log.error("Config " + name + " could not be validated.", e);
				logString += "Config " + name + " could not be validated.\n\n";
				continue;
			}
			if (violationsByValdityRules.size() != 0) {
				logString += "Config " + name + " is invalid.\n";
				logString += describeViolations(violationsByValdityRules) + "\n";
				continue;
			}
			String loadError = loadError(sections.get(name));
			if (loadError == null) {
				logString += "Config " + name + " is valid.\n\n";
			} else {
				logString += "Config " + name + " is invalid.\n";
				logString += "    " + loadError + "\n\n";
			}
		}
		log.info(logString);
	}

	/**
	 * Loads the configuration on the model as mv -validate does, without searching.
	 *
	 * @param config
	 *            The configuration section.
	 * @return Null if the loader accepts the configuration, else the message of the
	 *         error that the loader raised.
	 */
	private String loadError(final Configuration config) {
		try {
			configureModel(config, new PrintWriter(new StringWriter(), true));
			return null;
		} catch (ConfigurationException e) {
			return e.getMessage() != null ? e.getMessage() : LogMessages.configurationRefused;
		} catch (RuntimeException e) {
			return e.getMessage() != null ? e.getMessage() : e.getClass().getName();
		}
	}

	/**
	 * Validates the arguments. At least one argument must be given.
	 * 
	 * @param arguments
	 *            The arguments.
	 * @param log
	 *            The log.
	 * @return Whether at least one argument is given.
	 */
	private boolean validateArguments(String[] arguments, Logger log) {
		if (arguments == null || log == null) {
			throw new IllegalArgumentException();
		}
		if (arguments.length < 1) {
			LOG.error(UIElements.CLI_NoConfigFileArgument);
			return false;
		}
		return true;
	}

	/**
	 * Validates configuration names given as arguments.
	 * 
	 * @param configurationsForNames
	 *            The existing configurations for their names.
	 * @param names
	 *            The configuration names given as arguments.
	 * @param LOG
	 *            The logger.
	 * @return Whether every name given is a configuration of the file.
	 */
	private static boolean validateNameArguments(Map<String, SettingsConfiguration> configurationsForNames,
			String[] names, final Logger LOG) {
		if (configurationsForNames == null || names == null || LOG == null) {
			throw new IllegalArgumentException();
		}
		if (names.length != 0) {
			List<String> namesWithoutDuplicates = new ArrayList<>();
			List<String> duplicatedNames = new ArrayList<>();
			for (String name : names) {
				if (!namesWithoutDuplicates.contains(name)) {
					namesWithoutDuplicates.add(name);
				} else {
					duplicatedNames.add(name);
				}
			}
			List<String> notContainedNames = new ArrayList<>();
			for (String name : names) {
				if (!configurationsForNames.containsKey(name)) {
					notContainedNames.add(name);
				}
			}
			if (!duplicatedNames.isEmpty()) {
				LOG.warn(UIElements.CLI_MultipleNamesArguments(duplicatedNames));
			}
			if (!notContainedNames.isEmpty()) {
				LOG.error(UIElements.CLI_UncontainedNamesArgumentss(notContainedNames));
				return false;
			}
		}
		return true;
	}

}
