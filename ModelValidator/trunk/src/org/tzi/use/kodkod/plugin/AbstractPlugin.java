package org.tzi.use.kodkod.plugin;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.tzi.kodkod.KodkodModelValidatorConfiguration;
import org.tzi.kodkod.model.iface.IModel;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.kodkod.UseLogAppender;
import org.tzi.use.kodkod.transform.enrich.ModelEnricher;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.ocl.expr.Expression;
import org.tzi.use.uml.sys.MSystem;

/**
 * Abstract base class for the new commands.
 * 
 * @author Hendrik Reitmann
 * 
 */
public abstract class AbstractPlugin {

	static {
		KodkodPlugin.configureLogging();
	}

	protected static final Logger LOG = Logger.getLogger(AbstractPlugin.class);

	protected Session session;
	protected MModel mModel;
	protected MSystem mSystem;

	protected void initialize(Session session) {
		this.session = session;
		mSystem = session.system();
		mModel = mSystem.model();
		PluginModelFactory.INSTANCE.registerForSession(session);
	}

	protected void initialize(Session session, MainWindow mainWindow) {
		initialize(session);
		UseLogAppender.initialize(mainWindow.logWriter());
	}

	/**
	 * Enriches the model with a given object diagram (automatic diagram extraction).
	 *
	 * @return false if the extraction failed. The error is logged then, and the
	 *         caller must not validate the model.
	 */
	protected boolean enrichModel() {
		ModelEnricher enricher = KodkodModelValidatorConfiguration.getInstance().getModelEnricher();
		try {
			enricher.enrichModel(mSystem, model());
			return true;
		} catch (IllegalStateException e) {
			LOG.error(e.getMessage() + " " + e.getCause());
			return false;
		}
	}

	protected IModel model() {
		return PluginModelFactory.INSTANCE.getModel(mModel);
	}

	/**
	 * Returns the expanded expression of each class invariant of the model.
	 */
	protected List<Expression> extractInvariants() {
		final List<Expression> invariants = new ArrayList<>();
		for (MClassInvariant mClassInvariant : mModel.classInvariants()) {
			invariants.add(mClassInvariant.expandedExpression());
		}
		return invariants;
	}

}
