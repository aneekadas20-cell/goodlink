package com.goodlink.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.goodlink.ds.ActivityArray;
import com.goodlink.ds.CategoryTree;
import com.goodlink.ds.MyLinkedList;
import com.goodlink.ds.MyQueue;
import com.goodlink.ds.NgoBST;
import com.goodlink.ds.UrgentRequests;
import com.goodlink.model.Achievement;
import com.goodlink.model.Activity;
import com.goodlink.model.Donation;
import com.goodlink.model.NGO;
import com.goodlink.model.PotentialDonor;
import com.goodlink.model.UrgentRequest;
import com.goodlink.model.Volunteer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central platform service managing GoodLink's custom data structures.
 */
@Service
public class PlatformService {

    private static final Logger log =
            LoggerFactory.getLogger(PlatformService.class);

    private final NgoBST ngoBST = new NgoBST();
    private final ActivityArray activities = new ActivityArray(10);
    private final CategoryTree categoryTree =
            new CategoryTree("NGO Categories");
    private final MyLinkedList<Volunteer> volunteers =
            new MyLinkedList<>();
    private final MyQueue<Donation> donations = new MyQueue<>();
    private final UrgentRequests urgentRequests = new UrgentRequests();
    private final AtomicInteger nextRequestId = new AtomicInteger(1);
    private final MyLinkedList<Achievement> achievements =
            new MyLinkedList<>();

    private final EmailService emailService;
    private final PdfCertificateService pdfCertificateService;

    public PlatformService(
            EmailService emailService,
            PdfCertificateService pdfCertificateService) {
        this.emailService = emailService;
        this.pdfCertificateService = pdfCertificateService;
    }

    // ================= SAVED STATE (JSON FILE) =================

    public static class NgoData {
        public int id;
        public String name;
        public String category;
        public String city;
        public String email;
    }

    public static class VolunteerData {
        public long chatId;
        public String name;
        public String email;
        public String bloodType;
    }

    public static class ActivityData {
        public int id;
        public int ngoId;
        public String title;
        public String date;
        public List<Long> volunteerChatIds = new ArrayList<>();
    }

    public static class AchievementData {
        public String title;
        public String description;
        public String date;
        public String volunteerName;
    }

    public static class Snapshot {
        public List<NgoData> ngos = new ArrayList<>();
        public List<VolunteerData> volunteers = new ArrayList<>();
        public List<ActivityData> activities = new ArrayList<>();
        public List<AchievementData> achievements = new ArrayList<>();
    }

    private Path dataPath() {
        return Paths.get(
                Optional.ofNullable(System.getenv("DATA_FILE"))
                        .orElse("data/goodlink-data.json"));
    }

    /** Saves NGOs, volunteers, activities, and achievements. */
    public synchronized void persistState() {
        try {
            Snapshot snapshot = new Snapshot();

            ngoBST.inOrder(ngo -> {
                NgoData data = new NgoData();
                data.id = ngo.getId();
                data.name = ngo.getName();
                data.category = ngo.getCategory();
                data.city = ngo.getCity();
                data.email = ngo.getEmail();
                snapshot.ngos.add(data);
            });

            volunteers.forEach(volunteer -> {
                VolunteerData data = new VolunteerData();
                data.chatId = volunteer.getChatId();
                data.name = volunteer.getName();
                data.email = volunteer.getEmail();
                data.bloodType = volunteer.getBloodType();
                snapshot.volunteers.add(data);
            });

            activities.forEach(activity -> {
                ActivityData data = new ActivityData();
                data.id = activity.getId();
                data.ngoId = activity.getNgoId();
                data.title = activity.getTitle();
                data.date = activity.getDate();

                activity.getVolunteers().forEach(
                        volunteer ->
                                data.volunteerChatIds.add(
                                        volunteer.getChatId()));

                snapshot.activities.add(data);
            });

            achievements.forEach(achievement -> {
                AchievementData data = new AchievementData();
                data.title = achievement.getTitle();
                data.description = achievement.getDescription();
                data.date = achievement.getDate();
                data.volunteerName = achievement.getVolunteerName();
                snapshot.achievements.add(data);
            });

            Path path = dataPath();

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(path.toFile(), snapshot);

        } catch (Exception e) {
            log.warn("Could not save GoodLink state: {}", e.getMessage());
        }
    }

    /** Loads saved data when available. */
    private boolean loadPersistedState() {
        Path path = dataPath();

        if (!Files.exists(path)) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(
                    DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);

            Snapshot snapshot =
                    mapper.readValue(path.toFile(), Snapshot.class);

            for (NgoData ngo : snapshot.ngos) {
                ngoBST.insert(new NGO(
                        ngo.id, ngo.name, ngo.category,
                        ngo.city, ngo.email));

                if (emailService != null) {
                    emailService.registerNgoEmail(ngo.email);
                }
            }

            for (VolunteerData data : snapshot.volunteers) {
                volunteers.add(new Volunteer(
                        data.chatId, data.name, data.email, data.bloodType));
            }

            for (ActivityData data : snapshot.activities) {
                Activity activity = new Activity(
                        data.id, data.ngoId, data.title, data.date);

                activities.add(activity);

                for (Long chatId : data.volunteerChatIds) {
                    Volunteer volunteer = getVolunteer(chatId);

                    if (volunteer != null) {
                        activity.addVolunteer(volunteer);
                    }
                }
            }

            // Restore achievements in their saved newest-first order.
            for (int i = snapshot.achievements.size() - 1;
                 i >= 0; i--) {

                AchievementData data = snapshot.achievements.get(i);

                achievements.addFirst(new Achievement(
                        data.title, data.description,
                        data.date, data.volunteerName));
            }

            if (emailService != null) {
                emailService.registerNgoEmail(
                        emailService.getDefaultAdminEmail());
            }

            log.info("Loaded saved GoodLink state from {}", path);
            return true;

        } catch (Exception e) {
            log.warn("Could not load saved state from {}: {}",
                    path, e.getMessage());
            return false;
        }
    }

    // ================= STARTUP =================

    @PostConstruct
    public void seedInitialData() {
        log.info("Initializing GoodLink custom data structures...");
        seedCategories();

        if (loadPersistedState()) {
            return;
        }

        seedDefaults();
    }

    private void seedCategories() {
        categoryTree.addCategory("NGO Categories", "Elder Care");
        categoryTree.addCategory("Elder Care", "Old Age Homes");
        categoryTree.addCategory("Elder Care", "Dementia Care");

        categoryTree.addCategory("NGO Categories", "Child Welfare");
        categoryTree.addCategory("Child Welfare", "Orphanages");
        categoryTree.addCategory("Child Welfare", "Street Education");

        categoryTree.addCategory("NGO Categories", "Health & Nutrition");
        categoryTree.addCategory("Health & Nutrition", "Blood Banks");
        categoryTree.addCategory("Health & Nutrition", "Food Banks");

        categoryTree.addCategory("NGO Categories", "Animal Welfare");
        categoryTree.addCategory("Animal Welfare", "Animal Shelters");

        categoryTree.addCategory("NGO Categories", "Environment");
        categoryTree.addCategory("Environment", "Tree Plantation");

        categoryTree.addCategory("NGO Categories", "Education");
        categoryTree.addCategory("Education", "Community Libraries");

        categoryTree.addCategory(
                "NGO Categories", "Mental Wellness / Awareness");
        categoryTree.addCategory(
                "Mental Wellness / Awareness", "Mental Wellness");
        categoryTree.addCategory(
                "Mental Wellness / Awareness", "Awareness");
        categoryTree.addCategory(
                "Mental Wellness / Awareness", "Youth Awareness");
    }

    private void seedDefaults() {
        // NGOs stored in the BST.
        ngoBST.insert(new NGO(
                101, "Silver Years Elder Haven",
                "Old Age Homes", "Mumbai", "admin@silveryears.org"));

        ngoBST.insert(new NGO(
                102, "Little Sparks Children Foundation",
                "Orphanages", "Delhi", "contact@littlesparks.org"));

        ngoBST.insert(new NGO(
                103, "LifePulse Blood Network",
                "Blood Banks", "Bengaluru", "relief@lifepulse.org"));

        ngoBST.insert(new NGO(
                104, "Annapurna Food Rescue",
                "Food Banks", "Pune", "help@annapurna.org"));

        ngoBST.insert(new NGO(
                105, "Paws & Tails Shelter",
                "Animal Shelters", "Hyderabad", "care@pawstails.org"));

        ngoBST.insert(new NGO(
                106, "Green Roots Initiative",
                "Environment", "Bengaluru",
                "contact@greenroots.example.org"));

        ngoBST.insert(new NGO(
                107, "ReadRise Foundation",
                "Education", "Kolkata",
                "info@readrise.example.org"));

        ngoBST.insert(new NGO(
                108, "Mindful Youth Initiative",
                "Mental Wellness / Awareness", "Delhi",
                "help@mindfulyouth.example.org"));

        if (emailService != null) {
            emailService.registerNgoEmail("admin@silveryears.org");
            emailService.registerNgoEmail("contact@littlesparks.org");
            emailService.registerNgoEmail("relief@lifepulse.org");
            emailService.registerNgoEmail("help@annapurna.org");
            emailService.registerNgoEmail("care@pawstails.org");
            emailService.registerNgoEmail(
                    "contact@greenroots.example.org");
            emailService.registerNgoEmail(
                    "info@readrise.example.org");
            emailService.registerNgoEmail(
                    "help@mindfulyouth.example.org");
            emailService.registerNgoEmail(
                    emailService.getDefaultAdminEmail());
        }

        // Activities.
        activities.add(new Activity(
                1, 101, "Weekend Companion Care at Silver Oak",
                "2026-10-25"));

        activities.add(new Activity(
                2, 102, "Weekend Math & Art for Street Children",
                "2026-10-28"));

        activities.add(new Activity(
                3, 103, "Mega Blood Donation Drive at Town Hall",
                "2026-11-02"));

        activities.add(new Activity(
                4, 104, "Community Food Packaging & Distribution",
                "2026-11-05"));

        activities.add(new Activity(
                5, 105, "Weekend Shelter Pet Grooming & Care",
                "2026-11-10"));

        activities.add(new Activity(
                6, 106, "Green Neighbourhood Tree Plantation Drive",
                "2026-11-16"));

        activities.add(new Activity(
                7, 107, "Community Library Setup & Book Donation",
                "2026-11-25"));

        activities.add(new Activity(
                8, 108, "Youth Mental Wellness Awareness Campaign",
                "2026-12-04"));

        // Achievements, newest first.
        achievements.addFirst(new Achievement(
                "Platform Launch",
                "GoodLink NGO Volunteer Platform initialized successfully.",
                "2026-10-01",
                "GoodLink Core Team"));

        achievements.addFirst(new Achievement(
                "Hunger Relief Mission",
                "Successfully packed and distributed 500 meal kits in Pune.",
                "2026-10-12",
                "Annapurna Volunteers"));

        log.info(
                "GoodLink seed data loaded. NGOs in BST: {}, "
                        + "Activities in Array: {}, Achievements: {}",
                ngoBST.size(), activities.size(), achievements.size());
    }

    // ================= VOLUNTEER REGISTRATION =================

    public synchronized Volunteer registerVolunteer(
            long chatId, String name, String email) {
        return registerVolunteer(chatId, name, email, null);
    }

    public synchronized Volunteer registerVolunteer(
            long chatId, String name, String email, String bloodType) {

        Volunteer existing = getVolunteer(chatId);

        if (existing != null) {
            existing.setName(name);
            existing.setEmail(email);
            if (bloodType != null && !bloodType.isBlank()) {
                existing.setBloodType(bloodType);
            }

            log.info("Updated registration for volunteer chatId: {}",
                    chatId);

            persistState();
            return existing;
        }

        Volunteer volunteer = new Volunteer(chatId, name, email, bloodType);
        volunteers.add(volunteer);

        log.info("Registered new volunteer: {} (chatId: {})",
                name, chatId);

        persistState();
        return volunteer;
    }

    public Volunteer getVolunteer(long chatId) {
        final Volunteer[] match = {null};

        volunteers.forEach(volunteer -> {
            if (volunteer.getChatId() == chatId) {
                match[0] = volunteer;
            }
        });

        return match[0];
    }

    // ================= NGOs =================

    public String listNgos() {
        return ngoBST.inOrderPrint();
    }

    public NGO findNgoById(int id) {
        return ngoBST.search(id);
    }

    // ================= ACTIVITIES =================

    public String listActivities() {
        if (activities.size() == 0) {
            return "No activities scheduled at this moment.";
        }

        StringBuilder sb = new StringBuilder();

        activities.forEach(activity -> {
            NGO ngo = ngoBST.search(activity.getNgoId());
            String ngoName =
                    ngo != null ? ngo.getName() : "Unknown NGO";

            sb.append(String.format(
                    "• [#%d] *%s*%n  NGO: %s%n  Date: %s%n"
                            + "  Volunteers Registered: %d%n%n",
                    activity.getId(),
                    activity.getTitle(),
                    ngoName,
                    activity.getDate(),
                    activity.getVolunteers().size()));
        });

        return sb.toString();
    }

    public Activity findActivityById(int id) {
        return activities.findById(id);
    }

    // ================= JOIN ACTIVITY =================

    public synchronized String joinActivity(
            long chatId, int activityId) {

        Activity activity = activities.findById(activityId);

        if (activity == null) {
            return "Activity ID #" + activityId + " not found.";
        }

        Volunteer volunteer = getVolunteer(chatId);

        if (volunteer == null) {
            return "You are not registered on GoodLink. Please register first.";
        }

        if (activity.hasVolunteer(chatId)) {
            return "You have already joined Activity #" + activityId + ".";
        }

        activity.addVolunteer(volunteer);
        persistState();

        NGO ngo = ngoBST.search(activity.getNgoId());
        String ngoName = ngo != null ? ngo.getName() : "the partner NGO";

        boolean emailSent = false;
        String volunteerEmail = volunteer.getEmail();

        if (emailService == null) {
            log.error("Failed to send volunteer confirmation email to {}: EmailService is null", volunteerEmail);
        } else if (volunteerEmail == null || volunteerEmail.isBlank()) {
            log.error("Failed to send volunteer confirmation email: recipient address is null or blank for volunteer {}",
                    volunteer.getName());
        } else {
            emailSent = emailService.sendVolunteerEmail(
                    volunteerEmail,
                    "GoodLink Activity Registration Confirmed",
                    "Dear " + volunteer.getName() + ",\n\n"
                            + "Your registration for \""
                            + activity.getTitle()
                            + "\" has been completed successfully.\n\n"
                            + "NGO: " + ngoName + "\n"
                            + "Activity date: " + activity.getDate() + "\n\n"
                            + "Thank you for volunteering with GoodLink!\n\n"
                            + "Regards,\nGoodLink Platform Team");

            if (!emailSent) {
                String cause = emailService.getLastErrorMessage();
                log.error("Failed to send volunteer confirmation email to {}: {}",
                        volunteerEmail, cause != null ? cause : "Unknown error");
            }
        }

        if (emailSent) {
            return "SUCCESS: You have joined Activity #"
                    + activityId + " - " + activity.getTitle()
                    + ". A confirmation email was sent to "
                    + volunteer.getEmail() + ".";
        }

        return "SUCCESS: You have joined Activity #"
                + activityId + " - " + activity.getTitle()
                + ". However, the confirmation email could not be sent.";
    }

    // ================= RECORD DONATION =================

    private String formatDonationSummary(String category, String condition, String deliveryMethod,
                                         String pickupAddress, String preferredTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("Category: ").append(category != null ? category : "Other");
        sb.append(" | Condition: ").append(condition != null ? condition : "Not specified");
        if (deliveryMethod != null && deliveryMethod.equalsIgnoreCase("Pickup")) {
            sb.append(" | Pickup from: ").append(pickupAddress != null ? pickupAddress : "Not specified");
            if (preferredTime != null && !preferredTime.isBlank()) {
                sb.append(" on ").append(preferredTime);
            }
        } else {
            sb.append(" | Delivery: Drop-off");
        }
        return sb.toString();
    }

    public synchronized String recordDonation(
            long chatId, String item, int quantity) {
        return recordDonation(chatId, item, quantity, "Other", "Not specified", "Drop-off", null, null);
    }

    public synchronized String recordDonation(
            long chatId, String item, int quantity,
            String category, String condition, String deliveryMethod,
            String pickupAddress, String preferredTime) {

        Volunteer volunteer = getVolunteer(chatId);

        if (volunteer == null) {
            return "You are not registered on GoodLink. Please register first.";
        }

        if (item == null || item.trim().isEmpty() || quantity <= 0) {
            return "Invalid donation. Provide an item and a quantity greater than zero.";
        }

        String safeCategory = category != null && !category.isBlank() ? category.trim() : "Other";
        String safeCondition = condition != null && !condition.isBlank() ? condition.trim() : "Not specified";
        String safeDeliveryMethod = deliveryMethod != null && !deliveryMethod.isBlank() ? deliveryMethod.trim() : "Drop-off";
        String safePickupAddress = (pickupAddress != null && !pickupAddress.isBlank()) ? pickupAddress.trim() : null;
        String safePreferredTime = (preferredTime != null && !preferredTime.isBlank()) ? preferredTime.trim() : null;

        String date = LocalDate.now()
                .format(DateTimeFormatter.ISO_DATE);

        Donation donation = new Donation(
                chatId, item.trim(), quantity, date,
                safeCategory, safeCondition, safeDeliveryMethod,
                safePickupAddress, safePreferredTime);

        donations.enqueue(donation);
        persistState();

        String donationSummary = formatDonationSummary(
                safeCategory, safeCondition, safeDeliveryMethod,
                safePickupAddress, safePreferredTime);

        boolean emailSent = false;
        String donorEmail = volunteer.getEmail();

        if (emailService == null) {
            log.error("Failed to send donation receipt email to {}: EmailService is null", donorEmail);
        } else if (donorEmail == null || donorEmail.isBlank()) {
            log.error("Failed to send donation receipt email: recipient address is null or blank for donor {}",
                    volunteer.getName());
        } else {
            String subject = "GoodLink Donation Receipt";
            String body = "Dear " + volunteer.getName() + ",\n\n"
                    + "Thank you for your generous donation pledge to GoodLink!\n\n"
                    + "Donation Details:\n"
                    + "Item: " + item.trim() + "\n"
                    + "Quantity: " + quantity + "\n"
                    + "Date: " + date + "\n"
                    + donationSummary + "\n\n"
                    + "Your donation has been recorded in our queue and will be distributed to partner NGOs in need.\n\n"
                    + "Warm regards,\nGoodLink Platform Team";

            emailSent = emailService.sendVolunteerEmail(donorEmail, subject, body);

            if (!emailSent) {
                String cause = emailService.getLastErrorMessage();
                log.error("Failed to send donation receipt email to {}: {}",
                        donorEmail, cause != null ? cause : "Unknown error");
            }
        }

        return "SUCCESS: Donation recorded!\n"
                + "Item: " + item.trim() + "\n"
                + "Quantity: " + quantity + "\n"
                + donationSummary + "\n"
                + "Thank you, " + volunteer.getName()
                + ", for supporting GoodLink!";
    }

    // ================= RECORD URGENT REQUEST =================

    private String formatBloodSummary(int unitsNeeded, String hospitalName, String reason, String contactPhone) {
        StringBuilder sb = new StringBuilder();
        sb.append("Units needed: ").append(unitsNeeded > 0 ? unitsNeeded : 1);
        sb.append(" | Hospital: ").append(hospitalName != null && !hospitalName.isBlank() ? hospitalName.trim() : "Not specified");
        sb.append(" | Reason: ").append(reason != null && !reason.isBlank() ? reason.trim() : "None specified");
        sb.append(" | Contact: ").append(contactPhone != null && !contactPhone.isBlank() ? contactPhone.trim() : "Not provided");
        return sb.toString();
    }

    public synchronized String recordUrgentRequest(
            long chatId, String type, String details, String city) {
        return recordUrgentRequest(chatId, type, details, city, 1, "Not specified", null, "Not provided");
    }

    public synchronized String recordUrgentRequest(
            long chatId, String type, String details, String city,
            int unitsNeeded, String hospitalName, String reason, String contactPhone) {

        Volunteer volunteer = getVolunteer(chatId);

        if (volunteer == null) {
            return "You are not registered on GoodLink. Please register first.";
        }

        if (type == null || details == null || city == null
                || type.trim().isEmpty()
                || details.trim().isEmpty()
                || city.trim().isEmpty()) {
            return "Invalid request. Provide the type, details, and city.";
        }

        String normalizedType = type.trim().toUpperCase();

        if (!normalizedType.equals("BLOOD")
                && !normalizedType.equals("FOOD")) {
            return "Unsupported request type. Please use BLOOD or FOOD.";
        }

        String date = LocalDate.now()
                .format(DateTimeFormatter.ISO_DATE);

        int safeUnits = unitsNeeded > 0 ? unitsNeeded : 1;
        String safeHospital = hospitalName != null && !hospitalName.isBlank() ? hospitalName.trim() : "Not specified";
        String safeReason = reason != null && !reason.isBlank() ? reason.trim() : null;
        String safePhone = contactPhone != null && !contactPhone.isBlank() ? contactPhone.trim() : "Not provided";

        int reqId = nextRequestId.getAndIncrement();

        UrgentRequest request = new UrgentRequest(
                reqId,
                normalizedType,
                details.trim(),
                city.trim(),
                true,
                date,
                chatId,
                safeUnits,
                safeHospital,
                safeReason,
                safePhone);

        urgentRequests.enqueue(request);
        persistState();

        NGO ngo = findNgoForRequest(normalizedType, city.trim());

        // Local aliases so the NGO email block below reads cleanly.
        Volunteer requester = volunteer;
        String requestType = normalizedType;
        String cleanDetails = details.trim();
        String cleanCity = city.trim();
        boolean urgent = true;
        String today = date;
        String recipientEmail = ngo != null ? ngo.getEmail() : null;
        String bloodSummary = normalizedType.equals("BLOOD")
                ? formatBloodSummary(safeUnits, safeHospital, safeReason, safePhone)
                : null;

        // 1. Alert email to the NGO, with Reply-To set to the requester so the NGO can
        // hit Reply in their email client and reach the requester directly
        String ngoSubject = "\uD83D\uDEA8 URGENT " + requestType + " request #" + reqId + " in " + cleanCity + " \u2014 reply needed";
        StringBuilder ngoBody = new StringBuilder();
        ngoBody.append("An urgent ").append(requestType).append(" request has been filed on GoodLink.\n\n")
                .append("Request ID: #").append(reqId).append("\n")
                .append("Type: ").append(requestType).append("\n")
                .append("Details: ").append(cleanDetails).append("\n")
                .append("City: ").append(cleanCity).append("\n");
        if (bloodSummary != null) {
            ngoBody.append(bloodSummary).append("\n");
        }
        ngoBody.append("Priority: ").append(urgent ? "HIGH" : "NORMAL").append("\n\n")
                .append("REQUESTER CONTACT:\n")
                .append("Name: ").append(requester != null ? requester.getName() : "Not registered").append("\n")
                .append("Email: ").append(requester != null ? requester.getEmail() : "Not available").append("\n")
                .append("Telegram Chat ID: ").append(chatId).append("\n");
        if (contactPhone != null && !contactPhone.isBlank()) {
            ngoBody.append("Phone: ").append(contactPhone.trim()).append("\n");
        }
        ngoBody.append("Date: ").append(today).append("\n\n")
                .append("Please reply directly to this email (it will reach the requester) or contact them ")
                .append("right away. This is time-sensitive.\n\n")
                .append("GoodLink Platform");

        boolean alerted = emailService != null && recipientEmail != null
                && emailService.sendNgoEmailWithReplyTo(
                        recipientEmail, ngoSubject, ngoBody.toString(),
                        requester != null ? requester.getEmail() : null);

        if (!alerted) {
            log.warn("Urgent {} request in {} could not be emailed to an NGO.",
                    requestType, cleanCity);
        }

        // 2. Confirmation email to the requester
        String requesterEmail = requester != null ? requester.getEmail() : null;
        if (emailService == null) {
            log.error("Failed to send urgent request confirmation email to {}: EmailService is null", requesterEmail);
        } else if (requesterEmail == null || requesterEmail.isBlank()) {
            log.error("Failed to send urgent request confirmation email: recipient address is null or blank for requester {}",
                    requester != null ? requester.getName() : "Unknown");
        } else {
            String requesterSubject = "GoodLink Urgent " + requestType + " Request Confirmation #" + reqId;
            StringBuilder requesterBody = new StringBuilder();
            requesterBody.append("Dear ").append(requester.getName()).append(",\n\n")
                    .append("Your urgent ").append(requestType).append(" request has been received and added to our high-priority queue.\n\n")
                    .append("Request Details:\n")
                    .append("Request ID: #").append(reqId).append("\n")
                    .append("Type: ").append(requestType).append("\n")
                    .append("Details: ").append(cleanDetails).append("\n")
                    .append("City: ").append(cleanCity).append("\n");
            if (bloodSummary != null) {
                requesterBody.append(bloodSummary).append("\n");
            }
            requesterBody.append("Date: ").append(today).append("\n\n")
                    .append("We have notified partner NGOs in your area. They will reach out to you directly if they can assist.\n\n")
                    .append("Warm regards,\nGoodLink Platform Team");

            boolean requesterNotified = emailService.sendVolunteerEmail(requesterEmail, requesterSubject, requesterBody.toString());

            if (!requesterNotified) {
                String cause = emailService.getLastErrorMessage();
                log.error("Failed to send urgent request confirmation email to {}: {}",
                        requesterEmail, cause != null ? cause : "Unknown error");
            }
        }

        StringBuilder reply = new StringBuilder();
        reply.append("SUCCESS: Urgent ").append(normalizedType).append(" request #").append(reqId).append(" recorded!\n")
                .append("Details: ").append(details.trim()).append("\n")
                .append("City: ").append(city.trim()).append("\n");
        if (bloodSummary != null) {
            reply.append(bloodSummary).append("\n");
        }
        reply.append("Your request has been added to the urgent queue.");

        return reply.toString();
    }

    public UrgentRequest findUrgentRequestById(int id) {
        return urgentRequests.findById(id);
    }

    public static String formatTimeAgo(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return "recently";
        }
        try {
            LocalDate requestDate = LocalDate.parse(dateStr.trim());
            LocalDate today = LocalDate.now();
            long days = java.time.temporal.ChronoUnit.DAYS.between(requestDate, today);
            if (days <= 0) {
                return "today";
            } else if (days == 1) {
                return "1 day ago";
            } else {
                return days + " days ago";
            }
        } catch (Exception e) {
            return dateStr;
        }
    }

    public String listOpenRequests() {
        if (urgentRequests.isEmpty()) {
            return "No open urgent requests at this moment.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🚨 *Open Urgent Requests:*\n\n");

        urgentRequests.forEach(request -> {
            String timeAgo = formatTimeAgo(request.getDate());
            sb.append(String.format(
                    "• [#%d] *%s Request* in *%s*%n"
                            + "  Details: %s%n"
                            + "  Filed: %s (%s)%n",
                    request.getRequestId(),
                    request.getType(),
                    request.getCity(),
                    request.getDetails(),
                    timeAgo,
                    request.getDate()));
            if (request.getPotentialDonors().isEmpty()) {
                sb.append("  Potential Donors: None yet\n\n");
            } else {
                List<String> donorSummaries = new ArrayList<>();
                for (PotentialDonor d : request.getPotentialDonors()) {
                    donorSummaries.add(d.getName() + " (" + d.getBloodType() + ")");
                }
                sb.append("  Potential Donors: ").append(String.join(", ", donorSummaries)).append("\n\n");
            }
        });

        sb.append("To offer help, use: `RESPOND <requestId>` (e.g. `RESPOND 1`)");
        return sb.toString();
    }

    private static final Map<String, Set<String>> BLOOD_COMPATIBILITY = Map.of(
            "O-", Set.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+"),
            "O+", Set.of("O+", "A+", "B+", "AB+"),
            "A-", Set.of("A-", "A+", "AB-", "AB+"),
            "A+", Set.of("A+", "AB+"),
            "B-", Set.of("B-", "B+", "AB-", "AB+"),
            "B+", Set.of("B+", "AB+"),
            "AB-", Set.of("AB-", "AB+"),
            "AB+", Set.of("AB+")
    );

    public static boolean isBloodCompatible(String donorType, String recipientType) {
        if (donorType == null || recipientType == null) {
            return false;
        }
        Set<String> recipients = BLOOD_COMPATIBILITY.get(donorType.trim().toUpperCase());
        if (recipients == null) {
            return false;
        }
        return recipients.contains(recipientType.trim().toUpperCase());
    }

    public static String extractBloodType(String text) {
        if (text == null) return null;
        String clean = text.trim().toUpperCase();
        for (String bt : List.of("AB+", "AB-", "A+", "A-", "B+", "B-", "O+", "O-")) {
            if (clean.equals(bt)) {
                return bt;
            }
        }
        for (String bt : List.of("AB+", "AB-", "A+", "A-", "B+", "B-", "O+", "O-")) {
            if (clean.contains(bt)) {
                return bt;
            }
        }
        return clean;
    }

    public synchronized String respondToUrgentRequest(long chatId, int requestId, String bloodType) {
        Volunteer volunteer = getVolunteer(chatId);
        if (volunteer == null) {
            return "You are not registered on GoodLink. Please register first with `REGISTER <name> <email>`.";
        }

        UrgentRequest request = urgentRequests.findById(requestId);
        if (request == null) {
            return "❌ Urgent Request #" + requestId + " not found.\nType `OPENREQUESTS` to view all open urgent requests.";
        }

        if (request.hasDonor(chatId)) {
            return "You have already offered to help with Urgent Request #" + requestId + ". Thank you!";
        }

        String safeBlood = bloodType != null && !bloodType.isBlank() ? bloodType.trim().toUpperCase() : "Not specified";
        if (safeBlood.equals("Not specified") && volunteer.getBloodType() != null && !volunteer.getBloodType().isBlank()) {
            safeBlood = volunteer.getBloodType().trim().toUpperCase();
        }

        if (request.getType() != null && request.getType().equalsIgnoreCase("BLOOD")) {
            String neededBlood = extractBloodType(request.getDetails());
            if (!isBloodCompatible(safeBlood, neededBlood)) {
                return "Your blood type (" + safeBlood + ") is not compatible with the needed type (" + neededBlood + ").";
            }
        }

        if (volunteer.getBloodType() == null || volunteer.getBloodType().isBlank()) {
            volunteer.setBloodType(safeBlood);
            persistState();
        }

        PotentialDonor donor = new PotentialDonor(chatId, volunteer.getName(), volunteer.getEmail(), safeBlood);
        request.addPotentialDonor(donor);

        NGO ngo = findNgoForRequest(request.getType(), request.getCity());
        String recipientEmail = ngo != null ? ngo.getEmail() : null;

        if (emailService != null && recipientEmail != null) {
            String subject = "Potential donor for your " + request.getType() + " request #" + requestId;
            String body = "Dear " + (ngo != null ? ngo.getName() : "Partner NGO") + ",\n\n"
                    + "A volunteer has offered to help with your urgent " + request.getType() + " request #" + requestId + "!\n\n"
                    + "REQUEST DETAILS:\n"
                    + "Request ID: #" + requestId + "\n"
                    + "Type: " + request.getType() + "\n"
                    + "Details: " + request.getDetails() + "\n"
                    + "City: " + request.getCity() + "\n"
                    + (request.getType().equalsIgnoreCase("BLOOD")
                            ? "Units Needed: " + request.getUnitsNeeded() + "\nHospital: " + request.getHospitalName() + "\n"
                            : "")
                    + "\nPOTENTIAL DONOR DETAILS:\n"
                    + "Name: " + volunteer.getName() + "\n"
                    + "Email: " + volunteer.getEmail() + "\n"
                    + "Blood Type: " + safeBlood + "\n"
                    + "Telegram Chat ID: " + chatId + "\n\n"
                    + "Please reach out to the volunteer directly to coordinate assistance.\n\n"
                    + "Warm regards,\nGoodLink Platform Team";

            boolean sent = emailService.sendNgoEmailWithReplyTo(recipientEmail, subject, body, volunteer.getEmail());
            if (!sent) {
                log.warn("Could not email NGO {} about donor for request #{}", recipientEmail, requestId);
            }
        }

        return "SUCCESS: Thank you, " + volunteer.getName() + "! Your offer to help ("
                + safeBlood + ") with Urgent Request #" + requestId
                + " has been recorded and forwarded to " + (ngo != null ? ngo.getName() : "the partner NGO")
                + ". They will contact you directly.";
    }

    private NGO findNgoForRequest(String type, String city) {
        final NGO[] match = {null};

        ngoBST.inOrder(ngo -> {
            if (match[0] == null) {
                if (type.equalsIgnoreCase("BLOOD")
                        && ngo.getCategory().contains("Blood")) {
                    match[0] = ngo;
                } else if (type.equalsIgnoreCase("FOOD")
                        && ngo.getCategory().contains("Food")) {
                    match[0] = ngo;
                }
            }
        });

        return match[0];
    }

    // ================= ACHIEVEMENTS =================

    public String listAchievements() {
        if (achievements.isEmpty()) {
            return "No achievements recorded yet.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🏆 *GoodLink Wall of Achievements* (Newest First):\n\n");

        achievements.forEach(achievement -> {
            sb.append(String.format(
                    "🏅 *%s*%n  %s%n  Awarded to: _%s_ on %s%n%n",
                    achievement.getTitle(),
                    achievement.getDescription(),
                    achievement.getVolunteerName(),
                    achievement.getDate()));
        });

        return sb.toString();
    }

    public void addAchievement(Achievement achievement) {
        achievements.addFirst(achievement);
        persistState();
    }

    // ================= CATEGORIES =================

    public String printCategories() {
        return categoryTree.printTree();
    }

    // ================= CERTIFICATES & ADMIN COMPLETION =================

    /**
     * Certificates are generated only through the completion workflow.
     */
    public synchronized String completeParticipation(
            int activityId, long chatId) {

        String result =
                completeParticipationInternal(activityId, chatId);

        if (result.startsWith("SUCCESS")) {
            persistState();
        }

        return result;
    }

    private String completeParticipationInternal(
            int activityId, long chatId) {

        Activity activity = activities.findById(activityId);

        if (activity == null) {
            return "Activity ID #" + activityId + " not found.";
        }

        Volunteer volunteer = activity.getVolunteer(chatId);

        if (volunteer == null) {
            volunteer = getVolunteer(chatId);

            if (volunteer == null) {
                return "Volunteer with Chat ID " + chatId
                        + " is not registered on GoodLink.";
            }

            // Preserve the existing admin completion behaviour.
            activity.addVolunteer(volunteer);
        }

        NGO ngo = ngoBST.search(activity.getNgoId());

        byte[] pdfBytes =
                pdfCertificateService.generateCertificate(
                        volunteer, activity, ngo);

        String ngoName = ngo != null ? ngo.getName() : "Partner NGO";

        String subject =
                "Your Certificate of Completion: " + activity.getTitle();

        String body = String.format(
                "Dear %s,\n\n"
                        + "Congratulations! Your participation in the "
                        + "activity \"%s\" organized with %s has been "
                        + "marked complete.\n\n"
                        + "Attached is your official GoodLink "
                        + "Certificate of Appreciation.\n"
                        + "Thank you for contributing your time and "
                        + "energy to our community!\n\n"
                        + "Warm regards,\nGoodLink Platform Team",
                volunteer.getName(),
                activity.getTitle(),
                ngoName);

        String attachmentFilename =
                "Certificate_" + activity.getId() + "_"
                        + volunteer.getName().replaceAll("\\s+", "_")
                        + ".pdf";

        boolean certSent = emailService.sendVolunteerEmailWithAttachment(
                volunteer.getEmail(),
                subject,
                body,
                attachmentFilename,
                pdfBytes,
                "application/pdf");

        if (!certSent) {
            String cause = emailService.getLastErrorMessage();
            log.error("Failed to send certificate email to {}: {}",
                    volunteer.getEmail(), cause != null ? cause : "Unknown error");
        }

        String today = LocalDate.now()
                .format(DateTimeFormatter.ISO_DATE);

        Achievement achievement = new Achievement(
                "Service Completed: " + activity.getTitle(),
                "Successfully completed volunteering service with "
                        + ngoName + ".",
                today,
                volunteer.getName());

        achievements.addFirst(achievement);

        return String.format(
                "SUCCESS: Marked participation complete for %s "
                        + "(ChatId: %d) on Activity #%d (%s). "
                        + "Certificate PDF generated and emailed to %s. "
                        + "Achievement posted to wall.",
                volunteer.getName(),
                chatId,
                activity.getId(),
                activity.getTitle(),
                volunteer.getEmail());
    }

    // ================= ACCESSORS =================

    public NgoBST getNgoBST() {
        return ngoBST;
    }

    public ActivityArray getActivities() {
        return activities;
    }

    public MyQueue<Donation> getDonations() {
        return donations;
    }

    public UrgentRequests getUrgentRequests() {
        return urgentRequests;
    }

    public MyLinkedList<Achievement> getAchievements() {
        return achievements;
    }

    public MyLinkedList<Volunteer> getVolunteers() {
        return volunteers;
    }

    public void addNgo(NGO ngo) {
        ngoBST.insert(ngo);

        if (emailService != null && ngo != null) {
            emailService.registerNgoEmail(ngo.getEmail());
        }

        persistState();
    }

    public NGO updateNgoEmail(int ngoId, String contactEmail) {
        NGO ngo = ngoBST.search(ngoId);

        if (ngo != null) {
            String oldEmail = ngo.getEmail();
            ngo.setEmail(contactEmail);

            if (emailService != null) {
                emailService.unregisterNgoEmail(oldEmail);
                emailService.registerNgoEmail(contactEmail);
            }

            log.info(
                    "Updated contact email for NGO #{} ({}) from {} to {}",
                    ngoId, ngo.getName(), oldEmail, contactEmail);

            persistState();
        }

        return ngo;
    }

    public EmailService getEmailService() {
        return emailService;
    }

    public void addActivity(Activity activity) {
        activities.add(activity);
        persistState();
    }

    public PdfCertificateService getPdfCertificateService() {
        return pdfCertificateService;
    }

    public CategoryTree getCategoryTree() {
        return categoryTree;
    }
}