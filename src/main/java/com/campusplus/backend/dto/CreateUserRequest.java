package com.campusplus.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "İsim boş bırakılamaz")
        @Size(max = 100, message = "İsim en fazla 100 karakter olmalıdır")
        String name,
        @NotBlank(message = "E-posta boş bırakılamaz")
        @Email(message = "Geçerli bir e-posta adresi giriniz")
        @Size(max = 255, message = "E-posta en fazla 255 karakter olmalıdır")
        String email
) {}
