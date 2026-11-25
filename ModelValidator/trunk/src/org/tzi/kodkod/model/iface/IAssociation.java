package org.tzi.kodkod.model.iface;

import java.util.List;

import org.tzi.kodkod.model.config.IConfigurator;

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
	 * Sets the configurator.
	 */
	public void setConfigurator(IConfigurator<IAssociation> configurator);

	/**
	 * Returns the configurator.
	 */
	public IConfigurator<IAssociation> getConfigurator();
}
