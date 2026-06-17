package com.example.demo.controller;

import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.EvidenceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Case", description = "사건 상세 API")
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final EvidenceDetailService evidenceDetailService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "사건 상세", description = "사건 ID(사건명)로 소속 증거 목록을 조회합니다.")
    @GetMapping("/{caseId}")
    public CaseDetailResponse getCaseDetail(@PathVariable String caseId) {
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                caseId
        );
    }
}
