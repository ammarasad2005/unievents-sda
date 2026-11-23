package unievents.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton JDBC connection manager (design class diagram: «singleton»
 * DatabaseController with getInstance/getConnection/beginTransaction/
 * commit/rollback/closeConnection).
 *
 * Connection settings come from (in order of precedence):
 *   1. Environment variables UNIEVENTS_DB_URL / UNIEVENTS_DB_USER / UNIEVENTS_DB_PASSWORD
 *   2. JVM system properties unievents.db.url / .user / .password
 *   3. unievents.properties on the classpath (db.url / db.user / db.password)
 *
 * Example Supabase URL:
 *   jdbc:postgresql://db.PROJECT_REF.supabase.co:5432/postgres
 *   or the pooler: jdbc:postgresql://aws-0-REGION.pooler.supabase.com:6543/postgres?sslmode=require
 */
public final class DatabaseController {

    private static volatile DatabaseController instance;

    private final String url;
    private final String user;
    private final String password;
    private Connection connection;

    private DatabaseController() {
        this.url = setting("db.url", "UNIEVENTS_DB_URL");
        this.user = setting("db.user", "UNIEVENTS_DB_USER", "postgres");
        this.password = setting("db.password", "UNIEVENTS_DB_PASSWORD");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Database URL not configured. Set UNIEVENTS_DB_URL or db.url in unievents.properties.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Database password not configured. Set UNIEVENTS_DB_PASSWORD or db.password.");
        }
    }

    public static DatabaseController getInstance() {
        DatabaseController result = instance;
        if (result == null) {
            synchronized (DatabaseController.class) {
                result = instance;
                if (result == null) {
                    instance = new DatabaseController();
                    result = instance;
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Settings resolution
    // ------------------------------------------------------------------

    private String setting(String key, String envVar) {
        return setting(key, envVar, null);
    }

    private String setting(String key, String envVar, String fallback) {
        String value = System.getenv(envVar);
        if (value == null || value.isBlank()) {
            value = System.getProperty("unievents." + key);
        }
        if (value == null || value.isBlank()) {
            value = classpathProperty(key);
        }
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private String classpathProperty(String key) {
        try {
            var props = new java.util.Properties();
            try (var in = DatabaseController.class.getResourceAsStream("/unievents.properties")) {
                if (in == null) {
                    return null;
                }
                props.load(in);
            }
            return props.getProperty(key);
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Connection lifecycle
    // ------------------------------------------------------------------

    /**
     * Returns the live connection, opening it lazily.
     *
     * @throws IllegalStateException when the database cannot be reached
     */
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(url, user, password);
                connection.setAutoCommit(true);
            }
            return connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot connect to the database: " + e.getMessage(), e);
        }
    }

    /** Opens a transaction (auto-commit off). */
    public void beginTransaction() {
        try {
            Connection c = getConnection();
            if (c.getAutoCommit()) {
                c.setAutoCommit(false);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot begin transaction: " + e.getMessage(), e);
        }
    }

    /** Commits the current transaction and restores auto-commit. */
    public void commit() {
        try {
            Connection c = getConnection();
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot commit transaction: " + e.getMessage(), e);
        }
    }

    /** Rolls back the current transaction and restores auto-commit. */
    public void rollback() {
        try {
            Connection c = getConnection();
            c.rollback();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot roll back transaction: " + e.getMessage(), e);
        }
    }

    /** Closes the physical connection (called on app shutdown). */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // shutdown-time best effort only
        } finally {
            connection = null;
        }
    }

    /** True when a connection can be established (used by the login screen). */
    public boolean isAvailable() {
        try {
            getConnection();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
