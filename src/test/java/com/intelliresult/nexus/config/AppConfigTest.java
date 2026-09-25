package com.intelliresult.nexus.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises AppConfig against the real application.properties on the test
 * classpath (Maven includes main/resources on the test classpath by
 * default), rather than mocking property loading - the thing actually worth
 * verifying here is the fallback/parsing behavior, not a mock's own setup.
 */
class AppConfigTest {

    @Test
    void getString_returnsValueFromPropertiesFileWhenNoEnvVarIsSet() {
        // app.name is a real key with a real default in application.properties
        // and is extremely unlikely to collide with a real env var in any test runner.
        assertEquals("IntelliResult Nexus", AppConfig.getString("app.name", "fallback-should-not-be-used"));
    }

    @Test
    void getString_returnsCallerDefaultForCompletelyUnknownKey() {
        assertEquals("fallback-value",
                AppConfig.getString("this.key.does.not.exist.anywhere", "fallback-value"));
    }

    @Test
    void getInt_parsesValidIntegerFromPropertiesFile() {
        // db.pool.maximumPoolSize=10 in application.properties
        assertEquals(10, AppConfig.getInt("db.pool.maximumPoolSize", -1));
    }

    @Test
    void getInt_fallsBackToDefaultForUnknownKey() {
        assertEquals(42, AppConfig.getInt("no.such.integer.key", 42));
    }

    @Test
    void getBoolean_fallsBackToDefaultForUnknownKey() {
        assertTrue(AppConfig.getBoolean("no.such.boolean.key", true));
    }

    @Test
    void getRequiredString_throwsForKeyThatIsGenuinelyAbsent() {
        assertTrue(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> AppConfig.getRequiredString("definitely.not.configured.anywhere")
        ).getMessage().contains("INTELLIRESULT_DEFINITELY_NOT_CONFIGURED_ANYWHERE"));
    }

    @Test
    void isProduction_falseByDefaultInLocalDevProperties() {
        // application.properties ships with app.environment=development
        assertTrue(!AppConfig.isProduction());
    }
}
