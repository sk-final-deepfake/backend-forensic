package com.example.demo.repository;

import com.example.demo.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCode(String code);
}
