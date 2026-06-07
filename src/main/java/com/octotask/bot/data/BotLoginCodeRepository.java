package com.octotask.bot.data;

import com.octotask.bot.data.model.AppUser;
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
 * Per-user login codes, stored in the bot-owned AIDB so we never alter the
 * shared OctoTask ATP schema. Each APP_USER gets one private code; the user
 * logs in with {@code /login <code>} (no name), which removes both the shared
 * access code and the impersonation hole of name-based login. Auto-creates its
 * table on startup.
 */
@Repository
@ConditionalOnBean(name = "vectorDataSource")
public class BotLoginCodeRepository {

    private static final Logger log = LoggerFactory.getLogger(BotLoginCodeRepository.class);

    private final JdbcTemplate jdbc;

    public BotLoginCodeRepository(@Qualifier("vectorDataSource") DataSource ds) {
        this.jdbc = new JdbcTemplate(ds);
    }

    @PostConstruct
    public void ensureTable() {
        try {
            jdbc.execute("CREATE TABLE bot_login_code (" +
                    " app_user_id NUMBER PRIMARY KEY," +
                    " code VARCHAR2(40) NOT NULL UNIQUE," +
                    " app_user_name VARCHAR2(255) NOT NULL," +
                    " team_id NUMBER," +
                    " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            log.info("Created BOT_LOGIN_CODE table");
        } catch (Exception e) {
            // ORA-00955: name already in use — fine.
            log.debug("BOT_LOGIN_CODE table already exists or could not be created: {}", e.getMessage());
        }
    }

    /** Resolve a login code to the user it belongs to; null if the code is unknown. */
    public AppUser findByCode(String code) {
        try {
            return jdbc.queryForObject(
                    "SELECT app_user_id, app_user_name, team_id FROM bot_login_code WHERE code = ?",
                    (rs, n) -> {
                        int teamId = rs.getInt("team_id");
                        Integer team = rs.wasNull() ? null : teamId;
                        return new AppUser(rs.getInt("app_user_id"), rs.getString("app_user_name"), team);
                    }, code);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** The existing code for a user, or null if none has been issued yet. */
    public String findCodeForUser(int appUserId) {
        try {
            return jdbc.queryForObject(
                    "SELECT code FROM bot_login_code WHERE app_user_id = ?", String.class, appUserId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public void insert(String code, int appUserId, String name, Integer teamId) {
        jdbc.update("INSERT INTO bot_login_code (app_user_id, code, app_user_name, team_id) VALUES (?, ?, ?, ?)",
                appUserId, code, name, teamId);
    }

    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM bot_login_code", Long.class);
        return n == null ? 0 : n;
    }
}
