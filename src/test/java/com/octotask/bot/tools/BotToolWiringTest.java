package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.Task;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-Java unit test: instantiate every tool against a mocked data client,
 * make sure their schemas are well-formed and execute() round-trips through
 * the mocked client.
 */
class BotToolWiringTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void everyToolBuildsAValidParametersSchema() {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        List<BotTool> tools = List.of(
                new GetTeamTasksTool(client),
                new GetTeamMembersTool(client),
                new GetLateTasksTool(client),
                new GetUserTasksTool(client),
                new CreateTaskTool(client),
                new CompleteTaskTool(client),
                new GetPendingTasksTool(client),
                new GetTopPriorityTaskTool(client),
                new GetUserKpisTool(client),
                new GetTeamKpisTool(client),
                new GetSprintAnalyticsTool(client),
                new GetDailyBriefingTool(client)
        );

        for (BotTool tool : tools) {
            ObjectNode params = mapper.createObjectNode();
            tool.buildParameters(params);
            assertThat(params.get("type").asText()).isEqualTo("OBJECT");
            assertThat(params.has("properties")).isTrue();
            assertThat(params.has("required")).isTrue();
            assertThat(params.get("required").size()).isGreaterThan(0);
        }
    }

    @Test
    void getTeamTasksToolDelegatesToClient() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.getTasksByTeamId(7)).thenReturn(List.of(new Task()));
        GetTeamTasksTool tool = new GetTeamTasksTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("teamId", 7);
        Object result = tool.execute(args);

        Mockito.verify(client).getTasksByTeamId(7);
        assertThat(result).isInstanceOf(List.class);
    }

    @Test
    void completeTaskToolReturnsSuccessMessage() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.markTaskCompleted(42)).thenReturn(1);
        CompleteTaskTool tool = new CompleteTaskTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("taskId", 42);
        Object result = tool.execute(args);

        assertThat(result.toString()).contains("42").contains("completed");
    }

    @Test
    void getUserKpisToolPassesUserName() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.getUserKpis("alice")).thenReturn(new HashMap<>());
        GetUserKpisTool tool = new GetUserKpisTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("userName", "alice");
        tool.execute(args);

        Mockito.verify(client).getUserKpis("alice");
    }
}
