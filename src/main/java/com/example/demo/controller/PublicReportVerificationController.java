package com.example.demo.controller;

import com.example.demo.dto.PublicReportViewResponse;
import com.example.demo.dto.PublicReportVerifyResponse;
import com.example.demo.service.report.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public Report Verification", description = "공개 리포트 진위 확인 API")
@RestController
@RequestMapping("/api/v1/public/reports")
@RequiredArgsConstructor
public class PublicReportVerificationController {

    private final ReportPdfService reportPdfService;

    @Operation(summary = "공개 리포트 진위 확인", description = "PDF QR의 검증 토큰 또는 검증코드로 리포트 무결성, 전자서명, 블록체인 앵커 상태를 확인합니다.")
    @GetMapping("/verify")
    public PublicReportVerifyResponse verify(
            @RequestParam(value = "token", required = false) String token,
            @RequestParam(value = "code", required = false) String code
    ) {
        return reportPdfService.verifyPublicReport(token, code);
    }

    @Operation(summary = "외부 보고서 열람 정보 조회", description = "RV 열람코드로 공개 보고서 정보를 조회합니다.")
    @GetMapping(value = "/view", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public PublicReportViewResponse view(@RequestParam("code") String code) {
        return reportPdfService.getPublicReportView(code);
    }

    @Operation(summary = "외부 보고서 PDF 열람", description = "RV 열람코드로 공개 보고서 PDF를 다운로드합니다.")
    @GetMapping("/view/pdf")
    public ResponseEntity<byte[]> downloadPublicReport(@RequestParam("code") String code) {
        ReportPdfService.ReportPdfPayload payload = reportPdfService.downloadPublicReport(code);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + payload.fileName() + "\"")
                .header("X-Report-Hash", payload.reportHash())
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload.content());
    }
}
