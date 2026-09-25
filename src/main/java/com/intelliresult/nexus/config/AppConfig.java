package com.intelliresult.nexus.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application.properties once at class-init time and layers environment
 * variables on top of it, so every other class asks AppConfig for a value
 * instead of reading files or System.getenv() directly.
 * Precedence is env var > properties file > caller-supplied default. This
 * gives one config surface for local dev (edit application.properties) and
 * production (set environment variables) without two different code paths.
 * "db.url" resolves to env var "INTELLIRESULT_DB_URL" via
 * {@link #toEnvKey(String)} - dots become underscores, upper-cased, prefixed.
 */
public final class AppConfig {

    private static final Logger LOGGER = LogManager.getLogger(AppConfig.class);
    private static final String ENV_PREFIX = "INTELLIRESULT_";
    private static final String PROPERTIES_RESOURCE = "/application.properties";

    private static final Properties PROPERTIES = loadProperties();

    private AppConfig() {
        // Static-only utility class; instantiation would suggest per-instance
        // state that this class deliberately does not have.
    }

    // Reads application.properties from the classpath exactly once. A missing
    // or unreadable file fails the whole application at startup rather than
    // limping along with silently-empty configuration.
    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(PROPERTIES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Missing " + PROPERTIES_RESOURCE + " on the classpath - "
                                + "the WAR was built incorrectly.");
            }
            props.load(in);
            LOGGER.info("Loaded {} configuration keys from {}", props.size(), PROPERTIES_RESOURCE);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + PROPERTIES_RESOURCE, e);
        }
        return props;
    }

    private static String toEnvKey(String propertyKey) {
        return ENV_PREFIX + propertyKey.replace('.', '_').toUpperCase();
    }

    /**
     * Returns the resolved value for {@code key}, or {@code defaultValue} if
     * it is set in neither the environment nor application.properties.
     */
    public static String getString(String key, String defaultValue) {
        String envValue = System.getenv(toEnvKey(key));
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return PROPERTIES.getProperty(key, defaultValue);
    }

    /**
     * Returns the resolved value for {@code key}, or throws if it is
     * genuinely required and absent from both the environment and the
     * properties file. Used for secrets (DB password) that must never have a
     * silently-applied default, since a fallback password is a security bug
     * waiting to happen, not a convenience.
     */
    public static String getRequiredString(String key) {
        String value = getString(key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Required configuration '" + key + "' is not set. Set the "
                            + toEnvKey(key) + " environment variable.");
        }
        return value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Configuration '{}' = '{}' is not a valid integer; using default {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    public static long getLong(String key, long defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Configuration '{}' = '{}' is not a valid long; using default {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Configuration '{}' = '{}' is not a valid double; using default {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = getString(key, null);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }

    /** True when app.environment=production; used sparingly for the handful of behaviors that must genuinely differ between environments (e.g. cookie Secure flag guidance in the deployment guide). */
    public static boolean isProduction() {
        return "production".equalsIgnoreCase(getString("app.environment", "development"));
    }
}
