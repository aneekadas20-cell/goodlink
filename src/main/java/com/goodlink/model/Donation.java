package com.goodlink.model;

/**
 * Donation model representing item donations queued FIFO in MyQueue.
 */
public class Donation {
    private long chatId;
    private String item;
    private int quantity;
    private String date;
    private String category;
    private String condition;
    private String deliveryMethod;
    private String pickupAddress;
    private String preferredTime;

    public Donation() {
    }

    public Donation(long chatId, String item, int quantity, String date) {
        this(chatId, item, quantity, date, "Other", "Not specified", "Drop-off", null, null);
    }

    public Donation(long chatId, String item, int quantity, String date,
                    String category, String condition, String deliveryMethod,
                    String pickupAddress, String preferredTime) {
        this.chatId = chatId;
        this.item = item;
        this.quantity = quantity;
        this.date = date;
        this.category = category;
        this.condition = condition;
        this.deliveryMethod = deliveryMethod;
        this.pickupAddress = pickupAddress;
        this.preferredTime = preferredTime;
    }

    public long getChatId() {
        return chatId;
    }

    public void setChatId(long chatId) {
        this.chatId = chatId;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public String getDeliveryMethod() {
        return deliveryMethod;
    }

    public void setDeliveryMethod(String deliveryMethod) {
        this.deliveryMethod = deliveryMethod;
    }

    public String getPickupAddress() {
        return pickupAddress;
    }

    public void setPickupAddress(String pickupAddress) {
        this.pickupAddress = pickupAddress;
    }

    public String getPreferredTime() {
        return preferredTime;
    }

    public void setPreferredTime(String preferredTime) {
        this.preferredTime = preferredTime;
    }

    @Override
    public String toString() {
        return String.format("%d x %s [%s, %s, %s] (ChatId: %d, Date: %s)",
                quantity, item, category, condition, deliveryMethod, chatId, date);
    }
}
