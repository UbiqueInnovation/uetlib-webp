#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLASSPATH_FILE="${ROOT_DIR}/target/jmh-classpath.txt"

mvn -q -f "${ROOT_DIR}/pom.xml" test-compile dependency:build-classpath \
  -Dmdep.includeScope=test \
  -Dmdep.outputFile="${CLASSPATH_FILE}"

CP="${ROOT_DIR}/target/classes:${ROOT_DIR}/target/test-classes:$(cat "${CLASSPATH_FILE}")"

if [ "$#" -eq 0 ]; then
  set -- -rf json -rff "${ROOT_DIR}/target/jmh-result.json"
fi

exec java \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path="${ROOT_DIR}/native/build" \
  ${JMH_JAVA_OPTS:-} \
  -cp "${CP}" \
  org.openjdk.jmh.Main \
  "$@"
