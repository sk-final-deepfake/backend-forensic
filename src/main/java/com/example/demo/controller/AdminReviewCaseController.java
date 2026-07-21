package com.example.demo.controller;

import com.example.demo.dto.mypage.AnalysisHistoryPageResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.user.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Review Cases", description = "검토 배정 화면용 사건 목록 API")
@RestController
@RequestMapping("/api/v1/admin/review-cases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminReviewCaseController {

    private final MyPageService myPageService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "검토 배정 사건 목록", description = "기관 관리자·시스템 관리자가 검토 배정 큐를 조회합니다.")
    @GetMapping(produces = "application/json;charset=UTF-8")
    public AnalysisHistoryPageResponse listReviewCases(
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q
    ) {
        return myPageService.getAnalysisHistory(
                authUserResolver.requireCurrentUser(),
                sort,
                page,
                size,
                status,
                q
        );
    }
}
