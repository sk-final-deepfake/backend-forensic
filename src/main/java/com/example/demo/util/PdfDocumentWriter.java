package com.example.demo.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

import java.io.ByteArrayOutputStream;
import java.util.List;

public final class PdfDocumentWriter {

    private PdfDocumentWriter() {
    }

    public static byte[] writeReport(String title, List<String> lines) {
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

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", ex);
        }
    }
}
