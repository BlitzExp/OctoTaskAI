package com.octotask.bot.data;

import com.octotask.bot.data.model.CreateTask;
import com.octotask.bot.data.model.Task;

import java.util.List;
import java.util.Map;

/**
 * Data access contract used by every bot tool. The current implementation
 * talks JDBC directly to the OctoTask Oracle schema; a future HTTP impl can
 * replace this without touching any tool.
 */
public interface OctoTaskDataClient {

    // --- Reads ---
    List<Task> getTasksByTeamId(int teamId);
    List<Task> getTasksByUserName(String userName);
    List<Task> getPendingTasksByUserName(String userName);
    Task       getTopPriorityTask(String userName);

    List<Map<String, Object>> getTeamMembers(long teamId);

    int        getLateTasksBySprint(int teamId, int sprintId);
    Map<String, Object> getTeamKpis(int teamId);
    Map<String, Object> getSprintAnalytics(int teamId, int sprintId);
    Map<String, Object> getUserKpis(String userName);

    // --- Writes ---
    Task createTask(CreateTask data);
    int  markTaskCompleted(int taskId);
}
