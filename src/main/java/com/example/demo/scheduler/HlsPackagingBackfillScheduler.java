package com.example.demo.scheduler;

import com.example.demo.config.HlsPackagingProperties;
import com.example.demo.domain.enums.HlsStatus;
import com.example.demo.repository.EvidenceHlsRepository;
import com.example.demo.service.evidence.hls.EvidenceHlsPackagingService;
import com.example.demo.service.evidence.hls.HlsPackagingEnqueuer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hls.packaging.enabled", havingValue = "true", matchIfMissing = true)
public class HlsPackagingBackfillScheduler {

    private final HlsPackagingProperties properties;
    private final EvidenceHlsRepository evidenceHlsRepository;
    private final HlsPackagingEnqueuer hlsPackagingEnqueuer;
    private final EvidenceHlsPackagingService packagingService;

    @Scheduled(fixedDelayString = "${hls.packaging.backfill-interval-ms:60000}")
    public void enqueuePendingJobs() {
        int rolledBack = packagingService.rollbackStalePackaging();
        if (rolledBack > 0) {
            log.info("Rolled back {} stale HLS PACKAGING rows", rolledBack);
        }

        List<Long> evidenceIds = evidenceHlsRepository.findEvidenceIdsNeedingHlsPackaging(
                List.of(HlsStatus.PENDING, HlsStatus.FAILED),
                PageRequest.of(0, properties.getBatchSize())
        );
        for (Long evidenceId : evidenceIds) {
            hlsPackagingEnqueuer.enqueue(evidenceId);
        }
        if (!evidenceIds.isEmpty()) {
            log.info("Enqueued {} HLS packaging jobs", evidenceIds.size());
        }
    }
}
