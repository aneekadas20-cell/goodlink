package com.goodlink.model;

import com.goodlink.ds.MyLinkedList;

/**
 * Activity model representing a volunteering event.
 * Uses custom MyLinkedList for storing registered volunteers.
 */
public class Activity {
    private int id;
    private int ngoId;
    private String title;
    private String date;
    private final MyLinkedList<Volunteer> volunteers;

    public Activity() {
        this.volunteers = new MyLinkedList<>();
    }

    public Activity(int id, int ngoId, String title, String date) {
        this.id = id;
        this.ngoId = ngoId;
        this.title = title;
        this.date = date;
        this.volunteers = new MyLinkedList<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNgoId() {
        return ngoId;
    }

    public void setNgoId(int ngoId) {
        this.ngoId = ngoId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public MyLinkedList<Volunteer> getVolunteers() {
        return volunteers;
    }

    public boolean addVolunteer(Volunteer volunteer) {
        if (!volunteers.contains(volunteer)) {
            volunteers.add(volunteer);
            return true;
        }
        return false;
    }

    public boolean hasVolunteer(long chatId) {
        final boolean[] found = {false};
        volunteers.forEach(v -> {
            if (v.getChatId() == chatId) {
                found[0] = true;
            }
        });
        return found[0];
    }

    public Volunteer getVolunteer(long chatId) {
        final Volunteer[] result = {null};
        volunteers.forEach(v -> {
            if (v.getChatId() == chatId) {
                result[0] = v;
            }
        });
        return result[0];
    }

    @Override
    public String toString() {
        return String.format("[#%d] %s (Date: %s, NGO ID: %d, Volunteers: %d)",
                id, title, date, ngoId, volunteers.size());
    }
}
