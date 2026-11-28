package org.tzi.use.kodkod.transform.ocl;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;
import org.tzi.use.kodkod.transform.ocl.anyOperation.AnyTestSuite;
import org.tzi.use.kodkod.transform.ocl.attributeAccess.AttributeAccess_Test;
import org.tzi.use.kodkod.transform.ocl.booleanOperation.BooleanTestSuite;
import org.tzi.use.kodkod.transform.ocl.classOperation.AllInstances_Test;
import org.tzi.use.kodkod.transform.ocl.ifThenElse.IfThenElse_Test;
import org.tzi.use.kodkod.transform.ocl.integerOperation.IntegerTestSuite;
import org.tzi.use.kodkod.transform.ocl.let.Let_Test;
import org.tzi.use.kodkod.transform.ocl.navigation.Navigation_Test;
import org.tzi.use.kodkod.transform.ocl.setConstructor.SetConstructor_Test;
import org.tzi.use.kodkod.transform.ocl.setOperation.SetTestSuite;

@RunWith(Suite.class)
@SuiteClasses({ AnyTestSuite.class, AttributeAccess_Test.class, BooleanTestSuite.class, AllInstances_Test.class, IfThenElse_Test.class,
		IntegerTestSuite.class, SetConstructor_Test.class, Navigation_Test.class, Let_Test.class, SetTestSuite.class, ObjectDiagram_Test.class,
		UntransformedInvariant_Test.class, PartialModel_Test.class,
		SubsetsAssociationEnd_Test.class, UnionAssociationEnd_Test.class,
		RedefinesAssociationEnd_Test.class,
		SolutionDiagram_Test.class,
		CollectionAttributeDiagram_Test.class,
		QualifiedAssociation_Test.class,
		NestedCollection_Test.class,
		CollectionKind_Test.class,
		StringOperation_Test.class,
		SolutionRecheck_Test.class,
		RefusedSolutionVerdict_Test.class,
		IndepSolutionCheck_Test.class,
		SetAny_Test.class,
		ZeroOrOneNavigation_Test.class,
		DerivedEndNavigation_Test.class,
		OperationCall_Test.class,
		ZeroMultiplicityEnd_Test.class,
		UnhandledExpression_Test.class,
		CollectionTypeTest_Test.class,
		RealValue_Test.class,
		DefaultConfiguration_Test.class,
		RealAtom_Test.class,
		RealStep_Test.class,
		AttributeValueBounds_Test.class,
		AssociationClassMultiplicity_Test.class,
		IntegerArithmetic_Test.class,
		IntegerCollectionBounds_Test.class,
		OclAnyAttribute_Test.class,
		EmptyDomainAttribute_Test.class,
		ClosureUndefined_Test.class,
		PluginCommands_Test.class,
		ConfigurationValidationCmd_Test.class,
		KodkodValidateCmd_Test.class,
		UnknownConfigurationKey_Test.class,
		KodkodQueryCmd_Test.class,
		KodkodCTScrollingValidateCmd_Test.class,
		ScrollingNext_Test.class,
		StructureCheck_Test.class,
		ObjectDiagramExtraction_Test.class,
		ObjectDiagramCollectionExtraction_Test.class,
		CollectDuplicates_Test.class,
		SubclassSetAttributeBounds_Test.class,
		SubclassAttributeMaxValidation_Test.class,
		MultipleInheritanceAttribute_Test.class,
		NonTranslatedAttribute_Test.class,
		DerivedEndMultiplicity_Test.class })
public class TestSuite {
}
