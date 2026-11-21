#!/usr/bin/env bash
# Starts a stopped cluster; the services answer a few minutes later on the same address and certificate.
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
# shellcheck source=deploy/azure/common.sh
source "$root/deploy/azure/common.sh"
az aks start --resource-group "$RESOURCE_GROUP" --name "$CLUSTER"
use_cluster
"$root/deploy/scripts/wait-ready.sh" 900s
wait_https "$(terraform -chdir="$root/deploy/azure/terraform" output -raw fqdn)"
echo "Started: https://$(terraform -chdir="$root/deploy/azure/terraform" output -raw fqdn)"
