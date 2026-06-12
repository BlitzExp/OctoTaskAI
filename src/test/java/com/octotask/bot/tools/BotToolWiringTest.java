package com.octotask.bot.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.octotask.bot.data.OctoTaskDataClient;
import com.octotask.bot.data.model.CreateTask;
import com.octotask.bot.data.model.Task;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
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
    void completeTaskToolRecordsHoursAndReturnsSuccessMessage() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.markTaskCompleted(42, new BigDecimal("3"))).thenReturn(1);
        CompleteTaskTool tool = new CompleteTaskTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("taskId", 42);
        args.put("hoursWorked", 3);
        Object result = tool.execute(args);

        Mockito.verify(client).markTaskCompleted(42, new BigDecimal("3"));
        assertThat(tool.successMessage(result)).contains("42").contains("completada").contains("3 h");
    }

    @Test
    void createTaskDefaultsToTeamsLatestSprintWhenNotProvided() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.getLatestSprintIdForUser(7)).thenReturn(9);
        Mockito.when(client.createTask(Mockito.any(CreateTask.class))).thenReturn(new Task());
        CreateTaskTool tool = new CreateTaskTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("name", "Arreglar login");
        args.put("description", "El login truena con SSO");
        args.put("assigneeId", 7);
        args.put("priority", 1);
        // no sprintId -> should fall back to the team's latest sprint
        tool.execute(args);

        ArgumentCaptor<CreateTask> captor = ArgumentCaptor.forClass(CreateTask.class);
        Mockito.verify(client).createTask(captor.capture());
        assertThat(captor.getValue().getSprintId()).isEqualTo(9);
        Mockito.verify(client).getLatestSprintIdForUser(7);
    }

    @Test
    void createTaskUsesExplicitSprintIdWhenProvided() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.createTask(Mockito.any(CreateTask.class))).thenReturn(new Task());
        CreateTaskTool tool = new CreateTaskTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("name", "Arreglar login");
        args.put("description", "El login truena con SSO");
        args.put("assigneeId", 7);
        args.put("priority", 1);
        args.put("sprintId", 3);
        tool.execute(args);

        ArgumentCaptor<CreateTask> captor = ArgumentCaptor.forClass(CreateTask.class);
        Mockito.verify(client).createTask(captor.capture());
        assertThat(captor.getValue().getSprintId()).isEqualTo(3);
        Mockito.verify(client, Mockito.never()).getLatestSprintIdForUser(Mockito.anyInt());
    }

    @Test
    void lateTasksToolDefaultsToTeamsCurrentSprintWhenNoNumberGiven() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.getLatestSprintIdForTeam(7)).thenReturn(9);
        Mockito.when(client.getLateTasksBySprint(7, 9)).thenReturn(4);
        GetLateTasksTool tool = new GetLateTasksTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("teamId", 7); // sprintNumber omitted -> current sprint
        tool.execute(args);

        Mockito.verify(client).getLatestSprintIdForTeam(7);
        Mockito.verify(client).getLateTasksBySprint(7, 9);
    }

    @Test
    void lateTasksToolTranslatesSprintNumberToId() throws Exception {
        OctoTaskDataClient client = Mockito.mock(OctoTaskDataClient.class);
        Mockito.when(client.getSprintIdByNumber(7, 3)).thenReturn(15);
        Mockito.when(client.getLateTasksBySprint(7, 15)).thenReturn(2);
        GetLateTasksTool tool = new GetLateTasksTool(client);

        ObjectNode args = mapper.createObjectNode();
        args.put("teamId", 7);
        args.put("sprintNumber", 3); // user says "sprint 3" -> id 15
        tool.execute(args);

        Mockito.verify(client).getSprintIdByNumber(7, 3);
        Mockito.verify(client).getLateTasksBySprint(7, 15);
        Mockito.verify(client, Mockito.never()).getLatestSprintIdForTeam(Mockito.anyInt());
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
