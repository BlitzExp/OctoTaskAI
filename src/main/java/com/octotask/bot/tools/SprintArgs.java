package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.octotask.bot.data.OctoTaskDataClient;

/**
 * Shared resolution of the sprint a team-scoped tool should operate on.
 *
 * <p>Users speak in sprint <em>numbers</em> ("sprint 3"), never the internal id.
 * When a {@code sprintNumber} is supplied we translate it to the id within the
 * team; when it is omitted we default to the team's current (most recent) sprint
 * so the bot never has to ask for an opaque id.
 */
final class SprintArgs {

    private SprintArgs() {}

    static int resolveSprintId(OctoTaskDataClient client, int teamId, JsonNode arguments) {
        if (arguments.has("sprintNumber") && !arguments.get("sprintNumber").isNull()) {
            int number = arguments.get("sprintNumber").asInt();
            Integer id = client.getSprintIdByNumber(teamId, number);
            if (id == null) {
                throw new IllegalStateException("No encontré el sprint " + number + " en tu equipo.");
            }
            return id;
        }
        Integer latest = client.getLatestSprintIdForTeam(teamId);
        if (latest == null) {
            throw new IllegalStateException("Tu equipo aún no tiene sprints.");
        }
        return latest;
    }
}
