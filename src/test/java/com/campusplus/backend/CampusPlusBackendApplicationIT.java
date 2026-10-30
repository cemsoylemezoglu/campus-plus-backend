package com.campusplus.backend;

import com.campusplus.backend.security.JwtTestKeys;
import com.campusplus.backend.support.PostgresTestDatabase;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

// Failsafe only: ./mvnw -Ppostgres-it verify. No fallback to the application's DB_URL.
@SpringBootTest
@Import(CampusPlusBackendApplicationIT.DatabaseConfiguration.class)
class CampusPlusBackendApplicationIT {

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase database = PostgresTestDatabase.from(System::getenv);
        database.registerProperties(registry);
        String ephemeralSecret = JwtTestKeys.newSecret();
        registry.add("JWT_SECRET", () -> ephemeralSecret);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DatabaseConfiguration {
        @Bean
        DataSource dataSource() {
            // A dedicated bean prevents Hikari/application URL overrides from redirecting this test.
            return PostgresTestDatabase.from(System::getenv).dataSource();
        }
    }

    @Test
    void contextLoads() {
    }
}
