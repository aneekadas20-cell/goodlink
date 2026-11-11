package com.goodlink.model;

import java.util.Objects;

/**
 * Volunteer model identified by Telegram chatId.
 */
public class Volunteer {
    private long chatId;
    private String name;
    private String email;
    private String bloodType;

    public Volunteer() {
    }

    public Volunteer(long chatId, String name, String email) {
        this(chatId, name, email, null);
    }

    public Volunteer(long chatId, String name, String email, String bloodType) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Volunteer volunteer = (Volunteer) o;
        return chatId == volunteer.chatId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(chatId);
    }

    @Override
    public String toString() {
        return String.format("%s (%s, ChatId: %d, Blood: %s)", name, email, chatId, bloodType != null ? bloodType : "N/A");
    }
}
