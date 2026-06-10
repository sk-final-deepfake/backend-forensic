package com.example.demo.domain;

import com.example.demo.domain.enums.CustodyTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "custody_logs")
@Getter
@Setter
@NoArgsConstructor
public class CustodyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private CustodyTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "subject_hash", length = 64)
    private String subjectHash;

    @Column(name = "storage_path_at_event", columnDefinition = "clob")
    private String storagePathAtEvent;

    @Column(columnDefinition = "clob")
    private String reason;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "event_payload_json", columnDefinition = "json")
    private String eventPayloadJson;

    @Column(name = "previous_log_hash", length = 64)
    private String previousLogHash;

    @Column(name = "current_log_hash", nullable = false, length = 64)
    private String currentLogHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
