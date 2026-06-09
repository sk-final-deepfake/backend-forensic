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
    }

    private boolean isH2Database() {
        String url = environment.getProperty("spring.datasource.url", "");
        return url.startsWith("jdbc:h2:");
    }
}
