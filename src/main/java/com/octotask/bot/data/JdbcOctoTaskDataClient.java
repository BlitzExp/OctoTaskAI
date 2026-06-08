package com.octotask.bot.data;

import com.octotask.bot.data.mapper.TaskRowMapper;
import com.octotask.bot.data.model.AppUser;
import com.octotask.bot.data.model.CreateTask;
import com.octotask.bot.data.model.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JdbcOctoTaskDataClient implements OctoTaskDataClient {

    private final JdbcTemplate jdbc;

    public JdbcOctoTaskDataClient(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // -----------------------------------------------------------------
    // Identity
    // -----------------------------------------------------------------

    @Override
    public AppUser findUserByName(String name) {
        String sql = "SELECT id, name, team_id FROM APP_USER WHERE LOWER(name) = LOWER(?) FETCH FIRST 1 ROWS ONLY";
        List<AppUser> users = jdbc.query(sql, (rs, n) -> {
            int teamId = rs.getInt("team_id");
            Integer team = rs.wasNull() ? null : teamId;
            return new AppUser(rs.getInt("id"), rs.getString("name"), team);
        }, name);
        return users.isEmpty() ? null : users.get(0);
    }

    @Override
    public List<AppUser> listUsers() {
        String sql = "SELECT id, name, team_id FROM APP_USER ORDER BY id";
        return jdbc.query(sql, (rs, n) -> {
            int teamId = rs.getInt("team_id");
            Integer team = rs.wasNull() ? null : teamId;
            return new AppUser(rs.getInt("id"), rs.getString("name"), team);
        });
    }

    // -----------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------

    @Override
    public List<Task> getTasksByTeamId(int teamId) {
        String sql = "SELECT t.*, u.name as userName, s.end_date as sprintEndDate, s.SPRINT_NUM as sprintNumber " +
                "FROM TASKS t " +
                "JOIN APP_USER u ON t.user_id = u.id " +
                "JOIN SPRINT s ON t.sprint_id = s.id " +
                "WHERE u.team_id = ? AND t.visible = 1";
        return jdbc.query(sql, new TaskRowMapper(), teamId);
    }

    @Override
    public List<Task> getTasksByUserName(String userName) {
        String sql = "SELECT t.*, u.name as userName, s.end_date as sprintEndDate, s.SPRINT_NUM as sprintNumber " +
                "FROM TASKS t " +
                "JOIN APP_USER u ON t.user_id = u.id " +
                "JOIN SPRINT s ON t.sprint_id = s.id " +
                "WHERE LOWER(u.name) = LOWER(?)";
        return jdbc.query(sql, new TaskRowMapper(), userName);
    }

    @Override
    public List<Task> getPendingTasksByUserName(String userName) {
        String sql = "SELECT t.*, u.name as userName, s.end_date as sprintEndDate, s.SPRINT_NUM as sprintNumber " +
                "FROM TASKS t " +
                "JOIN APP_USER u ON t.user_id = u.id " +
                "JOIN SPRINT s ON t.sprint_id = s.id " +
                "WHERE LOWER(u.name) = LOWER(?) AND t.state_id != 1";
        return jdbc.query(sql, new TaskRowMapper(), userName);
    }

    @Override
    public Task getTopPriorityTask(String userName) {
        String sql = "SELECT t.*, u.name as userName, s.end_date as sprintEndDate, s.SPRINT_NUM as sprintNumber " +
                "FROM TASKS t " +
                "JOIN APP_USER u ON t.user_id = u.id " +
                "JOIN SPRINT s ON t.sprint_id = s.id " +
                "WHERE LOWER(u.name) = LOWER(?) AND t.state_id != 1 " +
                "ORDER BY t.priority_id ASC, s.end_date ASC " +
                "FETCH FIRST 1 ROWS ONLY";
        List<Task> tasks = jdbc.query(sql, new TaskRowMapper(), userName);
        return tasks.isEmpty() ? null : tasks.get(0);
    }

    @Override
    public List<Map<String, Object>> getTeamMembers(long teamId) {
        String sql = "SELECT ID AS \"id\", NAME AS \"name\" FROM APP_USER WHERE TEAM_ID = ?";
        return jdbc.queryForList(sql, teamId);
    }

    @Override
    public int getLateTasksBySprint(int teamId, int sprintId) {
        String sql = "SELECT COUNT(*) FROM TASKS t " +
                "JOIN TASK_STATE ts ON ts.id = t.state_id " +
                "JOIN SPRINT s ON s.id = t.sprint_id " +
                "WHERE s.team_id = ? AND t.sprint_id = ? AND ts.name = 'LATE' AND t.visible = 1";
        Integer count = jdbc.queryForObject(sql, Integer.class, teamId, sprintId);
        return count == null ? 0 : count;
    }

    @Override
    public Map<String, Object> getTeamKpis(int teamId) {
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("Total_Tasks", queryCount(
                "SELECT COUNT(*) FROM TASKS t JOIN APP_USER u ON u.id = t.user_id " +
                        "WHERE u.team_id = ? AND t.visible = 1",
                teamId));
        kpis.put("Completed_Tasks", queryCount(
                "SELECT COUNT(*) FROM TASKS t JOIN TASK_STATE ts ON ts.id = t.state_id " +
                        "JOIN SPRINT s ON s.id = t.sprint_id " +
                        "WHERE ts.name = 'DONE' AND s.team_id = ? AND t.visible = 1",
                teamId));
        kpis.put("Pending_Tasks", queryCount(
                "SELECT COUNT(*) FROM TASKS t JOIN TASK_STATE ts ON ts.id = t.state_id " +
                        "JOIN SPRINT s ON s.id = t.sprint_id " +
                        "WHERE ts.name != 'DONE' AND s.team_id = ? AND t.visible = 1",
                teamId));
        kpis.put("Late_Tasks", queryCount(
                "SELECT COUNT(*) FROM TASKS t JOIN TASK_STATE ts ON ts.id = t.state_id " +
                        "JOIN APP_USER u ON u.id = t.user_id " +
                        "WHERE u.team_id = ? AND ts.name = 'LATE' AND t.visible = 1",
                teamId));
        kpis.put("Averages_Per_Member", averageTasksPerStatus(teamId));
        kpis.put("Average_Hours_Per_Sprint", averageWorkHoursPerSprint(teamId));
        return kpis;
    }

    @Override
    public Map<String, Object> getSprintAnalytics(int teamId, int sprintId) {
        Map<String, Object> out = new HashMap<>();
        out.put("Member_Status_Breakdown", memberStatusBreakdown(teamId, sprintId));
        out.put("Member_Work_Hours", memberWorkHoursBySprint(teamId, sprintId));
        return out;
    }

    @Override
    public Map<String, Object> getUserKpis(String userName) {
        String sql = "SELECT " +
                "COUNT(*) as total_tasks, " +
                "SUM(CASE WHEN t.state_id = 1 THEN 1 ELSE 0 END) as completed_tasks, " +
                "SUM(CASE WHEN t.state_id != 1 THEN 1 ELSE 0 END) as pending_tasks, " +
                "SUM(t.spent_hours) as total_hours_spent " +
                "FROM TASKS t " +
                "JOIN APP_USER u ON t.user_id = u.id " +
                "WHERE LOWER(u.name) = LOWER(?)";
        return jdbc.queryForMap(sql, userName);
    }

    // -----------------------------------------------------------------
    // Writes
    // -----------------------------------------------------------------

    @Override
    public Task createTask(CreateTask data) {
        int visibility = 1;
        int stateId = 2;
        BigDecimal spentHours = BigDecimal.ZERO;
        BigDecimal estimatedHours = data.getEstimatedHours() != null ? data.getEstimatedHours() : BigDecimal.ZERO;

        String insertSql = "INSERT INTO TASKS " +
                "(USER_ID, NAME, DESCRIPTION, SPRINT_ID, PRIORITY_ID, LINK_TO_FILE, CREATED_AT, UPDATED_AT, " +
                " COST, SPENT_HOURS, VISIBILITY, STATE_ID) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        int rows = jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(insertSql, new String[] { "ID" });
            ps.setInt(1, data.getAssigneeId());
            ps.setString(2, data.getName());
            ps.setString(3, data.getDescription());
            ps.setInt(4, data.getSprintId());
            ps.setInt(5, data.getPriority());
            ps.setString(6, data.getAttachment());
            ps.setBigDecimal(7, estimatedHours);
            ps.setBigDecimal(8, spentHours);
            ps.setInt(9, visibility);
            ps.setInt(10, stateId);
            return ps;
        }, keyHolder);

        if (rows == 0 || keyHolder.getKey() == null) {
            throw new IllegalStateException("Task insert succeeded but generated ID was not returned");
        }
        return getTaskById(keyHolder.getKey().intValue());
    }

    @Override
    public int markTaskCompleted(int taskId) {
        String sql = "UPDATE TASKS SET state_id = 1 WHERE id = ?";
        int rowsAffected = jdbc.update(sql, taskId);
        if (rowsAffected == 0) {
            throw new IllegalStateException("Could not find a task with ID " + taskId);
        }
        return rowsAffected;
    }

    // -----------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------

    private Task getTaskById(int taskId) {
        String sql = "SELECT t.*, u.name as userName, s.end_date as sprintEndDate, s.SPRINT_NUM as sprintNumber " +
                "FROM TASKS t " +
                "LEFT JOIN APP_USER u ON t.user_id = u.id " +
                "LEFT JOIN SPRINT s ON t.sprint_id = s.id " +
                "WHERE t.id = ?";
        return jdbc.queryForObject(sql, new TaskRowMapper(), taskId);
    }

    private int queryCount(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private List<Map<String, Object>> memberStatusBreakdown(int teamId, int sprintId) {
        String sql = "SELECT u.name AS user_name, " +
                "COUNT(CASE WHEN ts.name = 'DONE' THEN 1 END) AS completed_tasks, " +
                "COUNT(CASE WHEN ts.name = 'LATE' THEN 1 END) AS late_tasks, " +
                "COUNT(CASE WHEN ts.name IN ('PENDING', 'ON GOING') AND t.visible = 1 THEN 1 END) AS pending_tasks " +
                "FROM APP_USER u " +
                "LEFT JOIN TASKS t ON t.user_id = u.id AND t.sprint_id = ? " +
                "LEFT JOIN TASK_STATE ts ON ts.id = t.state_id " +
                "WHERE u.team_id = ? " +
                "GROUP BY u.id, u.name";
        return jdbc.query(sql, (rs, n) -> Map.of(
                "user_name", rs.getString("user_name"),
                "completed_tasks", rs.getInt("completed_tasks"),
                "late_tasks", rs.getInt("late_tasks"),
                "pending_tasks", rs.getInt("pending_tasks")), sprintId, teamId);
    }

    private List<Map<String, Object>> memberWorkHoursBySprint(int teamId, int sprintId) {
        String sql = "SELECT u.id AS user_id, u.name AS user_name, " +
                "COALESCE(SUM(t.spent_hours), 0) AS total_work_hours " +
                "FROM APP_USER u " +
                "LEFT JOIN TASKS t ON t.user_id = u.id AND t.sprint_id = ? " +
                "WHERE u.team_id = ? AND t.visible = 1 " +
                "GROUP BY u.id, u.name";
        return jdbc.query(sql, (rs, n) -> Map.of(
                "user_name", rs.getString("user_name"),
                "total_work_hours", rs.getInt("total_work_hours")), sprintId, teamId);
    }

    private List<Map<String, Object>> averageTasksPerStatus(int teamId) {
        String sql = "SELECT u.id AS user_id, u.name AS user_name, " +
                "AVG(completed) AS avg_completed_tasks, " +
                "AVG(late)      AS avg_late_tasks, " +
                "AVG(pending)   AS avg_pending_tasks " +
                "FROM (" +
                "  SELECT t.user_id, t.sprint_id, " +
                "    SUM(CASE WHEN ts.name = 'DONE' THEN 1 ELSE 0 END) AS completed, " +
                "    SUM(CASE WHEN ts.name = 'LATE' THEN 1 ELSE 0 END) AS late, " +
                "    SUM(CASE WHEN ts.name IN ('PENDING', 'ON GOING') AND t.visible = 1 THEN 1 ELSE 0 END) AS pending "
                +
                "  FROM TASKS t " +
                "  JOIN TASK_STATE ts ON ts.id = t.state_id " +
                "  JOIN SPRINT s ON s.id = t.sprint_id " +
                "  WHERE s.team_id = ? AND t.visible = 1 " +
                "  GROUP BY t.user_id, t.sprint_id" +
                ") sprint_stats " +
                "JOIN APP_USER u ON u.id = sprint_stats.user_id " +
                "GROUP BY u.id, u.name";
        return jdbc.query(sql, (rs, n) -> Map.of(
                "user_name", rs.getString("user_name"),
                "avg_completed_tasks", rs.getDouble("avg_completed_tasks"),
                "avg_late_tasks", rs.getDouble("avg_late_tasks"),
                "avg_pending_tasks", rs.getDouble("avg_pending_tasks"),
                "avg_total_tasks", rs.getDouble("avg_completed_tasks")
                        + rs.getDouble("avg_late_tasks")
                        + rs.getDouble("avg_pending_tasks")),
                teamId);
    }

    private List<Map<String, Object>> averageWorkHoursPerSprint(int teamId) {
        String sql = "SELECT u.id AS user_id, u.name AS user_name, " +
                "AVG(total_hours) AS avg_hours_per_sprint " +
                "FROM (" +
                "  SELECT t.user_id, t.sprint_id, COALESCE(SUM(t.spent_hours), 0) AS total_hours " +
                "  FROM TASKS t " +
                "  JOIN SPRINT s ON s.id = t.sprint_id " +
                "  WHERE s.team_id = ? AND t.visible = 1 " +
                "  GROUP BY t.user_id, t.sprint_id" +
                ") sprint_hours " +
                "JOIN APP_USER u ON u.id = sprint_hours.user_id " +
                "GROUP BY u.id, u.name";
        return jdbc.query(sql, (rs, n) -> Map.of(
                "user_name", rs.getString("user_name"),
                "avg_hours_per_sprint", rs.getDouble("avg_hours_per_sprint")), teamId);
    }
}
