#!/usr/bin/env bash
# Report, as Markdown, what changed upstream since a date: the commits on the default branches of
# useocl/use, useocl/use_plugins, useocl/plugin_monitor and useocl/use_next, and the issues, pull
# requests and new repositories of the useocl organization, since that date; the repositories that
# GitHub code search finds with the model validator's source and the forks of useocl/use, each
# pushed since that date; and the plugin jars useocl/use bundles at its current head. Uses git,
# GNU date and an authenticated gh from PATH.
# Usage: ci/upstream-watch.sh <since>
#   since: a date, YYYY-MM-DD
set -euo pipefail

[ "$#" -eq 1 ] || { sed -n '2,9p' "$0" >&2; exit 2; }
since="$1"
[ "$(date -d "$since" +%F 2>/dev/null)" = "$since" ] || { echo "not a date: $since" >&2; exit 2; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "## Official commits since $since"
for repo in useocl/use useocl/use_plugins useocl/plugin_monitor useocl/use_next; do
  git init -q "$work/$repo"
  git -C "$work/$repo" fetch -q --filter=blob:none --depth=500 "https://github.com/$repo" HEAD
  commits="$(git -C "$work/$repo" log --format="- $repo %h %cs %s" --since="$since 00:00:00" FETCH_HEAD)"
  oldest="$(git -C "$work/$repo" log --format=%cs FETCH_HEAD | tail -1)"
  if [ -f "$work/$repo/.git/shallow" ] && ! [ "$oldest" \< "$since" ]; then
    echo "more than 500 commits of $repo since $since: give a later date" >&2
    exit 1
  fi
  echo "${commits:-- $repo: no commit since $since}"
done

echo
echo "## Official issues and pull requests updated since $since"
for kind in issue pull-request; do
  gh api -X GET --paginate search/issues -f q="org:useocl is:$kind updated:>=$since" -f per_page=100 \
    --jq '.items[] | "- \(.html_url) \(.state) \(.title)"'
done

echo
echo "## New official repositories since $since"
gh api -X GET --paginate search/repositories -f q="org:useocl created:>=$since" -f per_page=100 \
  --jq '.items[] | "- \(.full_name) \(.description // "")"'

echo
echo "## Bundled plugin jars in official USE"
jars="$(git -C "$work/useocl/use" ls-tree --name-only FETCH_HEAD use-assembly/src/main/resources/plugins/ |
  sed -n 's|.*/\(.*\.jar\)$|- \1|p')"
echo "${jars:-- no plugin jar in use-assembly/src/main/resources/plugins}"

echo
echo "## Public repositories with validator source pushed since $since"
repos="$(gh api -X GET --paginate search/code -f q='filename:KodkodModelValidatorConfiguration.java' \
  -f per_page=100 --jq '.items[].repository.full_name' | sort -u | sed 's/^/repo:/' | tr '\n' ' ')"
[ -n "$repos" ] || { echo "code search found no repository with validator source" >&2; exit 1; }
gh api -X GET --paginate search/repositories -f q="$repos pushed:>=$since" -f per_page=100 \
  --jq '.items[] | "- \(.full_name) pushed \(.pushed_at[0:10]) \(.description // "")"'

echo
echo "## Forks of official USE pushed since $since"
gh api -X GET --paginate repos/useocl/use/forks -f per_page=100 \
  --jq ".[] | select(.pushed_at >= \"$since\") | \"- \(.full_name) pushed \(.pushed_at[0:10])\""
