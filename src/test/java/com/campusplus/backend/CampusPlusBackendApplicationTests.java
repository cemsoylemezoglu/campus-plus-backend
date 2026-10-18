package com.campusplus.backend;

import org.junit.jupiter.api.Test;
import com.campusplus.backend.security.JwtTestKeys;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CampusPlusBackendApplicationTests {

    @DynamicPropertySource
    static void testJwtKey(DynamicPropertyRegistry registry) {
        String ephemeralSecret = JwtTestKeys.newSecret();
        registry.add("JWT_SECRET", () -> ephemeralSecret);
    }

	@Test
	void contextLoads() {
	}

}
