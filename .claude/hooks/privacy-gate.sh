#!/usr/bin/env bash
# PreToolUse hook. Before a git push or a GitHub post, a model reads what is about to be published
# against .claude/skills/privacy-guard/SKILL.md and blocks the call on a leak (exit 2, reason on stderr).
# It fails closed: when the check cannot run, the call is blocked with the cause.
set -uo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mission="$here/../skills/privacy-guard/SKILL.md"
max_bytes=400000

block() { echo "privacy-guard: $*" >&2; exit 2; }

input="$(cat)"
tool="$(jq -r '.tool_name // empty' <<<"$input")"
[ -n "$tool" ] || block "cannot read the hook input"

if [ "$tool" = Bash ]; then
	cwd="$(jq -r '.cwd // empty' <<<"$input")"
	cd "${cwd:-.}" 2>/dev/null || block "cannot enter $cwd"
	base="$(git rev-parse -q --verify '@{upstream}' 2>/dev/null)" ||
		base="$(git merge-base HEAD origin/develop 2>/dev/null)" ||
		base="$(git merge-base HEAD origin/HEAD 2>/dev/null)" ||
		block "cannot find what this push adds: no upstream and no origin/develop"
	[ -n "$(git rev-list "$base..HEAD")" ] || exit 0
	content="$(
		echo "## Commit messages"
		git log --format='--- commit %h%n%B' "$base..HEAD"
		echo "## Lines added by the diff"
		git diff --unified=0 --no-color "$base..HEAD" | grep -E '^(\+\+\+ |\+)' | grep -v '^+++ /dev/null'
	)"
	what="a git push of the commits $(git rev-parse --short "$base")..$(git rev-parse --short HEAD)"
else
	content="$(jq '.tool_input' <<<"$input")"
	what="the GitHub call $tool"
fi

[ "${#content}" -le "$max_bytes" ] || block "$what is larger than $max_bytes bytes; publish it in smaller parts"

prompt="$(
	cat "$mission"
	printf '\n\n# The publication to check: %s\n\n<publication>\n%s\n</publication>\n' "$what" "$content"
)"
answer="$(printf '%s' "$prompt" | timeout 280 claude -p --model sonnet --effort medium --tools "" \
	--setting-sources project --no-session-persistence 2>&1)" || block "the check did not run: $answer"

verdict="$(printf '%s\n' "$answer" | sed -e 's/[[:space:]]*$//' | grep -v '^$' | tail -n 1)"
case "$verdict" in
	PASS) exit 0 ;;
	BLOCK:*) block "${verdict#BLOCK: }" ;;
	*) block "the check gave no verdict line: $answer" ;;
esac
