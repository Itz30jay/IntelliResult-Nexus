<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    Wired into web.xml's <error-page> mapping for HTTP 404. isErrorPage
    stays "false" - a 404 is a routing outcome, not a thrown exception, so
    there is nothing here for isErrorPage="true" to expose. Standalone
    layout (no admin/teacher/student-head include) for the same reason
    verify/result.jsp and report-view.jsp are standalone: this page can be
    reached by someone not authenticated at all (a stale or mistyped
    link), so it can never assume a sidebar's worth of session state
    exists. The "go to your dashboard" link below reads
    sessionScope.sessionUserRole directly for that reason - it degrades
    to a login link when nobody is signed in, rather than assuming.
--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Page Not Found - IntelliResult Nexus</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <style>
        body {
            min-height: 100vh; margin: 0; display: flex; align-items: center; justify-content: center;
            background: var(--color-paper); padding: var(--space-6); font-family: Inter, sans-serif;
        }
        .error-card { text-align: center; max-width: 440px; }
        .error-code { font-family: var(--font-display); font-size: 4.5rem; font-weight: 600; color: var(--color-seal); margin: 0; line-height: 1; }
        .error-title { font-size: 1.2rem; font-weight: 600; color: var(--color-ink); margin: var(--space-3) 0 var(--space-2); }
        .error-text { color: var(--color-ink-soft); font-size: 0.9rem; line-height: 1.6; margin-bottom: var(--space-6); }
        .error-action {
            display: inline-block; background: var(--color-ink); color: #fff; text-decoration: none;
            padding: 0.7rem 1.5rem; border-radius: var(--radius-sm); font-size: 0.9rem; font-weight: 600;
        }
    </style>
</head>
<body>
    <div class="error-card">
        <p class="error-code">404</p>
        <p class="error-title">Page not found</p>
        <p class="error-text">The page you're looking for doesn't exist, may have moved, or the link may be out of date.</p>
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
