package ru.smirnov.warehouse.common.service;

import org.springframework.stereotype.Service;
import ru.smirnov.warehouse.common.entity.User;
import ru.smirnov.warehouse.common.repository.UserRepository;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    public User save(User user) {
        return userRepository.save(user);
    }

}
