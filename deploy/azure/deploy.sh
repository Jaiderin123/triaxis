#!/usr/bin/env bash
# Deploys Triaxis to Azure (ADR 0010): Terraform creates the resource group, the AKS cluster, the static public
# IP and the optional budget; then the images are pushed, the Secret is created and the aks overlay is applied.
# Needs: az login, deploy/azure/terraform/terraform.tfvars, ~/.config/triaxis/{ghcr,deploy,gemini}.env.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root/deploy/azure/terraform"

terraform init -input=false >/dev/null
terraform apply -input=false
fqdn=$(terraform output -raw fqdn)
eval "$(terraform output -raw get_credentials)"
cd "$root"

expected=$(grep -m1 'host=' deploy/k8s/overlays/aks/kustomization.yaml | sed 's/.*host=//')
if [ "$fqdn" != "$expected" ]; then
  echo "The host of the aks overlay ($expected) differs from the public IP ($fqdn): fix the overlay first." >&2
  exit 1
fi

deploy/azure/push-images.sh
deploy/scripts/create-secrets.sh aks
kubectl apply -k deploy/k8s/overlays/aks
deploy/scripts/wait-ready.sh 900s
echo "Ready: https://$fqdn (the certificate of Let's Encrypt may take a minute after the first start)"
