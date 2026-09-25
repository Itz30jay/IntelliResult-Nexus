<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Change Password - IntelliResult Nexus</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/auth.css">
    <style>
        .plain-shell { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: var(--space-6); background: var(--color-paper); }
    </style>
</head>
<body>
    <div class="plain-shell">
        <div class="auth-card">
            <h1>Change password</h1>
            <p class="auth-lede">
                <c:if test="${not empty currentUser}">Signed in as <c:out value="${currentUser.fullName}"/>.</c:if>
            </p>

            <c:if test="${not empty errorMessage}">
                <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
            </c:if>
            <c:if test="${not empty successMessage}">
                <div class="auth-success"><c:out value="${successMessage}"/></div>
            </c:if>

            <form method="post" action="${pageContext.request.contextPath}/change-password">
                <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">

                <div class="field">
                    <label for="currentPassword">Current password</label>
                    <input type="password" id="currentPassword" name="currentPassword" autocomplete="current-password" required>
                </div>

                <div class="field">
                    <label for="newPassword">New password</label>
                    <input type="password" id="newPassword" name="newPassword" autocomplete="new-password" minlength="8" required>
                </div>

                <div class="field">
                    <label for="confirmPassword">Confirm new password</label>
                    <input type="password" id="confirmPassword" name="confirmPassword" autocomplete="new-password" minlength="8" required>
                </div>

                <button type="submit" class="btn-primary">Update password</button>
            </form>

            <div class="auth-footer-link">
                <a href="${pageContext.request.contextPath}/">&larr; Back to dashboard</a>
            </div>
        </div>
    </div>
</body>
</html>
