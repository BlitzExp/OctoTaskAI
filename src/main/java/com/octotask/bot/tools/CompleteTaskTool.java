package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompleteTaskTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(CompleteTaskTool.class);

    private final OctoTaskDataClient client;

    public CompleteTaskTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "complete_task"; }

    @Override
    public String getDescription() {
        return "Marks a specific task as completed or DONE. Ask the user for the Task ID if they don't provide it.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        parameters.putObject("properties")
                .putObject("taskId").put("type", "INTEGER").put("description", "The numeric ID of the task to complete");
        parameters.putArray("required").add("taskId");
    }

    @Override
    public Object execute(JsonNode arguments) {
        int taskId = arguments.get("taskId").asInt();
        log.info("complete_task taskId={}", taskId);
        client.markTaskCompleted(taskId);
        return "SUCCESS: Task " + taskId + " has been marked as completed.";
    }

    @Override
    public String successMessage(Object result) {
        if (result instanceof String s && s.startsWith("SUCCESS")) {
            String id = s.replaceAll("\\D+", "");
            return "✅ Tarea " + id + " marcada como completada.";
        }
        return null;
    }
}
