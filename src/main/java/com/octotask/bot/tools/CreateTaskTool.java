package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.CreateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CreateTaskTool implements BotTool {

    private static final Logger log = LoggerFactory.getLogger(CreateTaskTool.class);

    private final OctoTaskDataClient client;

    public CreateTaskTool(OctoTaskDataClient client) {
        this.client = client;
    }

    @Override
    public String getName() { return "create_task"; }

    @Override
    public String getDescription() {
        return "Creates a new task in the database. If you are missing required information, ask the user for it before calling this tool.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        ObjectNode props = parameters.putObject("properties");
        props.putObject("name").put("type", "STRING").put("description", "A short title for the task");
        props.putObject("description").put("type", "STRING").put("description", "A detailed description of the task");
        props.putObject("assigneeId").put("type", "INTEGER").put("description", "The User ID of the person this task is assigned to");
        props.putObject("sprintId").put("type", "INTEGER").put("description", "The Sprint ID this task belongs to");
        props.putObject("priority").put("type", "INTEGER").put("description", "The priority level (e.g., 1, 2, 3)");
        props.putObject("estimatedHours").put("type", "NUMBER").put("description", "Estimated hours to complete (Optional)");
        parameters.putArray("required").add("name").add("description").add("assigneeId").add("sprintId").add("priority");
    }

    @Override
    public Object execute(JsonNode arguments) {
        CreateTask task = new CreateTask();
        task.setName(arguments.get("name").asText());
        task.setDescription(arguments.get("description").asText());
        task.setAssigneeId(arguments.get("assigneeId").asInt());
        task.setSprintId(arguments.get("sprintId").asInt());
        task.setPriority(arguments.get("priority").asInt());
        if (arguments.has("estimatedHours")) {
            task.setEstimatedHours(new BigDecimal(arguments.get("estimatedHours").asText()));
        }
        log.info("create_task name={} assigneeId={} sprintId={}", task.getName(), task.getAssigneeId(), task.getSprintId());
        return client.createTask(task);
    }
}
