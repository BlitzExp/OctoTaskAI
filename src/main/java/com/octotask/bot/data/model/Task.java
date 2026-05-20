package com.octotask.bot.data.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class Task {
    private int ID;
    private String name;
    private String description;
    private int userID;
    private String userName;
    private int sprintID;
    private int sprintNumber;
    private OffsetDateTime sprintEndDate;
    private int stateID;
    private int priorityID;
    private String linkToFile;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private BigDecimal cost;
    private BigDecimal spentHours;
    private int visibility;

    public int getID() { return ID; }
    public void setID(int ID) { this.ID = ID; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getUserID() { return userID; }
    public void setUserID(int userID) { this.userID = userID; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public int getSprintID() { return sprintID; }
    public void setSprintID(int sprintID) { this.sprintID = sprintID; }
    public int getSprintNumber() { return sprintNumber; }
    public void setSprintNumber(int sprintNumber) { this.sprintNumber = sprintNumber; }
    public OffsetDateTime getSprintEndDate() { return sprintEndDate; }
    public void setSprintEndDate(OffsetDateTime sprintEndDate) { this.sprintEndDate = sprintEndDate; }
    public int getStateID() { return stateID; }
    public void setStateID(int stateID) { this.stateID = stateID; }
    public int getPriorityID() { return priorityID; }
    public void setPriorityID(int priorityID) { this.priorityID = priorityID; }
    public String getLinkToFile() { return linkToFile; }
    public void setLinkToFile(String linkToFile) { this.linkToFile = linkToFile; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
    public BigDecimal getSpentHours() { return spentHours; }
    public void setSpentHours(BigDecimal spentHours) { this.spentHours = spentHours; }
    public int getVisibility() { return visibility; }
    public void setVisibility(int visibility) { this.visibility = visibility; }
}
