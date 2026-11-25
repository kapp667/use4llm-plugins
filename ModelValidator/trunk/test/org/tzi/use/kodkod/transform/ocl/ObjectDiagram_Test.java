package org.tzi.use.kodkod.transform.ocl;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.tzi.use.uml.mm.MAssociationClass;
import org.tzi.use.uml.mm.MAssociationEnd;
import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.sys.MObject;

/**
 * Checks that the Kodkod instance every test is evaluated on holds exactly the
 * object diagram of the test: USE and Kodkod agree on the objects of each
 * class, and on the attribute values and the navigations of each object.
 */
public class ObjectDiagram_Test extends OCLTest {

	// the objects of every class
	@Test
	public void test1() {
		for (MClass cls : mSystem.model().classes()) {
			test("test1", cls.name() + ".allInstances");
		}
	}

	// the attribute values of every object
	@Test
	public void test2() {
		for (MObject object : mSystem.state().allObjects()) {
			for (MAttribute attribute : object.cls().allAttributes()) {
				test("test2", object.name() + "." + attribute.name());
			}
		}
	}

	// the navigations from every object to the association ends: with test1, they
	// also hold every link object, which is navigated to its participants. When
	// the object's class plays several roles in the association, USE requires the
	// navigation to name the source role.
	@Test
	public void test3() {
		for (MObject object : mSystem.state().allObjects()) {
			for (String role : object.cls().navigableEnds().keySet()) {
				// the link objects of an association class are checked from their side
				if (object.cls().navigableEnds().get(role) instanceof MAssociationClass) {
					continue;
				}
				MAssociationEnd target = (MAssociationEnd) object.cls().navigableEnds().get(role);
				List<MAssociationEnd> sources = new ArrayList<MAssociationEnd>();
				for (MAssociationEnd end : target.association().associationEnds()) {
					if (end != target && end.cls() == object.cls()) {
						sources.add(end);
					}
				}
				if (sources.size() <= 1) {
					test("test3", object.name() + "." + role);
				} else {
					for (MAssociationEnd source : sources) {
						test("test3", object.name() + "." + role + "[" + source.nameAsRolename() + "]");
					}
				}
			}
		}
	}

}
