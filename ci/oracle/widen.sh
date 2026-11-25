#!/usr/bin/env bash
# Derive the oracle's wide bounds from the default properties file the validator writes.
# Usage: ci/oracle/widen.sh <default properties file> > <wide properties file>
# Each class gets 0 to 3 objects and each association 0 to any number of links; the bounds of
# Integer, String and Real are kept.
set -euo pipefail

[ "$#" -eq 1 ] || { sed -n '2,5p' "$0" >&2; exit 2; }

# The validator heads the bounds of a class with "# ---...--- <class>" and those of an
# association, an association class included, with "# <association> (<role>:<class>, ...)".
awk '{ sub(/\r$/, "") }
     /^# -+ / { kind = "class" }
     /^# [^ ]+ \(/ { kind = "association" }
     /^(Integer|String|Real)_(min|max) *=/ { print; next }
     /^[^#].*_min *=/ { sub(/=.*/, "= 0"); print; next }
     /^[^#].*_max *=/ {
       if (kind == "class") { sub(/=.*/, "= 3") }
       else if (kind == "association") { sub(/=.*/, "= -1") }
       else { print "widen.sh: " FILENAME ":" NR ": a bound with no class or association header" > "/dev/stderr"; exit 1 }
       print; next }
     { print }' "$1"
