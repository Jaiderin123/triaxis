#!/usr/bin/env bash
# Removes everything when the evaluation ends. The workloads go first, so the load balancer releases the public IP
# before Terraform deletes it; then Terraform deletes the IP, the cluster and the resource group.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
root=$(cd "$(dirname "$0")/../.." && pwd)
kubectl delete -k "$root/deploy/k8s/overlays/aks" --ignore-not-found --wait=true || true
cd "$root/deploy/azure/terraform"
terraform destroy -input=false
