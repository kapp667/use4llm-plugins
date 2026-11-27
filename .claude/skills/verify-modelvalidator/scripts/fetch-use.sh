#!/usr/bin/env bash
# Download the USE 7.5.0 binary release the repo's CI uses, check its SHA-256 and unpack it.
# Usage: fetch-use.sh <new or empty dir>
# Prints the USE install dir (the one that holds lib/use-gui.jar) on its last line.
# Needs network access to github.com (release assets are redirected to a CDN host).
set -euo pipefail

[ "$#" -eq 1 ] || { sed -n '2,5p' "$0" >&2; exit 2; }
version=7.5.0
# Keep in step with USE_ZIP_SHA256 in .github/workflows/ci.yml.
sha=9b5a49d1d2345e65d1da8518008887e8bc12df388b6eef08228c5a1052618d10
mkdir -p "$1"
dest="$(cd "$1" && pwd)"
[ -z "$(ls -A "$dest")" ] || { echo "$dest is not empty" >&2; exit 1; }

zip="$dest/use-$version-use-bin.zip"
# curl first: it needs only the public URL. gh uses GraphQL, which some sandboxes block.
if ! curl -fsSL --retry 5 --retry-delay 10 --retry-all-errors -o "$zip" \
    "https://github.com/useocl/use/releases/download/v$version/use-$version-use-bin.zip"; then
  rm -f "$zip"
  gh release download "v$version" --repo useocl/use --pattern "use-$version-use-bin.zip" --dir "$dest"
fi
echo "$sha  $zip" | sha256sum --check
unzip -q "$zip" -d "$dest/unpacked"
jar="$(find "$dest/unpacked" -path '*/lib/use-gui.jar' -not -path '*/plugins/*' | sort | sed -n 1p)"
[ -n "$jar" ] || { echo "use-gui.jar not found in the release" >&2; exit 1; }
dirname "$(dirname "$jar")"
