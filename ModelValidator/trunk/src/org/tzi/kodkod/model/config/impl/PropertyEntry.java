package org.tzi.kodkod.model.config.impl;

import java.util.Locale;

import org.apache.commons.configuration.ConversionException;



/**
 * Contains the names for the configuration file.
 * 
 * @author Hendrik Reitmann
 * 
 */

public class PropertyEntry {

	/*
	 * "String" + stringValuesMin = ...
	 * "String" + stringValuesMax = ...
	 */
	public static final String stringValuesMin = "_min";
	public static final String stringValuesMax = "_max";

	/*
	 * "Real" + realValueMin = ...
	 * "Real" + realValueMax = ...
	 * "Real" + realStep = ...
	 */
	public static final String realValuesMin = "_min";
	public static final String realValuesMax = "_max";
	public static final String realStep = "_step";

	/*
	 * "Integer" + integerValueMin = ...
	 * "Integer" + integerValueMax = ...
	 */
	public static final String integerValuesMin = "_min";
	public static final String integerValuesMax = "_max";

	/*
	 * Classname + objMin = ...
	 * Classname + objMax = ...
	 */
	public static final String objMin = "_min";
	public static final String objMax = "_max";

	/*
	 * Associationname + linksMin = ...
	 * Associationname + linksMin = ...
	 */
	public static final String linksMin = "_min";
	public static final String linksMax = "_max";

	/*
	 * Classname_attributename + attributeDefValuesMin = ...
	 * Classname_attributename + attributeDefValuesMax = ... 
	 * Classname_attributename + attributeColSizeMin = ... 
	 * Classname_attributename + attributeColSizeMax = ...
	 */
	public static final String attributeDefValuesMin = "_min";
	public static final String attributeDefValuesMax = "_max";
	public static final String attributeColSizeMin = "_minSize";
	public static final String attributeColSizeMax = "_maxSize";
	
	public static final String aggregationcyclefreeness = "aggregationcyclefreeness";
	public static final String forbiddensharing = "forbiddensharing";
	
	public static final String COMMENT_LABEL = "# ";
	public static final int LINE_WIDTH = 80;
	public static final String DEFAULT_SECTION_NAME = "default";
	
	public static final String ASSOCIATIONCLASS = "_ac";
	
	public static final String INVARIANT_ACTIVE = "active";
	public static final String INVARIANT_INACTIVE = "inactive";
	public static final String INVARIANT_NEGATE = "negate";
	

	/**
	 * Reads the state of an invariant. The state is active, inactive or negate, in any case.
	 *
	 * @param invariantName
	 *            The name of the invariant as Class::name, for the message.
	 * @param value
	 *            The state as the file gives it.
	 * @return The state in lower case.
	 * @throws ConversionException
	 *             If the value is none of the three states.
	 */
	public static String readInvariantState(String invariantName, String value) throws ConversionException {
		String state = value.trim().toLowerCase(Locale.ROOT);
		if (state.equals(INVARIANT_ACTIVE) || state.equals(INVARIANT_INACTIVE) || state.equals(INVARIANT_NEGATE)) {
			return state;
		}
		throw new ConversionException(invariantName + ": '" + value + "' is not a state of an invariant.");
	}

	/**
	 * Reads the value of an on/off option. The value is on or off, in any case.
	 *
	 * @param optionName
	 *            The name of the option, for the message.
	 * @param value
	 *            The value as the file gives it.
	 * @return Whether the option is on.
	 * @throws ConversionException
	 *             If the value is neither on nor off.
	 */
	public static boolean readOnOff(String optionName, String value) throws ConversionException {
		String trimmed = value.trim();
		if (trimmed.equalsIgnoreCase("on")) {
			return true;
		}
		if (trimmed.equalsIgnoreCase("off")) {
			return false;
		}
		throw new ConversionException(optionName + ": '" + value + "' is not a value of " + optionName + ".");
	}
}
