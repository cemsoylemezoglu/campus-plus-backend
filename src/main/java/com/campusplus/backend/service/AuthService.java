package com.campusplus.backend.service;

import com.campusplus.backend.dto.RegisterRequest;
import com.campusplus.backend.dto.UserResponse;
import com.campusplus.backend.entity.User;
import com.campusplus.backend.exception.EmailAlreadyExistsException;
import com.campusplus.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(@Valid RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Bu e-posta adresi zaten kullanılıyor: " + request.email());
        }
        User user = new User(request.name(), request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Flush here so a racing unique-constraint violation surfaces before returning a response.
        return UserResponse.from(userRepository.saveAndFlush(user));
    }
}
