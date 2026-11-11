package com.goodlink.model;

import java.util.ArrayList;
import java.util.List;

/**
 * UrgentRequest model for critical community needs (e.g., BLOOD, FOOD).
 */
public class UrgentRequest {
    private int requestId;          // unique sequential numeric ID
    private String type;            // e.g., BLOOD or FOOD
    private String details;         // specific description/requirement
    private String city;            // location of requirement
    private boolean urgent;         // true for high priority / urgent queue
    private String date;            // timestamp or formatted date
    private long chatId;            // requesting user's Telegram chatId
    private int unitsNeeded;        // units needed (e.g. for blood)
    private String hospitalName;    // hospital or location name
    private String reason;          // patient condition or reason (nullable)
    private String contactPhone;    // contact phone number
    private List<PotentialDonor> potentialDonors = new ArrayList<>();

    public UrgentRequest() {
    }

    public UrgentRequest(String type, String details, String city, boolean urgent, String date, long chatId) {
        this(0, type, details, city, urgent, date, chatId, 1, "Not specified", null, "Not provided");
    }

    public UrgentRequest(String type, String details, String city, boolean urgent, String date, long chatId,
                         int unitsNeeded, String hospitalName, String reason, String contactPhone) {
        this(0, type, details, city, urgent, date, chatId, unitsNeeded, hospitalName, reason, contactPhone);
    }

    public UrgentRequest(int requestId, String type, String details, String city, boolean urgent, String date, long chatId,
                         int unitsNeeded, String hospitalName, String reason, String contactPhone) {
        this.requestId = requestId;
        this.type = type;
        this.details = details;
        this.city = city;
        this.urgent = urgent;
        this.date = date;
        this.chatId = chatId;
        this.unitsNeeded = unitsNeeded;
        this.hospitalName = hospitalName;
        this.reason = reason;
        this.contactPhone = contactPhone;
        this.potentialDonors = new ArrayList<>();
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public void setUrgent(boolean urgent) {
        this.urgent = urgent;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public int getUnitsNeeded() {
        return unitsNeeded;
    }

    public void setUnitsNeeded(int unitsNeeded) {
        this.unitsNeeded = unitsNeeded;
    }

    public String getHospitalName() {
        return hospitalName;
    }

    public void setHospitalName(String hospitalName) {
        this.hospitalName = hospitalName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public List<PotentialDonor> getPotentialDonors() {
        return potentialDonors;
    }

    public void setPotentialDonors(List<PotentialDonor> potentialDonors) {
        this.potentialDonors = potentialDonors != null ? potentialDonors : new ArrayList<>();
    }

    public void addPotentialDonor(PotentialDonor donor) {
        if (donor != null) {
            this.potentialDonors.add(donor);
        }
    }

    public boolean hasDonor(long chatId) {
        if (potentialDonors == null) return false;
        return potentialDonors.stream().anyMatch(d -> d.getChatId() == chatId);
    }

    @Override
    public String toString() {
        return String.format("[#%d %s%s] %s in %s (Reported: %s, Units: %d, Hospital: %s, Contact: %s, Donors: %d)",
                requestId, urgent ? "URGENT " : "NORMAL ", type, details, city, date, unitsNeeded, hospitalName, contactPhone,
                potentialDonors != null ? potentialDonors.size() : 0);
    }
}
