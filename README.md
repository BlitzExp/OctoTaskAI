# OctoTaskAI

The AI agent of OctoTask — a Spring Boot microservice that receives Telegram
webhooks and answers/acts on them **entirely with local models** (no cloud LLM):
local sentence embeddings choose which tool to run, and a local LLM (Ollama)
phrases the reply. It executes one of 11 bot tools against the OctoTask Oracle
ATP database.

## Architecture

```
Telegram ──webhook──► TelegramWebhookController  (validates secret token)
                            │
                            ▼
                       BotOrchestrator
                            │
        ┌───────────────────┼───────────────────────────────┐
        ▼                   ▼                                 ▼
   LoginService        SemanticRouter                   ReplyComposer
   (/login gate)       embed(msg) ─► DJLEmbeddingService      │
        │              vector search ─► AIDB rutas_semanticas │
        │                   │  picks funcion_backend (a tool) │
        ▼                   ▼                                 │
   BOT_USER_LINK     ToolArgumentResolver                     │
   (AIDB)            • inject identity (userName/teamId/id)    │
   telegram_id ─►    • LLM slot-extract the rest ──► Ollama    │
   APP_USER          • ask user if a required field missing    │
        │                   │                                 │
        └──────────────────►▼                                 │
                       BotTool (1 of 11) ─► OctoTaskDataClient │
                                              JdbcOctoTaskDataClient
                                                    │          │
                                                    ▼          │
                                              Oracle ATP ──────┘
                                              (TASKS, APP_USER, SPRINT, TASK_STATE)
```

Two databases:
- **ATP** (`wallet/ATP`, user `octotask`) — the shared OctoTask schema the tools read/write.
- **AIDB** (`wallet/AIDB`, user `OCTOTASKBOT*`) — bot-owned Oracle 23ai DB holding the
  `rutas_semanticas` vector table and `bot_user_link`. We never alter the ATP schema.

### How a message is handled
1. **Login gate** — an unlinked chat is told to `/login <access-code> <name>`. The link
   (`telegram_chat_id → APP_USER`) is stored in `bot_user_link` (AIDB).
2. **Semantic routing** — the message is embedded locally (MiniLM ONNX via DJL) and matched
   against seeded example phrasings in `rutas_semanticas`; the closest route's
   `funcion_backend` names the tool. Below `bot.router.min-similarity` → ask to rephrase.
3. **Argument resolution** — identity fills `userName`/`teamId`/`assigneeId`; the local LLM
   extracts any remaining fields (e.g. `taskId`, `sprintId`); missing required fields are
   requested from the user.
4. **Execution** — the tool runs against Oracle ATP.
5. **Reply** — the local LLM phrases the rows into a Telegram message (templated fallback if
   Ollama is down).

## Prerequisites
- Java 17, Oracle wallets in `wallet/ATP` and `wallet/AIDB`.
- **Ollama** for the NLP layer (optional but recommended):
  ```bash
  # install from https://ollama.com, then:
  ollama serve
  ollama pull qwen2.5:1.5b-instruct   # lightweight (~1 GB), good Spanish + JSON; sized for OCI
  ```
  If Ollama is unreachable, the bot still works using templated replies and identity-only
  argument resolution.

## Run locally
```bash
cp .env.example .env       # fill in tokens, DB creds, BOT_ACCESS_CODE; drop wallets into ./wallet
set -a; source .env; set +a
./mvnw spring-boot:run
```
The service listens on `:8080`; the webhook endpoint is `POST /api/telegram/webhook`.

First run: set `BOT_ROUTER_SEED=true` once to populate `rutas_semanticas` with the tool
example phrasings (it only seeds when the table is empty).

## Build & containerize
```bash
./mvnw clean package
docker build -t octotask-ai:dev .
docker run --rm -p 8080:8080 --env-file .env -v "$(pwd)/wallet:/app/wallet" octotask-ai:dev
```

## Register the webhook (one-time per deploy)
```bash
TELEGRAM_BOT_TOKEN=xxx TELEGRAM_WEBHOOK_SECRET=yyy ./set-webhook.sh https://your-public-host
```

## Deployment
See [`docs/DEPLOY_OCI.md`](docs/DEPLOY_OCI.md) for deploying to OCI (app + Ollama sidecar,
wallets as secrets).

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | _required_ | Bot auth with Telegram API |
| `TELEGRAM_WEBHOOK_SECRET` | _(off)_ | If set, webhook rejects calls without the matching header |
| `OLLAMA_ENABLED` | true | Enable the local LLM layer |
| `OLLAMA_URL` | http://localhost:11434 | Ollama server URL |
| `OLLAMA_MODEL` | qwen2.5:3b | Local model name |
| `BOT_ACCESS_CODE` | _required for login_ | Shared code users provide to `/login` |
| `BOT_ROUTER_MIN_SIMILARITY` | 0.45 | Min cosine similarity to accept a route |
| `BOT_ROUTER_SEED` | false | Seed `rutas_semanticas` on startup (if empty) |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | _required_ | Oracle ATP (OctoTask schema) |
| `db2_url`, `db2_user`, `db2_password` | _required_ | Oracle AIDB (vector + links) |
| `SERVER_PORT` | 8080 | HTTP listen port |

## Adding a new bot tool
1. Create a `@Component` implementing `BotTool` in `com.octotask.bot.tools`.
2. Add example phrasings for it to `RouteSeeder` (or insert routes with
   `funcion_backend = <toolName>`), then re-seed.
3. The orchestrator dispatches to it automatically by name.

## Notes & limitations
- Bot reads/writes the OctoTask ATP schema directly; coordinate any schema migration.
- The MiniLM ONNX model (~470 MB) ships via Git LFS — run `git lfs pull` after cloning.
- `/login` uses a shared access code for now; a per-user one-time code issued by the
  OctoTask web app is the recommended hardening step.
