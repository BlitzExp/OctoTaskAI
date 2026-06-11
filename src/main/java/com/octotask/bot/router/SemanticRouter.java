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

    // If the best route beats the next-best (different) tool by less than this,
    // the intent is ambiguous → ask the user which they meant.
    @Value("${bot.router.ambiguity-margin:0.06}")
    private double ambiguityMargin = 0.06;

    // Below this absolute similarity the winner is weak → confirm before acting.
    @Value("${bot.router.confirm-below:0.58}")
    private double confirmBelow = 0.58;

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

    /**
     * Like {@link #route} but graded into confidence bands so the orchestrator
     * can disambiguate or confirm instead of silently guessing:
     * <ul>
     *   <li>{@code NONE} — nothing cleared the minimum similarity.</li>
     *   <li>{@code AMBIGUOUS} — two different tools are within
     *       {@code ambiguity-margin} of each other → ask which.</li>
     *   <li>{@code LOW_CONFIDENCE} — the winner is below {@code confirm-below}
     *       → confirm before acting.</li>
     *   <li>{@code CONFIDENT} — act directly.</li>
     * </ul>
     */
    public Routing classify(String userText) {
        if (repo == null) return Routing.none();
        try {
            float[] vector = embeddings.embed(List.of(userText)).get(0);
            List<RouteMatch> matches = repo.searchWithDistance(vector, 5);
            if (matches.isEmpty()) return Routing.none();

            RouteMatch best = matches.get(0);
            double sBest = best.getSimilarity();
            String bestTool = best.getRoute().getFuncionBackend();
            log.info("Router: text='{}' -> tool={} similarity={}", userText, bestTool, String.format("%.3f", sBest));

            if (sBest < minSimilarity) return Routing.none();
            Decision bestD = new Decision(bestTool, sBest, best.getRoute().getDescripcionTexto());

            // Best alternative that maps to a *different* tool (skip duplicate
            // seeds of the same tool sitting in positions 2..k).
            RouteMatch alt = null;
            for (int i = 1; i < matches.size(); i++) {
                if (!matches.get(i).getRoute().getFuncionBackend().equals(bestTool)) {
                    alt = matches.get(i);
                    break;
                }
            }
            if (alt != null) {
                double sAlt = alt.getSimilarity();
                if (sAlt >= minSimilarity && (sBest - sAlt) < ambiguityMargin) {
                    Decision altD = new Decision(alt.getRoute().getFuncionBackend(), sAlt,
                            alt.getRoute().getDescripcionTexto());
                    return Routing.ambiguous(bestD, altD);
                }
            }
            if (sBest < confirmBelow) return Routing.lowConfidence(bestD);
            return Routing.confident(bestD);
        } catch (Exception e) {
            log.error("Routing failed for text='{}'", userText, e);
            return Routing.none();
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

    /** A graded routing outcome: see {@link #classify}. */
    public static final class Routing {
        public enum Kind { CONFIDENT, AMBIGUOUS, LOW_CONFIDENCE, NONE }

        private final Kind kind;
        private final Decision best;
        private final Decision alternative;

        private Routing(Kind kind, Decision best, Decision alternative) {
            this.kind = kind;
            this.best = best;
            this.alternative = alternative;
        }

        public static Routing none() { return new Routing(Kind.NONE, null, null); }
        public static Routing confident(Decision d) { return new Routing(Kind.CONFIDENT, d, null); }
        public static Routing lowConfidence(Decision d) { return new Routing(Kind.LOW_CONFIDENCE, d, null); }
        public static Routing ambiguous(Decision best, Decision alt) { return new Routing(Kind.AMBIGUOUS, best, alt); }

        public Kind getKind() { return kind; }
        public Decision getBest() { return best; }
        public Decision getAlternative() { return alternative; }
    }
}
