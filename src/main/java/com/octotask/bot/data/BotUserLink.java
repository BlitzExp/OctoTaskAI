package com.octotask.bot.data;

/** Mapping of a Telegram chat to an OctoTask APP_USER (the bot's notion of "logged in"). */
public class BotUserLink {
    private final long telegramChatId;
    private final int appUserId;
    private final String appUserName;
    private final Integer teamId;

    public BotUserLink(long telegramChatId, int appUserId, String appUserName, Integer teamId) {
        this.telegramChatId = telegramChatId;
        this.appUserId = appUserId;
        this.appUserName = appUserName;
        this.teamId = teamId;
    }

    public long getTelegramChatId() { return telegramChatId; }
    public int getAppUserId() { return appUserId; }
    public String getAppUserName() { return appUserName; }
    public Integer getTeamId() { return teamId; }
}
