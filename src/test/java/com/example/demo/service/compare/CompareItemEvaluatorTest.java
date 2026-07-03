package com.example.demo.service.compare;

import com.example.demo.domain.enums.CompareItemResult;
import com.example.demo.domain.enums.CompareVerdict;
import com.example.demo.dto.compare.CompareItemDto;
import com.example.demo.service.blockchain.BlockchainAnchorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CompareItemEvaluatorTest {

    @Mock
    private BlockchainAnchorService blockchainAnchorService;

    private CompareItemEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CompareItemEvaluator(blockchainAnchorService, new ObjectMapper());
    }

    @Test
    void determineVerdict_matchingHashReturnsOriginalMatch() {
        CompareVerdict verdict = evaluator.determineVerdict(List.of(), "same-hash", "same-hash");

        assertThat(verdict).isEqualTo(CompareVerdict.ORIGINAL_MATCH);
    }

    @Test
    void determineVerdict_mismatchItemReturnsTampered() {
        List<CompareItemDto> items = List.of(
                CompareItemDto.builder()
                        .itemKey("FILE_SIZE")
                        .result(CompareItemResult.MISMATCH)
                        .build()
        );

        CompareVerdict verdict = evaluator.determineVerdict(items, "candidate", "original");

        assertThat(verdict).isEqualTo(CompareVerdict.TAMPERED);
    }
}
