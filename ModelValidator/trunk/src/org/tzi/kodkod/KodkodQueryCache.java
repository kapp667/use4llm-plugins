package org.tzi.kodkod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import kodkod.engine.Evaluator;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.value.Value;
import org.tzi.use.uml.sys.MLink;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MObjectState;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Cache to store the Kodkod-Evaluator of the solution that USE shows as its
 * system state. Queries compare their value with the solution only while the
 * state still has the content it had when the solution was shown.
 * 
 * @author Hendrik Reitmann
 * 
 */

public enum KodkodQueryCache {

	INSTANCE;

	private Evaluator evaluator;
	private MSystemState state;
	private List<Object> content;
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
			this.content = contentOf(state);
		}
	}

	/**
	 * Returns the evaluator of the solution that the state shows, or null if no
	 * solution was cached with this state or the content of the state changed
	 * since the solution was cached. Changes that post no event are found too.
	 */
	public synchronized Evaluator evaluatorFor(MSystemState state) {
		return state == this.state && content.equals(contentOf(state)) ? evaluator : null;
	}

	public synchronized void clear() {
		evaluator = null;
		state = null;
		content = null;
	}

	/**
	 * Returns what the state shows: its objects with their attribute values,
	 * and its links with their ends and qualifier values. Two states with equal
	 * contents show the same objects, values and links.
	 */
	private static List<Object> contentOf(MSystemState state) {
		List<Object> content = new ArrayList<>();
		List<MObject> objects = new ArrayList<>(state.allObjects());
		objects.sort(Comparator.comparing(MObject::name));
		for (MObject object : objects) {
			content.add(objectContent(object, state));
		}
		List<MLink> links = new ArrayList<>(state.allLinks());
		links.sort(Comparator.comparing(KodkodQueryCache::linkKey));
		for (MLink link : links) {
			content.add(linkContent(link));
		}
		return content;
	}

	private static List<Object> objectContent(MObject object, MSystemState state) {
		List<Object> attributes = new ArrayList<>();
		MObjectState objectState = object.state(state);
		List<Map.Entry<MAttribute, Value>> values = new ArrayList<>(objectState.attributeValueMap().entrySet());
		values.sort(Comparator.comparing(entry -> entry.getKey().name()));
		for (Map.Entry<MAttribute, Value> value : values) {
			attributes.add(Arrays.asList(value.getKey(), value.getValue()));
		}
		return Arrays.asList(object, object.cls(), attributes);
	}

	private static List<Object> linkContent(MLink link) {
		return Arrays.asList(link.association(), link.linkedObjects(), link.getQualifier());
	}

	private static String linkKey(MLink link) {
		StringBuilder key = new StringBuilder(link.association().name());
		for (MObject object : link.linkedObjects()) {
			key.append(' ').append(object.name());
		}
		return key.append(' ').append(link.getQualifier()).toString();
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
