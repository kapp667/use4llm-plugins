package org.tzi.kodkod.model.config.impl;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
	private final DecimalFormat decimalFormat;
	private boolean rangeSet = false;
	private double realLower;
	private double realUpper;
	
	public RealConfigurator(){
		decimalFormat = new DecimalFormat("#0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
	}
	
	@Override
	public TupleSet lowerBound(ConfigurableType type, int arity, TupleFactory tupleFactory) {
		TupleSet lower = tupleFactory.noneOf(1);
		
		for (String[] specific : allValues()) {
			lower.add(tupleFactory.tuple(type.name() + "_" + decimalFormat.format(Double.parseDouble(specific[0]))));
		}

		for (double value : rangeValues()) {
			lower.add(tupleFactory.tuple(type.name() + "_" + decimalFormat.format(value)));
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

		for (double value : rangeValues()) {
			atoms.add(type.name() + "_" + decimalFormat.format(value));
		}

		for (String[] specific : allValues()) {
			atoms.add(type.name() + "_" + decimalFormat.format(Double.parseDouble(specific[0])));
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
	private List<Double> rangeValues() {
		List<Double> values = new ArrayList<Double>();
		if (rangeSet) {
			for (int k = 0; realLower + k * step <= realUpper; k++) {
				values.add(realLower + k * step);
			}
		}
		return values;
	}

}
