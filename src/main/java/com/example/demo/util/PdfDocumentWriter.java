package com.example.demo.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class PdfDocumentWriter {

    private PdfDocumentWriter() {
    }

    public static byte[] writeReport(String title, List<String> lines) {
        return writeReport(title, lines, null);
    }

    public static byte[] writeReport(String title, List<String> lines, String qrContent) {
        return writeReport(title, lines, qrContent, null);
    }

    public static byte[] writeReport(String title, List<String> lines, String qrContent, String verificationCode) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            Document document = new Document();
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            document.add(new Paragraph(title, titleFont));
            document.add(new Paragraph(" "));
            for (String line : lines) {
                document.add(new Paragraph(line == null ? "" : line, bodyFont));
            }

            if (qrContent != null && !qrContent.isBlank()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Report Authenticity Verification:", bodyFont));
                document.add(new Paragraph("Mobile: scan the QR code. PC: open the verification URL and enter the verification code.", bodyFont));
                Image qrImage = QrCodeImageWriter.createPdfImage(qrContent, 120);
                qrImage.setAlignment(Image.ALIGN_LEFT);
                document.add(qrImage);
                document.add(new Paragraph("Verification URL: " + qrContent, bodyFont));
                if (verificationCode != null && !verificationCode.isBlank()) {
                    document.add(new Paragraph("Verification Code: " + verificationCode, bodyFont));
                }
            }

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", ex);
        }
    }
}
