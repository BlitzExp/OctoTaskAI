package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GetUserTasksTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(GetUserTasksTool.class);

    private final OctoTaskDataClient client;

    public GetUserTasksTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "get_user_tasks"; }

    @Override
    public String getDescription() {
        return "Fetches all tasks assigned to a specific user by their name.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        parameters.putObject("properties")
                .putObject("userName").put("type", "STRING").put("description", "The first name or full name of the user");
        parameters.putArray("required").add("userName");
    }

    @Override
    public Object execute(JsonNode arguments) {
        String userName = arguments.get("userName").asText();
        log.info("get_user_tasks userName={}", userName);
        return client.getTasksByUserName(userName);
    }
}
