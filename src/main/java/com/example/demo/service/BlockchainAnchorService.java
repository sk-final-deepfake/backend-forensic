package com.example.demo.service;

import com.example.demo.blockchain.BlockchainAnchorClient;
import com.example.demo.blockchain.BlockchainAnchorResult;
import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.Report;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.dto.BlockchainAnchorRecordDto;
import com.example.demo.dto.BlockchainAnchorStatusResponse;
import com.example.demo.dto.detail.BlockchainInfoDto;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.util.ApiDateTimeFormatter;
import com.example.demo.util.MerkleTreeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainAnchorService {

    private final BlockchainAnchorProperties properties;
    private final BlockchainAnchorClient anchorClient;
    private final BlockchainAnchorRepository anchorRepository;
    private final CustodyLogRepository custodyLogRepository;
    private final EvidenceRepository evidenceRepository;
    private final HashService hashService;
    private final NotificationService notificationService;

    @Transactional
    public BlockchainAnchor anchorEvidenceHash(Evidence evidence, Long userId) {
        if (!properties.isEnabled() || evidence == null) {
            return null;
        }

        return anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                        evidence.getEvidenceId(),
                        BlockchainAnchorType.EVIDENCE_HASH
                )
                .filter(existing -> existing.getStatus() == BlockchainAnchorStatus.ANCHORED)
                .orElseGet(() -> executeAnchor(
                        BlockchainAnchorType.EVIDENCE_HASH,
                        evidence.getOriginalHashValue(),
                        evidence.getEvidenceId(),
                        null,
                        userId,
                        null,
                        null
                ));
    }

    @Transactional
    public BlockchainAnchor anchorReportHash(Report report, Long userId) {
        if (!properties.isEnabled() || report == null || report.getReportHash() == null) {
            return null;
        }

        return anchorRepository.findTopByReportIdAndAnchorTypeOrderByCreatedAtDesc(
                        report.getReportId(),
                        BlockchainAnchorType.REPORT_HASH
                )
                .filter(existing -> existing.getStatus() == BlockchainAnchorStatus.ANCHORED)
                .orElseGet(() -> executeAnchor(
                        BlockchainAnchorType.REPORT_HASH,
                        report.getReportHash(),
                        report.getEvidenceId(),
                        report.getReportId(),
                        userId,
                        null,
                        null
                ));
    }

    @Transactional
    public BlockchainAnchor anchorDailyMerkleRoot(LocalDate batchDate) {
        if (!properties.isEnabled()) {
            return null;
        }

        LocalDate targetDate = batchDate == null ? LocalDate.now().minusDays(1) : batchDate;
        if (anchorRepository.existsByMerkleBatchDateAndAnchorType(targetDate, BlockchainAnchorType.MERKLE_ROOT)) {
            log.info("Merkle root already anchored for batchDate={}", targetDate);
            return anchorRepository.findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(
                    targetDate,
                    BlockchainAnchorType.MERKLE_ROOT
            ).orElse(null);
        }

        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = targetDate.plusDays(1).atStartOfDay();
        List<String> leafHashes = custodyLogRepository.findAll().stream()
                .filter(log -> !log.getCreatedAt().isBefore(start) && log.getCreatedAt().isBefore(end))
                .map(CustodyLog::getCurrentLogHash)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (leafHashes.isEmpty()) {
            log.info("Skipping merkle anchor for batchDate={} — no custody logs", targetDate);
            return null;
        }

        String merkleRoot = MerkleTreeUtil.computeRoot(leafHashes, hashService);
        return executeAnchor(
                BlockchainAnchorType.MERKLE_ROOT,
                merkleRoot,
                null,
                null,
                null,
                targetDate,
                leafHashes.size()
        );
    }

    @Transactional(readOnly = true)
    public BlockchainAnchorStatusResponse getEvidenceAnchorStatus(User user, Long evidenceId) {
        requireOwnedEvidence(user, evidenceId);

        BlockchainAnchorRecordDto evidenceAnchor = anchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(evidenceId, BlockchainAnchorType.EVIDENCE_HASH)
                .map(this::toDto)
                .orElse(null);

        List<BlockchainAnchorRecordDto> reportAnchors = anchorRepository
                .findByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(evidenceId, BlockchainAnchorType.REPORT_HASH)
                .stream()
                .map(this::toDto)
                .toList();

        BlockchainAnchorRecordDto latestMerkle = anchorRepository
                .findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(
                        LocalDate.now().minusDays(1),
                        BlockchainAnchorType.MERKLE_ROOT
                )
                .map(this::toDto)
                .orElseGet(() -> anchorRepository.findAll().stream()
                        .filter(anchor -> anchor.getAnchorType() == BlockchainAnchorType.MERKLE_ROOT)
                        .filter(anchor -> anchor.getStatus() == BlockchainAnchorStatus.ANCHORED)
                        .max((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                        .map(this::toDto)
                        .orElse(null));

        return BlockchainAnchorStatusResponse.builder()
                .evidenceId(evidenceId)
                .evidenceHashAnchor(evidenceAnchor)
                .reportHashAnchors(reportAnchors)
                .latestMerkleRootAnchor(latestMerkle)
                .build();
    }

    @Transactional(readOnly = true)
    public BlockchainInfoDto getEvidenceBlockchainInfo(Long evidenceId) {
        return anchorRepository
                .findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(evidenceId, BlockchainAnchorType.EVIDENCE_HASH)
                .map(this::toInfoDto)
                .orElse(BlockchainInfoDto.builder()
                        .status("NOT_ANCHORED")
                        .anchorType(BlockchainAnchorType.EVIDENCE_HASH.name())
                        .build());
    }

    private BlockchainAnchor executeAnchor(
            BlockchainAnchorType anchorType,
            String subjectHash,
            Long evidenceId,
            Long reportId,
            Long userId,
            LocalDate merkleBatchDate,
            Integer merkleLeafCount
    ) {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setAnchorType(anchorType);
        anchor.setSubjectHash(subjectHash);
        anchor.setEvidenceId(evidenceId);
        anchor.setReportId(reportId);
        anchor.setCreatedBy(userId);
        anchor.setMerkleBatchDate(merkleBatchDate);
        anchor.setMerkleLeafCount(merkleLeafCount);
        anchor.setStatus(BlockchainAnchorStatus.PENDING);
        anchor.setNetwork(properties.getNetwork());
        anchor.setCreatedAt(LocalDateTime.now());
        anchorRepository.save(anchor);

        BlockchainAnchorResult result = anchorClient.anchor(subjectHash, anchorType);
        if (result.success()) {
            anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
            anchor.setTransactionHash(result.transactionHash());
            anchor.setBlockNumber(result.blockNumber());
            anchor.setAnchoredAt(LocalDateTime.now());
            notifyIfNeeded(anchor, userId);
        } else {
            anchor.setStatus(BlockchainAnchorStatus.FAILED);
            anchor.setErrorMessage(result.errorMessage());
            log.warn("Blockchain anchor failed type={} subjectHash={} error={}",
                    anchorType, subjectHash, result.errorMessage());
        }

        return anchorRepository.save(anchor);
    }

    private void notifyIfNeeded(BlockchainAnchor anchor, Long userId) {
        if (userId == null || anchor.getAnchorType() == BlockchainAnchorType.MERKLE_ROOT) {
            return;
        }
        notificationService.notifyBlockchainAnchored(
                userId,
                anchor.getEvidenceId(),
                anchor.getAnchorType(),
                anchor.getTransactionHash()
        );
    }

    private Evidence requireOwnedEvidence(User user, Long evidenceId) {
        return evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, user.getUserId())
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));
    }

    private BlockchainAnchorRecordDto toDto(BlockchainAnchor anchor) {
        return BlockchainAnchorRecordDto.builder()
                .anchorId(anchor.getAnchorId())
                .anchorType(anchor.getAnchorType())
                .status(anchor.getStatus())
                .subjectHash(anchor.getSubjectHash())
                .transactionHash(anchor.getTransactionHash())
                .blockNumber(anchor.getBlockNumber())
                .network(anchor.getNetwork())
                .anchoredAt(ApiDateTimeFormatter.formatUtc(anchor.getAnchoredAt()))
                .evidenceId(anchor.getEvidenceId())
                .reportId(anchor.getReportId())
                .merkleBatchDate(anchor.getMerkleBatchDate() == null
                        ? null
                        : anchor.getMerkleBatchDate().toString())
                .merkleLeafCount(anchor.getMerkleLeafCount())
                .message(resolveMessage(anchor))
                .build();
    }

    private BlockchainInfoDto toInfoDto(BlockchainAnchor anchor) {
        return BlockchainInfoDto.builder()
                .status(anchor.getStatus().name())
                .anchorType(anchor.getAnchorType().name())
                .subjectHash(anchor.getSubjectHash())
                .transactionHash(anchor.getTransactionHash())
                .anchoredAt(ApiDateTimeFormatter.formatUtc(anchor.getAnchoredAt()))
                .network(anchor.getNetwork())
                .build();
    }

    private String resolveMessage(BlockchainAnchor anchor) {
        return switch (anchor.getStatus()) {
            case ANCHORED -> "블록체인 앵커링이 완료되었습니다.";
            case PENDING -> "블록체인 앵커링이 진행 중입니다.";
            case FAILED -> anchor.getErrorMessage() == null
                    ? "블록체인 앵커링에 실패했습니다."
                    : anchor.getErrorMessage();
        };
    }
}
