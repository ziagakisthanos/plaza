#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

NEXUS_URL="${NEXUS_URL:-http://localhost:8091}"
NEXUS_CONTAINER="${NEXUS_CONTAINER:-nexus}"
DOCKER_PORT="${DOCKER_PORT:-5000}"
: "${NEXUS_ADMIN_PASSWORD:?set NEXUS_ADMIN_PASSWORD in .env}"

API="$NEXUS_URL/service/rest/v1"

authenticated() {
  curl -sS -o /dev/null -w '%{http_code}' -u "admin:$1" "$API/security/anonymous"
}

send() {
  method=$1
  path=$2
  body=$3
  response=$(mktemp)
  code=$(curl -sS -o "$response" -w '%{http_code}' -u "admin:$NEXUS_ADMIN_PASSWORD" \
    -X "$method" -H 'Content-Type: application/json' --data "$body" "$API$path")
  case $code in
    2??) rm -f "$response" ;;
    *)
      echo "$method $path failed with $code" >&2
      cat "$response" >&2
      rm -f "$response"
      exit 1
      ;;
  esac
}

repository_exists() {
  code=$(curl -sS -o /dev/null -w '%{http_code}' -u "admin:$NEXUS_ADMIN_PASSWORD" "$API/repositories/$1")
  [ "$code" = 200 ]
}

create_repository() {
  name=$1
  path=$2
  body=$3
  if repository_exists "$name"; then
    echo "repository $name already exists"
  else
    send POST "/repositories/$path" "$body"
    echo "repository $name created"
  fi
}

maven_hosted() {
  name=$1
  version_policy=$2
  write_policy=$3
  create_repository "$name" maven/hosted "$(printf '{"name":"%s","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"%s"},"maven":{"versionPolicy":"%s","layoutPolicy":"STRICT","contentDisposition":"ATTACHMENT"}}' "$name" "$write_policy" "$version_policy")"
}

docker_hosted() {
  name=$1
  port=$2
  create_repository "$name" docker/hosted "$(printf '{"name":"%s","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true,"writePolicy":"ALLOW"},"docker":{"v1Enabled":false,"forceBasicAuth":false,"httpPort":%s}}' "$name" "$port")"
}

wait_for_nexus() {
  echo "waiting for nexus at $NEXUS_URL"
  curl -sf --retry 60 --retry-delay 5 --retry-all-errors --max-time 5 "$API/status" > /dev/null
}

set_admin_password() {
  if [ "$(authenticated "$NEXUS_ADMIN_PASSWORD")" = 200 ]; then
    echo "admin password already set"
    return
  fi
  initial=$(docker exec "$NEXUS_CONTAINER" cat /nexus-data/admin.password)
  curl -sSf -u "admin:$initial" -X PUT -H 'Content-Type: text/plain' \
    --data "$NEXUS_ADMIN_PASSWORD" "$API/security/users/admin/change-password"
  echo "admin password set"
}

require_eula() {
  if curl -sSf -u "admin:$NEXUS_ADMIN_PASSWORD" "$API/system/eula" | grep -q '"accepted" : true'; then
    return
  fi
  echo "The Community Edition license agreement has not been accepted yet." >&2
  echo "Read it at https://links.sonatype.com/products/nxrm/ce-eula and accept it" >&2
  echo "when you sign in at $NEXUS_URL, then run this script again." >&2
  exit 1
}

wait_for_nexus
set_admin_password
require_eula

maven_hosted jars-releases RELEASE ALLOW_ONCE
maven_hosted jars-snapshots SNAPSHOT ALLOW
maven_hosted wars-releases RELEASE ALLOW_ONCE
maven_hosted wars-snapshots SNAPSHOT ALLOW
docker_hosted docker-hosted "$DOCKER_PORT"

send PUT /repositories/maven/group/maven-public '{"name":"maven-public","online":true,"storage":{"blobStoreName":"default","strictContentTypeValidation":true},"group":{"memberNames":["maven-central","jars-releases","jars-snapshots"]}}'
echo "group maven-public serves maven-central, jars-releases and jars-snapshots"

send PUT /security/realms/active '["NexusAuthenticatingRealm","DockerToken"]'
echo "docker bearer token realm active"

curl -sSf -u "admin:$NEXUS_ADMIN_PASSWORD" "$API/repositories" | grep '"name"'
