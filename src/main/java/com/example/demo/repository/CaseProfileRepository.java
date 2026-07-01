package com.example.demo.repository;

import com.example.demo.domain.CaseProfile;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseProfileRepository extends JpaRepository<CaseProfile, Long> {

    Optional<CaseProfile> findByUploaderIdAndCaseKey(Long uploaderId, String caseKey);

    List<CaseProfile> findByUploaderIdAndCaseKeyIn(Long uploaderId, Collection<String> caseKeys);
}
