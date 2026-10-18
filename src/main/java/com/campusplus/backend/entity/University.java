package com.campusplus.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "universities")
public class University {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30, updatable = false)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 100)
    private String city;
    @Column(name = "country_code", nullable = false, length = 2, updatable = false)
    private String countryCode;
    @Column(name = "website_url", length = 500)
    private String websiteUrl;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected University() {}
    public University(String code, String name, String city, String countryCode, String websiteUrl, Instant now) {
        this.code = code; this.name = name; this.city = city; this.countryCode = countryCode;
        this.websiteUrl = websiteUrl; this.createdAt = now; this.updatedAt = now;
    }
    public void update(String name, String city, String websiteUrl, boolean active, Instant now) {
        this.name = name; this.city = city; this.websiteUrl = websiteUrl;
        this.active = active; this.updatedAt = now;
    }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getCountryCode() { return countryCode; }
    public String getWebsiteUrl() { return websiteUrl; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
