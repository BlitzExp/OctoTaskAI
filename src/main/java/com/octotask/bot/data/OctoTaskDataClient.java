package com.octotask.bot.data;

import com.octotask.bot.data.model.AppUser;
import com.octotask.bot.data.model.CreateTask;
import com.octotask.bot.data.model.Task;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Data access contract used by every bot tool. The current implementation
 * talks JDBC directly to the OctoTask Oracle schema; a future HTTP impl can
 * replace this without touching any tool.
 */
public interface OctoTaskDataClient {

    // --- Identity ---
    /** Look up an APP_USER by (case-insensitive) name for login linking; null if not found. */
    AppUser findUserByName(String name);

    /** All APP_USERs, used to seed per-user login codes. */
    List<AppUser> listUsers();

    // --- Reads ---
    List<Task> getTasksByTeamId(int teamId);
    List<Task> getTasksByUserName(String userName);
    List<Task> getPendingTasksByUserName(String userName);
    Task       getTopPriorityTask(String userName);

    /**
     * The id of the most recent sprint of the team the given user belongs to,
     * or {@code null} if the user's team has no sprints. Used so creating a task
     * defaults to the team's current sprint instead of asking the user for it.
     */
    Integer    getLatestSprintIdForUser(int userId);

    List<Map<String, Object>> getTeamMembers(long teamId);

    int        getLateTasksBySprint(int teamId, int sprintId);
    Map<String, Object> getTeamKpis(int teamId);
    Map<String, Object> getSprintAnalytics(int teamId, int sprintId);
    Map<String, Object> getUserKpis(String userName);

    // --- Writes ---
    Task createTask(CreateTask data);

    /**
     * Mark a task DONE and record the hours the user spent on it.
     * @return the number of rows updated (1 on success).
     */
    int  markTaskCompleted(int taskId, BigDecimal hoursWorked);
}
