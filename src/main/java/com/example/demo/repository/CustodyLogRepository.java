package com.example.demo.repository;

import com.example.demo.domain.CustodyLog;
import com.example.demo.domain.enums.CustodyTargetType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustodyLogRepository extends JpaRepository<CustodyLog, Long> {

    List<CustodyLog> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
            CustodyTargetType targetType,
            Long targetId
    );
}
