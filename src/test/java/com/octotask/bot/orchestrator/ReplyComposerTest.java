package com.octotask.bot.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.octotask.bot.data.model.Task;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the deterministic (no-LLM) phrasing path: large result sets are
 * paginated and rendered without ever calling the local model — the fix for the
 * 30s Ollama read-timeouts on big lists. Constructed with {@code Optional.empty()}
 * so there is no LLM, forcing the deterministic formatter.
 */
class ReplyComposerTest {

    private final ReplyComposer composer = new ReplyComposer(new ObjectMapper(), Optional.empty());

    @Test
    void largeListIsPaginatedAndRenderedCleanly() {
        List<Task> tasks = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            Task t = new Task();
            t.setID(i);
            t.setName("Tarea " + i);
            t.setPriorityID(1);
            t.setSprintNumber(3);
            tasks.add(t);
        }

        String out = composer.compose("dime todas mis tareas", tasks, null);

        assertThat(out).contains("25 resultados");      // header with the true total
        assertThat(out).contains("#1 Tarea 1");          // first item, task-style line
        assertThat(out).contains("prioridad 1");         // compact task tags
        assertThat(out).contains("y 15 más");            // 25 - page size (10) collapsed
        assertThat(out).doesNotContain("\"name\"");      // not a raw JSON dump
    }

    @Test
    void emptyListSaysNoResults() {
        assertThat(composer.compose("mis tareas", List.of(), null))
                .isEqualTo("No encontré resultados.");
    }

    @Test
    void smallListWithoutLlmStillRendersDeterministically() {
        Task t = new Task();
        t.setID(7);
        t.setName("Arreglar login");
        String out = composer.compose("mis tareas", List.of(t), null);

        assertThat(out).contains("1 resultado:");
        assertThat(out).contains("#7 Arreglar login");
    }
}
