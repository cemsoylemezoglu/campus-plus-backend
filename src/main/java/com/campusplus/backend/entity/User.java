package com.campusplus.backend.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users")
public class User {

    public User() {
    }

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    // Nullable for accounts created before password-based registration.
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "İsim boş bırakılamaz")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "E-posta adresi boş bırakılamaz")
    @Email(message = "Geçerli bir e-posta adresi giriniz")
    @Column(nullable = false, length = 255, unique = true)
    private String email;
}