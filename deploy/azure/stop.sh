#!/usr/bin/env bash
# Stops the cluster between demonstrations: the node is released and not billed; disks and the IP stay (ADR 0010).
set -euo pipefail
az aks stop --resource-group "${RESOURCE_GROUP:-triaxis-rg}" --name "${CLUSTER:-triaxis-aks}"
