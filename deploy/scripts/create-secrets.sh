#!/usr/bin/env bash
# Creates the Secret triaxis-secrets in the current kubectl context (ADR 0010). Values are generated or read
# from local files outside the repository and never printed.
#   kind: demonstration users (nurse / nurse-demo, doctor / doctor-demo), no Gemini key.
#   aks:  DEPLOY_NURSE_PASSWORD and DEPLOY_DOCTOR_PASSWORD from ~/.config/triaxis/deploy.env and GEMINI_API_KEY
#         from ~/.config/triaxis/gemini.env. ALERT_WEBHOOK_URL is added when it is set in the environment.
# An existing Secret is kept (the database password must not change under a running volume); pass --replace to
# create it again.
set -euo pipefail
export PATH="$HOME/.local/bin:$PATH"
mode=${1:?usage: create-secrets.sh kind|aks [--replace]}
replace=${2:-}
namespace=triaxis

kubectl create namespace "$namespace" --dry-run=client -o yaml | kubectl apply -f - >/dev/null
if kubectl -n "$namespace" get secret triaxis-secrets >/dev/null 2>&1; then
  if [ "$replace" != "--replace" ]; then
    echo "triaxis-secrets already exists: kept (use --replace to create it again)"
    exit 0
  fi
  kubectl -n "$namespace" delete secret triaxis-secrets >/dev/null
fi

case "$mode" in
  kind)
    nurse_password=nurse-demo
    doctor_password=doctor-demo
    gemini_key=""
    ;;
  aks)
    # shellcheck disable=SC1090
    source "$HOME/.config/triaxis/deploy.env"
    # shellcheck disable=SC1090
    source "$HOME/.config/triaxis/gemini.env"
    nurse_password=${DEPLOY_NURSE_PASSWORD:?DEPLOY_NURSE_PASSWORD missing in ~/.config/triaxis/deploy.env}
    doctor_password=${DEPLOY_DOCTOR_PASSWORD:?DEPLOY_DOCTOR_PASSWORD missing in ~/.config/triaxis/deploy.env}
    gemini_key=${GEMINI_API_KEY:?GEMINI_API_KEY missing in ~/.config/triaxis/gemini.env}
    ;;
  *)
    echo "unknown mode $mode (kind or aks)" >&2
    exit 1
    ;;
esac

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
chmod 700 "$work"
random() { openssl rand -hex 24 | tr -d '\n'; }
# BCrypt cost 10, read from standard input so the password is never an argument of a process.
bcrypt() { printf '%s' "$1" | htpasswd -niBC 10 user | cut -d: -f2 | tr -d '\n'; }

random > "$work/POSTGRES_PASSWORD"
random > "$work/REDIS_PASSWORD"
random > "$work/METRICS_PASSWORD"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/JWT_PRIVATE_KEY" 2>/dev/null
bcrypt "$nurse_password" > "$work/NURSE_PASSWORD_HASH"
bcrypt "$doctor_password" > "$work/DOCTOR_PASSWORD_HASH"
args=()
for key in POSTGRES_PASSWORD REDIS_PASSWORD METRICS_PASSWORD JWT_PRIVATE_KEY NURSE_PASSWORD_HASH DOCTOR_PASSWORD_HASH; do
  args+=("--from-file=$key=$work/$key")
done
if [ -n "$gemini_key" ]; then
  printf '%s' "$gemini_key" > "$work/GEMINI_API_KEY"
  args+=("--from-file=GEMINI_API_KEY=$work/GEMINI_API_KEY")
fi
if [ -n "${ALERT_WEBHOOK_URL:-}" ]; then
  printf '%s' "$ALERT_WEBHOOK_URL" > "$work/ALERT_WEBHOOK_URL"
  args+=("--from-file=ALERT_WEBHOOK_URL=$work/ALERT_WEBHOOK_URL")
fi
kubectl -n "$namespace" create secret generic triaxis-secrets "${args[@]}" >/dev/null
echo "triaxis-secrets created ($mode): ${#args[@]} keys"
