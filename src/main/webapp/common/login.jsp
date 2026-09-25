<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    Public page (see security.public.paths in application.properties).
    Every dynamic value below goes through <c:out> - the error message and
    the re-populated email field are both attacker-influenceable input in
    principle (a crafted email param could contain markup), so neither is
    ever interpolated with raw EL (${...} outside <c:out>), per Sec. 4's
    "never render untrusted input directly."
--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sign in - IntelliResult Nexus</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Fraunces:ital,opsz,wght@0,9..144,500;0,9..144,600;1,9..144,500&family=Inter:wght@400;500;600&family=IBM+Plex+Mono:wght@500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/auth.css">
</head>
<body>
    <div class="auth-shell">

        <div class="auth-brand">
            <div class="auth-wordmark">IntelliResult <span>Nexus</span></div>

            <div class="auth-brand-body">
                <svg class="auth-seal" viewBox="0 0 84 84" fill="none" aria-hidden="true">
                    <circle cx="42" cy="42" r="40" stroke="#D4AC76" stroke-width="1.5" stroke-dasharray="2 4"/>
                    <circle cx="42" cy="42" r="32" stroke="#D4AC76" stroke-width="1.5"/>
                    <path d="M28 42.5L37 51.5L56 32.5" stroke="#D4AC76" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <h1 class="auth-headline">Every result, <em>verified</em>.</h1>
                <p class="auth-subtext">Marks, grades, and marksheets that carry their own audit trail - from entry to approval to a QR code anyone can check.</p>
            </div>

            <div></div>
        </div>

        <div class="auth-form-panel">
            <div class="auth-card">
                <h1>Sign in</h1>
                <p class="auth-lede">Enter your email, roll number, or employee ID.</p>

                <c:if test="${not empty errorMessage}">
                    <div class="auth-error" role="alert">
                        <c:out value="${errorMessage}"/>
                    </div>
                </c:if>

                <form method="post" action="${pageContext.request.contextPath}/login" novalidate>
                    <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                    <c:if test="${not empty param.returnUrl}">
                        <input type="hidden" name="returnUrl" value="<c:out value='${param.returnUrl}'/>">
                    </c:if>

                    <div class="field">
                        <label for="identifier">Email, roll number, or employee ID</label>
                        <input type="text" id="identifier" name="identifier" autocomplete="username"
                               value="<c:out value='${identifier}'/>" required autofocus>
                    </div>

                    <div class="field">
                        <label for="password">Password</label>
                        <input type="password" id="password" name="password" autocomplete="current-password" required>
                    </div>

                    <button type="submit" class="btn-primary">Sign in</button>
                </form>

                <div style="display:flex; justify-content:space-between; margin-top:var(--space-4); font-size:0.85rem;">
                    <a href="${pageContext.request.contextPath}/forgot-password" style="color:var(--color-ink-soft);">Forgot password?</a>
                    <a href="${pageContext.request.contextPath}/register" style="color:var(--color-seal); font-weight:600;">Create an account</a>
                </div>
            </div>
        </div>

    </div>
</body>
</html>
