package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.enums.FileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceStoragePathsTest {

    @Test
    @DisplayName("S3 original key uses caseName-caseNumber and hides original filename")
    void originalKey_usesCaseDerivedName() {
        Evidence evidence = Evidence.builder()
                .caseName("딥페이크 테스트")
                .caseNumber("2026-서울-0123")
                .fileName("홍길동_증거영상.mp4")
                .fileType(FileType.VIDEO)
                .build();
        ReflectionTestUtils.setField(evidence, "evidenceId", 103L);

        String caseKey = EvidenceStoragePaths.resolveCaseKey(evidence);

        assertThat(EvidenceStoragePaths.storedObjectFileName(evidence, "홍길동_증거영상.mp4"))
                .isEqualTo("딥페이크-테스트-2026-서울-0123.mp4");
        assertThat(EvidenceStoragePaths.originalKey(caseKey, evidence, "홍길동_증거영상.mp4"))
                .isEqualTo("cases/2026-서울-0123/103/original/딥페이크-테스트-2026-서울-0123.mp4");
        assertThat(EvidenceStoragePaths.copyKey(caseKey, evidence, "홍길동_증거영상.mp4"))
                .isEqualTo("cases/2026-서울-0123/103/copy/딥페이크-테스트-2026-서울-0123.mp4");
        assertThat(EvidenceStoragePaths.manifestKey(caseKey, 103L))
                .isEqualTo("cases/2026-서울-0123/103/manifest/evidence-manifest.json");
    }

    @Test
    @DisplayName("storedObjectBaseName avoids duplicate when caseName equals caseNumber")
    void storedObjectBaseName_deduplicatesIdenticalCaseFields() {
        Evidence evidence = Evidence.builder()
                .caseName("딥페이크 테스트")
                .caseNumber("딥페이크 테스트")
                .fileName("clip.mp4")
                .fileType(FileType.VIDEO)
                .build();

        assertThat(EvidenceStoragePaths.storedObjectFileName(evidence, "clip.MOV"))
                .isEqualTo("딥페이크-테스트.mov");
    }

    @Test
    @DisplayName("storedObjectFileName normalizes extension to lowercase")
    void storedObjectFileName_lowercasesExtension() {
        Evidence evidence = Evidence.builder()
                .caseName("사건-a")
                .caseNumber("2026-001")
                .fileName("clip.MOV")
                .fileType(FileType.VIDEO)
                .build();

        assertThat(EvidenceStoragePaths.storedObjectFileName(evidence, "clip.MOV"))
                .isEqualTo("사건-a-2026-001.mov");
    }
}
