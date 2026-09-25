<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    Public page (see security.public.paths). Mirrors login.jsp's shell/
    styling exactly (auth-shell/auth-brand/auth-form-panel/auth-card) so the
    two public entry points to this system feel like one product, not two.
    Every dynamic value goes through <c:out>, same reasoning as login.jsp's
    own header comment - this form is reachable by anyone, logged in or not.
--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Create an account - IntelliResult Nexus</title>
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
                <h1 class="auth-headline">Join the <em>record</em>.</h1>
                <p class="auth-subtext">Register once, and an administrator verifies your details before your account goes live - the same care every result in this system already gets.</p>
            </div>
            <div></div>
        </div>

        <div class="auth-form-panel">
            <div class="auth-card">
                <c:choose>
                    <c:when test="${submitted}">
                        <h1>Request submitted</h1>
                        <p class="auth-lede">
                            Your ${submittedRole == 'TEACHER' ? 'teacher' : 'student'} registration is now
                            <span class="badge badge-status badge-status-pending">PENDING</span> review.
                            An administrator will verify your details; you'll be able to log in with the password
                            you just chose once it's approved.
                        </p>
                        <a href="${pageContext.request.contextPath}/login" class="btn-primary" style="display:block; text-align:center; text-decoration:none;">Back to sign in</a>
                    </c:when>
                    <c:otherwise>
                        <h1>Create an account</h1>
                        <p class="auth-lede">Choose your role to get started.</p>

                        <c:if test="${not empty errorMessage}">
                            <div class="auth-error" role="alert"><c:out value="${errorMessage}"/></div>
                        </c:if>

                        <div class="role-toggle" role="tablist" aria-label="Account type">
                            <button type="button" id="tabStudent" class="role-toggle-btn" onclick="selectRole('STUDENT')">Student</button>
                            <button type="button" id="tabTeacher" class="role-toggle-btn" onclick="selectRole('TEACHER')">Teacher</button>
                        </div>

                        <form method="post" action="${pageContext.request.contextPath}/register" novalidate>
                            <input type="hidden" name="csrfToken" value="${sessionScope.csrfToken}">
                            <input type="hidden" id="role" name="role" value="${empty selectedRole ? 'STUDENT' : selectedRole}">

                            <div class="field">
                                <label for="fullName">Full name</label>
                                <input type="text" id="fullName" name="fullName" required autofocus value="<c:out value='${param.fullName}'/>">
                                <c:if test="${not empty fieldErrors.fullName}"><div class="field-error"><c:out value="${fieldErrors.fullName}"/></div></c:if>
                            </div>

                            <div class="field" id="rollNoField">
                                <label for="rollNo">Roll number <span style="font-weight:400; color:var(--color-ink-soft);">(this becomes your login ID)</span></label>
                                <input type="text" id="rollNo" name="rollNo" value="<c:out value='${param.rollNo}'/>">
                                <c:if test="${not empty fieldErrors.rollNo}"><div class="field-error"><c:out value="${fieldErrors.rollNo}"/></div></c:if>
                            </div>

                            <div class="field" id="employeeCodeField" style="display:none;">
                                <label for="employeeCode">Employee ID <span style="font-weight:400; color:var(--color-ink-soft);">(this becomes your login ID)</span></label>
                                <input type="text" id="employeeCode" name="employeeCode" value="<c:out value='${param.employeeCode}'/>">
                                <c:if test="${not empty fieldErrors.employeeCode}"><div class="field-error"><c:out value="${fieldErrors.employeeCode}"/></div></c:if>
                            </div>

                            <div class="field">
                                <label for="phone">Phone number</label>
                                <input type="tel" id="phone" name="phone" required value="<c:out value='${param.phone}'/>">
                                <c:if test="${not empty fieldErrors.phone}"><div class="field-error"><c:out value="${fieldErrors.phone}"/></div></c:if>
                            </div>

                            <div class="field">
                                <label for="email">Email <span style="font-weight:400; color:var(--color-ink-soft);">(optional - needed later for Forgot Password)</span></label>
                                <input type="email" id="email" name="email" value="<c:out value='${param.email}'/>">
                                <c:if test="${not empty fieldErrors.email}"><div class="field-error"><c:out value="${fieldErrors.email}"/></div></c:if>
                            </div>

                            <div class="field">
                                <label for="password">Password</label>
                                <input type="password" id="password" name="password" autocomplete="new-password" required>
                                <div class="field-hint">At least 8 characters, with a letter and a number.</div>
                                <c:if test="${not empty fieldErrors.password}"><div class="field-error"><c:out value="${fieldErrors.password}"/></div></c:if>
                            </div>

                            <button type="submit" class="btn-primary">Submit for verification</button>
                        </form>

                        <p style="margin-top:var(--space-4); font-size:0.85rem; text-align:center;">
                            Already have an account? <a href="${pageContext.request.contextPath}/login" style="color:var(--color-seal); font-weight:600;">Sign in</a>
                        </p>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

    </div>

    <style>
        .role-toggle { display: flex; gap: var(--space-2); margin: var(--space-4) 0; }
        .role-toggle-btn {
            flex: 1; padding: 0.6rem; border-radius: var(--radius-md); border: 1px solid var(--color-rule);
            background: var(--color-paper); color: var(--color-ink-soft); font-family: var(--font-body);
            font-weight: 600; cursor: pointer; transition: all 0.15s ease;
        }
        .role-toggle-btn.active { background: var(--color-ink); color: var(--color-paper); border-color: var(--color-ink); }
    </style>
    <script>
        function selectRole(role) {
            document.getElementById('role').value = role;
            document.getElementById('tabStudent').classList.toggle('active', role === 'STUDENT');
            document.getElementById('tabTeacher').classList.toggle('active', role === 'TEACHER');
            document.getElementById('rollNoField').style.display = (role === 'STUDENT') ? 'block' : 'none';
            document.getElementById('employeeCodeField').style.display = (role === 'TEACHER') ? 'block' : 'none';
            document.getElementById('rollNo').required = (role === 'STUDENT');
            document.getElementById('employeeCode').required = (role === 'TEACHER');
        }
        selectRole('${empty selectedRole ? "STUDENT" : selectedRole}');
    </script>
</body>
</html>
