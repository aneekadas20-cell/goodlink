package com.goodlink.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles sending emails via Gmail SMTP through Spring Mail.
 * Reads MAIL_USER and MAIL_PASS from environment variables.
 * Gracefully handles missing credentials or connection errors so the application remains robust.
 * Supports optional NGO_EMAIL_OVERRIDE for testing/redirection of NGO-bound emails.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUser;

    @Value("${spring.mail.password:}")
    private String mailPass;

    @Value("${goodlink.admin.email:admin@goodlink.ngo}")
    private String defaultAdminEmail;

    @Value("${ngo.email.override:${NGO_EMAIL_OVERRIDE:}}")
    private String ngoEmailOverride;

    private volatile String lastErrorMessage;

    private final Set<String> knownNgoEmails = ConcurrentHashMap.newKeySet();

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        initDefaultNgoEmails();
    }

    public EmailService(JavaMailSender mailSender, String ngoEmailOverride) {
        this.mailSender = mailSender;
        this.ngoEmailOverride = ngoEmailOverride;
        initDefaultNgoEmails();
    }

    private void initDefaultNgoEmails() {
        knownNgoEmails.add("admin@silveryears.org");
        knownNgoEmails.add("contact@littlesparks.org");
        knownNgoEmails.add("relief@lifepulse.org");
        knownNgoEmails.add("help@annapurna.org");
        knownNgoEmails.add("care@pawstails.org");
        knownNgoEmails.add("contact@greenroots.example.org");
        knownNgoEmails.add("info@readrise.example.org");
        knownNgoEmails.add("help@mindfulyouth.example.org");
        knownNgoEmails.add("admin@goodlink.ngo");
    }

    public void registerNgoEmail(String email) {
        if (email != null && !email.isBlank()) {
            knownNgoEmails.add(email.trim().toLowerCase());
        }
    }

    public void unregisterNgoEmail(String email) {
        if (email != null && !email.isBlank()) {
            knownNgoEmails.remove(email.trim().toLowerCase());
        }
    }

    public boolean isKnownNgoEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return knownNgoEmails.contains(email.trim().toLowerCase());
    }

    public String getNgoEmailOverride() {
        if (ngoEmailOverride != null && !ngoEmailOverride.isBlank()) {
            return ngoEmailOverride.trim();
        }
        String env = System.getenv("NGO_EMAIL_OVERRIDE");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return null;
    }

    public void setNgoEmailOverride(String ngoEmailOverride) {
        this.ngoEmailOverride = ngoEmailOverride;
    }

    public boolean isNgoEmailOverridden() {
        String override = getNgoEmailOverride();
        return override != null && !override.isBlank();
    }

    public String resolveNgoRecipient(String ngoEmail) {
        if (isNgoEmailOverridden()) {
            return getNgoEmailOverride();
        }
        return ngoEmail;
    }

    public String resolveNgoSubject(String ngoEmail, String subject) {
        if (isNgoEmailOverridden()) {
            String prefix = "[TEST - intended for " + ngoEmail + "]";
            if (subject == null || subject.isBlank()) {
                return prefix;
            }
            if (subject.startsWith(prefix)) {
                return subject;
            }
            return prefix + " " + subject;
        }
        return subject;
    }

    /**
     * Checks if email credentials are configured.
     */
    public boolean isConfigured() {
        return mailUser != null && !mailUser.isBlank() && mailPass != null && !mailPass.isBlank();
    }

    /**
     * Sends an email specifically addressed to an NGO.
     * If NGO_EMAIL_OVERRIDE is set, redirects to that address and prefixes the subject
     * with "[TEST - intended for <ngo email>]".
     */
    public boolean sendNgoEmail(String ngoEmail, String subject, String text) {
        String recipient = resolveNgoRecipient(ngoEmail);
        String finalSubject = resolveNgoSubject(ngoEmail, subject);
        if (isNgoEmailOverridden()) {
            log.info("📧 Redirecting NGO email intended for {} to override address: {}", ngoEmail, recipient);
        }
        return sendDirectEmail(recipient, finalSubject, text);
    }

    /**
     * Sends an email specifically addressed to an NGO, with a Reply-To header set to
     * the requester's email so the NGO can hit "Reply" and reach the requester directly,
     * instead of replying back to the system. If replyToEmail is null/blank, behaves
     * exactly like sendNgoEmail. Respects NGO_EMAIL_OVERRIDE the same way.
     */
    public boolean sendNgoEmailWithReplyTo(String ngoEmail, String subject, String text, String replyToEmail) {
        String recipient = resolveNgoRecipient(ngoEmail);
        String finalSubject = resolveNgoSubject(ngoEmail, subject);
        if (isNgoEmailOverridden()) {
            log.info("📧 Redirecting NGO email (reply-to {}) intended for {} to override address: {}",
                    replyToEmail, ngoEmail, recipient);
        }
        return sendDirectEmailWithReplyTo(recipient, finalSubject, text, replyToEmail);
    }

    /**
     * Sends an email with an attachment specifically addressed to an NGO.
     * If NGO_EMAIL_OVERRIDE is set, redirects to that address and prefixes the subject
     * with "[TEST - intended for <ngo email>]".
     */
    public boolean sendNgoEmailWithAttachment(String ngoEmail, String subject, String text,
                                            String attachmentFilename, byte[] attachmentData, String mimeType) {
        String recipient = resolveNgoRecipient(ngoEmail);
        String finalSubject = resolveNgoSubject(ngoEmail, subject);
        if (isNgoEmailOverridden()) {
            log.info("📧 Redirecting NGO email with attachment intended for {} to override address: {}", ngoEmail, recipient);
        }
        return sendDirectEmailWithAttachment(recipient, finalSubject, text, attachmentFilename, attachmentData, mimeType);
    }

    /**
     * Sends an email to a volunteer or donor. Never affected by NGO_EMAIL_OVERRIDE.
     */
    public boolean sendVolunteerEmail(String volunteerEmail, String subject, String text) {
        return sendDirectEmail(volunteerEmail, subject, text);
    }

    /**
     * Sends an email with attachment to a volunteer or donor. Never affected by NGO_EMAIL_OVERRIDE.
     */
    public boolean sendVolunteerEmailWithAttachment(String volunteerEmail, String subject, String text,
                                                  String attachmentFilename, byte[] attachmentData, String mimeType) {
        return sendDirectEmailWithAttachment(volunteerEmail, subject, text, attachmentFilename, attachmentData, mimeType);
    }

    /**
     * General email sender. If recipient is a known NGO and NGO_EMAIL_OVERRIDE is active,
     * redirects to the override address. Otherwise sends directly to the recipient.
     */
    public boolean sendEmail(String to, String subject, String text) {
        if (isKnownNgoEmail(to)) {
            return sendNgoEmail(to, subject, text);
        }
        return sendDirectEmail(to, subject, text);
    }

    /**
     * General email sender with attachment. If recipient is a known NGO and NGO_EMAIL_OVERRIDE is active,
     * redirects to the override address. Otherwise sends directly to the recipient.
     */
    public boolean sendEmailWithAttachment(String to, String subject, String text,
                                          String attachmentFilename, byte[] attachmentData, String mimeType) {
        if (isKnownNgoEmail(to)) {
            return sendNgoEmailWithAttachment(to, subject, text, attachmentFilename, attachmentData, mimeType);
        }
        return sendDirectEmailWithAttachment(to, subject, text, attachmentFilename, attachmentData, mimeType);
    }

    /**
     * Sends a plain text or HTML email directly to the given recipient.
     */
    public boolean sendDirectEmail(String to, String subject, String text) {
        return sendDirectEmailWithReplyTo(to, subject, text, null);
    }

    /**
     * Sends a plain text email directly to the given recipient, optionally setting a
     * Reply-To header so replies go to a different address (e.g. the original requester)
     * instead of the system's sending account. If replyTo is null/blank, no Reply-To is set.
     */
    public boolean sendDirectEmailWithReplyTo(String to, String subject, String text, String replyTo) {
        if (to == null || to.isBlank()) {
            lastErrorMessage = "Recipient address is empty";
            log.warn("Cannot send email: recipient address is empty.");
            return false;
        }

        if (!isConfigured()) {
            lastErrorMessage = null;
            log.info("📧 [SIMULATED EMAIL - MAIL_USER/MAIL_PASS not set in ENV]\nTo: {}\nReply-To: {}\nSubject: {}\nBody:\n{}\n-------------------------",
                    to, replyTo, subject, text);
            return true;
        }

        try {
            lastErrorMessage = null;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailUser, "GoodLink Volunteer Platform");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }

            mailSender.send(message);
            log.info("Email successfully sent to: {}", to);
            return true;
        } catch (Exception e) {
            lastErrorMessage = e.getMessage();
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    /**
     * Sends an email with a file attachment directly to the given recipient.
     */
    public boolean sendDirectEmailWithAttachment(String to, String subject, String text,
                                                String attachmentFilename, byte[] attachmentData, String mimeType) {
        if (to == null || to.isBlank()) {
            lastErrorMessage = "Recipient address is empty";
            log.warn("Cannot send email with attachment: recipient address is empty.");
            return false;
        }

        if (!isConfigured()) {
            lastErrorMessage = null;
            log.info("📧 [SIMULATED EMAIL WITH ATTACHMENT - MAIL_USER/MAIL_PASS not set in ENV]\nTo: {}\nSubject: {}\nAttachment: {} ({} bytes)\nBody:\n{}\n-------------------------",
                    to, subject, attachmentFilename, (attachmentData != null ? attachmentData.length : 0), text);
            return true;
        }

        try {
            lastErrorMessage = null;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailUser, "GoodLink Volunteer Platform");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);

            if (attachmentData != null && attachmentFilename != null) {
                ByteArrayResource resource = new ByteArrayResource(attachmentData);
                helper.addAttachment(attachmentFilename, resource, mimeType);
            }

            mailSender.send(message);
            log.info("Email with attachment '{}' successfully sent to: {}", attachmentFilename, to);
            return true;
        } catch (Exception e) {
            lastErrorMessage = e.getMessage();
            log.error("Failed to send email with attachment to {}: {}", to, e.getMessage());
            return false;
        }
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public String getDefaultAdminEmail() {
        return defaultAdminEmail;
    }
}