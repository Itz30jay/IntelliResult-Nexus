<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Access Denied - IntelliResult Nexus</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <style>
        .denied-shell { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: var(--space-6); }
        .denied-card { max-width: 420px; text-align: center; }
        .denied-code { font-family: var(--font-mono); font-size: 0.8rem; letter-spacing: 0.08em; color: var(--color-danger); text-transform: uppercase; }
        .denied-title { font-family: var(--font-display); font-size: 1.75rem; margin: var(--space-2) 0 var(--space-4); }
        .denied-body { color: var(--color-ink-soft); line-height: 1.6; margin-bottom: var(--space-8); }
        .denied-actions { display: flex; gap: var(--space-3); justify-content: center; }
        .denied-actions a { padding: 0.6rem 1.2rem; border-radius: var(--radius-sm); font-size: 0.9rem; font-weight: 600; text-decoration: none; }
        .denied-actions .primary { background: var(--color-ink); color: var(--color-paper); }
        .denied-actions .secondary { border: 1px solid var(--color-rule); color: var(--color-ink); }
    </style>
</head>
<body>
    <div class="denied-shell">
        <div class="denied-card">
            <div class="denied-code">403 &middot; Access Denied</div>
            <h1 class="denied-title">This page isn't available to your account</h1>
            <p class="denied-body">
                <c:choose>
                    <c:when test="${not empty currentUser}">
                        You're signed in as <c:out value="${currentUser.fullName}"/>
                        (<c:out value="${currentUser.role}"/>). This section requires a different role.
                    </c:when>
                    <c:otherwise>
                        This section requires a different account role than the one you're signed in with.
                    </c:otherwise>
                </c:choose>
            </p>
            <div class="denied-actions">
                <c:set var="dashboardHref" value="${not empty dashboardUrl ? dashboardUrl : pageContext.request.contextPath}"/>
                <a class="primary" href="<c:out value='${dashboardHref}'/>">Go to my dashboard</a>
                <form method="post" action="${pageContext.request.contextPath}/logout" style="margin:0;">
                    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                    <button type="submit" class="secondary" style="background:none;cursor:pointer;font:inherit;">Sign out</button>
                </form>
            </div>
        </div>
    </div>
</body>
</html>
