package com.campusplus.backend.service;

import com.campusplus.backend.dto.CreateUserRequest;
import com.campusplus.backend.dto.UpdateUserRequest;
import com.campusplus.backend.entity.User;
import com.campusplus.backend.exception.EmailAlreadyExistsException;
import com.campusplus.backend.exception.UserNotFoundException;
import com.campusplus.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private UserRepository repository;
    private UserService service;
    private User user;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        service = new UserService(repository);
        user = new User("Cem", "cem@example.com");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void createReturnsSafeResponse() {
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            assertNull(saved.getId());
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        var response = service.saveUser(new CreateUserRequest("Ada", "ada@example.com"));
        assertEquals(2L, response.id());
        assertEquals("Ada", response.name());
        assertEquals("ada@example.com", response.email());
    }

    @Test
    void duplicateCreateDoesNotSave() {
        when(repository.existsByEmail(user.getEmail())).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class,
                () -> service.saveUser(new CreateUserRequest(user.getName(), user.getEmail())));
        verify(repository, never()).save(any());
    }

    @Test
    void updateAllowsKeepingOwnEmail() {
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.save(user)).thenReturn(user);
        var response = service.updateUser(1L, new UpdateUserRequest("Yeni isim", user.getEmail()));
        assertEquals("Yeni isim", response.name());
        assertEquals(1L, response.id());
        verify(repository).existsByEmailAndIdNot("cem@example.com", 1L);
    }

    @Test
    void duplicateUpdateDoesNotMutateOrSave() {
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.existsByEmailAndIdNot("other@example.com", 1L)).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class,
                () -> service.updateUser(1L, new UpdateUserRequest("Changed", "other@example.com")));
        assertEquals("Cem", user.getName());
        assertEquals("cem@example.com", user.getEmail());
        verify(repository, never()).save(any());
    }

    @Test
    void readsReturnResponses() {
        when(repository.findAll()).thenReturn(List.of(user));
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        assertEquals(service.getUserById(1L), service.getAllUsers().getFirst());
        assertEquals("cem@example.com", service.getUserById(1L).email());
    }

    @Test
    void missingUserFailsForReadUpdateAndDelete() {
        assertThrows(UserNotFoundException.class, () -> service.getUserById(99L));
        assertThrows(UserNotFoundException.class,
                () -> service.updateUser(99L, new UpdateUserRequest("Ada", "ada@example.com")));
        assertThrows(UserNotFoundException.class, () -> service.deleteUser(99L));
        verify(repository, never()).save(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void deleteUsesExistingUser() {
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        service.deleteUser(1L);
        verify(repository).delete(user);
    }
}
