package com.goodlink.bot;

import com.goodlink.model.UrgentRequest;
import com.goodlink.model.Volunteer;
import com.goodlink.service.PlatformService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.goodlink.bot.ConversationState;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Long-polling Telegram Bot for GoodLink NGO Volunteer Platform.
 * Uses the telegrambots library with long polling (no webhook or public URL required).
 * Bot token is read from TELEGRAM_BOT_TOKEN environment variable.
 */
@Component
public class GoodLinkTelegramBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(GoodLinkTelegramBot.class);

    private final PlatformService platformService;
    // In‑memory map of chatId → ConversationState for multi‑step interactions
    private final Map<Long, ConversationState> conversationStates = new ConcurrentHashMap<>();

    /** Known command prefixes — used to detect when a user types a command while in a workflow. */
    private static final Set<String> KNOWN_COMMANDS = Set.of(
            "REGISTER", "NGOREGISTER", "NGOS", "ACTIVITIES", "JOIN",
            "DONATE", "URGENT", "ACHIEVEMENTS", "CATEGORIES", "HELP", "CANCEL",
            "OPENREQUESTS", "RESPOND"
    );

    private static final Set<String> VALID_BLOOD_TYPES = Set.of(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    private static final Map<String, String> DONATE_CATEGORIES = Map.of(
            "CLOTHES", "Clothes",
            "FOOD", "Food",
            "BOOKS", "Books",
            "ELECTRONICS", "Electronics",
            "MEDICAL", "Medical",
            "OTHER", "Other"
    );

    private static final Map<String, String> DONATE_CONDITIONS = Map.of(
            "NEW", "New",
            "GENTLY USED", "Gently Used",
            "OLD", "Old"
    );

    private String normalizeDeliveryMethod(String input) {
        if (input == null) return null;
        String clean = input.trim().replaceAll("[\\s-]+", "").toUpperCase();
        if (clean.equals("PICKUP")) return "Pickup";
        if (clean.equals("DROPOFF")) return "Drop-off";
        return null;
    }

    private String validateAndCleanPhone(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        boolean hasPlus = trimmed.startsWith("+");
        String afterPlus = hasPlus ? trimmed.substring(1) : trimmed;
        if (!afterPlus.matches("[\\d\\s-]+")) {
            return null;
        }
        String digits = afterPlus.replaceAll("[\\s-]+", "");
        if (!digits.matches("\\d{7,15}")) {
            return null;
        }
        return (hasPlus ? "+" : "") + digits;
    }

    /** Shown when an unregistered user tries to start an urgent request. */
    private static final String URGENT_REGISTER_FIRST_MESSAGE =
            "🚨 *Registration Required*\n\n"
                    + "Urgent requests can only be sent by registered volunteers, so the NGO can contact you.\n\n"
                    + "Please register first with `REGISTER <name> <email>` (or tap 📝 Register), then try again.";

    @Value("${TELEGRAM_BOT_TOKEN:${telegram.bot.token:}}")
    private String botToken;

    @Value("${telegram.bot.username:GoodLinkBot}")
    private String botUsername;

    public GoodLinkTelegramBot(PlatformService platformService) {
        this.platformService = platformService;
    }

    @PostConstruct
    public void init() {
        String token = getBotToken();
        if (token == null || token.isBlank()) {
            log.warn("⚠️ TELEGRAM_BOT_TOKEN is not set in environment. Telegram Bot long-polling is inactive. Set TELEGRAM_BOT_TOKEN to connect with Telegram.");
            return;
        }

        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            log.info("🤖 GoodLink Telegram Bot successfully registered and listening via Long Polling (Bot username: {})", getBotUsername());
        } catch (TelegramApiException e) {
            log.error("Failed to register Telegram Bot via Long Polling: {}", e.getMessage(), e);
        }
    }

    @Override
    public String getBotUsername() {
        return (botUsername != null && !botUsername.isBlank()) ? botUsername : "GoodLinkBot";
    }

    @Override
    public String getBotToken() {
        if (botToken != null && !botToken.isBlank()) {
            return botToken;
        }
        return System.getenv("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update == null) {
            return;
        }
        // Handle regular text messages
        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText().trim();
            log.info("Received message from Chat ID {}: {}", chatId, messageText);

            // First check if the user is in a multi‑step workflow
            String reply = processCommand(chatId, messageText);
            if (reply != null) {
                sendResponse(chatId, reply);
            }
            return;
        }
        // Handle button callbacks
        if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            long chatId = callback.getMessage().getChatId();
            String data = callback.getData();
            log.info("Received callback from Chat ID {}: {}", chatId, data);
            String reply = processCallback(chatId, data);
            if (reply != null) {
                sendResponse(chatId, reply);
            }
        }
    }

    /**
     * Returns true if the input looks like a known command or slash-command
     * (used to let users escape a workflow by typing a command).
     */
    private boolean isKnownCommand(String input) {
        String upper = input.toUpperCase();
        // Strip leading slash
        if (upper.startsWith("/")) upper = upper.substring(1);
        // Check first word
        String firstWord = upper.split("\\s+")[0];
        return KNOWN_COMMANDS.contains(firstWord);
    }

    /**
     * Returns true if the input matches a reply-keyboard button label
     * (emoji prefix + text).
     */
    private boolean isReplyKeyboardButton(String input) {
        String stripped = input.replaceAll("^[^\\p{L}]+", "").trim().toUpperCase();
        return stripped.equals("NGOS") || stripped.equals("ACTIVITIES")
                || stripped.equals("DONATE") || stripped.equals("URGENT HELP")
                || stripped.equals("ACHIEVEMENTS") || stripped.equals("REGISTER")
                || stripped.equals("JOIN ACTIVITY") || stripped.equals("HELP")
                || stripped.equals("I CAN DONATE") || stripped.equals("OPEN REQUESTS")
                || stripped.equals("OPENREQUESTS");
    }

    /**
     * Strips the @BotUsername suffix from slash commands (e.g. "/start@MyBot")
     * but ONLY when the @ is preceded by a slash command, so that emails and
     * other input containing @ are not mangled.
     */
    private String stripBotMention(String input) {
        // Only strip @suffix on slash commands like "/start@BotName"
        if (input.startsWith("/") && input.contains("@")) {
            return input.substring(0, input.indexOf('@')).trim();
        }
        return input;
    }

    /**
     * Processes input commands from Telegram user.
     */
    public String processCommand(long chatId, String input) {
        String trimmed = input.trim();
        trimmed = stripBotMention(trimmed);

        // If user is in a multi-step workflow, delegate there first
        // UNLESS the input is a known command or keyboard button (let the user escape)
        ConversationState state = conversationStates.get(chatId);
        if (state != null && state.getWorkflow() != ConversationState.Workflow.NONE) {
            if (!isKnownCommand(trimmed) && !isReplyKeyboardButton(trimmed)) {
                return handleWorkflowStep(chatId, trimmed, state);
            }
            // User typed a command while in a workflow — cancel the workflow and fall through
            conversationStates.remove(chatId);
        }

        // --- Reply-keyboard button routing ---
        // Strip leading emoji and whitespace so button taps map to commands
        String stripped = trimmed.replaceAll("^[^\\p{L}]+", "").trim();
        String strippedUpper = stripped.toUpperCase();

        if (strippedUpper.equals("NGOS")) {
            return "📋 *Registered NGOs (Sorted by ID - BST In-Order):*\n\n" + platformService.listNgos();
        }
        if (strippedUpper.equals("ACTIVITIES")) {
            return "🌟 *Upcoming Volunteer Activities:*\n\n" + platformService.listActivities();
        }
        if (strippedUpper.equals("DONATE")) {
            return "❌ *Invalid Format*\nUsage: `DONATE <item> <quantity>`\nExample: `DONATE Blankets 10` or `DONATE Rice(kg) 50`";
        }
        if (strippedUpper.equals("URGENT HELP")) {
            // Must be registered before starting an urgent request
            if (!isRegistered(chatId)) {
                return URGENT_REGISTER_FIRST_MESSAGE;
            }
            return "❌ *Invalid Format*\nUsage: `URGENT <BLOOD|FOOD> <details> <city>`\nExample: `URGENT BLOOD O-positive needed for surgery at Ruby Clinic Pune`";
        }
        if (strippedUpper.equals("ACHIEVEMENTS")) {
            return platformService.listAchievements();
        }
        if (strippedUpper.equals("REGISTER")) {
            return "❌ *Invalid Format*\nUsage: `REGISTER <name> <email>`\nExample: `REGISTER Jane Doe jane@example.com`";
        }
        if (strippedUpper.equals("JOIN ACTIVITY")) {
            // Start the join-activity workflow (ask for ID)
            ConversationState joinState = new ConversationState();
            joinState.setWorkflow(ConversationState.Workflow.JOIN_SELECT_ID);
            conversationStates.put(chatId, joinState);
            return "🤝 *Join Activity*\nSend the *activity ID* you wish to join.\n(Type `ACTIVITIES` to view the list, or send `CANCEL` to abort.)";
        }
        if (strippedUpper.equals("I CAN DONATE")) {
            if (!isRegistered(chatId)) {
                return URGENT_REGISTER_FIRST_MESSAGE;
            }
            ConversationState respondState = new ConversationState();
            respondState.setWorkflow(ConversationState.Workflow.RESPOND_SELECT_ID);
            conversationStates.put(chatId, respondState);
            return "🩸 *I Can Donate*\nPlease type `OPENREQUESTS` to view open urgent requests, then send the *Request ID* you wish to respond to.\n(Or send `CANCEL` to abort.)";
        }
        if (strippedUpper.equals("OPEN REQUESTS") || strippedUpper.equals("OPENREQUESTS")) {
            return platformService.listOpenRequests();
        }
        if (strippedUpper.equals("HELP")) {
            sendMessageWithKeyboard(chatId, buildHelpText(chatId), buildMainMenuKeyboard());
            return null; // response already sent
        }

        String upper = trimmed.toUpperCase();

        // 1. HELP or /start – display interactive main menu
        if (upper.equals("/START") || upper.equals("/HELP")) {
            // Send welcome text with inline keyboard
            sendMessageWithKeyboard(chatId, buildHelpText(chatId), buildMainMenuKeyboard());
            return null; // response already sent
        }

        // 2. NGOREGISTER <ngoId> <contactEmail>
        if (upper.startsWith("NGOREGISTER") || upper.startsWith("/NGOREGISTER")) {
            return handleNgoRegister(chatId, trimmed);
        }

        // 3. REGISTER <name> <email>
        if (upper.startsWith("REGISTER") || upper.startsWith("/REGISTER")) {
            return handleRegister(chatId, trimmed);
        }

        // 3. NGOS
        if (upper.equals("NGOS") || upper.equals("/NGOS")) {
            return "📋 *Registered NGOs (Sorted by ID - BST In-Order):*\n\n" + platformService.listNgos();
        }

        // 4. ACTIVITIES
        if (upper.equals("ACTIVITIES") || upper.equals("/ACTIVITIES")) {
            return "🌟 *Upcoming Volunteer Activities:*\n\n" + platformService.listActivities();
        }

        // 5. JOIN <activityId> or bare JOIN
        if (upper.equals("JOIN") || upper.equals("/JOIN")) {
            // Bare JOIN without an ID — start the workflow
            ConversationState joinState = new ConversationState();
            joinState.setWorkflow(ConversationState.Workflow.JOIN_SELECT_ID);
            conversationStates.put(chatId, joinState);
            return "🤝 *Join Activity*\nSend the *activity ID* you wish to join.\n(Type `ACTIVITIES` to view the list, or send `CANCEL` to abort.)";
        }
        if (upper.startsWith("JOIN ") || upper.startsWith("/JOIN ")) {
            return handleJoin(chatId, trimmed);
        }

        // 6. DONATE <item> <quantity>
        if (upper.startsWith("DONATE ") || upper.startsWith("/DONATE ")) {
            return handleDonate(chatId, trimmed);
        }

        // 7. URGENT <BLOOD|FOOD> <details> <city>
        if (upper.startsWith("URGENT ") || upper.startsWith("/URGENT ")) {
            return handleUrgent(chatId, trimmed);
        }

        // 8. ACHIEVEMENTS
        if (upper.equals("ACHIEVEMENTS") || upper.equals("/ACHIEVEMENTS")) {
            return platformService.listAchievements();
        }

        // 9. CATEGORIES
        if (upper.equals("CATEGORIES") || upper.equals("/CATEGORIES")) {
            return "🌳 *NGO Category Hierarchy:*\n```\n" + platformService.printCategories() + "```";
        }

        // 10. OPENREQUESTS
        if (upper.equals("OPENREQUESTS") || upper.equals("/OPENREQUESTS")) {
            return platformService.listOpenRequests();
        }

        // 11. RESPOND <requestId> or bare RESPOND
        if (upper.equals("RESPOND") || upper.equals("/RESPOND")) {
            if (!isRegistered(chatId)) {
                return URGENT_REGISTER_FIRST_MESSAGE;
            }
            ConversationState respondState = new ConversationState();
            respondState.setWorkflow(ConversationState.Workflow.RESPOND_SELECT_ID);
            conversationStates.put(chatId, respondState);
            return "🩸 *Respond to Urgent Request*\nSend the *Request ID* you wish to respond to.\n(Type `OPENREQUESTS` to view open requests, or send `CANCEL` to abort.)";
        }
        if (upper.startsWith("RESPOND ") || upper.startsWith("/RESPOND ")) {
            return handleRespond(chatId, trimmed);
        }

        // 12. CANCEL – abort any ongoing workflow
        if (upper.equals("/CANCEL") || upper.equals("CANCEL")) {
            conversationStates.remove(chatId);
            return "❌ Interaction cancelled. Use /help or /start to see the main menu.";
        }

        // 11. #<number> — shorthand for JOIN <number>
        String hashMatch = trimmed.replaceAll("^#\\s*", "");
        if (!hashMatch.equals(trimmed) && !hashMatch.isEmpty()) {
            try {
                int actId = Integer.parseInt(hashMatch.trim());
                return platformService.joinActivity(chatId, actId);
            } catch (NumberFormatException ignore) {
                // not a valid #<number> — fall through to unknown
            }
        }

        // Unknown text -> reply with the help menu
        return "❓ *Unknown Command*\n\n" + buildHelpText(chatId);
    }

    /** True if this Telegram chat has already registered as a volunteer. */
    private boolean isRegistered(long chatId) {
        return platformService.getVolunteer(chatId) != null;
    }

    private String handleNgoRegister(long chatId, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            return "❌ *Invalid Format*\nUsage: `NGOREGISTER <ngoId> <contactEmail>`\nExample: `NGOREGISTER 101 contact@silveryears.org`";
        }

        int ngoId;
        try {
            ngoId = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return "❌ *Invalid NGO ID*\nPlease provide a valid numeric NGO ID (e.g. `NGOREGISTER 101 contact@silveryears.org`).\nType `NGOS` to view all registered NGOs and their IDs.";
        }

        String contactEmail = parts[2].trim();
        if (!contactEmail.contains("@") || !contactEmail.contains(".")) {
            return "❌ *Invalid Email Address*\nPlease provide a valid contact email (e.g. `NGOREGISTER 101 contact@silveryears.org`).";
        }

        com.goodlink.model.NGO ngo = platformService.updateNgoEmail(ngoId, contactEmail);
        if (ngo == null) {
            return "❌ NGO with ID #" + ngoId + " not found.\nType `NGOS` to view all registered NGOs and their IDs.";
        }

        return String.format(
                "🏢 *NGO Contact Email Updated!*\n\n" +
                "NGO ID: `#%d`\n" +
                "Organization: *%s*\n" +
                "Category: %s\n" +
                "City: %s\n" +
                "📧 Real Contact Email: *%s*",
                ngo.getId(), ngo.getName(), ngo.getCategory(), ngo.getCity(), ngo.getEmail()
        );
    }

    private String handleRegister(long chatId, String command) {
        // Pattern: REGISTER <name> <email>
        // Name can be multiple words, email is the last token
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            return "❌ *Invalid Format*\nUsage: `REGISTER <name> <email>`\nExample: `REGISTER Jane Doe jane@example.com`";
        }

        String email = parts[parts.length - 1].trim();
        if (!isValidEmail(email)) {
            return "❌ *Invalid Email Address*\nPlease provide a valid email (e.g., `REGISTER John Doe john@example.com`).";
        }

        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            if (nameBuilder.length() > 0) nameBuilder.append(" ");
            nameBuilder.append(parts[i]);
        }
        String name = nameBuilder.toString();

        Volunteer volunteer = platformService.registerVolunteer(chatId, name, email);
        return String.format(
                "🎉 *Registration Successful!*\n\n" +
                "👤 Name: *%s*\n" +
                "📧 Email: *%s*\n" +
                "🆔 Chat ID: `%d`\n\n" +
                "You can now join volunteer activities with `JOIN <activityId>` and pledge donations with `DONATE <item> <quantity>`.",
                volunteer.getName(), volunteer.getEmail(), chatId
        );
    }

    private String handleJoin(long chatId, String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return "❌ *Missing Activity ID*\nUsage: `JOIN <activityId>`\nExample: `JOIN 1`\n(Type `ACTIVITIES` to view list)";
        }

        try {
            // Strip non-digit chars (e.g. "#") from the ID token
            String idToken = parts[1].replaceAll("[^0-9]", "");
            if (idToken.isEmpty()) {
                return "❌ Invalid Activity ID. Please enter a valid number (e.g. `JOIN 1`).";
            }
            int activityId = Integer.parseInt(idToken);
            return platformService.joinActivity(chatId, activityId);
        } catch (NumberFormatException e) {
            return "❌ Invalid Activity ID. Please enter a valid number (e.g. `JOIN 1`).";
        }
    }

    private String handleDonate(long chatId, String command) {
        // DONATE <item> <quantity>
        // Quantity is expected as the last token
        String[] parts = command.split("\\s+");
        if (parts.length < 3) {
            return "❌ *Invalid Format*\nUsage: `DONATE <item> <quantity>`\nExample: `DONATE Blankets 10` or `DONATE Rice(kg) 50`";
        }

        String quantityStr = parts[parts.length - 1];
        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0) {
                return "❌ Quantity must be greater than zero.";
            }
        } catch (NumberFormatException e) {
            return "❌ Invalid quantity. The last parameter must be a number.\nExample: `DONATE Blankets 10`";
        }

        StringBuilder item = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            if (item.length() > 0) item.append(" ");
            item.append(parts[i]);
        }

        return platformService.recordDonation(chatId, item.toString(), quantity);
    }

    private boolean isValidBloodType(String bloodType) {
        if (bloodType == null) {
            return false;
        }
        return VALID_BLOOD_TYPES.contains(bloodType.trim().toUpperCase());
    }

    private String handleUrgent(long chatId, String command) {
        // Must be registered before sending an urgent request
        if (!isRegistered(chatId)) {
            return URGENT_REGISTER_FIRST_MESSAGE;
        }

        // URGENT <BLOOD|FOOD> <details> <city>
        // Format: URGENT <TYPE> <DETAILS...> <CITY>
        String[] parts = command.split("\\s+");
        if (parts.length < 4) {
            return "❌ *Invalid Format*\nUsage: `URGENT <BLOOD|FOOD> <details> <city>`\nExample: `URGENT BLOOD O+ Pune` or `URGENT FOOD Rice and blankets Pune`";
        }

        String type = parts[1].toUpperCase();
        if (!type.equals("BLOOD") && !type.equals("FOOD")) {
            return "❌ *Invalid Request Type*: `" + parts[1] + "`\nAllowed types: `BLOOD` or `FOOD`.";
        }

        if (type.equals("BLOOD")) {
            String token = parts[2].trim();
            if (!isValidBloodType(token)) {
                return "❌ *Invalid Blood Type*: `" + token + "`\nAllowed blood types: A+, A-, B+, B-, AB+, AB-, O+, or O-.\nExample: `URGENT BLOOD O+ Pune`";
            }
            if (parts.length != 4) {
                return "❌ *Invalid Format*\nUsage: `URGENT BLOOD <token> <city>`\nExample: `URGENT BLOOD O+ Pune`";
            }
            String bloodType = token.toUpperCase();
            String city = parts[3].trim();
            return platformService.recordUrgentRequest(chatId, type, bloodType, city);
        }

        String city = parts[parts.length - 1];
        StringBuilder details = new StringBuilder();
        for (int i = 2; i < parts.length - 1; i++) {
            if (details.length() > 0) details.append(" ");
            details.append(parts[i]);
        }

        return platformService.recordUrgentRequest(chatId, type, details.toString(), city);
    }

    private String handleRespond(long chatId, String command) {
        if (!isRegistered(chatId)) {
            return "🚨 *Registration Required*\n\n"
                    + "You must be registered on GoodLink to respond to urgent requests.\n\n"
                    + "Please register first with `REGISTER <name> <email>` (or tap 📝 Register), then try again.";
        }

        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            ConversationState respondState = new ConversationState();
            respondState.setWorkflow(ConversationState.Workflow.RESPOND_SELECT_ID);
            conversationStates.put(chatId, respondState);
            return "🩸 *Respond to Urgent Request*\nPlease send the *Request ID* you wish to respond to.\n(Type `OPENREQUESTS` to view open requests, or send `CANCEL` to abort.)";
        }

        String idStr = parts[1].replaceAll("[^0-9]", "");
        int requestId;
        try {
            requestId = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            return "❌ *Invalid Request ID*\nPlease provide a valid numeric Request ID (e.g. `RESPOND 1`).\nType `OPENREQUESTS` to view all open urgent requests.";
        }

        UrgentRequest request = platformService.findUrgentRequestById(requestId);
        if (request == null) {
            return "❌ Urgent Request #" + requestId + " not found.\nType `OPENREQUESTS` to view all open urgent requests.";
        }

        Volunteer volunteer = platformService.getVolunteer(chatId);
        if (parts.length >= 3 && isValidBloodType(parts[2])) {
            String blood = parts[2].trim().toUpperCase();
            volunteer.setBloodType(blood);
            platformService.persistState();
            return platformService.respondToUrgentRequest(chatId, requestId, blood);
        }

        if (volunteer.getBloodType() != null && !volunteer.getBloodType().isBlank() && !volunteer.getBloodType().equalsIgnoreCase("Not specified")) {
            return platformService.respondToUrgentRequest(chatId, requestId, volunteer.getBloodType());
        }

        ConversationState respondState = new ConversationState();
        respondState.setWorkflow(ConversationState.Workflow.RESPOND_BLOOD_TYPE);
        respondState.put("requestId", String.valueOf(requestId));
        conversationStates.put(chatId, respondState);
        return "What is your blood type? (A+, A-, B+, B-, AB+, AB-, O+, or O-)";
    }

    /**
     * Validates an email address: must contain exactly one @, at least one dot
     * after the @, local part non-empty, domain non-empty.
     * Accepts normal addresses like user@gmail.com without being overly restrictive.
     */
    private boolean isValidEmail(String email) {
        if (email == null) return false;
        email = email.trim();
        int atIdx = email.indexOf('@');
        if (atIdx <= 0) return false;                         // no @ or nothing before it
        if (email.indexOf('@', atIdx + 1) >= 0) return false; // more than one @
        String domain = email.substring(atIdx + 1);
        return domain.contains(".") && !domain.startsWith(".") && !domain.endsWith(".");
    }

    private String buildHelpMenu(long chatId) {
        Volunteer volunteer = platformService.getVolunteer(chatId);
        String greeting = (volunteer != null)
                ? "👋 Welcome back, *" + volunteer.getName() + "*!"
                : "👋 Welcome to *GoodLink* - Connecting Volunteers & NGOs!";

        return greeting + "\n\n" +
                "Here are the available commands:\n\n" +
                "🔹 `REGISTER <name> <email>`\n" +
                "    Register your profile (links your Telegram Chat ID)\n\n" +
                "🔹 `NGOREGISTER <ngoId> <contactEmail>`\n" +
                "    Set or update NGO contact email at runtime\n\n" +
                "🔹 `NGOS`\n" +
                "    List all verified partner NGOs (sorted via BST)\n\n" +
                "🔹 `ACTIVITIES`\n" +
                "    Browse upcoming volunteer drives\n\n" +
                "🔹 `JOIN <activityId>`\n" +
                "    Register for an activity & receive email confirmation\n\n" +
                "🔹 `DONATE <item> <quantity>`\n" +
                "    Pledge supplies (queued FIFO) & receive email receipt\n\n" +
                "🔹 `URGENT <BLOOD|FOOD> <details> <city>`\n" +
                "    Dispatch high-priority alert to NGO admins (registration required)\n\n" +
                "🔹 `OPENREQUESTS`\n" +
                "    Browse open urgent requests needing help\n\n" +
                "🔹 `RESPOND <requestId>`\n" +
                "    Offer to help with an open urgent request\n\n" +
                "🔹 `ACHIEVEMENTS`\n" +
                "    View Wall of Achievements (newest first)\n\n" +
                "🔹 `CATEGORIES`\n" +
                "    View NGO category tree hierarchy\n\n" +
                "🔹 `HELP` or `/start`\n" +
                "    Display this menu";
    }

    private String buildHelpText(long chatId) {
        return buildHelpMenu(chatId);
    }

    private void sendResponse(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.enableMarkdown(true);
        message.setReplyMarkup(buildPersistentReplyKeyboard());
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn("Failed to send markdown message, falling back to plain text: {}", e.getMessage());
            try {
                SendMessage fallback = new SendMessage();
                fallback.setChatId(String.valueOf(chatId));
                fallback.setText(text.replaceAll("[*_`\\[\\]]", ""));
                fallback.setReplyMarkup(buildPersistentReplyKeyboard());
                execute(fallback);
            } catch (TelegramApiException ex) {
                log.error("Failed to send fallback message to ChatId {}: {}", chatId, ex.getMessage());
            }
        }
    }

    /** Sends a markdown message together with an inline keyboard, plus the persistent reply keyboard. */
    private void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) {
        // First send the inline keyboard message
        SendMessage inlineMsg = new SendMessage();
        inlineMsg.setChatId(String.valueOf(chatId));
        inlineMsg.setText(text);
        inlineMsg.enableMarkdown(true);
        inlineMsg.setReplyMarkup(keyboard);
        try {
            execute(inlineMsg);
        } catch (TelegramApiException e) {
            log.warn("Failed to send inline-keyboard message, falling back to plain text: {}", e.getMessage());
            sendResponse(chatId, text);
            return;
        }
        // Then re-assert the persistent reply keyboard so it stays visible
        SendMessage replyKbMsg = new SendMessage();
        replyKbMsg.setChatId(String.valueOf(chatId));
        replyKbMsg.setText("⬇️ Use the menu buttons below to navigate:");
        replyKbMsg.setReplyMarkup(buildPersistentReplyKeyboard());
        try {
            execute(replyKbMsg);
        } catch (TelegramApiException ignored) {
            // Non-critical: the inline keyboard message already went through
        }
    }

    /** Builds the inline keyboard for blood type selection. */
    private InlineKeyboardMarkup buildBloodTypeKeyboard() {
        InlineKeyboardButton ap = InlineKeyboardButton.builder().text("A+").callbackData("BLOOD_A+").build();
        InlineKeyboardButton an = InlineKeyboardButton.builder().text("A-").callbackData("BLOOD_A-").build();
        InlineKeyboardButton bp = InlineKeyboardButton.builder().text("B+").callbackData("BLOOD_B+").build();
        InlineKeyboardButton bn = InlineKeyboardButton.builder().text("B-").callbackData("BLOOD_B-").build();
        InlineKeyboardButton abp = InlineKeyboardButton.builder().text("AB+").callbackData("BLOOD_AB+").build();
        InlineKeyboardButton abn = InlineKeyboardButton.builder().text("AB-").callbackData("BLOOD_AB-").build();
        InlineKeyboardButton op = InlineKeyboardButton.builder().text("O+").callbackData("BLOOD_O+").build();
        InlineKeyboardButton on = InlineKeyboardButton.builder().text("O-").callbackData("BLOOD_O-").build();

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(java.util.Arrays.asList(ap, an, bp, bn));
        rows.add(java.util.Arrays.asList(abp, abn, op, on));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /** Builds the main menu inline keyboard with icons and button labels. */
    private InlineKeyboardMarkup buildMainMenuKeyboard() {
        InlineKeyboardButton register = InlineKeyboardButton.builder().text("🙋 Register as Volunteer").callbackData("MENU_REGISTER").build();
        InlineKeyboardButton ngos = InlineKeyboardButton.builder().text("🏢 Explore NGOs").callbackData("MENU_NGOS").build();
        InlineKeyboardButton activities = InlineKeyboardButton.builder().text("📅 Browse Activities").callbackData("MENU_ACTIVITIES").build();
        InlineKeyboardButton join = InlineKeyboardButton.builder().text("🤝 Join an Activity").callbackData("MENU_JOIN").build();
        InlineKeyboardButton donate = InlineKeyboardButton.builder().text("🎁 Donate Items").callbackData("MENU_DONATE").build();
        InlineKeyboardButton urgent = InlineKeyboardButton.builder().text("🚨 Urgent Help").callbackData("MENU_URGENT").build();
        InlineKeyboardButton openRequests = InlineKeyboardButton.builder().text("🩸 Open Requests").callbackData("MENU_OPENREQUESTS").build();
        InlineKeyboardButton achievements = InlineKeyboardButton.builder().text("🏆 Achievements").callbackData("MENU_ACHIEVEMENTS").build();
        InlineKeyboardButton categories = InlineKeyboardButton.builder().text("🌳 NGO Categories").callbackData("MENU_CATEGORIES").build();
        InlineKeyboardButton help = InlineKeyboardButton.builder().text("❓ Help & Commands").callbackData("MENU_HELP").build();
        // Arrange buttons in rows (2 per row for compact view)
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(java.util.Arrays.asList(register, ngos));
        rows.add(java.util.Arrays.asList(activities, join));
        rows.add(java.util.Arrays.asList(donate, urgent));
        rows.add(java.util.Arrays.asList(openRequests));
        rows.add(java.util.Arrays.asList(achievements, categories));
        rows.add(java.util.Arrays.asList(help));
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Builds the persistent Reply Keyboard (4 rows × 2 columns).
     * resizeKeyboard = true keeps buttons compact.
     * oneTimeKeyboard = false keeps it always visible.
     */
    private ReplyKeyboardMarkup buildPersistentReplyKeyboard() {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📋 NGOs"));
        row1.add(new KeyboardButton("🎯 Activities"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("👐 Donate"));
        row2.add(new KeyboardButton("🚨 Urgent Help"));

        KeyboardRow row3 = new KeyboardRow();
        row3.add(new KeyboardButton("🏆 Achievements"));
        row3.add(new KeyboardButton("📝 Register"));

        KeyboardRow row4 = new KeyboardRow();
        row4.add(new KeyboardButton("🔗 Join Activity"));
        row4.add(new KeyboardButton("❓ Help"));

        KeyboardRow row5 = new KeyboardRow();
        row5.add(new KeyboardButton("🩸 Open Requests"));
        row5.add(new KeyboardButton("🩸 I Can Donate"));

        ReplyKeyboardMarkup replyKeyboard = new ReplyKeyboardMarkup();
        replyKeyboard.setKeyboard(List.of(row1, row2, row3, row4, row5));
        replyKeyboard.setResizeKeyboard(true);
        replyKeyboard.setOneTimeKeyboard(false);
        return replyKeyboard;
    }

    /** Handles callbacks from inline keyboard buttons. */
    private String processCallback(long chatId, String data) {
        if (data != null && data.startsWith("BLOOD_")) {
            String selectedBlood = data.substring(6).trim().toUpperCase();
            ConversationState state = conversationStates.get(chatId);
            if (state != null && state.getWorkflow() == ConversationState.Workflow.REGISTER_BLOOD_TYPE) {
                return handleWorkflowStep(chatId, selectedBlood, state);
            }
        }

        switch (data) {
            case "MENU_REGISTER":
                ConversationState regState = new ConversationState();
                regState.setWorkflow(ConversationState.Workflow.REGISTER_NAME);
                conversationStates.put(chatId, regState);
                sendResponse(chatId, "🙋 *Registration*\nPlease send your *full name*.");
                return null;
            case "MENU_NGOS":
                conversationStates.remove(chatId);
                return "📋 *Registered NGOs*\n\n" + platformService.listNgos();
            case "MENU_ACTIVITIES":
                conversationStates.remove(chatId);
                return "🌟 *Upcoming Volunteer Activities*\n\n" + platformService.listActivities();
            case "MENU_JOIN":
                ConversationState joinState = new ConversationState();
                joinState.setWorkflow(ConversationState.Workflow.JOIN_SELECT_ID);
                conversationStates.put(chatId, joinState);
                sendResponse(chatId, "🤝 *Join Activity*\nSend the *activity ID* you wish to join (type `ACTIVITIES` to view).");
                return null;
            case "MENU_DONATE":
                ConversationState dState = new ConversationState();
                dState.setWorkflow(ConversationState.Workflow.DONATE_ITEM);
                conversationStates.put(chatId, dState);
                sendResponse(chatId, "🎁 *Donate Items*\nWhat *item* would you like to donate?");
                return null;
            case "MENU_URGENT":
                // Must be registered before starting the urgent-request workflow
                if (!isRegistered(chatId)) {
                    return URGENT_REGISTER_FIRST_MESSAGE;
                }
                ConversationState uState = new ConversationState();
                uState.setWorkflow(ConversationState.Workflow.URGENT_TYPE);
                conversationStates.put(chatId, uState);
                sendResponse(chatId, "🚨 *Urgent Help*\nSpecify the *type* (`BLOOD` or `FOOD`).");
                return null;
            case "MENU_OPENREQUESTS":
                conversationStates.remove(chatId);
                return platformService.listOpenRequests();
            case "MENU_ACHIEVEMENTS":
                conversationStates.remove(chatId);
                return platformService.listAchievements();
            case "MENU_CATEGORIES":
                conversationStates.remove(chatId);
                return "🌳 *NGO Category Hierarchy*\n```\n" + platformService.printCategories() + "```";
            case "MENU_HELP":
                conversationStates.remove(chatId);
                // Show help text without buttons
                return buildHelpText(chatId);
            default:
                return "❓ Unknown menu option. Use /help or /start to view available commands.";
        }
    }

    /** Handles the next step of a multi‑step workflow. */
    private String handleWorkflowStep(long chatId, String input, ConversationState state) {
        switch (state.getWorkflow()) {
            case REGISTER_NAME:
                state.put("name", input);
                state.setWorkflow(ConversationState.Workflow.REGISTER_EMAIL);
                return "✉️ Please provide your *email address* for registration.";
            case REGISTER_EMAIL:
                String email = input.trim();
                if (!isValidEmail(email)) {
                    return "❌ Invalid email format. Please send a valid email address (e.g. `user@example.com`).";
                }
                state.put("email", email);
                state.setWorkflow(ConversationState.Workflow.REGISTER_BLOOD_TYPE);
                sendMessageWithKeyboard(chatId, "🩸 What is your blood type? (A+, A-, B+, B-, AB+, AB-, O+ or O-)", buildBloodTypeKeyboard());
                return null;
            case REGISTER_BLOOD_TYPE:
                String regBlood = input.trim().toUpperCase();
                if (!isValidBloodType(regBlood)) {
                    return "Please enter a valid blood type: A+, A-, B+, B-, AB+, AB-, O+ or O-";
                }
                String regName = state.get("name");
                String regEmail = state.get("email");
                Volunteer regVol = platformService.registerVolunteer(chatId, regName, regEmail, regBlood);
                state.clear();
                return String.format(
                        "🎉 *Registration Successful!*\n\n"
                                + "👤 Name: *%s*\n"
                                + "📧 Email: *%s*\n"
                                + "🆔 Chat ID: `%d`\n"
                                + "🩸 Blood Type: %s\n\n"
                                + "You can now join activities with `JOIN <id>` or donate with `DONATE <item> <qty>`.",
                        regVol.getName(), regVol.getEmail(), chatId, regVol.getBloodType());
            case DONATE_ITEM:
                state.put("item", input);
                state.setWorkflow(ConversationState.Workflow.DONATE_QUANTITY);
                return "📦 How many *" + input + "* would you like to pledge?";
            case DONATE_QUANTITY:
                String item = state.get("item");
                try {
                    int qty = Integer.parseInt(input.trim());
                    if (qty <= 0) {
                        return "❌ Quantity must be positive. Please send a valid number.";
                    }
                    state.put("quantity", String.valueOf(qty));
                    state.setWorkflow(ConversationState.Workflow.DONATE_CATEGORY);
                    return "What category is this? (Clothes / Food / Books / Electronics / Medical / Other)";
                } catch (NumberFormatException e) {
                    return "❌ Invalid number. Please send the quantity as a numeric value.";
                }
            case DONATE_CATEGORY:
                String matchedCat = DONATE_CATEGORIES.get(input.trim().toUpperCase());
                if (matchedCat == null) {
                    return "❌ Invalid category. Please choose from: Clothes, Food, Books, Electronics, Medical, Other.\n\n"
                            + "What category is this? (Clothes / Food / Books / Electronics / Medical / Other)";
                }
                state.put("category", matchedCat);
                if (matchedCat.equalsIgnoreCase("Food") || matchedCat.equalsIgnoreCase("Medical")) {
                    state.put("condition", "N/A");
                    state.setWorkflow(ConversationState.Workflow.DONATE_DELIVERY_METHOD);
                    return "Would you like this picked up, or will you drop it off? (Pickup / Drop-off)";
                } else {
                    state.setWorkflow(ConversationState.Workflow.DONATE_CONDITION);
                    return "What is the condition of the item(s)? (New / Gently Used / Old)";
                }
            case DONATE_CONDITION:
                String matchedCond = DONATE_CONDITIONS.get(input.trim().toUpperCase());
                if (matchedCond == null) {
                    return "❌ Invalid condition. Please choose from: New, Gently Used, Old.\n\n"
                            + "What is the condition of the item(s)? (New / Gently Used / Old)";
                }
                state.put("condition", matchedCond);
                state.setWorkflow(ConversationState.Workflow.DONATE_DELIVERY_METHOD);
                return "Would you like this picked up, or will you drop it off? (Pickup / Drop-off)";
            case DONATE_DELIVERY_METHOD:
                String matchedMethod = normalizeDeliveryMethod(input);
                if (matchedMethod == null) {
                    return "❌ Invalid choice. Please choose either Pickup or Drop-off.\n\n"
                            + "Would you like this picked up, or will you drop it off? (Pickup / Drop-off)";
                }
                state.put("deliveryMethod", matchedMethod);
                if (matchedMethod.equals("Pickup")) {
                    state.setWorkflow(ConversationState.Workflow.DONATE_PICKUP_ADDRESS);
                    return "What is your pickup address?";
                } else {
                    state.put("pickupAddress", null);
                    state.put("preferredTime", null);
                    String dItem = state.get("item");
                    int dQty = Integer.parseInt(state.get("quantity"));
                    String dCat = state.get("category");
                    String dCond = state.get("condition");
                    String dMethod = state.get("deliveryMethod");
                    String result = platformService.recordDonation(chatId, dItem, dQty, dCat, dCond, dMethod, null, null);
                    state.clear();
                    return result;
                }
            case DONATE_PICKUP_ADDRESS:
                state.put("pickupAddress", input.trim());
                state.setWorkflow(ConversationState.Workflow.DONATE_PICKUP_TIME);
                return "What date and time works for pickup? (e.g. 25 Nov, 5pm)";
            case DONATE_PICKUP_TIME:
                state.put("preferredTime", input.trim());
                String pItem = state.get("item");
                int pQty = Integer.parseInt(state.get("quantity"));
                String pCat = state.get("category");
                String pCond = state.get("condition");
                String pMethod = state.get("deliveryMethod");
                String pAddress = state.get("pickupAddress");
                String pTime = state.get("preferredTime");
                String pResult = platformService.recordDonation(chatId, pItem, pQty, pCat, pCond, pMethod, pAddress, pTime);
                state.clear();
                return pResult;
            case URGENT_TYPE:
                String type = input.trim().toUpperCase();
                if (!type.equals("BLOOD") && !type.equals("FOOD")) {
                    return "❌ Invalid type. Use `BLOOD` or `FOOD`.";
                }
                state.put("type", type);
                if (type.equals("BLOOD")) {
                    state.setWorkflow(ConversationState.Workflow.URGENT_BLOOD_TYPE);
                    return "What blood type is needed? (A+, A-, B+, B-, AB+, AB-, O+, or O-)";
                } else {
                    state.setWorkflow(ConversationState.Workflow.URGENT_DETAILS);
                    return "📝 Please provide *details* of the urgent request.";
                }
            case URGENT_BLOOD_TYPE:
                String bloodInput = input.trim();
                if (!isValidBloodType(bloodInput)) {
                    return "❌ Invalid blood type. Valid options: A+, A-, B+, B-, AB+, AB-, O+, or O-.\n\n"
                            + "What blood type is needed? (A+, A-, B+, B-, AB+, AB-, O+, or O-)";
                }
                state.put("details", bloodInput.toUpperCase());
                state.setWorkflow(ConversationState.Workflow.URGENT_UNITS);
                return "How many units are needed?";
            case URGENT_UNITS:
                try {
                    int units = Integer.parseInt(input.trim());
                    if (units <= 0) {
                        return "❌ Units must be a positive whole number.\n\nHow many units are needed?";
                    }
                    state.put("unitsNeeded", String.valueOf(units));
                    state.setWorkflow(ConversationState.Workflow.URGENT_HOSPITAL);
                    return "What is the hospital or location name?";
                } catch (NumberFormatException e) {
                    return "❌ Please enter a positive whole number.\n\nHow many units are needed?";
                }
            case URGENT_HOSPITAL:
                state.put("hospitalName", input.trim());
                state.setWorkflow(ConversationState.Workflow.URGENT_CITY);
                return "📍 Finally, tell us the *city* where help is needed.";
            case URGENT_DETAILS:
                state.put("details", input.trim());
                state.setWorkflow(ConversationState.Workflow.URGENT_CITY);
                return "📍 Finally, tell us the *city* where help is needed.";
            case URGENT_CITY:
                String uType = state.get("type");
                String city = input.trim();
                if ("FOOD".equalsIgnoreCase(uType)) {
                    String uDetails = state.get("details");
                    String urgentResult = platformService.recordUrgentRequest(chatId, uType, uDetails, city);
                    state.clear();
                    return urgentResult;
                }
                state.put("city", city);
                state.setWorkflow(ConversationState.Workflow.URGENT_REASON);
                return "What is the reason or patient condition? (e.g. Surgery, Accident, Chronic condition) — you may type 'skip' if you prefer not to share.";
            case URGENT_REASON:
                String reasonInput = input.trim();
                if (reasonInput.equalsIgnoreCase("skip")) {
                    state.put("reason", null);
                } else {
                    state.put("reason", reasonInput);
                }
                state.setWorkflow(ConversationState.Workflow.URGENT_CONTACT_PHONE);
                return "What is a contact phone number for this request?";
            case URGENT_CONTACT_PHONE:
                String cleanedPhone = validateAndCleanPhone(input);
                if (cleanedPhone == null) {
                    return "❌ Invalid phone number. Please enter a valid phone number (7 to 15 digits, optional leading +).\n\n"
                            + "What is a contact phone number for this request?";
                }
                String bType = state.get("type");
                String bDetails = state.get("details");
                String bCity = state.get("city");
                int bUnits = Integer.parseInt(state.get("unitsNeeded"));
                String bHospital = state.get("hospitalName");
                String bReason = state.get("reason");
                String urgentResult = platformService.recordUrgentRequest(chatId, bType, bDetails, bCity,
                        bUnits, bHospital, bReason, cleanedPhone);
                state.clear();
                return urgentResult;
            case JOIN_SELECT_ID:
                // Strip optional # prefix and parse as integer
                String idInput = input.trim().replaceAll("^#\\s*", "");
                try {
                    int actId = Integer.parseInt(idInput);
                    String joinResult = platformService.joinActivity(chatId, actId);
                    state.clear();
                    return joinResult;
                } catch (NumberFormatException e) {
                    return "❌ Invalid activity ID. Please send a numeric ID (e.g. `1` or `#8`).\nType `ACTIVITIES` to view the list, or `CANCEL` to abort.";
                }
            case RESPOND_SELECT_ID:
                String reqIdInput = input.trim().replaceAll("^#\\s*", "");
                int resId;
                try {
                    resId = Integer.parseInt(reqIdInput);
                } catch (NumberFormatException e) {
                    return "❌ Invalid Request ID. Please send a numeric ID (e.g. `1` or `#1`).\nType `OPENREQUESTS` to view open requests, or `CANCEL` to abort.";
                }
                UrgentRequest targetReq = platformService.findUrgentRequestById(resId);
                if (targetReq == null) {
                    return "❌ Urgent Request #" + resId + " not found.\nType `OPENREQUESTS` to view all open urgent requests, or `CANCEL` to abort.";
                }
                Volunteer vol = platformService.getVolunteer(chatId);
                if (vol != null && vol.getBloodType() != null && !vol.getBloodType().isBlank() && !vol.getBloodType().equalsIgnoreCase("Not specified")) {
                    String result = platformService.respondToUrgentRequest(chatId, resId, vol.getBloodType());
                    state.clear();
                    return result;
                }
                state.put("requestId", String.valueOf(resId));
                state.setWorkflow(ConversationState.Workflow.RESPOND_BLOOD_TYPE);
                return "What is your blood type? (A+, A-, B+, B-, AB+, AB-, O+, or O-)";
            case RESPOND_BLOOD_TYPE:
                String respondBloodInput = input.trim();
                if (!isValidBloodType(respondBloodInput)) {
                    return "❌ Invalid blood type. Valid options: A+, A-, B+, B-, AB+, AB-, O+, or O-.\n\n"
                            + "What is your blood type? (A+, A-, B+, B-, AB+, AB-, O+, or O-)";
                }
                String donorBlood = respondBloodInput.toUpperCase();
                int respondReqId = Integer.parseInt(state.get("requestId"));
                Volunteer donorVol = platformService.getVolunteer(chatId);
                if (donorVol != null) {
                    donorVol.setBloodType(donorBlood);
                    platformService.persistState();
                }
                String respResult = platformService.respondToUrgentRequest(chatId, respondReqId, donorBlood);
                state.clear();
                return respResult;
            default:
                return null;
        }
    }
}