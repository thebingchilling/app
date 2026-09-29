#!/usr/bin/env bash
# Runs the bridge unit tests and the end-to-end TCP/UDP test against an independent rc4-md5 server.
# Needs: JDK 11+, Maven, Python 3, network access to Maven Central (for org.json, JVM tests only).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/pom.xml" <<'EOF'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>t</groupId><artifactId>t</artifactId><version>1</version>
<dependencies><dependency><groupId>org.json</groupId><artifactId>json</artifactId><version>20240303</version></dependency></dependencies>
</project>
EOF
mvn -B -q -f "$WORK/pom.xml" dependency:copy-dependencies -DoutputDirectory="$WORK/lib"
JSON_JAR="$WORK/lib/json-20240303.jar"

mkdir -p "$WORK/classes"
javac --release 8 -Xlint:-options -cp "$JSON_JAR" -d "$WORK/classes" \
  "$HERE"/src/com/longoipo/rc4/*.java "$HERE"/test/com/longoipo/rc4/*.java

java -cp "$WORK/classes:$JSON_JAR" com.longoipo.rc4.BridgeUnitTest
python3 "$HERE/test/e2e_test.py" "$WORK/classes" "$JSON_JAR"
