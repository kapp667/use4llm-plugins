package org.tzi.kodkod.model.impl;

import org.tzi.kodkod.model.iface.IModel;

import kodkod.ast.Formula;

/**
 * @author Frank Hilken
 */
public class DerivedAssociation extends Association {

	DerivedAssociation(IModel model, String name) {
		super(model, name);
	}

	@Override
	public Formula constraints() {
		return Formula.and(multiplicityDefinitions(), cycleFreenessDefinitions());
	}
	
	@Override
	protected Formula multiplicityDefinitions() {
		//TODO multiplicities
		return Formula.TRUE;
	}
	
	@Override
	protected Formula cycleFreenessDefinitions() {
		//TODO cycle-freeness
		return Formula.TRUE;
	}
	
}
