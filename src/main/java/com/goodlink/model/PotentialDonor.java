package com.goodlink.model;

/**
 * Represents a volunteer who responded as a potential donor for an urgent request.
 */
public class PotentialDonor {
    private long chatId;
    private String name;
    private String email;
    private String bloodType;

    public PotentialDonor() {
    }

    public PotentialDonor(long chatId, String name, String email, String bloodType) {
        this.chatId = chatId;
        this.name = name;
        this.email = email;
        this.bloodType = bloodType;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBloodType() {
        return bloodType;
    }

    public void setBloodType(String bloodType) {
        this.bloodType = bloodType;
    }

    @Override
    public String toString() {
        return String.format("%s (%s, %s, ChatId: %d)", name, email, bloodType, chatId);
    }
}

