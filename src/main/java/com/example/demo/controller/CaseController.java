package com.example.demo.controller;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.detail.CaseDetailResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.EvidenceDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @GetMapping(value = {"", "/{caseId}"}, produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    public ResponseEntity<?> getCaseDetail(
            @PathVariable(required = false) String caseId,
            @RequestParam(required = false) String caseKey
    ) {
        String finalId = caseKey != null ? caseKey : caseId;
        
        if (finalId == null || finalId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("INVALID_REQUEST")
                            .message("사건 식별자가 필요합니다.")
                            .build());
        }

        try {
            CaseDetailResponse response = evidenceDetailService.getCaseDetail(
                    authUserResolver.requireCurrentUser(),
                    finalId
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("CASE_NOT_FOUND")
                            .message(e.getMessage())
                            .build());
        }
    }
}
