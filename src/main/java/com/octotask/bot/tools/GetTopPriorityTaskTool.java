package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetTopPriorityTaskTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetTopPriorityTaskTool.class);

    private final OctoTaskDataClient client;

    public GetTopPriorityTaskTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_top_priority_task"; }

    @Override
    public String getDescription() {
        return "Recommends the single most important task the user should work on right now based on priority.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        parameters.putObject("properties")
                .putObject("userName").put("type", "STRING").put("description", "The name of the user");
        parameters.putArray("required").add("userName");
    }

    @Override
    public Object execute(JsonNode arguments) {
        String userName = arguments.get("userName").asText();
        log.info("get_top_priority_task userName={}", userName);
        Task top = client.getTopPriorityTask(userName);
        return top != null ? top : "You have no pending tasks!";
    }
}
