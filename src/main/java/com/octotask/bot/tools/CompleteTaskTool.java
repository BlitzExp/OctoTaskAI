package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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
        return "Marks a specific task as completed or DONE and records the hours worked on it. " +
                "Ask the user for the Task ID and the hours they worked if they don't provide them.";
    }

    @Override
    public void buildParameters(ObjectNode parameters) {
        parameters.put("type", "OBJECT");
        ObjectNode props = parameters.putObject("properties");
        props.putObject("taskId").put("type", "INTEGER").put("description", "The numeric ID of the task to complete");
        props.putObject("hoursWorked").put("type", "NUMBER")
                .put("description", "The number of hours the user spent working on the task");
        parameters.putArray("required").add("taskId").add("hoursWorked");
    }

    @Override
    public Object execute(JsonNode arguments) {
        int taskId = arguments.get("taskId").asInt();
        BigDecimal hoursWorked = new BigDecimal(arguments.get("hoursWorked").asText());
        log.info("complete_task taskId={} hoursWorked={}", taskId, hoursWorked);
        client.markTaskCompleted(taskId, hoursWorked);
        return new CompletionResult(taskId, hoursWorked);
    }

    @Override
    public String successMessage(Object result) {
        if (result instanceof CompletionResult r) {
            return "✅ Tarea " + r.taskId() + " marcada como completada (" + trimNum(r.hoursWorked()) + " h registradas).";
        }
        return null;
    }

    /** Strip trailing zeros so 3.0 -> "3" and 3.50 -> "3.5". */
    private static String trimNum(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }

    /** Result of a completion: the task and the hours recorded against it. */
    public record CompletionResult(int taskId, BigDecimal hoursWorked) {}
}
