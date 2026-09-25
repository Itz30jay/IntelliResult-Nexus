<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
    Phase 1's placeholder here explicitly said "Phase 4 replaces this with
    a redirect to the login page" - confirmed during Phase 16's audit that
    it never actually happened; this file was still the untouched Phase 1
    smoke-test page. Closed here rather than left for a later phase, since
    Sec. 69's whole navigation model assumes every role has a real landing
    page and "/" quietly wasn't one.

    sessionUserRole (AppConstants.SESSION_USER_ROLE) already exists as a
    session attribute set at login - reading it directly here needs no
    servlet of its own and works whether or not this request happens to
    pass through AuthenticationFilter's own re-verification.
--%>
<c:choose>
    <c:when test="${empty sessionScope.sessionUserRole}">
        <c:redirect url="/login"/>
    </c:when>
    <c:when test="${sessionScope.sessionUserRole == 'ADMIN'}">
        <c:redirect url="/admin/dashboard"/>
    </c:when>
    <c:when test="${sessionScope.sessionUserRole == 'TEACHER'}">
        <c:redirect url="/teacher/dashboard"/>
    </c:when>
    <c:otherwise>
        <c:redirect url="/student/dashboard"/>
    </c:otherwise>
</c:choose>
