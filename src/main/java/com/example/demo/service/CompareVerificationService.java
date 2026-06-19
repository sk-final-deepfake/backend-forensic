package com.example.demo.service;

import com.example.demo.domain.CompareVerification;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceMetadata;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.CompareItemResult;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.dto.MediaMetadata;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.dto.compare.CompareFileInfoDto;
import com.example.demo.dto.compare.CompareOriginalPageResponse;
import com.example.demo.dto.compare.CompareResultResponse;
import com.example.demo.dto.compare.CompareSummaryDto;
import com.example.demo.dto.compare.CompareVerifyResponse;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.CompareVerificationRepository;
import com.example.demo.repository.EvidenceMetadataRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.FfprobeCompareHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompareVerificationService {

    private final EvidenceRepository evidenceRepository;
    private final EvidenceMetadataRepository evidenceMetadataRepository;
    private final CompareVerificationRepository compareVerificationRepository;
    private final FileValidationService fileValidationService;
    private final HashService hashService;
    private final MediaService mediaService;
    private final BlockchainAnchorService blockchainAnchorService;
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public CompareVerifyResponse verify(User user, Long evidenceId, MultipartFile candidateFile) {
        return verify(user, evidenceId, candidateFile, null);
    }

    public CompareVerifyResponse verify(User user, Long evidenceId, MultipartFile candidateFile, String requestId) {
        Evidence original = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "원본 증거를 찾을 수 없습니다."));

        fileValidationService.validate(candidateFile);
        EvidenceMetadata originalMetadata = evidenceMetadataRepository.findByEvidenceId(evidenceId).orElse(null);

        Path tempFile = saveTempCandidate(candidateFile);
        try {
            String candidateHash = hashService.generateSha256(tempFile);
            long candidateSize = candidateFile.getSize();
            Optional<FfprobeCompareHelper.ProbeSnapshot> candidateProbe = extractProbe(tempFile);

            List<CompareItemDto> items = buildComparisonItems(original, originalMetadata, candidateHash, candidateSize, candidateProbe);
            CompareSummaryDto summary = summarize(items);
            CompareVerdict verdict = determineVerdict(items, candidateHash, original.getOriginalHashValue());

            CompareVerification saved = persistVerification(
                    user.getUserId(),
                    evidenceId,
                    candidateFile.getOriginalFilename(),
                    candidateHash,
                    candidateSize,
                    verdict,
                    summary,
                    items
            );

            return toVerifyResponse(saved, items, summary);
        } finally {
            deleteQuietly(tempFile);
        }
    }

    @Transactional(readOnly = true)
    public CompareResultResponse getResult(User user, Long compareId) {
        CompareVerification verification = compareVerificationRepository
                .findByCompareIdAndUserId(compareId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "COMPARE_NOT_FOUND", "비교 검증 결과를 찾을 수 없습니다."));

        List<CompareItemDto> items = deserializeItems(verification.getResultJson());
        CompareSummaryDto summary = CompareSummaryDto.builder()
                .matchCount(verification.getMatchCount())
                .mismatchCount(verification.getMismatchCount())
                .skippedCount(verification.getSkippedCount())
                .verdictLabel(verdictLabel(verification.getVerdict()))
                .build();

        return CompareResultResponse.builder()
                .compareId(verification.getCompareId())
                .originalEvidenceId(verification.getOriginalEvidenceId())
                .candidateFileName(verification.getCandidateFileName())
                .verdict(verification.getVerdict())
                .summary(summary)
                .items(items)
                .createdAt(ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()))
                .build();
    }

    @Transactional(readOnly = true)
    public CompareVerification requireOwnedVerification(User user, Long compareId) {
        return compareVerificationRepository.findByCompareIdAndUserId(compareId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "COMPARE_NOT_FOUND", "비교 검증 결과를 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public CompareOriginalPageResponse listOriginals(User user, String search, int page, int size) {
        Page<Evidence> result = evidenceRepository.findCompareOriginals(
                user.getUserId(),
                search,
                PageRequest.of(page, size)
        );

        return CompareOriginalPageResponse.builder()
                .content(result.getContent().stream().map(this::toOriginalFileInfo).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public CompareFileInfoDto getOriginalFileInfo(User user, Long evidenceId) {
        Evidence original = evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "원본 증거를 찾을 수 없습니다."));
        return toOriginalFileInfo(original);
    }

    @Transactional(readOnly = true)
    public CompareFileInfoDto getCandidateFileInfo(User user, Long compareId) {
        CompareVerification verification = requireOwnedVerification(user, compareId);
        return CompareFileInfoDto.builder()
                .compareId(verification.getCompareId())
                .fileName(verification.getCandidateFileName())
                .fileSize(verification.getCandidateFileSize())
                .sha256(verification.getCandidateHash())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(verification.getCreatedAt()))
                .build();
    }

    private CompareFileInfoDto toOriginalFileInfo(Evidence evidence) {
        return CompareFileInfoDto.builder()
                .evidenceId(evidence.getEvidenceId())
                .fileName(evidence.getFileName())
                .fileSize(evidence.getFileSize())
                .sha256(evidence.getOriginalHashValue())
                .caseName(evidence.getCaseName())
                .caseNumber(evidence.getCaseNumber())
                .fileType(evidence.getFileType() != null ? evidence.getFileType().name() : null)
                .mimeType(evidence.getMimeType())
                .uploadedAt(ApiDateTimeFormatter.formatUtc(evidence.getUploadedAt()))
                .build();
    }

    private CompareVerification persistVerification(
            Long userId,
            Long evidenceId,
            String candidateFileName,
            String candidateHash,
            long candidateSize,
            CompareVerdict verdict,
            CompareSummaryDto summary,
            List<CompareItemDto> items
    ) {
        CompareVerification verification = new CompareVerification();
        verification.setUserId(userId);
        verification.setOriginalEvidenceId(evidenceId);
        verification.setCandidateFileName(candidateFileName);
        verification.setCandidateHash(candidateHash);
        verification.setCandidateFileSize(candidateSize);
        verification.setVerdict(verdict);
        verification.setMatchCount(summary.getMatchCount());
        verification.setMismatchCount(summary.getMismatchCount());
        verification.setSkippedCount(summary.getSkippedCount());
        verification.setResultJson(serializeItems(items));
        verification.setCreatedAt(LocalDateTime.now());
        return compareVerificationRepository.save(verification);
    }

    private List<CompareItemDto> buildComparisonItems(
            Evidence original,
            EvidenceMetadata originalMetadata,
            String candidateHash,
            long candidateSize,
            Optional<FfprobeCompareHelper.ProbeSnapshot> candidateProbe
    ) {
        List<CompareItemDto> items = new ArrayList<>();

        items.add(compareValues(
                "SHA256",
                "SHA-256 해시",
                original.getOriginalHashValue(),
                candidateHash
        ));

        items.add(compareValues(
                "FILE_SIZE",
                "파일 크기",
                String.valueOf(original.getFileSize()),
                String.valueOf(candidateSize)
        ));

        Optional<FfprobeCompareHelper.ProbeSnapshot> originalProbe = originalMetadata == null
                ? Optional.empty()
                : FfprobeCompareHelper.fromFfprobeJson(originalMetadata.getFfprobeJson(), objectMapper);

        items.add(compareOptional(
                "DURATION",
                "영상 길이(초)",
                originalMetadata != null && originalMetadata.getDurationSec() != null
                        ? String.valueOf(originalMetadata.getDurationSec())
                        : originalProbe.map(p -> p.getDurationSec() == null ? null : String.valueOf(p.getDurationSec())).orElse(null),
                candidateProbe.map(p -> p.getDurationSec() == null ? null : String.valueOf(p.getDurationSec())).orElse(null)
        ));

        String originalCodec = originalMetadata != null && originalMetadata.getCodec() != null
                ? originalMetadata.getCodec()
                : originalProbe.map(p -> FfprobeCompareHelper.formatCodec(p.getVideoCodec(), p.getAudioCodec())).orElse(null);
        String candidateCodec = candidateProbe
                .map(p -> FfprobeCompareHelper.formatCodec(p.getVideoCodec(), p.getAudioCodec()))
                .orElse(null);
        items.add(compareOptional("CODEC", "코덱 정보", originalCodec, candidateCodec));

        String originalTimestamp = originalMetadata != null && originalMetadata.getCapturedAt() != null
                ? ApiDateTimeFormatter.formatUtc(originalMetadata.getCapturedAt())
                : originalProbe.map(FfprobeCompareHelper.ProbeSnapshot::getTimestamp).orElse(null);
        String candidateTimestamp = candidateProbe.map(FfprobeCompareHelper.ProbeSnapshot::getTimestamp).orElse(null);
        items.add(compareOptional("TIMESTAMP", "메타데이터 타임스탬프", originalTimestamp, candidateTimestamp));

        items.add(compareOptional(
                "GOP",
                "GOP 구조",
                originalProbe.map(FfprobeCompareHelper.ProbeSnapshot::getGopFingerprint).orElse(null),
                candidateProbe.map(FfprobeCompareHelper.ProbeSnapshot::getGopFingerprint).orElse(null)
        ));

        items.add(compareOptional(
                "STREAM_CHECKSUM",
                "스트림 식별값",
                originalProbe.map(FfprobeCompareHelper.ProbeSnapshot::getStreamFingerprint).orElse(null),
                candidateProbe.map(FfprobeCompareHelper.ProbeSnapshot::getStreamFingerprint).orElse(null)
        ));

        items.add(compareOptional(
                "BLOCKCHAIN_HASH",
                "등록 해시 대조",
                blockchainAnchorService.findAnchoredEvidenceSubjectHash(original.getEvidenceId()).orElse(null),
                candidateHash
        ));

        return items;
    }

    private CompareItemDto compareValues(String key, String label, String originalValue, String candidateValue) {
        CompareItemResult result = originalValue != null && originalValue.equals(candidateValue)
                ? CompareItemResult.MATCH
                : CompareItemResult.MISMATCH;
        return CompareItemDto.builder()
                .itemKey(key)
                .label(label)
                .originalValue(originalValue)
                .candidateValue(candidateValue)
                .result(result)
                .build();
    }

    private CompareItemDto compareOptional(String key, String label, String originalValue, String candidateValue) {
        if (originalValue == null || candidateValue == null) {
            return CompareItemDto.builder()
                    .itemKey(key)
                    .label(label)
                    .originalValue(originalValue)
                    .candidateValue(candidateValue)
                    .result(CompareItemResult.SKIPPED)
                    .build();
        }
        CompareItemResult result = originalValue.equals(candidateValue)
                ? CompareItemResult.MATCH
                : CompareItemResult.MISMATCH;
        return CompareItemDto.builder()
                .itemKey(key)
                .label(label)
                .originalValue(originalValue)
                .candidateValue(candidateValue)
                .result(result)
                .build();
    }

    private CompareSummaryDto summarize(List<CompareItemDto> items) {
        int match = 0;
        int mismatch = 0;
        int skipped = 0;
        for (CompareItemDto item : items) {
            switch (item.getResult()) {
                case MATCH -> match++;
                case MISMATCH -> mismatch++;
                case SKIPPED -> skipped++;
            }
        }
        return CompareSummaryDto.builder()
                .matchCount(match)
                .mismatchCount(mismatch)
                .skippedCount(skipped)
                .build();
    }

    private CompareVerdict determineVerdict(List<CompareItemDto> items, String candidateHash, String originalHash) {
        if (originalHash != null && originalHash.equals(candidateHash)) {
            return CompareVerdict.ORIGINAL_MATCH;
        }
        boolean hasMismatch = items.stream().anyMatch(item -> item.getResult() == CompareItemResult.MISMATCH);
        if (hasMismatch) {
            return CompareVerdict.TAMPERED;
        }
        return CompareVerdict.INCONCLUSIVE;
    }

    private CompareVerifyResponse toVerifyResponse(
            CompareVerification saved,
            List<CompareItemDto> items,
            CompareSummaryDto summary
    ) {
        CompareSummaryDto summaryWithLabel = CompareSummaryDto.builder()
                .matchCount(summary.getMatchCount())
                .mismatchCount(summary.getMismatchCount())
                .skippedCount(summary.getSkippedCount())
                .verdictLabel(verdictLabel(saved.getVerdict()))
                .build();

        return CompareVerifyResponse.builder()
                .compareId(saved.getCompareId())
                .originalEvidenceId(saved.getOriginalEvidenceId())
                .candidateFileName(saved.getCandidateFileName())
                .verdict(saved.getVerdict())
                .summary(summaryWithLabel)
                .items(items)
                .createdAt(ApiDateTimeFormatter.formatUtc(saved.getCreatedAt()))
                .build();
    }

    private String verdictLabel(CompareVerdict verdict) {
        return switch (verdict) {
            case ORIGINAL_MATCH -> "원본 일치";
            case TAMPERED -> "위변조 의심";
            case INCONCLUSIVE -> "판정 불가";
        };
    }

    private Optional<FfprobeCompareHelper.ProbeSnapshot> extractProbe(Path tempFile) {
        try {
            MediaMetadata metadata = mediaService.extractMetadata(tempFile);
            if (metadata.getFfprobeJson() != null) {
                return FfprobeCompareHelper.fromFfprobeJson(metadata.getFfprobeJson(), objectMapper);
            }
        } catch (Exception ignored) {
            // ffprobe unavailable in test/dev — metadata items will be SKIPPED
        }
        return Optional.empty();
    }

    private Path saveTempCandidate(MultipartFile candidateFile) {
        try {
            Path tempDir = Paths.get(uploadDir, "compare-temp");
            Files.createDirectories(tempDir);
            Path tempFile = tempDir.resolve(UUID.randomUUID() + "-" + candidateFile.getOriginalFilename());
            candidateFile.transferTo(tempFile);
            return tempFile;
        } catch (IOException ex) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "COMPARE_FILE_ERROR", "비교 대상 파일 처리에 실패했습니다.");
        }
    }

    private void deleteQuietly(Path tempFile) {
        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
            // best effort cleanup
        }
    }

    private String serializeItems(List<CompareItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("비교 결과 직렬화에 실패했습니다.", ex);
        }
    }

    private List<CompareItemDto> deserializeItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("비교 결과 역직렬화에 실패했습니다.", ex);
        }
    }

    /**
     * FE compare flow sends a client-side cancellation token.
     * Verification runs synchronously, so there is no server-side job to abort.
     */
    public void cancel(String requestId) {
        // acknowledged no-op for API compatibility
    }
}
