#!/bin/bash
# Wrapper script to run Maven commands in Docker without local Java/Maven installation
#
# Usage:
#   ./mvnw.sh test              # Run tests
#   ./mvnw.sh clean package     # Build JARs
#   ./mvnw.sh dependency:tree   # Any Maven command
#   ./mvnw.sh -it bash          # Interactive shell for debugging

set -e

# Build the dev image if it doesn't exist
IMAGE_NAME="echo-maven-dev"

if [[ ! "$(docker images -q $IMAGE_NAME 2> /dev/null)" ]]; then
  echo "Building dev image..."
  docker build -f Dockerfile.dev -t $IMAGE_NAME .
fi

# If first arg is -it, run interactive shell
if [[ "$1" == "-it" ]]; then
  shift
  docker run -it --rm \
    -v "$(pwd)":/app \
    -v maven-cache:/root/.m2 \
    -w /app \
    $IMAGE_NAME "$@"
else
  # Run Maven command
  docker run --rm \
    -v "$(pwd)":/app \
    -v maven-cache:/root/.m2 \
    -w /app \
    $IMAGE_NAME mvn "$@"
fi
