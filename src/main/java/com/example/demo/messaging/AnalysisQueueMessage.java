package com.example.demo.messaging;

public record AnalysisQueueMessage(
        Long analysisRequestId,
        Long evidenceId,
        Long requestedBy,
        String caseName,
        String subjectHash,
        String storagePath
) {
}
