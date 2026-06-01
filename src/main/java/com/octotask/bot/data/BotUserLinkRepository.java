package com.octotask.bot.data;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;

/**
 * Stores Telegram-chat → APP_USER links in the bot-owned AIDB (vector) database,
 * so we never alter the shared OctoTask ATP schema. Auto-creates its table on
 * startup for convenience.
 */
@Repository
@ConditionalOnBean(name = "vectorDataSource")
public class BotUserLinkRepository {

    private static final Logger log = LoggerFactory.getLogger(BotUserLinkRepository.class);

    private final JdbcTemplate jdbc;

    public BotUserLinkRepository(@Qualifier("vectorDataSource") DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
    }

    @PostConstruct
    public void ensureTable() {
        try {
            jdbc.execute("CREATE TABLE bot_user_link (" +
                    " telegram_chat_id NUMBER PRIMARY KEY," +
                    " app_user_id NUMBER NOT NULL," +
                    " app_user_name VARCHAR2(255) NOT NULL," +
                    " team_id NUMBER," +
                    " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            log.info("Created BOT_USER_LINK table");
        } catch (Exception e) {
            // ORA-00955: name is already used by an existing object — fine.
            log.debug("BOT_USER_LINK table already exists or could not be created: {}", e.getMessage());
        }
    }

    public BotUserLink findByChatId(long chatId) {
        try {
            return jdbc.queryForObject(
                    "SELECT telegram_chat_id, app_user_id, app_user_name, team_id FROM bot_user_link WHERE telegram_chat_id = ?",
                    (rs, n) -> {
                        int teamId = rs.getInt("team_id");
                        Integer team = rs.wasNull() ? null : teamId;
                        return new BotUserLink(rs.getLong("telegram_chat_id"), rs.getInt("app_user_id"),
                                rs.getString("app_user_name"), team);
                    }, chatId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** Insert or update the link for a chat. */
    public void upsert(BotUserLink link) {
        int updated = jdbc.update(
                "UPDATE bot_user_link SET app_user_id = ?, app_user_name = ?, team_id = ? WHERE telegram_chat_id = ?",
                link.getAppUserId(), link.getAppUserName(), link.getTeamId(), link.getTelegramChatId());
        if (updated == 0) {
            jdbc.update("INSERT INTO bot_user_link (telegram_chat_id, app_user_id, app_user_name, team_id) VALUES (?, ?, ?, ?)",
                    link.getTelegramChatId(), link.getAppUserId(), link.getAppUserName(), link.getTeamId());
        }
    }

    public void deleteByChatId(long chatId) {
        jdbc.update("DELETE FROM bot_user_link WHERE telegram_chat_id = ?", chatId);
    }
}
