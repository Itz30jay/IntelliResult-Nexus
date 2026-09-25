<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- Public page (see security.public.paths). Mirrors login.jsp/register.jsp's shell exactly. Three steps, one page - see ForgotPasswordServlet's own Javadoc for why the step is resolved server-side rather than tracked in the URL. --%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Forgot Password - IntelliResult Nexus</title>
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
                    <path d="M42 24V44L54 52" stroke="#D4AC76" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
                <h1 class="auth-headline">Regain <em>access</em>.</h1>
                <p class="auth-subtext">Verify it's really you with a one-time code, choose a new password, and an administrator confirms the change before it takes effect.</p>
            </div>
            <div></div>
        </div>

        <div class="auth-form-panel">
            <div class="auth-card">

                <c:if test="${not empty errorMessage}">
                    <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
                </c:if>

                <c:choose>
                    <c:when test="${currentStep == 'done'}">
                        <h1>Request submitted</h1>
                        <p class="auth-lede">Your new password is saved and <span class="badge badge-status badge-status-pending">PENDING</span> admin approval. You'll be able to log in with it once it's approved &mdash; your current password still works until then.</p>
                        <a href="${pageContext.request.contextPath}/login" class="btn-primary" style="display:block; text-align:center; text-decoration:none;">Back to sign in</a>
                    </c:when>

                    <c:when test="${currentStep == 'new-password'}">
                        <h1>Choose a new password</h1>
                        <p class="auth-lede">Verified as <strong><c:out value="${identifier}"/></strong>. This won't take effect until an admin approves it.</p>
                        <form method="post" action="${pageContext.request.contextPath}/forgot-password">
                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                            <input type="hidden" name="step" value="submit-password">
                            <div class="field">
                                <label for="newPassword">New password</label>
                                <input type="password" id="newPassword" name="newPassword" autocomplete="new-password" required autofocus>
                                <div class="field-hint">At least 8 characters, with a letter and a number.</div>
                            </div>
                            <div class="field">
                                <label for="confirmPassword">Confirm new password</label>
                                <input type="password" id="confirmPassword" name="confirmPassword" autocomplete="new-password" required>
                            </div>
                            <button type="submit" class="btn-primary">Submit for approval</button>
                        </form>
                    </c:when>

                    <c:when test="${currentStep == 'verify-otp'}">
                        <h1>Enter your code</h1>
                        <p class="auth-lede">We've sent a 6-digit code to the email on file for <strong><c:out value="${identifier}"/></strong>, if an account exists. It's valid for 10 minutes.</p>
                        <form method="post" action="${pageContext.request.contextPath}/forgot-password">
                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                            <input type="hidden" name="step" value="verify-otp">
                            <input type="hidden" name="identifier" value="<c:out value='${identifier}'/>">
                            <div class="field">
                                <label for="otp">6-digit code</label>
                                <input type="text" id="otp" name="otp" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required autofocus
                                       style="font-family:var(--font-mono); font-size:1.4rem; letter-spacing:0.3em; text-align:center;">
                            </div>
                            <button type="submit" class="btn-primary">Verify</button>
                        </form>
                        <form method="post" action="${pageContext.request.contextPath}/forgot-password" style="margin-top:var(--space-3);">
                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                            <input type="hidden" name="step" value="request-otp">
                            <input type="hidden" name="identifier" value="<c:out value='${identifier}'/>">
                            <button type="submit" style="background:none; border:none; color:var(--color-ink-soft); font-size:0.85rem; cursor:pointer; text-decoration:underline; padding:0;">Didn't get it? Send a new code</button>
                        </form>
                    </c:when>

                    <c:otherwise>
                        <h1>Forgot password</h1>
                        <p class="auth-lede">Enter your email, roll number, or employee ID and we'll send a verification code.</p>
                        <form method="post" action="${pageContext.request.contextPath}/forgot-password">
                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                            <input type="hidden" name="step" value="request-otp">
                            <div class="field">
                                <label for="identifier">Email, roll number, or employee ID</label>
                                <input type="text" id="identifier" name="identifier" required autofocus value="<c:out value='${identifier}'/>">
                            </div>
                            <button type="submit" class="btn-primary">Send code</button>
                        </form>
                    </c:otherwise>
                </c:choose>

                <p style="margin-top:var(--space-4); font-size:0.85rem; text-align:center;">
                    <a href="${pageContext.request.contextPath}/login" style="color:var(--color-ink-soft);">Back to sign in</a>
                </p>
            </div>
        </div>
    </div>
</body>
</html>
