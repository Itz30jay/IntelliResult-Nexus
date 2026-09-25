package com.intelliresult.nexus.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

/**
 * Builds and owns the single application-wide Hibernate SessionFactory.
 * hibernate.cfg.xml supplies static, source-controlled settings (dialect,
 * session-context strategy, pool provider selection); this class layers the
 * environment-specific and secret values (URL, credentials, pool sizing) on
 * top from AppConfig before building the factory, which is what keeps
 * secrets out of a file that gets committed to Git.
 * getCurrentSession() relies on hibernate.current_session_context_class=thread
 * (set in hibernate.cfg.xml): whichever thread is handling the current HTTP
 * request gets a Session bound to it by HibernateSessionFilter, and every DAO
 * call during that request retrieves the *same* Session through this method.
 */
public final class HibernateUtil {

    private static final Logger LOGGER = LogManager.getLogger(HibernateUtil.class);

    private static final SessionFactory SESSION_FACTORY = buildSessionFactory();

    private HibernateUtil() {
        // Static-only utility class.
    }

    private static SessionFactory buildSessionFactory() {
        try {
            Configuration configuration = new Configuration().configure("hibernate.cfg.xml");

            // Connection details: required, no default password. A missing
            // DB password fails application startup immediately with a clear
            // message rather than the app limping along and failing on the
            // first request.
            configuration.setProperty("hibernate.connection.url",
                    AppConfig.getString("db.url", null));
            configuration.setProperty("hibernate.connection.username",
                    AppConfig.getString("db.username", null));
            configuration.setProperty("hibernate.connection.password",
                    AppConfig.getRequiredString("db.password"));

            // HikariCP pool sizing, tunable per environment without touching
            // source: a small local dev box and a production server need very
            // different pool sizes, and this is exactly the kind of value that
            // should never be hardcoded (Sec. 64 of the project spec).
            configuration.setProperty("hibernate.hikari.maximumPoolSize",
                    String.valueOf(AppConfig.getInt("db.pool.maximumPoolSize", 10)));
            configuration.setProperty("hibernate.hikari.minimumIdle",
                    String.valueOf(AppConfig.getInt("db.pool.minimumIdle", 2)));
            configuration.setProperty("hibernate.hikari.connectionTimeout",
                    String.valueOf(AppConfig.getLong("db.pool.connectionTimeoutMs", 30000)));
            configuration.setProperty("hibernate.hikari.idleTimeout",
                    String.valueOf(AppConfig.getLong("db.pool.idleTimeoutMs", 600000)));
            configuration.setProperty("hibernate.hikari.poolName", "intelliresult-hikari");

            // Overrides the static "validate" default from hibernate.cfg.xml
            // when a developer explicitly opts into "update" locally (see the
            // comment in application.properties for when each is appropriate).
            configuration.setProperty("hibernate.hbm2ddl.auto",
                    AppConfig.getString("db.schema.management", "validate"));

            SessionFactory factory = configuration.buildSessionFactory();
            LOGGER.info("Hibernate SessionFactory initialized successfully.");
            return factory;
        } catch (Exception e) {
            // A SessionFactory that fails to build means the application
            // cannot serve a single request correctly, so this is logged at
            // FATAL-equivalent severity and rethrown to abort startup rather
            // than deploying a web app that will 500 on every page.
            LOGGER.error("Hibernate SessionFactory initialization failed - aborting startup.", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    public static SessionFactory getSessionFactory() {
        return SESSION_FACTORY;
    }

    /**
     * Returns the Session bound to the calling thread by
     * HibernateSessionFilter. DAOs call this rather than opening their own
     * Session, so every DAO invoked while handling one HTTP request shares a
     * single first-level (L1) cache and a single set of pending changes.
     */
    public static org.hibernate.Session getCurrentSession() {
        return SESSION_FACTORY.getCurrentSession();
    }

    /** Closes the pool and releases JDBC resources. Called once from ApplicationStartupListener#contextDestroyed on graceful shutdown. */
    public static void shutdown() {
        if (SESSION_FACTORY != null && !SESSION_FACTORY.isClosed()) {
            SESSION_FACTORY.close();
            LOGGER.info("Hibernate SessionFactory closed.");
        }
    }
}
