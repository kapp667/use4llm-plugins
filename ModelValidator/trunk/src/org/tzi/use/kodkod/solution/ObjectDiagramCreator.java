package org.tzi.use.kodkod.solution;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import kodkod.ast.Relation;
import kodkod.instance.Tuple;
import kodkod.instance.TupleSet;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.config.impl.DefaultConfigurationValues;
import org.tzi.kodkod.model.config.impl.ModelConfigurator;
import org.tzi.kodkod.model.iface.IAssociation;
import org.tzi.kodkod.model.iface.IAssociationClass;
import org.tzi.kodkod.model.iface.IAttribute;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IInvariant;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Evaluator;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.value.BooleanValue;
import org.tzi.use.uml.sys.MObjectState;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemException;

/**
 * Class to create an object diagram from a solution of kodkod.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class ObjectDiagramCreator {

	public enum ErrorType {
		NONE, STRUCTURE, INVARIANT
	}
	
	protected static final Logger LOG = Logger.getLogger(ObjectDiagramCreator.class);
	
	private IModel model;
	private UseSystemApi systemApi;

	private Map<String, MObjectState> objectStates;

	private Map<Relation, IAttribute> attributes;

	public ObjectDiagramCreator(IModel model, Session session) {
		this.model = model;
		systemApi = UseSystemApi.create(session);
	}

	/**
	 * Creates the object diagram.
	 * 
	 * @param relations
	 * @throws MSystemException 
	 * @throws Exception
	 */
	public void create(Map<Relation, TupleSet> relations) throws UseApiException {
		objectStates = new HashMap<String, MObjectState>();
		attributes = attributesByRelation();

		Map<Relation, TupleSet> withoutClasses = createObjects(relations);

		createAssociationClasses(relations);

		emptyCollectionAttributes();

		createElements(withoutClasses);
	}

	private void emptyCollectionAttributes() throws UseApiException {
		MModel mModel = systemApi.getSystem().model();
		for (IAttribute attribute : attributes.values()) {
			MAttribute mAttribute = mModel.getClass(attribute.owner().name()).attribute(attribute.name(), false);
			if (attribute.type().isSet() && !mAttribute.isDerived()) {
				for (MObjectState objectState : objectStates.values()) {
					if (objectState.attributeValueMap().containsKey(mAttribute)) {
						systemApi.setAttributeValueEx(objectState.object(), mAttribute,
								ValueCreator.emptyCollection((CollectionType) mAttribute.type()));
					}
				}
			}
		}
	}

	public ErrorType hasDiagramErrors() {
		StringWriter buffer = new StringWriter();
		PrintWriter out = new PrintWriter(buffer);
		MSystem mSystem = systemApi.getSystem();
		boolean foundErrors = !mSystem.state().checkStructure(out);
		if (foundErrors) {
			String report = buffer.toString();

			boolean aggregationcyclefreeness = DefaultConfigurationValues.AGGREGATIONCYCLEFREENESS;
			boolean forbiddensharing = DefaultConfigurationValues.FORBIDDENSHARING;
			if (model.getConfigurator() instanceof ModelConfigurator) {
				aggregationcyclefreeness = ((ModelConfigurator) model.getConfigurator()).isAggregationCycleFree();
				forbiddensharing = ((ModelConfigurator) model.getConfigurator()).isForbiddensharing();
			}

			StructureErrors errors = new StructureErrors(report);
			if (errors.unexpected) {
				LOG.error(LogMessages.structureError(report));
				return ErrorType.STRUCTURE;
			}
			if ((aggregationcyclefreeness && errors.cycle) || (forbiddensharing && errors.shared)) {
				return ErrorType.STRUCTURE;
			}
		}

		// check invariants for correctness
		MModel mModel = mSystem.model();
		Evaluator evaluator = new Evaluator();
		boolean invariantError = false;
		for (IInvariant invariant : model.classInvariants()) {
			if (invariant.isActivated()) {
				BooleanValue result = (BooleanValue) evaluator.eval(mModel.getClassInvariant(invariant.qualifiedName()).expandedExpression(),
						mSystem.state());
				if ((invariant.isNegated() && result.isTrue()) || (!invariant.isNegated() && result.isFalse())) {
					LOG.error(LogMessages.unexpectedInvariantResult(invariant));
					invariantError = true;
				}
			}
		}
		
		return invariantError? ErrorType.INVARIANT : ErrorType.NONE;
	}
	
	/**
	 * The error lines of USE's structure report. A cycle and a sharing are the
	 * errors the configuration may allow. Any other error line is unexpected,
	 * and so is a report without a line that can be read.
	 */
	private static class StructureErrors {
		private boolean unexpected;
		private boolean cycle;
		private boolean shared;

		StructureErrors(String report) {
			boolean inError = false;
			for (String line : report.split("\\r?\\n")) {
				if (line.trim().isEmpty() || line.startsWith("checking structure")
						|| line.startsWith("checked structure")) {
					inError = false;
				} else if (Character.isWhitespace(line.charAt(0))) {
					// a continuation line belongs to the error above it
					if (!inError) {
						unexpected = true;
					}
				} else if (line.contains("cycle in the part-whole hierarchy")) {
					inError = true;
					cycle = true;
				} else if (line.contains(" is shared by object ")) {
					inError = true;
					shared = true;
				} else {
					inError = true;
					unexpected = true;
				}
			}
			if (!unexpected && !cycle && !shared) {
				unexpected = true;
			}
		}
	}

	private Map<Relation, TupleSet> createObjects(
			Map<Relation, TupleSet> relations) throws UseApiException {
		Map<Relation, TupleSet> withoutClasses = new HashMap<Relation, TupleSet>(
				relations);

		String relationName;
		ElementStrategy strategy;

		for (Relation relation : relations.keySet()) {
			strategy = null;
			relationName = relation.name();

			if (!isNegateRelation(relationName) && !isType(relationName)) {
				if (isClass(relation) && !isAssociationClass(relation.name())) {
					strategy = new ObjectStrategy(systemApi, objectStates, relationName);
					iterateTuplesAndCreate(strategy, relations.get(relation));

					withoutClasses.remove(relation);
				}
			}
		}

		return withoutClasses;
	}

	private void createElements(Map<Relation, TupleSet> relations) throws UseApiException {
		for (Relation relation : relations.keySet()) {
			ElementStrategy strategy = null;
			IAttribute attribute = attributes.get(relation);

			if (attribute != null) {
				strategy = new AttributeStrategy(systemApi, objectStates, attribute.owner().name(), attribute.name());
			} else if (isAssociation(relation)) {
				strategy = new LinkStrategy(systemApi, objectStates, relation.name());
			}

			if (strategy != null) {
				iterateTuplesAndCreate(strategy, relations.get(relation));
			}
		}
	}

	private void createAssociationClasses(Map<Relation, TupleSet> relations)
			throws UseApiException {
		for (MAssociationClass mAssociationClass : systemApi.getSystem().model()
				.getAssociationClassesOnly()) {
			Relation relation = model.getAssociation(mAssociationClass.name()).relation();
			ElementStrategy strategy = new LinkObjectStrategy(systemApi, objectStates, mAssociationClass);

			// filter association relations without a linkobject
			TupleSet validRelations = relations.get(relation).clone();
			for (Iterator<Tuple> it = validRelations.iterator(); it.hasNext();) {
				Tuple tuple = it.next();
				if(tuple.atom(0).equals(TypeConstants.UNDEFINED)){
					it.remove();
				}
			}
			iterateTuplesAndCreate(strategy, validRelations);
		}
	}

	private Map<Relation, IAttribute> attributesByRelation() {
		Map<Relation, IAttribute> byRelation = new HashMap<Relation, IAttribute>();
		for (IClass clazz : model.classes()) {
			for (IAttribute attribute : clazz.attributes()) {
				byRelation.put(attribute.relation(), attribute);
			}
		}
		return byRelation;
	}

	/**
	 * Iterates all tuples and creates the elements with the given strategy.
	 * 
	 * @param strategy
	 * @param tupleSet
	 * @throws MSystemException
	 */
	private void iterateTuplesAndCreate(ElementStrategy strategy,
			TupleSet tupleSet) throws UseApiException {
		if (strategy.canDo()) {
			Iterator<Tuple> iterator = tupleSet.iterator();
			Tuple currentTuple;
			while (iterator.hasNext()) {
				currentTuple = iterator.next();

				strategy.createElement(currentTuple);
			}
		}
	}

	private boolean isClass(Relation relation) {
		return model.getClass(relation.name()) != null && relation.arity() == 1;
	}

	private boolean isAssociationClass(String relationName) {
		return model.getClass(relationName) instanceof IAssociationClass;
	}

	private boolean isAssociation(Relation relation) {
		IAssociation association = model.getAssociation(relation.name());
		return association != null && association.relation() == relation;
	}

	private boolean isType(String name) {
		return model.getEnumType(name) != null
				|| model.typeFactory().buildInType(name) != null;
	}

	private boolean isNegateRelation(String relationName) {
		return relationName.startsWith("$");
	}
}
