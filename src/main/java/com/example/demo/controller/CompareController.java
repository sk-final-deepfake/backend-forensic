package com.example.demo.controller;

import com.example.demo.dto.compare.CompareResultResponse;
import com.example.demo.dto.compare.CompareVerifyResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.CompareVerificationService;
import com.example.demo.service.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Compare", description = "비교 검증 API")
@RestController
@RequestMapping("/api/v1/compare")
@RequiredArgsConstructor
public class CompareController {

    private final CompareVerificationService compareVerificationService;
    private final ReportPdfService reportPdfService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "비교 검증 실행", description = "원본 증거와 업로드한 대상 파일의 해시·메타데이터를 비교합니다.")
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompareVerifyResponse verify(
            @Parameter(description = "원본 증거 ID") @RequestParam("evidenceId") Long evidenceId,
            @Parameter(description = "비교 대상 영상 파일") @RequestParam("file") MultipartFile file
    ) {
        return compareVerificationService.verify(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                file
        );
    }

    @Operation(summary = "비교 검증 결과 조회")
    @GetMapping("/{compareId}")
    public CompareResultResponse getResult(@PathVariable Long compareId) {
        return compareVerificationService.getResult(
                authUserResolver.requireCurrentUser(),
                compareId
        );
    }

    @Operation(summary = "비교 검증 PDF 다운로드", description = "RQ-CMP-104: 비교 검증 결과 PDF 리포트")
    @GetMapping("/{compareId}/reports/pdf")
    public ResponseEntity<byte[]> downloadCompareReport(@PathVariable Long compareId) {
        ReportPdfService.ReportPdfPayload payload = reportPdfService.generateCompareReport(
                authUserResolver.requireCurrentUser(),
                compareId
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + payload.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload.content());
    }
}
