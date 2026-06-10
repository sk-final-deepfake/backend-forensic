package com.example.demo.repository;

import com.example.demo.domain.User;
import com.example.demo.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginIdAndDeletedAtIsNull(String loginId);

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByLoginIdAndUserIdNotAndDeletedAtIsNull(String loginId, Long userId);

    boolean existsByEmailAndUserIdNotAndDeletedAtIsNull(String email, Long userId);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(UserStatus status);

    @Query("SELECT u.userId FROM User u WHERE u.deletedAt IS NULL AND u.department = :department")
    List<Long> findUserIdsByDepartment(@Param("department") String department);

    @Query("SELECT DISTINCT u.department FROM User u WHERE u.deletedAt IS NULL ORDER BY u.department")
    List<String> findDistinctDepartments();

    @Query("""
            SELECT u FROM User u
            WHERE u.deletedAt IS NULL
              AND (:status IS NULL OR u.status = :status)
              AND (
                :search = '' OR
                LOWER(u.loginId) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR
                LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            ORDER BY u.createdAt DESC
            """)
    Page<User> findAdminUsers(
            @Param("status") UserStatus status,
            @Param("search") String search,
            Pageable pageable
    );
}
