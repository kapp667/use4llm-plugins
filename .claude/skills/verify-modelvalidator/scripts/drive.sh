#!/usr/bin/env bash
# Run one non-interactive USE session with the plugin: load a model, run a command file, keep the transcript.
# Usage: drive.sh <work dir> <model.use> <command file> <evidence dir>
#   work dir:      the one setup.sh made
#   model.use:     the model; its whole folder is copied into <evidence dir>/run, because the validator
#                  writes <model>.properties next to the model and a model may import its neighbours
#   command file:  USE shell commands, one per line, ending with exit; copied into <evidence dir>/run, so a
#                  properties file named in it resolves against that folder
#   evidence dir:  must be new or empty; gets transcript.txt (stdout and stderr of USE), status
#                  (USE's exit status, 124 or 137 for a timeout), commands.cmd and run/
# Judge the result with judge.sh. drive.sh itself only fails when it could not run USE at all.
# DRIVE_TIMEOUT sets the seconds allowed for the run (default 120).
set -euo pipefail

[ "$#" -eq 4 ] || { sed -n '2,12p' "$0" >&2; exit 2; }
work="$(cd "$1" && pwd)"
model="$(cd "$(dirname "$2")" && pwd)/$(basename "$2")"
cmdfile="$(cd "$(dirname "$3")" && pwd)/$(basename "$3")"
mkdir -p "$4"
ev="$(cd "$4" && pwd)"
limit="${DRIVE_TIMEOUT:-120}"

[ -f "$work/use/lib/use-gui.jar" ] || { echo "no private USE under $work; run setup.sh first" >&2; exit 1; }
[ -f "$model" ] && [ -f "$cmdfile" ] || { echo "model or command file missing" >&2; exit 1; }
[ -z "$(ls -A "$ev")" ] || { echo "$ev is not empty; evidence is never overwritten" >&2; exit 1; }

mkdir -p "$ev/run"
cp -r "$(dirname "$model")/." "$ev/run/"
cp "$cmdfile" "$ev/commands.cmd"
cp "$cmdfile" "$ev/run/$(basename "$cmdfile")"

status=0
# The same invocation as ci/oracle/run.sh: no GUI, no readline, the command file as the script.
(cd "$ev/run" && timeout --kill-after=10 "$limit" java -Djava.awt.headless=true -jar "$work/use/lib/use-gui.jar" \
  -nogui -nr "$(basename "$model")" "$(basename "$cmdfile")" </dev/null >"$ev/transcript.txt" 2>&1) || status=$?
echo "$status" >"$ev/status"
echo "transcript: $ev/transcript.txt (exit status $status)"
