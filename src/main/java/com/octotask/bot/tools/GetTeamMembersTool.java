package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTeamMembersTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetTeamMembersTool.class);

    private final OctoTaskDataClient client;

    public GetTeamMembersTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_team_members"; }

    @Override
    public String getDescription() {
        return "Gets a list of all users or members belonging to a specific team.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        parameters.putObject("properties")
                .putObject("teamId").put("type", "INTEGER").put("description", "The ID of the team");
        parameters.putArray("required").add("teamId");
    }

    @Override
    public Object execute(JsonNode arguments) {
        long teamId = arguments.get("teamId").asLong();
        log.info("get_team_members teamId={}", teamId);
        return client.getTeamMembers(teamId);
    }
}
