#!/usr/bin/env bash
# Builds the three images and pushes them to GitHub Container Registry with the tag of the aks overlay.
# GHCR_USER and GHCR_TOKEN (write:packages) come from ~/.config/triaxis/ghcr.env; the token goes through stdin.
# After the first push, make the three packages public in GitHub (Packages > Package settings > Visibility).
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
# shellcheck disable=SC1090
source "$HOME/.config/triaxis/ghcr.env"
: "${GHCR_USER:?GHCR_USER missing}" "${GHCR_TOKEN:?GHCR_TOKEN missing}"
tag=$(grep -m1 'newTag:' deploy/k8s/overlays/aks/kustomization.yaml | awk '{print $2}')
owner=$(echo "$GHCR_USER" | tr '[:upper:]' '[:lower:]')
printf '%s' "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin >/dev/null
for service in auth triage assistant; do
  image="ghcr.io/$owner/triaxis-$service:$tag"
  docker build -q -f docker/service.Dockerfile --build-arg SERVICE="$service" -t "$image" . >/dev/null
  docker push -q "$image"
done
docker logout ghcr.io >/dev/null
echo "pushed tag $tag"
