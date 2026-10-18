package com.campusplus.backend.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=[REDACTED], tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
