package com.goodlink.bot;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple in‑memory state holder used by {@link GoodLinkTelegramBot} to manage multi‑step
 * interactions (registration, donation, urgent request, etc.). The bot keeps a map of
 * chatId → ConversationState. Each state records the current step and any temporary
 * data collected during the conversation.
 *
 * This class purposefully avoids persistence – the state is cleared when the bot
 * restarts, which is acceptable for short‑lived user interactions.
 */
public class ConversationState {

    /** Represents the high‑level workflow the user is currently in. */
    public enum Workflow {
        NONE,
        REGISTER_NAME,
        REGISTER_EMAIL,
        REGISTER_BLOOD_TYPE,
        DONATE_ITEM,
        DONATE_QUANTITY,
        DONATE_CATEGORY,
        DONATE_CONDITION,
        DONATE_DELIVERY_METHOD,
        DONATE_PICKUP_ADDRESS,
        DONATE_PICKUP_TIME,
        URGENT_TYPE,
        URGENT_BLOOD_TYPE,
        URGENT_UNITS,
        URGENT_HOSPITAL,
        URGENT_REASON,
        URGENT_CONTACT_PHONE,
        URGENT_DETAILS,
        URGENT_CITY,
        JOIN_SELECT_ID,
        RESPOND_SELECT_ID,
        RESPOND_BLOOD_TYPE,
        // Add more as needed
    }

    private Workflow workflow = Workflow.NONE;
    private final Map<String, String> data = new HashMap<>();

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow workflow) {
        this.workflow = workflow;
    }

    /** Store a temporary value (e.g., "name", "email", "item", …). */
    public void put(String key, String value) {
        data.put(key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public void clear() {
        workflow = Workflow.NONE;
        data.clear();
    }
}
