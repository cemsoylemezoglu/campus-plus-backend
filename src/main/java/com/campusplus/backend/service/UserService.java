package com.campusplus.backend.service;

import com.campusplus.backend.entity.User;
import com.campusplus.backend.dto.CreateUserRequest;
import com.campusplus.backend.dto.UpdateUserRequest;
import com.campusplus.backend.dto.UserResponse;
import org.springframework.transaction.annotation.Transactional;
import com.campusplus.backend.exception.EmailAlreadyExistsException;
import com.campusplus.backend.exception.UserNotFoundException;
import com.campusplus.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse saveUser(CreateUserRequest request) {
        User user = new User(request.name(), request.email());
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Bu e-posta adresi zaten kullanılıyor: " + user.getEmail()
            );
        }

        return UserResponse.from(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @PreAuthorize("@userAccess.canAccess(authentication, #id)")
    public UserResponse getUserById(Long id) {
        return UserResponse.from(findUser(id));
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("Kullanıcı bulunamadı. ID: " + id)
                );
    }

    @Transactional
    @PreAuthorize("@userAccess.canAccess(authentication, #id)")
    public UserResponse updateUser(Long id, UpdateUserRequest updatedUser) {

        User user = findUser(id);

        if (userRepository.existsByEmailAndIdNot(updatedUser.email(), id)) {
            throw new EmailAlreadyExistsException("Bu e-posta adresi zaten kullanılıyor: " + updatedUser.email());
        }
        user.setName(updatedUser.name());
        user.setEmail(updatedUser.email());

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long id) {

        User user = findUser(id);

        userRepository.delete(user);
    }
}