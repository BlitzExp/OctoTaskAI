package com.octotask.bot.data;

/** A semantic route plus its cosine distance to the query (0 = identical, 2 = opposite). */
public class RouteMatch {
    private final SemanticRoute route;
    private final double distance;

    public RouteMatch(SemanticRoute route, double distance) {
        this.route = route;
        this.distance = distance;
    }

    public SemanticRoute getRoute() { return route; }
    public double getDistance() { return distance; }

    /** Cosine similarity in [-1, 1]; for normalized embeddings, 1 - distance. */
    public double getSimilarity() { return 1.0 - distance; }
}
