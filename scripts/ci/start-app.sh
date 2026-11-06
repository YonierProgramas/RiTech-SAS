#!/usr/bin/env bash
set -Eeuo pipefail
mkdir -p target/ci
jar=target/ritech-sas-0.1.0-SNAPSHOT.jar
if [[ ! -f "$jar" ]]; then echo "::error::Missing packaged Spring Boot application: $jar"; exit 1; fi
nohup java -jar "$jar" > target/ci/spring-boot.log 2>&1 &
app_pid=$!
printf '%s\n' "$app_pid" > target/ci/spring-boot.pid

failure_log() {
  echo '::error::RiTech did not become ready on http://localhost:8080/login.html.'
  node scripts/ci/sanitize-artifacts.cjs log target/ci/spring-boot.log
}
deadline=$((SECONDS + 120))
while ((SECONDS < deadline)); do
  if ! kill -0 "$app_pid" 2>/dev/null; then failure_log; exit 1; fi
  status=$(curl --silent --show-error --connect-timeout 2 --max-time 3 \
    --output target/ci/login-health.html --write-out '%{http_code}' http://localhost:8080/login.html || true)
  if [[ "$status" == 200 ]] && grep -q 'RiTech' target/ci/login-health.html \
    && grep -q 'action="/login"' target/ci/login-health.html; then
    echo 'RiTech is ready: HTTP 200 and the real public login form.'
    exit 0
  fi
  sleep 1
done
failure_log
exit 1
