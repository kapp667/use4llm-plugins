#!/usr/bin/env bash
# Read-only check that a work dir is worth driving: right Java, one copy of each plugin jar, the jars are
# the ones built from this checkout and are not older than its sources, and USE lists every mv command.
# Usage: doctor.sh <work dir>
# Prints one line per check and exits 1 if any fails. Run it first, and again whenever a drive behaves
# strangely or the sources changed. It writes only to a temporary folder it removes.
set -euo pipefail

[ "$#" -eq 1 ] || { sed -n '2,6p' "$0" >&2; exit 2; }
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/../../../.." && pwd)"
work="$(cd "$1" && pwd)"
bad=0
ok() { echo "ok:   $1"; }
no() { echo "FAIL: $1"; bad=1; }

major="$(java -version 2>&1 | sed -n 's/.* version "\([0-9]*\).*/\1/p' | sed -n 1p)"
if [ -n "$major" ] && [ "$major" -ge 21 ]; then ok "java $major (the build needs 21 or later)"; else no "java 21 or later is not on PATH (found: ${major:-none})"; fi

if [ -f "$work/use/lib/use-gui.jar" ]; then ok "private USE at $work/use"; else no "no private USE under $work; run setup.sh"; fi

plugins="$work/use/lib/plugins"
mvjars=("$plugins"/ModelValidatorPlugin*.jar)
fsjars=("$plugins"/use-filmstrip*.jar)
if [ "${#mvjars[@]}" -eq 1 ] && [ -f "${mvjars[0]}" ] && cmp -s "${mvjars[0]}" "$work/jars/ModelValidatorPlugin.jar"; then
  ok "exactly one ModelValidator jar, identical to jars/ModelValidatorPlugin.jar"
else
  no "the plugins folder does not hold exactly the built ModelValidatorPlugin.jar (a bundled one USE 7.5.0 ships would shadow it)"
fi
if [ "${#fsjars[@]}" -eq 1 ] && [ -f "${fsjars[0]}" ] && cmp -s "${fsjars[0]}" "$work/jars/use-filmstrip.jar"; then
  ok "exactly one Filmstrip jar, identical to jars/use-filmstrip.jar"
else
  no "the plugins folder does not hold exactly the built use-filmstrip.jar"
fi

# A jar older than a source file means the build under test is not this checkout.
stale="$(find "$root/ModelValidator/trunk/src" "$root/ModelValidator/trunk/resources" "$root/ModelValidator/trunk/useplugin.xml" \
  -type f -newer "$work/jars/ModelValidatorPlugin.jar" 2>/dev/null | sed -n 1p)"
if [ -z "$stale" ]; then ok "the jar is newer than every source of ModelValidator/trunk"; else no "stale jar: ${stale#"$root"/} is newer; rerun setup.sh"; fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
printf 'model Doctor\nclass C\nend\n' >"$tmp/Doctor.use"
printf 'plugins\nexit\n' >"$tmp/doctor.cmd"
if (cd "$tmp" && timeout --kill-after=10 120 java -Djava.awt.headless=true -jar "$work/use/lib/use-gui.jar" \
    -nogui -nr Doctor.use doctor.cmd </dev/null >"$tmp/out.txt" 2>&1); then
  for alias in '-validate' '?' '-vc' '-config' '-invIndep' '-scrolling'; do
    n="$(grep -cxF "  Alias: mv $alias" "$tmp/out.txt" || true)"
    if [ "$n" -eq 1 ]; then ok "USE lists mv $alias once"; else no "USE lists mv $alias $n times (expected once)"; fi
  done
else
  no "USE did not run the plugins command: $(tail -n 3 "$tmp/out.txt" | tr '\n' ' ')"
fi

if [ "$bad" -eq 0 ]; then echo "doctor: worth driving"; else echo "doctor: NOT worth driving"; fi
[ "$bad" -eq 0 ]
