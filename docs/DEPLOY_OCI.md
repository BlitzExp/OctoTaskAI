# Deploying OctoTaskAI to OCI

This service is a stateless Spring Boot container plus two dependencies:
- **Oracle ATP + AIDB** — already in OCI; reached over the wallets.
- **Ollama** — the local LLM, run as a companion container.

Recommended target: **OCI Container Instances** (simplest) or **OKE** (if you already run
Kubernetes). Both run the app container and an Ollama sidecar in the same private subnet.

## 1. Build and push the image to OCIR
```bash
./mvnw clean package
# tag: <region-key>.ocir.io/<tenancy-namespace>/octotask-ai:0.1.0
docker build -t mx-queretaro-1.ocir.io/<namespace>/octotask-ai:0.1.0 .
docker login mx-queretaro-1.ocir.io      # user: <namespace>/<oci-username>, pass: auth token
docker push mx-queretaro-1.ocir.io/<namespace>/octotask-ai:0.1.0
```

## 2. Store secrets (do NOT bake them into the image)
Put these in **OCI Vault** and inject as env vars / mounted files at runtime:
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_WEBHOOK_SECRET`, `BOT_ACCESS_CODE`
- `DB_PASSWORD`, `db2_password`
- The two wallet folders (`wallet/ATP`, `wallet/AIDB`) — mount as a read-only volume at
  `/app/wallet`. Keep `DB_URL`/`db2_url` pointing at `TNS_ADMIN=/app/wallet/ATP` and
  `/app/wallet/AIDB`.

> The repo's `.env` and `wallet/` are gitignored — never commit them or push them in the image.

## 3. Container Instance shape
Create a Container Instance with **two containers**:

| Container | Image | Notes |
|---|---|---|
| `ollama` | `ollama/ollama:latest` | command pulls the model on start; expose 11434 on localhost |
| `octotask-ai` | your OCIR image | env `OLLAMA_URL=http://localhost:11434`, wallets mounted |

Containers in one Container Instance share `localhost`, so `OLLAMA_URL=http://localhost:11434`
works. Give the instance enough memory for the model (qwen2.5:3b ≈ 3–4 GB) plus the JVM
(~1 GB) — start with **6–8 GB RAM / 2 OCPU**.

Pull the model once (init or first boot):
```bash
ollama pull qwen2.5:3b
```
For persistence across restarts, mount a volume at `/root/.ollama` so the model isn't
re-downloaded.

### Required env for the app container
```
SERVER_PORT=8080
TELEGRAM_BOT_TOKEN=...        TELEGRAM_WEBHOOK_SECRET=...
BOT_ACCESS_CODE=...           BOT_ROUTER_SEED=true   # first deploy only
OLLAMA_ENABLED=true           OLLAMA_URL=http://localhost:11434   OLLAMA_MODEL=qwen2.5:3b
DRIVER_CLASS_NAME=oracle.jdbc.OracleDriver
DB_USER=octotask  DB_PASSWORD=...  DB_URL=jdbc:oracle:thin:@mgqba7appqlizl1d_high?TNS_ADMIN=/app/wallet/ATP
db2_user=OCTOTASKBOT  db2_password=...  db2_url=jdbc:oracle:thin:@vectorbotdb_high?TNS_ADMIN=/app/wallet/AIDB
```

## 4. Expose HTTPS and register the webhook
Telegram requires a public HTTPS endpoint with a valid cert. Front the Container Instance
with an **OCI Load Balancer** (or API Gateway) terminating TLS, routing to port 8080.

Then register the webhook once:
```bash
TELEGRAM_BOT_TOKEN=... TELEGRAM_WEBHOOK_SECRET=... ./set-webhook.sh https://bot.yourdomain.com
```

## 5. First-run checklist
- [ ] Wallets mounted; app logs "Oracle DataSource configured" for both.
- [ ] `BOT_USER_LINK` table auto-created (log line on startup).
- [ ] `rutas_semanticas` seeded (`BOT_ROUTER_SEED=true` once → "Seeded N semantic routes").
- [ ] Ollama reachable (no "Ollama not reachable" warnings).
- [ ] Webhook returns 200; sending `/start` in Telegram replies with the welcome message.
- [ ] After first deploy, set `BOT_ROUTER_SEED=false`.

## Scaling notes
- The app is stateless (login state lives in AIDB), so it scales horizontally behind the LB.
- Each replica needs its own Ollama or a shared Ollama service; for multiple replicas prefer
  a dedicated Ollama Container Instance and point all apps at it via `OLLAMA_URL`.
