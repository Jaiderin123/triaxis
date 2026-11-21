#!/usr/bin/env bash
# Deploys Triaxis to Azure (ADR 0010). Usage: deploy.sh [--yes]
#   1. pushes the images to GHCR and checks that they can be pulled without credentials (the packages are private
#      after their first push: make them public in GitHub, then run this again);
#   2. Terraform plans the resource group, the AKS cluster, the static public IP and the budget, and applies that
#      exact plan after a confirmation (--yes skips it);
#   3. creates the Secret, applies the aks overlay and waits for the workloads and for HTTPS.
# Needs: az login, deploy/azure/terraform/terraform.tfvars, ~/.config/triaxis/{ghcr,deploy,gemini}.env.
set -euo pipefail
[ "${1:-}" = "--yes" ] && export ASSUME_YES=true
root=$(cd "$(dirname "$0")/../.." && pwd)
# shellcheck source=deploy/azure/common.sh
source "$root/deploy/azure/common.sh"
cd "$root"

deploy/azure/push-images.sh
owner=$(sed -n 's/^GHCR_USER=//p' "$HOME/.config/triaxis/ghcr.env" | tr '[:upper:]' '[:lower:]')
tag=$(grep -m1 'newTag:' deploy/k8s/overlays/aks/kustomization.yaml | awk '{print $2}')
for service in auth triage assistant; do
  token=$(curl -fsS "https://ghcr.io/token?scope=repository:$owner/triaxis-$service:pull" | jq -r '.token // empty')
  if ! curl -fsS -o /dev/null -H "Authorization: Bearer $token" \
      -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.v2+json' \
      "https://ghcr.io/v2/$owner/triaxis-$service/manifests/$tag"; then
    echo "ghcr.io/$owner/triaxis-$service:$tag cannot be pulled anonymously: make the package public and run again" >&2
    exit 1
  fi
done

cd "$root/deploy/azure/terraform"
terraform init -input=false >/dev/null
terraform plan -input=false -out=triaxis.tfplan
confirm "Apply this plan (it creates billed resources)?"
terraform apply -input=false triaxis.tfplan
fqdn=$(terraform output -raw fqdn)
cd "$root"

expected=$(grep -m1 'host=' deploy/k8s/overlays/aks/kustomization.yaml | sed 's/.*host=//')
if [ "$fqdn" != "$expected" ]; then
  echo "The host of the aks overlay ($expected) differs from the public IP ($fqdn): fix the overlay first." >&2
  exit 1
fi

use_cluster
deploy/scripts/create-secrets.sh aks
kubectl apply -k deploy/k8s/overlays/aks
deploy/scripts/wait-ready.sh 900s
wait_https "$fqdn"
echo "Ready: https://$fqdn"
