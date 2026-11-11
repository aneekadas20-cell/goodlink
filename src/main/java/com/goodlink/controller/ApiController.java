package com.goodlink.controller;

import com.goodlink.model.Activity;
import com.goodlink.model.NGO;
import com.goodlink.model.PotentialDonor;
import com.goodlink.model.UrgentRequest;
import com.goodlink.model.Volunteer;
import com.goodlink.service.PlatformService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Public REST API for Android app. Endpoints under /api/** do NOT require admin key.
 * All responses are JSON.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ApiController {

    private final PlatformService platformService;
    // simple atomic counter for generating unique negative chat IDs
    private static final AtomicLong negativeChatIdGenerator = new AtomicLong(-1L);

    public ApiController(PlatformService platformService) {
        this.platformService = platformService;
    }

    // ---------- Helper methods ----------
    private static final Set<String> VALID_BLOOD_TYPES = Set.of(
            "A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-"
    );

    private String normalizeAndValidateBloodType(String bloodType) {
        if (bloodType == null) {
            return null;
        }
        String normalized = bloodType.trim().toUpperCase();
        if (VALID_BLOOD_TYPES.contains(normalized)) {
            return normalized;
        }
        return null;
    }

    private Volunteer getOrCreateVolunteerByEmail(String email) {
        // search existing volunteers
        {
            final Volunteer[] match = {null};
            platformService.getVolunteers().forEach(v -> {
                if (v.getEmail().equalsIgnoreCase(email)) {
                    match[0] = v;
                }
            });
            if (match[0] != null) {
                return match[0];
            }
        }
        // not found: create with unique negative chatId and a placeholder name
        long chatId = negativeChatIdGenerator.getAndDecrement();
        Volunteer vol = platformService.registerVolunteer(chatId, "Volunteer_" + Math.abs(chatId), email);
        return vol;
    }

    // ---------- Endpoints ----------
    @GetMapping("/ngos")
    public ResponseEntity<List<Map<String, Object>>> listNgos() {
        List<Map<String, Object>> result = new ArrayList<>();
        platformService.getNgoBST().inOrder(ngo -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", ngo.getId());
            m.put("name", ngo.getName());
            m.put("category", ngo.getCategory());
            m.put("city", ngo.getCity());
            m.put("email", ngo.getEmail());
            result.add(m);
        });
        return ResponseEntity.ok(result);
    }

    @GetMapping("/activities")
    public ResponseEntity<List<Map<String, Object>>> listActivities() {
        List<Map<String, Object>> result = new ArrayList<>();
        platformService.getActivities().forEach(act -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", act.getId());
            m.put("ngoId", act.getNgoId());
            m.put("title", act.getTitle());
            m.put("date", act.getDate());
            m.put("volunteerCount", act.getVolunteers().size());
            result.add(m);
        });
        return ResponseEntity.ok(result);
    }

    @GetMapping("/drives")
    public ResponseEntity<List<Map<String, Object>>> listDrives() {
        List<Map<String, Object>> result = new ArrayList<>();
        platformService.getActivities().forEach(act -> {
            if (act.getTitle().toLowerCase().contains("awareness")) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", act.getId());
                m.put("ngoId", act.getNgoId());
                m.put("title", act.getTitle());
                m.put("date", act.getDate());
                m.put("volunteerCount", act.getVolunteers().size());
                result.add(m);
            }
        });
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerVolunteer(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String email = body.get("email");
        if (name == null || email == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Missing name or email"));
        }

        String validatedBloodType = null;
        if (body.containsKey("bloodType") && body.get("bloodType") != null) {
            String rawBlood = body.get("bloodType");
            validatedBloodType = normalizeAndValidateBloodType(rawBlood);
            if (validatedBloodType == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "ERROR", "message", "Invalid blood type"));
            }
        }

        // Find existing volunteer by email or create new
        final Volunteer[] match = {null};
        platformService.getVolunteers().forEach(vol -> {
            if (match[0] == null && vol.getEmail() != null && vol.getEmail().equalsIgnoreCase(email.trim())) {
                match[0] = vol;
            }
        });
        Volunteer existing = match[0];

        Volunteer v;
        if (existing != null) {
            existing.setName(name);
            if (validatedBloodType != null) {
                existing.setBloodType(validatedBloodType);
            }
            platformService.persistState();
            v = existing;
        } else {
            long chatId = negativeChatIdGenerator.getAndDecrement();
            v = platformService.registerVolunteer(chatId, name, email);
            if (validatedBloodType != null) {
                v.setBloodType(validatedBloodType);
                platformService.persistState();
            }
        }

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "chatId", v.getChatId(), "name", v.getName(), "email", v.getEmail()));
    }

    @PostMapping("/activities/{id}/join")
    public ResponseEntity<Map<String, Object>> joinActivity(@PathVariable("id") int activityId,
                                                             @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Missing email"));
        }
        Volunteer vol = getOrCreateVolunteerByEmail(email);
        String result = platformService.joinActivity(vol.getChatId(), activityId);
        String status = result.contains("✅") ? "SUCCESS" : (result.contains("ℹ️") ? "INFO" : "ERROR");
        return ResponseEntity.ok(Map.of("status", status, "message", result));
    }

    @PostMapping("/donations")
    public ResponseEntity<Map<String, Object>> recordDonation(@RequestBody Map<String, Object> body) {
        String email = (String) body.get("email");
        String item = (String) body.get("item");
        Integer quantityObj = (Integer) body.get("quantity");
        if (email == null || item == null || quantityObj == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Missing fields"));
        }

        String category = body.containsKey("category") && body.get("category") != null
                ? String.valueOf(body.get("category")) : "Other";
        String condition = body.containsKey("condition") && body.get("condition") != null
                ? String.valueOf(body.get("condition")) : "Not specified";
        String deliveryMethod = body.containsKey("deliveryMethod") && body.get("deliveryMethod") != null
                ? String.valueOf(body.get("deliveryMethod")) : "Drop-off";
        String pickupAddress = body.containsKey("pickupAddress") && body.get("pickupAddress") != null
                ? String.valueOf(body.get("pickupAddress")) : null;
        String preferredTime = body.containsKey("preferredTime") && body.get("preferredTime") != null
                ? String.valueOf(body.get("preferredTime")) : null;

        Volunteer vol = getOrCreateVolunteerByEmail(email);
        String result = platformService.recordDonation(vol.getChatId(), item, quantityObj,
                category, condition, deliveryMethod, pickupAddress, preferredTime);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", result));
    }

    @PostMapping("/urgent")
    public ResponseEntity<Map<String, Object>> createUrgent(@RequestBody Map<String, Object> body) {
        String type = body.get("type") != null ? String.valueOf(body.get("type")) : null;
        String details = body.get("details") != null ? String.valueOf(body.get("details")) : null;
        String city = body.get("city") != null ? String.valueOf(body.get("city")) : null;
        String email = body.get("email") != null ? String.valueOf(body.get("email")) : null; // optional
        if (type == null || details == null || city == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Missing fields"));
        }

        int unitsNeeded = 1;
        if (body.containsKey("unitsNeeded") && body.get("unitsNeeded") != null) {
            try {
                unitsNeeded = Integer.parseInt(String.valueOf(body.get("unitsNeeded")).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String hospitalName = body.containsKey("hospitalName") && body.get("hospitalName") != null
                ? String.valueOf(body.get("hospitalName")) : "Not specified";
        String reason = body.containsKey("reason") && body.get("reason") != null
                ? String.valueOf(body.get("reason")) : null;
        String contactPhone = body.containsKey("contactPhone") && body.get("contactPhone") != null
                ? String.valueOf(body.get("contactPhone")) : "Not provided";

        long chatId;
        if (email != null) {
            Volunteer vol = getOrCreateVolunteerByEmail(email);
            chatId = vol.getChatId();
        } else {
            chatId = negativeChatIdGenerator.getAndDecrement();
        }
        String result = platformService.recordUrgentRequest(chatId, type, details, city,
                unitsNeeded, hospitalName, reason, contactPhone);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", result));
    }

    @GetMapping("/urgent")
    public ResponseEntity<List<Map<String, Object>>> listUrgent() {
        List<Map<String, Object>> list = new ArrayList<>();
        platformService.getUrgentRequests().forEach(req -> {
            Map<String, Object> m = new HashMap<>();
            m.put("requestId", req.getRequestId());
            m.put("type", req.getType());
            m.put("details", req.getDetails());
            m.put("city", req.getCity());
            m.put("urgent", req.isUrgent());
            m.put("date", req.getDate());
            m.put("unitsNeeded", req.getUnitsNeeded());
            m.put("hospitalName", req.getHospitalName());
            m.put("reason", req.getReason());
            m.put("contactPhone", req.getContactPhone());
            List<Map<String, Object>> donors = new ArrayList<>();
            if (req.getPotentialDonors() != null) {
                for (PotentialDonor d : req.getPotentialDonors()) {
                    Map<String, Object> dm = new HashMap<>();
                    dm.put("chatId", d.getChatId());
                    dm.put("name", d.getName());
                    dm.put("email", d.getEmail());
                    dm.put("bloodType", d.getBloodType());
                    donors.add(dm);
                }
            }
            m.put("potentialDonors", donors);
            list.add(m);
        });
        return ResponseEntity.ok(list);
    }

    @RequestMapping(value = "/urgent/{id}/respond", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> respondToUrgent(
            @PathVariable("id") int id,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam(value = "email", required = false) String queryEmail,
            @RequestParam(value = "bloodType", required = false) String queryBloodType) {

        String email = null;
        String bloodType = null;
        if (body != null) {
            if (body.get("email") != null) {
                email = String.valueOf(body.get("email")).trim();
            }
            if (body.get("bloodType") != null) {
                bloodType = String.valueOf(body.get("bloodType")).trim();
            }
        }
        if ((email == null || email.isBlank()) && queryEmail != null) {
            email = queryEmail.trim();
        }
        if ((bloodType == null || bloodType.isBlank()) && queryBloodType != null) {
            bloodType = queryBloodType.trim();
        }

        if (email == null || email.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Missing email"));
        }

        UrgentRequest req = platformService.findUrgentRequestById(id);
        if (req == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "ERROR", "message", "Urgent Request #" + id + " not found"));
        }

        Volunteer vol = getOrCreateVolunteerByEmail(email);
        String savedBlood = normalizeAndValidateBloodType(vol.getBloodType());
        String effectiveBlood;
        if (savedBlood != null) {
            // Volunteer's saved blood type wins - ignore any different bloodType in request
            effectiveBlood = savedBlood;
        } else {
            // None is saved: use bloodType from body (or query param) and save on volunteer
            String incoming = normalizeAndValidateBloodType(bloodType);
            if (incoming != null) {
                vol.setBloodType(incoming);
                platformService.persistState();
                effectiveBlood = incoming;
            } else {
                effectiveBlood = (bloodType != null && !bloodType.isBlank())
                        ? bloodType.trim().toUpperCase()
                        : "Not specified";
            }
        }

        String result = platformService.respondToUrgentRequest(vol.getChatId(), id, effectiveBlood);
        String status = result.startsWith("SUCCESS") ? "SUCCESS" : (result.contains("already") ? "INFO" : "ERROR");
        return ResponseEntity.ok(Map.of("status", status, "message", result, "requestId", id));
    }

    @GetMapping("/achievements")
    public ResponseEntity<List<Map<String, Object>>> listAchievements() {
        List<Map<String, Object>> result = new ArrayList<>();
        platformService.getAchievements().forEach(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("title", a.getTitle());
            m.put("description", a.getDescription());
            m.put("date", a.getDate());
            m.put("volunteerName", a.getVolunteerName());
            result.add(m);
        });
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ngo-applications")
    public ResponseEntity<Map<String, Object>> applyNgo(@RequestBody Map<String, String> body) {
        String ngoIdStr = body.get("ngoId");
        String name = body.get("name");
        String email = body.get("email");
        String message = body.get("message");
        if (ngoIdStr == null || name == null || email == null || message == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Missing fields"));
        }
        int ngoId;
        try {
            ngoId = Integer.parseInt(ngoIdStr);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "ERROR", "message", "Invalid ngoId"));
        }
        NGO ngo = platformService.findNgoById(ngoId);
        if (ngo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "ERROR", "message", "NGO not found"));
        }
        // Send email to NGO (respecting override)
        if (platformService.getEmailService() != null) {
            String subject = "NGO Application from " + name;
            String bodyText = "Applicant Name: " + name + "\nEmail: " + email + "\nMessage: " + message;
            platformService.getEmailService().sendNgoEmail(ngo.getEmail(), subject, bodyText);
            // Confirmation to applicant
            platformService.getEmailService().sendVolunteerEmail(email, "Application Received", "Dear " + name + ",\n\nYour application to " + ngo.getName() + " has been received. We will get back to you soon.\n\nBest regards,\nGoodLink Team");
        }
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Application sent"));
    }
}
