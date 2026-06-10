package com.example.demo.repository;

import com.example.demo.domain.CustodyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface CustodyLogRepository extends JpaRepository<CustodyLog, Long>, JpaSpecificationExecutor<CustodyLog> {

    Optional<CustodyLog> findTopByOrderByLogIdDesc();

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime start, LocalDateTime end);

    long countByActionTypeIn(Collection<String> actionTypes);
}
