package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.uml.ocl.expr.ExpObjRef;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.ocl.expr.ExpressionWithValue;
import org.tzi.use.uml.ocl.value.IntegerValue;
import org.tzi.use.uml.sys.MObject;

/**
 * A skipped expression leaves the previous result in its place and yields a wrong
 * verdict, so each of these is refused by name.
 */
public class UnhandledExpression_Test extends OCLTest {

	private static final String SORTED_BY = "sortedBy returns an ordered collection, whose order the translation does not keep.";

	private static final String UNHANDLED = "the translation does not handle this kind of expression.";

	@Test
	public void sortedByOnIntegersIsRefused() {
		assertRefused(toOCLExpression("Set{3, 1, 2}->sortedBy(i | i)"), "Set {3,1,2}->sortedBy(i : Integer | i)", SORTED_BY);
	}

	@Test
	public void sortedByOnObjectsIsRefused() {
		assertRefused(toOCLExpression("Person.allInstances()->sortedBy(p | p.age)->first()"), "Person.allInstances->sortedBy(p : Person | p.age)", SORTED_BY);
	}

	@Test
	public void sortedByInsideAnIteratorIsRefused() {
		assertRefused(toOCLExpression("Person.allInstances()->forAll(p | Set{p.age}->sortedBy(a | a)->size() = 1)"), "Set {p.age}->sortedBy(a : Integer | a)", SORTED_BY);
	}

	@Test
	public void objectByUseIdIsRefused() {
		Expression expression = toOCLExpression("Person.byUseId('ada')");
		assertRefused(expression, expression.toString(), UNHANDLED);
	}

	@Test
	public void valueExpressionIsRefused() {
		Expression expression = new ExpressionWithValue(IntegerValue.valueOf(1));
		assertRefused(expression, expression.toString(), UNHANDLED);
	}

	@Test
	public void objectReferenceIsRefusedWithItsVariableForm() {
		MObject object = mSystem.state().allObjects().iterator().next();
		Expression expression = new ExpObjRef(object);
		assertRefused(expression, expression.toString(), "write it as the variable " + object.name() + ", which is translated.");
	}

	private void assertRefused(Expression expression, String refusedPart, String reason) {
		try {
			toKodkod(expression);
			fail("The expression was translated: " + expression);
		} catch (TransformationException e) {
			assertEquals("Cannot transform `" + refusedPart + "': " + reason, e.getMessage());
		}
	}
}
