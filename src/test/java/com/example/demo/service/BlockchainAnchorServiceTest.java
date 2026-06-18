package com.example.demo.service;

import com.example.demo.blockchain.BlockchainAnchorClient;
import com.example.demo.blockchain.BlockchainAnchorResult;
import com.example.demo.config.BlockchainAnchorProperties;
import com.example.demo.domain.BlockchainAnchor;
import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.BlockchainAnchorStatus;
import com.example.demo.domain.enums.BlockchainAnchorType;
import com.example.demo.domain.enums.FileType;
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
        when(anchorRepository.findTopByEvidenceIdAndAnchorTypeOrderByCreatedAtDesc(any(), eq(BlockchainAnchorType.EVIDENCE_HASH)))
                .thenReturn(Optional.empty());
        when(anchorClient.anchor(eq("abc"), eq(BlockchainAnchorType.EVIDENCE_HASH)))
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
        LocalDate batchDate = LocalDate.of(2026, 6, 16);
        when(anchorRepository.existsByMerkleBatchDateAndAnchorType(batchDate, BlockchainAnchorType.MERKLE_ROOT))
                .thenReturn(false);

        CustodyLog log = new CustodyLog();
        log.setCurrentLogHash("1111111111111111111111111111111111111111111111111111111111111111");
        log.setCreatedAt(batchDate.atTime(10, 0));
        when(custodyLogRepository.findAll()).thenReturn(List.of(log));
        when(anchorClient.anchor(any(), eq(BlockchainAnchorType.MERKLE_ROOT)))
                .thenReturn(new BlockchainAnchorResult("0xmerkle", 2L, true, null));
        when(anchorRepository.save(any(BlockchainAnchor.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BlockchainAnchor result = blockchainAnchorService.anchorDailyMerkleRoot(batchDate);

        assertThat(result.getStatus()).isEqualTo(BlockchainAnchorStatus.ANCHORED);
        assertThat(result.getMerkleLeafCount()).isEqualTo(1);
        assertThat(result.getSubjectHash()).isEqualTo(log.getCurrentLogHash());
    }
}
