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
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class PdfDocumentWriter {

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
        return writeReport(title, lines, qrContent, verificationCode, reportNo, true);
    }

    public static byte[] writeDraftReport(String title, List<String> lines) {
        return writeReport(title, lines, null, null, null, false);
    }

    private static byte[] writeReport(
            String title,
            List<String> lines,
            String qrContent,
            String verificationCode,
            String reportNo,
            boolean issued
    ) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            ReportData data = ReportData.parse(lines);
            boolean compareReport = isCompareReport(title, data);
            int totalPages = compareReport ? 2 : 4;

            Document document = new Document(PageSize.A4, 54, 54, 46, 56);
            PdfWriter writer = PdfWriter.getInstance(document, outputStream);
            writer.setPageEvent(new ReportFooter(totalPages));
            document.open();

            if (compareReport) {
                addCompareReportPage(document, data, reportNo, issued);
                document.newPage();
                addIntegrityPage(document, data, reportNo, qrContent, true, issued);
            } else {
                addAnalysisOverviewPage(document, data, reportNo, issued);
                document.newPage();
                addAnalysisDetailPage(document, data, reportNo);
                document.newPage();
                addAnalysisEvidencePage(document, data, reportNo);
                document.newPage();
                addIntegrityPage(document, data, reportNo, qrContent, false, issued);
            }

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF 생성에 실패했습니다.", ex);
        }
    }

    public static byte[] addPreviewWatermark(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return pdfBytes;
        }

        try {
            PdfReader reader = new PdfReader(pdfBytes);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, outputStream);
            PdfGState state = new PdfGState();
            state.setFillOpacity(0.14f);

            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                Rectangle pageSize = reader.getPageSizeWithRotation(page);
                PdfContentByte canvas = stamper.getOverContent(page);
                canvas.saveState();
                canvas.setGState(state);
                ColumnText.showTextAligned(
                        canvas,
                        Element.ALIGN_CENTER,
                        new Phrase("미리보기 - 검증 불가", font(48, Font.BOLD, MUTED)),
                        pageSize.getWidth() / 2f,
                        pageSize.getHeight() / 2f,
                        35
                );
                canvas.restoreState();
            }

            stamper.close();
            reader.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 미리보기 워터마크 적용에 실패했습니다.", ex);
        }
    }

    private static void addAnalysisOverviewPage(
            Document document,
            ReportData data,
            String reportNo,
            boolean issued
    )
            throws DocumentException {
        addPageHeader(document, "전체 종합 보고서", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "문서 개요");
        addInfoGrid(document, List.of(
                row("보고서 유형", "딥페이크 분석 종합 보고서"),
                row("검토 상태", issued ? "검토 승인 완료" : "검토 승인 대기"),
                row("문서 상태", issued ? "최종 발행본" : "미발행 · 미리보기"),
                row("생성자", data.value("Analyst Name").orElse("-")),
                row("생성일", LocalDateTime.now().format(DATE_TIME_FORMAT)),
                row("검증 방식", issued ? "QR 발행정보 조회 · PDF 해시 대조" : "발행 등록 후 QR 조회 · PDF 해시 대조")
        ));

        addSectionTitle(document, 2, "사건 및 증거 요약");
        addInfoGrid(document, List.of(
                row("사건명", data.value("Case Name").orElse("-")),
                row("사건 번호", data.value("Case Number").orElse("-")),
                row("대상 증거", prefixedEvidenceId(data.value("Evidence ID").orElse("-"))),
                row("파일명", data.value("File Name").orElse("-")),
                row("등록 일시", data.value("Uploaded At").orElse("-")),
                row("파일 유형", data.value("File Type").orElse("-")),
                row("분석 일시", data.value("Analyzed At").orElse("-")),
                row("원본 해시", shorten(data.value("SHA-256").orElse("-"), 18, 12))
        ));

        addSectionTitle(document, 3, "최종 분석 판정");
        addVerdictBox(document, riskLabel(data), data.value("Summary").orElse("-"));
        addInfoGrid(document, List.of(
                row("모델 종합 출력 점수", scoreText(data.value("Risk Score").orElse("-")) + " / 100"),
                row("모델 신뢰도 출력값", scoreText(data.value("Confidence").orElse("-")) + " / 100"),
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
        addFootnote(document, "※ 점수는 각 분석 모듈이 반환한 출력값입니다. 탐지 여부는 저장된 모듈 판정값을 따릅니다.");

        addSectionTitle(document, 2, "모듈별 측정 항목");
        PdfPTable table = reportTable(new float[]{1.2f, 2.2f, 0.7f, 0.9f, 1f});
        addTableHeader(table, "모듈", "측정 항목", "점수", "판정", "비고");
        List<ModuleBlock> modules = moduleRows(data);
        for (ModuleBlock module : modules) {
            int score = percent(module.value("Score").orElse("-"));
            String detected = detectedLabel(module.value("Detected").orElse(null));
            addTableRow(table,
                    moduleLabel(module.name),
                    moduleMetric(module.name),
                    String.valueOf(score),
                    detected,
                    moduleNote(module.name));
        }
        if (modules.isEmpty()) {
            addTableRow(table, "저장된 모듈 결과 없음", "-", "-", "확인 필요", "-");
        }
        document.add(table);

    }

    private static void addAnalysisEvidencePage(Document document, ReportData data, String reportNo)
            throws DocumentException {
        addPageHeader(document, "AI 상세 근거", "보고서 번호: " + blankFallback(reportNo, "-"));

        if (!hasDetailedEvidence(data)) {
            addSectionTitle(document, 1, "상세 시각화 데이터");
            addAvailabilityNotice(
                    document,
                    "저장된 실제 모듈 타임라인, 의심 구간 또는 대표 프레임 데이터가 없습니다. 임의 데이터를 생성하지 않습니다."
            );
            return;
        }

        addSectionTitle(document, 1, "모듈 타임라인 요약");
        addModuleTimelineSummary(document, data);

        addSectionTitle(document, 2, "위험도 상위 타임라인 지점");
        addTimelinePointTable(document, data);

        addSectionTitle(document, 3, "실제 의심 구간");
        addSuspiciousSegmentTable(document, data);

        addSectionTitle(document, 4, "대표 프레임 기록");
        addRepresentativeFrameTable(document, data);
    }

    private static void addCompareReportPage(
            Document document,
            ReportData data,
            String reportNo,
            boolean issued
    )
            throws DocumentException {
        addPageHeader(document, "비교검증 보고서", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "검증 개요");
        addInfoGrid(document, List.of(
                row("보고서 유형", "비교검증 보고서"),
                row("검토 상태", issued ? "검토 승인 완료" : "검토 승인 대기"),
                row("비교검증 ID", data.value("Compare ID").orElse("-")),
                row("생성일", data.value("Created At").orElse("-")),
                row("판정", compareVerdict(data.value("Verdict").orElse("-"))),
                row("검증 방식", "해시 · 파일 속성 · 메타데이터 항목 대조")
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
                String serializedRow = row.value.isBlank()
                        ? row.label
                        : row.label + ": " + row.value;
                CompareRow item = CompareRow.parse(serializedRow);
                addTableRow(result, item.label, item.original, item.candidate, compareResult(item.result));
            }
        }
        document.add(result);
    }

    private static void addIntegrityPage(
            Document document,
            ReportData data,
            String reportNo,
            String qrContent,
            boolean compareReport,
            boolean issued
    ) throws DocumentException {
        addPageHeader(document, "무결성 및 감사 이력", "보고서 번호: " + blankFallback(reportNo, "-"));

        addSectionTitle(document, 1, "무결성 검증 결과");
        addInfoGrid(document, List.of(
                row("분석대상 SHA-256", data.value("SHA-256").orElse(rowValue(data.section("Original File Information"), "SHA-256"))),
                row("최종 PDF SHA-256", issued ? "QR 검증 페이지에서 확인" : "최종 발행 전"),
                row("해시 알고리즘", "SHA-256"),
                row("QR 검증 범위", issued ? "발행 등록정보 조회" : "발행 등록 전"),
                row("PDF 전자서명", issued ? "미적용" : "발행 전"),
                row("PDF 동일성 확인", issued ? "검증 페이지에서 파일 해시 대조" : "발행 전")
        ));

        addSectionTitle(document, 2, "외부 해시 앵커 기록");
        addInfoGrid(document, List.of(
                row("앵커 상태", issued ? "검증 페이지에서 확인" : "발행 전"),
                row("역할", "발행 시점과 해시 존재 사실의 보조 기록"),
                row("네트워크", issued ? "등록된 경우 검증 페이지에 표시" : "-"),
                row("트랜잭션", issued ? "등록된 경우 검증 페이지에 표시" : "-"),
                row("법적 효력", "자동으로 보장하지 않음"),
                row("상세 확인", issued ? "QR 검증 페이지" : "검토 승인 대기")
        ));

        addSectionTitle(document, 3, "CoC 처리 이력");
        Paragraph cocNotice = new Paragraph(
                "현재 PDF 생성 데이터에는 실제 Chain of Custody 상세 이력이 연결되어 있지 않아 임의 처리 이력을 표시하지 않습니다.",
                font(10, Font.NORMAL, SLATE)
        );
        cocNotice.setLeading(16);
        cocNotice.setSpacingAfter(10);
        document.add(cocNotice);

        addSectionTitle(document, 4, "발행 등록 조회");
        addVerificationBlock(document, qrContent, issued);
        Paragraph verificationCaution = new Paragraph(
                "QR 조회로는 보고서 번호와 발행 등록정보를 확인할 수 있습니다. PDF 파일이 발행 당시 원본과 동일한지 확인하려면, 검증 페이지에 해당 PDF를 업로드하여 등록된 SHA-256 해시값과 대조하시기 바랍니다.",
                font(10, Font.BOLD, SLATE)
        );
        verificationCaution.setLeading(15);
        verificationCaution.setSpacingBefore(6);
        verificationCaution.setSpacingAfter(6);
        document.add(verificationCaution);
        Paragraph caution = new Paragraph(
                compareReport
                        ? "본 결과는 보고서에 특정된 두 파일의 기술적 동일성 또는 차이를 나타냅니다. 불일치의 원인, 고의적 변조 여부 또는 법률상 원본성은 본 결과만으로 판단할 수 없습니다."
                        : "본 보고서의 AI 분석 결과는 조작 여부를 확정하는 판정이 아니며, 확률에 기반한 참고 자료입니다. 최종 판단 시에는 원본 자료, 사건 맥락, 파일 비교 결과 및 전문가 검토를 종합하시기 바랍니다.",
                font(11, Font.NORMAL, INK)
        );
        caution.setLeading(16);
        caution.setSpacingBefore(8);
        document.add(caution);

        addApprovalBlock(document, data, issued);
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
        right.addElement(reportNo);
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
        List<String> evidenceItems = sectionValues(data.section("Evidence Items"), "Evidence Item");
        if (!evidenceItems.isEmpty()) {
            for (int index = 0; index < evidenceItems.size(); index++) {
                addReasonRow(table, "근거 " + (index + 1), evidenceItems.get(index));
            }
        } else if (data.modules.isEmpty()) {
            addReasonRow(table, "판정 요약", data.value("Summary").orElse("-"));
        } else {
            for (ModuleBlock module : data.modules) {
                String detail = switch (detectedLabel(module.value("Detected").orElse(null))) {
                    case "탐지 신호 있음" -> "해당 모듈의 저장된 판정값에 탐지 신호가 있습니다.";
                    case "탐지 신호 없음" -> "해당 모듈의 저장된 판정값에 탐지 신호가 없습니다.";
                    default -> "해당 모듈의 저장된 탐지 판정값을 확인할 수 없습니다.";
                };
                addReasonRow(table, moduleLabel(module.name), detail);
            }
        }
        document.add(table);
    }

    private static void addModelScoreBars(Document document, ReportData data) throws DocumentException {
        List<ModuleBlock> modules = moduleRows(data);
        if (modules.isEmpty()) {
            Paragraph notice = new Paragraph(
                    "저장된 모듈별 추론 결과가 없습니다.",
                    font(10, Font.NORMAL, SLATE)
            );
            notice.setSpacingAfter(10);
            document.add(notice);
            return;
        }

        PdfPTable table = new PdfPTable(new float[]{1.25f, 3.8f, 0.55f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);
        table.setSplitLate(false);

        for (ModuleBlock module : modules) {
            int score = percent(module.value("Score").orElse("-"));
            PdfPCell label = noBorderCell(new Phrase(moduleLabel(module.name), font(10, Font.NORMAL, INK)));
            label.setMinimumHeight(22);
            table.addCell(label);

            PdfPCell bar = noBorderCell(new Phrase(" "));
            bar.setMinimumHeight(22);
            bar.setCellEvent(new ScoreBarCellEvent(score));
            table.addCell(bar);

            PdfPCell scoreCell = noBorderCell(new Phrase(String.valueOf(score), font(10, Font.BOLD, INK)));
            scoreCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(scoreCell);
        }

        document.add(table);
    }

    private static boolean hasDetailedEvidence(ReportData data) {
        return !sectionValues(data.section("Module Timeline Summaries"), "Module Timeline").isEmpty()
                || !sectionValues(data.section("Timeline Points"), "Timeline Point").isEmpty()
                || !sectionValues(data.section("Suspicious Segments"), "Suspicious Segment").isEmpty()
                || !sectionValues(data.section("Representative Frames"), "Representative Frame").isEmpty();
    }

    private static void addModuleTimelineSummary(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Module Timeline Summaries");
        List<String> values = sectionValues(section, "Module Timeline");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "저장된 모듈별 타임라인 요약이 없습니다.");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.05f, 1.35f, 0.75f, 0.75f, 0.8f, 1.1f});
        addTableHeader(table, "모듈", "모델", "영상 점수", "판정 기준", "탐지", "저장 데이터");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            String points = field(fields, "points");
            String segments = field(fields, "segments");
            addTableRow(table,
                    moduleLabel(field(fields, "module")),
                    field(fields, "model"),
                    percentageText(field(fields, "videoScore")),
                    percentageText(field(fields, "threshold")),
                    detectedLabel(field(fields, "detected")),
                    "지점 " + blankFallback(points, "0") + " / 구간 " + blankFallback(segments, "0"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
    }

    private static void addTimelinePointTable(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Timeline Points");
        List<String> values = sectionValues(section, "Timeline Point");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "저장된 프레임·클립·프레임쌍 타임라인 지점이 없습니다.");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.15f, 0.8f, 1.2f, 0.75f, 2f});
        addTableHeader(table, "출처", "종류", "시점/구간", "위험도", "참조");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            addTableRow(table,
                    moduleLabel(field(fields, "source")),
                    timelineKindLabel(field(fields, "kind")),
                    timeRange(field(fields, "start"), field(fields, "end")),
                    percentageText(field(fields, "score")),
                    field(fields, "reference"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
    }

    private static void addSuspiciousSegmentTable(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Suspicious Segments");
        List<String> values = sectionValues(section, "Suspicious Segment");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "저장된 실제 의심 구간이 없습니다.");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.1f, 1.25f, 0.8f, 2.75f});
        addTableHeader(table, "출처", "구간", "최대 위험도", "저장 사유");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            addTableRow(table,
                    moduleLabel(field(fields, "source")),
                    timeRange(field(fields, "start"), field(fields, "end")),
                    percentageText(field(fields, "score")),
                    field(fields, "reason"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
    }

    private static void addRepresentativeFrameTable(Document document, ReportData data) throws DocumentException {
        Section section = data.section("Representative Frames");
        List<String> values = sectionValues(section, "Representative Frame");
        if (values.isEmpty()) {
            addAvailabilityNotice(document, "저장된 대표 프레임 기록이 없습니다.");
            return;
        }

        PdfPTable table = reportTable(new float[]{1.4f, 1.15f, 1.1f, 1.05f});
        addTableHeader(table, "시점", "프레임 번호", "위험도", "이미지 참조");
        for (String value : values) {
            Map<String, String> fields = parseFields(value);
            String timestamp = field(fields, "timestamp");
            if ("-".equals(timestamp)) {
                timestamp = formatSeconds(field(fields, "timeSec"));
            }
            addTableRow(table,
                    timestamp,
                    field(fields, "frameNumber"),
                    percentageText(field(fields, "score")),
                    booleanLabel(field(fields, "imageRegistered"), "등록됨", "없음"));
        }
        document.add(table);
        addIncludedCountFootnote(document, section, values.size());
        addFootnote(document, "※ 이미지 접근 URL은 만료될 수 있어 PDF에 기록하지 않고, 발행 당시 이미지 참조 등록 여부만 표시합니다.");
    }

    private static void addAvailabilityNotice(Document document, String message) throws DocumentException {
        Paragraph notice = new Paragraph(message, font(9.5f, Font.NORMAL, SLATE));
        notice.setLeading(15);
        notice.setSpacingAfter(10);
        document.add(notice);
    }

    private static void addIncludedCountFootnote(Document document, Section section, int includedCount)
            throws DocumentException {
        int totalCount = parseInteger(rowValue(section, "Total Count"), includedCount);
        if (totalCount > includedCount) {
            addFootnote(document, "※ 위험도 기준 상위 " + includedCount + "건을 표시합니다. 저장된 전체 데이터: " + totalCount + "건");
        }
    }

    private static void addVerificationBlock(Document document, String qrContent, boolean issued)
            throws DocumentException {
        if (!issued) {
            PdfPTable placeholder = new PdfPTable(1);
            placeholder.setWidthPercentage(100);
            placeholder.setSpacingAfter(12);

            PdfPCell placeholderCell = new PdfPCell();
            placeholderCell.setBorder(Rectangle.BOX);
            placeholderCell.setBorderColor(LIGHT_BORDER);
            placeholderCell.setBackgroundColor(SURFACE);
            placeholderCell.setPadding(16);
            placeholderCell.setMinimumHeight(66);
            placeholderCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            Paragraph message = new Paragraph(
                    "검증 QR과 URL은 발행 등록 후 생성됩니다.",
                    font(10.5f, Font.BOLD, SLATE)
            );
            message.setAlignment(Element.ALIGN_CENTER);
            placeholderCell.addElement(message);
            placeholder.addCell(placeholderCell);
            document.add(placeholder);
            return;
        }

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
        info.addElement(verificationLine("발행 상태", "기관 발행 등록 완료"));
        info.addElement(verificationLine("조회 방법", "QR 코드를 스캔하거나 아래 URL을 엽니다."));
        info.addElement(verificationLine("검증 URL", blankFallback(qrContent, "발행 URL 생성 오류")));
        info.addElement(verificationLine("검증 범위", "발행 등록정보 조회 (PDF 파일 자체는 미검사)"));
        outer.addCell(info);

        document.add(outer);
    }

    private static void addApprovalBlock(Document document, ReportData data, boolean issued) throws DocumentException {
        Paragraph title = new Paragraph("작성·검토 및 승인 정보", font(12, Font.BOLD, INK));
        title.setSpacingBefore(6);
        title.setSpacingAfter(4);
        document.add(title);

        String approvalTime = data.value("Review Approved At").orElse("-");
        String approvalInfo = issued
                ? "시스템 승인" + ("-".equals(approvalTime) ? "" : " · " + approvalTime)
                : "승인 전";
        addInfoGrid(document, List.of(
                row("작성자", actorSummary(data, "Analyst", "-")),
                row("검토자", actorSummary(data, "Reviewer", "미배정")),
                row("검토 결과", reviewStatusLabel(data.value("Review Status").orElse("NONE"))),
                row("승인 정보", approvalInfo),
                row("분석 완료", data.value("Analyzed At").orElse(data.value("Created At").orElse("-"))),
                row("발행 상태", issued ? "기관 발행 등록 완료" : "미발행 · 미리보기")
        ));
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
        return data.modules;
    }

    private static String riskLabel(ReportData data) {
        return switch (data.value("Risk Level").orElse("").trim().toUpperCase(Locale.ROOT)) {
            case "HIGH" -> "조작 가능성 관련 신호 높음";
            case "MEDIUM" -> "추가 검토 필요";
            case "LOW" -> "조작 가능성 관련 신호 낮음";
            default -> "판정 정보 없음";
        };
    }

    private static String detectedLabel(String value) {
        if (value == null || value.isBlank() || "-".equals(value)) {
            return "확인 필요";
        }
        if ("true".equalsIgnoreCase(value.trim())) {
            return "탐지 신호 있음";
        }
        if ("false".equalsIgnoreCase(value.trim())) {
            return "탐지 신호 없음";
        }
        return "확인 필요";
    }

    private static String actorSummary(ReportData data, String prefix, String fallback) {
        List<String> parts = new ArrayList<>();
        data.value(prefix + " Name")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(parts::add);
        data.value(prefix + " Department")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(parts::add);
        data.value(prefix + " Position")
                .filter(value -> !value.isBlank() && !"-".equals(value))
                .ifPresent(parts::add);
        return parts.isEmpty() ? fallback : String.join(" / ", parts);
    }

    private static String reviewStatusLabel(String value) {
        return switch (blankFallback(value, "NONE").trim().toUpperCase(Locale.ROOT)) {
            case "REPORT_APPROVED" -> "기관 내부 절차상 최종 승인";
            case "REVIEW_COMPLETED" -> "검토 완료";
            case "REVIEW_ASSIGNED" -> "검토 중";
            case "REVIEW_REQUESTED" -> "검토자 배정 대기";
            case "REVIEW_SUPPLEMENT_REQUESTED", "SUPPLEMENT_REQUESTED",
                    "REVIEW_REVISION_REQUESTED", "REVISION_REQUESTED", "REVIEW_NEEDS_CHANGES" -> "보완 요청";
            default -> "검토 전";
        };
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

    private static List<String> sectionValues(Section section, String label) {
        if (section == null) {
            return List.of();
        }
        return section.rows.stream()
                .filter(row -> row.label.equals(label))
                .map(Row::value)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private static Map<String, String> parseFields(String value) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return fields;
        }
        for (String part : value.split("\\|")) {
            String trimmed = part.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            fields.put(trimmed.substring(0, separator).trim(), trimmed.substring(separator + 1).trim());
        }
        return fields;
    }

    private static String field(Map<String, String> fields, String key) {
        return blankFallback(fields.get(key), "-");
    }

    private static String timelineKindLabel(String value) {
        return switch (blankFallback(value, "-").trim().toUpperCase(Locale.ROOT)) {
            case "FRAME" -> "프레임";
            case "CLIP" -> "클립";
            case "PAIR" -> "프레임쌍";
            default -> blankFallback(value, "-");
        };
    }

    private static String timeRange(String start, String end) {
        String formattedStart = formatSeconds(start);
        String formattedEnd = formatSeconds(end);
        if (formattedStart.equals(formattedEnd)) {
            return formattedStart;
        }
        return formattedStart + " - " + formattedEnd;
    }

    private static String formatSeconds(String raw) {
        Double value = parseDouble(raw);
        if (value == null) {
            return "-";
        }
        double safe = Math.max(0.0, value);
        int hours = (int) (safe / 3600.0);
        int minutes = (int) ((safe % 3600.0) / 60.0);
        double seconds = safe % 60.0;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%05.2f", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%02d:%05.2f", minutes, seconds);
    }

    private static String percentageText(String raw) {
        Double value = parseDouble(raw);
        if (value == null) {
            return "-";
        }
        if (Math.abs(value) <= 1.0) {
            value *= 100.0;
        }
        double bounded = Math.max(0.0, Math.min(100.0, value));
        return String.format(Locale.ROOT, "%.1f%%", bounded);
    }

    private static String booleanLabel(String raw, String trueLabel, String falseLabel) {
        if ("true".equalsIgnoreCase(blankFallback(raw, ""))) {
            return trueLabel;
        }
        if ("false".equalsIgnoreCase(blankFallback(raw, ""))) {
            return falseLabel;
        }
        return "확인 필요";
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInteger(String raw, int fallback) {
        if (raw == null || raw.isBlank() || "-".equals(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

            canvas.setColorFill(INK);
            canvas.rectangle(left, centerY - height / 2f, width * score / 100f, height);
            canvas.fill();
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
                    original = normalizeSerializedValue(trimmed.substring("original=".length()));
                } else if (trimmed.startsWith("candidate=")) {
                    candidate = normalizeSerializedValue(trimmed.substring("candidate=".length()));
                } else if (trimmed.startsWith("result=")) {
                    result = normalizeSerializedValue(trimmed.substring("result=".length()));
                }
            }
            return new CompareRow(label, shorten(original, 18, 10), shorten(candidate, 18, 10), result);
        }

        private static String normalizeSerializedValue(String value) {
            if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
                return "-";
            }
            return value.trim();
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
