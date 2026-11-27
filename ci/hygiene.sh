#!/usr/bin/env bash
# Check three mistakes that keep coming back, and print one line per violation:
#   (a) a commit message in the range names a private session (a session trailer or link);
#   (b) a journey's judge.args does not parse as shell quoting (xargs -a, as ci/gate.sh reads it);
#   (c) a file changed in the range has mixed line endings, or switched CRLF <-> LF from the base.
# Usage: ci/hygiene.sh [<range>] [--journeys <dir>]   (range defaults to origin/develop..HEAD)
# Exits 0 only when nothing was found.
set -uo pipefail

range=origin/develop..HEAD; journeys=
while [ "$#" -gt 0 ]; do
	case "$1" in
		--journeys) journeys="${2:?--journeys needs a folder}"; shift 2 ;;
		-h|--help) sed -n '2,7p' "$0"; exit 0 ;;
		*) range="$1"; shift ;;
	esac
done
cd "$(dirname "${BASH_SOURCE[0]}")/.."
bad=0
say() { echo "HYGIENE: $*"; bad=1; }

case "$range" in *..*) ;; *) echo "range must be <base>..<head>, got $range" >&2; exit 2 ;; esac
from="${range%%..*}"; to="${range##*..}"; to="${to:-HEAD}"
base="$(git merge-base "$from" "$to")" || { echo "no merge base for $range" >&2; exit 2; }

# (a) session trailers or links in commit messages
for c in $(git rev-list "$range"); do
	if git show -s --format=%B "$c" | grep -qiE 'claude-session|claude\.ai/code/session_'; then
		say "commit $(git rev-parse --short "$c") message holds a session trailer or link"
	fi
done

# (b) judge.args files parse with xargs and have no unpaired quote on any line
if [ -n "$journeys" ]; then
	while IFS= read -r -d '' f; do
		if ! xargs -a "$f" printf '%s\n' >/dev/null 2>&1; then
			say "$f does not parse with xargs -a (check its quotes and backslashes)"
		elif ! awk '{ q=""; for (i=1;i<=length($0);i++) { ch=substr($0,i,1)
				if (q=="") { if (ch=="\\") i++; else if (ch=="\x27"||ch=="\"") q=ch }
				else if (ch==q) q=""; else if (q=="\"" && ch=="\\") i++ }
				if (q!="") exit 1 }' "$f"; then
			say "$f has an unpaired quote"
		fi
	done < <(find "$journeys" -name judge.args -type f -print0)
fi

# (c) line endings of text files changed in the range
endings() { # prints crlf, lf, mixed or none for the blob on stdin
	LC_ALL=C awk '{ if (sub(/\r$/,"")) c++; else l++ } END { if (c&&l) print "mixed"; else if (c) print "crlf"; else if (l) print "lf"; else print "none" }'
}
while IFS=$'\t' read -r added _ path; do
	[ "$added" = "-" ] && continue   # binary
	now="$(git show "$to:$path" | endings)"
	[ "$now" = mixed ] && { say "$path has mixed line endings"; continue; }
	if git cat-file -e "$base:$path" 2>/dev/null; then
		was="$(git show "$base:$path" | endings)"
		if [ "$was" != "$now" ] && [ "$was" != none ] && [ "$now" != none ] && [ "$was" != mixed ]; then
			say "$path switched line endings from $was to $now"
		fi
	fi
done < <(git diff --numstat --diff-filter=AM "$base" "$to")

[ "$bad" -eq 0 ] && echo "HYGIENE OK"
exit "$bad"
