package com.goodlink.model;

/**
 * NGO model representing a non-governmental organization.
 */
public class NGO {
    private int id;
    private String name;
    private String category;
    private String city;
    private String email;

    public NGO() {
    }

    public NGO(int id, String name, String category, String city, String email) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.city = city;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return String.format("[#%d] %s | %s | %s (Email: %s)", id, name, category, city, email);
    }
}
