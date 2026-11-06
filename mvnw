#!/usr/bin/env sh
set -eu
BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.16
MAVEN_HOME="$BASE_DIR/.mvn/apache-maven-$MAVEN_VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  echo "[RiTech] Descargando Maven $MAVEN_VERSION una sola vez..."
  mkdir -p "$BASE_DIR/.mvn"
  TMP_ZIP="${TMPDIR:-/tmp}/apache-maven-$MAVEN_VERSION-bin.zip"
  URL="https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$URL" -o "$TMP_ZIP"; else wget -q "$URL" -O "$TMP_ZIP"; fi
  unzip -q -o "$TMP_ZIP" -d "$BASE_DIR/.mvn"
  rm -f "$TMP_ZIP"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
