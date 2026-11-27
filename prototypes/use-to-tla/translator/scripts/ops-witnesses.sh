#!/usr/bin/env bash
# States TLC reaches as witnesses for the ModelValidator (scripts/witness.py): TLC explores each
# spec through the states where every check holds and dumps them; for up to WITNESSES (5) states
# per spec, one per distinct set of witness bounds, USE builds the state and checks it, and when
# USE accepts it, the validator must find a solution with the bounds ci/oracle/witness.sh derives
# from it (exactly its objects and links), as the oracle's witness rows require: UNSAT, or a
# solution USE's check rejects, disagrees; a validator that cannot load the model is an error.
# Usage: scripts/ops-witnesses.sh <out dir> <results dir> <specs file>
#   specs file: one line per spec, "name<TAB>spec dir<TAB>model file" (tlaops output and its model)
# Output: <results dir>/ops-witnesses.tsv, one row per witness. Needs USE_HOME, MV_JAR, TLA2TOOLS.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
oracle="$here/../../../ci/oracle"
out="${1:?out dir}"; res="${2:?results dir}"; specs="${3:?specs file}"
use="${USE_HOME:?set USE_HOME}"; : "${MV_JAR:?set MV_JAR}" "${TLA2TOOLS:?set TLA2TOOLS}"
export TLA2TOOLS here out oracle
mkdir -p "$out" "$res"
usecopy="$out/use"; rm -rf "$usecopy"; mkdir -p "$usecopy"
cp -r "$use/lib" "$use/etc" "$use/oclextensions" "$usecopy/"
rm -f "$usecopy/lib/plugins"/ModelValidatorPlugin-*.jar
cp "$MV_JAR" "$usecopy/lib/plugins/"
export usecopy

one() { # <name> <spec dir> <model file>
  local name="$1" spec="$2" model="$3" w m n s status row
  w="$out/cases/$(printf '%s' "$name" | tr -c 'A-Za-z0-9._\n' '_')"
  rm -rf "$w"; mkdir -p "$w/model"
  cp -r "$(dirname "$model")/." "$w/model/"; m="$(basename "$model")"
  cp -r "$spec" "$w/spec"; rm -rf "$w/spec"/states*
  python3 -I "$here/scripts/witness.py" reach "$w/spec"
  (cd "$w/spec" && timeout --kill-after=10 "${TLC_TIMEOUT:-120}" java -XX:+UseParallelGC -Xss512m -cp "$TLA2TOOLS" tlc2.TLC -workers 1 \
    -metadir "$w/spec/states" -dump "$w/reach.dump" -config Reach.cfg Reach </dev/null >"$w/tlc-reach.txt" 2>&1) || true
  [ -f "$w/reach.dump" ] || { printf '%s\t-\t0\t0\t\t\t\t\tTLC failed\t%s\n' "$name" "$(grep -m1 -E 'Error|Exception' "$w/tlc-reach.txt" | tr '\t' ' ')" >"$w/rows.tsv"; return 0; }
  IFS=$'\t' read -r states keys n < <(python3 -I "$here/scripts/witness.py" pick "$w/spec" "$w/reach.dump" "${WITNESSES:-5}" "$w")
  usecmd() { status=0; (cd "$w/model" && timeout --kill-after=10 "${MV_TIMEOUT:-120}" java -Djava.awt.headless=true -jar "$usecopy/lib/use-gui.jar" \
    -nogui -nr "$m" "$1" </dev/null >"$2" 2>&1) || status=$?; }
  : >"$w/rows.tsv"
  local defaults="$w/model/${m%.use}.properties"
  for c in "$w"/w*.cmd; do
    [ -f "$c" ] || continue
    local i; i="$(basename "$c" .cmd)"
    usecmd "$c" "$w/$i.state.txt"
    IFS=$'\t' read -r check objects failure < <("$oracle/state.sh" "$w/$i.state.txt")
    if [ "$check" != ok ] || [ "$objects" = 0 ]; then
      printf '%s\t%s\t%s\t%s\t%s\t%s\tn/a\t0\tSKIPPED\t%s\n' "$name" "$i" "$states" "$keys" "$objects" "$check" \
        "$([ "$check" != ok ] && echo "USE's check rejects the state: $failure" || echo "no object")" >>"$w/rows.tsv"
      continue
    fi
    if [ ! -f "$defaults" ]; then
      printf 'mv -validate\nexit\n' >"$w/defaults.cmd"; usecmd "$w/defaults.cmd" "$w/defaults.txt"
    fi
    [ -f "$defaults" ] || { printf '%s\t%s\t%s\t%s\t%s\tok\tn/a\t0\tSKIPPED\tno default properties file\n' "$name" "$i" "$states" "$keys" "$objects" >>"$w/rows.tsv"; continue; }
    "$oracle/witness.sh" "$defaults" "$c" "$w/$i.state.txt" >"$w/model/$i.properties"
    # USE resolves the properties file against the folder of the command file.
    printf 'mv -validate %s.properties\ninfo state\ncheck\nexit\n' "$i" >"$w/model/$i.mv.cmd"
    s="$EPOCHREALTIME"; usecmd "$w/model/$i.mv.cmd" "$w/$i.mv.txt"
    row="$("$oracle/parse.sh" "$w/$i.mv.txt" "$status")"
    IFS=$'\t' read -r verdict vcheck vobjects errors note <<<"$row"
    note="${note#[0-9][0-9]:[0-9][0-9]:[0-9][0-9] }"
    printf '%s\t%s\t%s\t%s\t%s\tok\t%s\t%s\t%s\t%s\n' "$name" "$i" "$states" "$keys" "$objects" "$verdict" \
      "$(awk -v a="$s" -v b="$EPOCHREALTIME" 'BEGIN { printf "%.1f", b - a }')" \
      "$(case "$verdict/$vcheck" in SAT/ok) echo agree ;; ERROR/*) echo "validator error" ;; *) echo disagree ;; esac)" "$note" >>"$w/rows.tsv"
  done
  echo "$name: $states states, $keys bounds keys, $(cut -f9 "$w/rows.tsv" | sort | uniq -c | tr '\n' ' ')"
}
export -f one
tr '\t' '\n' <"$specs" | xargs -d '\n' -n 3 -P "${JOBS:-4}" bash -c 'one "$0" "$1" "$2"'
{ printf 'spec\twitness\tstates\tbounds_keys\tobjects\tuse_check\tmv_verdict\tmv_seconds\tresult\tnote\n'
  for f in "$out"/cases/*/rows.tsv; do cat "$f"; done | LC_ALL=C sort; } >"$res/ops-witnesses.tsv"
awk -F'\t' 'NR > 1 { n[$9]++ } END { for (k in n) printf "%s: %d\n", k, n[k] }' "$res/ops-witnesses.tsv" | LC_ALL=C sort
