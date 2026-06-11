package com.example.demo.service;

public interface AnalysisJobEnqueuer {

    void enqueue(Long analysisRequestId, Long evidenceId);
}
