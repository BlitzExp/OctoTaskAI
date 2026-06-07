package com.octotask.bot.login;

import com.octotask.bot.data.BotLoginCodeRepository;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * Issues one private login code per APP_USER (stored in the bot-owned AIDB).
 * Idempotent: only users without a code get one, so it is safe to leave enabled.
 *
 * Enable with bot.login.codes.seed=true (BOT_LOGIN_CODES_SEED=true). In a real
 * deployment the web app would mint these per user; this seeder bootstraps them
 * for existing users. The issued codes are logged once so an admin can hand them
 * out (the log is local and never committed).
 */
@Component
@ConditionalOnBean(BotLoginCodeRepository.class)
@ConditionalOnProperty(prefix = "bot.login.codes", name = "seed", havingValue = "true")
public class LoginCodeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoginCodeSeeder.class);

    // No ambiguous characters (0/O, 1/I/L) so codes are easy to read and type.
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LEN = 6;

    private final OctoTaskDataClient data;
    private final BotLoginCodeRepository codes;
    private final SecureRandom random = new SecureRandom();

    public LoginCodeSeeder(OctoTaskDataClient data, BotLoginCodeRepository codes) {
        this.data = data;
        this.codes = codes;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AppUser> users;
        try {
            users = data.listUsers();
        } catch (Exception e) {
            log.warn("Could not list APP_USERs to seed login codes; skipping: {}", e.getMessage());
            return;
        }

        int issued = 0;
        for (AppUser u : users) {
            try {
                if (codes.findCodeForUser(u.getId()) != null) continue;   // already has one
                String code = uniqueCode();
                codes.insert(code, u.getId(), u.getName(), u.getTeamId());
                issued++;
                log.info("Issued login code for {} (id={}): {}", u.getName(), u.getId(), code);
            } catch (Exception e) {
                log.warn("Failed to issue login code for user id={}: {}", u.getId(), e.getMessage());
            }
        }
        log.info("Login-code seeding complete: issued {} new code(s); {} user(s) total have codes",
                issued, safeCount());
    }

    private String uniqueCode() {
        // Retry on the rare collision (codes table enforces UNIQUE).
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = randomCode();
            if (codes.findByCode(code) == null) return code;
        }
        // Fall back to a longer code if we somehow keep colliding.
        return randomCode() + randomCode();
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LEN);
        for (int i = 0; i < CODE_LEN; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private long safeCount() {
        try { return codes.count(); } catch (Exception e) { return -1; }
    }
}
