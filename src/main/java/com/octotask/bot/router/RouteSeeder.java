package com.octotask.bot.router;

import com.octotask.bot.ai.EmbeddingService;
import com.octotask.bot.data.SemanticRoutingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-time seeder: populates {@code rutas_semanticas} with example phrasings for
 * each bot tool (funcion_backend = the tool's name). The semantic router matches
 * a user's message against these to choose a tool. Runs only when the table is
 * empty, so it is safe to leave enabled.
 *
 * Enable with bot.router.seed-on-startup=true (BOT_ROUTER_SEED=true).
 */
@Component
@ConditionalOnBean(SemanticRoutingRepository.class)
@ConditionalOnProperty(prefix = "bot.router", name = "seed-on-startup", havingValue = "true")
public class RouteSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RouteSeeder.class);

    private final EmbeddingService embeddings;
    private final SemanticRoutingRepository repo;

    /** When true, delete all existing routes before seeding (clears stale/polluted rows). */
    @Value("${bot.router.reset:false}")
    private boolean reset;

    public RouteSeeder(EmbeddingService embeddings, SemanticRoutingRepository repo) {
        this.embeddings = embeddings;
        this.repo = repo;
    }

    /** tool name -> example phrasings (Spanish + English). */
    private static final Map<String, List<String>> SEED = new LinkedHashMap<>() {{
        put("get_user_tasks", List.of(
                "muéstrame mis tareas", "cuáles son mis tareas", "ver todas mis tareas",
                "show my tasks", "list all my tasks"));
        put("get_pending_tasks", List.of(
                "qué tengo pendiente", "mis tareas pendientes", "qué tareas tengo pendientes",
                "qué tareas tengo pendiente", "tareas sin terminar", "qué me falta por hacer",
                "what do I have pending", "my pending tasks"));
        put("get_top_priority_task", List.of(
                "qué hago primero", "cuál es mi tarea más importante", "en qué debería trabajar ahora",
                "what should I work on next", "my highest priority task"));
        put("get_user_kpis", List.of(
                "mis estadísticas", "mis kpis", "muéstrame mis kpis", "muéstrame mis estadísticas",
                "ver mis kpis", "mis indicadores", "cómo voy", "cuántas horas he trabajado",
                "my stats", "my personal kpis", "show my kpis"));
        put("get_team_tasks", List.of(
                "tareas del equipo", "qué está haciendo el equipo", "ver las tareas del equipo",
                "team tasks", "what is the team working on"));
        put("get_team_kpis", List.of(
                "estadísticas del equipo", "kpis del equipo", "cómo va el equipo",
                "team kpis", "team statistics"));
        put("get_team_members", List.of(
                "quiénes están en mi equipo", "miembros del equipo", "lista de mi equipo",
                "team members", "who is on my team"));
        put("get_late_tasks_by_sprint", List.of(
                "cuántas tareas atrasadas hay en el sprint", "tareas tarde del sprint",
                "late tasks in the sprint", "how many late tasks this sprint"));
        put("get_sprint_analytics", List.of(
                "analítica del sprint", "cómo trabajó cada miembro en el sprint", "horas por miembro del sprint",
                "sprint analytics", "per-member breakdown for the sprint"));
        put("create_task", List.of(
                "crear una tarea", "nueva tarea", "agregar tarea", "quiero crear una tarea",
                "crea una tarea llamada con descripción asignada a un usuario en un sprint con prioridad",
                "registra una nueva tarea con nombre descripción sprint y prioridad",
                "dar de alta una tarea para un usuario en el sprint",
                "create a task", "add a new task",
                "create a task named with a description assigned to a user in a sprint with priority"));
        put("complete_task", List.of(
                "marcar tarea como completada", "completar tarea", "ya terminé la tarea",
                "marca la tarea número como completada", "cierra la tarea con id",
                "mark a task as done", "complete task", "mark task id as done"));
    }};

    @Override
    public void run(ApplicationArguments args) {
        if (reset) {
            try {
                int deleted = repo.deleteAll();
                log.info("bot.router.reset=true → deleted {} existing routes before reseeding", deleted);
            } catch (Exception e) {
                log.warn("Route reset failed; continuing: {}", e.getMessage());
            }
        }
        try {
            long existing = repo.count();
            if (existing > 0) {
                log.info("rutas_semanticas already has {} rows; skipping seed", existing);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check rutas_semanticas count; skipping seed: {}", e.getMessage());
            return;
        }

        int inserted = 0;
        for (Map.Entry<String, List<String>> entry : SEED.entrySet()) {
            String tool = entry.getKey();
            for (String phrase : entry.getValue()) {
                try {
                    float[] vec = embeddings.embed(List.of(phrase)).get(0);
                    repo.insertRoute(phrase, vec, tool);
                    inserted++;
                } catch (Exception e) {
                    log.error("Failed to seed route '{}' -> {}", phrase, tool, e);
                }
            }
        }
        log.info("Seeded {} semantic routes across {} tools", inserted, SEED.size());
    }
}
