#!/usr/bin/env bash
set -Eeuo pipefail
container_id=${1:?Pass the GitHub MySQL service container ID}

mysql_ci() {
  docker exec -i "$container_id" sh -c \
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=TCP -h127.0.0.1 -uroot --default-character-set=utf8mb4 --batch "$@"' sh "$@"
}

# Follow the Docker ping with an authenticated query, not just an open TCP port.
ready=false
for ((attempt=1; attempt<=30; attempt++)); do
  if mysql_ci --skip-column-names -e 'SELECT 1' >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]]; then
  echo '::error::MySQL did not accept an authenticated SELECT 1 within 30 attempts.'
  exit 1
fi

existing=$(mysql_ci --skip-column-names -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='ritech_sprint2'")
if [[ "$existing" != 0 ]]; then
  echo '::error::Refusing to initialize an existing ritech_sprint2 schema. CI requires a fresh service.'
  exit 1
fi
mysql_ci < database/ritech_database.sql
mysql_ci < database/RiTech_Verificar_BD.sql
echo 'Fresh CI schema initialized from the versioned SQL; ddl-auto remains validate.'
