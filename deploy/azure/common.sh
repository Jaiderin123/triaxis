# shellcheck shell=bash
# Shared by the Azure scripts (sourced, not run). Every kubectl call of the scripts targets the AKS cluster
# explicitly, never whatever context was current (a kind cluster, for example).
export PATH="$HOME/.local/bin:$PATH"
RESOURCE_GROUP=${RESOURCE_GROUP:-triaxis-rg}
CLUSTER=${CLUSTER:-triaxis-aks}

# Fetches the credentials of the cluster and makes it the current kubectl context.
use_cluster() {
  az aks get-credentials --resource-group "$RESOURCE_GROUP" --name "$CLUSTER" --overwrite-existing >/dev/null
  kubectl config use-context "$CLUSTER" >/dev/null
}

# Asks before an action that creates, starts or deletes billed resources; --yes skips the question.
confirm() {
  [ "${ASSUME_YES:-}" = "true" ] && return 0
  if [ ! -t 0 ]; then
    echo "$1: no terminal to confirm, run it again with --yes" >&2
    exit 1
  fi
  read -r -p "$1 [y/N] " answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "cancelled" >&2; exit 1; }
}

# Waits until the host answers over HTTPS with a valid certificate. The first ACME challenge can run before the
# load balancer sends traffic to Traefik and fail with a timeout; Traefik is then restarted once to try again.
wait_https() {
  local host=$1 try
  for try in 1 2; do
    for _ in $(seq 1 18); do
      curl -fsS -o /dev/null --max-time 10 "https://$host/.well-known/jwks.json" 2>/dev/null && return 0
      sleep 10
    done
    [ "$try" = 1 ] && kubectl -n triaxis rollout restart deployment/traefik >/dev/null \
      && kubectl -n triaxis rollout status deployment/traefik --timeout=300s >/dev/null
  done
  echo "https://$host does not answer with a valid certificate yet: see kubectl -n triaxis logs deploy/traefik" >&2
  return 1
}
