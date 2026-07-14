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
            "Analyst Department: 디지털포렌식팀",
            "Reviewer Name: Reviewer Lee",
            "Reviewer Department: 검토팀",
            "Review Status: REPORT_APPROVED",
            "Review Approved At: 2026.07.14 10:30",
            "Evidence ID: 101",
            "File Name: sample.mp4",
            "SHA-256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "Risk Level: HIGH",
            "Risk Score: 72",
            "Confidence: 91",
            "Analyzed At: 2026.07.14 10:00",
            "--- Module: xception ---",
            "Detected: true",
            "Score: 72",
            "Confidence: 91",
            "=== Evidence Items ===",
            "Total Count: 1",
            "Evidence Item: 얼굴 경계 불일치 신호가 저장되었습니다.",
            "=== Module Timeline Summaries ===",
            "Total Count: 1",
            "Module Timeline: module=temporal | model=TimeSFormer v1.2 | videoScore=0.72 | threshold=0.6 | detected=true | points=2 | segments=1",
            "=== Timeline Points ===",
            "Total Count: 2",
            "Timeline Point: source=temporal | kind=CLIP | start=12.0 | end=15.0 | score=0.82 | reference=클립 3 / 프레임 288-360",
            "Timeline Point: source=optical | kind=PAIR | start=12.2 | end=12.2 | score=0.74 | reference=프레임쌍 15 / 프레임 300-301 / 움직임 1.1",
            "=== Suspicious Segments ===",
            "Total Count: 1",
            "Suspicious Segment: source=temporal | start=12.0 | end=15.0 | score=0.82 | reason=시간적 불일치",
            "=== Representative Frames ===",
            "Total Count: 1",
            "Representative Frame: timeSec=12.4 | timestamp=00:12 | frameNumber=310 | score=0.82 | imageRegistered=true"
    );

    @Test
    void generatedReportContainsAutomaticPublicVerificationUrlWithoutManualCode() throws Exception {
        String verifyUrl = "https://verify.example.test/verify?token=vrf_test_token";
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
            assertThat(reader.getNumberOfPages()).isEqualTo(4);
            String integrityPageText = new PdfTextExtractor(reader).getTextFromPage(4);
            String overviewText = new PdfTextExtractor(reader).getTextFromPage(1);
            String detailPageText = new PdfTextExtractor(reader).getTextFromPage(2);
            String evidencePageText = new PdfTextExtractor(reader).getTextFromPage(3);
            assertThat(overviewText)
                    .contains("서울-디지털증거-2026")
                    .contains("Analyst Kim")
                    .contains("조작 가능성 관련 신호 높음")
                    .contains("얼굴 경계 불일치 신호가 저장되었습니다.");
            assertThat(detailPageText)
                    .contains("탐지 신호 있음")
                    .doesNotContain("전체 구간")
                    .doesNotContain("기준 미만");
            assertThat(evidencePageText)
                    .contains("AI 상세 근거")
                    .contains("TimeSFormer")
                    .contains("00:12.00 - 00:15.00")
                    .contains("시간적 불일치")
                    .contains("프레임쌍 15")
                    .contains("등록됨");
            assertThat(integrityPageText)
                    .contains("Analyst Kim / 디지털포렌식팀")
                    .contains("Reviewer Lee / 검토팀")
                    .contains("기관 내부 절차상 최종 승인")
                    .contains("시스템 승인 · 2026.07.14 10:30")
                    .contains(verifyUrl)
                    .contains("발행 등록정보 조회")
                    .contains("PDF 파일 자체는 미검사")
                    .contains("등록된 SHA-256 해시값과 대조")
                    .contains("확률에 기반한 참고 자료")
                    .contains("PDF 전자서명")
                    .contains("미적용")
                    .doesNotContain(verificationCode)
                    .doesNotContain("(서명)")
                    .doesNotContain("출력본 확인 서명");
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
            String integrityPageText = new PdfTextExtractor(reader).getTextFromPage(4);
            assertThat(overviewText)
                    .contains("검토 승인 대기")
                    .contains("미발행 · 미리보기");
            assertThat(integrityPageText)
                    .contains("검증 QR과 URL은 발행 등록 후 생성됩니다.")
                    .contains("미발행 · 미리보기")
                    .contains("승인 전")
                    .doesNotContain("출력본 확인 서명")
                    .doesNotContain("vrf_")
                    .doesNotContain("VF-TEST");
        } finally {
            reader.close();
        }
    }

    @Test
    void previewWatermarkClearlyStatesThatVerificationIsUnavailable() throws Exception {
        byte[] pdf = PdfDocumentWriter.addPreviewWatermark(
                PdfDocumentWriter.writeDraftReport("ForenShield Analysis Report", REPORT_LINES)
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1))
                    .contains("미리보기 - 검증 불가");
        } finally {
            reader.close();
        }
    }

    @Test
    void analysisReportWithoutVisualizationShowsNoFabricatedDetail() throws Exception {
        byte[] pdf = PdfDocumentWriter.writeDraftReport(
                "ForenShield Analysis Report",
                List.of("Risk Level: LOW", "Risk Score: 10")
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(4);
            String evidencePageText = new PdfTextExtractor(reader).getTextFromPage(3);
            assertThat(evidencePageText)
                    .contains("저장된 실제 모듈 타임라인, 의심 구간 또는 대표 프레임 데이터가 없습니다.")
                    .contains("임의 데이터를 생성하지 않습니다.")
                    .doesNotContain("전체 구간");
        } finally {
            reader.close();
        }
    }

    @Test
    void compareReportRendersActualSerializedComparisonRows() throws Exception {
        List<String> lines = List.of(
                "Compare ID: 42",
                "Verdict: TAMPERED",
                "Match Count: 0",
                "Mismatch Count: 1",
                "Skipped Count: 0",
                "Review Status: REPORT_APPROVED",
                "=== Comparison Results ===",
                "SHA-256 | original=aaaaaaaa | candidate=bbbbbbbb | result=MISMATCH",
                "Duration | original=null | candidate=null | result=SKIPPED"
        );

        byte[] pdf = PdfDocumentWriter.writeReport(
                "ForenShield Compare Verification Report",
                lines,
                "https://verify.example.test/verify?token=vrf_compare",
                "VF-COMP-1234",
                "RPT-20260714-COMPARE"
        );

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(2);
            String resultPageText = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(resultPageText)
                    .contains("SHA-256")
                    .contains("aaaaaaaa")
                    .contains("bbbbbbbb")
                    .contains("불일치")
                    .doesNotContain("null");
        } finally {
            reader.close();
        }
    }
}
