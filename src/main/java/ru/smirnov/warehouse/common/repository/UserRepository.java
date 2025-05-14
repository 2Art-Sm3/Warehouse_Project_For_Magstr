package ru.smirnov.warehouse.common.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.smirnov.warehouse.common.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
}
