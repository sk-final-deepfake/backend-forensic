package com.example.demo.controller;

import com.example.demo.dto.caseworkflow.SetRepresentativeEvidenceRequest;
import com.example.demo.dto.caseworkflow.UpdateCaseNameRequest;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.evidence.CaseWorkflowService;
import com.example.demo.service.evidence.EvidenceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Case", description = "사건 상세 API")
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final EvidenceDetailService evidenceDetailService;
    private final CaseWorkflowService caseWorkflowService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "사건 상세", description = "사건 ID(사건명)로 소속 증거 목록을 조회합니다.")
    @GetMapping(value = {"", "/{caseId}"}, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse getCaseDetail(
            @PathVariable(required = false) String caseId,
            @RequestParam(required = false) String caseKey
    ) {
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                caseKey,
                caseId
        );
    }

    @Operation(summary = "대표 증거 지정", description = "사건의 대표 증거를 지정합니다. v2 사건 관리 UI 연동.")
    @PatchMapping(value = "/representative", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<Void> setRepresentativeEvidence(
            @RequestParam String caseKey,
            @Valid @RequestBody SetRepresentativeEvidenceRequest request
    ) {
        caseWorkflowService.setRepresentativeEvidence(
                authUserResolver.requireCurrentUser(),
                caseKey,
                request.getEvidenceId()
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사건명 변경", description = "사건에 속한 모든 증거의 사건명을 일괄 변경합니다. v2 사건 편집 UI 연동.")
    @PatchMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse updateCaseName(
            @RequestParam String caseKey,
            @Valid @RequestBody UpdateCaseNameRequest request
    ) {
        String newCaseKey = caseWorkflowService.renameCase(
                authUserResolver.requireCurrentUser(),
                caseKey,
                request.getCaseName()
        );
        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                newCaseKey
        );
    }
}
