package com.dataforge.authapi.data.storage;

import com.dataforge.authapi.data.entity.UserEntity;
import com.dataforge.authapi.data.repository.UserRepository;
import com.dataforge.authapi.auth.model.User;
import com.dataforge.authapi.auth.storage.UserStorage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserStorageImpl implements UserStorage {

    private final UserRepository userRepository;

    public UserStorageImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public boolean create(
            UUID userId,
            String email,
            String passwordHash,
            Instant createdAt
    ) {
        int insertedRows = userRepository.insertUser(
                userId,
                email,
                passwordHash,
                createdAt
        );

        return insertedRows == 1;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toUser);
    }

    private User toUser(UserEntity entity) {
        return new User(
                entity.getUserId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}