package com.octotask.bot.data.model;

/** A row from the OctoTask APP_USER table, used to identify a logged-in Telegram user. */
public class AppUser {
    private final int id;
    private final String name;
    private final Integer teamId;

    public AppUser(int id, String name, Integer teamId) {
        this.id = id;
        this.name = name;
        this.teamId = teamId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public Integer getTeamId() { return teamId; }
}
