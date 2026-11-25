#!/usr/bin/env bash
# Build the ModelValidator and Filmstrip plugin jars against a USE install, then run the
# ModelValidator JUnit suite. Uses javac, jar and java from PATH.
# Usage: ci/build-and-test.sh <USE install dir> <JUnit jar dir> <output dir> [<JaCoCo jar dir>]
#   USE install dir: the unpacked USE binary release (searched for lib/use-core-*.jar and lib/use-gui.jar)
#   JUnit jar dir:   holds junit-4.13.2.jar and hamcrest-core-1.3.jar
#   JaCoCo jar dir:  optional, holds org.jacoco.agent-*-runtime.jar and org.jacoco.cli-*-nodeps.jar;
#                    when given, the JUnit run is measured and a report is written
# Outputs: <output dir>/ModelValidatorPlugin.jar and <output dir>/use-filmstrip.jar,
#          plus <output dir>/coverage (html/, coverage.csv, coverage.xml) with a JaCoCo dir
set -euo pipefail

[ "$#" -eq 3 ] || [ "$#" -eq 4 ] || { sed -n '2,10p' "$0" >&2; exit 2; }
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
usedir="$(cd "$1" && pwd)"
junit="$(cd "$2" && pwd)"
mkdir -p "$3"
out="$(cd "$3" && pwd)"
jacoco=""
if [ "$#" -eq 4 ]; then jacoco="$(cd "$4" && pwd)"; fi

# The release zip may or may not have a top folder: look for the jars instead of assuming a path.
usecore="$(find "$usedir" -path '*/lib/use-core-*.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
usegui="$(find "$usedir" -path '*/lib/use-gui.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
[ -n "$usecore" ] && [ -n "$usegui" ] || { echo "use-core-*.jar or use-gui.jar not found under $usedir" >&2; exit 1; }
usecp="$usecore:$usegui"
echo "USE core: $usecore"
echo "USE gui:  $usegui"

build_mv() {
  local mv="$root/ModelValidator/trunk" b="$out/mv"
  mkdir -p "$b/classes"
  local cp="$usecp:$(find "$mv/lib" -name '*.jar' | sort | tr '\n' ':')"
  find "$mv/src" -name '*.java' >"$b/sources.txt"
  javac -nowarn -Xmaxerrs 500 -encoding UTF-8 --release 21 -proc:none -cp "$cp" -d "$b/classes" @"$b/sources.txt"
  cp -r "$mv/resources" "$b/classes/resources"
  cp -r "$mv/log4j" "$b/classes/log4j"
  cp "$mv/useplugin.xml" "$b/classes/"
  # The plugin jar bundles its libraries (Kodkod, SAT4J, ...), as the ant jar target does.
  (cd "$b/classes" && for j in "$mv"/lib/*.jar; do jar xf "$j"; done)
  rm -f "$b/classes/META-INF/MANIFEST.MF"
  jar cfm "$out/ModelValidatorPlugin.jar" "$mv/META-INF/MANIFEST.MF" -C "$b/classes" .
  echo "[mv] jar: $out/ModelValidatorPlugin.jar"
}

build_fs() {
  local fs="$root/FilmstripTransformation/trunk" b="$out/fs"
  mkdir -p "$b/classes"
  find "$fs/src" -name '*.java' >"$b/sources.txt"
  javac -nowarn -Xmaxerrs 500 -encoding UTF-8 --release 21 -proc:none -cp "$usecp" -d "$b/classes" @"$b/sources.txt"
  cp -r "$fs/resources" "$b/classes/resources"
  cp "$fs/useplugin.xml" "$fs/README" "$fs/AUTHORS" "$b/classes/"
  jar cfm "$out/use-filmstrip.jar" "$fs/META-INF/MANIFEST.MF" -C "$b/classes" .
  echo "[fs] jar: $out/use-filmstrip.jar"
}

run_junit() {
  local mv="$root/ModelValidator/trunk" tc="$out/mv/test-classes"
  local cp="$out/mv/classes:$usecp:$junit/junit-4.13.2.jar:$junit/hamcrest-core-1.3.jar"
  mkdir -p "$tc"
  find "$mv/test" -name '*.java' >"$out/mv/test-sources.txt"
  javac -nowarn -encoding UTF-8 --release 21 -proc:none -cp "$cp" -d "$tc" @"$out/mv/test-sources.txt"
  echo "[junit] test sources compiled"
  local agent=() status=0 report=0
  if [ -n "$jacoco" ]; then
    agent=("-javaagent:$(find "$jacoco" -name 'org.jacoco.agent-*-runtime.jar' | sort | sed -n 1p)=destfile=$out/jacoco.exec")
  fi
  # The tests read their models relative to the working directory.
  cd "$mv"
  java "${agent[@]}" -Djava.awt.headless=true -cp "$tc:$mv/test:$cp" org.junit.runner.JUnitCore org.tzi.use.kodkod.transform.ocl.TestSuite || status=$?
  if [ -n "$jacoco" ]; then
    report_coverage || report=$?
    [ "$status" -ne 0 ] || status="$report"
  fi
  return "$status"
}

# The report runs even when JUnit fails; run_junit still returns JUnit's status.
# Only the plugin's own packages (org/tzi) are measured, not the libraries unpacked into the same folder.
# The Filmstrip plugin has no tests, so it is left out.
report_coverage() {
  local mv="$root/ModelValidator/trunk" cli
  cli="$(find "$jacoco" -name 'org.jacoco.cli-*-nodeps.jar' | sort | sed -n 1p)"
  mkdir -p "$out/coverage"
  java -jar "$cli" report "$out/jacoco.exec" --classfiles "$out/mv/classes/org/tzi" --sourcefiles "$mv/src" \
    --html "$out/coverage/html" --csv "$out/coverage/coverage.csv" --xml "$out/coverage/coverage.xml"
}

build_mv
build_fs
run_junit
