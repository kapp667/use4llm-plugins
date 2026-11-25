package org.tzi.kodkod.model.iface;

import java.util.List;

import org.tzi.kodkod.model.config.IConfigurator;

import kodkod.ast.Expression;

/**
 * Instances of the type IAssocation represent associations in a model.
 * 
 * @author Hendrik Reitmann
 */
public interface IAssociation extends IModelElement {

	/**
	 * Adds an association end to the end of the association end list.
	 */
	public void addAssociationEnd(IAssociationEnd associationEnd);

	/**
	 * Returns all association ends of the association.
	 */
	public List<IAssociationEnd> associationEnds();

	/**
	 * Sets an association class
	 */
	public void setAssociationClass(IAssociationClass associationClass);

	/**
	 * Returns the association class, null if no association class is set.
	 */
	public IAssociationClass associationClass();

	/**
	 * @return returns whether this association is an association class or not
	 */
	public boolean isAssociationClass();
	
	/**
	 * @return Returns true if this is a binary association.
	 */
	public boolean isBinaryAssociation();

	/**
	 * Adds the constraint of a {@code subsets} on an association end of this
	 * binary association: every link through the end is a link through the
	 * subsetted end of the other binary association, between the same objects
	 * at the corresponding ends.
	 * 
	 * @param end the subsetting end of this association
	 * @param subsettedAssociation the association of the subsetted end
	 * @param subsettedEnd the subsetted end
	 */
	public void addSubsettedEnd(IAssociationEnd end, IAssociation subsettedAssociation, IAssociationEnd subsettedEnd);

	/**
	 * Adds a binary association that redefines this binary association. This
	 * association then has no link to an object of the redefining association's
	 * class at an end where that class differs from its own, and navigation,
	 * multiplicities, subsets and unions through its ends also go through the
	 * redefining links. The caller adds every redefining association, directly
	 * or not.
	 * 
	 * @param redefiningAssociation the redefining association
	 */
	public void addRedefiningAssociation(IAssociation redefiningAssociation);

	/**
	 * Returns the associations that redefine this association, directly or
	 * not.
	 */
	public List<IAssociation> redefiningAssociations();

	/**
	 * Returns the links that navigation, multiplicities, subsets and unions go
	 * through: those of the relation and, if this association is redefined,
	 * those of the redefining associations.
	 * 
	 * @return the links, with the arity of the relation
	 */
	public Expression navigationLinks();

	/**
	 * Sets the configurator.
	 */
	public void setConfigurator(IConfigurator<IAssociation> configurator);

	/**
	 * Returns the configurator.
	 */
	public IConfigurator<IAssociation> getConfigurator();
}
