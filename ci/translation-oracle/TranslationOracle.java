package org.tzi.use.kodkod.transform.ocl;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.tzi.use.kodkod.plugin.KodkodQueryCmd;
import org.tzi.use.kodkod.plugin.KodkodScrollingValidateCmd;

/**
 * Asks mv ? for every expression of a list in every state that the scrolling
 * validator finds within a properties file, and writes what mv ? answered.
 * mv ? compares USE's value of the expression with the value of its
 * translation in the Kodkod solution that the state shows.
 *
 * Usage: TranslationOracle model.use model.properties expressions.txt
 * maxStates describe-expression out.tsv
 *
 * Each row of out.tsv is: state, outcome, expression, USE value, translated
 * value or reason, and "warn" when the translation logged a warning. The
 * outcome is agree, differ, not-compared or other (anything else mv ? printed,
 * such as a parse error). Agreeing rows are counted, not written. An
 * expression without allInstances does not read the state and is asked in the
 * first state only. A state row gives USE's value of the describing expression.
 */
public class TranslationOracle {

	private static final String NEXT = "to search a new solution";
	private static final String NOT_COMPARED = "Not compared with a solution: ";
	private static final String DIFFERS = "differently from USE. USE: ";

	public static void main(String[] args) throws Exception {
		if (args.length != 6) {
			System.err.println("Usage: TranslationOracle model.use model.properties expressions.txt maxStates describe-expression out.tsv");
			System.exit(2);
		}
		String spec = Files.readString(Path.of(args[0]), StandardCharsets.UTF_8);
		List<String> expressions = Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8);
		int maxStates = Integer.parseInt(args[3]);
		String describe = args[4];
		long agreed = 0;
		int states = 0;
		try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Path.of(args[5]), StandardCharsets.UTF_8))) {
			PluginShell shell = new PluginShell(spec);
			KodkodQueryCmd query = new KodkodQueryCmd();
			KodkodScrollingValidateCmd scrolling = new KodkodScrollingValidateCmd();
			shell.run(query, "enable");
			String solved = shell.run(scrolling, args[1]);
			while (solved.contains(NEXT) && states < maxStates) {
				states++;
				out.println(states + "\tstate\t" + describe + "\t" + useValue(shell.run(query, describe)) + "\t\t");
				for (String expression : expressions) {
					if (states > 1 && !expression.contains("allInstances")) {
						continue;
					}
					String answer = shell.run(query, expression);
					String outcome = outcome(answer);
					if (outcome.equals("agree")) {
						agreed++;
						continue;
					}
					out.println(states + "\t" + outcome + "\t" + expression + "\t" + useValue(answer) + "\t"
							+ detail(outcome, answer) + "\t" + (answer.contains("WARN ") ? "warn" : ""));
				}
				solved = shell.run(scrolling, "next");
			}
			out.println("0\tend\t" + (solved.contains(NEXT) ? "stopped at maxStates" : "no more states") + "\t" + states + "\t"
					+ agreed + "\t");
			shell.close();
		}
		System.out.println("states: " + states + ", agreeing answers: " + agreed);
	}

	private static String outcome(String answer) {
		if (answer.contains(DIFFERS)) {
			return "differ";
		}
		if (answer.contains("ERROR") || answer.contains("Exception") || !answer.startsWith("INFO ")) {
			return "other";
		}
		return answer.contains(NOT_COMPARED) ? "not-compared" : "agree";
	}

	private static String useValue(String answer) {
		String first = answer.split("\n", 2)[0];
		return first.startsWith("INFO ") ? flat(first.substring(5)) : "";
	}

	private static String detail(String outcome, String answer) {
		if (outcome.equals("differ")) {
			String rest = answer.substring(answer.indexOf(DIFFERS) + DIFFERS.length());
			return flat(rest.substring(rest.indexOf("; Kodkod: ") + 10, rest.indexOf(".\n") < 0 ? rest.length() : rest.indexOf(".\n")));
		}
		if (outcome.equals("not-compared")) {
			String rest = answer.substring(answer.indexOf(NOT_COMPARED) + NOT_COMPARED.length());
			return flat(rest.split("\n", 2)[0]);
		}
		return flat(answer);
	}

	private static String flat(String text) {
		return text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim();
	}
}
