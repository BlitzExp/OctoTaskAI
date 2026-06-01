package com.octotask.bot.router;

import com.octotask.bot.ai.EmbeddingService;
import com.octotask.bot.data.RouteMatch;
import com.octotask.bot.data.SemanticRoutingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Decides which tool a free-text message maps to, using local embeddings +
 * vector search over the seeded {@code rutas_semanticas} routes. Replaces the
 * cloud LLM's function-selection role.
 */
@Service
public class SemanticRouter {

    private static final Logger log = LoggerFactory.getLogger(SemanticRouter.class);

    private final EmbeddingService embeddings;
    private final SemanticRoutingRepository repo;

    @Value("${bot.router.min-similarity:0.45}")
    private double minSimilarity;

    public SemanticRouter(EmbeddingService embeddings, Optional<SemanticRoutingRepository> repo) {
        this.embeddings = embeddings;
        this.repo = repo.orElse(null);
    }

    public boolean available() {
        return repo != null;
    }

    /** The chosen tool name + confidence, or empty if nothing is confident enough. */
    public Optional<Decision> route(String userText) {
        if (repo == null) return Optional.empty();
        try {
            float[] vector = embeddings.embed(List.of(userText)).get(0);
            List<RouteMatch> matches = repo.searchWithDistance(vector, 1);
            if (matches.isEmpty()) return Optional.empty();
            RouteMatch best = matches.get(0);
            double similarity = best.getSimilarity();
            String tool = best.getRoute().getFuncionBackend();
            log.info("Router: text='{}' -> tool={} similarity={}", userText, tool, String.format("%.3f", similarity));
            if (similarity < minSimilarity) {
                return Optional.empty();
            }
            return Optional.of(new Decision(tool, similarity, best.getRoute().getDescripcionTexto()));
        } catch (Exception e) {
            log.error("Routing failed for text='{}'", userText, e);
            return Optional.empty();
        }
    }

    public static class Decision {
        private final String toolName;
        private final double similarity;
        private final String matchedExample;

        public Decision(String toolName, double similarity, String matchedExample) {
            this.toolName = toolName;
            this.similarity = similarity;
            this.matchedExample = matchedExample;
        }

        public String getToolName() { return toolName; }
        public double getSimilarity() { return similarity; }
        public String getMatchedExample() { return matchedExample; }
    }
}
