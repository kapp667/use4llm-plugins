#!/usr/bin/env bash
# Build the plugin jars from this checkout and make a private USE copy that loads them.
# Usage: setup.sh <USE install dir> <work dir>
#   USE install dir: the unpacked USE 7.5.0 release (see fetch-use.sh); lib/use-gui.jar is searched under it
#   work dir:        created if missing; gets jars/, use/ and a build.log. Reuse it across drives;
#                    rerunning rebuilds the jars from the current sources and refreshes use/.
# The private copy mirrors ci/oracle/run.sh: USE refuses a second plugin of the same name, so the
# bundled ModelValidator and Filmstrip jars are removed before the jars under test are copied in.
set -euo pipefail

[ "$#" -eq 2 ] || { sed -n '2,8p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
usedir="$(cd "$1" && pwd)"
mkdir -p "$2"
work="$(cd "$2" && pwd)"

usegui="$(find "$usedir" -path '*/lib/use-gui.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
[ -n "$usegui" ] || { echo "use-gui.jar not found under $usedir" >&2; exit 1; }
usehome="$(dirname "$(dirname "$usegui")")"

echo "building the plugin jars into $work/jars (log: $work/build.log)"
if ! "$root/ci/build-and-test.sh" --build-only "$usedir" "$work/jars" >"$work/build.log" 2>&1; then
  tail -n 30 "$work/build.log" >&2
  echo "setup: the build failed; fix the build before verifying anything" >&2
  exit 1
fi

rm -rf "$work/use"
mkdir -p "$work/use"
cp -r "$usehome/lib" "$work/use/"
for d in etc oclextensions; do
  if [ -d "$usehome/$d" ]; then cp -r "$usehome/$d" "$work/use/"; fi
done
rm -f "$work/use/lib/plugins"/ModelValidatorPlugin-*.jar "$work/use/lib/plugins/use-filmstrip.jar"
cp "$work/jars/ModelValidatorPlugin.jar" "$work/jars/use-filmstrip.jar" "$work/use/lib/plugins/"
echo "ready: $work/use/lib/use-gui.jar runs with the jars built from $(git -C "$root" rev-parse --short HEAD 2>/dev/null || echo 'this checkout')"
echo "next: $here/doctor.sh $work"
