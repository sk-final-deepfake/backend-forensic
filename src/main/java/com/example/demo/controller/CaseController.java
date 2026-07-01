package com.example.demo.controller;

import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.evidence.EvidenceDetailService;
import com.example.demo.util.CaseKeyNormalizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Case", description = "사건 상세 API")
@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CaseController {

    private final EvidenceDetailService evidenceDetailService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "사건 상세", description = "사건 ID(사건명)로 소속 증거 목록을 조회합니다.")
    @GetMapping(value = {"", "/{caseId}"}, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public CaseDetailResponse getCaseDetail(
            @PathVariable(required = false) String caseId,
            @RequestParam(required = false) String caseKey
    ) {
        String finalId = CaseKeyNormalizer.normalize(caseKey != null ? caseKey : caseId);

        if (finalId == null || finalId.isBlank()) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "사건 식별자가 필요합니다.");
        }

        return evidenceDetailService.getCaseDetail(
                authUserResolver.requireCurrentUser(),
                finalId
        );
    }
}
