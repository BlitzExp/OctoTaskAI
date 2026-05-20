package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetPendingTasksTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetPendingTasksTool.class);

    private final OctoTaskDataClient client;

    public GetPendingTasksTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_pending_tasks"; }

    @Override
    public String getDescription() {
        return "Fetches ONLY the pending or incomplete tasks for a specific user. Use this when the user asks what they need to do.";
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
        log.info("get_pending_tasks userName={}", userName);
        return client.getPendingTasksByUserName(userName);
    }
}
