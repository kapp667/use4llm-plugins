package org.tzi.use.plugin.filmstrip;

import java.util.ArrayList;
import java.util.Map.Entry;

import org.tzi.use.uml.mm.MAttribute;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.ocl.type.TupleType;
import org.tzi.use.uml.ocl.type.TupleType.Part;
import org.tzi.use.uml.ocl.type.Type;
import org.tzi.use.uml.ocl.type.Type.VoidHandling;
import org.tzi.use.util.StringUtil;

public final class FilmstripModelConstants {

	// Every name the transformation adds to the model starts with this
	// prefix; empty by default. Set it with setNamePrefix before a
	// transformation and reset it afterwards (see FilmstripTransformer).
	private static String namePrefix = "";
	
	public static String SNAPSHOT_CLASSNAME;
	public static String FILMSTRIP_ASSOCNAME;
	
	public static String SNAPSHOTITEM_CLASSNAME;
	public static String ORDERABLE_ASSOCNAME;
	public static String SNAPSHOTELEMENT_ASSOCNAME;
	public static String SUCC_ROLENAME;
	public static String PRED_ROLENAME;
	
	public static String OPC_CLASSNAME;
	public static final String OPC_ABBREVIATION = "OpC";
	public static String OPC_SELF_VARNAME;
	public static final String OPC_RETURNVALUE_VARNAME = "result";
	
	public static String COPY_OPNAME;
	public static String COPY_PRE_UNCOPIED_NAME;
	
	//element for filmstrip binary association 
	
	public static String SNAPSHOT_OPC;
	public static String SNAP_ROLENAME;
	public static String OPC_ROLENAME;
	
	public static String OpCSnapSNAPSHOT_PRED_OP;
	public static String OpCSnapSNAPSHOT_SUCC_OP;
	public static String OpCSnapOPC_PRED_OP;
	public static String OpCSnapOPC_SUCC_OP;

	public static String SUCC_OPC_EXISTS_NAME;
	public static String SUCC_OPC_EXISTS;
	/////////////
	public static String SNAPSHOT_ROLENAME;
	
	public static String SNAPSHOT_PRED_OP;
	public static String SNAPSHOT_SUCC_OP;
	
	// constraints
	public static String OPC_INV_ASSOCCLASSBEHAVIOR_NAME;
	public static String OPC_INV_ASSOCCLASSBEHAVIOR;
	
	public static String SNAPSHOT_INV_CYCLEFREE_NAME;
	public static String SNAPSHOT_INV_CYCLEFREE;
	
	public static String SNAPSHOT_INV_ONEFILMSTRIP_NAME;
	public static String SNAPSHOT_INV_ONEFILMSTRIP;
	
	public static String OPC_INV_SELFDEFINED_NAME;
	public static String OPC_INV_SELFDEFINED;
	
	public static String OPC_INV_SELFINPRED_NAME;
	public static String OPC_INV_SELFINPRED;
	
	public static String CLASS_INV_VALIDLINKING_NAME;
	public static String CLASS_INV_VALIDLINKING;
	
	static {
		setNamePrefix("");
	}
	
	private FilmstripModelConstants(){
	}
	
	public static String getNamePrefix(){
		return namePrefix;
	}
	
	/**
	 * Sets the prefix of every name the transformation adds and recomputes
	 * the names and OCL fragments built from them. The fields are shared and
	 * not thread-safe: FilmstripTransformer sets them while it holds the lock
	 * on this class.
	 */
	public static void setNamePrefix(String prefix){
		namePrefix = prefix;
		
		SNAPSHOT_CLASSNAME = prefix + "Snapshot";
		FILMSTRIP_ASSOCNAME = prefix + "Filmstrip";
		
		SNAPSHOTITEM_CLASSNAME = prefix + "SnapshotItem";
		ORDERABLE_ASSOCNAME = prefix + "PredSucc";
		SNAPSHOTELEMENT_ASSOCNAME = prefix + "SnapElement";
		SUCC_ROLENAME = prefix + "succ";
		PRED_ROLENAME = prefix + "pred";
		
		OPC_CLASSNAME = prefix + "OperationCall";
		OPC_SELF_VARNAME = prefix + "aSelf";
		
		COPY_OPNAME = prefix + "copy";
		COPY_PRE_UNCOPIED_NAME = prefix + "uncopied";
		
		SNAPSHOT_OPC = prefix + "SnapshotOpC";
		SNAP_ROLENAME = prefix + "snapshot";
		OPC_ROLENAME = prefix + "opc";
		
		OpCSnapSNAPSHOT_PRED_OP = "self." + PRED_ROLENAME;
		OpCSnapSNAPSHOT_SUCC_OP = "self." + SUCC_ROLENAME;
		OpCSnapOPC_PRED_OP = "self." + SNAP_ROLENAME;
		OpCSnapOPC_SUCC_OP = "self." + SNAP_ROLENAME + "." + SUCC_ROLENAME;
		
		SUCC_OPC_EXISTS_NAME = prefix + "succOpCExists";
		SUCC_OPC_EXISTS = "(not self. " + SUCC_ROLENAME + ".oclIsUndefined() implies (not self."+OPC_ROLENAME+".oclIsUndefined()))";
		
		SNAPSHOT_ROLENAME = FilmstripModelConstants.makeRoleName(SNAPSHOT_CLASSNAME);
		
		SNAPSHOT_PRED_OP = "if self." + PRED_ROLENAME
				+ "->size() = 1 then self." + PRED_ROLENAME
				+ "->any( true ) else oclUndefined(" + SNAPSHOT_CLASSNAME
				+ ") endif";
		SNAPSHOT_SUCC_OP = "if self." + SUCC_ROLENAME
				+ "->size() = 1 then self." + SUCC_ROLENAME
				+ "->any( true ) else oclUndefined(" + SNAPSHOT_CLASSNAME
				+ ") endif";
		
		OPC_INV_ASSOCCLASSBEHAVIOR_NAME = prefix + "assocClassBehavior";
		OPC_INV_ASSOCCLASSBEHAVIOR = 
				"self." + PRED_ROLENAME + "->size() = 1 and "
				+ "self." + SUCC_ROLENAME + "->size() = 1 and "
				+ OPC_CLASSNAME + ".allInstances()->forAll( op | "
				+ "(self." + PRED_ROLENAME + " = op." + PRED_ROLENAME + " and "
				+ "self." + SUCC_ROLENAME + " = op." + SUCC_ROLENAME + ") implies "
				+ "self = op)";
		
		SNAPSHOT_INV_CYCLEFREE_NAME = prefix + "cycleFree";
		SNAPSHOT_INV_CYCLEFREE = "Set{ self. " + SUCC_ROLENAME + "() }->closure( s | s."
				+ SUCC_ROLENAME + "() )->excludes( self )";
		
		SNAPSHOT_INV_ONEFILMSTRIP_NAME = prefix + "oneFilmstrip";
		SNAPSHOT_INV_ONEFILMSTRIP = 
				"(" + SNAPSHOT_CLASSNAME + ".allInstances()->select( s | s."
				+ PRED_ROLENAME + "().oclIsUndefined() )->size() = 1 and "
				+ SNAPSHOT_CLASSNAME + ".allInstances()->select( s | s."
				+ SUCC_ROLENAME + "().oclIsUndefined() )->size() = 1)";
		
		OPC_INV_SELFDEFINED_NAME = prefix + "aSelfDefined";
		OPC_INV_SELFDEFINED = "self." + OPC_SELF_VARNAME + ".isDefined";
		
		OPC_INV_SELFINPRED_NAME = prefix + "aSelfInPred";
		OPC_INV_SELFINPRED = "self." + OPC_SELF_VARNAME
				+ "." + SNAPSHOT_ROLENAME + " = self."
				+ PRED_ROLENAME + "()";
		
		CLASS_INV_VALIDLINKING_NAME = prefix + "validSnapshotLinking";
		CLASS_INV_VALIDLINKING = "self." + SUCC_ROLENAME
				+ ".isDefined implies " + "self." + SUCC_ROLENAME + "."
				+ SNAPSHOT_ROLENAME + " = self."
				+ SNAPSHOT_ROLENAME + "." + SUCC_ROLENAME + "()";
	}
	
	public static String makeOpCName(String className){
		return String.format("%s%s%s", namePrefix, className, FilmstripModelConstants.OPC_ABBREVIATION);
	}
	
	public static String makeOpCName(String className, String opName){
		return String.format("%s%s_%s%s", namePrefix, opName, className, FilmstripModelConstants.OPC_ABBREVIATION);
	}
	
	// only called on names of generated classes: the letter after the
	// prefix is lowered (fs_SnapshotItem -> fs_snapshotItem)
	public static String makeRoleName(String className){
		if(className.isEmpty()){
			return "";
		}
		int start = className.startsWith(namePrefix) ? namePrefix.length() : 0;
		if(start == className.length()){
			return className;
		}
		return className.substring(0, start)
				+ Character.toLowerCase(className.charAt(start))
				+ className.substring(start + 1);
	}
	
	public static String makeInvName(String invName, boolean preCond){
		return String.format("%s%s_%s", namePrefix, (preCond?"pre":"post"), invName);
	}
	
	public static String makeSnapshotClsAssocName(String cls){
		return String.format("%s%s", SNAPSHOT_CLASSNAME, cls);
	}
	
	public static String makeSnapshotClsRoleName(String cls){
		return String.format("%s%s", SNAPSHOT_ROLENAME, cls);
	}

	public static String makeClsOrdableAssocName(String cls) {
		return String.format("%s%s", FilmstripModelConstants.ORDERABLE_ASSOCNAME, cls);
	}

	public static String makeClsPredRolename(String cls) {
		return String.format("%s%s", FilmstripModelConstants.PRED_ROLENAME, cls);
	}
	
	public static String makeClsSuccRolename(String cls) {
		return String.format("%s%s", SUCC_ROLENAME, cls);
	}
	
	public static String makeParamDefinedInvName(MAttribute param, boolean inPred){
		// the unprefixed word, capitalized below; the prefix leads the whole name
		String predSucc = inPred ? "pred" : "succ";
		return String.format("%sparam%sIn%s", namePrefix,
				Character.toUpperCase(param.name().charAt(0))
						+ param.name().substring(1),
				Character.toUpperCase(predSucc.charAt(0))
						+ predSucc.substring(1));
	}
	
	public static String makeParamDefinedInv(String varName, Type t, boolean inPred){
		if(t.isTypeOfClass()){
			return "((not " + varName + ".oclIsUndefined()) implies " + varName
					+ "." + SNAPSHOT_ROLENAME + " = self."
					+ (inPred ? PRED_ROLENAME : SUCC_ROLENAME) + "())";
		}
		else if(t.isKindOfCollection(VoidHandling.EXCLUDE_VOID)){
			CollectionType collType = (CollectionType) t;
			String tmp = makeParamDefinedInv("c", collType.elemType(), inPred);
			if(!tmp.equals("true")){
				return varName + "->forAll( c | " + tmp + " )";
			}
		}
		else if(t.isTypeOfTupleType()){
			TupleType tupleType = (TupleType) t;
			
			ArrayList<String> parts = new ArrayList<String>(tupleType.getParts().size());
			for(Entry<String, Part> part : tupleType.getParts().entrySet()){
				String tmp = makeParamDefinedInv(varName + "." + part.getKey(), part.getValue().type(), inPred);
				if(!tmp.equals("true")){
					parts.add(tmp);
				}
			}
			
			if(parts.size() > 0){
				return StringUtil.fmtSeq(parts, " and ");
			}
		}
		return "true";
	}
	
	public static String makeValidLinkingInvName(String assoc){
		return String.format("%svalidLinking%s", namePrefix, assoc);
	}
	
	public static String makeValidLinkingInvPart(String endName, boolean isCollection){
		if(isCollection){
			return String.format("self.%s->forAll( c | c.%s = self.%s )", endName,
					SNAPSHOT_ROLENAME,
					SNAPSHOT_ROLENAME);
		}
		else {
			return String.format("(self.%s.isDefined implies self.%s = self.%s.%s)", endName,
					SNAPSHOT_ROLENAME, endName,
					SNAPSHOT_ROLENAME);
		}
	}
	
}
