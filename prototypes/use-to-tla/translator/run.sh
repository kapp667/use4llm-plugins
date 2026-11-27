#!/usr/bin/env bash
# Rerun everything: build the plugin, run every case of the corpus through USE and TLC, and
# compare. Usage: run.sh [<out dir> [<results dir>]] (defaults: results/run, results).
# JOBS sets the parallelism, CORPUS a corpus file to use instead of the whole corpus, TLA_MUTANT
# a deliberate translation bug (see Translator.MUTANT).
# Required: USE_HOME, an unpacked USE 7.5.0 release; USE_SRC, a checkout of USE's sources at
# v7.5.0 (its test models are the second corpus); MV_JAR, the ModelValidatorPlugin.jar that
# ci/build-and-test.sh --build-only writes; TLA2TOOLS, the tla2tools.jar of the TLA+ tools.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
use="${USE_HOME:?set USE_HOME}"
: "${USE_SRC:?set USE_SRC}" "${MV_JAR:?set MV_JAR}" "${TLA2TOOLS:?set TLA2TOOLS}"
export TLA2TOOLS
out="${1:-$here/results/run}"
results="${2:-$here/results}"
"$here/build.sh" "$use"
rm -rf "$out"; mkdir -p "$out/use"
cp -r "$use/lib" "$use/etc" "$use/oclextensions" "$out/use/"
# The bundled ModelValidator 5.2.0 does not run on USE 7.5.0: use the fork's build, as the oracle does.
rm -f "$out/use/lib/plugins"/ModelValidatorPlugin-*.jar
cp "$MV_JAR" "$out/use/lib/plugins/"
cp "$here/build/use2tla.jar" "$out/use/lib/plugins/"
# The corpus: every model with a witness, a .cmd, .in or .soil file beside it holding "!" lines.
{
  (cd "$use/examples" && find . -name '*.use' | sed 's|^\./||' | LC_ALL=C sort | sed "s|^|$use/examples\t|")
  (cd "$USE_SRC" && find use-core/src/test/resources use-gui/src/it/resources -name '*.use' | LC_ALL=C sort | sed "s|^|$USE_SRC\t|")
  (cd "$here/probes" && find . -name '*.use' | sed 's|^\./||' | LC_ALL=C sort | sed "s|^|$here/probes\t|")
} >"$out/all-models.tsv"
# A model with a witness, a .cmd, .in or .soil file beside it holding "!" lines, is a witness case;
# every model with an invariant is also a validator case, S0 being the validator's solution.
while IFS=$'\t' read -r root m; do
  for ext in cmd in soil; do
    w="$root/${m%.use}.$ext"
    if [ -f "$w" ] && grep -q '^!' "$w"; then printf '%s\t%s\t%s\n' "$root" "$m" "$w"; break; fi
  done
  if grep -qw inv "$root/$m" && [ "$root" != "$here/probes" ]; then printf '%s\t%s\tvalidator\n' "$root" "$m"; fi
done <"$out/all-models.tsv" >"$out/corpus.tsv"
if [ -n "${CORPUS:-}" ]; then cp "$CORPUS" "$out/corpus.tsv"; fi
echo "corpus: $(wc -l <"$out/corpus.tsv") cases"
tr '\t' '\n' <"$out/corpus.tsv" | xargs -d '\n' -n 3 -P "${JOBS:-4}" "$here/scripts/run-case.sh" "$out"
python3 -I "$here/scripts/compare.py" "$out" "$results"
