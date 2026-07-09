package com.example.demo.service.evidence.hls;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.User;
import com.example.demo.domain.enums.FileType;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import com.example.demo.repository.EvidenceRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.support.EvidenceTestFixtures;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration"
})
@ActiveProfiles("test")
class EvidenceHlsLookupServiceTest {

    @Autowired
    private EvidenceHlsLookupService evidenceHlsLookupService;

    @Autowired
    private EvidenceHlsRepository evidenceHlsRepository;

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long evidenceId1;
    private Long evidenceId2;

    @BeforeEach
    void setUp() {
        evidenceHlsRepository.deleteAll();
        evidenceRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(EvidenceTestFixtures.defaultApprovedUser(passwordEncoder));
        LocalDateTime now = LocalDateTime.now();

        Evidence first = evidenceRepository.save(
                EvidenceTestFixtures.videoEvidence(user.getUserId(), "a.mp4", 'a')
        );
        Evidence second = evidenceRepository.save(
                EvidenceTestFixtures.videoEvidence(user.getUserId(), "b.mp4", 'b')
        );
        evidenceId1 = first.getEvidenceId();
        evidenceId2 = second.getEvidenceId();

        evidenceHlsRepository.save(EvidenceHls.createPending(evidenceId1, now));
        EvidenceHls ready = EvidenceHls.createPending(evidenceId2, now);
        ready.markReady("hls/" + evidenceId2 + "/", new byte[] {1, 2, 3, 4}, now);
        evidenceHlsRepository.save(ready);
    }

    @Test
    void findByEvidenceIds_returnsMapInSingleQuery() {
        Map<Long, EvidenceHls> map = evidenceHlsLookupService.findByEvidenceIds(
                List.of(evidenceId1, evidenceId2)
        );

        assertThat(map).hasSize(2);
        assertThat(map.get(evidenceId1).getHlsStatus()).isEqualTo(HlsStatus.PENDING);
        assertThat(map.get(evidenceId2).getHlsStatus()).isEqualTo(HlsStatus.READY);
    }

    @Test
    void resolveStatus_videoWithoutRow_returnsPending() {
        User user = userRepository.findAll().get(0);
        Evidence extra = evidenceRepository.save(
                EvidenceTestFixtures.videoEvidence(user.getUserId(), "c.mp4", 'c')
        );
        Map<Long, EvidenceHls> map = evidenceHlsLookupService.findByEvidenceIds(List.of(evidenceId1));

        assertThat(evidenceHlsLookupService.resolveStatus(FileType.VIDEO, extra.getEvidenceId(), map))
                .isEqualTo(HlsStatus.PENDING);
    }

    @Test
    void resolveStatus_nonVideo_returnsNull() {
        Map<Long, EvidenceHls> map = evidenceHlsLookupService.findByEvidenceIds(List.of(evidenceId1));

        assertThat(evidenceHlsLookupService.resolveStatus(FileType.IMAGE, evidenceId1, map))
                .isNull();
    }

    @Test
    void findEvidenceIdsNeedingHlsPackaging_includesPendingAndMissingRow() {
        User user = userRepository.findAll().get(0);
        Evidence third = evidenceRepository.save(
                EvidenceTestFixtures.videoEvidence(user.getUserId(), "d.mp4", 'd')
        );

        List<Long> ids = evidenceHlsRepository.findEvidenceIdsNeedingHlsPackaging(
                List.of(HlsStatus.PENDING, HlsStatus.FAILED),
                PageRequest.of(0, 100)
        );

        assertThat(ids).contains(evidenceId1, third.getEvidenceId());
        assertThat(ids).doesNotContain(evidenceId2);
    }
}
