package com.goodlink.controller;

import com.goodlink.bot.GoodLinkTelegramBot;
import com.goodlink.model.Achievement;
import com.goodlink.model.Activity;
import com.goodlink.model.Donation;
import com.goodlink.model.NGO;
import com.goodlink.model.UrgentRequest;
import com.goodlink.model.Volunteer;
import com.goodlink.service.PlatformService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin & Platform Controller for:
 * - Activity completion & certificate generation (PDF)
 * - Volunteer registration & activity joining
 * - Platform live metrics & data structures state
 * - Direct PDF certificate preview/download
 * - Telegram Bot simulated web console
 * - NGO & Activity additions
 */
@RestController
@RequestMapping("/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    private final PlatformService platformService;
    private final GoodLinkTelegramBot bot;

    public AdminController(PlatformService platformService, @Lazy GoodLinkTelegramBot bot) {
        this.platformService = platformService;
        this.bot = bot;
    }

    private boolean isAdmin(HttpHeaders headers) {
        String adminKey = System.getenv("ADMIN_KEY");
        if (adminKey == null || adminKey.isEmpty()) {
            return false;
        }
        String headerKey = headers.getFirst("X-Admin-Key");
        return adminKey.equals(headerKey);
    }

    /**
     * Marks participation complete, generates PDF certificate, and emails as attachment.
     */
    @PostMapping("/complete/{activityId}/{chatId}")
    public ResponseEntity<Map<String, Object>> markComplete(
            @PathVariable("activityId") int activityId,
            @PathVariable("chatId") long chatId,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String result = platformService.completeParticipation(activityId, chatId);

        if (result.startsWith("SUCCESS")) {
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "activityId", activityId,
                    "chatId", chatId,
                    "message", result
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "ERROR",
                    "activityId", activityId,
                    "chatId", chatId,
                    "message", result
            ));
        }
    }

    /**
     * Directly generates and returns the OpenPDF certificate bytes for preview/download in browser.
     */
    @GetMapping(value = "/certificate/{activityId}/{chatId}", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadCertificate(
            @PathVariable("activityId") int activityId,
            @PathVariable("chatId") long chatId,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Activity activity = platformService.findActivityById(activityId);
        if (activity == null) {
            return ResponseEntity.notFound().build();
        }

        Volunteer volunteer = activity.getVolunteer(chatId);
        if (volunteer == null) {
            volunteer = platformService.getVolunteer(chatId);
        }
        if (volunteer == null) {
            return ResponseEntity.notFound().build();
        }

        NGO ngo = platformService.findNgoById(activity.getNgoId());
        byte[] pdfBytes = platformService.getPdfCertificateService().generateCertificate(volunteer, activity, ngo);

        HttpHeaders responseHeaders = new HttpHeaders();
        String safeName = volunteer.getName().replaceAll("\\s+", "_");
        responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"GoodLink_Certificate_" + activityId + "_" + safeName + ".pdf\"");

        return ResponseEntity.ok()
                .headers(responseHeaders)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Helper endpoint to register a volunteer via REST (in addition to Telegram).
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerVolunteer(
            @RequestParam("chatId") long chatId,
            @RequestParam("name") String name,
            @RequestParam("email") String email,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Volunteer volunteer = platformService.registerVolunteer(chatId, name, email);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "chatId", volunteer.getChatId(),
                "name", volunteer.getName(),
                "email", volunteer.getEmail()
        ));
    }

    /**
     * Helper endpoint to join an activity via REST (in addition to Telegram).
     */
    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinActivity(
            @RequestParam("chatId") long chatId,
            @RequestParam("activityId") int activityId,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String result = platformService.joinActivity(chatId, activityId);
        return ResponseEntity.ok(Map.of(
                "status", result.contains("✅") ? "SUCCESS" : (result.contains("ℹ️") ? "INFO" : "ERROR"),
                "message", result
        ));
    }

    /**
     * Pledge a donation via REST.
     */
    @PostMapping("/donate")
    public ResponseEntity<Map<String, Object>> pledgeDonation(
            @RequestParam("chatId") long chatId,
            @RequestParam("item") String item,
            @RequestParam("quantity") int quantity,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String result = platformService.recordDonation(chatId, item, quantity);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", result
        ));
    }

    /**
     * File an urgent request via REST.
     */
    @PostMapping("/urgent")
    public ResponseEntity<Map<String, Object>> createUrgentRequest(
            @RequestParam("chatId") long chatId,
            @RequestParam("type") String type,
            @RequestParam("details") String details,
            @RequestParam("city") String city,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String result = platformService.recordUrgentRequest(chatId, type, details, city);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", result
        ));
    }

    /**
     * Simulates a Telegram message to test bot commands directly in the web UI.
     */
    @PostMapping("/bot/simulate")
    public ResponseEntity<Map<String, Object>> simulateBotCommand(
            @RequestParam(value = "chatId", defaultValue = "999888777") long chatId,
            @RequestParam("message") String message,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String reply = (bot != null)
                ? bot.processCommand(chatId, message)
                : "GoodLinkTelegramBot is currently uninitialized.";

        return ResponseEntity.ok(Map.of(
                "chatId", chatId,
                "input", message,
                "reply", reply
        ));
    }

    /**
     * Platform health and data structures status dashboard.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getPlatformStatus(@RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of(
                "platform", "GoodLink NGO Volunteer Platform",
                "ngosInBST", platformService.getNgoBST().size(),
                "activitiesInArray", platformService.getActivities().size(),
                "donationsInQueue", platformService.getDonations().size(),
                "urgentRequestsQueue", Map.of(
                        "total", platformService.getUrgentRequests().size(),
                        "urgent", platformService.getUrgentRequests().getUrgentCount(),
                        "normal", platformService.getUrgentRequests().getNormalCount()
                ),
                "achievementsOnWall", platformService.getAchievements().size(),
                "volunteersCount", platformService.getVolunteers().size()
        ));
    }

    // Overloaded methods for tests (no HttpHeaders) -------------------------------------------------
    public ResponseEntity<java.util.Map<String, Object>> getPlatformStatus() {
        return getPlatformStatus(new HttpHeaders());
    }

    public ResponseEntity<java.util.Map<String, Object>> markComplete(int activityId, long chatId) {
        return markComplete(activityId, chatId, new HttpHeaders());
    }

    public ResponseEntity<byte[]> downloadCertificate(int activityId, long chatId) {
        return downloadCertificate(activityId, chatId, new HttpHeaders());
    }

    /**
     * Returns full data snapshot across all custom data structures for the frontend visualizer.
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> getFullData(@RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 1. NGOs from NgoBST (in-order)
        List<NGO> ngos = new ArrayList<>();
        platformService.getNgoBST().inOrder(ngos::add);

        // 2. Activities from ActivityArray
        List<Map<String, Object>> activitiesList = new ArrayList<>();
        platformService.getActivities().forEach(act -> {
            NGO ngo = platformService.findNgoById(act.getNgoId());
            List<Map<String, Object>> volList = new ArrayList<>();
            act.getVolunteers().forEach(v -> volList.add(Map.of(
                    "chatId", v.getChatId(),
                    "name", v.getName(),
                    "email", v.getEmail()
            )));

            Map<String, Object> map = new HashMap<>();
            map.put("id", act.getId());
            map.put("ngoId", act.getNgoId());
            map.put("ngoName", (ngo != null) ? ngo.getName() : "Partner NGO");
            map.put("title", act.getTitle());
            map.put("date", act.getDate());
            map.put("volunteerCount", act.getVolunteers().size());
            map.put("volunteers", volList);
            activitiesList.add(map);
        });

        // 3. Registered Volunteers
        List<Volunteer> volunteerList = new ArrayList<>();
        platformService.getVolunteers().forEach(volunteerList::add);

        // 4. Donations Queue (FIFO)
        List<Donation> donationList = new ArrayList<>();
        platformService.getDonations().forEach(donationList::add);

        // 5. Urgent Requests (Urgent + Normal)
        List<UrgentRequest> urgentList = new ArrayList<>();
        platformService.getUrgentRequests().forEach(urgentList::add);

        // 6. Achievements Wall (Newest First)
        List<Achievement> achievementList = new ArrayList<>();
        platformService.getAchievements().forEach(achievementList::add);

        // 7. Categories ASCII tree diagram
        String categoryTreeAscii = platformService.printCategories();

        Map<String, Object> response = new HashMap<>();
        response.put("ngos", ngos);
        response.put("activities", activitiesList);
        response.put("volunteers", volunteerList);
        response.put("donations", donationList);
        response.put("urgentRequests", urgentList);
        response.put("achievements", achievementList);
        response.put("categoryTreeAscii", categoryTreeAscii);
        response.put("stats", Map.of(
                "ngosCount", ngos.size(),
                "activitiesCount", activitiesList.size(),
                "volunteersCount", volunteerList.size(),
                "donationsCount", donationList.size(),
                "urgentCount", platformService.getUrgentRequests().getUrgentCount(),
                "normalRequestsCount", platformService.getUrgentRequests().getNormalCount(),
                "achievementsCount", achievementList.size()
        ));

        return ResponseEntity.ok(response);
    }

    /**
     * Helper to add a new NGO to the NgoBST.
     */
    @PostMapping("/ngo")
    public ResponseEntity<Map<String, Object>> addNgo(
            @RequestParam("id") int id,
            @RequestParam("name") String name,
            @RequestParam("category") String category,
            @RequestParam("city") String city,
            @RequestParam("email") String email,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NGO ngo = new NGO(id, name, category, city, email);
        platformService.addNgo(ngo);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "NGO #" + id + " (" + name + ") successfully added into NgoBST."
        ));
    }

    /**
     * Helper to add a new Activity to the ActivityArray.
     */
    @PostMapping("/activity")
    public ResponseEntity<Map<String, Object>> addActivity(
            @RequestParam("id") int id,
            @RequestParam("ngoId") int ngoId,
            @RequestParam("title") String title,
            @RequestParam("date") String date,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Activity activity = new Activity(id, ngoId, title, date);
        platformService.addActivity(activity);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Activity #" + id + " (\"" + title + "\") added into ActivityArray."
        ));
    }

    /**
     * Updates an NGO's contact email at runtime (REST counterpart to NGOREGISTER).
     */
    @PostMapping({"/ngo/register", "/ngoregister"})
    public ResponseEntity<Map<String, Object>> updateNgoEmail(
            @RequestParam("ngoId") int ngoId,
            @RequestParam("contactEmail") String contactEmail,
            @RequestHeader HttpHeaders headers) {
        if (!isAdmin(headers)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        NGO ngo = platformService.updateNgoEmail(ngoId, contactEmail);
        if (ngo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "ERROR",
                    "message", "NGO #" + ngoId + " not found."
            ));
        }
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "ngoId", ngo.getId(),
                "name", ngo.getName(),
                "contactEmail", ngo.getEmail(),
                "message", "NGO #" + ngoId + " (" + ngo.getName() + ") contact email updated to " + contactEmail
        ));
    }
}