package com.campusplus.backend.dto;

import jakarta.validation.constraints.Email;
import com.campusplus.backend.validation.PasswordByteLimit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "İsim boş bırakılamaz")
        @Size(max = 100, message = "İsim en fazla 100 karakter olmalıdır")
        String name,

        @NotBlank(message = "E-posta boş bırakılamaz")
        @Email(message = "Geçerli bir e-posta adresi giriniz")
        @Size(max = 255, message = "E-posta en fazla 255 karakter olmalıdır")
        String email,

        @NotBlank(message = "Parola boş bırakılamaz")
        @Size(min = 8, message = "Parola en az 8 karakter olmalıdır")
        @PasswordByteLimit
        String password

) {
    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + ", password=[REDACTED]]";
    }
}
