package com.campusplus.backend.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Test-only connection boundary. Never reads DB_URL, DB_USERNAME or DB_PASSWORD. */
public final class PostgresTestDatabase {
    public static final String URL = "jdbc:postgresql://127.0.0.1:5434/campus_plus_test";
    private static final String IDENTITY_SQL = "select current_database(), host(inet_server_addr()), "
            + "inet_server_port(), current_setting('server_version_num')::integer";
    private final String username;
    private final String password;

    private PostgresTestDatabase(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public static PostgresTestDatabase from(Function<String, String> environment) {
        String url = required(environment, "TEST_DB_URL");
        if (!URL.equals(url)) {
            // Do not echo a rejected URL: JDBC URLs can contain credentials or tokens.
            throw new IllegalStateException("TEST_DB_URL must target the dedicated Campus+ test database; see README.");
        }
        return new PostgresTestDatabase(required(environment, "TEST_DB_USERNAME"),
                required(environment, "TEST_DB_PASSWORD"));
    }

    private static String required(Function<String, String> environment, String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for postgres-it; see README.");
        }
        return value;
    }

    public void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    public DataSource dataSource() {
        return verified(new DriverManagerDataSource(URL, username, password));
    }

    public DataSource writableDataSource() {
        if (!"campus_plus_test_runner".equals(username)) {
            throw new IllegalStateException("Writable integration tests require the limited Campus+ test runner.");
        }
        return verified(new DriverManagerDataSource(URL, username, password), true);
    }

    static DataSource verified(DataSource delegate) {
        return verified(delegate, false);
    }

    static DataSource verified(DataSource delegate, boolean writable) {
        return new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = null;
                try {
                    connection = delegate.getConnection();
                    // Every connection starts read-only, before verifying the actual server identity.
                    connection.setReadOnly(true);
                    try (var statement = connection.createStatement()) {
                        statement.setQueryTimeout(5);
                        try (var result = statement.executeQuery(IDENTITY_SQL)) {
                            if (result.next() && "campus_plus_test".equals(result.getString(1))
                                    && "127.0.0.1".equals(result.getString(2)) && result.getInt(3) == 5434
                                    && result.getInt(4) >= 170000 && result.getInt(4) < 180000) {
                                if (writable) {
                                    verifyLimitedRunner(connection);
                                    connection.setReadOnly(false);
                                }
                                return connection;
                            }
                        }
                    }
                    throw new SQLException("Unauthorized Campus+ PostgreSQL test server identity.");
                } catch (SQLException exception) {
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException ignored) {
                            // Preserve the safe verification error without attaching driver details.
                        }
                    }
                    throw new SQLException("Campus+ PostgreSQL 17 test target verification failed.", exception.getSQLState());
                }
            }

            @Override
            public Connection getConnection(String user, String pass) throws SQLException {
                throw new SQLFeatureNotSupportedException("Alternate test connection credentials are not supported.");
            }
        };
    }

    private static void verifyLimitedRunner(Connection connection) throws SQLException {
        String sql = "select current_user='campus_plus_test_runner' and not "
                + "(rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls) "
                + "and not exists(select 1 from pg_auth_members where member=r.oid) "
                + "and not has_database_privilege(current_user,current_database(),'CREATE') "
                + "and not has_schema_privilege(current_user,'public','CREATE') "
                + "and not has_table_privilege(current_user,'public.users','TRUNCATE') "
                + "and not has_table_privilege(current_user,'public.flyway_schema_history','INSERT,UPDATE,DELETE') "
                + "from pg_roles r where rolname=current_user";
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var result = statement.executeQuery(sql)) {
                if (result.next() && result.getBoolean(1)) return;
            }
        }
        throw new SQLException("Writable tests require the limited Campus+ runner without DDL privileges.");
    }
}
