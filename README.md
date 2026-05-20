# OctoTaskAI

The AI agent of OctoTask — a Spring Boot microservice that receives Telegram
webhooks, asks Gemini what to do, and executes one of 11 bot tools against the
shared OctoTask Oracle ATP database.

## Architecture

```
Telegram  ──webhook──►  TelegramWebhookController
                            │
                            ▼
                       BotOrchestrator ──► GeminiClient ──► Gemini API
                            │                  │
                            │                  └─ functionCall name + args
                            ▼
                       BotTool (one of 11) ──► OctoTaskDataClient (interface)
                                                       │
                                                       ▼
                                          JdbcOctoTaskDataClient (today)
                                          HttpOctoTaskDataClient (future)
                                                       │
                                                       ▼
                                                Oracle ATP
```

The `OctoTaskDataClient` interface is the swap point: today it's JDBC against
the OctoTask schema; later it can become an HTTP client against the OctoTask
REST API without touching any tool.

## Run locally

```bash
cp .env.example .env
# fill in TELEGRAM_BOT_TOKEN, GEMINI_API_KEY, DB_USER, DB_PASSWORD, DB_URL
# drop your Oracle wallet files into ./wallet/

set -a; source .env; set +a
./mvnw spring-boot:run
```

The service listens on `:8080`. The webhook endpoint is `POST /api/telegram/webhook`.

## Build & containerize

```bash
./mvnw clean package
docker build -t octotask-ai:dev .
docker run --rm -p 8080:8080 --env-file .env -v "$(pwd)/wallet:/app/wallet" octotask-ai:dev
```

## Register the webhook (one-time per deploy)

```bash
TELEGRAM_BOT_TOKEN=xxx ./set-webhook.sh https://your-public-host
```

## Adding a new bot tool

1. Create a class in `com.octotask.bot.tools` that implements `BotTool`.
2. Annotate it `@Component`.
3. That's it — `GeminiToolSchemaBuilder` picks it up automatically and the
   orchestrator routes calls to it.

## Configuration

All settings come from environment variables (see `.env.example`). The most
relevant:

| Env var | Default | Purpose |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | _required_ | Bot auth with Telegram API |
| `GEMINI_API_KEY` | _required_ | Google Generative Language API key |
| `GEMINI_API_URL` | gemini-3.1-flash-lite-preview | Model endpoint |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | _required_ | Oracle ATP credentials |
| `BOT_MEMORY_MAX_CHARS` | 200 | Per-chat context window size |
| `SERVER_PORT` | 8080 | HTTP listen port |

## Notes & limitations

- Chat memory is in-process and per-pod. Restarts wipe context.
- Bot reads/writes the OctoTask schema directly (`TASKS`, `APP_USER`, `SPRINT`,
  `TASK_STATE`). Any schema migration in OctoTask must be coordinated.
- Webhook mode requires a public HTTPS endpoint with a valid cert.
