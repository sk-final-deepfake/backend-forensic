package com.example.demo.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaseNumberSupportTest {

    @Test
    void resolve_prefersExplicitCaseNumber() {
        assertThat(CaseNumberSupport.resolve("2026-서울-0123", "딥페이크 테스트"))
                .isEqualTo("2026-서울-0123");
    }

    @Test
    void resolve_fallsBackToCaseName() {
        assertThat(CaseNumberSupport.resolve(null, "딥페이크 테스트"))
                .isEqualTo("딥페이크 테스트");
    }
}
