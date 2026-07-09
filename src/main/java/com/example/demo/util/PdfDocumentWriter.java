package com.example.demo.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class PdfDocumentWriter {

    private static final int THRESHOLD = 60;
    private static final Color INK = new Color(15, 23, 42);
    private static final Color SLATE = new Color(71, 85, 105);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LIGHT = new Color(148, 163, 184);
    private static final Color BORDER = new Color(100, 116, 139);
    private static final Color LIGHT_BORDER = new Color(203, 213, 225);
    private static final Color SURFACE = new Color(241, 245, 249);
    private static final Color TEAL = new Color(0, 137, 123);
    private static final Color DANGER = new Color(185, 28, 28);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy. MM. dd.");
    private static final BaseFont DOCUMENT_BASE_FONT = loadDocumentBaseFont();

    private PdfDocumentWriter() {
    }

    public static byte[] writeReport(String title, List<String> lines) {
        return writeReport(title, lines, null);
    }

    public static byte[] writeReport(String title, List<String> lines, String qrContent) {
        return writeReport(title, lines, qrContent, null);
    }

    public static byte[] writeReport(String title, List<String> lines, String qrContent, String verificationCode) {
        return writeReport(title, lines, qrContent, verificationCode, null);
    }

    public static byte[] writeReport(
            String title,
            List<String> lines,
            String qrContent,
            String verificationCode,
            String reportNo
    ) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ReportData data = ReportData.parse(lines);
            boolean compareReport = isCompareReport(title, data);
            int totalPages = compareReport ? 2 : 3;

            Document document = new Document(PageSize.A4, 54, 54, 46, 56);
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            writer.setPageEvent(new ReportFooter(totalPages));
            document.open();

            if (compareReport) {
                addCompareReportPage(document, data, reportNo);
                document.newPage();
                addIntegrityPage(document, "비교검증 보고서", data, reportNo, qrContent, verificationCode, true);
            } else {
                addAnalysisOverviewPage(document, data, reportNo);
                document.newPage();
                addAnalysisDetailPage(document, data, reportNo);
                document.newPage();
                addIntegrityPage(document, "전체 종합 보고서", data, reportNo, qrContent, verificationCode, false);
            }

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", ex);
        }
    }

    private static void addAnalysisOverviewPage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "전체 종합 보고서", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "문서 개요");
        addInfoGrid(document, List.of(
                row("보고서 유형", "딥페이크 분석 종합 보고서"),
                row("검토 상태", "검토 승인 완료"),
                row("보안 등급", "내부망 전용"),
                row("생성자", "분석관"),
                row("생성일", LocalDateTime.now().format(DATE_TIME_FORMAT)),
                row("검증 방식", "QR · 검증 URL · 전자서명")
        ));

        addSectionTitle(document, 2, "사건 및 증거 요약");
        addInfoGrid(document, List.of(
                row("사건명", data.value("Case Name").orElse("사건명 미지정")),
                row("사건 번호", data.value("Case Number").orElse("-")),
                row("대상 증거", prefixedEvidenceId(data.value("Evidence ID").orElse("-"))),
                row("파일명", data.value("File Name").orElse("-")),
                row("등록 일시", data.value("Uploaded At").orElse("-")),
                row("파일 유형", data.value("File Type").orElse("VIDEO")),
                row("분석 일시", data.value("Analyzed At").orElse("-")),
                row("원본 해시", shorten(data.value("SHA-256").orElse("-"), 18, 12))
        ));

        addSectionTitle(document, 3, "최종 분석 판정");
        addVerdictBox(document, riskLabel(data), data.value("Summary").orElse("-"));
        addInfoGrid(document, List.of(
                row("위험 점수", scoreText(data.value("Risk Score").orElse("-")) + " / 100 (판정 기준 60)"),
                row("분석 신뢰도", scoreText(data.value("Confidence").orElse("-")) + "%"),
                row("위험 등급", data.value("Risk Level").orElse("-")),
                row("분석 상태", data.value("Analysis Status").orElse("-"))
        ));

        addSectionTitle(document, 4, "핵심 근거 요약");
        addReasonRows(document, data);
    }

    private static void addAnalysisDetailPage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "AI 분석 상세", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "모델별 추론 결과");
        addModelScoreBars(document, data);
        addFootnote(document, "※ 점선은 판정 기준(60점)을 나타냅니다.");

        addSectionTitle(document, 2, "모듈별 측정 항목");
        PdfPTable table = reportTable(new float[]{1.2f, 2.2f, 0.7f, 0.9f, 1f});
        addTableHeader(table, "모듈", "측정 항목", "점수", "판정", "비고");
        List<ModuleBlock> modules = moduleRows(data);
        for (ModuleBlock module : modules) {
            int score = percent(module.value("Score").orElse("-"));
            addTableRow(table,
                    moduleLabel(module.name),
                    moduleMetric(module.name),
                    String.valueOf(score),
                    score >= THRESHOLD ? "기준 초과" : "기준 미만",
                    moduleNote(module.name));
        }
        document.add(table);

        addSectionTitle(document, 3, "의심 구간 및 타임라인");
        PdfPTable timeline = reportTable(new float[]{1f, 1.1f, 0.8f, 0.9f, 2f});
        addTableHeader(timeline, "구간", "모듈", "최대 점수", "상태", "설명");
        int index = 0;
        for (ModuleBlock module : modules) {
            int score = percent(module.value("Score").orElse("-"));
            addTableRow(timeline,
                    index == 0 ? "전체 구간" : "모듈 구간",
                    moduleLabel(module.name),
                    String.valueOf(score),
                    score >= THRESHOLD ? "기준 초과" : "기준 미만",
                    module.value("Detected").orElse("-").equalsIgnoreCase("true") ? "추가 검토 필요" : "위험 신호 낮음");
            index++;
        }
        document.add(timeline);
    }

    private static void addCompareReportPage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "비교검증 보고서", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "검증 개요");
        addInfoGrid(document, List.of(
                row("보고서 유형", "비교검증 보고서"),
                row("검토 상태", "검토 승인 완료"),
                row("비교검증 ID", data.value("Compare ID").orElse("-")),
                row("생성일", data.value("Created At").orElse(LocalDateTime.now().format(DATE_TIME_FORMAT))),
                row("판정", compareVerdict(data.value("Verdict").orElse("-"))),
                row("검증 방식", "해시 · 메타데이터 · 전자서명")
        ));

        addSectionTitle(document, 2, "원본 및 비교 대상");
        Section original = data.section("Original File Information");
        Section candidate = data.section("Candidate File Information");
        PdfPTable files = reportTable(new float[]{1f, 1f});
        addFileInfoCell(files, "기준 증거", original);
        addFileInfoCell(files, "비교 대상", candidate);
        document.add(files);

        addSectionTitle(document, 3, "비교 결과 요약");
        addInfoGrid(document, List.of(
                row("일치", data.value("Match Count").orElse("0") + "건"),
                row("불일치", data.value("Mismatch Count").orElse("0") + "건"),
                row("제외", data.value("Skipped Count").orElse("0") + "건"),
                row("최종 판정", compareVerdict(data.value("Verdict").orElse("-")))
        ));

        addSectionTitle(document, 4, "주요 불일치 항목");
        Section comparison = data.section("Comparison Results");
        PdfPTable result = reportTable(new float[]{1.25f, 1.9f, 1.9f, 0.85f});
        addTableHeader(result, "항목", "기준", "대상", "결과");
        if (comparison == null || comparison.rows.isEmpty()) {
            addTableRow(result, "비교 결과", "-", "-", "확인 필요");
        } else {
            for (Row row : comparison.rows) {
                CompareRow item = CompareRow.parse(row.value);
                addTableRow(result, item.label, item.original, item.candidate, compareResult(item.result));
            }
        }
        document.add(result);
    }

    private static void addIntegrityPage(
            Document document,
            String reportTitle,
            ReportData data,
            String reportNo,
            String qrContent,
            String verificationCode,
            boolean compareReport
    ) throws DocumentException {
        addPageHeader(document, "무결성 및 감사 이력", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "무결성 검증 결과");
        addInfoGrid(document, List.of(
                row("원본 해시", shorten(data.value("SHA-256").orElse(rowValue(data.section("Original File Information"), "SHA-256")), 18, 12)),
                row("PDF 해시", "검증 페이지에서 확인"),
                row("해시 알고리즘", "SHA-256"),
                row("검증 결과", "저장된 해시 기준 검증"),
                row("전자서명", "유효성 확인 가능"),
                row("서명 기관", "ForenShield Evidence Authority")
        ));

        addSectionTitle(document, 2, "블록체인 기록");
        addInfoGrid(document, List.of(
                row("기록 상태", "검증 페이지에서 확인"),
                row("네트워크", "private forensic ledger"),
                row("트랜잭션", "검증 페이지에서 확인"),
                row("앵커 시각", "-"),
                row("블록 번호", "-"),
                row("상태", "조회 필요")
        ));

        addSectionTitle(document, 3, "CoC 처리 이력");
        PdfPTable coc = reportTable(new float[]{1.15f, 1.4f, 1f, 0.8f, 1.6f});
        addTableHeader(coc, "시각", "이벤트", "담당", "상태", "해시");
        addTableRow(coc, LocalDateTime.now().format(DATE_TIME_FORMAT), "보고서 생성", compareReport ? "분석관" : "분석관", "완료", "검증 페이지에서 확인");
        document.add(coc);

        addSectionTitle(document, 4, "검증 방법 및 유의사항");
        addVerificationBlock(document, qrContent, verificationCode);
        Paragraph caution = new Paragraph(
                "본 보고서의 AI 분석 결과는 조작 여부를 확정하지 않는 참고 소견이며, 최종 판단은 원본 자료, 사건 맥락, 전문가 검토와 함께 이루어져야 합니다.",
                font(11, Font.NORMAL, INK)
        );
        caution.setLeading(16);
        caution.setSpacingBefore(8);
        document.add(caution);

        addSignatureBlock(document, reportTitle);
    }

    private static void addPageHeader(Document document, String title, String subtitle) throws DocumentException {
        PdfPTable meta = new PdfPTable(new float[]{1.2f, 1f});
        meta.setWidthPercentage(100);

        PdfPCell left = noBorderCell(new Phrase("ForenShield AI 디지털 증거 분석 시스템", font(9, Font.NORMAL, SLATE)));
        left.setHorizontalAlignment(Element.ALIGN_LEFT);
        meta.addCell(left);

        PdfPCell right = noBorderCell();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph reportNo = new Paragraph(blankFallback(subtitle, "-"), font(9, Font.NORMAL, SLATE));
        reportNo.setAlignment(Element.ALIGN_RIGHT);
        Paragraph security = new Paragraph("보안 등급: 내부망 전용", font(9, Font.NORMAL, SLATE));
        security.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(reportNo);
        right.addElement(security);
        meta.addCell(right);
        document.add(meta);

        addRule(document, INK, 1.8f, 4, 2);
        addRule(document, INK, 0.7f, 0, 24);

        Paragraph heading = new Paragraph(title, font(22, Font.BOLD, INK));
        heading.setAlignment(Element.ALIGN_CENTER);
        heading.setSpacingAfter(14);
        document.add(heading);
        PdfPTable smallRule = new PdfPTable(1);
        smallRule.setWidthPercentage(18);
        smallRule.setHorizontalAlignment(Element.ALIGN_CENTER);
        smallRule.setSpacingAfter(18);
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(1.5f);
        cell.setBackgroundColor(INK);
        smallRule.addCell(cell);
        document.add(smallRule);
    }

    private static void addSectionTitle(Document document, int number, String title) throws DocumentException {
        Paragraph section = new Paragraph(number + ". " + title, font(13, Font.BOLD, INK));
        section.setSpacingBefore(7);
        section.setSpacingAfter(5);
        document.add(section);
    }

    private static void addInfoGrid(Document document, List<DisplayRow> rows) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.05f, 2.45f, 1.05f, 2.45f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);

        for (int index = 0; index < rows.size(); index += 2) {
            DisplayRow left = rows.get(index);
            addInfoLabel(table, left.label());
            addInfoValue(table, left.value());

            if (index + 1 < rows.size()) {
                DisplayRow right = rows.get(index + 1);
                addInfoLabel(table, right.label());
                addInfoValue(table, right.value());
            } else {
                addInfoLabel(table, "");
                addInfoValue(table, "");
            }
        }
        document.add(table);
    }

    private static void addVerdictBox(Document document, String label, String summary) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(INK);
        cell.setBorderWidth(1.4f);
        cell.setPadding(14);

        Paragraph small = new Paragraph("종합 판정", font(10, Font.NORMAL, MUTED));
        small.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(small);

        Paragraph verdict = new Paragraph(label, font(18, Font.BOLD, INK));
        verdict.setAlignment(Element.ALIGN_CENTER);
        verdict.setSpacingAfter(6);
        cell.addElement(verdict);

        Paragraph body = new Paragraph(blankFallback(summary, "-"), font(10, Font.NORMAL, SLATE));
        body.setAlignment(Element.ALIGN_CENTER);
        body.setLeading(16);
        cell.addElement(body);

        table.addCell(cell);
        document.add(table);
    }

    private static void addReasonRows(Document document, ReportData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.2f, 4.4f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        if (data.modules.isEmpty()) {
            addReasonRow(table, "판정 요약", data.value("Summary").orElse("-"));
        } else {
            for (ModuleBlock module : data.modules) {
                String detail = module.value("Detected").orElse("-").equalsIgnoreCase("true")
                        ? "해당 모듈에서 의심 신호가 감지되었습니다."
                        : "해당 모듈의 위험 신호가 기준 미만으로 측정되었습니다.";
                addReasonRow(table, moduleLabel(module.name), detail);
            }
        }
        document.add(table);
    }

    private static void addModelScoreBars(Document document, ReportData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1.25f, 3.8f, 0.55f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);

        for (ModuleBlock module : moduleRows(data)) {
            int score = percent(module.value("Score").orElse("-"));
            PdfPCell label = noBorderCell(new Phrase(moduleLabel(module.name), font(10, Font.NORMAL, INK)));
            label.setMinimumHeight(22);
            table.addCell(label);

            PdfPCell bar = noBorderCell(new Phrase(" "));
            bar.setMinimumHeight(22);
            bar.setCellEvent(new ScoreBarCellEvent(score));
            table.addCell(bar);

            PdfPCell scoreCell = noBorderCell(new Phrase(String.valueOf(score), font(10, score >= THRESHOLD ? Font.BOLD : Font.NORMAL, INK)));
            scoreCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(scoreCell);
        }

        document.add(table);
    }

    private static void addVerificationBlock(Document document, String qrContent, String verificationCode)
            throws DocumentException {
        PdfPTable outer = new PdfPTable(new float[]{1.2f, 4.1f});
        outer.setWidthPercentage(100);
        outer.setSpacingAfter(12);

        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.BOX);
        qrCell.setBorderColor(BORDER);
        qrCell.setPadding(6);
        qrCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        qrCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (qrContent != null && !qrContent.isBlank()) {
            Image qrImage = QrCodeImageWriter.createPdfImage(qrContent, 78);
            qrImage.setAlignment(Image.ALIGN_CENTER);
            qrCell.addElement(qrImage);
        } else {
            Paragraph noQr = new Paragraph("QR 없음", font(10, Font.BOLD, LIGHT));
            noQr.setAlignment(Element.ALIGN_CENTER);
            qrCell.addElement(noQr);
        }
        outer.addCell(qrCell);

        PdfPCell info = new PdfPCell();
        info.setBorder(Rectangle.BOX);
        info.setBorderColor(BORDER);
        info.setPadding(8);
        info.addElement(verificationLine("모바일", "QR 코드를 스캔하여 공개 진위 확인 페이지에 접속합니다."));
        info.addElement(verificationLine("PC", "검증 URL 접속 후 검증코드를 입력합니다."));
        info.addElement(verificationLine("검증 URL", blankFallback(qrContent, "-")));
        info.addElement(verificationLine("검증코드", blankFallback(verificationCode, "-")));
        outer.addCell(info);

        document.add(outer);
    }

    private static void addSignatureBlock(Document document, String reportTitle) throws DocumentException {
        Paragraph statement = new Paragraph("위와 같이 " + reportTitle + "를 보고합니다.", font(13, Font.NORMAL, INK));
        statement.setAlignment(Element.ALIGN_CENTER);
        statement.setSpacingBefore(12);
        document.add(statement);

        Paragraph date = new Paragraph(LocalDateTime.now().format(DATE_FORMAT), font(12, Font.BOLD, INK));
        date.setAlignment(Element.ALIGN_CENTER);
        date.setSpacingBefore(10);
        date.setSpacingAfter(10);
        document.add(date);

        PdfPTable signatures = new PdfPTable(new float[]{0.9f, 3.2f, 1.4f});
        signatures.setWidthPercentage(72);
        signatures.setHorizontalAlignment(Element.ALIGN_CENTER);
        addSignatureRow(signatures, "작성자", "분석관", "(서명)");
        addSignatureRow(signatures, "검토자", "책임 검토관", "(서명)");
        document.add(signatures);
    }

    private static PdfPTable reportTable(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);
        return table;
    }

    private static void addFileInfoCell(PdfPTable table, String title, Section section) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(10);
        cell.addElement(new Paragraph(title, font(11, Font.BOLD, INK)));
        cell.addElement(smallLine("증거번호", prefixedEvidenceId(rowValue(section, "Evidence ID"))));
        cell.addElement(smallLine("파일명", rowValue(section, "File Name")));
        cell.addElement(smallLine("파일 크기", rowValue(section, "File Size")));
        cell.addElement(smallLine("SHA-256", shorten(rowValue(section, "SHA-256"), 14, 10)));
        cell.addElement(smallLine("등록 일시", rowValue(section, "Uploaded At")));
        table.addCell(cell);
    }

    private static void addTableHeader(PdfPTable table, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font(9, Font.BOLD, INK)));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBackgroundColor(SURFACE);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private static void addTableRow(PdfPTable table, String... values) {
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(blankFallback(value, "-"), font(8.5f, Font.NORMAL, INK)));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setBorder(Rectangle.BOX);
            cell.setBorderColor(BORDER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private static void addInfoLabel(PdfPTable table, String label) {
        PdfPCell cell = new PdfPCell(new Phrase(blankFallback(label, ""), font(9, Font.BOLD, INK)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(SURFACE);
        cell.setPadding(6);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static void addInfoValue(PdfPTable table, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(blankFallback(value, "-"), font(9, Font.NORMAL, INK)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setPadding(6);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(cell);
    }

    private static void addReasonRow(PdfPTable table, String label, String value) {
        PdfPCell left = noBorderCell(new Phrase(blankFallback(label, "-"), font(10, Font.BOLD, INK)));
        left.setPaddingBottom(8);
        table.addCell(left);

        PdfPCell right = noBorderCell(new Phrase(blankFallback(value, "-"), font(10, Font.NORMAL, INK)));
        right.setPaddingBottom(8);
        table.addCell(right);
    }

    private static void addSignatureRow(PdfPTable table, String role, String name, String sign) {
        addSignatureCell(table, role, true, Element.ALIGN_CENTER);
        addSignatureCell(table, name, false, Element.ALIGN_LEFT);
        addSignatureCell(table, sign, false, Element.ALIGN_CENTER);
    }

    private static void addSignatureCell(PdfPTable table, String text, boolean shaded, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(11, shaded ? Font.BOLD : Font.NORMAL, shaded ? INK : SLATE)));
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER);
        cell.setBackgroundColor(shaded ? SURFACE : Color.WHITE);
        cell.setPadding(8);
        cell.setMinimumHeight(30);
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private static Paragraph verificationLine(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(16);
        paragraph.setSpacingAfter(4);
        paragraph.add(new Phrase(label + "  ", font(9, Font.BOLD, SLATE)));
        paragraph.add(new Phrase(blankFallback(value, "-"), font(9, Font.NORMAL, INK)));
        return paragraph;
    }

    private static Paragraph smallLine(String label, String value) {
        Paragraph paragraph = new Paragraph();
        paragraph.setLeading(15);
        paragraph.setSpacingBefore(4);
        paragraph.add(new Phrase(label + "  ", font(8, Font.BOLD, MUTED)));
        paragraph.add(new Phrase(blankFallback(value, "-"), font(8, Font.NORMAL, INK)));
        return paragraph;
    }

    private static void addFootnote(Document document, String text) throws DocumentException {
        Paragraph paragraph = new Paragraph(text, font(8.5f, Font.NORMAL, MUTED));
        paragraph.setAlignment(Element.ALIGN_RIGHT);
        paragraph.setSpacingAfter(10);
        document.add(paragraph);
    }

    private static void addRule(Document document, Color color, float height, float spacingBefore, float spacingAfter)
            throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(spacingBefore);
        line.setSpacingAfter(spacingAfter);
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(height);
        cell.setBackgroundColor(color);
        line.addCell(cell);
        document.add(line);
    }

    private static PdfPCell noBorderCell() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static PdfPCell noBorderCell(Phrase phrase) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0);
        return cell;
    }

    private static Font font(float size, int style, Color color) {
        return new Font(DOCUMENT_BASE_FONT, size, style, color);
    }

    private static Font monoFont(float size, int style, Color color) {
        Font fallback = FontFactory.getFont(FontFactory.COURIER, size, style, color);
        return fallback == null ? new Font(Font.COURIER, size, style, color) : fallback;
    }

    private static BaseFont loadDocumentBaseFont() {
        String[] candidates = {
                "/System/Library/Fonts/AppleSDGothicNeo.ttc,0",
                "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJKkr-Regular.otf",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        };

        for (String candidate : candidates) {
            String filePath = candidate.contains(",")
                    ? candidate.substring(0, candidate.indexOf(','))
                    : candidate;
            if (!Files.exists(Paths.get(filePath))) {
                continue;
            }
            try {
                return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
                // Try the next platform font.
            }
        }

        try {
            return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 폰트 초기화에 실패했습니다.", ex);
        }
    }

    private static List<ModuleBlock> moduleRows(ReportData data) {
        if (!data.modules.isEmpty()) {
            return data.modules;
        }

        ModuleBlock fallback = new ModuleBlock("Late Fusion");
        fallback.rows.add(new Row("Score", data.value("Risk Score").orElse("0")));
        fallback.rows.add(new Row("Detected", percent(data.value("Risk Score").orElse("0")) >= THRESHOLD ? "true" : "false"));
        fallback.rows.add(new Row("Confidence", data.value("Confidence").orElse("-")));
        return List.of(fallback);
    }

    private static String riskLabel(ReportData data) {
        int score = percent(data.value("Risk Score").orElse("-"));
        if (score >= 80) {
            return "조작 가능성 높음";
        }
        if (score >= THRESHOLD) {
            return "추가 검토 필요";
        }
        return "조작 가능성 낮음";
    }

    private static String compareVerdict(String value) {
        String normalized = blankFallback(value, "-").toLowerCase(Locale.ROOT);
        if (normalized.contains("tamper") || normalized.contains("mismatch") || normalized.contains("위변조")) {
            return "위변조 의심";
        }
        if (normalized.contains("match") || normalized.contains("일치")) {
            return "일치";
        }
        return blankFallback(value, "-");
    }

    private static String compareResult(String value) {
        String normalized = blankFallback(value, "-").toLowerCase(Locale.ROOT);
        if (normalized.contains("mismatch")) {
            return "불일치";
        }
        if (normalized.contains("match")) {
            return "일치";
        }
        if (normalized.contains("skipped")) {
            return "제외";
        }
        return blankFallback(value, "-");
    }

    private static String moduleLabel(String value) {
        String normalized = blankFallback(value, "").toLowerCase(Locale.ROOT);
        if (normalized.contains("fusion") || normalized.equals("deepfake")) {
            return "Late Fusion";
        }
        if (normalized.contains("xception") || normalized.contains("cnn")) {
            return "Xception";
        }
        if (normalized.contains("times") || normalized.contains("temporal")) {
            return "TimeSFormer";
        }
        if (normalized.contains("gmflow") || normalized.contains("optical")) {
            return "GMFlow";
        }
        return blankFallback(value, "분석 모듈");
    }

    private static String moduleMetric(String value) {
        String label = moduleLabel(value);
        return switch (label) {
            case "Late Fusion" -> "종합 위험 프로파일";
            case "Xception" -> "얼굴 경계부·질감 패턴";
            case "TimeSFormer" -> "시간적 일관성";
            case "GMFlow" -> "움직임 벡터";
            default -> "분석 점수";
        };
    }

    private static String moduleNote(String value) {
        String label = moduleLabel(value);
        return switch (label) {
            case "Late Fusion" -> "최종 종합";
            case "Xception" -> "CNN";
            case "TimeSFormer" -> "클립";
            case "GMFlow" -> "프레임쌍";
            default -> "모듈";
        };
    }

    private static int percent(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return 0;
        }
        try {
            String cleaned = raw.replace("%", "").trim();
            double value = Double.parseDouble(cleaned);
            if (value <= 1d) {
                value *= 100d;
            }
            return Math.max(0, Math.min(100, (int) Math.round(value)));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String scoreText(String raw) {
        return String.valueOf(percent(raw));
    }

    private static String prefixedEvidenceId(String value) {
        String safe = blankFallback(value, "-");
        if ("-".equals(safe) || safe.startsWith("EVD-")) {
            return safe;
        }
        return "EVD-" + safe;
    }

    private static String shorten(String value, int head, int tail) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= head + tail + 3) {
            return trimmed;
        }
        return trimmed.substring(0, head) + "..." + trimmed.substring(trimmed.length() - tail);
    }

    private static String rowValue(Section section, String key) {
        if (section == null) {
            return "-";
        }
        return section.value(key).orElse("-");
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static DisplayRow row(String label, String value) {
        return new DisplayRow(label, value);
    }

    private static boolean isCompareReport(String title, ReportData data) {
        return title != null && title.toLowerCase(Locale.ROOT).contains("compare")
                || data.value("Compare ID").isPresent()
                || data.section("Comparison Results") != null;
    }

    private static final class ScoreBarCellEvent implements PdfPCellEvent {
        private final int score;

        private ScoreBarCellEvent(int score) {
            this.score = Math.max(0, Math.min(100, score));
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte canvas = canvases[PdfPTable.LINECANVAS];
            float left = position.getLeft() + 2;
            float right = position.getRight() - 2;
            float centerY = (position.getTop() + position.getBottom()) / 2f;
            float width = right - left;
            float height = 8f;

            canvas.saveState();
            canvas.setColorStroke(BORDER);
            canvas.rectangle(left, centerY - height / 2f, width, height);
            canvas.stroke();

            canvas.setColorFill(score >= THRESHOLD ? INK : LIGHT);
            canvas.rectangle(left, centerY - height / 2f, width * score / 100f, height);
            canvas.fill();

            float thresholdX = left + width * THRESHOLD / 100f;
            canvas.setColorStroke(INK);
            canvas.setLineDash(2f, 2f);
            canvas.moveTo(thresholdX, centerY - 8f);
            canvas.lineTo(thresholdX, centerY + 8f);
            canvas.stroke();
            canvas.restoreState();
        }
    }

    private static final class ReportFooter extends PdfPageEventHelper {
        private final int totalPages;

        private ReportFooter(int totalPages) {
            this.totalPages = totalPages;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottom() - 12;
            canvas.saveState();
            canvas.setColorStroke(INK);
            canvas.setLineWidth(0.6f);
            canvas.moveTo(document.left(), y + 3);
            canvas.lineTo(document.right(), y + 3);
            canvas.stroke();
            canvas.setLineWidth(1.4f);
            canvas.moveTo(document.left(), y);
            canvas.lineTo(document.right(), y);
            canvas.stroke();
            canvas.restoreState();

            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_LEFT,
                    new Phrase("본 문서는 ForenShield AI 시스템이 생성한 분석 보고서이며, 무단 복제·배포를 금합니다.", font(8, Font.NORMAL, MUTED)),
                    document.left(),
                    document.bottom() - 30,
                    0
            );
            ColumnText.showTextAligned(
                    canvas,
                    Element.ALIGN_RIGHT,
                    new Phrase("- " + writer.getPageNumber() + " / " + totalPages + " -", font(8, Font.NORMAL, MUTED)),
                    document.right(),
                    document.bottom() - 30,
                    0
            );
        }
    }

    private record DisplayRow(String label, String value) {
    }

    private record Row(String label, String value) {
        Optional<String> valueFor(String key) {
            return label.equals(key) ? Optional.ofNullable(value) : Optional.empty();
        }
    }

    private static final class Section {
        private final String title;
        private final List<Row> rows = new ArrayList<>();

        private Section(String title) {
            this.title = title;
        }

        private Optional<String> value(String key) {
            return rows.stream()
                    .map(row -> row.valueFor(key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
    }

    private static final class ModuleBlock {
        private final String name;
        private final List<Row> rows = new ArrayList<>();

        private ModuleBlock(String name) {
            this.name = name;
        }

        private Optional<String> value(String key) {
            return rows.stream()
                    .map(row -> row.valueFor(key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
    }

    private static final class ReportData {
        private final Section root = new Section("Overview");
        private final List<Section> sections = new ArrayList<>();
        private final List<ModuleBlock> modules = new ArrayList<>();

        private static ReportData parse(List<String> lines) {
            ReportData data = new ReportData();
            Section currentSection = data.root;
            ModuleBlock currentModule = null;

            for (String rawLine : lines == null ? List.<String>of() : lines) {
                String line = rawLine == null ? "" : rawLine.trim();
                if (line.isBlank()) {
                    currentModule = null;
                    continue;
                }
                if (line.startsWith("--- Module:") && line.endsWith("---")) {
                    String moduleName = line
                            .replace("--- Module:", "")
                            .replace("---", "")
                            .trim();
                    currentModule = new ModuleBlock(moduleName);
                    data.modules.add(currentModule);
                    continue;
                }
                if (line.startsWith("===") && line.endsWith("===")) {
                    String sectionTitle = line.replace("=", "").trim();
                    currentSection = new Section(sectionTitle);
                    data.sections.add(currentSection);
                    currentModule = null;
                    continue;
                }

                Row row = parseRow(line);
                if (currentModule != null) {
                    currentModule.rows.add(row);
                } else {
                    currentSection.rows.add(row);
                }
            }
            return data;
        }

        private Optional<String> value(String key) {
            Optional<String> rootValue = root.value(key);
            if (rootValue.isPresent()) {
                return rootValue;
            }
            return sections.stream()
                    .map(section -> section.value(key))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }

        private Section section(String title) {
            return sections.stream()
                    .filter(section -> section.title.equalsIgnoreCase(title))
                    .findFirst()
                    .orElse(null);
        }
    }

    private record CompareRow(String label, String original, String candidate, String result) {
        private static CompareRow parse(String value) {
            String[] parts = value == null ? new String[0] : value.split("\\|");
            String label = parts.length > 0 ? parts[0].trim() : "-";
            String original = "-";
            String candidate = "-";
            String result = "-";
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.startsWith("original=")) {
                    original = trimmed.substring("original=".length()).trim();
                } else if (trimmed.startsWith("candidate=")) {
                    candidate = trimmed.substring("candidate=".length()).trim();
                } else if (trimmed.startsWith("result=")) {
                    result = trimmed.substring("result=".length()).trim();
                }
            }
            return new CompareRow(label, shorten(original, 18, 10), shorten(candidate, 18, 10), result);
        }
    }

    private static Row parseRow(String line) {
        int separator = line.indexOf(':');
        if (separator >= 0) {
            return new Row(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return new Row(line, "");
    }
}
