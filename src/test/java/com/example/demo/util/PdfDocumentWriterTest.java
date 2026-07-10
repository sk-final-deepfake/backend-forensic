package com.example.demo.util;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentWriterTest {

    private static final List<String> REPORT_LINES = List.of(
            "Case Name: 서울-디지털증거-2026",
            "Case Number: CASE-2026-0710",
            "Analyst Name: Analyst Kim",
            "Reviewer Name: Reviewer Lee",
            "Evidence ID: 101",
            "File Name: sample.mp4",
            "SHA-256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "Risk Score: 72",
            "Confidence: 91"
    );

    @Test
    void generatedReportContainsPublicVerificationUrlAndCode() throws Exception {
        String verifyUrl = "https://forensheildjangdochi.com/verify?token=vrf_test_token";
        String verificationCode = "VF-TEST-1234";

        byte[] pdf = PdfDocumentWriter.writeReport(
                "ForenShield Analysis Report",
                REPORT_LINES,
                verifyUrl,
                verificationCode,
                "RPT-20260710-TEST"
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(3);
            String integrityPageText = new PdfTextExtractor(reader).getTextFromPage(3);
            String overviewText = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(overviewText)
                    .contains("서울-디지털증거-2026")
                    .contains("Analyst Kim");
            assertThat(integrityPageText).contains("Reviewer Lee");
            assertThat(integrityPageText).contains(verifyUrl);
            assertThat(integrityPageText).contains(verificationCode);
        } finally {
            reader.close();
        }
    }

    @Test
    void draftReportContainsNoPublicVerificationCredential() throws Exception {
        byte[] pdf = PdfDocumentWriter.writeDraftReport("ForenShield Analysis Report", REPORT_LINES);

        PdfReader reader = new PdfReader(pdf);
        try {
            String overviewText = new PdfTextExtractor(reader).getTextFromPage(1);
            String integrityPageText = new PdfTextExtractor(reader).getTextFromPage(3);
            assertThat(overviewText).contains("검토 승인 대기");
            assertThat(integrityPageText)
                    .contains("검토 승인 후 QR과 공개 검증코드가 발급됩니다.")
                    .doesNotContain("vrf_")
                    .doesNotContain("VF-TEST");
        } finally {
            reader.close();
        }
    }
}
