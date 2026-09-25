package com.intelliresult.nexus.listener;

import com.intelliresult.nexus.config.AppConfig;
import com.intelliresult.nexus.config.HibernateUtil;
import io.sentry.Sentry;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Runs once when the WAR deploys and once when it undeploys/shuts down.
 * Everything here is intentionally front-loaded and eager rather than lazy:
 * building the Hibernate SessionFactory and initializing Sentry at startup
 * means a broken DB connection string or a malformed config value surfaces
 * as a loud, obvious deployment failure in the server log, instead of as a
 * confusing 500 error the first time some unlucky user loads a page.
 */
@WebListener
public class ApplicationStartupListener implements ServletContextListener {

    private static final Logger LOGGER = LogManager.getLogger(ApplicationStartupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        String appName = AppConfig.getString("app.name", "IntelliResult Nexus");
        LOGGER.info("========================================================");
        LOGGER.info("{} starting up (environment: {})", appName, AppConfig.getString("app.environment", "development"));
        LOGGER.info("========================================================");

        initializeSentry();

        // Forces HibernateUtil's static initializer to run now rather than on
        // first getCurrentSession() call, so a bad db.url/db.password fails
        // deployment immediately instead of on the first user's request.
        HibernateUtil.getSessionFactory();
        LOGGER.info("Hibernate SessionFactory ready.");

        LOGGER.info("{} startup complete.", appName);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        LOGGER.info("Shutting down - releasing Hibernate and Sentry resources.");
        HibernateUtil.shutdown();
        // Flushes any Sentry events still queued for delivery before the JVM
        // exits, so a crash-on-shutdown isn't silently lost.
        Sentry.close();
    }

    private void initializeSentry() {
        String dsn = AppConfig.getString("sentry.dsn", "");
        Sentry.init(options -> {
            options.setDsn(dsn);
            options.setEnvironment(AppConfig.getString("sentry.environment", "development"));
            options.setTracesSampleRate(AppConfig.getDouble("sentry.tracesSampleRate", 0.2));
            // Never let Sentry echo request bodies/headers back into events -
            // marks, emails, and names have no business leaving this server
            // through an error-tracking pipeline (Sec. 4/41: never send
            // sensitive student data to third-party tooling).
            options.setSendDefaultPii(false);
        });

        if (dsn.isBlank()) {
            LOGGER.warn("SENTRY_DSN not set - Sentry SDK initialized in a disabled/no-op state. "
                    + "Set the INTELLIRESULT_SENTRY_DSN environment variable to enable error tracking.");
        } else {
            LOGGER.info("Sentry initialized for environment '{}'.", AppConfig.getString("sentry.environment", "development"));
        }
    }
}
