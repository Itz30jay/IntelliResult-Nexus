<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    Public page (see security.public.paths) - no session, no sidebar,
    reachable by anyone who scans a marksheet's QR code. Every dynamic
    value comes from VerificationService's own DTO, never from a request
    parameter echoed back, so there is nothing attacker-influenceable on
    this page the way login.jsp's re-populated email field is - <c:out> is
    used uniformly anyway, for the same reason login.jsp's own comment
    gives: a later edit that adds a field shouldn't have to remember which
    ones were "safe" to skip escaping.
--%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Verify Marksheet - IntelliResult Nexus</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Fraunces:ital,opsz,wght@0,9..144,500;0,9..144,600;1,9..144,500&family=Inter:wght@400;500;600&family=IBM+Plex+Mono:wght@500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/tokens.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/auth.css">
</head>
<body>
<div class="verify-shell">
    <div class="verify-card">
        <div class="verify-wordmark">IntelliResult <span>Nexus</span></div>

        <c:choose>
            <c:when test="${verification.valid}">
                <div class="verify-banner verify-banner-valid">
                    <svg class="verify-banner-icon" width="48" height="48" viewBox="0 0 84 84" fill="none" aria-hidden="true">
                        <circle cx="42" cy="42" r="40" stroke="#2F6B52" stroke-width="1.5" stroke-dasharray="2 4"/>
                        <circle cx="42" cy="42" r="32" stroke="#2F6B52" stroke-width="1.5"/>
                        <path d="M28 42.5L37 51.5L56 32.5" stroke="#2F6B52" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                    <p class="verify-banner-title">Verified</p>
                    <p class="verify-banner-subtext">This is an authentic IntelliResult Nexus academic record.</p>
                </div>

                <div class="verify-detail-row">
                    <span class="verify-detail-label">Student</span>
                    <span class="verify-detail-value"><c:out value="${verification.studentName}"/></span>
                </div>
                <div class="verify-detail-row">
                    <span class="verify-detail-label">Examination</span>
                    <span class="verify-detail-value"><c:out value="${verification.examName}"/></span>
                </div>
                <div class="verify-detail-row">
                    <span class="verify-detail-label">Academic Year</span>
                    <span class="verify-detail-value"><c:out value="${verification.academicYearLabel}"/></span>
                </div>
                <div class="verify-detail-row">
                    <span class="verify-detail-label">Result Status</span>
                    <span class="verify-detail-value"><c:out value="${verification.resultStatusLabel}"/></span>
                </div>
            </c:when>
            <c:otherwise>
                <div class="verify-banner verify-banner-invalid">
                    <svg class="verify-banner-icon" width="48" height="48" viewBox="0 0 84 84" fill="none" aria-hidden="true">
                        <circle cx="42" cy="42" r="40" stroke="#A3384A" stroke-width="1.5" stroke-dasharray="2 4"/>
                        <path d="M30 30L54 54M54 30L30 54" stroke="#A3384A" stroke-width="3" stroke-linecap="round"/>
                    </svg>
                    <p class="verify-banner-title">Not Verified</p>
                    <p class="verify-banner-subtext">We could not find a matching official record for this code. It may be mistyped, expired, or was not issued by IntelliResult Nexus.</p>
                </div>
            </c:otherwise>
        </c:choose>

        <p class="verify-timestamp">Checked <c:out value="${verifiedAtDisplay}"/></p>
    </div>
</div>
</body>
</html>
