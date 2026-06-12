package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetLateTasksTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetLateTasksTool.class);

    private final OctoTaskDataClient client;

    public GetLateTasksTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_late_tasks_by_sprint"; }

    @Override
    public String getDescription() {
        return "Fetches the number of LATE tasks for a specific team in a specific sprint.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        ObjectNode props = parameters.putObject("properties");
        props.putObject("teamId").put("type", "INTEGER").put("description", "The ID of the team");
        props.putObject("sprintNumber").put("type", "INTEGER")
                .put("description", "The sprint number the user refers to, e.g. 1, 2, 3 "
                        + "(Optional — defaults to the team's current sprint)");
        // Only teamId is required (and it comes from the logged-in identity).
        // sprintNumber is optional: omitted → the team's current sprint.
        parameters.putArray("required").add("teamId");
    }

    @Override
    public Object execute(JsonNode arguments) {
        int teamId = arguments.get("teamId").asInt();
        int sprintId = SprintArgs.resolveSprintId(client, teamId, arguments);
        log.info("get_late_tasks_by_sprint teamId={} sprintId={}", teamId, sprintId);
        return client.getLateTasksBySprint(teamId, sprintId);
    }
}
