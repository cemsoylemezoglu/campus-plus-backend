package com.campusplus.backend.academic;

import com.campusplus.backend.entity.*;
import com.campusplus.backend.repository.*;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.support.JpaEntityInformationSupport;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

// Hibernate has no connection URL/credentials. The provider refuses connection requests.
// No JDBC metadata access, schema generation, migration or SQL execution is allowed.
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AcademicMappingsTest {
    private StandardServiceRegistry registry;
    private SessionFactory factory;

    @BeforeAll
    void buildMappings() {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.connection.provider_class", UserSuppliedConnectionProviderImpl.class.getName())
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        factory = new MetadataSources(registry).addAnnotatedClass(User.class)
                .addAnnotatedClass(University.class).addAnnotatedClass(StudentProfile.class)
                .buildMetadata().buildSessionFactory();
    }
    @AfterAll
    void closeMappings() {
        if (factory != null) factory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }
    @Test
    void repositoryHqlCompilesAgainstRealEntityMetadataWithoutConnecting() {
        try (var session = factory.openSession()) {
            for (Class<?> type : new Class<?>[]{UniversityRepository.class, UserRepository.class}) {
                for (var method : type.getDeclaredMethods()) {
                    Query annotation = method.getAnnotation(Query.class);
                    if (annotation != null) {
                        Class<?> resultType = type == UniversityRepository.class ? University.class : User.class;
                        assertDoesNotThrow(() -> session.createQuery(annotation.value(), resultType));
                    }
                }
            }
        }
    }
    @Test
    void sharedUserIdWithNullVersionIsRecognizedAsNewProfile() {
        User user = new User("Student", "student@example.com");
        ReflectionTestUtils.setField(user, "id", 2L);
        var university = new University("UNI", "University", "City", "TR", null, Instant.EPOCH);
        var profile = new StudentProfile(user, university, "Department", null, null, Instant.EPOCH);
        try (var session = factory.openSession()) {
            var metadata = JpaEntityInformationSupport.getEntityInformation(StudentProfile.class, session);
            assertEquals(2L, metadata.getId(profile));
            assertTrue(metadata.isNew(profile));
            ReflectionTestUtils.setField(profile, "version", 0L);
            assertFalse(metadata.isNew(profile));
        }
    }
}
