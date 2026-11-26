#!/usr/bin/env bash
# Build the throwaway USE-to-TLA+ plugin jar against a USE 7.5.0 install.
# Usage: build.sh [<USE install dir>]   Output: build/use2tla.jar
# The USE install dir defaults to $USE_HOME.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
use="${1:-${USE_HOME:?set USE_HOME to an unpacked USE 7.5.0 release}}"
cp="$use/lib/use-core-7.5.0.jar:$use/lib/use-gui.jar"
b="$here/build"
rm -rf "$b/classes"; mkdir -p "$b/classes"
javac -nowarn -encoding UTF-8 --release 21 -proc:none -cp "$cp" -d "$b/classes" $(find "$here/plugin/src" -name '*.java')
cp "$here/plugin/resources/Prelude.tla" "$here/plugin/useplugin.xml" "$b/classes/"
jar cfm "$b/use2tla.jar" "$here/plugin/META-INF/MANIFEST.MF" -C "$b/classes" .
echo "built $b/use2tla.jar"
