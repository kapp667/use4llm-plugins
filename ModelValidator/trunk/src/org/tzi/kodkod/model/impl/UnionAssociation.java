package org.tzi.kodkod.model.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.PrintHelper;
import org.tzi.kodkod.model.iface.IAssociation;
import org.tzi.kodkod.model.iface.IModel;

import kodkod.ast.Expression;
import kodkod.ast.Formula;

/**
 * A binary association with {@code union} ends. As in USE, its links are the
 * links of the associations whose ends subset its ends at the same position,
 * and only them.
 */
public class UnionAssociation extends Association {

	private static final Logger LOG = Logger.getLogger(UnionAssociation.class);

	private List<IAssociation> subsettingAssociations = new ArrayList<IAssociation>();

	UnionAssociation(IModel model, String name) {
		super(model, name);
	}

	/**
	 * Adds an association whose links are links of this association: one of
	 * its ends subsets the end of this association at the same position.
	 */
	public void addSubsettingAssociation(IAssociation association) {
		if (!subsettingAssociations.contains(association)) {
			subsettingAssociations.add(association);
		}
	}

	@Override
	public Formula constraints() {
		return super.constraints().and(unionDefinition());
	}

	/**
	 * Creates the formula that makes the links of this association the union
	 * of the links of its subsetting associations. The undefined values that
	 * stand for the missing links of a 0..1 end are left to the multiplicity
	 * constraints.
	 */
	private Formula unionDefinition() {
		Expression union = Expression.NONE.product(Expression.NONE);
		for (IAssociation association : subsettingAssociations) {
			union = union.union(links(association));
		}
		Formula formula = links(this).eq(union);
		LOG.debug("Union for " + name() + ": " + PrintHelper.prettyKodkod(formula));
		return formula;
	}
}
