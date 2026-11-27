package org.tzi.kodkod.model.config.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kodkod.instance.TupleFactory;
import kodkod.instance.TupleSet;

import org.tzi.kodkod.model.type.ConfigurableType;

/**
 * Configurator for the real type.
 * 
 * @author Hendrik Reitmann
 * 
 */
public class RealConfigurator extends TypeConfigurator{

	private double step = 0.5;
	private boolean rangeSet = false;
	private double realLower;
	private double realUpper;
	
	@Override
	public TupleSet lowerBound(ConfigurableType type, int arity, TupleFactory tupleFactory) {
		TupleSet lower = tupleFactory.noneOf(1);
		
		for (String[] specific : allValues()) {
			lower.add(tupleFactory.tuple(atomName(type.name(), BigDecimal.valueOf(Double.parseDouble(specific[0])))));
		}

		for (BigDecimal value : rangeValues()) {
			lower.add(tupleFactory.tuple(atomName(type.name(), value)));
		}
		
		return lower;
	}
	
	@Override
	public TupleSet upperBound(ConfigurableType type, int arity, TupleFactory tupleFactory) {
		return lowerBound(type, arity, tupleFactory);
	}
	
	@Override
	public Set<Object> atoms(ConfigurableType type, List<Object> literals) {
		Set<Object >atoms = new LinkedHashSet<Object>();
		atoms.addAll(literals);		

		for (BigDecimal value : rangeValues()) {
			atoms.add(atomName(type.name(), value));
		}

		for (String[] specific : allValues()) {
			atoms.add(atomName(type.name(), BigDecimal.valueOf(Double.parseDouble(specific[0]))));
		}
		
		return new LinkedHashSet<Object>(atoms);
	}

	public void setStep(double step) {
		this.step = step;
	}

	/**
	 * Sets the bounds of the range. The bounds keep the values the file gives.
	 */
	public void setRealRange(double lower, double upper) {
		this.realLower = lower;
		this.realUpper = upper;
		this.rangeSet = true;
	}

	/**
	 * Returns the values lower + k * step that are at most the upper bound.
	 * Each value comes from k, so no rounding error builds up over the steps.
	 */
	private List<BigDecimal> rangeValues() {
		List<BigDecimal> values = new ArrayList<BigDecimal>();
		if (rangeSet) {
			BigDecimal lower = BigDecimal.valueOf(realLower);
			BigDecimal stepValue = BigDecimal.valueOf(step);
			BigDecimal upper = BigDecimal.valueOf(realUpper);
			for (int k = 0; ; k++) {
				BigDecimal value = lower.add(stepValue.multiply(BigDecimal.valueOf(k)));
				if (value.compareTo(upper) > 0) {
					break;
				}
				values.add(value);
			}
		}
		return values;
	}

	/**
	 * Returns the atom name of a Real value. The name holds the exact decimal value
	 * without trailing zeros and in every locale, so Real_1.5 names 1.50.
	 */
	public static String atomName(String typeName, BigDecimal value) {
		return typeName + "_" + value.stripTrailingZeros().toPlainString();
	}

}
