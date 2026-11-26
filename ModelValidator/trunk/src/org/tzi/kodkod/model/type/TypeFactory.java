package org.tzi.kodkod.model.type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kodkod.ast.IntExpression;

import org.tzi.kodkod.model.iface.IClass;

/**
 * Abstract factory to create and get the types.
 * 
 * @author Hendrik Reitmann
 */
public abstract class TypeFactory {

	private Map<String, Type> buildInTypes;
	private final Set<IntExpression> singleIntegerReads = Collections.newSetFromMap(new IdentityHashMap<IntExpression, Boolean>());

	public TypeFactory() {
		buildInTypes = createBuildInTypes();
	}

	/**
	 * Returns the sums that OCLOperationGroup.integerValue builds as the
	 * translation runs, each over an expression that holds at most one atom
	 * wherever the sum can change the truth of the formula. IntegerRanges
	 * bounds each as a single value.
	 */
	public Set<IntExpression> singleIntegerReads() {
		return singleIntegerReads;
	}

	/**
	 * Returns a list with all types with atoms.
	 */
	public List<TypeAtoms> typeAtoms() {
		List<TypeAtoms> typeAtoms = new ArrayList<TypeAtoms>();
		for (Type type : buildInTypes.values()) {
			if (type instanceof TypeAtoms) {
				typeAtoms.add((TypeAtoms) type);
			}
		}
		return typeAtoms;
	}

	/**
	 * Returns a list with all types with literals.
	 */
	public List<TypeLiterals> typeLiterals() {
		List<TypeLiterals> typeLiterals = new ArrayList<TypeLiterals>();
		for (Type type : buildInTypes.values()) {
			if (type instanceof TypeLiterals) {
				typeLiterals.add((TypeLiterals) type);
			}
		}
		return typeLiterals;
	}

	/**
	 * Returns a list with the configurable types.
	 */
	public List<ConfigurableType> configurableTypes() {
		List<ConfigurableType> configurableTypes = new ArrayList<ConfigurableType>();
		for (Type type : buildInTypes.values()) {
			if (type instanceof ConfigurableType) {
				configurableTypes.add((ConfigurableType) type);
			}
		}
		return configurableTypes;
	}

	/**
	 * Returns a collection with the build-in-types. A build-in-type exists only
	 * once in the model.
	 */
	public Collection<Type> buildInTypes() {
		return buildInTypes.values();
	}

	/**
	 * Returns the build-in-type with the given name, null if it does not exist.
	 */
	public Type buildInType(String name) {
		return buildInTypes.get(name);
	}

	/**
	 * Returns the any type.
	 */
	public Type anyType() {
		return buildInTypes.get(TypeConstants.ANY);
	}

	/**
	 * Returns the undefined type.
	 */
	public TypeAtoms undefinedType() {
		return (TypeAtoms) buildInTypes.get(TypeConstants.UNDEFINED);
	}

	/**
	 * Returns the undefined set type.
	 */
	public TypeAtoms undefinedSetType() {
		return (TypeAtoms) buildInTypes.get(TypeConstants.UNDEFINED_SET);
	}

	/**
	 * Returns the string type.
	 */
	public TypeLiterals stringType() {
		return (TypeLiterals) buildInTypes.get(TypeConstants.STRING);
	}

	/**
	 * Returns the integer type.
	 */
	public TypeLiterals integerType() {
		return (TypeLiterals) buildInTypes.get(TypeConstants.INTEGER);
	}

	/**
	 * Returns the boolean type.
	 */
	public TypeLiterals booleanType() {
		return (TypeLiterals) buildInTypes.get(TypeConstants.BOOLEAN);
	}

	/**
	 * Returns the real type.
	 */
	public TypeLiterals realType() {
		return (TypeLiterals) buildInTypes.get(TypeConstants.REAL);
	}

	/**
	 * Returns the object type of the given class.
	 */
	public Type objectType(IClass clazz) {
		return clazz.objectType();
	}

	/**
	 * Creates the build-in-types.
	 */
	protected abstract Map<String, Type> createBuildInTypes();

	/**
	 * Creates a new enum type.
	 */
	public abstract TypeLiterals enumType(String name, List<String> literals);

	/**
	 * Creates a set type.
	 */
	public abstract Type setType(Type elemType);

	/**
	 * Creates a bag type.
	 */
	public abstract Type bagType(Type elemType);

	/**
	 * Creates a ordered set type.
	 */
	public abstract Type orderedSetType(Type elemType);

	/**
	 * Creates a sequence type.
	 */
	public abstract Type sequenceType(Type elemType);

	/**
	 * Creates a collection type.
	 */
	public abstract Type collectionType(Type elemType);
}
