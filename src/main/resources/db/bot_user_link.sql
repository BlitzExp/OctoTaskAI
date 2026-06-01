-- Telegram chat -> OctoTask APP_USER mapping. Lives in the bot-owned AIDB
-- (vector) database so the shared OctoTask ATP schema is never modified.
-- The app auto-creates this on startup; this file is for reference / manual setup.

CREATE TABLE bot_user_link (
    telegram_chat_id NUMBER PRIMARY KEY,
    app_user_id      NUMBER NOT NULL,
    app_user_name    VARCHAR2(255) NOT NULL,
    team_id          NUMBER,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
