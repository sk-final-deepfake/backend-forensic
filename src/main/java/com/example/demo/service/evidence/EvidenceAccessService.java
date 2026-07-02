package com.example.demo.service.evidence;

import com.example.demo.domain.Evidence;
import com.example.demo.domain.User;
import com.example.demo.exception.BusinessException;
import com.example.demo.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvidenceAccessService {

    private final EvidenceRepository evidenceRepository;

    public Evidence requireOwned(User user, Long evidenceId) {
        return requireOwned(user.getUserId(), evidenceId);
    }

    public Evidence requireOwned(Long userId, Long evidenceId) {
        return evidenceRepository
                .findByEvidenceIdAndUploaderIdAndDeletedAtIsNull(evidenceId, userId)
                .orElseThrow(() -> new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증거를 찾을 수 없습니다."));
    }
}
