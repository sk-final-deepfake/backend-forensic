package com.example.demo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "analysis_result_id")
    private Long analysisResultId;

    @Column(name = "compare_id")
    private Long compareId;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "report_file_name", length = 500)
    private String reportFileName;

    @Column(name = "storage_path", nullable = false, columnDefinition = "clob")
    private String storagePath;

    @Column(name = "report_hash", length = 64)
    private String reportHash;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
