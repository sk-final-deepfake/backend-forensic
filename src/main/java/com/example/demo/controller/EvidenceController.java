package com.example.demo.controller;

import com.example.demo.dto.ErrorResponse;
import com.example.demo.dto.EvidenceStatsResponse;
import com.example.demo.dto.FileUploadResponse;
import com.example.demo.dto.StartAnalysisRequest;
import com.example.demo.dto.StartAnalysisResponse;
import com.example.demo.dto.detail.EvidenceDetailResponse;
import com.example.demo.exception.FileSizeExceededException;
import com.example.demo.exception.HashGenerationException;
import com.example.demo.exception.UnsupportedFileTypeException;
import com.example.demo.security.AuthUserResolver;
import com.example.demo.service.AnalysisService;
import com.example.demo.service.EvidenceCancelService;
import com.example.demo.service.EvidenceDetailService;
import com.example.demo.service.EvidenceStatsService;
import com.example.demo.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/evidences")
@RequiredArgsConstructor
public class EvidenceController {

    private final FileService fileService;
    private final EvidenceStatsService evidenceStatsService;
    private final AnalysisService analysisService;
    private final EvidenceDetailService evidenceDetailService;
    private final EvidenceCancelService evidenceCancelService;
    private final AuthUserResolver authUserResolver;

    @Operation(summary = "미디어별 분석 건수", description = "로그인 사용자의 분석 시작(요청) 건수를 조회합니다.")
    @GetMapping("/stats")
    public ResponseEntity<EvidenceStatsResponse> stats() {
        return ResponseEntity.ok(evidenceStatsService.getMediaStats(
                authUserResolver.requireCurrentUser().getUserId()
        ));
    }

    @Operation(summary = "파일 업로드", description = "파일을 서버에 업로드하고 SHA-256 해시를 생성합니다.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            @Parameter(description = "사건명") @RequestParam(value = "caseName", required = false) String caseName
    ) {
        try {
            FileUploadResponse response = fileService.upload(
                    file,
                    caseName,
                    authUserResolver.requireCurrentUser().getUserId()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            String errorCode = "INVALID_REQUEST";
            if ("FILE_NOT_FOUND".equals(e.getMessage()) || "업로드된 파일이 없습니다.".equals(e.getMessage())) {
                errorCode = "FILE_NOT_FOUND";
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode(errorCode)
                            .message(e.getMessage())
                            .build());
        } catch (UnsupportedFileTypeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("UNSUPPORTED_FILE_TYPE")
                            .message(e.getMessage())
                            .build());
        } catch (FileSizeExceededException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("FILE_SIZE_EXCEEDED")
                            .message(e.getMessage())
                            .build());
        } catch (HashGenerationException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("HASH_GENERATION_FAILED")
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("FILE_UPLOAD_FAILED")
                            .message("파일 업로드에 실패했습니다.")
                            .build());
        }
    }

    @Operation(summary = "업로드 취소", description = "분석 시작 전 업로드된 증거를 취소하고 DB·S3에서 삭제합니다.")
    @DeleteMapping("/{evidenceId}")
    public ResponseEntity<?> cancelUpload(@PathVariable Long evidenceId) {
        try {
            evidenceCancelService.cancelUpload(
                    authUserResolver.requireCurrentUser(),
                    evidenceId
            );
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("EVIDENCE_NOT_FOUND")
                            .message(e.getMessage())
                            .build());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("ANALYSIS_ALREADY_STARTED")
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("EVIDENCE_CANCEL_FAILED")
                            .message("업로드 취소에 실패했습니다.")
                            .build());
        }
    }

    @Operation(summary = "증거 상세", description = "증거 ID로 상세 분석·메타데이터·무결성 정보를 조회합니다.")
    @GetMapping("/{evidenceId}/detail")
    public ResponseEntity<?> getEvidenceDetail(@PathVariable Long evidenceId) {
        try {
            EvidenceDetailResponse response = evidenceDetailService.getEvidenceDetail(
                    authUserResolver.requireCurrentUser(),
                    evidenceId
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("EVIDENCE_NOT_FOUND")
                            .message(e.getMessage())
                            .build());
        }
    }

    @Operation(summary = "분석 시작", description = "업로드된 증거에 대한 분석을 요청합니다. 사건명은 필수입니다.")
    @PostMapping("/analyze")
    public ResponseEntity<?> startAnalysis(@RequestBody StartAnalysisRequest request) {
        try {
            StartAnalysisResponse response = analysisService.startAnalysis(
                    authUserResolver.requireCurrentUser(),
                    request.getEvidenceIds(),
                    request.getCaseName()
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("INVALID_REQUEST")
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.builder()
                            .success(false)
                            .errorCode("ANALYSIS_START_FAILED")
                            .message("분석 요청에 실패했습니다.")
                            .build());
        }
    }
}
