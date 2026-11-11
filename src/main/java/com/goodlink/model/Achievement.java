package com.goodlink.model;

/**
 * Achievement model representing completed milestones and volunteering completions.
 * Preserved on the achievements wall in newest-first order using MyLinkedList.addFirst().
 */
public class Achievement {
    private String title;
    private String description;
    private String date;
    private String volunteerName;

    public Achievement() {
    }

    public Achievement(String title, String description, String date, String volunteerName) {
        this.title = title;
        this.description = description;
        this.date = date;
        this.volunteerName = volunteerName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getVolunteerName() {
        return volunteerName;
    }

    public void setVolunteerName(String volunteerName) {
        this.volunteerName = volunteerName;
    }

    @Override
    public String toString() {
        return String.format("🏆 %s: %s (Awarded to %s on %s)",
                title, description, volunteerName, date);
    }
}
