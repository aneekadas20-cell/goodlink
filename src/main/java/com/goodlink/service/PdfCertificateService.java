package com.goodlink.service;

import com.goodlink.model.Activity;
import com.goodlink.model.NGO;
import com.goodlink.model.Volunteer;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;

/**
 * Generates OpenPDF certificates for volunteers upon completion of an activity.
 * Certificates are sent ONLY after completion via admin endpoint.
 */
@Service
public class PdfCertificateService {

    private static final Logger log = LoggerFactory.getLogger(PdfCertificateService.class);

    public byte[] generateCertificate(Volunteer volunteer, Activity activity, NGO ngo) {
        Document document = new Document(PageSize.A4.rotate(), 50, 50, 50, 50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            // Draw decorative border
            PdfContentByte cb = writer.getDirectContent();
            cb.setColorStroke(new Color(41, 128, 185)); // Deep ocean blue
            cb.setLineWidth(4f);
            cb.rectangle(30, 30, PageSize.A4.rotate().getWidth() - 60, PageSize.A4.rotate().getHeight() - 60);
            cb.stroke();

            cb.setColorStroke(new Color(243, 156, 18)); // Gold accent inner border
            cb.setLineWidth(1.5f);
            cb.rectangle(36, 36, PageSize.A4.rotate().getWidth() - 72, PageSize.A4.rotate().getHeight() - 72);
            cb.stroke();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, new Color(44, 62, 80));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(41, 128, 185));
            Font certForFont = FontFactory.getFont(FontFactory.HELVETICA, 13, new Color(127, 140, 141));
            Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, new Color(192, 57, 43));
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 13, new Color(52, 73, 94));
            Font highlightFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(44, 62, 80));
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, new Color(149, 165, 166));

            // Content paragraphs
            Paragraph emptySpace = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 10));

            Paragraph header = new Paragraph("GOODLINK VOLUNTEER PLATFORM", subtitleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            document.add(header);

            Paragraph title = new Paragraph("CERTIFICATE OF APPRECIATION", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(10);
            title.setSpacingAfter(15);
            document.add(title);

            Paragraph presentedTo = new Paragraph("THIS IS PROUDLY PRESENTED TO", certForFont);
            presentedTo.setAlignment(Element.ALIGN_CENTER);
            document.add(presentedTo);

            Paragraph volunteerNamePara = new Paragraph(volunteer.getName().toUpperCase(), nameFont);
            volunteerNamePara.setAlignment(Element.ALIGN_CENTER);
            volunteerNamePara.setSpacingBefore(8);
            volunteerNamePara.setSpacingAfter(15);
            document.add(volunteerNamePara);

            String ngoName = (ngo != null) ? ngo.getName() : "Partner NGO";
            Paragraph text1 = new Paragraph();
            text1.setAlignment(Element.ALIGN_CENTER);
            text1.add(new Paragraph("for successfully participating and completing community service in", bodyFont));
            document.add(text1);

            Paragraph activityPara = new Paragraph("\"" + activity.getTitle() + "\"", highlightFont);
            activityPara.setAlignment(Element.ALIGN_CENTER);
            activityPara.setSpacingBefore(6);
            activityPara.setSpacingAfter(6);
            document.add(activityPara);

            Paragraph text2 = new Paragraph();
            text2.setAlignment(Element.ALIGN_CENTER);
            text2.add(new Paragraph("organized by ", bodyFont));
            text2.add(new Paragraph(ngoName, highlightFont));
            text2.add(new Paragraph(" on " + activity.getDate() + ".", bodyFont));
            document.add(text2);

            Paragraph divider = new Paragraph("________________________________________", footerFont);
            divider.setAlignment(Element.ALIGN_CENTER);
            divider.setSpacingBefore(20);
            document.add(divider);

            Paragraph signOff = new Paragraph(
                    "Issued on: " + LocalDate.now() + "  |  Authorized by GoodLink Platform  |  Certificate ID: GL-" + activity.getId() + "-" + volunteer.getChatId(),
                    footerFont
            );
            signOff.setAlignment(Element.ALIGN_CENTER);
            signOff.setSpacingBefore(8);
            document.add(signOff);

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            log.error("Error generating PDF certificate for volunteer {}", volunteer.getName(), e);
            throw new RuntimeException("Failed to generate PDF certificate", e);
        }
    }
}
