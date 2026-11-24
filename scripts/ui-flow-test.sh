#!/bin/bash
# Headless UI integration test: logs in as each role and drives the real
# screens against the database (requires schema.sql + seed.sql loaded and
# UNIEVENTS_DB_URL / UNIEVENTS_DB_USER / UNIEVENTS_DB_PASSWORD configured).
set -u
cd "$(dirname "$0")/.."

JDK="${JAVA_HOME:?JAVA_HOME must point at a JDK 17+}"
M2="${M2_REPO:-$HOME/.m2/repository}"

CP="target/classes:src/main/resources"
for artifact in javafx-base javafx-graphics javafx-controls; do
  VER="$(ls "$M2/org/openjfx/$artifact" 2>/dev/null | sort -V | tail -1)"
  for jar in "$M2/org/openjfx/$artifact/$VER"/*.jar; do
    case "$jar" in *-win.jar|*-mac.jar|*-mac-aarch64.jar) ;; *) CP="$CP:$jar";; esac
  done
done
PG="$(ls "$M2/org/postgresql/postgresql"/*/postgresql-*.jar 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$PG" ] && CP="$CP:$PG"
MONOCLE="$(ls "$M2/org/testfx/openjfx-monocle"/*/openjfx-monocle-*.jar 2>/dev/null | sort -V | tail -1 || true)"
[ -n "$MONOCLE" ] && CP="$CP:$MONOCLE"

timeout 120 "$JDK/bin/java" \
  -Dglass.platform=Monocle -Dmonocle.platform=Headless \
  -Dprism.order=sw \
  -cp "$CP" unievents.ui.UiFlowLauncher 2>&1
echo "exit=$?"
