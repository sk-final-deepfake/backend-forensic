package com.example.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * H2 로컬/테스트: 레거시 prototype 테이블 제거 및 ERD v3 부분 인덱스 보완.
 */
@Slf4j
@Component
@Profile({"local", "default", "test"})
@RequiredArgsConstructor
public class H2ErdSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (!isH2Database()) {
            return;
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS evidence");

        try {
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_analysis_requests_one_completed
                    ON analysis_requests (evidence_id)
                    WHERE status = 'COMPLETED'
                    """);
        } catch (Exception e) {
            log.warn("Could not create partial unique index on analysis_requests: {}", e.getMessage());
        }

        migrateReportsTableForComparePdf();
        migrateEvidencesV2Workflow();
        migrateEvidenceMetadataReadiness();
    }

    void migrateEvidencesV2Workflow() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE evidences
                    ADD COLUMN IF NOT EXISTS lifecycle_status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL
                    """);
        } catch (Exception e) {
            log.warn("evidences.lifecycle_status migration skipped: {}", e.getMessage());
        }
        addColumnIfMissing("evidences", "evidence_role", "VARCHAR(20)");
        addColumnIfMissing("evidences", "display_label", "VARCHAR(100)");
        addColumnIfMissing("evidences", "replacement_evidence_id", "BIGINT");
        addColumnIfMissing("evidences", "excluded_reason", "VARCHAR(500)");
    }

    void migrateEvidenceMetadataReadiness() {
        addColumnIfMissing("evidence_metadata", "readiness_json", "JSON");
    }

    private void addColumnIfMissing(String table, String column, String type) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s".formatted(table, column, type));
        } catch (Exception e) {
            log.warn("{}.{} migration skipped: {}", table, column, e.getMessage());
        }
    }

    /**
     * compare PDF는 analysis_result_id 없이 reports에 저장됩니다.
     * 레거시 H2 스키마(NOT NULL)를 PostgreSQL 마이그레이션(002)과 동일하게 완화합니다.
     */
    void migrateReportsTableForComparePdf() {
        try {
            jdbcTemplate.execute("ALTER TABLE reports ALTER COLUMN analysis_result_id DROP NOT NULL");
        } catch (Exception e) {
            log.debug("reports.analysis_result_id already nullable or table missing: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE reports ADD COLUMN IF NOT EXISTS compare_id BIGINT");
        } catch (Exception e) {
            log.debug("reports.compare_id migration skipped: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_reports_compare_id ON reports (compare_id)");
        } catch (Exception e) {
            log.warn("Could not create idx_reports_compare_id: {}", e.getMessage());
        }
    }

    private boolean isH2Database() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:h2:");
    }
}
