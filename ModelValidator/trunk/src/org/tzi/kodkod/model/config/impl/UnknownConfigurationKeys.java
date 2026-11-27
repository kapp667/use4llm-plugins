package org.tzi.kodkod.model.config.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;

/**
 * The keys of a configuration section that name something of the model. These are the
 * forms {@link PropertyConfigurationVisitor} reads; any other key of a section is unknown
 * and must be reported, not ignored.
 * <p>
 * The names come from the USE model, not from the transformed model: the transformation
 * leaves out some attributes (for example of type OclAny) and stops at the first element it
 * cannot transform, and a key of such an element names something of the model all the same.
 */
public final class UnknownConfigurationKeys {

	private static final String[] GLOBAL_KEYS = { TypeConstants.INTEGER, TypeConstants.INTEGER + PropertyEntry.integerValuesMin,
			TypeConstants.INTEGER + PropertyEntry.integerValuesMax, TypeConstants.STRING,
			TypeConstants.STRING + PropertyEntry.stringValuesMin, TypeConstants.STRING + PropertyEntry.stringValuesMax,
			TypeConstants.REAL, TypeConstants.REAL + PropertyEntry.realValuesMin,
			TypeConstants.REAL + PropertyEntry.realValuesMax, TypeConstants.REAL + PropertyEntry.realStep,
			PropertyEntry.aggregationcyclefreeness, PropertyEntry.forbiddensharing };

	private UnknownConfigurationKeys() {
	}

	/**
	 * The line logged for one unknown key.
	 */
	public static String message(String key) {
		return "ERROR: " + key + ": this key names no class, attribute, association, invariant or option of the model. "
				+ "The model validator does not search with this configuration.";
	}

	/**
	 * The keys of the section that name nothing of the model, in the order the section gives them.
	 */
	public static List<String> unknown(MModel model, Configuration config) {
		Set<String> known = knownKeys(model, config);
		List<String> unknown = new ArrayList<String>();
		for (Iterator<String> keys = config.getKeys(); keys.hasNext();) {
			String key = keys.next();
			if (!known.contains(key)) {
				unknown.add(key);
			}
		}
		return unknown;
	}

	private static Set<String> knownKeys(MModel model, Configuration config) {
		Set<String> known = new HashSet<String>(Arrays.asList(GLOBAL_KEYS));
		for (MAssociation association : model.associations()) {
			String name = association.name();
			known.add(name);
			known.add(name + PropertyEntry.linksMin);
			known.add(name + PropertyEntry.linksMax);
		}
		for (MClass clazz : model.classes()) {
			String name = clazz.name();
			known.add(name);
			known.add(name + PropertyEntry.objMin);
			known.add(name + PropertyEntry.objMax);
			if (clazz instanceof MAssociationClass) {
				known.add(name + PropertyEntry.ASSOCIATIONCLASS);
			}
			List<String> objects = objects(clazz, config);
			for (MAttribute attribute : clazz.attributes()) {
				String prefix = name + "_" + attribute.name();
				known.add(prefix);
				known.add(prefix + PropertyEntry.attributeDefValuesMin);
				known.add(prefix + PropertyEntry.attributeDefValuesMax);
				known.add(prefix + PropertyEntry.attributeColSizeMin);
				known.add(prefix + PropertyEntry.attributeColSizeMax);
				for (String object : objects) {
					known.add(object + "_" + attribute.name());
				}
			}
		}
		for (MClassInvariant invariant : model.classInvariants()) {
			known.add(invariant.qualifiedName().replaceFirst("::", "_"));
		}
		return known;
	}

	/**
	 * The objects listed for the class and its subclasses, as the visitor reads them.
	 */
	private static List<String> objects(MClass clazz, Configuration config) {
		List<String> objects = new ArrayList<String>();
		addObjects(clazz.name(), config, objects);
		if (clazz instanceof MAssociationClass) {
			addObjects(clazz.name() + PropertyEntry.ASSOCIATIONCLASS, config, objects);
		}
		for (MClass child : clazz.children()) {
			objects.addAll(objects(child, config));
		}
		return objects;
	}

	private static void addObjects(String key, Configuration config, List<String> objects) {
		for (String element : config.getStringArray(key)) {
			String object = element.trim();
			if (object.startsWith("Set{")) {
				object = object.substring(4);
			}
			if (object.endsWith("}")) {
				object = object.substring(0, object.length() - 1);
			}
			object = object.trim();
			if (!object.isEmpty() && !object.equals(TypeConstants.UNDEFINED) && !object.equals(TypeConstants.UNDEFINED_SET)) {
				objects.add(object);
			}
		}
	}
}
