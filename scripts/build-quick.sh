#!/usr/bin/env bash
set -eo pipefail

source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk env
source "$(dirname "$0")/../env.build.sh"

exec mvnd clean install -Dquickly "$@"
