package org.tzi.use.kodkod.transform.ocl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.tzi.kodkod.helper.LogMessages;
import org.tzi.kodkod.model.iface.IClass;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.kodkod.model.type.EnumType;
import org.tzi.kodkod.model.type.ObjectType;
import org.tzi.kodkod.model.type.TypeConstants;
import org.tzi.kodkod.model.type.TypeLiterals;
import org.tzi.kodkod.ocl.OCLMethodInvoker;
import org.tzi.use.kodkod.transform.TransformationException;
import org.tzi.use.kodkod.transform.TypeConverter;
import org.tzi.use.uml.ocl.expr.ExpAllInstances;
import org.tzi.use.uml.ocl.expr.ExpAsType;
import org.tzi.use.uml.ocl.expr.ExpAttrOp;
import org.tzi.use.uml.ocl.expr.ExpBagLiteral;
import org.tzi.use.uml.ocl.expr.ExpCollectionLiteral;
import org.tzi.use.uml.ocl.expr.ExpConstBoolean;
import org.tzi.use.uml.ocl.expr.ExpConstEnum;
import org.tzi.use.uml.ocl.expr.ExpConstInteger;
import org.tzi.use.uml.ocl.expr.ExpConstReal;
import org.tzi.use.uml.ocl.expr.ExpConstString;
import org.tzi.use.uml.ocl.expr.ExpEmptyCollection;
import org.tzi.use.uml.ocl.expr.ExpIf;
import org.tzi.use.uml.ocl.expr.ExpIsKindOf;
import org.tzi.use.uml.ocl.expr.ExpIsTypeOf;
import org.tzi.use.uml.ocl.expr.ExpIterate;
import org.tzi.use.uml.ocl.expr.ExpLet;
import org.tzi.use.uml.ocl.expr.ExpNavigation;
import org.tzi.use.uml.ocl.expr.ExpNavigationClassifierSource;
import org.tzi.use.uml.ocl.expr.ExpObjAsSet;
import org.tzi.use.uml.ocl.expr.ExpObjOp;
import org.tzi.use.uml.ocl.expr.ExpOrderedSetLiteral;
import org.tzi.use.uml.ocl.expr.ExpQuery;
import org.tzi.use.uml.ocl.expr.ExpRange;
import org.tzi.use.uml.ocl.expr.ExpSelectByKind;
import org.tzi.use.uml.ocl.expr.ExpSelectByType;
import org.tzi.use.uml.ocl.expr.ExpSequenceLiteral;
import org.tzi.use.uml.ocl.expr.ExpSetLiteral;
import org.tzi.use.uml.ocl.expr.ExpStdOp;
import org.tzi.use.uml.ocl.expr.ExpTupleLiteral;
import org.tzi.use.uml.ocl.expr.ExpTupleSelectOp;
import org.tzi.use.uml.ocl.expr.ExpUndefined;
import org.tzi.use.uml.ocl.expr.ExpVariable;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.util.StringUtil;

import kodkod.ast.Expression;
import kodkod.ast.Node;
import kodkod.ast.Relation;
import kodkod.ast.Variable;

/**
 * Default visitor implementation for the transform use expression.
 * 
 * @author Hendrik Reitmann
 */
public class DefaultExpressionVisitor extends SimpleExpressionVisitor {

	protected static final Logger LOG = Logger.getLogger(DefaultExpressionVisitor.class);
	
	protected IModel model;
	protected Map<String, Node> variables;
	protected List<String> collectionVariables;
	protected Map<String, Variable> replaceVariables;
	
	protected final Relation undefined;
	protected final Relation undefined_Set;

	protected Node object;
	protected boolean set;
	protected Type sourceType;

	private boolean collectionOfCollectionsAccepted = false;

	public DefaultExpressionVisitor(IModel model, Map<String, Node> variables, Map<String, Variable> replaceVariables,
			List<String> collectionVariables) {
		this.model = model;
		this.variables = variables;
		this.replaceVariables = replaceVariables;
		this.collectionVariables = collectionVariables;

		undefined = model.typeFactory().undefinedType().relation();
		undefined_Set = model.typeFactory().undefinedSetType().relation();

		set = false;
	}

	/**
	 * Returns the transformation result.
	 * 
	 * @return
	 */
	public Node getObject() {
		return object;
	}

	/**
	 * Returns true if the result object represents a set.
	 * 
	 * @return
	 */
	public boolean isSet() {
		return set;
	}

	public Type getSourceType() {
		return sourceType;
	}
	
	/**
	 * Accepts a collection of collections as the expression this visitor
	 * transforms.
	 */
	public void acceptCollectionOfCollections() {
		collectionOfCollectionsAccepted = true;
	}

	/**
	 * Transforms an expression that USE takes as a value of the given type. An
	 * expression of type OclVoid, such as null, has the value null, which is the
	 * undefined set if the type is a collection.
	 */
	public void processAs(org.tzi.use.uml.ocl.expr.Expression exp, Type type) {
		if (exp.type().isTypeOfVoidType() && type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
			object = undefined_Set;
			set = true;
		} else {
			exp.processWithVisitor(this);
		}
	}

	@Override
	public void visitAllInstances(ExpAllInstances exp) {
		super.visitAllInstances(exp);
		List<Object> arguments = new ArrayList<Object>();
		
		if(exp.getSourceType().isTypeOfClass()){
			// <Class>.allInstances()
			IClass clazz = model.getClass(exp.getSourceType().name());
			arguments.add(clazz.inheritanceOrRegularRelation());
		}
		//TODO collect cannot handle vars with arity > 1, but assoc relations have arity = #(associationEnds)
//		else if(exp.getSourceType().isTypeOfAssociation()){
//			// <Association>.allInstances()
//			IAssociation assoc = model.getAssociation(exp.getSourceType().name());
//			arguments.add(assoc.relation());
//		}
		else {
			throw new TransformationException("allInstances() not supported for type " + StringUtil.inQuotes(exp.getSourceType().name()));
		}

		invokeMethod("allInstances", arguments, false);
	}

	@Override
	public void visitAsType(ExpAsType exp) {
		super.visitAsType(exp);
		refuseCollectionOfCollections(exp);
		refuseTypeTestWithCollection(exp, exp.getSourceExpr(), exp.getTargetType());
		refuseReal(exp);
		try {
			visitTypeOperation("oclAsType", exp.getSourceExpr(), exp.getTargetType(), true);
		} catch (NoSuchMethodError e) {
			throw new TransformationException(LogMessages.noSuchMethodError, e);
		}
	}

	@Override
	public void visitAttrOp(ExpAttrOp exp) {
		super.visitAttrOp(exp);
		refuseCollectionOfCollections(exp);
		refuseReal(exp);
		visitVariableOperation(exp);
	}

	@Override
	public void visitBagLiteral(ExpBagLiteral exp) {
		super.visitBagLiteral(exp);
		LOG.warn(LogMessages.unsupportedCollectionWarning("bags"));
		visitCollectionLiteral(exp, "bagLiteral");
	}

	@Override
	public void visitConstBoolean(ExpConstBoolean exp) {
		super.visitConstBoolean(exp);
		Map<String, Expression> typeLiterals = model.typeFactory().booleanType().typeLiterals();
		if (exp.value()) {
			object = typeLiterals.get(TypeConstants.BOOLEAN_TRUE);
		} else {
			object = typeLiterals.get(TypeConstants.BOOLEAN_FALSE);
		}
	}

	@Override
	public void visitConstEnum(ExpConstEnum exp) {
		super.visitConstEnum(exp);
		EnumType enumType = model.getEnumType(exp.type().shortName());
		if (enumType != null) {
			enumType.addTypeLiteral(exp.value());
			object = enumType.getTypeLiteral(exp.value());
		} else {
			throw new TransformationException("Cannot find enum " + exp.type().shortName() + ".");
		}
	}

	@Override
	public void visitConstInteger(ExpConstInteger exp) {
		super.visitConstInteger(exp);
		visitConstInteger(exp.value());
	}

	@Override
	public void visitConstReal(ExpConstReal exp) {
		super.visitConstReal(exp);
		throw new TransformationException(LogMessages.realValueError(exp.toString()));
	}

	@Override
	public void visitConstString(ExpConstString exp) {
		LOG.debug("ExpConstString");

		TypeLiterals stringType = model.typeFactory().stringType();
		stringType.addTypeLiteral(exp.value());
		object = stringType.getTypeLiteral(exp.value());
	}

	@Override
	public void visitEmptyCollection(ExpEmptyCollection exp) {
		super.visitEmptyCollection(exp);
		refuseCollectionOfCollections(exp);
		refuseReal(exp);
		sourceType = exp.type();
		invokeMethod("setLiteral", new ArrayList<Object>(), false);
	}

	@Override
	public void visitObjOp(ExpObjOp exp) {
		super.visitObjOp(exp);
		refuseCollectionOfCollections(exp);
		refuseReal(exp);
		DefaultExpressionVisitor visitor = new OperationExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		exp.processWithVisitor(visitor);
		object = visitor.getObject();
		set = visitor.isSet();
	}

	@Override
	public void visitLet(ExpLet exp) {
		super.visitLet(exp);
		refuseCollectionOfCollections(exp);
		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		visitor.processAs(exp.getVarExpression(), exp.getVarType());
		Node varExpression = visitor.getObject();

		variables.put(exp.getVarname(), varExpression);

		if (visitor.isSet()) {
			collectionVariables.add(exp.getVarname());
		}

		visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		exp.getInExpression().processWithVisitor(visitor);
		object = visitor.getObject();
		set = visitor.isSet();

		variables.remove(exp.getVarname());
	}

	@Override
	public void visitIf(ExpIf exp) {
		super.visitIf(exp);
		refuseCollectionOfCollections(exp);
		boolean collection = exp.type().isKindOfCollection(VoidHandling.EXCLUDE_VOID);

		List<Object> arguments = new ArrayList<Object>();

		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		exp.getCondition().processWithVisitor(visitor);
		arguments.add(visitor.getObject());

		arguments.add(branch(exp.getThenExpression(), exp.type()));
		arguments.add(branch(exp.getElseExpression(), exp.type()));

		invokeMethod("if_then_else", arguments, collection);
	}

	private Object branch(org.tzi.use.uml.ocl.expr.Expression branch, Type type) {
		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		visitor.processAs(branch, type);
		if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID) && !visitor.isSet()) {
			throw new TransformationException("Cannot transform " + StringUtil.inQuotes(branch.toString())
					+ ": it is a branch of a collection-valued if but is not translated to a set.");
		}
		return visitor.getObject();
	}

	@Override
	public void visitIsKindOf(ExpIsKindOf exp) {
		super.visitIsKindOf(exp);
		refuseTypeTestWithCollection(exp, exp.getSourceExpr(), exp.getTargetType());
		visitTypeOperation("oclIsKindOf", exp.getSourceExpr(), exp.getTargetType(), true);
	}

	@Override
	public void visitIsTypeOf(ExpIsTypeOf exp) {
		super.visitIsTypeOf(exp);
		refuseTypeTestWithCollection(exp, exp.getSourceExpr(), exp.getTargetType());
		visitTypeOperation("oclIsTypeOf", exp.getSourceExpr(), exp.getTargetType(), false);
	}

	@Override
	public void visitIterate(ExpIterate exp) {
		super.visitIterate(exp);
		throw new TransformationException("Iterate not supported");
	}

	@Override
	public void visitNavigation(ExpNavigation exp) {
		super.visitNavigation(exp);
		visitVariableOperation(exp);
	}

	@Override
	public void visitNavigationClassifierSource(ExpNavigationClassifierSource exp) {
		super.visitNavigationClassifierSource(exp);
		visitVariableOperation(exp);
	}
	
	@Override
	public void visitObjAsSet(ExpObjAsSet exp) {
		super.visitObjAsSet(exp);
		visitVariableOperation(exp.getObjectExpression());
		object = ((Expression) object).eq(undefined).thenElse(Expression.NONE, (Expression) object);
		set = true;
	}

	@Override
	public void visitOrderedSetLiteral(ExpOrderedSetLiteral exp) {
		super.visitOrderedSetLiteral(exp);
		LOG.warn(LogMessages.unsupportedCollectionWarning("orderedSets"));
		visitCollectionLiteral(exp, "orderedSetLiteral");
	}

	@Override
	public void visitQuery(ExpQuery exp) {
		super.visitQuery(exp);
		refuseCollectionOfCollections(exp);
		QueryExpressionVisitor visitor = new QueryExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		exp.processWithVisitor(visitor);
		object = visitor.getObject();
		set = visitor.isSet();
	}
	
	@Override
	public void visitSelectByKind(ExpSelectByKind expSelectByKind) {
		super.visitSelectByKind(expSelectByKind);
		refuseCollectionOfCollections(expSelectByKind);
		refuseReal(expSelectByKind);
		visitTypeOperation("selectByKind", expSelectByKind.getSourceExpression(), expSelectByKind.type().elemType(), true);
	}
	
	@Override
	public void visitExpSelectByType(ExpSelectByType expSelectByType) {
		super.visitExpSelectByType(expSelectByType);
		refuseCollectionOfCollections(expSelectByType);
		refuseReal(expSelectByType);
		visitTypeOperation("selectByType", expSelectByType.getSourceExpression(), expSelectByType.type().elemType(), false);
	}

	@Override
	public void visitSequenceLiteral(ExpSequenceLiteral exp) {
		super.visitSequenceLiteral(exp);
		LOG.warn(LogMessages.unsupportedCollectionWarning("sequences"));
		visitCollectionLiteral(exp, "sequenceLiteral");
	}

	@Override
	public void visitSetLiteral(ExpSetLiteral exp) {
		super.visitSetLiteral(exp);
		visitCollectionLiteral(exp, "setLiteral");
	}

	@Override
	public void visitStdOp(ExpStdOp exp) {
		super.visitStdOp(exp);
		refuseCollectionOfCollections(exp);
		StandardOperationVisitor visitor = new StandardOperationVisitor(model, variables, replaceVariables, collectionVariables);
		exp.processWithVisitor(visitor);
		object = visitor.getObject();
		set = visitor.isSet();
	}

	@Override
	public void visitTupleLiteral(ExpTupleLiteral exp) {
		super.visitTupleLiteral(exp);
		throw new TransformationException("Tuples are not supported");
	}

	@Override
	public void visitTupleSelectOp(ExpTupleSelectOp exp) {
		super.visitTupleSelectOp(exp);
		throw new TransformationException("Tuples are not supported");
	}
	
	@Override
	public void visitUndefined(ExpUndefined exp) {
		super.visitUndefined(exp);
		refuseCollectionOfCollections(exp);
		refuseReal(exp);
		if (exp.type().isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
			object = undefined_Set;
			set = true;
		} else {
			object = undefined;
		}
	}

	@Override
	public void visitVariable(ExpVariable exp) {
		super.visitVariable(exp);
		refuseCollectionOfCollections(exp);
		refuseReal(exp);
		visitVariableOperation(exp);
	}

	@Override
	public void visitRange(ExpRange exp) {
		super.visitRange(exp);
		
		org.tzi.use.uml.ocl.expr.Expression[] expToVisit = new org.tzi.use.uml.ocl.expr.Expression[]{
				exp.getStart(),
				exp.getEnd()
		};
		List<Object> args = new ArrayList<Object>(2);
		
		DefaultExpressionVisitor visitor;
		for(org.tzi.use.uml.ocl.expr.Expression e : expToVisit){
			visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
			e.processWithVisitor(visitor);
			args.add(visitor.getObject());
		}
		
		invokeMethod("mkSetRange", args, false);
	}
	
	/**
	 * Handle a constant int value.
	 * 
	 * @param value
	 */
	protected void visitConstInteger(int value) {
		TypeLiterals integerType = model.typeFactory().integerType();
		
		integerType.addTypeLiteral("" + value);
		object = integerType.getTypeLiteral("" + value);
	}

	/**
	 * Visit every kind of collection literal.
	 * 
	 * @param exp
	 * @param literal
	 */
	private void visitCollectionLiteral(ExpCollectionLiteral exp, String literal) {
		refuseCollectionOfCollections(exp);
		List<Object> arguments = new ArrayList<Object>();

		sourceType = exp.type();
		
		DefaultExpressionVisitor visitor;
		for (org.tzi.use.uml.ocl.expr.Expression expression : exp.getElemExpr()) {
			visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
			expression.processWithVisitor(visitor);
			arguments.add(visitor.getObject());
		}

		invokeMethod(literal, arguments, false);
	}

	/**
	 * Visit every kind of variable operation.
	 * 
	 * @param exp
	 */
	private void visitVariableOperation(org.tzi.use.uml.ocl.expr.Expression exp) {
		VariableOperationVisitor visitor = new VariableOperationVisitor(model, variables, replaceVariables, collectionVariables);
		exp.processWithVisitor(visitor);
		object = visitor.getObject();
		set = visitor.isSet();
		sourceType = visitor.getSourceType();
	}

	private void refuseTypeTestWithCollection(org.tzi.use.uml.ocl.expr.Expression typeTest, org.tzi.use.uml.ocl.expr.Expression source, Type targetType) {
		if (isCollectionOfCollections(targetType)) {
			throw new TransformationException(LogMessages.collectionOfCollectionsError(targetType.toString()));
		}
		refuseCollectionOfCollections(source);
		if (source.type().isKindOfCollection(VoidHandling.EXCLUDE_VOID) || targetType.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
			throw new TransformationException(LogMessages.collectionTypeTestError(typeTest.toString()));
		}
	}

	/**
	 * Visit every kind of type operation.
	 *
	 * @param opName
	 * @param sourceExpression
	 * @param targetType
	 */
	private void visitTypeOperation(String opName, org.tzi.use.uml.ocl.expr.Expression sourceExpression, Type targetType, boolean useInheritance) {
		if (isCollectionOfCollections(targetType)) {
			throw new TransformationException(LogMessages.collectionOfCollectionsError(targetType.toString()));
		}
		if (mentionsReal(targetType)) {
			throw new TransformationException(LogMessages.realValueError(targetType.toString()));
		}
		List<Object> arguments = new ArrayList<Object>();

		DefaultExpressionVisitor visitor = new DefaultExpressionVisitor(model, variables, replaceVariables, collectionVariables);
		sourceExpression.processWithVisitor(visitor);
		arguments.add(visitor.getObject());
		set = visitor.isSet();

		TypeConverter typeConverter = new TypeConverter(model);
		org.tzi.kodkod.model.type.Type type = typeConverter.convert(targetType);
		Expression typeExpression;
		if(type instanceof ObjectType){
			IClass cls = ((ObjectType) type).clazz();
			typeExpression = useInheritance ? cls.inheritanceOrRegularRelation() : cls.relation();
		} else {
			typeExpression = typeConverter.typeToExpression(type);
		}
		
		if (typeExpression != null) {
			arguments.add(typeExpression);
		} else {
			throw new TransformationException("No support for " + targetType + " as target type of " + opName + ".");
		}

		invokeMethod(opName, arguments, set);
	}

	/**
	 * Refuses an expression whose value is a collection of collections, since the
	 * transformation represents a collection by the set of its elements.
	 */
	private void refuseCollectionOfCollections(org.tzi.use.uml.ocl.expr.Expression exp) {
		if (!collectionOfCollectionsAccepted && isCollectionOfCollections(exp.type())) {
			throw new TransformationException(LogMessages.collectionOfCollectionsError(exp.toString()));
		}
	}

	/**
	 * Refuses an expression whose value is a Real or a collection of Reals, since
	 * the transformation does not compute with Real values. An operation that USE
	 * types as Real only because an operand is undefined computes with integers and
	 * is not refused.
	 */
	private void refuseReal(org.tzi.use.uml.ocl.expr.Expression exp) {
		if (mentionsReal(exp.type())) {
			throw new TransformationException(LogMessages.realValueError(exp.toString()));
		}
	}

	private static boolean mentionsReal(Type type) {
		if (type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)) {
			return mentionsReal(((CollectionType) type).elemType());
		}
		return type.isTypeOfReal();
	}

	private static boolean isCollectionOfCollections(Type type) {
		return type.isKindOfCollection(VoidHandling.EXCLUDE_VOID)
				&& ((CollectionType) type).elemType().isKindOfCollection(VoidHandling.EXCLUDE_VOID);
	}

	/**
	 * Whether a value of the type can hold an element more than once, which the
	 * set the transformation represents it by does not keep.
	 */
	protected static boolean canHoldDuplicates(Type type) {
		return type.isTypeOfBag() || type.isTypeOfSequence() || type.isTypeOfCollection();
	}

	/**
	 * Whether the elements of a value of the type can have an order, which the set
	 * the transformation represents it by does not keep.
	 */
	protected static boolean canBeOrdered(Type type) {
		return type.isTypeOfSequence() || type.isTypeOfOrderedSet() || type.isTypeOfCollection();
	}

	/**
	 * Invokes the method to transform the operation with the given name.
	 */
	protected void invokeMethod(String opName, List<Object> arguments, boolean setOperation) {
		OCLMethodInvoker invoker = new OCLMethodInvoker();
		invoker.invoke(opName, arguments, setOperation);
		object = invoker.getObject();
		set = invoker.isSet();
	}
}
