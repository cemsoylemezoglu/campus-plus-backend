package com.campusplus.backend.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PostgresTestDatabaseTest {
    private static Map<String, String> settings() {
        return new HashMap<>(Map.of("TEST_DB_URL", PostgresTestDatabase.URL,
                "TEST_DB_USERNAME", "test-fixture-user", "TEST_DB_PASSWORD", "unit-test-canary"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEST_DB_URL", "TEST_DB_USERNAME", "TEST_DB_PASSWORD"})
    void missingTestSettingIsRejectedEvenWhenApplicationCredentialsExist(String name) {
        var environment = settings();
        environment.remove(name);
        environment.putAll(Map.of("DB_URL", PostgresTestDatabase.URL, "DB_USERNAME", "application-user",
                "DB_PASSWORD", "application-secret"));
        var error = assertThrows(IllegalStateException.class, () -> PostgresTestDatabase.from(environment::get));
        assertTrue(error.getMessage().contains(name));
        assertFalse(error.getMessage().contains("application-secret"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TEST_DB_URL", "TEST_DB_USERNAME", "TEST_DB_PASSWORD"})
    void blankTestSettingIsRejected(String name) {
        var environment = settings();
        environment.put(name, "  ");
        assertThrows(IllegalStateException.class, () -> PostgresTestDatabase.from(environment::get));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:postgresql://127.0.0.1:5434/campus_plus",
            "jdbc:postgresql://127.0.0.1:5432/campus_plus_test",
            "jdbc:postgresql://localhost:5434/campus_plus_test",
            "jdbc:postgresql://remote.example:5434/campus_plus_test",
            "jdbc:postgresql://127.0.0.1:5434/another_project",
            "jdbc:postgresql://127.0.0.1:5434/campus_plus_test?password=url-canary",
            "jdbc:postgresql://127.0.0.1:5434/campus_plus_test?currentSchema=public",
            "jdbc:postgresql://127.0.0.1:5434/campus_plus_test/",
            "jdbc:postgresql://test:user@127.0.0.1:5434/campus_plus_test",
            "jdbc:h2:mem:campus_plus_test"})
    void mainDatabaseOtherServersAndUrlOverridesAreRejectedWithoutConnecting(String url) {
        var environment = settings();
        environment.put("TEST_DB_URL", url);
        var error = assertThrows(IllegalStateException.class, () -> PostgresTestDatabase.from(environment::get));
        assertFalse(error.getMessage().contains(url));
        assertFalse(error.getMessage().contains("url-canary"));
    }

    @Test
    void validSettingsCreateDataSourceWithoutConnectingOrExposingCredentials() {
        var database = PostgresTestDatabase.from(settings()::get);
        DataSource dataSource = database.dataSource();
        assertNotNull(dataSource);
        assertFalse(database.toString().contains("unit-test-canary"));
        assertFalse(dataSource.toString().contains("unit-test-canary"));
    }

    @ParameterizedTest
    @CsvSource({"campus_plus,127.0.0.1,5434,170011", "campus_plus_test,127.0.0.1,5432,170011",
            "campus_plus_test,127.0.0.2,5434,170011", "campus_plus_test,127.0.0.1,5434,140000",
            "campus_plus_test,127.0.0.1,5434,180000"})
    void mismatchedActualIdentityClosesConnectionBeforeApplicationCanUseIt(String database, String host,
                                                                            int port, int version) throws Exception {
        var fixture = connection(database, host, port, version);
        var error = assertThrows(SQLException.class, () -> PostgresTestDatabase.verified(fixture.source()).getConnection());
        assertEquals("Campus+ PostgreSQL 17 test target verification failed.", error.getMessage());
        verify(fixture.connection()).setReadOnly(true);
        verify(fixture.connection()).close();
    }

    @Test
    void authorizedPostgres17IdentityReturnsReadOnlyConnection() throws Exception {
        var fixture = connection("campus_plus_test", "127.0.0.1", 5434, 170011);
        assertSame(fixture.connection(), PostgresTestDatabase.verified(fixture.source()).getConnection());
        verify(fixture.connection()).setReadOnly(true);
        verify(fixture.connection(), never()).close();
        verify(fixture.statement()).setQueryTimeout(5);
    }

    @Test
    void identityQueryFailureClosesConnectionAndDoesNotExposeDriverDetails() throws Exception {
        var fixture = connection("campus_plus_test", "127.0.0.1", 5434, 170011);
        when(fixture.statement().executeQuery(anyString())).thenThrow(new SQLException("driver-secret-canary", "08006"));
        var error = assertThrows(SQLException.class, () -> PostgresTestDatabase.verified(fixture.source()).getConnection());
        assertFalse(error.getMessage().contains("driver-secret-canary"));
        assertNull(error.getCause());
        verify(fixture.connection()).close();
    }

    @Test
    void connectionFailureDoesNotExposeDriverDetails() throws Exception {
        DataSource delegate = mock(DataSource.class);
        when(delegate.getConnection()).thenThrow(new SQLException("connection-secret-canary", "08001"));
        var error = assertThrows(SQLException.class, () -> PostgresTestDatabase.verified(delegate).getConnection());
        assertEquals("Campus+ PostgreSQL 17 test target verification failed.", error.getMessage());
        assertEquals("08001", error.getSQLState());
        assertNull(error.getCause());
    }

    @Test
    void alternateCredentialsCannotBypassTheConnectionBoundary() throws Exception {
        DataSource delegate = mock(DataSource.class);
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> PostgresTestDatabase.verified(delegate).getConnection("other", "other-canary"));
        verifyNoInteractions(delegate);
    }

    @Test
    void writableTestsRejectAdminOrMigrationCredentialsBeforeConnecting() {
        for (String username : new String[]{"cem", "campus_plus_test_migrator", "campus_plus_user"}) {
            var environment = settings();
            environment.put("TEST_DB_USERNAME", username);
            assertThrows(IllegalStateException.class,
                    () -> PostgresTestDatabase.from(environment::get).writableDataSource());
        }
    }

    @Test
    void writableModeDoesNotDisableReadOnlyOnWrongServer() throws Exception {
        var fixture = connection("campus_plus", "127.0.0.1", 5434, 170011);
        assertThrows(SQLException.class, () -> PostgresTestDatabase.verified(fixture.source(), true).getConnection());
        verify(fixture.connection(), never()).setReadOnly(false);
        verify(fixture.connection()).close();
    }

    @Test
    void privilegedRunnerIsRejectedBeforeReadOnlyIsDisabled() throws Exception {
        var fixture = connection("campus_plus_test", "127.0.0.1", 5434, 170011);
        assertThrows(SQLException.class, () -> PostgresTestDatabase.verified(fixture.source(), true).getConnection());
        verify(fixture.connection(), never()).setReadOnly(false);
        verify(fixture.connection()).close();
    }

    @Test
    void verifiedLimitedRunnerCanWriteOnlyAfterIdentityAndPrivilegeChecks() throws Exception {
        var fixture = connection("campus_plus_test", "127.0.0.1", 5434, 170011);
        var capabilities = mock(ResultSet.class);
        when(capabilities.next()).thenReturn(true);
        when(capabilities.getBoolean(1)).thenReturn(true);
        when(fixture.statement().executeQuery(startsWith("select current_user="))).thenReturn(capabilities);
        assertSame(fixture.connection(), PostgresTestDatabase.verified(fixture.source(), true).getConnection());
        var order = inOrder(fixture.connection(), fixture.statement());
        order.verify(fixture.connection()).setReadOnly(true);
        order.verify(fixture.statement()).executeQuery(startsWith("select current_database()"));
        order.verify(fixture.statement()).executeQuery(startsWith("select current_user="));
        order.verify(fixture.connection()).setReadOnly(false);
        verify(fixture.connection(), never()).close();
    }

    private record Fixture(DataSource source, Connection connection, Statement statement) {}

    private static Fixture connection(String database, String host, int port, int version) throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getString(1)).thenReturn(database);
        when(result.getString(2)).thenReturn(host);
        when(result.getInt(3)).thenReturn(port);
        when(result.getInt(4)).thenReturn(version);
        return new Fixture(source, connection, statement);
    }
}
