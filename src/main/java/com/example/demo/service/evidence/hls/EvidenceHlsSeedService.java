package com.example.demo.service.evidence.hls;

import com.example.demo.config.HlsPackagingProperties;
import com.example.demo.domain.EvidenceHls;
import com.example.demo.domain.enums.FileType;
import com.example.demo.repository.EvidenceHlsRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EvidenceHlsSeedService {

    private final HlsPackagingProperties properties;
    private final EvidenceHlsRepository evidenceHlsRepository;
    private final ObjectProvider<HlsPackagingEnqueuer> hlsPackagingEnqueuer;

    @Transactional
    public void seedPendingAndEnqueue(Long evidenceId, FileType fileType) {
        if (fileType != FileType.VIDEO) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        evidenceHlsRepository.findByEvidenceId(evidenceId).orElseGet(() -> {
            EvidenceHls created = EvidenceHls.createPending(evidenceId, now);
            return evidenceHlsRepository.save(created);
        });
        if (!properties.isEnabled()) {
            return;
        }
        HlsPackagingEnqueuer enqueuer = hlsPackagingEnqueuer.getIfAvailable();
        if (enqueuer != null) {
            enqueuer.enqueue(evidenceId);
        }
    }
}
