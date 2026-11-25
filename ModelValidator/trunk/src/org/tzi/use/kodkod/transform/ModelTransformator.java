package org.tzi.use.kodkod.transform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IAssociation;
import org.tzi.kodkod.model.iface.IAssociationClass;
import org.tzi.kodkod.model.iface.IAssociationEnd;
import org.tzi.kodkod.model.iface.IAttribute;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.iface.IModelFactory;
import org.tzi.kodkod.model.impl.DerivedAssociationEnd;
import org.tzi.kodkod.model.impl.DerivedAssociationEnd.Parameter;
import org.tzi.kodkod.model.impl.UnionAssociation;
import org.tzi.kodkod.model.type.ObjectType;
import org.tzi.kodkod.model.type.Type;
import org.tzi.kodkod.model.type.TypeFactory;
import org.tzi.use.graph.DirectedGraph;
import org.tzi.use.uml.mm.MAggregationKind;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MClassifier;
import org.tzi.use.uml.mm.MGeneralization;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.VarDecl;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.util.StringUtil;
import org.tzi.use.util.uml.sorting.UseFileOrderComparator;

/**
 * Class to transform the use model in a model of the model validator.
 * 
 * @author Hendrik Reitmann
 * @author Frank Hilken
 */
public class ModelTransformator {

	private static final Logger LOG = Logger.getLogger(ModelTransformator.class);

	private final IModelFactory factory;
	private final TypeFactory typeFactory;

	public ModelTransformator(final IModelFactory factory, final TypeFactory typeFactory) {
		this.factory = factory;
		this.typeFactory = typeFactory;
	}

	public IModel transform(MModel mModel) {
		long startTime = System.currentTimeMillis();

		LOG.info(LogMessages.startModelTransform(mModel.name()));

		IModel model = factory.createModel(mModel.name(), factory, typeFactory);

		try {
			transformEnums(model, mModel.enumTypes());

			List<MAssociationClass> associationClasses = new ArrayList<MAssociationClass>(mModel.getAssociationClassesOnly());

			List<MClass> classes = new ArrayList<MClass>(mModel.classes());
			classes.removeAll(associationClasses);

			List<MAssociation> simpleAssociations = new ArrayList<MAssociation>(mModel.associations());
			simpleAssociations.removeAll(associationClasses);

			Collections.sort(associationClasses, new UseFileOrderComparator());
			Collections.sort(classes, new UseFileOrderComparator());
			Collections.sort(simpleAssociations, new UseFileOrderComparator());
			
			transformClasses(model, classes);

			transformAssociationClasses(model, associationClasses);

			transformGeneralization(model, mModel.generalizationGraph());

			transformAttributes(model, classes);

			transformSimpleAssociations(model, simpleAssociations);

			transformRedefinitions(model, mModel.associations());

			transformSubsets(model, mModel.associations());

			transformUnions(model, mModel.associations());

			InvariantTransformator invariantTransformator = new InvariantTransformator(factory, typeFactory);
			List<MClassInvariant> classInvariants = new ArrayList<MClassInvariant>(mModel.classInvariants());
			Collections.sort(classInvariants, new UseFileOrderComparator());
			
			invariantTransformator.transformAndAdd(model, classInvariants);

			LOG.info(LogMessages.modelTransformSuccessful);
			LOG.info(LogMessages.modelTransformTime((System.currentTimeMillis() - startTime)));

		} catch (Exception exception) {
			if (LOG.isDebugEnabled()) {
				LOG.error(LogMessages.modelTransformError, exception);
			} else {
				LOG.error(LogMessages.modelTransformError + " " + LogMessages.exceptionReason(exception));
			}
			model.addTransformationError(LogMessages.modelTransformStoppedError(mModel.name(), LogMessages.exceptionReason(exception)));
		}

		return model;
	}

	private void transformEnums(IModel model, Set<EnumType> enumTypes) {
		List<String> literals;
		for (EnumType enumType : enumTypes) {
			literals = new ArrayList<String>();
			for (String literal : enumType.getLiterals()) {
				literals.add(literal);
			}
			model.addEnumType((org.tzi.kodkod.model.type.EnumType) typeFactory.enumType(enumType.name(), literals));
		}
	}

	private void transformClasses(IModel model, List<MClass> mClasses) {
		for (MClass mClass : mClasses) {
			if (!mClass.name().startsWith("$")) {
				IClass kClass = factory.createClass(model, mClass.name(), mClass.isAbstract());
				model.addClass(kClass);
			} else {
				LOG.error(LogMessages.className$Error);
			}
		}
	}

	private void transformAttributes(IModel model, List<MClass> mClasses) {
		for (MClass mClass : mClasses) {
			IClass kClass = model.getClass(mClass.name());
			transformClassAttributes(model, kClass, mClass.attributes());
		}
	}

	private void transformClassAttributes(IModel model, IClass clazz, List<MAttribute> attributes) {
		TypeConverter typeConverter = new TypeConverter(model);

		for (MAttribute mAttribute : attributes) {
			Type type = typeConverter.convert(mAttribute.type());
			if (type != null) {
				IAttribute attr;
				if(mAttribute.isDerived()){
					attr = factory.createDerivedAttribute(model, mAttribute.name(), type, clazz, mAttribute.getDeriveExpression());
				} else {
					attr = factory.createAttribute(model, mAttribute.name(), type, clazz);
				}
				clazz.addAttribute(attr);
			}
		}
	}

	private void transformSimpleAssociations(IModel model, List<MAssociation> mAssociations) {
		MultiplicityTransformator multTransformator = new MultiplicityTransformator();
		for (MAssociation mAssociation : mAssociations) {
			model.addAssociation(transformAssociation(multTransformator, model, mAssociation));
		}
	}

	/**
	 * Adds the constraints of the {@code subsets} on association ends. Like
	 * USE's check, it supports binary associations only.
	 */
	private void transformSubsets(IModel model, Collection<MAssociation> mAssociations) {
		for (MAssociation mAssociation : mAssociations) {
			for (MAssociationEnd mEnd : mAssociation.associationEnds()) {
				for (MAssociationEnd mSubsettedEnd : mEnd.getSubsettedEnds()) {
					String end = mAssociation.name() + "::" + mEnd.nameAsRolename();
					String subsettedEnd = mSubsettedEnd.association().name() + "::" + mSubsettedEnd.nameAsRolename();
					if (mAssociation.associationEnds().size() != 2 || mSubsettedEnd.association().associationEnds().size() != 2) {
						throw new TransformationException(LogMessages.subsetsNotBinaryError(end, subsettedEnd));
					}
					if (mAssociation.isDerived() || mSubsettedEnd.association().isDerived()) {
						throw new TransformationException(LogMessages.subsetsDerivedError(end, subsettedEnd));
					}
					IAssociation association = model.getAssociation(mAssociation.name());
					IAssociation subsettedAssociation = model.getAssociation(mSubsettedEnd.association().name());
					association.addSubsettedEnd(associationEnd(association, mEnd), subsettedAssociation,
							associationEnd(subsettedAssociation, mSubsettedEnd));
				}
			}
		}
	}

	/**
	 * Gives each association with union ends the associations whose ends
	 * subset its ends at the same position: as in USE's navigation, its links
	 * are theirs.
	 */
	private void transformUnions(IModel model, Collection<MAssociation> mAssociations) {
		for (MAssociation mAssociation : mAssociations) {
			if (!mAssociation.isUnion() || mAssociation.isDerived()) {
				continue;
			}
			UnionAssociation association = (UnionAssociation) model.getAssociation(mAssociation.name());
			List<MAssociationEnd> mEnds = mAssociation.associationEnds();
			for (int i = 0; i < mEnds.size(); i++) {
				for (MAssociationEnd mSubsettingEnd : mEnds.get(i).getSubsettingEnds()) {
					if (mSubsettingEnd.association().associationEnds().indexOf(mSubsettingEnd) == i) {
						association.addSubsettingAssociation(model.getAssociation(mSubsettingEnd.association().name()));
					}
				}
			}
		}
	}

	/**
	 * Gives each redefined association the associations that redefine it,
	 * directly or not. It runs before the subsets, whose constraints are built
	 * at once from the redefining links.
	 */
	private void transformRedefinitions(IModel model, Collection<MAssociation> mAssociations) {
		for (MAssociation mAssociation : mAssociations) {
			for (MAssociation mRedefiningAssociation : mAssociation.getRedefinedByClosure()) {
				refuseRedefinitionInvolving(mAssociation, mAssociation, mRedefiningAssociation);
				refuseRedefinitionInvolving(mRedefiningAssociation, mAssociation, mRedefiningAssociation);
				model.getAssociation(mAssociation.name()).addRedefiningAssociation(model.getAssociation(mRedefiningAssociation.name()));
			}
		}
	}

	private void refuseRedefinitionInvolving(MAssociation mAssociation, MAssociation mRedefined, MAssociation mRedefining) {
		String redefined = mRedefined.name();
		String redefining = mRedefining.name();
		if (mAssociation.associationEnds().size() != 2) {
			throw new TransformationException(LogMessages.redefinesNotBinaryError(redefined, redefining));
		}
		if (mAssociation instanceof MAssociationClass) {
			throw new TransformationException(LogMessages.redefinesAssociationClassError(redefined, redefining));
		}
		if (mAssociation.isDerived()) {
			throw new TransformationException(LogMessages.redefinesDerivedError(redefined, redefining));
		}
		if (mAssociation.isUnion()) {
			throw new TransformationException(LogMessages.redefinesUnionError(redefined, redefining));
		}
	}

	private IAssociationEnd associationEnd(IAssociation association, MAssociationEnd mEnd) {
		return association.associationEnds().get(mEnd.association().associationEnds().indexOf(mEnd));
	}

	private IAssociation transformAssociation(MultiplicityTransformator multTransformator, IModel model, MAssociation mAssociation) {
		if(mAssociation.hasQualifiedEnds()){
			throw new TransformationException(LogMessages.qualifiedAssociationError(mAssociation.name()));
		}

		IAssociation association;
		if(mAssociation.isDerived()){
			association = factory.createDerivedAssociation(model, mAssociation.name());
		} else if(mAssociation.isUnion()){
			if(mAssociation.associationEnds().size() != 2){
				throw new TransformationException(LogMessages.unionNotBinaryError(mAssociation.name()));
			}
			if(mAssociation instanceof MAssociationClass){
				throw new TransformationException(LogMessages.unionAssociationClassError(mAssociation.name()));
			}
			association = factory.createUnionAssociation(model, mAssociation.name());
		} else {
			association = factory.createAssociation(model, mAssociation.name());
		}

		for (MAssociationEnd mAssociationEnd : mAssociation.associationEnds()) {
			IAssociationEnd end;
			if(mAssociationEnd.isDerived()){
				DerivedAssociationEnd derivedEnd = (DerivedAssociationEnd) factory.createDerivedAssociationEnd(mAssociationEnd.name(),
						multTransformator.transform(mAssociationEnd.multiplicity()),
						transformAggregationKind(mAssociationEnd.aggregationKind()),
						model.getClass(mAssociationEnd.cls().name()), mAssociationEnd.getDeriveExpression());
				
				TypeConverter tc = new TypeConverter(model);
				List<DerivedAssociationEnd.Parameter> params = new ArrayList<DerivedAssociationEnd.Parameter>();
				for (VarDecl varDecl : mAssociationEnd.getDeriveParamter()) {
					Type convertedType = tc.convert(varDecl.type());
					
					if(!convertedType.isObjectType()){
						// should not be possible in a valid UML/OCL model
						throw new TransformationException("Parameter type of derived association expression is not a class.");
					}
					
					params.add(new Parameter(varDecl.name(), ((ObjectType) convertedType).clazz()));
				}
				derivedEnd.setDerivedParameters(params);
				
				end = derivedEnd;
			} else {
				end = factory.createAssociationEnd(mAssociationEnd.name(),
						multTransformator.transform(mAssociationEnd.multiplicity()),
						transformAggregationKind(mAssociationEnd.aggregationKind()),
						model.getClass(mAssociationEnd.cls().name()));
			}
			
			association.addAssociationEnd(end);
			end.associatedClass().addAssociation(association);
		}
		
		return association;
	}

	private int transformAggregationKind(int useAggregationKind) {
		if(useAggregationKind == MAggregationKind.COMPOSITION){
			return IAssociationEnd.COMPOSITION;
		} else if(useAggregationKind == MAggregationKind.AGGREGATION){
			return IAssociationEnd.AGGREGATION;
		} else {
			return IAssociationEnd.REGULAR;
		}
	}

	private void transformAssociationClasses(IModel model, List<MAssociationClass> mAssociationClasses) {
		MultiplicityTransformator multTransformator = new MultiplicityTransformator();

		for (MAssociationClass mAssociationClass : mAssociationClasses) {
			if(mAssociationClass.isDerived()){
				LOG.error("Derived association classes are not supported. Ignoring " + StringUtil.inQuotes(mAssociationClass.name()) + ".");
				continue;
			}
			
			IAssociationClass associationClass = factory.createAssociationClass(model, mAssociationClass.name(), mAssociationClass.isAbstract());
			transformClassAttributes(model, associationClass, mAssociationClass.attributes());
			model.addClass(associationClass);

			IAssociation association = transformAssociation(multTransformator, model, mAssociationClass);
			association.setAssociationClass(associationClass);

			model.addAssociation(association);
		}
	}

	private void transformGeneralization(IModel model, DirectedGraph<MClassifier, MGeneralization> mGeneralizationGraph) {
		IClass source, target;

		Iterator<MGeneralization> iterator = mGeneralizationGraph.edgeIterator();
		while (iterator.hasNext()) {
			MGeneralization next = iterator.next();
			if (!(next.source() instanceof MClass))  continue;
			
			source = model.getClass(next.source().name());
			target = model.getClass(next.target().name());
			source.addParent(target);
			target.addChild(source);
		}
	}
}
