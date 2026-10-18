package com.campusplus.backend.controller;

import com.campusplus.backend.entity.User;
import com.campusplus.backend.exception.GlobalExceptionHandler;
import com.campusplus.backend.repository.UserRepository;
import com.campusplus.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// MVC + service tests with an isolated repository: no Spring Boot context or database connection.
class UserControllerTest {
    private UserRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        User user = new User("Cem", "cem@example.com");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setPasswordHash("existing-password-hash");
        when(repository.findById(1L)).thenReturn(Optional.of(user));
        when(repository.findAll()).thenReturn(List.of(user));
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        mvc = standaloneSetup(new UserController(new UserService(repository)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void allResponsesContainOnlyPublicFields() throws Exception {
        String body = """
                {"name":"Cem","email":"cem@example.com"}
                """;
        for (MockHttpServletRequestBuilder request : List.of(
                post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body),
                put("/api/users/1").contentType(MediaType.APPLICATION_JSON).content(body),
                get("/api/users/1"))) {
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(content().json("""
                            {"id":1,"name":"Cem","email":"cem@example.com"}
                            """, org.springframework.test.json.JsonCompareMode.STRICT));
        }
        mvc.perform(get("/api/users")).andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":1,"name":"Cem","email":"cem@example.com"}]
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsBlankNameAndInvalidEmail(String method) throws Exception {
        var request = method.equals("POST") ? post("/api/users") : put("/api/users/1");
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists()).andExpect(jsonPath("$.email").exists());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    void rejectsFieldsExceedingDatabaseLimits(String method) throws Exception {
        var request = method.equals("POST") ? post("/api/users") : put("/api/users/1");
        mvc.perform(request.contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(101)
                                + "\",\"email\":\"" + "a".repeat(244) + "@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists()).andExpect(jsonPath("$.email").exists());
        verify(repository, never()).save(any());
    }

    @Test
    void clientCannotAssignDatabaseId() throws Exception {
        when(repository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertNull(saved.getId());
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":99,\"name\":\"Cem\",\"email\":\"cem@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void duplicateUpdateReturns409() throws Exception {
        when(repository.existsByEmailAndIdNot("other@example.com", 1L)).thenReturn(true);
        mvc.perform(put("/api/users/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cem\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void missingUserReturns404() throws Exception {
        mvc.perform(get("/api/users/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(repository.existsByEmail("cem@example.com")).thenReturn(true);
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cem\",\"email\":\"cem@example.com\"}"))
                .andExpect(status().isConflict());
        verify(repository, never()).save(any());
    }

    @Test
    void databaseConflictDoesNotExposeInternalDetails() throws Exception {
        when(repository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("sensitive SQL details"));
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cem\",\"email\":\"cem@example.com\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "İşlem mevcut kayıtlarla çakışıyor. Gönderdiğiniz bilgileri kontrol ediniz."));
    }

    @Test
    void deletePreservesExistingEndpointBehavior() throws Exception {
        mvc.perform(delete("/api/users/1")).andExpect(status().isOk());
        verify(repository).delete(any(User.class));
    }
}
