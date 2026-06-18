package com.example.demo.repository;

import com.example.demo.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
}
