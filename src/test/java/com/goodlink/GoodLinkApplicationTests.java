package com.goodlink;

import com.goodlink.bot.GoodLinkTelegramBot;
import com.goodlink.ds.ActivityArray;
import com.goodlink.ds.CategoryTree;
import com.goodlink.ds.MyLinkedList;
import com.goodlink.ds.MyQueue;
import com.goodlink.ds.NgoBST;
import com.goodlink.ds.UrgentRequests;
import com.goodlink.model.Activity;
import com.goodlink.model.NGO;
import com.goodlink.model.UrgentRequest;
import com.goodlink.model.Volunteer;
import com.goodlink.service.EmailService;
import com.goodlink.service.PdfCertificateService;
import com.goodlink.service.PlatformService;
import com.goodlink.controller.AdminController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class GoodLinkApplicationTests {

    @MockBean
    private JavaMailSender mailSender;

    private EmailService emailService;
    private PdfCertificateService pdfService;
    private PlatformService platformService;
    private GoodLinkTelegramBot bot;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        pdfService = new PdfCertificateService();
        platformService = new PlatformService(emailService, pdfService);
        platformService.seedInitialData();
        bot = new GoodLinkTelegramBot(platformService);
    }

    // ================= 1. MyLinkedList Tests =================
    @Test
    @DisplayName("MyLinkedList: add, addFirst, remove, contains, size")
    void testMyLinkedList() {
        MyLinkedList<String> list = new MyLinkedList<>();
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());

        list.add("Alpha");
        list.add("Beta");
        assertEquals(2, list.size());
        assertEquals("Alpha", list.get(0));
        assertEquals("Beta", list.get(1));

        // addFirst (achievements newest first)
        list.addFirst("Zeta");
        assertEquals(3, list.size());
        assertEquals("Zeta", list.get(0));
        assertEquals("Alpha", list.get(1));

        assertTrue(list.contains("Alpha"));
        assertFalse(list.contains("Gamma"));

        // remove
        assertTrue(list.remove("Alpha"));
        assertEquals(2, list.size());
        assertFalse(list.contains("Alpha"));
        assertEquals("Zeta", list.get(0));
        assertEquals("Beta", list.get(1));
    }

    // ================= 2. MyQueue Tests =================
    @Test
    @DisplayName("MyQueue: FIFO order, enqueue, dequeue, peek, size")
    void testMyQueue() {
        MyQueue<Integer> queue = new MyQueue<>();
        assertTrue(queue.isEmpty());
        assertNull(queue.peek());
        assertNull(queue.dequeue());

        queue.enqueue(10);
        queue.enqueue(20);
        queue.enqueue(30);

        assertEquals(3, queue.size());
        assertEquals(10, queue.peek());

        // Dequeue in FIFO order
        assertEquals(10, queue.dequeue());
        assertEquals(20, queue.dequeue());
        assertEquals(1, queue.size());
        assertEquals(30, queue.peek());
        assertEquals(30, queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    // ================= 3. NgoBST Tests =================
    @Test
    @DisplayName("NgoBST: insert, search, in-order print")
    void testNgoBST() {
        NgoBST bst = new NgoBST();
        NGO ngo1 = new NGO(200, "Hope Center", "Elder Care", "Delhi", "hope@ngo.org");
        NGO ngo2 = new NGO(100, "Animal Friends", "Animal Welfare", "Mumbai", "animal@ngo.org");
        NGO ngo3 = new NGO(300, "Life Foundation", "Blood Bank", "Pune", "life@ngo.org");

        bst.insert(ngo1);
        bst.insert(ngo2);
        bst.insert(ngo3);

        assertEquals(3, bst.size());

        // Search
        assertNotNull(bst.search(100));
        assertEquals("Animal Friends", bst.search(100).getName());
        assertNotNull(bst.search(300));
        assertEquals("Life Foundation", bst.search(300).getName());
        assertNull(bst.search(999));

        // In-order print should list 100 first, then 200, then 300
        String output = bst.inOrderPrint();
        assertTrue(output.indexOf("#100") < output.indexOf("#200"));
        assertTrue(output.indexOf("#200") < output.indexOf("#300"));
    }

    // ================= 4. UrgentRequests Tests =================
    @Test
    @DisplayName("UrgentRequests: Urgent queue is ALWAYS served before normal queue")
    void testUrgentRequests() {
        UrgentRequests requests = new UrgentRequests();
        assertTrue(requests.isEmpty());

        UrgentRequest normal1 = new UrgentRequest("FOOD", "Extra food from party", "Delhi", false, "2026-10-14", 101);
        UrgentRequest normal2 = new UrgentRequest("CLOTHES", "Winter jackets", "Pune", false, "2026-10-14", 102);
        UrgentRequest urgent1 = new UrgentRequest("BLOOD", "O-neg needed immediately", "Mumbai", true, "2026-10-14", 103);
        UrgentRequest urgent2 = new UrgentRequest("FOOD", "Emergency rations for flood area", "Assam", true, "2026-10-14", 104);

        // Enqueue normal first
        requests.enqueue(normal1);
        requests.enqueue(normal2);
        // Then enqueue urgent
        requests.enqueue(urgent1);
        requests.enqueue(urgent2);

        assertEquals(4, requests.size());

        // Dequeue should serve URGENT first!
        UrgentRequest served1 = requests.dequeue();
        assertTrue(served1.isUrgent());
        assertEquals("BLOOD", served1.getType());

        UrgentRequest served2 = requests.dequeue();
        assertTrue(served2.isUrgent());
        assertEquals("Assam", served2.getCity());

        // Now normal queue is served
        UrgentRequest served3 = requests.dequeue();
        assertFalse(served3.isUrgent());
        assertEquals("Delhi", served3.getCity());

        UrgentRequest served4 = requests.dequeue();
        assertFalse(served4.isUrgent());
        assertEquals("CLOTHES", served4.getType());

        assertTrue(requests.isEmpty());
    }

    // ================= 5. CategoryTree Tests =================
    @Test
    @DisplayName("CategoryTree: Hierarchical category tree operations")
    void testCategoryTree() {
        CategoryTree tree = new CategoryTree("NGO Categories");
        tree.addCategory("NGO Categories", "Elder Care");
        tree.addCategory("Elder Care", "Old Age Homes");
        tree.addCategory("Elder Care", "Dementia Care");

        assertNotNull(tree.find("Elder Care"));
        assertNotNull(tree.find("Old Age Homes"));
        assertNull(tree.find("Space Travel"));

        String treeDiagram = tree.printTree();
        assertTrue(treeDiagram.contains("Elder Care"));
        assertTrue(treeDiagram.contains("Old Age Homes"));
    }

    // ================= 6. ActivityArray Tests =================
    @Test
    @DisplayName("ActivityArray: Array storage and resizing")
    void testActivityArray() {
        ActivityArray arr = new ActivityArray(2);
        arr.add(new Activity(1, 101, "Act 1", "2026-10-20"));
        arr.add(new Activity(2, 102, "Act 2", "2026-10-21"));
        // triggers expansion
        arr.add(new Activity(3, 103, "Act 3", "2026-10-22"));

        assertEquals(3, arr.size());
        assertEquals("Act 2", arr.findById(2).getTitle());
        assertNull(arr.findById(99));
    }

    // ================= 7. PDF Certificate Generation =================
    @Test
    @DisplayName("PdfCertificateService: OpenPDF generates valid PDF bytes")
    void testPdfCertificateGeneration() {
        Volunteer volunteer = new Volunteer(12345L, "Sarah Connor", "sarah@resistance.org");
        Activity activity = new Activity(1, 101, "Companion Care", "2026-10-25");
        NGO ngo = new NGO(101, "Silver Haven", "Elder Care", "Mumbai", "admin@silverhaven.org");

        byte[] pdf = pdfService.generateCertificate(volunteer, activity, ngo);
        assertNotNull(pdf);
        assertTrue(pdf.length > 500, "PDF should contain valid byte content");

        // PDF file starts with magic bytes "%PDF-"
        String header = new String(pdf, 0, Math.min(pdf.length, 5));
        assertEquals("%PDF-", header);
    }

    // ================= 8. Telegram Bot Command Processing =================
    @Test
    @DisplayName("Telegram Bot: Handles all bot commands according to specifications")
    void testBotCommands() {
        long testChatId = 987654321L;

        // /start and HELP
        String helpReply = bot.processCommand(testChatId, "/start");
        assertTrue(helpReply.contains("GoodLink"));
        assertTrue(helpReply.contains("REGISTER"));

        // REGISTER <name> <email>
        String regReply = bot.processCommand(testChatId, "REGISTER Bruce Wayne bruce@wayne-enterprises.com");
        assertTrue(regReply.contains("Registration Successful"));
        assertNotNull(platformService.getVolunteer(testChatId));

        // NGOS
        String ngosReply = bot.processCommand(testChatId, "NGOS");
        assertTrue(ngosReply.contains("Registered NGOs"));
        assertTrue(ngosReply.contains("Silver Years"));

        // ACTIVITIES
        String actReply = bot.processCommand(testChatId, "ACTIVITIES");
        assertTrue(actReply.contains("Upcoming Volunteer Activities"));
        assertTrue(actReply.contains("Companion Care"));

        // JOIN <activityId>
        String joinReply = bot.processCommand(testChatId, "JOIN 1");
        assertTrue(joinReply.contains("Successfully Joined"));
        assertTrue(joinReply.contains("bruce@wayne-enterprises.com"));

        // Double JOIN should be recognized
        String duplicateJoin = bot.processCommand(testChatId, "JOIN 1");
        assertTrue(duplicateJoin.contains("already registered"));

        // DONATE <item> <quantity>
        String donateReply = bot.processCommand(testChatId, "DONATE Warm Blankets 25");
        assertTrue(donateReply.contains("Donation Queued"));
        assertTrue(donateReply.contains("Warm Blankets"));
        assertEquals(1, platformService.getDonations().size());

        // URGENT <BLOOD|FOOD> <details> <city>
        String urgentReply = bot.processCommand(testChatId, "URGENT BLOOD O-negative 2 units required Ruby Hall Clinic Pune");
        assertTrue(urgentReply.contains("Urgent Request Registered"));
        assertTrue(urgentReply.contains("HIGH (Urgent Queue)"));
        assertEquals(1, platformService.getUrgentRequests().size());

        // ACHIEVEMENTS
        String achReply = bot.processCommand(testChatId, "ACHIEVEMENTS");
        assertTrue(achReply.contains("GoodLink Wall of Achievements"));

        // Unknown text -> help menu
        String unknownReply = bot.processCommand(testChatId, "what is this app?");
        assertTrue(unknownReply.contains("Unknown Command"));
        assertTrue(unknownReply.contains("REGISTER <name> <email>"));
    }

    // ================= 9. Admin Completion & Certificate Workflow =================
    @Test
    @DisplayName("PlatformService: Complete participation marks complete, posts achievement & produces cert")
    void testCompleteParticipation() {
        long chatId = 11223344L;
        platformService.registerVolunteer(chatId, "Diana Prince", "diana@themyscira.gov");
        platformService.joinActivity(chatId, 1);

        int achievementsBefore = platformService.getAchievements().size();

        String result = platformService.completeParticipation(1, chatId);
        assertTrue(result.startsWith("SUCCESS"));
        assertTrue(result.contains("Diana Prince"));
        assertTrue(result.contains("Certificate PDF generated"));

        // Achievement should have been prepended to the wall
        assertEquals(achievementsBefore + 1, platformService.getAchievements().size());
        assertEquals("Service Completed: Weekend Companion Care at Silver Oak",
                platformService.getAchievements().get(0).getTitle());
    }

    // ================= 10. Seed Data Contact Email Integrity =================
    @Test
    @DisplayName("Seed Data: Each NGO retains its own real contact email in seed data")
    void testSeedNgoEmailsPreserved() {
        assertEquals("admin@silveryears.org", platformService.findNgoById(101).getEmail());
        assertEquals("contact@littlesparks.org", platformService.findNgoById(102).getEmail());
        assertEquals("relief@lifepulse.org", platformService.findNgoById(103).getEmail());
        assertEquals("help@annapurna.org", platformService.findNgoById(104).getEmail());
        assertEquals("care@pawstails.org", platformService.findNgoById(105).getEmail());
        String email106 = platformService.findNgoById(106).getEmail();
        System.out.println("DEBUG EMAIL: '" + email106 + "' len=" + email106.length());
        System.out.println("DEBUG ENDSWITH: " + email106.endsWith("@example.org"));
        assertEquals("contact@greenroots.example.org", email106);
        assertEquals("info@readrise.example.org", platformService.findNgoById(107).getEmail());
        assertEquals("help@mindfulyouth.example.org", platformService.findNgoById(108).getEmail());
    }

    // ================= 11. NGOREGISTER Bot Command Tests =================
    @Test
    @DisplayName("NGOREGISTER: Allows NGO admin to set NGO contact email at runtime")
    void testNgoRegisterCommand() {
        long adminChatId = 77889900L;

        // Valid update
        String reply = bot.processCommand(adminChatId, "NGOREGISTER 103 new.contact@lifepulse.org");
        assertTrue(reply.contains("NGO Contact Email Updated"));
        assertTrue(reply.contains("new.contact@lifepulse.org"));
        assertEquals("new.contact@lifepulse.org", platformService.findNgoById(103).getEmail());

        // Also test leading slash
        String replySlash = bot.processCommand(adminChatId, "/NGOREGISTER 104 dispatch@annapurna.org");
        assertTrue(replySlash.contains("NGO Contact Email Updated"));
        assertEquals("dispatch@annapurna.org", platformService.findNgoById(104).getEmail());

        // Non-existent NGO
        String notFound = bot.processCommand(adminChatId, "NGOREGISTER 999 unknown@ngo.org");
        assertTrue(notFound.contains("not found"));

        // Invalid format (missing email)
        String invalidFormat = bot.processCommand(adminChatId, "NGOREGISTER 103");
        assertTrue(invalidFormat.contains("Invalid Format"));

        // Invalid email address
        String invalidEmail = bot.processCommand(adminChatId, "NGOREGISTER 103 notanemail");
        assertTrue(invalidEmail.contains("Invalid Email Address"));

        // Invalid numeric ID
        String invalidId = bot.processCommand(adminChatId, "NGOREGISTER abc email@test.org");
        assertTrue(invalidId.contains("Invalid NGO ID"));
    }

    // ================= 12. NGO_EMAIL_OVERRIDE Behavior Tests =================
    @Test
    @DisplayName("NGO_EMAIL_OVERRIDE: When set, NGO emails redirect with [TEST - intended for <ngo email>]; volunteer/donor unaffected")
    void testNgoEmailOverrideWhenSet() {
        String testOverrideInbox = "testing-inbox@goodlink.ngo";
        emailService.setNgoEmailOverride(testOverrideInbox);
        assertTrue(emailService.isNgoEmailOverridden());

        String realNgoEmail = "relief@lifepulse.org";
        String originalSubject = "🚨 [CRITICAL ALERT] Urgent BLOOD Request in Bengaluru";

        // Recipient redirected to override address
        assertEquals(testOverrideInbox, emailService.resolveNgoRecipient(realNgoEmail));

        // Subject prefixed with [TEST - intended for <ngo email>]
        String resolvedSubject = emailService.resolveNgoSubject(realNgoEmail, originalSubject);
        assertTrue(resolvedSubject.startsWith("[TEST - intended for " + realNgoEmail + "]"));
        assertTrue(resolvedSubject.contains(originalSubject));

        // Volunteer and Donor emails must NOT be affected
        long volChatId = 99112233L;
        String volunteerEmail = "volunteer.jane@example.com";
        platformService.registerVolunteer(volChatId, "Jane Volunteer", volunteerEmail);

        // Volunteer joining activity
        String joinReply = platformService.joinActivity(volChatId, 2);
        assertTrue(joinReply.contains("Successfully Joined"));
        assertTrue(joinReply.contains(volunteerEmail));
        assertEquals(volunteerEmail, platformService.getVolunteer(volChatId).getEmail());

        // Donor pledging donation
        String donateReply = platformService.recordDonation(volChatId, "First Aid Kits", 15);
        assertTrue(donateReply.contains("Donation Queued"));
        assertTrue(donateReply.contains(volunteerEmail));

        // Direct volunteer email check
        assertTrue(emailService.sendVolunteerEmail(volunteerEmail, "Hello Volunteer", "Body"));
    }

    @Test
    @DisplayName("NGO_EMAIL_OVERRIDE: When not set, NGO emails go to real contact email without subject prefix")
    void testNgoEmailOverrideWhenNotSet() {
        emailService.setNgoEmailOverride(null);
        assertFalse(emailService.isNgoEmailOverridden());

        String realNgoEmail = "relief@lifepulse.org";
        String originalSubject = "🚨 [CRITICAL ALERT] Urgent BLOOD Request in Bengaluru";

        assertEquals(realNgoEmail, emailService.resolveNgoRecipient(realNgoEmail));
        assertEquals(originalSubject, emailService.resolveNgoSubject(realNgoEmail, originalSubject));
        assertFalse(emailService.resolveNgoSubject(realNgoEmail, originalSubject).startsWith("[TEST - intended for"));
    }

    @Test
    @DisplayName("NGOREGISTER combined with NGO_EMAIL_OVERRIDE: Uses updated contact email in subject prefix")
    void testNgoRegisterWithOverride() {
        String testOverrideInbox = "dev-team@goodlink.ngo";
        emailService.setNgoEmailOverride(testOverrideInbox);

        // Update NGO 105 contact email
        bot.processCommand(123456L, "NGOREGISTER 105 emergency@pawstails.org");
        assertEquals("emergency@pawstails.org", platformService.findNgoById(105).getEmail());

        // Resolve recipient and subject
        assertEquals(testOverrideInbox, emailService.resolveNgoRecipient("emergency@pawstails.org"));
        String subject = emailService.resolveNgoSubject("emergency@pawstails.org", "Dog Food Shortage");
        assertTrue(subject.startsWith("[TEST - intended for emergency@pawstails.org]"));
    }

    // ================= 13. Platform Status: 8 NGOs & 8 Activities =================
    @Test
    @DisplayName("Platform Status: Shows 8 NGOs and 8 Activities")
    void testPlatformStatusCounts() {
        assertEquals(8, platformService.getNgoBST().size());
        assertEquals(8, platformService.getActivities().size());

        AdminController adminController = new AdminController(platformService, bot);
        ResponseEntity<Map<String, Object>> statusResp = adminController.getPlatformStatus();
        assertEquals(HttpStatus.OK, statusResp.getStatusCode());
        assertNotNull(statusResp.getBody());
        assertEquals(8, statusResp.getBody().get("ngosInBST"));
        assertEquals(8, statusResp.getBody().get("activitiesInArray"));
    }

    // ================= 14. Commands & Certificates for IDs 6, 7, 8 =================
    @Test
    @DisplayName("Commands & Certificates: NGOS, ACTIVITIES, JOIN, CATEGORIES and Certificate work for IDs 6, 7, 8")
    void testNewNgosAndActivitiesCommandsAndCertificates() {
        long testVolChatId = 88776655L;
        bot.processCommand(testVolChatId, "REGISTER Bruce Banner banner@avengers.org");

        // 1. NGOS command lists the new NGOs (sorted by ID)
        String ngosReply = bot.processCommand(testVolChatId, "NGOS");
        assertTrue(ngosReply.contains("Green Roots Initiative"));
        assertTrue(ngosReply.contains("ReadRise Foundation"));
        assertTrue(ngosReply.contains("Mindful Youth Initiative"));
        assertTrue(ngosReply.contains("#106"));
        assertTrue(ngosReply.contains("#107"));
        assertTrue(ngosReply.contains("#108"));

        // 2. ACTIVITIES command lists the new activities
        String actReply = bot.processCommand(testVolChatId, "ACTIVITIES");
        assertTrue(actReply.contains("Green Neighbourhood Tree Plantation Drive"));
        assertTrue(actReply.contains("Community Library Setup & Book Donation"));
        assertTrue(actReply.contains("Youth Mental Wellness Awareness Campaign"));
        assertTrue(actReply.contains("Green Roots Initiative"));
        assertTrue(actReply.contains("ReadRise Foundation"));
        assertTrue(actReply.contains("Mindful Youth Initiative"));

        // 3. CATEGORIES command displays new categories
        String catReply = bot.processCommand(testVolChatId, "CATEGORIES");
        assertTrue(catReply.contains("Environment"));
        assertTrue(catReply.contains("Education"));
        assertTrue(catReply.contains("Mental Wellness"));

        // 4. JOIN commands for 6, 7, and 8
        String join6 = bot.processCommand(testVolChatId, "JOIN 6");
        assertTrue(join6.contains("Successfully Joined"));
        assertTrue(join6.contains("Green Neighbourhood Tree Plantation Drive"));
        assertTrue(join6.contains("Green Roots Initiative"));

        String join7 = bot.processCommand(testVolChatId, "JOIN 7");
        assertTrue(join7.contains("Successfully Joined"));
        assertTrue(join7.contains("Community Library Setup & Book Donation"));
        assertTrue(join7.contains("ReadRise Foundation"));

        String join8 = bot.processCommand(testVolChatId, "JOIN 8");
        assertTrue(join8.contains("Successfully Joined"));
        assertTrue(join8.contains("Youth Mental Wellness Awareness Campaign"));
        assertTrue(join8.contains("Mindful Youth Initiative"));

        // 5. Certificate generation and completion endpoint for 6, 7, and 8
        AdminController adminController = new AdminController(platformService, bot);
        for (int actId = 6; actId <= 8; actId++) {
            // Complete participation
            ResponseEntity<Map<String, Object>> completeResp = adminController.markComplete(actId, testVolChatId);
            assertEquals(HttpStatus.OK, completeResp.getStatusCode());
            assertNotNull(completeResp.getBody());
            assertEquals("SUCCESS", completeResp.getBody().get("status"));

            // Download certificate
            ResponseEntity<byte[]> certResp = adminController.downloadCertificate(actId, testVolChatId);
            assertEquals(HttpStatus.OK, certResp.getStatusCode());
            assertNotNull(certResp.getBody());
            assertTrue(certResp.getBody().length > 500);
            String pdfHeader = new String(certResp.getBody(), 0, Math.min(certResp.getBody().length, 5));
            assertEquals("%PDF-", pdfHeader);
        }
    }
}
