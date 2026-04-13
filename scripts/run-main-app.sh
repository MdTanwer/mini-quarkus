#!/usr/bin/env bash
set -eo pipefail

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
source "$(dirname "$0")/../env.build.sh"

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

"$repo_root/mvnw" package -pl integration-tests/main -am "${@}"
exec java -jar "$repo_root/integration-tests/main/target/mini-quarkus-main.jar"
