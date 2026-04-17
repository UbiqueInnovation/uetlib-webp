#!/usr/bin/env bash
set -euo pipefail

CORE_DIR="${UETLIB_CORE_DIR:-${HOME}/work/git/uetlib-core}"
OUT_FILE="${1:-${PWD}/target/uetlib-core-classpath.txt}"

if [ ! -d "${CORE_DIR}" ]; then
  echo "uetlib-core directory not found: ${CORE_DIR}" >&2
  echo "Set UETLIB_CORE_DIR=/path/to/uetlib-core" >&2
  exit 1
fi

mkdir -p "$(dirname "${OUT_FILE}")"

mvn -q -f "${CORE_DIR}/pom.xml" dependency:build-classpath \
  -Dmdep.outputFile="${OUT_FILE}"

CORE_JAR="$(find "${CORE_DIR}/target" -maxdepth 1 -type f \
  -name 'uetlib-core-*.jar' \
  ! -name '*sources.jar' \
  ! -name '*javadoc.jar' \
  | sort \
  | tail -n 1)"

if [ -z "${CORE_JAR}" ]; then
  echo "uetlib-core jar not found under ${CORE_DIR}/target. Run mvn -f ${CORE_DIR}/pom.xml package first." >&2
  exit 1
fi

printf '%s:%s\n' "${CORE_JAR}" "$(cat "${OUT_FILE}")"
