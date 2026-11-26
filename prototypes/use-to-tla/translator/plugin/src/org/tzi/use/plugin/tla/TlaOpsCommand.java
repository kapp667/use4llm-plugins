package org.tzi.use.plugin.tla;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;

import org.tzi.use.main.shell.runtime.IPluginShellCmd;
import org.tzi.use.runtime.shell.IPluginShellCmdDelegate;
import org.tzi.use.util.Log;

/**
 * Shell command "tlaops (settings file | default) outdir [module]": translates the loaded model's
 * operations under the settings into outdir/module.tla and .cfg (module defaults to UseOps), and
 * writes ops.tsv (what was translated or refused, and why), checks.tsv (TLC invariant -> what it
 * checks), types.tsv (the pools and the types of the state's values and of the operations'
 * parameters, which a trace tool reads) and settings.txt (the settings used).
 */
public class TlaOpsCommand implements IPluginShellCmdDelegate {

	@Override
	public void performCommand(IPluginShellCmd cmd) {
		PrintStream out = Log.out();
		String[] args = cmd.getCmdArguments().trim().split("\\s+");
		if (args.length < 2 || !cmd.getSession().hasSystem()) {
			out.println("Usage: tlaops (settings file | default) outdir [module], with a model loaded");
			return;
		}
		String name = args.length > 2 ? args[2] : "UseOps";
		File dir = new File(args[1]);
		try {
			Settings set = args[0].equals("default") ? Settings.defaults(cmd.getSession().system().model()) : Settings.read(Paths.get(args[0]));
			Ops ops = new Ops(cmd.getSession().system().model(), set);
			String m = ops.module(name);
			dir.mkdirs();
			TlaCommand.write(dir, name + ".tla", m);
			TlaCommand.write(dir, name + ".cfg", ops.cfg);
			TlaCommand.write(dir, "settings.txt", set.text());
			StringBuilder r = new StringBuilder("kind\tname\tstatus\tconstruct\tdetail\n");
			int ok = 0, no = 0;
			for (String[] row : ops.report) {
				r.append(String.join("\t", row)).append('\n');
				if (row[0].equals("operation")) {
					if (row[2].equals("translated")) ok++;
					else no++;
				}
			}
			TlaCommand.write(dir, "ops.tsv", r.toString());
			StringBuilder c = new StringBuilder("operator\tchecks\n");
			for (java.util.Map.Entry<String, String> e : ops.checks.entrySet()) c.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
			TlaCommand.write(dir, "checks.tsv", c.toString());
			TlaCommand.write(dir, "types.tsv", ops.types());
			out.println("tlaops: " + ok + " operations translated, " + no + " refused, " + ops.checks.size() + " checks, written to " + dir
					+ (Ops.MUTANT.isEmpty() ? "" : ", mutant " + Ops.MUTANT + " applied " + ops.mutated + " times"));
		} catch (IOException | IllegalArgumentException ex) {
			out.println("tlaops: " + ex.getMessage());
		}
	}
}
