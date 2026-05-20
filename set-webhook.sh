#!/usr/bin/env bash
#
# Register the public webhook with Telegram. Run once after deploy.
#
# Usage:
#   TELEGRAM_BOT_TOKEN=xxx ./set-webhook.sh https://your-host.example.com
#
set -euo pipefail

if [[ -z "${TELEGRAM_BOT_TOKEN:-}" ]]; then
  echo "TELEGRAM_BOT_TOKEN env var is required" >&2
  exit 1
fi

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <public-base-url>" >&2
  echo "Example: $0 https://bot.octotask.example.com" >&2
  exit 1
fi

BASE_URL="${1%/}"
WEBHOOK_URL="${BASE_URL}/api/telegram/webhook"

echo "Registering webhook: ${WEBHOOK_URL}"
curl -sS -X POST \
  "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook" \
  -H "Content-Type: application/json" \
  -d "{\"url\":\"${WEBHOOK_URL}\"}"
echo
