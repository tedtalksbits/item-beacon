#!/usr/bin/env bash
# Loads the mod's classes and applies every client mixin (the mod's and Fabric API's) without starting the
# game (no window, no natives needed), in a Fabric preLaunch entrypoint, then runs Mixin's audit and
# exits 0 (AUDIT RESULT OK) or 1. Catches linkage and mixin mistakes that otherwise only show up as a broken game
# (Shulker Styles client 0.2.0: black screen) and overrides that no longer override anything. Linux/Git Bash; needs the client built.
#   ci/mixin-audit/run.sh [extra gradle args, e.g. --offline -I init.gradle]   (GRADLE=<command> replaces ./gradlew)
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; client="$here/../../fabric-client"
work="$(mktemp -d)"; trap 'rm -rf "$work"' EXIT
(cd "$client" && ${GRADLE:-./gradlew} -q -Dcpfile="$work/cp" -I "$here/print-classpath.init.gradle" "$@" classes printRuntimeCp)
cp="$(cat "$work/cp")"
mkdir -p "$work/out" "$work/game/mods"
cp "$here/fabric.mod.json" "$work/out/"
"${JAVA:-java}" -version >/dev/null 2>&1
javac -cp "$cp" -d "$work/out" "$here/MixinAudit.java"
cd "$work" && "${JAVA:-java}" -Dfabric.development=true -cp "$work/out:$cp" net.fabricmc.loader.impl.launch.knot.KnotClient --gameDir "$work/game" 2>&1 | grep -E 'AUDIT|ERROR'
