package com.dataforge.authapi.data.repository;

import com.dataforge.authapi.data.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @Query(
            value = "SELECT * FROM users WHERE email = :email",
            nativeQuery = true
    )
    Optional<UserEntity> findByEmail(@Param("email") String email);

    @Modifying
    @Query(
            value = """
                INSERT INTO users (
                    user_id,
                    email,
                    password_hash,
                    user_role,
                    created_at,
                    updated_at
                )
                VALUES (
                    :userId,
                    :email,
                    :passwordHash,
                    'USER',
                    :createdAt,
                    :createdAt
                )
                ON CONFLICT (email) DO NOTHING
                """,
            nativeQuery = true
    )
    int insertUser(
            @Param("userId") UUID userId,
            @Param("email") String email,
            @Param("passwordHash") String passwordHash,
            @Param("createdAt") Instant createdAt
    );
}