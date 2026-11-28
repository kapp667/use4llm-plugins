package org.tzi.kodkod.model.config.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
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
 * and must be reported, not ignored. A key of an attribute the model validator does not
 * translate is reported too, with the attribute it names: the visitor never reads it.
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
	 * The error lines for the keys of the section that cannot take effect: a key that names nothing of the
	 * model, and a key of an attribute that the translated model does not hold. In the order the section
	 * gives the keys.
	 *
	 * @param model
	 *            the USE model
	 * @param translated
	 *            the model the model validator translated from the USE model
	 */
	public static List<String> refusals(MModel model, IModel translated, Configuration config) {
		Map<String, String> notTranslated = new HashMap<String, String>();
		Set<String> known = knownKeys(model, translated, config, notTranslated);
		List<String> refusals = new ArrayList<String>();
		for (Iterator<String> keys = config.getKeys(); keys.hasNext();) {
			String key = keys.next();
			if (!known.contains(key)) {
				String attribute = notTranslated.get(key);
				refusals.add(attribute == null ? unknownMessage(key) : notTranslatedMessage(key, attribute));
			}
		}
		return refusals;
	}

	private static String unknownMessage(String key) {
		return "ERROR: " + key + ": this key names no class, attribute, association, invariant or option of the model. "
				+ "The model validator does not search with this configuration.";
	}

	private static String notTranslatedMessage(String key, String attribute) {
		return "ERROR: " + key + ": " + attribute + ", which the model validator does not translate, so this setting has no effect. "
				+ "The model validator does not search with this configuration.";
	}

	/**
	 * The keys that name something translated. The keys of an attribute that is not translated go to
	 * {@code notTranslated}, with the attribute they name.
	 */
	private static Set<String> knownKeys(MModel model, IModel translated, Configuration config,
			Map<String, String> notTranslated) {
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
			IClass translatedClass = translated.getClass(name);
			List<String> objects = objects(clazz, config);
			for (MAttribute attribute : clazz.attributes()) {
				String prefix = name + "_" + attribute.name();
				List<String> keys = new ArrayList<String>(Arrays.asList(prefix, prefix + PropertyEntry.attributeDefValuesMin,
						prefix + PropertyEntry.attributeDefValuesMax, prefix + PropertyEntry.attributeColSizeMin,
						prefix + PropertyEntry.attributeColSizeMax));
				for (String object : objects) {
					keys.add(object + "_" + attribute.name());
				}
				if (translatedClass != null && translatedClass.getAttribute(attribute.name()) != null) {
					known.addAll(keys);
				} else {
					String reason = "attribute " + name + "::" + attribute.name() + " has type " + attribute.type().shortName();
					for (String key : keys) {
						notTranslated.put(key, reason);
					}
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
