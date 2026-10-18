package com.campusplus.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class JsonSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(response, 401, "{\"status\":401,\"error\":\"Kimlik doğrulama gerekli\","
                + "\"message\":\"Geçerli kimlik doğrulama bilgileri gerekli.\"}");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException, ServletException {
        write(response, 403, "{\"status\":403,\"error\":\"Erişim reddedildi\","
                + "\"message\":\"Bu işlem için yetkiniz bulunmuyor.\"}");
    }

    private void write(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json);
    }
}
