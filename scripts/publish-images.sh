#!/bin/sh
set -eu

cd "$(dirname "$0")/.."

if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

REGISTRY="${REGISTRY:-localhost:5000}"
NAMESPACE="${NAMESPACE:-buy-02}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
: "${NEXUS_USER:?set NEXUS_USER in .env}"
: "${NEXUS_PASSWORD:?set NEXUS_PASSWORD in .env}"

VERSION=$(sh ./mvnw -q -N org.apache.maven.plugins:maven-help-plugin:3.5.0:evaluate -Dexpression=project.version -DforceStdout)

echo "building jars for version $VERSION"
sh ./mvnw -q -DskipTests package

echo "$NEXUS_PASSWORD" | docker login "$REGISTRY" -u "$NEXUS_USER" --password-stdin

java_services="discovery gateway user-service product-service media-service order-service"

for service in $java_services; do
  image="$REGISTRY/$NAMESPACE/$service:$VERSION"
  echo "building $image"
  docker build -t "$image" "$service"
  docker push "$image"
done

image="$REGISTRY/$NAMESPACE/frontend:$VERSION"
echo "building $image"
docker build -t "$image" frontend
docker push "$image"

echo "published version $VERSION to $REGISTRY/$NAMESPACE"
