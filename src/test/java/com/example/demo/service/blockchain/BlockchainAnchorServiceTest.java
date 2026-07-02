package com.example.demo.service.blockchain;

import com.example.demo.service.evidence.HashService;
import com.example.demo.service.notification.NotificationService;
import com.example.demo.service.blockchain.client.BlockchainAnchorClient;
import com.example.demo.service.blockchain.client.BlockchainAnchorRequest;
import com.example.demo.service.blockchain.client.BlockchainAnchorResult;
import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.FileType;
import com.example.demo.dto.detail.BlockchainInfoDto;
import com.example.demo.repository.BlockchainAnchorRepository;
import com.example.demo.repository.CustodyLogRepository;
import com.example.demo.repository.EvidenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainAnchorServiceTest {

    @Mock
    private BlockchainAnchorProperties properties;

    @Mock
    private BlockchainAnchorClient anchorClient;

    @Mock
    private BlockchainAnchorRepository anchorRepository;

    @Mock
    private CustodyLogRepository custodyLogRepository;

    @Mock
    private EvidenceRepository evidenceRepository;

    @Mock
    private HashService hashService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BlockchainAnchorService blockchainAnchorService;

    @Test
    void anchorEvidenceHash_persistsAnchoredRecord() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(properties.getClientId()).thenReturn("forenshield-be");
        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(any(), eq(BlockchainAnchorType.EVIDENCE_HASH)))
                .thenReturn(Optional.empty());
        when(anchorClient.anchor(any(BlockchainAnchorRequest.class)))
                .thenReturn(new BlockchainAnchorResult("0xtx", 1L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> {
            BlockchainAnchor anchor = invocation.getArgument(0);
            anchor.setAnchorId(10L);
            return anchor;
        });

        Evidence evidence = Evidence.builder()
                .uploaderId(5L)
                .fileName("sample.mp4")
                .fileType(FileType.VIDEO)
                .mimeType("video/mp4")
                .fileSize(1L)
                .hashAlgorithm(Evidence.HASH_ALGORITHM_SHA256)
                .originalHashValue("abc")
                .originalStoragePath("path")
                .uploadedAt(LocalDateTime.now())
                .build();

        BlockchainAnchor result = blockchainAnchorService.anchorEvidenceHash(evidence, 5L);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getTransactionHash()).isEqualTo("0xtx");
        verify(notificationService).notifyBlockchainAnchored(eq(5L), isNull(), eq(BlockchainAnchorType.EVIDENCE_HASH), eq("0xtx"));
    }

    @Test
    void anchorDailyMerkleRoot_skipsWhenBatchAlreadyExists() {
        when(properties.isEnabled()).thenReturn(true);
        LocalDate batchDate = LocalDate.of(2026, 6, 16);
        when(anchorRepository.existsByMerkleBatchDateAndAnchorType(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(true);
        when(anchorRepository.findTopByMerkleBatchDateAndAnchorTypeOrderByCreatedAtDesc(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(Optional.of(new BlockchainAnchor()));

        BlockchainAnchor result = blockchainAnchorService.anchorDailyMerkleRoot(batchDate);

        assertThat(result).isNotNull();
    }

    @Test
    void anchorDailyMerkleRoot_buildsRootFromCustodyLogs() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getNetwork()).thenReturn("local-simulated");
        when(properties.getClientId()).thenReturn("forenshield-be");
        LocalDate batchDate = LocalDate.of(2026, 6, 16);
        when(anchorRepository.existsByMerkleBatchDateAndAnchorType(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(false);

        CustodyLog log = new CustodyLog();
        log.setCurrentLogHash("1111111111111111111111111111111111111111111111111111111111111111");
        log.setCreatedAt(batchDate.atTime(10, 0));
        when(custodyLogRepository.findAll()).thenReturn(List.of(log));
        when(anchorClient.anchor(any(BlockchainAnchorRequest.class)))
                .thenReturn(new BlockchainAnchorResult("0xmerkle", 2L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlockchainAnchor result = blockchainAnchorService.anchorDailyMerkleRoot(batchDate);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getMerkleLeafCount()).isEqualTo(1);
        assertThat(result.getSubjectHash()).isEqualTo(log.getCurrentLogHash());
    }

    @Test
    void getEvidenceBlockchainInfo_reportsHashIntegrityAndExplorerUrl() {
        when(properties.getExplorerUrlTemplate()).thenReturn("https://explorer.test/tx/{txHash}");

        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(7L);
        when(evidence.getOriginalHashValue()).thenReturn("abc");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash("abc");
        anchor.setTransactionHash("0xabc123");
        anchor.setNetwork("local-simulated");
        anchor.setAnchoredAt(LocalDateTime.now());

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                7L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        BlockchainInfoDto info = blockchainAnchorService.getEvidenceBlockchainInfo(evidence);

        assertThat(info.getHashValid()).isTrue();
        assertThat(info.getVerificationMessage()).contains("일치");
        assertThat(info.getTransactionExplorerUrl()).isEqualTo("https://explorer.test/tx/0xabc123");
    }

    @Test
    void getEvidenceBlockchainInfo_reportsMismatchWhenHashesDiffer() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(8L);
        when(evidence.getOriginalHashValue()).thenReturn("current-hash");

        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setAnchorType(BlockchainAnchorType.EVIDENCE_HASH);
        anchor.setSubjectHash("anchored-hash");

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                8L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        BlockchainInfoDto info = blockchainAnchorService.getEvidenceBlockchainInfo(evidence);

        assertThat(info.getHashValid()).isFalse();
        assertThat(info.getVerificationMessage()).contains("일치하지 않습니다");
    }

    @Test
    void findAnchoredEvidenceSubjectHash_returnsLatestAnchoredHash() {
        BlockchainAnchor anchor = new BlockchainAnchor();
        anchor.setStatus(BlockchainAnchorStatus.ANCHORED);
        anchor.setSubjectHash("registered-hash");

        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(
                9L,
                BlockchainAnchorType.EVIDENCE_HASH
        )).thenReturn(Optional.of(anchor));

        assertThat(blockchainAnchorService.findAnchoredEvidenceSubjectHash(9L))
                .contains("registered-hash");
    }
}
