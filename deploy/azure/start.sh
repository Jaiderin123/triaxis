#!/usr/bin/env bash
# Starts a stopped cluster; the services are ready a few minutes later, on the same address.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
az aks start --resource-group "${RESOURCE_GROUP:-triaxis-rg}" --name "${CLUSTER:-triaxis-aks}"
"$(dirname "$0")/../scripts/wait-ready.sh" 900s
