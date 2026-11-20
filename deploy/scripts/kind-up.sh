#!/usr/bin/env bash
# Validates the manifests on a local kind cluster: builds the images, loads them, creates the Secret and applies
# the kind overlay. The Ingress answers on http://localhost:8088. Remove it with: kind delete cluster --name triaxis
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"

kind get clusters | grep -qx triaxis || kind create cluster --config deploy/kind/cluster.yaml
kubectl config use-context kind-triaxis >/dev/null
docker compose build auth triage assistant
for service in auth triage assistant; do
  kind load docker-image "triaxis-$service:local" --name triaxis
done
deploy/scripts/create-secrets.sh kind
kubectl apply -k deploy/k8s/overlays/kind
deploy/scripts/wait-ready.sh
echo "Ready: http://localhost:8088 (POST /auth/token, /triage/..., /asistente/...)"
