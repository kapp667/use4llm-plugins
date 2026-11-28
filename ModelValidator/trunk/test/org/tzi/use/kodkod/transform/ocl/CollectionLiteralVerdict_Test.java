package org.tzi.use.kodkod.transform.ocl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A Bag, Sequence or OrderedSet literal is translated like a set, which keeps neither the
 * duplicates of a bag nor the order of a sequence or an ordered set (issue #135). A run that
 * ends in a verdict must give the verdict that USE's evaluator gives, so such a literal is
 * exact where the operation reads only what a set keeps, and an operation that reads the
 * duplicates or the order is refused with an error and no verdict. A warning before a verdict
 * is no protection: it leaves the user with a verdict that may be wrong, and the run prints
 * none. A set literal keeps working as before.
 */
public class CollectionLiteralVerdict_Test extends MvCommandSession {

	private static final String BOUNDS = "[c]\nInteger_min = 0\nInteger_max = 3\n"
			+ "A_min = 1\nA_max = 1\nA_x = Set{1,2}\nA_i = active\n";

	private static String model(String invariant) {
		return "model M\n"
				+ "class A\n"
				+ "attributes\n"
				+ "  x : Integer\n"
				+ "end\n"
				+ "constraints\n"
				+ "context A inv i: " + invariant + "\n";
	}

	private String validateInvariant(String invariant) throws Exception {
		open(model(invariant));
		return validate(file("c.properties", BOUNDS));
	}

	private static boolean hasVerdict(String output) {
		return output.contains("SATISFIABLE");
	}

	private void assertSatisfiableWithoutWarning(String output) {
		assertTrue(output, output.contains("INFO SATISFIABLE"));
		assertFalse(output, output.contains("UNSATISFIABLE"));
		assertFalse(output, output.contains("ERROR"));
		assertFalse("a WARN line is printed:\n" + output, output.contains("WARN"));
		assertTrue(output, useCheckPasses());
	}

	private void assertRefusedWithoutWarning(String output, String reason) {
		assertTrue(output, output.contains(reason));
		assertFalse(output, hasVerdict(output));
		assertFalse("a WARN line is printed:\n" + output, output.contains("WARN"));
	}

	// claim 1
	@Test
	public void aBagLiteralThatIsOnlyTestedForMembershipGivesAnExactVerdict() throws Exception {
		String output = validateInvariant("Bag{1,2,2}->includes(self.x)");
		assertSatisfiableWithoutWarning(output);
		assertFalse(output, output.contains("does not support bags"));
	}

	// claim 2
	@Test
	public void aBagLiteralWhoseSizeCountsDuplicatesIsRefusedWithoutVerdict() throws Exception {
		String output = validateInvariant("Bag{1,2,2}->size() = 3");
		assertRefusedWithoutWarning(output, "Cannot transform `Bag {1,2,2}->size': its result depends on the duplicates"
				+ " of a collection that is not a set, which the transformation does not keep.");
	}

	// claim 3
	@Test
	public void aSequenceLiteralThatIsOnlyTestedForMembershipGivesAnExactVerdict() throws Exception {
		String output = validateInvariant("Sequence{2,1,1}->includes(self.x)");
		assertSatisfiableWithoutWarning(output);
		assertFalse(output, output.contains("does not support sequences"));
	}

	// claim 4
	@Test
	public void theFirstElementOfASequenceLiteralIsRefusedWithoutVerdict() throws Exception {
		String output = validateInvariant("Sequence{2,1}->first() = 2");
		assertRefusedWithoutWarning(output, "OCL operation first is not supported.");
	}

	// claim 5
	@Test
	public void anOrderedSetLiteralThatIsOnlyTestedForMembershipGivesAnExactVerdict() throws Exception {
		String output = validateInvariant("OrderedSet{1,2}->includes(self.x)");
		assertSatisfiableWithoutWarning(output);
		assertFalse(output, output.contains("does not support orderedSets"));
	}

	// claim 6
	@Test
	public void comparingTwoOrderedSetLiteralsIsRefusedWithoutVerdict() throws Exception {
		String output = validateInvariant("OrderedSet{2,1} = OrderedSet{1,2}");
		assertRefusedWithoutWarning(output, "Cannot transform `(OrderedSet {2,1} = OrderedSet {1,2})':"
				+ " only two sets can be compared.");
	}

	// claim 7: must keep working
	@Test
	public void aSetLiteralKeepsGivingAnExactVerdict() throws Exception {
		String output = validateInvariant("Set{1,2,2}->includes(self.x) and Set{1,2,2}->size() = 2 and Set{2,1} = Set{1,2}");
		assertSatisfiableWithoutWarning(output);
	}
}
