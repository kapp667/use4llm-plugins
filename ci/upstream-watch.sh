#!/usr/bin/env bash
set -euo pipefail
shopt -s inherit_errexit

usage() {
  cat >&2 <<'USAGE'
Usage: ci/upstream-watch.sh YYYY-MM-DD
Prints, as Markdown, what changed in the official USE repositories and the public projects that
carry the model validator since 00:00 UTC on that day. Prints nothing and exits 1 rather than
print a partial list. Needs git, GNU date, jq and a logged-in gh on PATH.
USAGE
  exit 2
}

[ "$#" -eq 1 ] || usage
since="$1"
case "$since" in -h | --help) usage ;; esac
[ "$(date -u -d "$since" +%F 2>/dev/null)" = "$since" ] || { echo "not a date in the form YYYY-MM-DD: $since" >&2; exit 2; }
export TZ=UTC

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
report="$work/report.md"

# Prints the items of every page of a GitHub search, one JSON object per line, or exits 1 if
# GitHub marks a page incomplete or reports more than the 1000 results it returns.
search_all() {
  local kind="$1" query="$2" pages
  pages="$(gh api -X GET --paginate --slurp "search/$kind" -f q="$query" -f per_page=100)"
  if [ "$(jq '[.[].incomplete_results] | any' <<<"$pages")" != false ]; then
    echo "GitHub returned incomplete results for the query: $query" >&2
    exit 1
  fi
  if [ "$(jq '.[0].total_count' <<<"$pages")" -gt 1000 ]; then
    echo "GitHub reports more than 1000 results for the query: $query" >&2
    exit 1
  fi
  jq -c '.[].items[]' <<<"$pages"
}

# Prints its standard input, or "- none" if it is empty, or "- <text>" with a text given.
or_none() {
  local lines
  lines="$(cat)"
  echo "${lines:-- ${1:-none}}"
}

{
  echo "## Commits to the default branches of the official repositories since $since"
  for repo in useocl/use useocl/use_plugins useocl/plugin_monitor useocl/use_next; do
    git init -q "$work/$repo"
    git -C "$work/$repo" fetch -q --filter=blob:none --depth=500 "https://github.com/$repo" HEAD
    # The fetch stops at the commits .git/shallow names: one dated on or after the day means
    # that commits since the day were left out.
    if [ -f "$work/$repo/.git/shallow" ]; then
      newest_cut="$(git -C "$work/$repo" log --no-walk --date=format-local:%F --format=%cd \
        $(cat "$work/$repo/.git/shallow") | sort | tail -1)"
      if ! [ "$newest_cut" \< "$since" ]; then
        echo "the newest 500 commits of $repo do not reach back before $since" >&2
        exit 1
      fi
    fi
    git -C "$work/$repo" log --date=format-local:%F --format="- $repo %h %cd %s" \
      --since="$since 00:00:00 +0000" FETCH_HEAD | or_none "$repo: no commits"
  done

  echo
  echo "## Issues and pull requests of useocl updated since $since"
  for kind in issue pull-request; do
    search_all issues "org:useocl is:$kind updated:>=$since" | jq -r '"- \(.html_url) \(.state) \(.title)"'
  done | or_none

  echo
  echo "## Repositories of useocl created since $since"
  search_all repositories "org:useocl fork:true created:>=$since" |
    jq -r '"- \(.full_name) \(.description // "")"' | or_none

  echo
  echo "## Plugin jars bundled in useocl/use now (not filtered by date)"
  plugins=use-assembly/src/main/resources/plugins
  git -C "$work/useocl/use" cat-file -e "FETCH_HEAD:$plugins" 2>/dev/null ||
    { echo "useocl/use has no folder $plugins" >&2; exit 1; }
  git -C "$work/useocl/use" ls-tree --name-only FETCH_HEAD "$plugins/" | sed -n 's|.*/\(.*\.jar\)$|- \1|p' | or_none

  echo
  echo "## Public repositories with a file named KodkodModelValidatorConfiguration.java, pushed since $since"
  repos="$(search_all code 'filename:KodkodModelValidatorConfiguration.java' | jq -r '.repository.full_name' |
    sort -u | sed 's/^/repo:/' | tr '\n' ' ')"
  [ -n "$repos" ] || { echo "code search found no file named KodkodModelValidatorConfiguration.java" >&2; exit 1; }
  search_all repositories "$repos fork:true pushed:>=$since" |
    jq -r '"- \(.full_name) pushed \(.pushed_at[0:10]) \(.description // "")"' | or_none

  echo
  echo "## Direct forks of useocl/use and useocl/use_plugins pushed since $since"
  for repo in useocl/use useocl/use_plugins; do
    gh api -X GET --paginate "repos/$repo/forks" -f per_page=100 \
      --jq ".[] | select(.pushed_at >= \"$since\") | \"- \(.full_name) pushed \(.pushed_at[0:10]) (fork of $repo)\""
  done | or_none
} >"$report"

cat "$report"
