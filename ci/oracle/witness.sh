#!/usr/bin/env bash
# Derive the oracle's witness bounds from the default properties file the validator writes and a
# state USE built from a model's witness commands.
# Usage: ci/oracle/witness.sh <default properties file> <replay command file> <replay output>
#          <default run output> > <witness properties file>
#   replay command file: the witness's "!" commands, then "info model", "info state" and "check"
#   replay output:       USE's output for that command file
#   default run output:  USE's output of the default run of the model ("mv -validate" with no
#                        properties file), which says which attributes the validator does not translate
# Each class gets exactly its own objects in that state and each association exactly its links.
# Each attribute may be undefined in any number of objects (Class_attr_min = 0, Class_attr_max = -1),
# except an attribute the validator does not translate: the validator refuses its keys, so none is
# written. The Integer bounds are widened to the integer literals of the commands, and String_max to
# the number of their distinct string literals; the Real bounds are kept. Exits 1 if the properties
# file bounds a class or association the state gives no size for.
set -euo pipefail

[ "$#" -eq 4 ] || { sed -n '2,14p' "$0" >&2; exit 2; }
props="$1"
commands="$2"
output="$3"
run="$4"

# The attributes the validator does not translate, "Class_attribute" per line, from its INFO line
# "Attribute Class::attribute of type T is not translated by the model validator, ...".
untranslated="$(awk '/INFO: Attribute [^ ]+::[^ ]+ of type .* is not translated by the model validator/ {
                       s = $0; sub(/.*INFO: Attribute /, "", s); sub(/ of type .*/, "", s); sub(/::/, "_", s); print s }' "$run")"

# The sizes of the state, "name count" per line: the own objects of each class from the class
# table of "info state", then the links of each association from its association table.
sizes="$(awk '/^class +: #objects/ { k = 1; next }
              /^association +: #links/ { k = 2; next }
              /^-+$/ { next }
              /^total +:/ { k = 0; next }
              k && $2 == ":" { print $1, $3 }' "$output")"

# The attributes of each class, "Class_attribute" per line, from the model "info model" prints:
# indented "name : type" lines between a class header and its next section.
attributes="$(awk '/^(abstract )?(class|associationclass) / { c = ($1 == "abstract") ? $3 : $2; a = 0; next }
                   /^attributes$/ { a = 1; next }
                   /^[^ ]/ { a = 0 }
                   a && /^ +[A-Za-z_][A-Za-z0-9_]* *:/ { n = $1; sub(/:.*/, "", n); print c "_" n }' "$output")"

# A number not inside a name or a Real, with the character before it; ".." splits a range. A
# number inside a string literal may be taken too, which only widens the bounds.
integers="$(sed 's/\.\./ /g' "$commands" | grep -oE "(^|[^A-Za-z0-9_.'])-?[0-9]+(\.[0-9]+)?" | grep -v '\.' | grep -oE -- '-?[0-9]+$' | sort -n || true)"
lowest="$(printf '%s\n' "$integers" | sed -n 1p)"
highest="$(printf '%s\n' "$integers" | sed -n '$p')"
strings="$({ grep -oE "'[^']*'" "$commands" || true; } | sort -u | wc -l)"

awk -v sizes="$sizes" -v attributes="$attributes" -v untranslated="$untranslated" -v lowest="${lowest:-0}" -v highest="${highest:-0}" -v strings="$strings" '
  BEGIN {
    n = split(sizes, lines, "\n")
    for (i = 1; i <= n; i++) if (split(lines[i], f, " ") == 2) size[f[1]] = f[2]
    m = split(untranslated, skip_names, "\n")
    for (i = 1; i <= m; i++) skip[skip_names[i]] = 1
  }
  { sub(/\r$/, "") }
  /^Integer_min *=/ { v = $NF + 0; if (lowest < v) v = lowest; print "Integer_min = " v; next }
  /^Integer_max *=/ { v = $NF + 0; if (highest > v) v = highest; print "Integer_max = " v; next }
  /^String_max *=/ { v = $NF + 0; if (strings > v) v = strings; print "String_max = " v; next }
  /^Real_(min|max) *=/ { print; next }
  /^[^#].*_(min|max) *=/ {
    key = $0; sub(/_(min|max) *=.*/, "", key)
    if (!(key in size)) { print "witness.sh: " FILENAME ":" FNR ": no size in the state for " key > "/dev/stderr"; failed = 1; exit 1 }
    sub(/=.*/, "= " size[key]); print; next
  }
  { print }
  END {
    if (failed) exit 1
    n = split(attributes, names, "\n")
    for (i = 1; i <= n; i++) if (names[i] != "" && !(names[i] in skip)) { print names[i] "_min = 0"; print names[i] "_max = -1" }
  }' "$props"
