package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTeamKpisTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetTeamKpisTool.class);

    private final OctoTaskDataClient client;

    public GetTeamKpisTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_team_kpis"; }

    @Override
    public String getDescription() {
        return "Fetches overall statistics, analytics, and KPIs for a specific team " +
               "(total, completed, pending, late tasks, and averages).";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        parameters.putObject("properties")
                .putObject("teamId").put("type", "INTEGER").put("description", "The numeric ID of the team");
        parameters.putArray("required").add("teamId");
    }

    @Override
    public Object execute(JsonNode arguments) {
        int teamId = arguments.get("teamId").asInt();
        log.info("get_team_kpis teamId={}", teamId);
        return client.getTeamKpis(teamId);
    }
}
