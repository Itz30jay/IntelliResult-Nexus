<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    Catches both explicit HTTP 500 responses and any uncaught Throwable
    (web.xml's <exception-type>java.lang.Throwable</exception-type>
    mapping). Renders no exception detail, stack trace, or message to the
    browser - Sec. 4/45 require friendly errors with technical detail
    logged internally only.

    The one scriptlet in this entire codebase lives in the block below,
    and only there - every other JSP in this project is pure JSTL/EL, and
    that stays true here too for everything except the four lines that
    genuinely cannot be: Log4j2 and Sentry are Java APIs with no JSTL tag
    for "call this method", and isErrorPage="true" is what makes the
    `exception` variable those four lines need available at all. Kept to
    the smallest possible scope, fully-qualified rather than adding
    page-level imports, so it stays visually impossible to mistake for
    the page's own presentation logic below it.
--%>
<%
    if (exception != null) {
        org.apache.logging.log4j.LogManager.getLogger("com.intelliresult.nexus.GlobalErrorHandler")
                .error("Unhandled exception reached the generic error page (request: {} {})",
                        request.getMethod(), request.getRequestURI(), exception);
        io.sentry.Sentry.captureException(exception, scope -> scope.setTag("module", "global-error-handler"));
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Something Went Wrong - IntelliResult Nexus</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <style>
        body {
            min-height: 100vh; margin: 0; display: flex; align-items: center; justify-content: center;
            background: var(--color-paper); padding: var(--space-6); font-family: Inter, sans-serif;
        }
        .error-card { text-align: center; max-width: 460px; }
        .error-icon { color: var(--color-danger); margin-bottom: var(--space-4); }
        .error-title { font-size: 1.2rem; font-weight: 600; color: var(--color-ink); margin: 0 0 var(--space-2); }
        .error-text { color: var(--color-ink-soft); font-size: 0.9rem; line-height: 1.6; margin-bottom: var(--space-6); }
        .error-action {
            display: inline-block; background: var(--color-ink); color: #fff; text-decoration: none;
            padding: 0.7rem 1.5rem; border-radius: var(--radius-sm); font-size: 0.9rem; font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="error-card">
        <svg class="error-icon" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="13"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p class="error-title">Something went wrong on our end</p>
        <p class="error-text">The error has been logged and, if this keeps happening, our team will be notified automatically. Please try again in a moment.</p>
        <c:choose>
            <c:when test="${empty sessionScope.sessionUserRole}">
                <a class="error-action" href="${pageContext.request.contextPath}/login">Go to login</a>
            </c:when>
            <c:when test="${sessionScope.sessionUserRole == 'ADMIN'}">
                <a class="error-action" href="${pageContext.request.contextPath}/admin/dashboard">Go to your dashboard</a>
            </c:when>
            <c:when test="${sessionScope.sessionUserRole == 'TEACHER'}">
                <a class="error-action" href="${pageContext.request.contextPath}/teacher/dashboard">Go to your dashboard</a>
            </c:when>
            <c:otherwise>
                <a class="error-action" href="${pageContext.request.contextPath}/student/dashboard">Go to your dashboard</a>
            </c:otherwise>
        </c:choose>
    </div>
</body>
</html>
