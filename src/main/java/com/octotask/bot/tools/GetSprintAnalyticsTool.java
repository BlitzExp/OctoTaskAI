package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetSprintAnalyticsTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetSprintAnalyticsTool.class);

    private final OctoTaskDataClient client;

    public GetSprintAnalyticsTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_sprint_analytics"; }

    @Override
    public String getDescription() {
        return "Fetches deep analytics for a specific sprint, showing exactly how many hours each member worked and their completed/late tasks.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        ObjectNode props = parameters.putObject("properties");
        props.putObject("teamId").put("type", "INTEGER").put("description", "The numeric ID of the team");
        props.putObject("sprintNumber").put("type", "INTEGER")
                .put("description", "The sprint number the user refers to, e.g. 1, 2, 3 "
                        + "(Optional — defaults to the team's current sprint)");
        // Only teamId is required (injected from identity); sprintNumber is
        // optional and defaults to the team's current sprint.
        parameters.putArray("required").add("teamId");
    }

    @Override
    public Object execute(JsonNode arguments) {
        int teamId = arguments.get("teamId").asInt();
        int sprintId = SprintArgs.resolveSprintId(client, teamId, arguments);
        log.info("get_sprint_analytics teamId={} sprintId={}", teamId, sprintId);
        return client.getSprintAnalytics(teamId, sprintId);
    }
}
