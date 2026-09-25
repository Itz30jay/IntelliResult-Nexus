package com.intelliresult.nexus.filter;

import com.intelliresult.nexus.config.HibernateUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.context.internal.ManagedSessionContext;

import java.io.IOException;

/**
 * Registered explicitly in web.xml, first in the filter chain - NOT via
 * @WebFilter. This filter must run before AuthenticationFilter (Phase 4),
 * which needs a bound Session to query UserDAO, and the Servlet
 * specification does not guarantee any particular execution order between
 * annotation-scanned filters. web.xml's <filter-mapping> declaration order
 * is the one ordering mechanism the spec DOES guarantee, which is why this
 * filter and Phase 4's three security filters are all registered there
 * instead - see web.xml's own comment on the full chain order.
 */
public class HibernateSessionFilter implements jakarta.servlet.Filter {

    private static final Logger LOGGER = LogManager.getLogger(HibernateSessionFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        Session session = HibernateUtil.getSessionFactory().openSession();
        ManagedSessionContext.bind(session);
        try {
            chain.doFilter(request, response);
        } catch (Exception e) {
            // Logged here as well as by whatever ultimately handles the
            // exception, because a request that fails mid-filter-chain is
            // exactly the case where losing the stack trace is most costly -
            // it's the one that never made it to a controller's own catch block.
            LOGGER.error("Unhandled exception while processing request", e);
            throw e;
        } finally {
            ManagedSessionContext.unbind(HibernateUtil.getSessionFactory());
            if (session.isOpen()) {
                session.close();
            }
        }
    }
}
