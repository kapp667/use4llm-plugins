package org.tzi.kodkod;

import kodkod.engine.Evaluator;

import org.tzi.use.uml.sys.MSystemState;
import org.tzi.use.uml.sys.events.tags.SystemStateChangedEvent;

import com.google.common.eventbus.Subscribe;

/**
 * Cache to store the Kodkod-Evaluator of the solution that USE shows as its
 * system state. Queries compare their value with the solution only while USE
 * shows that state unchanged.
 * 
 * @author Hendrik Reitmann
 * 
 */

public enum KodkodQueryCache {

	INSTANCE;

	private Evaluator evaluator;
	private MSystemState state;
	private boolean queryEnabled = false;

	private KodkodQueryCache() {
	}

	/**
	 * Keeps the evaluator of a solution with the state that shows the solution,
	 * if the comparison of queries with solutions is enabled.
	 */
	public synchronized void cache(Evaluator evaluator, MSystemState state) {
		clear();
		if (queryEnabled) {
			this.evaluator = evaluator;
			this.state = state;
			state.system().getEventBus().register(this);
		}
	}

	/**
	 * Returns the evaluator of the solution that the state shows, or null if no
	 * solution was cached with this state or the state changed since.
	 */
	public synchronized Evaluator evaluatorFor(MSystemState state) {
		return state == this.state ? evaluator : null;
	}

	public synchronized void clear() {
		if (state != null) {
			state.system().getEventBus().unregister(this);
		}
		evaluator = null;
		state = null;
	}

	@Subscribe
	public void onStateChanged(SystemStateChangedEvent event) {
		clear();
	}

	/**
	 * Enables the comparison of queries with the solutions found from now on, or
	 * disables it and forgets the cached evaluator.
	 */
	public synchronized void setQueryEnabled(boolean queryEnabled) {
		this.queryEnabled = queryEnabled;
		if (!queryEnabled) {
			clear();
		}
	}

	public synchronized boolean isQueryEnabled() {
		return queryEnabled;
	}
}
