package com.cineflow.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Utility class responsible for managing JDBC database connections.
 * Loads credentials from 'db.properties' to ensure sensitive credentials
 * are never hard-coded in the source code.
 */
public class DBConnection {

    private static final String DEFAULT_PROPERTIES_FILE = "db.properties";
    private static Properties properties = new Properties();
    private static boolean initialized = false;

    // Static initializer block loads properties once at class loading
    static {
        loadProperties();
    }

    /**
     * Loads the database configuration properties from file or classpath.
     */
    public static synchronized void loadProperties() {
        properties.clear();
        File externalFile = new File(DEFAULT_PROPERTIES_FILE);

        // Auto-bootstrap db.properties from db.properties.example if missing for new clones
        if (!externalFile.exists()) {
            File exampleFile = new File("db.properties.example");
            if (exampleFile.exists()) {
                try {
                    java.nio.file.Files.copy(exampleFile.toPath(), externalFile.toPath());
                    System.out.println("[DBConnection] Created 'db.properties' from 'db.properties.example'.");
                } catch (IOException e) {
                    System.err.println("[DBConnection] Could not auto-create db.properties: " + e.getMessage());
                }
            }
        }

        if (externalFile.exists()) {
            try (FileInputStream fis = new FileInputStream(externalFile)) {
                properties.load(fis);
                initialized = true;
            } catch (IOException e) {
                System.err.println("[DBConnection] Failed to load external db.properties: " + e.getMessage());
            }
        }

        // Fallback to classpath resource
        if (!initialized || properties.isEmpty()) {
            try (InputStream is = DBConnection.class.getClassLoader().getResourceAsStream(DEFAULT_PROPERTIES_FILE)) {
                if (is != null) {
                    properties.load(is);
                    initialized = true;
                }
            } catch (IOException ignored) {}
        }

        // Support environment variable overrides (useful for teammates, CI/CD, or Docker)
        String envUrl = System.getenv("CINEFLOW_DB_URL") != null ? System.getenv("CINEFLOW_DB_URL") : System.getenv("DB_URL");
        String envUser = System.getenv("CINEFLOW_DB_USER") != null ? System.getenv("CINEFLOW_DB_USER") : System.getenv("DB_USER");
        String envPass = System.getenv("CINEFLOW_DB_PASSWORD") != null ? System.getenv("CINEFLOW_DB_PASSWORD") : System.getenv("DB_PASSWORD");

        if (envUrl != null && !envUrl.isBlank()) properties.setProperty("db.url", envUrl);
        if (envUser != null && !envUser.isBlank()) properties.setProperty("db.user", envUser);
        if (envPass != null) properties.setProperty("db.password", envPass);
    }

    /**
     * Obtains a new JDBC Connection from DriverManager.
     * Callers must close this connection or manage it using try-with-resources.
     *
     * @return active java.sql.Connection
     * @throws SQLException if a database access error occurs or credentials are invalid
     */
    public static Connection getConnection() throws SQLException {
        if (!initialized || properties.isEmpty()) {
            loadProperties();
        }

        String url = properties.getProperty("db.url");
        String user = properties.getProperty("db.user");
        String password = properties.getProperty("db.password");

        if (url == null || user == null) {
            throw new SQLException("Database connection configuration missing. " +
                    "Ensure 'db.properties' exists with 'db.url', 'db.user', and 'db.password'.");
        }

        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException ex) {
            String advice = "";
            if (ex.getMessage() != null && ex.getMessage().contains("Access denied")) {
                advice = " -> Check your MySQL password in 'db.properties' (currently: '" + (password != null ? (password.length() > 0 ? "******" : "(empty)") : "null") + "').";
            } else if (ex.getMessage() != null && (ex.getMessage().contains("Communications link failure") || ex.getMessage().contains("Connection refused"))) {
                advice = " -> Ensure MySQL Server service is running on port 3306.";
            }
            throw new SQLException("Could not connect to MySQL at [" + url + "] as user '" + user + "'" + advice + " Original error: " + ex.getMessage(), ex);
        }
    }

    /**
     * Exposes loaded configuration properties.
     */
    public static synchronized Properties getProperties() {
        if (!initialized || properties.isEmpty()) {
            loadProperties();
        }
        return properties;
    }

    /**
     * Tests whether a connection can be established to the database.
     *
     * @return ConnectionTestResult with success status, database metadata or error details
     */
    public static ConnectionTestResult testConnection() {
        try (Connection conn = getConnection()) {
            if (conn != null && !conn.isClosed()) {
                DatabaseMetaData meta = conn.getMetaData();
                String productName = meta.getDatabaseProductName();
                String productVersion = meta.getDatabaseProductVersion();
                String catalog = conn.getCatalog();
                return new ConnectionTestResult(true, "Database connected",
                        productName + " " + productVersion, catalog, null);
            }
            return new ConnectionTestResult(false, "Connection is closed or null", "", "", null);
        } catch (SQLException e) {
            return new ConnectionTestResult(false, "Connection failed: " + e.getMessage(),
                    "", "", e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }

    public static String getDbUrl() {
        return properties.getProperty("db.url", "Unknown");
    }

    public static String getDbUser() {
        return properties.getProperty("db.user", "Unknown");
    }

    /**
     * Value object encapsulating connection diagnostics.
     */
    public static class ConnectionTestResult {
        private final boolean success;
        private final String message;
        private final String databaseVersion;
        private final String databaseName;
        private final Exception exception;

        public ConnectionTestResult(boolean success, String message, String databaseVersion,
                                    String databaseName, Exception exception) {
            this.success = success;
            this.message = message;
            this.databaseVersion = databaseVersion;
            this.databaseName = databaseName;
            this.exception = exception;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getDatabaseVersion() {
            return databaseVersion;
        }

        public String getDatabaseName() {
            return databaseName;
        }

        public Exception getException() {
            return exception;
        }
    }
}

