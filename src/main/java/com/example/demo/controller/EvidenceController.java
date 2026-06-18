package com.example.demo.controller;

import com.example.demo.dto.BlockchainAnchorStatusResponse;
import com.example.demo.dto.CocChainVerifyResponse;
import com.example.demo.dto.AnalysisStatusResponse;
import com.example.demo.dto.AnalysisTrendResponse;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.RecentAnalysisResponse;
import com.example.demo.dto.ReportVerifyResponse;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.AnalysisCancelService;
import com.example.demo.service.AnalysisService;
import com.example.demo.service.AnalysisStatusService;
import com.example.demo.service.EvidenceCancelService;
import com.example.demo.service.EvidenceDetailService;
import com.example.demo.service.EvidenceStatsService;
import com.example.demo.service.FileService;
import com.example.demo.service.BlockchainAnchorService;
import com.example.demo.service.CocChainVerificationService;
import com.example.demo.service.ReportPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Evidence", description = "증거 관련 API")
@RestController
@RequestMapping({"/api/v1/evidences", "/api/evidences"})
@RequiredArgsConstructor
public class EvidenceController {

    private final FileService fileService;
    private final EvidenceStatsService evidenceStatsService;
    private final AnalysisService analysisService;
    private final EvidenceDetailService evidenceDetailService;
    private final EvidenceCancelService evidenceCancelService;
    private final AnalysisCancelService analysisCancelService;
    private final AnalysisStatusService analysisStatusService;
    private final ReportPdfService reportPdfService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final CocChainVerificationService cocChainVerificationService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "대시보드 통계", description = "RQ-DSH-043: 총 분석·딥페이크 탐지·완료·처리 중 건수를 조회합니다.")
    @GetMapping("/stats")
    public EvidenceStatsResponse stats() {
        return evidenceStatsService.getDashboardStats(
                authUserResolver.requireCurrentUser().getUserId()
        );
    }

    @Operation(summary = "최근 분석 추이", description = "RQ-DSH-044: 최근 N일 일별 완료 분석 건수(꺾은선 차트용)를 조회합니다.")
    @GetMapping("/stats/trend")
    public AnalysisTrendResponse analysisTrend(
            @Parameter(description = "조회 일수 (1~30, 기본 7)") @RequestParam(defaultValue = "7") int days
    ) {
        return evidenceStatsService.getAnalysisTrend(
                authUserResolver.requireCurrentUser().getUserId(),
                days
        );
    }

    @Operation(summary = "최근 분석 이력", description = "RQ-DSH-045: 대시보드 위젯용 최근 분석 요청 목록(3~5건)을 조회합니다.")
    @GetMapping("/stats/recent")
    public RecentAnalysisResponse recentAnalyses(
            @Parameter(description = "조회 건수 (3~5, 기본 5)") @RequestParam(defaultValue = "5") int limit
    ) {
        return evidenceStatsService.getRecentAnalyses(
                authUserResolver.requireCurrentUser().getUserId(),
                limit
        );
    }

    @Operation(summary = "파일 업로드", description = "파일을 서버에 업로드하고 SHA-256 해시를 생성합니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse upload(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "사건명") @RequestParam(value = "caseName", required = false) String caseName
    ) {
        return fileService.upload(
                file,
                caseName,
                authUserResolver.requireCurrentUser().getUserId()
        );
    }

    @Operation(summary = "업로드 취소", description = "분석 시작 전 업로드된 증거를 취소하고 DB·S3에서 삭제합니다.")
    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<Void> cancelUpload(@PathVariable Long evidenceId) {
        evidenceCancelService.cancelUpload(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "분석 상태 조회", description = "증거의 큐 대기·진행·완료·실패 상태와 진행률을 조회합니다. FAILED 시 errorCode·errorMessage 포함.")
    @GetMapping("/{evidenceId}/analysis-status")
    public AnalysisStatusResponse getAnalysisStatus(@PathVariable Long evidenceId) {
        return analysisStatusService.getStatus(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "증거 초기화", description = "메인 화면 초기화 시 증거와 연결된 분석 요청을 모두 삭제하고 DB·S3에서 제거합니다.")
    @DeleteMapping("/{evidenceId}/reset")
    public ResponseEntity<Void> resetEvidence(@PathVariable Long evidenceId) {
        evidenceCancelService.resetEvidence(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "분석 중단", description = "대기·진행 중인 분석 작업만 중단합니다. 원본 증거는 DB·S3에 유지됩니다.")
    @DeleteMapping("/{evidenceId}/analysis")
    public ResponseEntity<Void> cancelAnalysis(@PathVariable Long evidenceId) {
        analysisCancelService.cancelAnalysis(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "증거 상세", description = "증거 ID로 상세 분석·메타데이터·무결성 정보를 조회합니다.")
    @GetMapping("/{evidenceId}/detail")
    public EvidenceDetailResponse getEvidenceDetail(@PathVariable Long evidenceId) {
        return evidenceDetailService.getEvidenceDetail(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "분석 시작", description = "업로드된 증거에 대한 분석을 요청합니다. evidenceId(단건) 또는 evidenceIds(복수) 중 하나를 사용합니다.")
    @PostMapping("/analyze")
    public StartAnalysisResponse startAnalysis(@RequestBody StartAnalysisRequest request) {
        return analysisService.startAnalysis(
                authUserResolver.requireCurrentUser(),
                request
        );
    }

    @Operation(summary = "분석 PDF 리포트 다운로드", description = "RQ-DTL-082~086: 분석 결과 PDF 리포트")
    @GetMapping("/{evidenceId}/reports/pdf")
    public ResponseEntity<byte[]> downloadAnalysisReport(@PathVariable Long evidenceId) {
        ReportPdfService.ReportPdfPayload payload = reportPdfService.generateEvidenceReport(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + payload.fileName() + "\"")
                .header("X-Report-Hash", payload.reportHash())
                .contentType(MediaType.APPLICATION_PDF)
                .body(payload.content());
    }

    @Operation(summary = "PDF reportHash 검증", description = "RQ-DTL-087: 저장된 PDF reportHash 무결성 검증")
    @GetMapping("/{evidenceId}/reports/verify")
    public ReportVerifyResponse verifyAnalysisReport(
            @PathVariable Long evidenceId,
            @RequestParam("reportHash") String reportHash
    ) {
        return reportPdfService.verifyReportHash(
                authUserResolver.requireCurrentUser(),
                evidenceId,
                reportHash
        );
    }

    @Operation(summary = "CoC 해시 체인 검증", description = "RQ-HIS-107: 증거 감사 로그 해시 체인 무결성 검증")
    @GetMapping("/{evidenceId}/coc/verify")
    public CocChainVerifyResponse verifyCocChain(@PathVariable Long evidenceId) {
        return cocChainVerificationService.verifyEvidenceChain(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }

    @Operation(summary = "블록체인 앵커 상태", description = "RQ-DTL-078: 원본 해시·PDF reportHash·머클 루트 앵커 상태 조회")
    @GetMapping("/{evidenceId}/blockchain")
    public BlockchainAnchorStatusResponse blockchainStatus(@PathVariable Long evidenceId) {
        return blockchainAnchorService.getEvidenceAnchorStatus(
                authUserResolver.requireCurrentUser(),
                evidenceId
        );
    }
}
