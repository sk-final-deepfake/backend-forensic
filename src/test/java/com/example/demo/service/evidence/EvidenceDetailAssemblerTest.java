package com.example.demo.service.evidence;

import com.example.demo.config.EvidenceManifestProperties;
import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceManifest;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.SignatureStatus;
import com.example.demo.dto.IntegrityCheckItem;
import com.example.demo.dto.IntegrityVerifyResponse;
import com.example.demo.dto.detail.RecoveryScoreDto;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.analysis.AnalysisInfoAssembler;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.example.demo.service.custody.CustodyChainVerifier;
import com.example.demo.service.manifest.EvidenceManifestService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceDetailAssemblerTest {

    @Mock
    private EvidenceRepository evidenceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustodyChainVerifier custodyChainVerifier;
    @Mock
    private BlockchainAnchorService blockchainAnchorService;
    @Mock
    private EvidenceManifestService evidenceManifestService;
    @Mock
    private EvidenceManifestProperties evidenceManifestProperties;
    @Mock
    private AnalysisInfoAssembler analysisInfoAssembler;
    @Mock
    private CaseEvidencePresentationService caseEvidencePresentationService;
    @Mock
    private EvidenceMediaUrlService evidenceMediaUrlService;

    private EvidenceDetailAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new EvidenceDetailAssembler(
                evidenceRepository,
                userRepository,
                custodyChainVerifier,
                blockchainAnchorService,
                evidenceManifestService,
                evidenceManifestProperties,
                analysisInfoAssembler,
                caseEvidencePresentationService,
                evidenceMediaUrlService
        );
    }

    @Test
    void assemble_reusesVerificationForChainAndSignature() {
        Evidence evidence = mock(Evidence.class);
        when(evidence.getEvidenceId()).thenReturn(7L);
        when(evidence.getUploaderId()).thenReturn(1L);
        when(evidence.getCaseName()).thenReturn("case-a");
        when(evidence.getFileName()).thenReturn("video.mp4");
        when(evidence.getFileType()).thenReturn(FileType.VIDEO);
        when(evidence.getUploadedAt()).thenReturn(LocalDateTime.now());
        when(evidence.getHashAlgorithm()).thenReturn("SHA-256");
        when(evidence.getOriginalHashValue()).thenReturn("a".repeat(64));

        EvidenceManifest manifest = mock(EvidenceManifest.class);
        when(manifest.getSignatureStatus()).thenReturn(SignatureStatus.SIGNED);

        IntegrityVerifyResponse verification = IntegrityVerifyResponse.builder()
                .checks(List.of(
                        IntegrityCheckItem.builder().checkType("COC_CHAIN").valid(false).build(),
                        IntegrityCheckItem.builder().checkType("SIGNATURE").valid(true).build()
                ))
                .build();
        RecoveryScoreDto recovery = RecoveryScoreDto.builder()
                .recoveryScore(100)
                .dataLossPercent(0)
                .grade("A")
                .build();

        when(evidenceRepository.findByUploaderIdAndCaseKey(1L, "case-a")).thenReturn(List.of(evidence));
        when(caseEvidencePresentationService.resolveDisplayLabel(any(), any())).thenReturn("증거 1");
        when(caseEvidencePresentationService.lifecycleStatusName(evidence)).thenReturn("ACTIVE");
        when(caseEvidencePresentationService.roleName(evidence)).thenReturn("PRIMARY");
        when(evidenceMediaUrlService.resolve(evidence))
                .thenReturn(new EvidenceMediaUrlService.MediaUrls(null, null, null));
        when(blockchainAnchorService.getEvidenceBlockchainInfo(evidence)).thenReturn(null);
        when(analysisInfoAssembler.assemble(null, null, List.of())).thenReturn(null);

        var response = assembler.assemble(
                evidence,
                verification,
                null,
                null,
                null,
                List.of(),
                List.of(),
                manifest,
                recovery
        );

        assertThat(response.getIntegrityInfo().isChainValid()).isFalse();
        assertThat(response.getIntegrityInfo().getVerificationStatus()).isEqualTo("CORRUPTED");
        assertThat(response.getSignatureInfo().getSignatureValid()).isTrue();
        verify(custodyChainVerifier, never()).isEvidenceChainValid(7L);
        verify(evidenceManifestService, never()).isSignatureValid(manifest);
    }
}
