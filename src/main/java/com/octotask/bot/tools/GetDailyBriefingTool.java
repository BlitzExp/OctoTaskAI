package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A personal daily briefing: aggregates the user's single most important task,
 * their pending/overdue counts, and a quick KPI snapshot into one compact digest.
 *
 * <p>It composes existing data methods in Java and renders the result with its
 * own {@link #successMessage} — so it is fast and deterministic and never goes
 * through the local LLM. This is deliberate: a pre-aggregated digest avoids both
 * the 30s phrasing timeout and any chance of the small model confabulating.
 */
@Component
public class GetDailyBriefingTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetDailyBriefingTool.class);

    private final OctoTaskDataClient client;

    public GetDailyBriefingTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_daily_briefing"; }

    @Override
    public String getDescription() {
        return "A personal daily briefing/digest: the single most important task to start on, " +
               "how many tasks are pending and overdue, and a quick KPI snapshot. " +
               "Use for vague 'what should I do today / catch me up / give me a summary' requests.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        parameters.putObject("properties")
                .putObject("userName").put("type", "STRING").put("description", "The name of the user");
        parameters.putArray("required").add("userName");
    }

    @Override
    public Object execute(JsonNode arguments) {
        String userName = arguments.get("userName").asText();
        log.info("get_daily_briefing userName={}", userName);
        Task top = client.getTopPriorityTask(userName);
        List<Task> pending = client.getPendingTasksByUserName(userName);
        Map<String, Object> kpis = client.getUserKpis(userName);
        return new Briefing(userName, top, pending == null ? List.of() : pending, kpis);
    }

    /** Deterministic, mobile-friendly digest — never depends on the LLM. */
    @Override
    public String successMessage(Object result) {
        if (!(result instanceof Briefing b)) return null;

        int pendingCount = b.pending().size();
        int overdue = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (Task t : b.pending()) {
            // Tasks carry no own due-date; the sprint's end is the best proxy
            // (same field get_top_priority_task tie-breaks on).
            if (t.getSprintEndDate() != null && t.getSprintEndDate().isBefore(now)) overdue++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Resumen de ").append(b.userName()).append("\n");

        if (b.top() != null) {
            Task t = b.top();
            sb.append("🔝 Empieza por: #").append(t.getID()).append(" «").append(t.getName()).append("»");
            if (t.getPriorityID() > 0) sb.append(" · prioridad ").append(t.getPriorityID());
            if (t.getSprintNumber() > 0) sb.append(" · sprint ").append(t.getSprintNumber());
            sb.append("\n");
        } else {
            sb.append("🎉 No tienes tareas pendientes.\n");
        }

        sb.append("📌 Pendientes: ").append(pendingCount);
        if (overdue > 0) sb.append("   ⚠️ Vencidas: ").append(overdue);
        sb.append("\n");

        Long completed = num(b.kpis(), "completed_tasks");
        Long total = num(b.kpis(), "total_tasks");
        Double hours = dbl(b.kpis(), "total_hours_spent");
        if (total != null && total > 0) {
            sb.append("📊 Completadas: ").append(completed == null ? 0 : completed).append("/").append(total);
            if (hours != null && hours > 0) sb.append(" · horas: ").append(trimNum(hours));
        }
        return sb.toString().trim();
    }

    // getUserKpis() returns a JDBC row whose keys Oracle upper-cases; resolve
    // them case-insensitively so we don't depend on the exact casing.
    private static Long num(Map<String, Object> m, String key) {
        Object v = ci(m, key);
        return v instanceof Number n ? n.longValue() : null;
    }

    private static Double dbl(Map<String, Object> m, String key) {
        Object v = ci(m, key);
        return v instanceof Number n ? n.doubleValue() : null;
    }

    private static Object ci(Map<String, Object> m, String key) {
        if (m == null) return null;
        for (Map.Entry<String, Object> e : m.entrySet())
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) return e.getValue();
        return null;
    }

    private static String trimNum(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }

    /** Pre-aggregated payload handed to {@link #successMessage}. */
    record Briefing(String userName, Task top, List<Task> pending, Map<String, Object> kpis) {}
}
