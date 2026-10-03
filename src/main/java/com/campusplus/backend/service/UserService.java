package com.campusplus.backend.service;

import com.campusplus.backend.entity.User;
import com.campusplus.backend.exception.EmailAlreadyExistsException;
import com.campusplus.backend.exception.UserNotFoundException;
import com.campusplus.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User saveUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "Bu e-posta adresi zaten kullanılıyor: " + user.getEmail()
            );
        }

        return userRepository.save(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("Kullanıcı bulunamadı. ID: " + id)
                );
    }

    public User updateUser(Long id, User updatedUser) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("Kullanıcı bulunamadı. ID: " + id)
                );

        user.setName(updatedUser.getName());
        user.setEmail(updatedUser.getEmail());

        return userRepository.save(user);
    }

    public void deleteUser(Long id) {

        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new UserNotFoundException("Kullanıcı bulunamadı. ID: " + id)
                );

        userRepository.delete(user);
    }
}