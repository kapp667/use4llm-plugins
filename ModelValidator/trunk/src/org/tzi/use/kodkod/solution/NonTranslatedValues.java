package org.tzi.use.kodkod.solution;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.TupleType;
import org.tzi.use.uml.ocl.value.BagValue;
import org.tzi.use.uml.ocl.value.CollectionValue;
import org.tzi.use.uml.ocl.value.ObjectValue;
import org.tzi.use.uml.ocl.value.OrderedSetValue;
import org.tzi.use.uml.ocl.value.SequenceValue;
import org.tzi.use.uml.ocl.value.SetValue;
import org.tzi.use.uml.ocl.value.TupleValue;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystemState;

/**
 * The defined values of the attributes that the translation leaves out, such as an OclAny or a Tuple
 * attribute. The solver never sees them, so the object diagram of a solution does not hold them. They
 * are kept from the state the user has, before USE resets it, and set on the objects of the new diagram.
 */
public final class NonTranslatedValues {

	private static final Logger LOG = Logger.getLogger(NonTranslatedValues.class);

	private final List<Entry> entries = new ArrayList<Entry>();

	private static final class Entry {
		private final String objectName;
		private final MAttribute attribute;
		private final Value value;

		private Entry(String objectName, MAttribute attribute, Value value) {
			this.objectName = objectName;
			this.attribute = attribute;
			this.value = value;
		}
	}

	private NonTranslatedValues() {
	}

	/**
	 * Collects the defined values of the non-derived attributes of the objects of the state that the model
	 * does not translate.
	 *
	 * @throws IllegalStateException
	 *             if such a value refers to an object that is not in the state, so it cannot be kept
	 */
	public static NonTranslatedValues collect(IModel model, MSystemState state) {
		NonTranslatedValues values = new NonTranslatedValues();
		for (MObject object : state.allObjects()) {
			IClass clazz = model.getClass(object.cls().name());
			for (MAttribute attribute : object.cls().allAttributes()) {
				if (attribute.isDerived() || clazz.getAttribute(attribute.name()) != null) {
					continue;
				}
				Value value = object.state(state).attributeValue(attribute);
				if (value.isUndefined()) {
					continue;
				}
				if (remap(value, state, state) == null) {
					throw new IllegalStateException(LogMessages.nonTranslatedValueError(object.name(), attribute.name()));
				}
				values.entries.add(new Entry(object.name(), attribute, value));
			}
		}
		return values;
	}

	/**
	 * Sets the kept values on the objects of the same name in the given state. An object of a value is
	 * replaced by the object of the same name in that state.
	 *
	 * @return false if a value cannot be set, after an error for it has been logged
	 */
	public boolean restore(UseSystemApi systemApi, MSystemState target) throws UseApiException {
		boolean restored = true;
		for (Entry entry : entries) {
			MObject object = target.objectByName(entry.objectName);
			Value value = object == null ? null : remap(entry.value, null, target);
			if (value == null) {
				LOG.error(LogMessages.nonTranslatedValueError(entry.objectName, entry.attribute.name()));
				restored = false;
			} else {
				systemApi.setAttributeValueEx(object, entry.attribute, value);
			}
		}
		return restored;
	}

	/**
	 * Returns the value with each object in it replaced by the object of the same name in the target state,
	 * or null if an object of it is not in the target state. A value of a tuple that holds an object cannot
	 * be rebuilt, so it gives null too. The source is null when the value is not taken from the target state.
	 */
	private static Value remap(Value value, MSystemState source, MSystemState target) {
		if (value instanceof ObjectValue) {
			MObject object = ((ObjectValue) value).value();
			MObject mapped = target.objectByName(object.name());
			if (mapped == null || source == target && mapped != object) {
				return null;
			}
			return new ObjectValue(mapped.cls(), mapped);
		}
		if (value instanceof CollectionValue) {
			CollectionValue collection = (CollectionValue) value;
			List<Value> elements = new ArrayList<Value>();
			for (Value element : collection.collection()) {
				Value mapped = remap(element, source, target);
				if (mapped == null) {
					return null;
				}
				elements.add(mapped);
			}
			if (value instanceof SetValue) {
				return new SetValue(collection.elemType(), elements);
			} else if (value instanceof BagValue) {
				return new BagValue(collection.elemType(), elements);
			} else if (value instanceof SequenceValue) {
				return new SequenceValue(collection.elemType(), elements);
			} else if (value instanceof OrderedSetValue) {
				return new OrderedSetValue(collection.elemType(), elements);
			}
			return null;
		}
		if (value instanceof TupleValue) {
			return containsObject(value) ? null : value;
		}
		return value;
	}

	private static boolean containsObject(Value value) {
		if (value instanceof ObjectValue) {
			return true;
		}
		if (value instanceof CollectionValue) {
			for (Value element : ((CollectionValue) value).collection()) {
				if (containsObject(element)) {
					return true;
				}
			}
		}
		if (value instanceof TupleValue) {
			TupleValue tuple = (TupleValue) value;
			for (String part : ((TupleType) tuple.type()).getParts().keySet()) {
				if (containsObject(tuple.getElementValue(part))) {
					return true;
				}
			}
		}
		return false;
	}
}
