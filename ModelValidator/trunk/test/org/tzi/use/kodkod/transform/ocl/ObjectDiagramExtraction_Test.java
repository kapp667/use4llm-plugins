package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.fail;

import org.junit.Test;

import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.kodkod.transform.enrich.ObjectDiagramModelEnricher;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.sys.MSystemState;

/**
 * Checks that the extraction of the object diagram refuses when it fails part
 * way, instead of letting the validation go on with a partly fixed state
 * (issue #104).
 */
public class ObjectDiagramExtraction_Test {

	private static final String STATE_SPEC = "model M\n"
			+ "class A\n"
			+ "end\n"
			+ "class B\n"
			+ "end\n";

	// no trigger is known in a consistent model today: a model without the class B
	// stands for any fault that stops the extraction after it fixed some objects
	private static final String MODEL_SPEC = "model M\n"
			+ "class A\n"
			+ "end\n";

	@Test
	public void extractionThatFailsPartWayRefuses() throws Exception {
		MModel mModel = TestSpecifications.compile(STATE_SPEC);
		MSystem mSystem = new MSystem(mModel);
		MSystemState state = mSystem.state();
		state.createObject(mModel.getClass("A"), "a1");
		state.createObject(mModel.getClass("B"), "b1");
		IModel model = TestSpecifications.transform(MODEL_SPEC);

		try {
			new ObjectDiagramModelEnricher().enrichModel(mSystem, model);
		} catch (Exception refused) {
			return;
		}
		fail("the extraction failed on b1 and still returned, so the validation goes on with a partial state");
	}
}
