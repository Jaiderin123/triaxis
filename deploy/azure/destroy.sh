#!/usr/bin/env bash
# Removes everything when the evaluation ends. Usage: destroy.sh [--yes]
# The workloads go first, so the load balancer releases the public IP before Terraform deletes it; then Terraform
# deletes the IP, the cluster and the resource group. A stopped cluster is started first, because a stopped
# cluster has no API server to delete the workloads with.
set -euo pipefail
[ "${1:-}" = "--yes" ] && export ASSUME_YES=true
root=$(cd "$(dirname "$0")/../.." && pwd)
# shellcheck source=deploy/azure/common.sh
source "$root/deploy/azure/common.sh"
confirm "Delete the cluster, the public IP and the resource group $RESOURCE_GROUP?"
if [ "$(az aks show --resource-group "$RESOURCE_GROUP" --name "$CLUSTER" --query powerState.code -o tsv 2>/dev/null)" = "Stopped" ]; then
  az aks start --resource-group "$RESOURCE_GROUP" --name "$CLUSTER"
fi
if az aks show --resource-group "$RESOURCE_GROUP" --name "$CLUSTER" >/dev/null 2>&1; then
  use_cluster
  kubectl delete -k "$root/deploy/k8s/overlays/aks" --ignore-not-found --wait=true || true
fi
cd "$root/deploy/azure/terraform"
terraform destroy -input=false -auto-approve
