package com.octotask.bot.login;

import com.octotask.bot.data.BotLoginCodeRepository;
import com.octotask.bot.data.BotUserLink;
import com.octotask.bot.data.BotUserLinkRepository;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Links a Telegram chat to an OctoTask APP_USER. A user runs:
 *   /login &lt;access-code&gt; &lt;their name&gt;
 * and, if the code matches and the name resolves to an APP_USER, the chat is
 * linked. From then on the bot scopes every query to that identity.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final OctoTaskDataClient data;
    private final BotUserLinkRepository links;
    private final BotLoginCodeRepository loginCodes;

    @Value("${bot.login.access-code:}")
    private String accessCode;

    public LoginService(OctoTaskDataClient data,
                        Optional<BotUserLinkRepository> links,
                        Optional<BotLoginCodeRepository> loginCodes) {
        this.data = data;
        this.links = links.orElse(null);
        this.loginCodes = loginCodes.orElse(null);
    }

    public boolean linkingAvailable() {
        return links != null;
    }

    public boolean isLoggedIn(long chatId) {
        return current(chatId) != null;
    }

    public BotUserLink current(long chatId) {
        return links == null ? null : links.findByChatId(chatId);
    }

    /** Handles a "/login ..." command and returns the text to reply with. */
    public String handleLogin(long chatId, String argsAfterCommand) {
        if (links == null) {
            return "El inicio de sesión no está disponible (base de datos de vínculos no configurada).";
        }
        String args = argsAfterCommand == null ? "" : argsAfterCommand.trim();
        if (args.isEmpty()) {
            return "Para iniciar sesión envía tu código personal:\n/login <tu código>\n" +
                   "Ejemplo: /login ABC234";
        }
        String[] parts = args.split("\\s+", 2);

        // --- Preferred: per-user personal code (single token, no name) ---
        // Removes the shared access code and the impersonation risk of name login.
        if (parts.length == 1 && loginCodes != null) {
            String personalCode = parts[0].trim();
            AppUser byCode = loginCodes.findByCode(personalCode);
            if (byCode != null) {
                links.upsert(new BotUserLink(chatId, byCode.getId(), byCode.getName(), byCode.getTeamId()));
                log.info("Linked chatId={} to appUserId={} ({}) via personal code", chatId, byCode.getId(), byCode.getName());
                return "¡Listo, " + byCode.getName() + "! Tu cuenta quedó vinculada. " +
                       "Ya puedes pedirme tus tareas, KPIs, crear tareas y más.";
            }
            return "Código personal no válido. Pídele a tu administrador tu código de OctoTask " +
                   "y envía:\n/login <tu código>";
        }

        // --- Legacy fallback: shared access code + name ---
        String code = parts[0];
        String name = parts[1].trim();

        if (accessCode == null || accessCode.isBlank()) {
            log.warn("BOT_ACCESS_CODE is not set; refusing all logins");
            return "El bot no tiene configurado un código de acceso. Contacta al administrador.";
        }
        if (!accessCode.equals(code)) {
            return "Código de acceso incorrecto.";
        }

        AppUser user = data.findUserByName(name);
        if (user == null) {
            return "No encontré un usuario llamado \"" + name + "\" en OctoTask. Verifica que sea tu nombre exacto.";
        }

        links.upsert(new BotUserLink(chatId, user.getId(), user.getName(), user.getTeamId()));
        log.info("Linked chatId={} to appUserId={} ({})", chatId, user.getId(), user.getName());
        return "¡Listo, " + user.getName() + "! Tu cuenta quedó vinculada. " +
               "Ya puedes pedirme tus tareas, KPIs, crear tareas y más.";
    }

    public String handleLogout(long chatId) {
        if (links == null) return "No hay sesión que cerrar.";
        links.deleteByChatId(chatId);
        return "Sesión cerrada. Usa /login para volver a vincular tu cuenta.";
    }
}
