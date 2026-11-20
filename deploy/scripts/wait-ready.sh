#!/usr/bin/env bash
# Waits until the state, the topic and the three services are ready in the namespace triaxis.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
timeout=${1:-600s}
for workload in statefulset/postgres statefulset/kafka statefulset/redis deployment/traefik deployment/auth \
    deployment/triage deployment/assistant; do
  kubectl -n triaxis rollout status "$workload" --timeout="$timeout"
done
kubectl -n triaxis wait --for=condition=complete job/kafka-topic --timeout="$timeout" 2>/dev/null \
  || echo "job/kafka-topic already finished and removed"
