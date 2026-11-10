package com.dataforge.authapi.data.entity;

import com.dataforge.authapi.users.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = UserEntity.USER_TABLE)
public class UserEntity {

    @Id
    @Column(name = USER_ID, nullable = false, updatable = false)
    private UUID userId;

    @Column(name = USER_EMAIL, nullable = false, length = 254)
    private String email;

    @Column(name = USER_PASSWORD_HASH, nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = USER_ROLE, nullable = false, length = 20)
    private UserRole role;

    @Column(name = USER_CREATED_AT, nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = USER_UPDATED_AT, nullable = false)
    private Instant updatedAt;

    /** Required by JPA to create an entity when reading it from the database. */
    protected UserEntity() {}

    public UserEntity(
            UUID userId,
            String email,
            String passwordHash,
            Instant createdAt
    ) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    static final String USER_TABLE = "users";
    static final String USER_ID = "user_id";
    static final String USER_EMAIL = "email";
    static final String USER_PASSWORD_HASH = "password_hash";
    static final String USER_ROLE = "user_role";
    static final String USER_CREATED_AT = "created_at";
    static final String USER_UPDATED_AT = "updated_at";
}