package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.CreateTask;
import com.octotask.bot.data.model.Task;
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
        props.putObject("sprintId").put("type", "INTEGER")
                .put("description", "The Sprint ID this task belongs to (Optional — defaults to the team's current sprint)");
        props.putObject("priority").put("type", "INTEGER").put("description", "The priority level (e.g., 1, 2, 3)");
        props.putObject("estimatedHours").put("type", "NUMBER").put("description", "Estimated hours to complete (Optional)");
        // sprintId is intentionally NOT required: when omitted we assign the task
        // to the assignee's team's most recent sprint instead of asking the user.
        parameters.putArray("required").add("name").add("description").add("assigneeId").add("priority");
    }

    @Override
    public Object execute(JsonNode arguments) {
        CreateTask task = new CreateTask();
        task.setName(arguments.get("name").asText());
        task.setDescription(arguments.get("description").asText());
        task.setAssigneeId(arguments.get("assigneeId").asInt());
        task.setSprintId(resolveSprintId(arguments));
        task.setPriority(arguments.get("priority").asInt());
        if (arguments.has("estimatedHours")) {
            task.setEstimatedHours(new BigDecimal(arguments.get("estimatedHours").asText()));
        }
        log.info("create_task name={} assigneeId={} sprintId={}", task.getName(), task.getAssigneeId(), task.getSprintId());
        return client.createTask(task);
    }

    /**
     * Use the sprint the user explicitly named, otherwise default to the
     * assignee's team's most recent sprint so the user never has to know IDs.
     */
    private int resolveSprintId(JsonNode arguments) {
        if (arguments.has("sprintId") && !arguments.get("sprintId").isNull()) {
            return arguments.get("sprintId").asInt();
        }
        Integer latest = client.getLatestSprintIdForUser(arguments.get("assigneeId").asInt());
        if (latest == null) {
            throw new IllegalStateException("No encontré un sprint para tu equipo; pídele a tu líder que cree uno.");
        }
        return latest;
    }

    @Override
    public String successMessage(Object result) {
        if (result instanceof Task t) {
            StringBuilder sb = new StringBuilder("✅ Tarea creada: #").append(t.getID())
                    .append(" «").append(t.getName()).append("»\n")
                    .append("• Sprint: ").append(t.getSprintID()).append("\n")
                    .append("• Prioridad: ").append(t.getPriorityID()).append("\n")
                    .append("• Asignada a: ")
                    .append(t.getUserName() != null ? t.getUserName() : t.getUserID());
            if (t.getDescription() != null && !t.getDescription().isBlank()) {
                sb.append("\n• Descripción: ").append(t.getDescription());
            }
            return sb.toString();
        }
        return null;
    }
}
