package org.tzi.use.plugin.filmstrip.logic;

import java.util.HashSet;
import java.util.Set;

import org.tzi.use.plugin.filmstrip.FilmstripModelConstants;
import org.tzi.use.plugin.filmstrip.logic.TransformationInputException.ModelElements;
import org.tzi.use.uml.mm.MAssociation;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MDataType;
import org.tzi.use.uml.mm.MElementAnnotation;
import org.tzi.use.uml.mm.MGeneralization;
import org.tzi.use.uml.mm.MMVisitor;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.MOperation;
import org.tzi.use.uml.mm.MPrePostCondition;
import org.tzi.use.uml.mm.commonbehavior.communications.MSignal;
import org.tzi.use.uml.ocl.type.EnumType;

import com.google.common.collect.Sets;

public class ModelValidator implements MMVisitor {
	
	// The sets are built for each validation, from the classes of the model.
	private final HashSet<String> reservedClassNames = Sets.newHashSet(
			FilmstripModelConstants.SNAPSHOT_CLASSNAME,
			FilmstripModelConstants.SNAPSHOTITEM_CLASSNAME,
			FilmstripModelConstants.OPC_CLASSNAME );
	private final HashSet<String> reservedClassPrefixes = Sets.newHashSet();
	private final HashSet<String> reservedClassSuffixes = Sets.newHashSet(
			FilmstripModelConstants.OPC_ABBREVIATION );

	// completed in the constructor with the associations generated for each
	// class of the model
	private final HashSet<String> reservedAssociationNames = Sets.newHashSet(
			FilmstripModelConstants.FILMSTRIP_ASSOCNAME,
			FilmstripModelConstants.SNAPSHOTELEMENT_ASSOCNAME,
			FilmstripModelConstants.ORDERABLE_ASSOCNAME,
			FilmstripModelConstants.SNAPSHOT_OPC );
	private final HashSet<String> reservedAssociationPrefixes = Sets.newHashSet();
	private final HashSet<String> reservedAssociationSuffixes = Sets.newHashSet(
			FilmstripModelConstants.ORDERABLE_ASSOCNAME );

	// every class inherits the role `snapshot' from SnapshotItem
	private final HashSet<String> reservedRoleNames = Sets.newHashSet(
			FilmstripModelConstants.SNAPSHOT_ROLENAME );
	private final HashSet<String> reservedRolePrefixes = Sets.newHashSet(
			FilmstripModelConstants.PRED_ROLENAME,
			FilmstripModelConstants.SUCC_ROLENAME );
	private final HashSet<String> reservedRoleSuffixes = Sets.newHashSet();

	private final HashSet<String> reservedInvNames = Sets.newHashSet(
			FilmstripModelConstants.CLASS_INV_VALIDLINKING_NAME);
	private final HashSet<String> reservedInvPrefixes = Sets.newHashSet(
			FilmstripModelConstants.makeValidLinkingInvName(""));
	private final HashSet<String> reservedInvSuffixes = Sets.newHashSet();

	private ModelElements elements;

	private ModelValidator(MModel model){
		elements = new ModelElements();
		for(MClass cls : model.classes()){
			reservedAssociationNames.add(
					FilmstripModelConstants.makeSnapshotClsAssocName(cls.name()));
			reservedAssociationNames.add(
					FilmstripModelConstants.makeClsOrdableAssocName(cls.name()));
		}
	}
	
	private ModelElements getElements() {
		return elements;
	}
	
	public static void validate(MModel model) throws TransformationInputException {
		
		ModelValidator validator = new ModelValidator(model);
		model.processWithVisitor(validator);
		ModelElements elems = validator.getElements();
		
		if(elems.hasElements()){
			throw new TransformationInputException(elems);
		}
	}
	
	private boolean isInvalidElement(Set<String> matches, Set<String> prefixes, 
			Set<String> suffixes, String elemName){
		if(matches.contains(elemName)){
			return true;
		}
		for(String prefix : prefixes){
			if(elemName.startsWith(prefix)){
				return true;
			}
		}
		for(String suffix : suffixes){
			if(elemName.endsWith(suffix)){
				return true;
			}
		}
		return false;
	}
	
	// USE keeps classes and associations in one namespace, so a name is
	// checked against the names generated for both.
	private boolean isInvalidClassOrAssociationName(String name) {
		return isInvalidElement(reservedClassNames, reservedClassPrefixes,
				reservedClassSuffixes, name)
				|| isInvalidElement(reservedAssociationNames,
						reservedAssociationPrefixes, reservedAssociationSuffixes,
						name);
	}

	private boolean isInvalidClass(MClass e) {
		return isInvalidClassOrAssociationName(e.name());
	}

	private boolean isInvalidAssociaton(MAssociation e) {
		return isInvalidClassOrAssociationName(e.name());
	}
	
	private boolean isInvalidRole(MAssociationEnd e) {
		return isInvalidElement(reservedRoleNames, reservedRolePrefixes,
				reservedRoleSuffixes, e.name());
	}
	
	private boolean isInvalidInvariant(MClassInvariant e) {
		return isInvalidElement(reservedInvNames, reservedInvPrefixes,
				reservedInvSuffixes, e.name());
	}
	
	@Override
	public void visitAnnotation(MElementAnnotation a) {
	}

	@Override
	public void visitAssociation(MAssociation e) {
		if(isInvalidAssociaton(e)){
			elements.addAssociation(e);
		}
		
		for(MAssociationEnd end : e.associationEnds()){
			end.processWithVisitor(this);
		}
	}
	
	@Override
	public void visitAssociationClass(MAssociationClass e) {
		if(isInvalidClass(e)){
			elements.addClass(e);
		}
		
		for(MAssociationEnd end : e.associationEnds()){
			end.processWithVisitor(this);
		}
	}

	@Override
	public void visitAssociationEnd(MAssociationEnd e) {
		if(isInvalidRole(e)){
			elements.addRole(e);
		}
	}

	@Override
	public void visitAttribute(MAttribute e) {
	}

	@Override
	public void visitClass(MClass e) {
		if(isInvalidClass(e)){
			elements.addClass(e);
		}
	}

	@Override
	public void visitClassInvariant(MClassInvariant e) {
		if(isInvalidInvariant(e)){
			elements.addClassInvariant(e);
		}
	}

	@Override
	public void visitGeneralization(MGeneralization e) {
	}

	@Override
	public void visitModel(MModel e) {
		for(MClass cls : e.classes()){
			cls.processWithVisitor(this);
		}
		
		for(MAssociation assoc : e.associations()){
			assoc.processWithVisitor(this);
		}
		
		for(MClassInvariant inv : e.modelClassInvariants()){
			inv.processWithVisitor(this);
		}
	}

	@Override
	public void visitOperation(MOperation e) {
	}

	@Override
	public void visitPrePostCondition(MPrePostCondition e) {
	}

	@Override
	public void visitSignal(MSignal mSignalImpl) {
	}

	@Override
	public void visitEnum(EnumType enumType) {
	}

	@Override
	public void visitDataType(MDataType e) {
		// not visited by visitModel; FilmstripMMVisitor refuses data types
	}
	
}
